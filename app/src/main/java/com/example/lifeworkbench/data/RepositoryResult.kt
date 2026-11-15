package com.example.lifeworkbench.data

/**
 * 五大业务模块 Repository 的统一结果类型（V1.1 阶段 3.0）。
 *
 * 参考 TaskRepository 的 TaskResult/TaskError 模式，但与任务类型解耦：
 * 各模块 Validator（3.1–3.5）以中文消息列表上报校验失败，见 [RepoError.ValidationFailed]。
 * 3.0 阶段无 Validator，只使用 Success / NotFound / Database。
 * 上层（ViewModel / 界面）据此区分“校验不通过”“找不到记录”“数据库异常”，
 * 不允许用 null 或空 catch 把失败吞掉。
 */
sealed class RepoResult<out T> {
    data class Success<T>(val data: T) : RepoResult<T>()
    data class Failure(val error: RepoError) : RepoResult<Nothing>()
}

/** 五大业务模块操作失败的原因。 */
sealed class RepoError {
    /** 数据校验不通过，messages 列出全部违规项的中文说明（3.0 暂不使用，3.1–3.5 各模块 Validator 上报）。 */
    data class ValidationFailed(val messages: List<String>) : RepoError()

    /** 按 ID 查不到记录。 */
    data class NotFound(val id: Int) : RepoError()

    /** 数据库异常（cause 保留原始异常，不吞掉）。 */
    data class Database(val cause: Throwable) : RepoError()
}
