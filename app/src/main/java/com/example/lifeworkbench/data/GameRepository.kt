package com.example.lifeworkbench.data

import com.example.lifeworkbench.data.entities.GameEntity
import com.example.lifeworkbench.data.entities.GameRecordDao
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

/**
 * 游戏记录数据访问统一入口（V1.1 阶段 3.0）。
 * UI 与 ViewModel 不直接操作 DAO，一律走 Repository。
 * 3.0 不做业务校验（GameValidator 在 3.4 建立，届时明确时长唯一计算依据与时间合法性）；
 * 不开发自动计时（计时器与互斥在 3.4 第二步，须先过独立审查）。
 * 写操作只负责落库与错误处理。
 * 更新/删除按实际影响行数判定：> 0 成功，= 0 返回 NotFound。
 *
 * 日期归属规则（与首页摘要 ModuleSummaryRepository 一致）：
 * 游戏归属于它开始的那一天（跨午夜的整场游戏仍归开始日）。
 * 实现：把本地日期换算为设备时区的 epoch 区间 [dayStart, dayEnd)，再按 startTime 查区间。
 */
class GameRepository(
    private val dao: GameRecordDao,
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() }
) {

    /** 创建游戏记录，返回新记录的行 ID。 */
    suspend fun createRecord(record: GameEntity): RepoResult<Long> {
        return runCatching { RepoResult.Success(dao.insertRecord(record)) }
            .getOrElse { RepoResult.Failure(RepoError.Database(it)) }
    }

    /** 按 ID 获取游戏记录，查不到返回 NotFound。 */
    suspend fun getRecordById(id: Int): RepoResult<GameEntity> {
        return runCatching {
            dao.getRecordById(id)?.let { RepoResult.Success(it) }
                ?: RepoResult.Failure(RepoError.NotFound(id))
        }.getOrElse { RepoResult.Failure(RepoError.Database(it)) }
    }

    /** 查询全部游戏记录（实时 Flow，按开始时间升序）。 */
    fun getAllRecords(): Flow<List<GameEntity>> = dao.getAllRecords()

    /**
     * 查询指定本地日期的游戏记录（实时 Flow，按开始时间升序）。
     * date 为 YYYY-MM-DD；归属规则与首页摘要一致（游戏归开始日）。
     */
    fun getRecordsByDate(date: String): Flow<List<GameEntity>> {
        val day = LocalDate.parse(date)
        val dayStart = day.atStartOfDay(zoneProvider()).toInstant().toEpochMilli()
        val dayEnd = day.plusDays(1).atStartOfDay(zoneProvider()).toInstant().toEpochMilli()
        return dao.getRecordsByStartTimeRange(dayStart, dayEnd)
    }

    /**
     * 更新游戏记录：先读原记录——不存在返回 NotFound；
     * 写入后按实际影响行数判定：> 0 成功，= 0 返回 NotFound。
     */
    suspend fun updateRecord(record: GameEntity): RepoResult<Unit> {
        return runCatching {
            val existing = dao.getRecordById(record.id)
            if (existing == null) {
                RepoResult.Failure(RepoError.NotFound(record.id))
            } else {
                val rows = dao.updateRecord(record)
                if (rows > 0) RepoResult.Success(Unit)
                else RepoResult.Failure(RepoError.NotFound(record.id))
            }
        }.getOrElse { RepoResult.Failure(RepoError.Database(it)) }
    }

    /** 删除游戏记录（按实体）：影响行数 > 0 成功，= 0 返回 NotFound。 */
    suspend fun deleteRecord(record: GameEntity): RepoResult<Unit> {
        return runCatching {
            val rows = dao.deleteRecord(record)
            if (rows > 0) RepoResult.Success(Unit)
            else RepoResult.Failure(RepoError.NotFound(record.id))
        }.getOrElse { RepoResult.Failure(RepoError.Database(it)) }
    }

    /** 删除游戏记录（按 ID）：影响行数 > 0 成功，= 0 返回 NotFound。 */
    suspend fun deleteRecordById(id: Int): RepoResult<Unit> {
        return runCatching {
            val rows = dao.deleteRecordById(id)
            if (rows > 0) RepoResult.Success(Unit)
            else RepoResult.Failure(RepoError.NotFound(id))
        }.getOrElse { RepoResult.Failure(RepoError.Database(it)) }
    }
}
