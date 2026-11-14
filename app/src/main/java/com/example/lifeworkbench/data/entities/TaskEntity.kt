package com.example.lifeworkbench.data.entities
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Insert
import androidx.room.Update
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
    val sourceId: Int? // 关联来源模块ID
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE planDate = :date")
    fun getTasksByDate(date: String): Flow<List<TaskEntity>>
    
    @Insert
    suspend fun insertTask(task: TaskEntity)

    // V1.1 阶段 0：状态修改持久化（仅增 DAO 方法，不改表结构、不升级数据库版本）
    @Update
    suspend fun updateTask(task: TaskEntity)
}
