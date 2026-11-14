package com.example.lifeworkbench.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.lifeworkbench.data.entities.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Room 内存数据库测试（V1.1 阶段 0）。
 * 在真实 Android 项目环境（Robolectric + Room 内存库）中验证：
 * 四种中文状态可正常保存读取、未知状态原样保存读取、状态修改可持久化、
 * 状态统计与数据库实际记录一致。
 * 数据库不改：使用内存库，不碰真实数据库文件，不改表结构，不升级数据库版本。
 */
@RunWith(RobolectricTestRunner::class)
class TaskDaoTest {

    private lateinit var db: AppDatabase

    private fun task(title: String, status: String, date: String = "2026-11-14") = TaskEntity(
        title = title,
        description = null,
        planDate = date,
        category = "学习",
        priority = 1,
        status = status,
        sourceId = null
    )

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun knownChineseStatuses_saveAndReadBack() = runBlocking {
        val dao = db.taskDao()
        // 直接用中文字符串写入（与其它通过的测试保持同一写法），避免在 DAO 测试里混入映射层逻辑
        val statuses = listOf("待开始", "进行中", "已完成", "已取消")
        for (value in statuses) {
            dao.insertTask(task("任务-$value", value))
        }
        val saved = dao.getTasksByDate("2026-11-14").first()
        assertEquals(4, saved.size)
        // 数据库里读回来的必须是中文原值，且能映射回英文常量（用集合比较，不依赖字符串排序）
        assertEquals(
            statuses.toSet(),
            saved.map { it.status }.toSet()
        )
        assertEquals(
            TaskStatus.KNOWN.toSet(),
            saved.map { it.taskStatus() }.toSet()
        )
    }

    @Test
    fun unknownStatus_saveAndReadBackUnchanged() = runBlocking {
        val dao = db.taskDao()
        dao.insertTask(task("旧任务", "已删除"))
        val saved = dao.getTasksByDate("2026-11-14").first()
        assertEquals(1, saved.size)
        assertEquals("已删除", saved[0].status)
        val status = saved[0].taskStatus()
        assertTrue(status is TaskStatus.Unknown)
        assertEquals("未知状态（已删除）", status.displayName)
    }

    @Test
    fun statusUpdate_persists() = runBlocking {
        val dao = db.taskDao()
        dao.insertTask(task("改状态", "待开始"))
        val saved = dao.getTasksByDate("2026-11-14").first()[0]
        dao.updateTask(saved.withStatus(TaskStatus.Completed))
        val updated = dao.getTasksByDate("2026-11-14").first()[0]
        assertEquals("已完成", updated.status)
        assertEquals(TaskStatus.Completed, updated.taskStatus())
    }

    @Test
    fun stats_matchDatabaseRecords() = runBlocking {
        val dao = db.taskDao()
        dao.insertTask(task("做完1", "已完成"))
        dao.insertTask(task("取消1", "已取消"))
        repeat(8) { i -> dao.insertTask(task("未知$i", "已删除")) }
        val saved = dao.getTasksByDate("2026-11-14").first()
        val stats = saved.completionStats()
        assertEquals(10, stats.total)
        assertEquals(1, stats.completed)
        assertEquals(1, stats.cancelled)
        assertEquals(8, stats.unknown)
        // 修订公式：1 / (10 - 1) × 100% = 11.11%，见设计文档第 7 节的 discrepancy 说明
        assertEquals(100.0 / 9.0, stats.completionRate, 0.000001)
    }

    // ---- V1.1 阶段 1 新增：完善后的 TaskDao 覆盖 ----

    @Test
    fun getTaskById_returnsInsertedTask() = runBlocking {
        val dao = db.taskDao()
        val id = dao.insertTask(task("按ID查", "待开始").copy(startMinute = 540, endMinute = 600))
        assertTrue(id > 0)
        val found = dao.getTaskById(id.toInt())
        assertEquals("按ID查", found?.title)
        assertEquals("待开始", found?.status)
        assertEquals(540, found?.startMinute)
        assertEquals(600, found?.endMinute)
        assertEquals(null, dao.getTaskById(999999))
    }

    @Test
    fun getTasksByDateRange_returnsOnlyInRange() = runBlocking {
        val dao = db.taskDao()
        dao.insertTask(task("11-13", "待开始", "2026-11-13"))
        dao.insertTask(task("11-14", "待开始", "2026-11-14"))
        dao.insertTask(task("11-15", "待开始", "2026-11-15"))
        dao.insertTask(task("11-16", "待开始", "2026-11-16"))
        val ranged = dao.getTasksByDateRange("2026-11-14", "2026-11-15").first()
        assertEquals(listOf("11-14", "11-15"), ranged.map { it.title })
        // 起止日期均包含在内；范围外不返回
        assertEquals(0, dao.getTasksByDateRange("2026-11-17", "2026-11-18").first().size)
    }

    @Test
    fun getTasksByDateAndStatus_filtersByStatus() = runBlocking {
        val dao = db.taskDao()
        dao.insertTask(task("待办", "待开始"))
        dao.insertTask(task("做完", "已完成"))
        dao.insertTask(task("取消", "已取消"))
        // 状态值必须用 TaskStatus 统一定义转换，测试里也不手写中文
        val done = dao.getTasksByDateAndStatus("2026-11-14", TaskStatus.Completed.toDbValue()!!).first()
        assertEquals(listOf("做完"), done.map { it.title })
        val pending = dao.getTasksByDateAndStatus("2026-11-14", TaskStatus.Pending.toDbValue()!!).first()
        assertEquals(listOf("待办"), pending.map { it.title })
    }

    @Test
    fun deleteTask_removesRow() = runBlocking {
        val dao = db.taskDao()
        val id = dao.insertTask(task("要删", "待开始"))
        dao.insertTask(task("保留", "待开始"))
        val saved = dao.getTaskById(id.toInt())!!
        dao.deleteTask(saved)
        assertEquals(null, dao.getTaskById(id.toInt()))
        assertEquals(listOf("保留"), dao.getTasksByDate("2026-11-14").first().map { it.title })
    }

    @Test
    fun deleteTaskById_removesRow() = runBlocking {
        val dao = db.taskDao()
        val id = dao.insertTask(task("按ID删", "待开始"))
        dao.deleteTaskById(id.toInt())
        assertEquals(null, dao.getTaskById(id.toInt()))
        assertEquals(0, dao.countTasksByDate("2026-11-14"))
    }

    @Test
    fun countTasksByDate_countsOnlyThatDate() = runBlocking {
        val dao = db.taskDao()
        dao.insertTask(task("A", "待开始", "2026-11-14"))
        dao.insertTask(task("B", "待开始", "2026-11-14"))
        dao.insertTask(task("C", "待开始", "2026-11-15"))
        assertEquals(2, dao.countTasksByDate("2026-11-14"))
        assertEquals(1, dao.countTasksByDate("2026-11-15"))
        assertEquals(0, dao.countTasksByDate("2026-11-16"))
    }

    @Test
    fun countTasksByStatus_includesUnknownValues() = runBlocking {
        val dao = db.taskDao()
        dao.insertTask(task("待办", "待开始"))
        dao.insertTask(task("做完", "已完成"))
        dao.insertTask(task("未知", "已删除"))
        dao.insertTask(task("取消", "已取消"))
        val counts = dao.countTasksByStatus("2026-11-14")
            .associate { it.status to it.count }
        assertEquals(1, counts["待开始"])
        assertEquals(1, counts["已完成"])
        assertEquals(1, counts["已删除"])
        assertEquals(1, counts["已取消"])
        // 未知值不被丢弃：按原始值分组后仍能映射回 Unknown
        val unknown = counts.keys.map { TaskStatus.fromDbValue(it) }.filterIsInstance<TaskStatus.Unknown>()
        assertEquals(1, unknown.size)
        assertEquals("已删除", unknown[0].rawValue)
    }

    @Test
    fun getTasksByDate_ordersTimedFirstThenUntimed() = runBlocking {
        val dao = db.taskDao()
        dao.insertTask(task("无时间", "待开始").copy(startMinute = null))
        dao.insertTask(task("下午", "待开始").copy(startMinute = 840, endMinute = 900))
        dao.insertTask(task("上午", "待开始").copy(startMinute = 540, endMinute = 600))
        val ordered = dao.getTasksByDate("2026-11-14").first().map { it.title }
        assertEquals(listOf("上午", "下午", "无时间"), ordered)
    }

    @Test
    fun getTasksByDate_flowReflectsInserts() = runBlocking {
        val dao = db.taskDao()
        assertEquals(0, dao.getTasksByDate("2026-11-14").first().size)
        dao.insertTask(task("新任务", "待开始"))
        val after = dao.getTasksByDate("2026-11-14").first()
        assertEquals(listOf("新任务"), after.map { it.title })
    }

    // ---- V1.1 阶段 1 最终验收前修复：update/delete 返回实际影响行数 ----

    @Test
    fun updateTask_returnsAffectedRowCount() = runBlocking {
        val dao = db.taskDao()
        val id = dao.insertTask(task("改", "待开始"))
        val saved = dao.getTaskById(id.toInt())!!
        assertEquals(1, dao.updateTask(saved.copy(title = "改后")))
        assertEquals("改后", dao.getTaskById(id.toInt())?.title)
        // 不存在的任务：影响 0 行，不抛异常、不假造成功
        assertEquals(0, dao.updateTask(saved.copy(id = 999999, title = "不存在")))
    }

    @Test
    fun deleteTask_returnsAffectedRowCount() = runBlocking {
        val dao = db.taskDao()
        val id = dao.insertTask(task("删", "待开始"))
        val saved = dao.getTaskById(id.toInt())!!
        assertEquals(1, dao.deleteTask(saved))
        // 已删过的同一实体再删：影响 0 行
        assertEquals(0, dao.deleteTask(saved))
    }

    @Test
    fun deleteTaskById_returnsAffectedRowCount() = runBlocking {
        val dao = db.taskDao()
        val id = dao.insertTask(task("按ID删", "待开始"))
        assertEquals(1, dao.deleteTaskById(id.toInt()))
        assertEquals(0, dao.deleteTaskById(id.toInt()))
        assertEquals(0, dao.deleteTaskById(999999))
    }
}
