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
        for (status in TaskStatus.KNOWN) {
            dao.insertTask(task("任务-${status.dbValue}", status.toDbValue()!!))
        }
        val saved = dao.getTasksByDate("2026-11-14").first()
        assertEquals(4, saved.size)
        // 数据库里读回来的必须是中文原值，且能映射回英文常量
        assertEquals(
            listOf("待开始", "进行中", "已完成", "已取消"),
            saved.map { it.status }.sorted()
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
}
