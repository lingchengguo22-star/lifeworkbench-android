package com.example.lifeworkbench.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.lifeworkbench.data.entities.DietEntity
import com.example.lifeworkbench.data.entities.FitnessEntity
import com.example.lifeworkbench.data.entities.GameEntity
import com.example.lifeworkbench.data.entities.SleepEntity
import com.example.lifeworkbench.data.entities.StudyRecordEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 阶段 2.2：五大模块只读摘要 Repository 测试（Robolectric + 真实 Room 内存数据库）。
 *
 * 覆盖：空数据、学习/健身/饮食按 date 字段汇总、游戏按 startTime 本地日期归属、
 * 睡眠按 endTime 本地日期归属（含跨午夜与计时中）、Flow 数据变化后刷新。
 */
@RunWith(RobolectricTestRunner::class)
class ModuleSummaryRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ModuleSummaryRepository

    private val today = LocalDate.of(2026, 11, 15)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).addMigrations(AppDatabase.MIGRATION_1_2).build()
        repo = ModuleSummaryRepository(
            studyDao = db.studyRecordDao(),
            dietDao = db.dietRecordDao(),
            fitnessDao = db.fitnessRecordDao(),
            gameDao = db.gameRecordDao(),
            sleepDao = db.sleepRecordDao()
        ) { today }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun epoch(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun summaries_empty_returnsZeroCounts() = runTest {
        val s = repo.getTodaySummaries().first()
        assertEquals(0, s.study.recordCount)
        assertEquals(0, s.study.totalMinutes)
        assertEquals(0, s.fitness.recordCount)
        assertEquals(0, s.diet.mealCount)
        assertEquals(0f, s.diet.totalCalories, 0.001f)
        assertEquals(0, s.game.recordCount)
        assertEquals(0, s.sleep.recordCount)
        assertEquals(0, s.sleep.totalMinutes)
    }

    @Test
    fun studySummary_sumsTodayMinutesOnly() = runTest {
        insertStudy("2026-11-15", 30)
        insertStudy("2026-11-15", 60)
        insertStudy("2026-11-14", 120)
        val s = repo.getTodaySummaries().first { it.study.recordCount == 2 }
        assertEquals(2, s.study.recordCount)
        assertEquals(90, s.study.totalMinutes)
    }

    @Test
    fun fitnessSummary_countsRecordsAndIgnoresNullDuration() = runTest {
        insertFitness("2026-11-15", 45)
        insertFitness("2026-11-15", null)
        insertFitness("2026-11-14", 30)
        val s = repo.getTodaySummaries().first { it.fitness.recordCount == 2 }
        assertEquals(2, s.fitness.recordCount)
        assertEquals(45, s.fitness.totalMinutes)
    }

    @Test
    fun dietSummary_sumsTodayCaloriesOnly() = runTest {
        insertDiet("2026-11-15", 500f)
        insertDiet("2026-11-15", 300f)
        insertDiet("2026-11-14", 700f)
        val s = repo.getTodaySummaries().first { it.diet.mealCount == 2 }
        assertEquals(2, s.diet.mealCount)
        assertEquals(800f, s.diet.totalCalories, 0.001f)
    }

    @Test
    fun gameSummary_usesStartTimeLocalDate() = runTest {
        insertGame("今晚游戏", epoch(2026, 11, 15, 20, 0), epoch(2026, 11, 15, 21, 30), 90)
        insertGame("昨晚游戏", epoch(2026, 11, 14, 23, 0), epoch(2026, 11, 15, 0, 30), 90)
        val s = repo.getTodaySummaries().first { it.game.recordCount == 1 }
        assertEquals(1, s.game.recordCount)
        assertEquals(90, s.game.totalMinutes)
    }

    @Test
    fun sleepSummary_usesEndTimeLocalDate_crossMidnight() = runTest {
        // 昨晚 23:30 入睡、今早 07:30 醒来：跨午夜，按醒来日期归属今天，共 480 分钟
        insertSleep(epoch(2026, 11, 14, 23, 30), epoch(2026, 11, 15, 7, 30))
        // 今晚 23:00 入睡、明早 07:00 醒来：归属明天，不计入今天
        insertSleep(epoch(2026, 11, 15, 23, 0), epoch(2026, 11, 16, 7, 0))
        val s = repo.getTodaySummaries().first { it.sleep.recordCount == 1 }
        assertEquals(1, s.sleep.recordCount)
        assertEquals(480, s.sleep.totalMinutes)
    }

    @Test
    fun sleepSummary_inProgress_countsByStartDateWithoutMinutes() = runTest {
        // 计时中（endTime 为空）：按开始日期归属今天，分钟数不计入
        insertSleep(epoch(2026, 11, 15, 23, 0), null)
        val s = repo.getTodaySummaries().first { it.sleep.recordCount == 1 }
        assertEquals(1, s.sleep.recordCount)
        assertEquals(0, s.sleep.totalMinutes)
    }

    @Test
    fun summaries_flowUpdatesWhenDataChanges() = runTest {
        val first = repo.getTodaySummaries().first()
        assertEquals(0, first.study.recordCount)
        insertStudy("2026-11-15", 25)
        val second = repo.getTodaySummaries().first { it.study.recordCount == 1 }
        assertEquals(25, second.study.totalMinutes)
    }

    // ---- 测试夹具：直接写库（摘要 DAO 为只读，业务写路径属于后续阶段） ----

    private suspend fun insertStudy(date: String, minutes: Int) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO study_records (date, durationMinutes, content, knowledgePointId) VALUES (?, ?, ?, NULL)",
            arrayOf(date, minutes, "学习内容")
        )
    }

    private suspend fun insertFitness(date: String, minutes: Int?) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO fitness_records (date, type, actionName, weight, reps, sets, durationMinutes) VALUES (?, '力量', '深蹲', NULL, NULL, NULL, ?)",
            arrayOf(date, minutes)
        )
    }

    private suspend fun insertDiet(date: String, calories: Float) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO diet_records (date, mealType, foodName, weightGrams, calories, protein, carbs, fat) VALUES (?, '午餐', '米饭', 200, ?, 10, 50, 5)",
            arrayOf(date, calories)
        )
    }

    private suspend fun insertGame(name: String, start: Long, end: Long?, minutes: Int) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO game_records (gameName, startTime, endTime, durationMinutes, recordType) VALUES (?, ?, ?, ?, '手动补录')",
            arrayOf(name, start, end, minutes)
        )
    }

    private suspend fun insertSleep(start: Long, end: Long?) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO sleep_records (startTime, endTime, quality, wakeUpCount, status) VALUES (?, ?, NULL, 0, '已完成')",
            arrayOf(start, end)
        )
    }
}
