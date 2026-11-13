package com.example.lifeworkbench.data

import com.example.lifeworkbench.data.entities.TaskEntity

/** 读取任务的状态：把数据库里的中文字符串转成 [TaskStatus]，未知值进入 [TaskStatus.Unknown]。 */
fun TaskEntity.taskStatus(): TaskStatus = TaskStatus.fromDbValue(status)

/**
 * 修改任务的状态：返回一个 status 已替换的新实体，原实体不变。
 * 传入 [TaskStatus.Unknown] 时写回读到的原值；若原值为 null（数据库列为 NOT NULL 时
 * 正常情况下不会出现），则保留原 status，不把 null 写进非空列。
 */
fun TaskEntity.withStatus(newStatus: TaskStatus): TaskEntity =
    copy(status = newStatus.toDbValue() ?: status)

/** 今日任务统计口径：完成率 = 已完成 /（总数 - 已取消 - 未知状态），未知状态单独计数，不参与完成率。 */
data class TaskCompletionStats(
    val total: Int,
    val completed: Int,
    val cancelled: Int,
    val unknown: Int,
    val completionRate: Double
)

/** 对一批任务做统计。空列表或分母为 0 时完成率为 0.0，不抛异常。 */
fun List<TaskEntity>.completionStats(): TaskCompletionStats {
    val statuses = map { it.taskStatus() }
    val completed = statuses.count { it == TaskStatus.Completed }
    val cancelled = statuses.count { it == TaskStatus.Cancelled }
    val unknown = statuses.count { it is TaskStatus.Unknown }
    val denominator = size - cancelled - unknown
    val rate = if (denominator > 0) completed.toDouble() / denominator else 0.0
    return TaskCompletionStats(
        total = size,
        completed = completed,
        cancelled = cancelled,
        unknown = unknown,
        completionRate = rate
    )
}
