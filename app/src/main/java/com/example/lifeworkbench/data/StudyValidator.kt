package com.example.lifeworkbench.data

import com.example.lifeworkbench.data.entities.StudyRecordEntity

/** 学习记录数据校验失败的原因（每项带中文说明，供界面直接展示）。 */
enum class StudyValidationError(val message: String) {
    INVALID_DATE("日期格式不正确，应为 YYYY-MM-DD 的真实日期"),
    DURATION_NOT_POSITIVE("学习时长必须大于 0 分钟"),
    DURATION_TOO_LONG("学习时长不能超过 1440 分钟"),
    EMPTY_CONTENT("学习内容不能为空"),
    CONTENT_TOO_LONG("学习内容最多 1000 个字符")
}

/**
 * 统一学习记录数据验证逻辑（V1.1 阶段 3.1）。
 * 校验不通过返回全部违规项，调用方不得把错误数据写入数据库。
 *
 * 规则（与 TaskValidator 保持同一尺度）：
 * - 日期：严格 YYYY-MM-DD 的真实日期，复用 [TaskValidator.isValidDate]，不另立一套日期规则；
 * - 时长：单条学习记录必须为合理正数，1~1440 分钟（一天最多 1440 分钟）；
 * - 内容：去首尾空格后不得为空，最多 1000 个字符；
 * - knowledgePointId：本阶段不校验、不转换、不建立知识点体系，原样透传（null 或已有值均保留）。
 * 新建与编辑共用同一套规则（学习记录没有任务那样的历史分类/历史状态兼容问题）。
 */
object StudyValidator {

    /** 单条学习记录允许的最长时长（分钟）：一天 1440 分钟。 */
    const val MAX_DURATION_MINUTES = 1440

    /** 学习内容允许的最长字符数（按 trim 后的内容计）。 */
    const val MAX_CONTENT_LENGTH = 1000

    fun validate(record: StudyRecordEntity): List<StudyValidationError> {
        val errors = mutableListOf<StudyValidationError>()
        val content = record.content.trim()
        if (content.isEmpty()) {
            errors += StudyValidationError.EMPTY_CONTENT
        } else if (content.length > MAX_CONTENT_LENGTH) {
            errors += StudyValidationError.CONTENT_TOO_LONG
        }
        if (!TaskValidator.isValidDate(record.date)) {
            errors += StudyValidationError.INVALID_DATE
        }
        if (record.durationMinutes <= 0) {
            errors += StudyValidationError.DURATION_NOT_POSITIVE
        } else if (record.durationMinutes > MAX_DURATION_MINUTES) {
            errors += StudyValidationError.DURATION_TOO_LONG
        }
        return errors
    }
}
