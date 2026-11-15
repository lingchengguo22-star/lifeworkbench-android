package com.example.lifeworkbench.data.entities

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 五大业务模块只读 DAO（V1.1 阶段 2.2：首页摘要）。
 *
 * 只做读查询，不修改业务表结构、不新增写操作；
 * 五大模块完整 CRUD 属于后续阶段，本阶段不提前开发。
 */
@Dao
interface StudyRecordDao {
    /** 按日期查学习记录（实时 Flow）。 */
    @Query("SELECT * FROM study_records WHERE date = :date ORDER BY id ASC")
    fun getRecordsByDate(date: String): Flow<List<StudyRecordEntity>>
}

@Dao
interface DietRecordDao {
    /** 按日期查饮食记录（实时 Flow）。 */
    @Query("SELECT * FROM diet_records WHERE date = :date ORDER BY id ASC")
    fun getRecordsByDate(date: String): Flow<List<DietEntity>>
}

@Dao
interface FitnessRecordDao {
    /** 按日期查健身记录（实时 Flow）。 */
    @Query("SELECT * FROM fitness_records WHERE date = :date ORDER BY id ASC")
    fun getRecordsByDate(date: String): Flow<List<FitnessEntity>>
}

@Dao
interface GameRecordDao {
    /** 查全部游戏记录（实时 Flow）。日期归属在 Repository 里按 startTime + 设备时区计算。 */
    @Query("SELECT * FROM game_records ORDER BY startTime ASC")
    fun getAllRecords(): Flow<List<GameEntity>>
}

@Dao
interface SleepRecordDao {
    /** 查全部睡眠记录（实时 Flow）。日期归属在 Repository 里按 endTime（无则 startTime）+ 设备时区计算。 */
    @Query("SELECT * FROM sleep_records ORDER BY startTime ASC")
    fun getAllRecords(): Flow<List<SleepEntity>>
}
