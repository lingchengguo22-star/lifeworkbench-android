package com.example.lifeworkbench.data

import com.example.lifeworkbench.data.entities.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first

/**
 * 任务操作的结果：成功带数据，失败带明确的错误原因。
 * 上层（ViewModel / 界面）据此区分“校验不通过”“找不到任务”“数据库异常”，
 * 不允许用 null 或空 catch 把失败吞掉。
 */
sealed class TaskResult<out T> {
    data class Success<T>(val data: T) : TaskResult<T>()
    data class Failure(val error: TaskError) : TaskResult<Nothing>()
}

/** 任务操作失败的原因。 */
sealed class TaskError {
    /** 数据校验不通过，errors 列出全部违规项。 */
    data class ValidationFailed(val errors: List<TaskValidationError>) : TaskError()

    /** 按 ID 查不到任务。 */
    data class NotFound(val id: Int) : TaskError()

    /** 数据库异常（cause 保留原始异常，不吞掉）。 */
    data class Database(val cause: Throwable) : TaskError()
}

/**
 * 任务数据访问统一入口（V1.1 阶段 1）。
 * UI 与 ViewModel 不直接操作 DAO，一律走 Repository。
 * 创建/更新前先跑 [TaskValidator]，校验不通过不写入数据库。
 * Room Flow 的响应式特征保持不变：按日期查询直接透传 DAO 的 Flow。
 */
class TaskRepository(private val dao: TaskDao) {

    /** 创建任务：先校验，通过后写入，返回新任务的行 ID。 */
    suspend fun createTask(task: TaskEntity): TaskResult<Long> {
        val errors = TaskValidator.validate(task)
        if (errors.isNotEmpty()) return TaskResult.Failure(TaskError.ValidationFailed(errors))
        return runCatching { TaskResult.Success(dao.insertTask(task)) }
            .getOrElse { TaskResult.Failure(TaskError.Database(it)) }
    }

    /** 按 ID 获取任务，查不到返回 NotFound。 */
    suspend fun getTaskById(id: Int): TaskResult<TaskEntity> {
        return runCatching {
            dao.getTaskById(id)?.let { TaskResult.Success(it) }
                ?: TaskResult.Failure(TaskError.NotFound(id))
        }.getOrElse { TaskResult.Failure(TaskError.Database(it)) }
    }

    /** 查询指定日期的任务（实时 Flow）。 */
    fun getTasksByDate(date: String): Flow<List<TaskEntity>> = dao.getTasksByDate(date)

    /** 查询日期范围内的任务（实时 Flow，起止日期均包含）。 */
    fun getTasksByDateRange(startDate: String, endDate: String): Flow<List<TaskEntity>> =
        dao.getTasksByDateRange(startDate, endDate)

    /** 查询指定日期、指定状态的任务（实时 Flow）。状态用统一定义传入，内部转中文数据库值。 */
    fun getTasksByDateAndStatus(date: String, status: TaskStatus): Flow<List<TaskEntity>> {
        val dbValue = status.toDbValue() ?: return emptyFlow()
        return dao.getTasksByDateAndStatus(date, dbValue)
    }

    /** 更新任务：先校验，通过后写入。 */
    suspend fun updateTask(task: TaskEntity): TaskResult<Unit> {
        val errors = TaskValidator.validate(task)
        if (errors.isNotEmpty()) return TaskResult.Failure(TaskError.ValidationFailed(errors))
        return runCatching {
            dao.updateTask(task)
            TaskResult.Success(Unit)
        }.getOrElse { TaskResult.Failure(TaskError.Database(it)) }
    }

    /** 删除任务（按实体）。 */
    suspend fun deleteTask(task: TaskEntity): TaskResult<Unit> {
        return runCatching {
            dao.deleteTask(task)
            TaskResult.Success(Unit)
        }.getOrElse { TaskResult.Failure(TaskError.Database(it)) }
    }

    /** 删除任务（按 ID）。 */
    suspend fun deleteTaskById(id: Int): TaskResult<Unit> {
        return runCatching {
            dao.deleteTaskById(id)
            TaskResult.Success(Unit)
        }.getOrElse { TaskResult.Failure(TaskError.Database(it)) }
    }

    /** 获取指定日期的今日统计（总数、已完成、已取消、未知、完成率）。 */
    suspend fun getTodayStats(date: String): TaskResult<TaskCompletionStats> {
        return runCatching {
            TaskResult.Success(dao.getTasksByDate(date).first().completionStats())
        }.getOrElse { TaskResult.Failure(TaskError.Database(it)) }
    }

    /**
     * 获取指定日期各状态的数量，按 [TaskStatus] 分组返回。
     * 数据库里的未知值映射为 Unknown（原值保留），不丢弃、不转换。
     */
    suspend fun getStatusCounts(date: String): TaskResult<Map<TaskStatus, Int>> {
        return runCatching {
            val counts = dao.countTasksByStatus(date)
                .groupBy({ TaskStatus.fromDbValue(it.status) }, { it.count })
                .mapValues { (_, values) -> values.sum() }
            TaskResult.Success(counts)
        }.getOrElse { TaskResult.Failure(TaskError.Database(it)) }
    }
}
