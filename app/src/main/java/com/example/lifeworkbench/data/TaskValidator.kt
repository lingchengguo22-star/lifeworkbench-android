package com.example.lifeworkbench.data

import com.example.lifeworkbench.data.entities.TaskEntity

/** 任务数据校验失败的原因（每项带中文说明，供界面直接展示）。 */
enum class TaskValidationError(val message: String) {
    EMPTY_TITLE("标题不能为空"),
    TITLE_TOO_LONG("标题最多 100 个字符"),
    DESCRIPTION_TOO_LONG("描述最多 1000 个字符"),
    INVALID_DATE("日期格式不正确，应为 YYYY-MM-DD 的真实日期"),
    EMPTY_CATEGORY("分类不能为空"),
    CATEGORY_TOO_LONG("分类最多 20 个字符"),
    INVALID_PRIORITY("优先级只能为 1、2、3"),
    TIME_PARTIAL("开始时间和结束时间必须同时为空或同时存在"),
    TIME_OUT_OF_RANGE("开始/结束时间必须在 0~1439 分钟之间"),
    END_NOT_AFTER_START("结束时间必须大于开始时间"),
    UNKNOWN_STATUS("不能用未知状态创建或修改任务，请选择一个已知状态")
}

/**
 * 统一任务数据验证逻辑（V1.1 阶段 1）。
 * 校验不通过返回全部违规项，调用方不得把错误数据写入数据库。
 * 注意：分类为开放值——已知 5 类（学习、健身、饮食、游戏、睡眠）之外的值允许保留，
 * 只校验非空与长度，不擅自破坏已有分类兼容性。
 * 未知状态不做任何自动转换：新建/修改时遇到未知状态直接报 UNKNOWN_STATUS。
 */
object TaskValidator {

    /** 已定义的分类（TaskEntity 注释）；“等”字表示开放，之外的值同样允许。 */
    val KNOWN_CATEGORIES = setOf("学习", "健身", "饮食", "游戏", "睡眠")

    fun validate(task: TaskEntity): List<TaskValidationError> {
        val errors = mutableListOf<TaskValidationError>()
        val title = task.title.trim()
        if (title.isEmpty()) {
            errors += TaskValidationError.EMPTY_TITLE
        } else if (title.length > 100) {
            errors += TaskValidationError.TITLE_TOO_LONG
        }
        if ((task.description?.length ?: 0) > 1000) {
            errors += TaskValidationError.DESCRIPTION_TOO_LONG
        }
        if (!isValidDate(task.planDate)) {
            errors += TaskValidationError.INVALID_DATE
        }
        val category = task.category.trim()
        if (category.isEmpty()) {
            errors += TaskValidationError.EMPTY_CATEGORY
        } else if (category.length > 20) {
            errors += TaskValidationError.CATEGORY_TOO_LONG
        }
        if (task.priority !in 1..3) {
            errors += TaskValidationError.INVALID_PRIORITY
        }
        val start = task.startMinute
        val end = task.endMinute
        if ((start == null) != (end == null)) {
            errors += TaskValidationError.TIME_PARTIAL
        }
        if (start != null && (start < 0 || start > 1439)) {
            errors += TaskValidationError.TIME_OUT_OF_RANGE
        }
        if (end != null && (end < 0 || end > 1439)) {
            errors += TaskValidationError.TIME_OUT_OF_RANGE
        }
        if (start != null && end != null && end <= start) {
            errors += TaskValidationError.END_NOT_AFTER_START
        }
        if (task.taskStatus() is TaskStatus.Unknown) {
            errors += TaskValidationError.UNKNOWN_STATUS
        }
        return errors
    }

    /**
     * 日期合法性：严格 YYYY-MM-DD（4-2-2 位数字），月份 1~12，
     * 天数符合当月实际天数（含闰年 2 月 29 日）。不依赖 java.time（minSdk 24）。
     */
    fun isValidDate(value: String): Boolean {
        if (!value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return false
        val year = value.substring(0, 4).toInt()
        val month = value.substring(5, 7).toInt()
        val day = value.substring(8, 10).toInt()
        if (month < 1 || month > 12) return false
        if (day < 1) return false
        val leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
        val daysInMonth = when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (leap) 29 else 28
            else -> return false
        }
        return day <= daysInMonth
    }
}
