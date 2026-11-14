package com.example.lifeworkbench.data

import com.example.lifeworkbench.data.entities.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TaskStatus 状态映射单元测试（V1.1 阶段 0）。
 * 覆盖：4 种已知状态、未知状态、空值（null / 空串）、非法值（英文、错别字、带空格），
 * 以及实体读取 / 修改 / 统计的旧数据兼容性。
 * 数据库不改：这些测试只验证内存中的映射逻辑，不碰 Room、不改表结构、不升级数据库版本。
 */
class TaskStatusTest {

    private fun task(status: String) = TaskEntity(
        title = "测试任务",
        description = null,
        planDate = "2026-11-14",
        category = "学习",
        priority = 1,
        status = status,
        sourceId = null
    )

    // ---------- 1. 四种已知状态 ----------

    @Test
    fun knownStatuses_mapToEnglishConstants() {
        assertEquals(TaskStatus.Pending, TaskStatus.fromDbValue("待开始"))
        assertEquals(TaskStatus.InProgress, TaskStatus.fromDbValue("进行中"))
        assertEquals(TaskStatus.Completed, TaskStatus.fromDbValue("已完成"))
        assertEquals(TaskStatus.Cancelled, TaskStatus.fromDbValue("已取消"))
    }

    @Test
    fun knownStatuses_dbValuesStayChinese() {
        // 数据库值必须是中文，绝不能改成英文
        assertEquals("待开始", TaskStatus.Pending.toDbValue())
        assertEquals("进行中", TaskStatus.InProgress.toDbValue())
        assertEquals("已完成", TaskStatus.Completed.toDbValue())
        assertEquals("已取消", TaskStatus.Cancelled.toDbValue())
        assertEquals(listOf("待开始", "进行中", "已完成", "已取消"), TaskStatus.KNOWN.map { it.toDbValue() })
        assertTrue(TaskStatus.KNOWN.all { it.isKnown })
    }

    @Test
    fun knownStatuses_displayNameEqualsDbValue() {
        assertEquals("待开始", TaskStatus.Pending.displayName)
        assertEquals("进行中", TaskStatus.InProgress.displayName)
        assertEquals("已完成", TaskStatus.Completed.displayName)
        assertEquals("已取消", TaskStatus.Cancelled.displayName)
    }

    @Test
    fun knownStatuses_roundTrip() {
        for (status in TaskStatus.KNOWN) {
            assertEquals(status, TaskStatus.fromDbValue(status.toDbValue()))
        }
    }

    // ---------- 2. 未知状态：绝不静默转换 ----------

    @Test
    fun unknownStatus_preservesRawValue() {
        val status = TaskStatus.fromDbValue("已删除")
        assertTrue(status is TaskStatus.Unknown)
        assertFalse(status.isKnown)
        assertEquals("已删除", (status as TaskStatus.Unknown).rawValue)
        assertEquals("已删除", status.toDbValue())
        assertEquals("未知状态（已删除）", status.displayName)
    }

    @Test
    fun unknownStatus_englishValueIsNotMapped() {
        // 英文状态绝不能被“认出来”，必须当作未知
        val status = TaskStatus.fromDbValue("DONE")
        assertTrue(status is TaskStatus.Unknown)
        assertEquals("DONE", status.toDbValue())
        assertEquals("未知状态（DONE）", status.displayName)
    }

    // ---------- 3. 空值：null 与空串 ----------

    @Test
    fun nullValue_isUnknownNotPending() {
        // null 绝不能默认成待开始。
        // 说明：TaskEntity.status 是 Kotlin 非空 String，Room 生成的列是 TEXT NOT NULL，
        // 因此 Room 管理的数据库里读不到 null；本用例只验证纯映射函数的防御分支，
        // 覆盖“数据库被人用原始 SQL 改坏”这种极端情况，与非空约束不存在矛盾。
        val status = TaskStatus.fromDbValue(null)
        assertTrue(status is TaskStatus.Unknown)
        assertEquals(null, (status as TaskStatus.Unknown).rawValue)
        assertEquals(null, status.toDbValue())
        assertEquals("未知状态（空）", status.displayName)
    }

    @Test
    fun emptyString_isUnknownNotPending() {
        val status = TaskStatus.fromDbValue("")
        assertTrue(status is TaskStatus.Unknown)
        assertEquals("", status.toDbValue())
        assertEquals("未知状态（空）", status.displayName)
    }

    // ---------- 4. 非法值：不做归一化 ----------

    @Test
    fun whitespacePaddedValue_isUnknown() {
        // 带空格不做 trim，必须当作未知，不能“修”成已知状态
        val status = TaskStatus.fromDbValue(" 待开始")
        assertTrue(status is TaskStatus.Unknown)
        assertEquals(" 待开始", status.toDbValue())
    }

    @Test
    fun partialValue_isUnknown() {
        // “完成”不是“已完成”，不能模糊匹配
        assertTrue(TaskStatus.fromDbValue("完成") is TaskStatus.Unknown)
        assertTrue(TaskStatus.fromDbValue("取消") is TaskStatus.Unknown)
    }

    // ---------- 5. SQL 与实际中文存储值一致 ----------

    @Test
    fun sqlInClause_usesActualChineseDbValues() {
        assertEquals(
            "status IN ('待开始', '进行中', '已完成', '已取消')",
            TaskStatus.knownStatusSqlInClause()
        )
        assertEquals(
            "t.status IN ('待开始', '进行中', '已完成', '已取消')",
            TaskStatus.knownStatusSqlInClause("t.status")
        )
    }

    // ---------- 6. 旧数据兼容：实体读取、修改、统计 ----------

    @Test
    fun entity_readsHistoricalChineseStatus() {
        assertEquals(TaskStatus.Completed, task("已完成").taskStatus())
        assertEquals(TaskStatus.Cancelled, task("已取消").taskStatus())
        assertEquals(TaskStatus.Pending, task("待开始").taskStatus())
    }

    @Test
    fun entity_unknownStatusRoundTripsUnchanged() {
        // 历史未知状态读出来再写回去，原值必须一字不变
        val original = task("已删除")
        val status = original.taskStatus()
        assertTrue(status is TaskStatus.Unknown)
        assertEquals("已删除", original.withStatus(status).status)
    }

    @Test
    fun entity_withStatusChangesDbValue() {
        val updated = task("待开始").withStatus(TaskStatus.Completed)
        assertEquals("已完成", updated.status)
        assertEquals("待开始", task("待开始").status) // 原实体不变
    }

    @Test
    fun stats_excludeOnlyCancelledFromRate() {
        val tasks = listOf(
            task("已完成"), task("已完成"), task("待开始"),
            task("已取消"), task("已删除") // 未知状态：单独计数，但计入分母
        )
        val stats = tasks.completionStats()
        assertEquals(5, stats.total)
        assertEquals(2, stats.completed)
        assertEquals(1, stats.cancelled)
        assertEquals(1, stats.unknown)
        // 完成率 = 2 / (5 - 1 已取消) × 100% = 50%
        assertEquals(50.0, stats.completionRate, 0.000001)
    }

    @Test
    fun stats_emptyListRateIsZero() {
        val stats = emptyList<TaskEntity>().completionStats()
        assertEquals(0, stats.total)
        assertEquals(0.0, stats.completionRate, 0.0)
    }

    @Test
    fun stats_allCancelledRateIsZero() {
        val stats = listOf(task("已取消"), task("已取消")).completionStats()
        assertEquals(0.0, stats.completionRate, 0.0)
    }

    // ---------- 7. 修订补充：完成率百分比口径 ----------

    @Test
    fun stats_oneCompletedNineUnknown_rateIs10Percent() {
        val tasks = listOf(task("已完成")) + List(9) { task("已删除") }
        val stats = tasks.completionStats()
        assertEquals(10, stats.total)
        assertEquals(9, stats.unknown)
        // 完成率 = 1 / (10 - 0) × 100% = 10%
        assertEquals(10.0, stats.completionRate, 0.000001)
    }

    @Test
    fun stats_oneCompletedOneCancelledEightUnknown_rateIs11Point11Percent() {
        val tasks = listOf(task("已完成"), task("已取消")) + List(8) { task("已删除") }
        val stats = tasks.completionStats()
        assertEquals(10, stats.total)
        assertEquals(1, stats.cancelled)
        assertEquals(8, stats.unknown)
        // 按修订公式：完成率 = 1 / (10 - 1 已取消) × 100% = 11.11%（原始数值，不四舍五入）
        assertEquals(100.0 / 9.0, stats.completionRate, 0.000001)
    }

    @Test
    fun stats_allUnknown_rateIsZero() {
        val tasks = List(10) { task("已删除") }
        val stats = tasks.completionStats()
        assertEquals(10, stats.total)
        assertEquals(10, stats.unknown)
        assertEquals(0.0, stats.completionRate, 0.0)
    }

    @Test
    fun stats_emptyList_rateIsZeroPercent() {
        val stats = emptyList<TaskEntity>().completionStats()
        assertEquals(0, stats.total)
        assertEquals(0.0, stats.completionRate, 0.0)
    }
}
