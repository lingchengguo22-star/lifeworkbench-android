package com.example.lifeworkbench.data.entities
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String?,
    val planDate: String, // YYYY-MM-DD
    val category: String, // 学习、健身、饮食、游戏、睡眠等
    val priority: Int,
    val status: String, // 待开始、进行中、已完成、已取消
    val sourceId: Int?, // 关联来源模块ID
    val startMinute: Int? = null, // 开始时间：从 0:00 起的分钟数，0~1439；null 表示无具体时间（V1.1 阶段 1 新增）
    val endMinute: Int? = null // 结束时间：从 0:00 起的分钟数，0~1439；null 表示无具体时间（V1.1 阶段 1 新增）
)

/**
 * 按日期与状态的统计结果：status 为数据库里的原始字符串（含未知值），
 * 映射为 [TaskStatus] 的工作由 Repository / 统计逻辑负责，DAO 不做转换、不丢未知值。
 */
data class TaskStatusCount(
    val status: String,
    val count: Int
)

@Dao
interface TaskDao {
    /**
     * 按日期查任务（实时 Flow）。排序：有时间的在前、按开始时间升序，无时间的在后，同组按 id 升序。
     * （V1.1 阶段 1：startMinute 加入排序；无时间的任务排在后面。）
     */
    @Query("SELECT * FROM tasks WHERE planDate = :date ORDER BY (startMinute IS NULL), startMinute ASC, id ASC")
    fun getTasksByDate(date: String): Flow<List<TaskEntity>>

    @Insert
    suspend fun insertTask(task: TaskEntity): Long

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): TaskEntity?

    /**
     * 按日期范围查任务（实时 Flow，起止日期均包含在内）。
     * 排序：先按日期升序，日期内再按开始时间升序（无时间的在后），同组按 id 升序。
     */
    @Query("SELECT * FROM tasks WHERE planDate BETWEEN :startDate AND :endDate ORDER BY planDate ASC, (startMinute IS NULL), startMinute ASC, id ASC")
    fun getTasksByDateRange(startDate: String, endDate: String): Flow<List<TaskEntity>>

    /**
     * 按日期与状态查任务（实时 Flow）。调用方必须传 [TaskStatus.toDbValue] 得到的状态值，
     * 禁止在调用处手写中文状态字符串。
     */
    @Query("SELECT * FROM tasks WHERE planDate = :date AND status = :status ORDER BY (startMinute IS NULL), startMinute ASC, id ASC")
    fun getTasksByDateAndStatus(date: String, status: String): Flow<List<TaskEntity>>

    // V1.1 阶段 0：状态修改持久化（仅增 DAO 方法，不改表结构、不升级数据库版本）
    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Int)

    @Query("SELECT COUNT(*) FROM tasks WHERE planDate = :date")
    suspend fun countTasksByDate(date: String): Int

    /**
     * 按日期统计各状态数量。直接按数据库里的原始 status 分组返回（含未知值），
     * 不过滤、不转换；调用方用 [TaskStatus.fromDbValue] 映射。
     */
    @Query("SELECT status, COUNT(*) AS count FROM tasks WHERE planDate = :date GROUP BY status")
    suspend fun countTasksByStatus(date: String): List<TaskStatusCount>
}
