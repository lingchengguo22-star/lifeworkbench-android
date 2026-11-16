package com.example.lifeworkbench.data

import com.example.lifeworkbench.data.entities.StudyRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * StudyValidator 测试（V1.1 阶段 3.1）。
 * 纯 Kotlin 逻辑，不依赖 Room / Robolectric：日期、时长、内容三组规则全覆盖，
 * 含边界值（1 / 1440 分钟、1000 字符、闰年 2 月 29 日）与多错误并报。
 */
class StudyValidatorTest {

    private fun record(
        date: String = "2026-11-15",
        durationMinutes: Int = 60,
        content: String = "学习内容",
        knowledgePointId: Int? = null
    ) = StudyRecordEntity(
        date = date,
        durationMinutes = durationMinutes,
        content = content,
        knowledgePointId = knowledgePointId
    )

    @Test
    fun validate_validRecord_noErrors() {
        assertTrue(StudyValidator.validate(record()).isEmpty())
    }

    @Test
    fun validate_emptyContent_reportsEmptyContent() {
        assertEquals(
            listOf(StudyValidationError.EMPTY_CONTENT),
            StudyValidator.validate(record(content = ""))
        )
    }

    @Test
    fun validate_blankContent_reportsEmptyContent() {
        assertEquals(
            listOf(StudyValidationError.EMPTY_CONTENT),
            StudyValidator.validate(record(content = "   "))
        )
    }

    @Test
    fun validate_contentWithSurroundingSpaces_passes() {
        assertTrue(StudyValidator.validate(record(content = "  Kotlin 学习  ")).isEmpty())
    }

    @Test
    fun validate_contentAtLimit_passes() {
        assertTrue(StudyValidator.validate(record(content = "学".repeat(1000))).isEmpty())
    }

    @Test
    fun validate_contentOverLimit_reportsTooLong() {
        assertEquals(
            listOf(StudyValidationError.CONTENT_TOO_LONG),
            StudyValidator.validate(record(content = "学".repeat(1001)))
        )
    }

    @Test
    fun validate_durationZero_reportsNotPositive() {
        assertEquals(
            listOf(StudyValidationError.DURATION_NOT_POSITIVE),
            StudyValidator.validate(record(durationMinutes = 0))
        )
    }

    @Test
    fun validate_durationNegative_reportsNotPositive() {
        assertEquals(
            listOf(StudyValidationError.DURATION_NOT_POSITIVE),
            StudyValidator.validate(record(durationMinutes = -30))
        )
    }

    @Test
    fun validate_durationOneMinute_passes() {
        assertTrue(StudyValidator.validate(record(durationMinutes = 1)).isEmpty())
    }

    @Test
    fun validate_durationAtLimit_passes() {
        assertTrue(StudyValidator.validate(record(durationMinutes = 1440)).isEmpty())
    }

    @Test
    fun validate_durationOverLimit_reportsTooLong() {
        assertEquals(
            listOf(StudyValidationError.DURATION_TOO_LONG),
            StudyValidator.validate(record(durationMinutes = 1441))
        )
    }

    @Test
    fun validate_emptyDate_reportsInvalidDate() {
        assertEquals(
            listOf(StudyValidationError.INVALID_DATE),
            StudyValidator.validate(record(date = ""))
        )
    }

    @Test
    fun validate_wrongDateFormat_reportsInvalidDate() {
        assertEquals(
            listOf(StudyValidationError.INVALID_DATE),
            StudyValidator.validate(record(date = "2026/11/15"))
        )
    }

    @Test
    fun validate_impossibleMonth_reportsInvalidDate() {
        assertEquals(
            listOf(StudyValidationError.INVALID_DATE),
            StudyValidator.validate(record(date = "2026-13-01"))
        )
    }

    @Test
    fun validate_feb30_reportsInvalidDate() {
        assertEquals(
            listOf(StudyValidationError.INVALID_DATE),
            StudyValidator.validate(record(date = "2026-02-30"))
        )
    }

    @Test
    fun validate_feb29NonLeapYear_reportsInvalidDate() {
        assertEquals(
            listOf(StudyValidationError.INVALID_DATE),
            StudyValidator.validate(record(date = "2026-02-29"))
        )
    }

    @Test
    fun validate_feb29LeapYear_passes() {
        assertTrue(StudyValidator.validate(record(date = "2024-02-29")).isEmpty())
    }

    @Test
    fun validate_multipleErrors_reportsAll() {
        val errors = StudyValidator.validate(
            record(date = "2026-13-40", durationMinutes = 0, content = "  ")
        )
        assertEquals(
            listOf(
                StudyValidationError.EMPTY_CONTENT,
                StudyValidationError.INVALID_DATE,
                StudyValidationError.DURATION_NOT_POSITIVE
            ),
            errors
        )
    }

    @Test
    fun validate_knowledgePointId_notValidated() {
        // knowledgePointId 本阶段不校验、不转换：有值、无值均原样通过
        assertTrue(StudyValidator.validate(record(knowledgePointId = 7)).isEmpty())
        assertTrue(StudyValidator.validate(record(knowledgePointId = null)).isEmpty())
    }
}
