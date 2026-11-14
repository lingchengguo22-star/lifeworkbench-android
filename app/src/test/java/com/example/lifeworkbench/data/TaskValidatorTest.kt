package com.example.lifeworkbench.data

import com.example.lifeworkbench.data.entities.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 任务数据验证测试（V1.1 阶段 1）。
 * 覆盖合法输入、非法输入、边界值和空值；纯 JUnit，不依赖数据库。
 */
class TaskValidatorTest {

    private fun task(
        title: String = "学习任务",
        description: String? = null,
        date: String = "2026-11-14",
        category: String = "学习",
        priority: Int = 1,
        status: String = "待开始",
        startMinute: Int? = null,
        endMinute: Int? = null
    ) = TaskEntity(
        title = title,
        description = description,
        planDate = date,
        category = category,
        priority = priority,
        status = status,
        sourceId = null,
        startMinute = startMinute,
        endMinute = endMinute
    )

    @Test
    fun validTask_hasNoErrors() {
        assertTrue(TaskValidator.validate(task()).isEmpty())
        // 带时间的合法任务
        assertTrue(TaskValidator.validate(task(startMinute = 540, endMinute = 600)).isEmpty())
    }

    @Test
    fun category_sixLegalValues_pass() {
        // 修复指令规定的 6 类合法值：新建任务只允许这些分类
        for (value in listOf("学习", "健身", "饮食", "游戏", "睡眠", "其他")) {
            assertTrue("分类 $value 应合法", TaskValidator.validate(task(category = value)).isEmpty())
        }
    }

    @Test
    fun category_unknownValue_rejectedOnCreate() {
        // 新建任务用 6 类之外的值：拒绝，不做自动替换
        assertEquals(
            listOf(TaskValidationError.INVALID_CATEGORY),
            TaskValidator.validate(task(category = "工作"))
        )
        assertEquals(
            listOf(TaskValidationError.INVALID_CATEGORY),
            TaskValidator.validate(task(category = "自定义"))
        )
    }

    @Test
    fun category_validateForUpdate_unchangedUnknown_passes() {
        // 编辑历史任务、未改分类：未知分类原样保留，不阻止保存
        assertTrue(
            TaskValidator.validateForUpdate(task(category = "工作"), "工作", "待开始").isEmpty()
        )
        // 超长历史分类同样不阻止保存（仅因分类未知/超长而拦截编辑是被禁止的）
        val longCategory = "a".repeat(25)
        assertTrue(
            TaskValidator.validateForUpdate(task(category = longCategory), longCategory, "待开始").isEmpty()
        )
    }

    @Test
    fun category_validateForUpdate_changedToIllegal_rejected() {
        // 主动把分类改成非法值：拒绝
        assertEquals(
            listOf(TaskValidationError.INVALID_CATEGORY),
            TaskValidator.validateForUpdate(task(category = "工作"), "学习", "待开始")
        )
        assertEquals(
            listOf(TaskValidationError.EMPTY_CATEGORY),
            TaskValidator.validateForUpdate(task(category = "  "), "学习", "待开始")
        )
        assertEquals(
            listOf(TaskValidationError.CATEGORY_TOO_LONG),
            TaskValidator.validateForUpdate(task(category = "a".repeat(21)), "学习", "待开始")
        )
    }

    @Test
    fun category_validateForUpdate_changedToLegal_passes() {
        // 把历史未知分类改成合法值：允许；合法值之间互改也允许
        assertTrue(
            TaskValidator.validateForUpdate(task(category = "健身"), "工作", "待开始").isEmpty()
        )
        assertTrue(
            TaskValidator.validateForUpdate(task(category = "其他"), "学习", "待开始").isEmpty()
        )
    }

    // ---- V1.1 阶段 1 最终修复：历史未知状态编辑兼容 ----

    @Test
    fun validateForUpdate_unchangedUnknownStatus_passes() {
        // 历史未知状态未改：允许保留，不自动转换
        assertTrue(
            TaskValidator.validateForUpdate(task(status = "已删除"), "学习", "已删除").isEmpty()
        )
    }

    @Test
    fun validateForUpdate_changedUnknownToLegal_passes() {
        // 未知状态主动改成四种合法状态之一：均允许
        for (legal in listOf("待开始", "进行中", "已完成", "已取消")) {
            assertTrue(
                "改成 $legal 应合法",
                TaskValidator.validateForUpdate(task(status = legal), "学习", "已删除").isEmpty()
            )
        }
    }

    @Test
    fun validateForUpdate_changedUnknownToIllegal_rejected() {
        // 未知状态主动改成另一个非法状态：拒绝
        assertEquals(
            listOf(TaskValidationError.UNKNOWN_STATUS),
            TaskValidator.validateForUpdate(task(status = "作废"), "学习", "已删除")
        )
    }

    @Test
    fun validateForUpdate_unchangedKnownStatus_passes() {
        // 已知状态未改：允许（回归）
        assertTrue(
            TaskValidator.validateForUpdate(task(status = "待开始"), "学习", "待开始").isEmpty()
        )
    }

    @Test
    fun title_emptyOrBlank_isError() {
        assertEquals(
            listOf(TaskValidationError.EMPTY_TITLE),
            TaskValidator.validate(task(title = ""))
        )
        assertEquals(
            listOf(TaskValidationError.EMPTY_TITLE),
            TaskValidator.validate(task(title = "   "))
        )
    }

    @Test
    fun title_lengthBoundaries() {
        assertTrue(TaskValidator.validate(task(title = "a".repeat(100))).isEmpty())
        assertEquals(
            listOf(TaskValidationError.TITLE_TOO_LONG),
            TaskValidator.validate(task(title = "a".repeat(101)))
        )
        // 首尾空格不计入：100 个有效字符加两端空格仍合法
        assertTrue(TaskValidator.validate(task(title = "  " + "a".repeat(100) + "  ")).isEmpty())
    }

    @Test
    fun description_lengthBoundaries() {
        assertTrue(TaskValidator.validate(task(description = "a".repeat(1000))).isEmpty())
        assertEquals(
            listOf(TaskValidationError.DESCRIPTION_TOO_LONG),
            TaskValidator.validate(task(description = "a".repeat(1001)))
        )
        assertTrue(TaskValidator.validate(task(description = null)).isEmpty())
    }

    @Test
    fun date_invalidValues() {
        val invalid = listOf(
            "", "2026-11-14 ", "2026-1-5", "2026/11/14",
            "2026-13-01", "2026-00-10", "2026-11-31", "2026-02-29", // 2026 不是闰年
            "2026-11-00", "2026-04-31", "abcd-11-14"
        )
        for (value in invalid) {
            assertEquals("日期 $value 应判非法", listOf(TaskValidationError.INVALID_DATE), TaskValidator.validate(task(date = value)))
        }
    }

    @Test
    fun date_validValues() {
        val valid = listOf("2026-11-14", "2024-02-29", "2000-02-29", "1900-02-28", "2026-12-31")
        for (value in valid) {
            assertTrue("日期 $value 应合法", TaskValidator.validate(task(date = value)).isEmpty())
        }
        // 1900 能被 100 整除但不能被 400 整除，不是闰年
        assertEquals(
            listOf(TaskValidationError.INVALID_DATE),
            TaskValidator.validate(task(date = "1900-02-29"))
        )
    }

    @Test
    fun category_emptyOrTooLong_isError() {
        assertEquals(
            listOf(TaskValidationError.EMPTY_CATEGORY),
            TaskValidator.validate(task(category = "  "))
        )
        assertEquals(
            listOf(TaskValidationError.CATEGORY_TOO_LONG),
            TaskValidator.validate(task(category = "a".repeat(21)))
        )
        // 20 个字符不超长，但不在 6 类合法值内，新建时仍拒绝
        assertEquals(
            listOf(TaskValidationError.INVALID_CATEGORY),
            TaskValidator.validate(task(category = "a".repeat(20)))
        )
    }

    @Test
    fun priority_only1To3() {
        for (p in listOf(1, 2, 3)) {
            assertTrue("优先级 $p 应合法", TaskValidator.validate(task(priority = p)).isEmpty())
        }
        for (p in listOf(0, 4, -1, 100)) {
            assertEquals(
                "优先级 $p 应判非法",
                listOf(TaskValidationError.INVALID_PRIORITY),
                TaskValidator.validate(task(priority = p))
            )
        }
    }

    @Test
    fun time_partial_isError() {
        assertEquals(
            listOf(TaskValidationError.TIME_PARTIAL),
            TaskValidator.validate(task(startMinute = 540))
        )
        assertEquals(
            listOf(TaskValidationError.TIME_PARTIAL),
            TaskValidator.validate(task(endMinute = 600))
        )
        assertTrue(TaskValidator.validate(task()).isEmpty())
    }

    @Test
    fun time_outOfRange_isError() {
        assertEquals(
            listOf(TaskValidationError.TIME_OUT_OF_RANGE),
            TaskValidator.validate(task(startMinute = -1, endMinute = 600))
        )
        assertEquals(
            listOf(TaskValidationError.TIME_OUT_OF_RANGE),
            TaskValidator.validate(task(startMinute = 540, endMinute = 1440))
        )
        assertTrue(TaskValidator.validate(task(startMinute = 0, endMinute = 1439)).isEmpty())
    }

    @Test
    fun time_endMustBeAfterStart() {
        assertEquals(
            listOf(TaskValidationError.END_NOT_AFTER_START),
            TaskValidator.validate(task(startMinute = 600, endMinute = 600))
        )
        assertEquals(
            listOf(TaskValidationError.END_NOT_AFTER_START),
            TaskValidator.validate(task(startMinute = 600, endMinute = 540))
        )
        assertTrue(TaskValidator.validate(task(startMinute = 540, endMinute = 541)).isEmpty())
    }

    @Test
    fun unknownStatus_isErrorNotConverted() {
        val errors = TaskValidator.validate(task(status = "已删除"))
        assertEquals(listOf(TaskValidationError.UNKNOWN_STATUS), errors)
        // 未知状态不被自动转为已知状态：校验失败，不产生其它“已修正”的结果
        assertTrue(errors.none { it == TaskValidationError.EMPTY_TITLE })
    }

    @Test
    fun multipleErrors_allReturned() {
        val errors = TaskValidator.validate(
            task(title = "", date = "2026-13-01", priority = 9)
        )
        assertEquals(
            listOf(
                TaskValidationError.EMPTY_TITLE,
                TaskValidationError.INVALID_DATE,
                TaskValidationError.INVALID_PRIORITY
            ),
            errors
        )
    }
}
