package com.example.lifeworkbench.data

import com.example.lifeworkbench.data.entities.DietRecordDao
import com.example.lifeworkbench.data.entities.FitnessRecordDao
import com.example.lifeworkbench.data.entities.GameRecordDao
import com.example.lifeworkbench.data.entities.SleepRecordDao
import com.example.lifeworkbench.data.entities.StudyRecordDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 学习摘要：今日记录条数与总学习分钟数。 */
data class StudySummary(val recordCount: Int, val totalMinutes: Int)

/** 健身摘要：今日记录条数与总训练分钟数（durationMinutes 为空的记录不计入分钟数）。 */
data class FitnessSummary(val recordCount: Int, val totalMinutes: Int)

/** 饮食摘要：今日用餐记录条数与总热量（千卡）。 */
data class DietSummary(val mealCount: Int, val totalCalories: Float)

/** 游戏摘要：今日游戏记录条数与总游戏分钟数。 */
data class GameSummary(val recordCount: Int, val totalMinutes: Int)

/** 睡眠摘要：今日睡眠记录条数与总睡眠分钟数（仅已结束的记录计入分钟数）。 */
data class SleepSummary(val recordCount: Int, val totalMinutes: Int)

/** 五大模块今日摘要。 */
data class ModuleSummaries(
    val study: StudySummary,
    val fitness: FitnessSummary,
    val diet: DietSummary,
    val game: GameSummary,
    val sleep: SleepSummary
)

/**
 * 五大业务模块只读摘要 Repository（V1.1 阶段 2.2：首页摘要）。
 *
 * 只读，不修改业务表结构、不创建假数据；没有数据时摘要为 0（界面显示空状态）。
 * 数据变化后 Flow 自动发出新值，首页摘要及时刷新。
 *
 * 日期归属规则（经时间字段及时区审查）：
 * - 学习 / 健身 / 饮食：记录自带 date 字段（YYYY-MM-DD），直接按该日期归属。
 * - 游戏：startTime 为 epoch 毫秒，按设备当前时区换算为本地日期归属
 *   （一次游戏属于它开始的那一天）。
 * - 睡眠：startTime/endTime 为 epoch 毫秒，按设备当前时区换算；
 *   归属日期取 endTime 的本地日期（即“今早醒来的那次睡眠”属于今天），
 *   endTime 为空（计时中）时取 startTime 的本地日期。
 *   睡眠分钟数仅统计已结束的记录（endTime 不为空），按 (endTime - startTime) / 60000 计算。
 */
class ModuleSummaryRepository(
    private val studyDao: StudyRecordDao,
    private val dietDao: DietRecordDao,
    private val fitnessDao: FitnessRecordDao,
    private val gameDao: GameRecordDao,
    private val sleepDao: SleepRecordDao,
    private val todayProvider: () -> LocalDate = { LocalDate.now() }
) {
    fun getTodaySummaries(): Flow<ModuleSummaries> {
        val today = todayProvider().toString()
        return combine(
            studyDao.getRecordsByDate(today),
            dietDao.getRecordsByDate(today),
            fitnessDao.getRecordsByDate(today),
            gameDao.getAllRecords(),
            sleepDao.getAllRecords()
        ) { study, diet, fitness, games, sleeps ->
            val todayDate = todayProvider()
            val todayGames = games.filter { localDateOf(it.startTime) == todayDate }
            val todaySleeps = sleeps.filter { localDateOf(it.endTime ?: it.startTime) == todayDate }
            ModuleSummaries(
                study = StudySummary(
                    recordCount = study.size,
                    totalMinutes = study.sumOf { it.durationMinutes }
                ),
                fitness = FitnessSummary(
                    recordCount = fitness.size,
                    totalMinutes = fitness.sumOf { it.durationMinutes ?: 0 }
                ),
                diet = DietSummary(
                    mealCount = diet.size,
                    totalCalories = diet.sumOf { it.calories.toDouble() }.toFloat()
                ),
                game = GameSummary(
                    recordCount = todayGames.size,
                    totalMinutes = todayGames.sumOf { it.durationMinutes }
                ),
                sleep = SleepSummary(
                    recordCount = todaySleeps.size,
                    totalMinutes = todaySleeps
                        .filter { it.endTime != null }
                        .sumOf { ((it.endTime!! - it.startTime) / 60000).toInt() }
                )
            )
        }
    }

    /** epoch 毫秒按设备当前时区换算为本地日期。 */
    private fun localDateOf(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
}
