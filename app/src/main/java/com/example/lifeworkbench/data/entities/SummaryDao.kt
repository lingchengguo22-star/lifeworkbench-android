package com.example.lifeworkbench.data.entities

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 五大业务模块 DAO（V1.1 阶段 2.2：首页摘要只读；阶段 3.0：补齐写操作与单条查询）。
 *
 * 阶段 3.0 只补数据访问能力，不改实体、不改表结构、不升级数据库版本。
 * 写操作（更新/删除）返回实际影响行数，调用方据此识别“目标行不存在”（0 行 = 不存在）。
 * 游戏与睡眠的日期查询遵循首页摘要的归属规则（见 ModuleSummaryRepository）：
 * 游戏归开始日，睡眠归结束日（计时中归开始日）；日期换算由 Repository 按设备时区完成，
 * DAO 只接受 epoch 区间，不在 SQL 里做时区换算。
 */
@Dao
interface StudyRecordDao {
    /** 按日期查学习记录（实时 Flow）。 */
    @Query("SELECT * FROM study_records WHERE date = :date ORDER BY id ASC")
    fun getRecordsByDate(date: String): Flow<List<StudyRecordEntity>>

    /** 查全部学习记录（实时 Flow，历史列表：日期倒序、同日按 id 倒序）。 */
    @Query("SELECT * FROM study_records ORDER BY date DESC, id DESC")
    fun getAllRecords(): Flow<List<StudyRecordEntity>>

    @Query("SELECT * FROM study_records WHERE id = :id")
    suspend fun getRecordById(id: Int): StudyRecordEntity?

    @Insert
    suspend fun insertRecord(record: StudyRecordEntity): Long

    @Update
    suspend fun updateRecord(record: StudyRecordEntity): Int

    @Delete
    suspend fun deleteRecord(record: StudyRecordEntity): Int

    @Query("DELETE FROM study_records WHERE id = :id")
    suspend fun deleteRecordById(id: Int): Int
}

@Dao
interface DietRecordDao {
    /** 按日期查饮食记录（实时 Flow）。 */
    @Query("SELECT * FROM diet_records WHERE date = :date ORDER BY id ASC")
    fun getRecordsByDate(date: String): Flow<List<DietEntity>>

    /** 查全部饮食记录（实时 Flow，历史列表：日期倒序、同日按 id 倒序）。 */
    @Query("SELECT * FROM diet_records ORDER BY date DESC, id DESC")
    fun getAllRecords(): Flow<List<DietEntity>>

    @Query("SELECT * FROM diet_records WHERE id = :id")
    suspend fun getRecordById(id: Int): DietEntity?

    @Insert
    suspend fun insertRecord(record: DietEntity): Long

    @Update
    suspend fun updateRecord(record: DietEntity): Int

    @Delete
    suspend fun deleteRecord(record: DietEntity): Int

    @Query("DELETE FROM diet_records WHERE id = :id")
    suspend fun deleteRecordById(id: Int): Int
}

@Dao
interface FitnessRecordDao {
    /** 按日期查健身记录（实时 Flow）。 */
    @Query("SELECT * FROM fitness_records WHERE date = :date ORDER BY id ASC")
    fun getRecordsByDate(date: String): Flow<List<FitnessEntity>>

    /** 查全部健身记录（实时 Flow，历史列表：日期倒序、同日按 id 倒序）。 */
    @Query("SELECT * FROM fitness_records ORDER BY date DESC, id DESC")
    fun getAllRecords(): Flow<List<FitnessEntity>>

    @Query("SELECT * FROM fitness_records WHERE id = :id")
    suspend fun getRecordById(id: Int): FitnessEntity?

    @Insert
    suspend fun insertRecord(record: FitnessEntity): Long

    @Update
    suspend fun updateRecord(record: FitnessEntity): Int

    @Delete
    suspend fun deleteRecord(record: FitnessEntity): Int

    @Query("DELETE FROM fitness_records WHERE id = :id")
    suspend fun deleteRecordById(id: Int): Int
}

@Dao
interface GameRecordDao {
    /** 查全部游戏记录（实时 Flow）。日期归属在 Repository 里按 startTime + 设备时区计算。 */
    @Query("SELECT * FROM game_records ORDER BY startTime ASC")
    fun getAllRecords(): Flow<List<GameEntity>>

    @Query("SELECT * FROM game_records WHERE id = :id")
    suspend fun getRecordById(id: Int): GameEntity?

    @Insert
    suspend fun insertRecord(record: GameEntity): Long

    @Update
    suspend fun updateRecord(record: GameEntity): Int

    @Delete
    suspend fun deleteRecord(record: GameEntity): Int

    @Query("DELETE FROM game_records WHERE id = :id")
    suspend fun deleteRecordById(id: Int): Int

    /**
     * 按开始时间区间查游戏记录（实时 Flow，按开始时间升序）。
     * Repository 把本地日期换算为 epoch 区间 [dayStart, dayEnd) 后调用；
     * 游戏归属于它开始的那一天（跨午夜的整场游戏仍归开始日），与首页摘要规则一致。
     */
    @Query("SELECT * FROM game_records WHERE startTime >= :dayStart AND startTime < :dayEnd ORDER BY startTime ASC")
    fun getRecordsByStartTimeRange(dayStart: Long, dayEnd: Long): Flow<List<GameEntity>>
}

@Dao
interface SleepRecordDao {
    /** 查全部睡眠记录（实时 Flow）。日期归属在 Repository 里按 endTime（无则 startTime）+ 设备时区计算。 */
    @Query("SELECT * FROM sleep_records ORDER BY startTime ASC")
    fun getAllRecords(): Flow<List<SleepEntity>>

    @Query("SELECT * FROM sleep_records WHERE id = :id")
    suspend fun getRecordById(id: Int): SleepEntity?

    @Insert
    suspend fun insertRecord(record: SleepEntity): Long

    @Update
    suspend fun updateRecord(record: SleepEntity): Int

    @Delete
    suspend fun deleteRecord(record: SleepEntity): Int

    @Query("DELETE FROM sleep_records WHERE id = :id")
    suspend fun deleteRecordById(id: Int): Int

    /**
     * 按日期区间查睡眠记录（实时 Flow，按开始时间升序）。
     * Repository 把本地日期换算为 epoch 区间 [dayStart, dayEnd) 后调用；
     * 归属规则与首页摘要一致：已结束的睡眠归起床日（endTime 所在日），
     * 计时中的睡眠（endTime 为空）归入睡日（startTime 所在日）。
     */
    @Query("SELECT * FROM sleep_records WHERE (endTime IS NOT NULL AND endTime >= :dayStart AND endTime < :dayEnd) OR (endTime IS NULL AND startTime >= :dayStart AND startTime < :dayEnd) ORDER BY startTime ASC")
    fun getRecordsByDateRange(dayStart: Long, dayEnd: Long): Flow<List<SleepEntity>>
}
