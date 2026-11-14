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
 * TaskRepository 测试（V1.1 阶段 1）。
 * 真实 Room 内存数据库 + Robolectric：校验不通过不写入、错误类型明确、
 * 今日统计与状态统计与数据库实际记录一致、未知状态原样保留。
 */
@RunWith(RobolectricTestRunner::class)
class TaskRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository

    private fun task(
        title: String = "学习任务",
        status: String = "待开始",
        date: String = "2026-11-14",
        startMinute: Int? = null,
        endMinute: Int? = null
    ) = TaskEntity(
        title = title,
        description = null,
        planDate = date,
        category = "学习",
        priority = 1,
        status = status,
        sourceId = null,
        startMinute = startMinute,
        endMinute = endMinute
    )

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = TaskRepository(db.taskDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun createTask_valid_returnsIdAndPersists() = runBlocking {
        val result = repo.createTask(task(startMinute = 540, endMinute = 600))
        assertTrue(result is TaskResult.Success)
        val id = (result as TaskResult.Success).data
        assertTrue(id > 0)
        val got = repo.getTaskById(id.toInt())
        assertTrue(got is TaskResult.Success)
        assertEquals("学习任务", (got as TaskResult.Success).data.title)
        assertEquals(540, got.data.startMinute)
    }

    @Test
    fun createTask_invalid_returnsValidationFailedAndWritesNothing() = runBlocking {
        val result = repo.createTask(task(title = "   "))
        assertTrue(result is TaskResult.Failure)
        val error = (result as TaskResult.Failure).error
        assertTrue(error is TaskError.ValidationFailed)
        assertEquals(
            listOf(TaskValidationError.EMPTY_TITLE),
            (error as TaskError.ValidationFailed).errors
        )
        assertEquals(0, db.taskDao().countTasksByDate("2026-11-14"))
    }

    @Test
    fun createTask_unknownStatus_returnsValidationFailed() = runBlocking {
        val result = repo.createTask(task(status = "已删除"))
        assertTrue(result is TaskResult.Failure)
        val error = (result as TaskResult.Failure).error
        assertTrue(error is TaskError.ValidationFailed)
        assertEquals(
            listOf(TaskValidationError.UNKNOWN_STATUS),
            (error as TaskError.ValidationFailed).errors
        )
        assertEquals(0, db.taskDao().countTasksByDate("2026-11-14"))
    }

    @Test
    fun getTaskById_missing_returnsNotFound() = runBlocking {
        val result = repo.getTaskById(999999)
        assertTrue(result is TaskResult.Failure)
        assertEquals(TaskError.NotFound(999999), (result as TaskResult.Failure).error)
    }

    @Test
    fun updateTask_invalid_doesNotOverwrite() = runBlocking {
        val id = (repo.createTask(task()) as TaskResult.Success).data.toInt()
        val result = repo.updateTask(task(title = "", status = "待开始").copy(id = id))
        assertTrue(result is TaskResult.Failure)
        assertTrue((result as TaskResult.Failure).error is TaskError.ValidationFailed)
        val kept = repo.getTaskById(id) as TaskResult.Success
        assertEquals("学习任务", kept.data.title)
    }

    @Test
    fun deleteTaskById_removesRow() = runBlocking {
        val id = (repo.createTask(task()) as TaskResult.Success).data.toInt()
        val result = repo.deleteTaskById(id)
        assertTrue(result is TaskResult.Success)
        assertTrue(repo.getTaskById(id) is TaskResult.Failure)
    }

    @Test
    fun getTasksByDate_flowEmitsInsertedTasks() = runBlocking {
        assertEquals(0, repo.getTasksByDate("2026-11-14").first().size)
        repo.createTask(task(title = "A"))
        repo.createTask(task(title = "B"))
        assertEquals(listOf("A", "B"), repo.getTasksByDate("2026-11-14").first().map { it.title })
    }

    @Test
    fun getTodayStats_matchesDatabaseRecords() = runBlocking {
        repo.createTask(task(title = "做完", status = "已完成"))
        repo.createTask(task(title = "取消", status = "已取消"))
        // 未知状态不能经 Repository 创建（会校验失败），用 DAO 直接写入模拟历史数据
        db.taskDao().insertTask(task(title = "未知", status = "已删除"))
        val result = repo.getTodayStats("2026-11-14")
        assertTrue(result is TaskResult.Success)
        val stats = (result as TaskResult.Success).data
        assertEquals(3, stats.total)
        assertEquals(1, stats.completed)
        assertEquals(1, stats.cancelled)
        assertEquals(1, stats.unknown)
        assertEquals(50.0, stats.completionRate, 0.000001) // 1 / (3 - 1) × 100%
    }

    @Test
    fun getStatusCounts_preservesUnknownValues() = runBlocking {
        repo.createTask(task(title = "待办", status = "待开始"))
        repo.createTask(task(title = "做完", status = "已完成"))
        db.taskDao().insertTask(task(title = "未知", status = "已删除"))
        val result = repo.getStatusCounts("2026-11-14")
        assertTrue(result is TaskResult.Success)
        val counts = (result as TaskResult.Success).data
        assertEquals(1, counts[TaskStatus.Pending])
        assertEquals(1, counts[TaskStatus.Completed])
        val unknown = counts.keys.filterIsInstance<TaskStatus.Unknown>()
        assertEquals(1, unknown.size)
        assertEquals("已删除", unknown[0].rawValue)
        assertEquals(1, counts[unknown[0]])
    }

    // ---- V1.1 阶段 1 最终验收前修复：update/delete 按影响行数返回 NotFound ----

    @Test
    fun updateTask_existing_returnsSuccessAndPersists() = runBlocking {
        val id = (repo.createTask(task()) as TaskResult.Success).data.toInt()
        val result = repo.updateTask(task(title = "改后标题").copy(id = id))
        assertTrue(result is TaskResult.Success)
        val kept = repo.getTaskById(id) as TaskResult.Success
        assertEquals("改后标题", kept.data.title)
    }

    @Test
    fun updateTask_missing_returnsNotFound() = runBlocking {
        val result = repo.updateTask(task(title = "不存在").copy(id = 999999))
        assertTrue(result is TaskResult.Failure)
        assertEquals(TaskError.NotFound(999999), (result as TaskResult.Failure).error)
    }

    @Test
    fun deleteTask_existing_returnsSuccess() = runBlocking {
        val id = (repo.createTask(task()) as TaskResult.Success).data.toInt()
        val saved = (repo.getTaskById(id) as TaskResult.Success).data
        val result = repo.deleteTask(saved)
        assertTrue(result is TaskResult.Success)
        assertTrue(repo.getTaskById(id) is TaskResult.Failure)
    }

    @Test
    fun deleteTask_missing_returnsNotFound() = runBlocking {
        val ghost = task(title = "不存在").copy(id = 999999)
        val result = repo.deleteTask(ghost)
        assertTrue(result is TaskResult.Failure)
        assertEquals(TaskError.NotFound(999999), (result as TaskResult.Failure).error)
    }

    @Test
    fun deleteTaskById_missing_returnsNotFound() = runBlocking {
        val result = repo.deleteTaskById(999999)
        assertTrue(result is TaskResult.Failure)
        assertEquals(TaskError.NotFound(999999), (result as TaskResult.Failure).error)
    }

    // ---- V1.1 阶段 1 最终验收前修复：分类闭合 6 类，历史未知分类保留 ----

    @Test
    fun createTask_unknownCategory_returnsValidationFailed() = runBlocking {
        val result = repo.createTask(task().copy(category = "工作"))
        assertTrue(result is TaskResult.Failure)
        val error = (result as TaskResult.Failure).error
        assertTrue(error is TaskError.ValidationFailed)
        assertEquals(
            listOf(TaskValidationError.INVALID_CATEGORY),
            (error as TaskError.ValidationFailed).errors
        )
        assertEquals(0, db.taskDao().countTasksByDate("2026-11-14"))
    }

    @Test
    fun createTask_otherCategory_returnsSuccess() = runBlocking {
        val result = repo.createTask(task().copy(category = "其他"))
        assertTrue(result is TaskResult.Success)
        val id = (result as TaskResult.Success).data.toInt()
        assertEquals("其他", (repo.getTaskById(id) as TaskResult.Success).data.category)
    }

    @Test
    fun updateTask_unchangedUnknownCategory_preservesAndSucceeds() = runBlocking {
        // 历史数据：未知分类经 DAO 直接写入（模拟旧版本数据）
        val id = db.taskDao().insertTask(task().copy(category = "工作")).toInt()
        val result = repo.updateTask(task(title = "改后标题").copy(id = id, category = "工作"))
        assertTrue(result is TaskResult.Success)
        val kept = repo.getTaskById(id) as TaskResult.Success
        assertEquals("工作", kept.data.category)
        assertEquals("改后标题", kept.data.title)
    }

    @Test
    fun updateTask_changeCategoryToIllegal_returnsValidationFailed() = runBlocking {
        val id = db.taskDao().insertTask(task().copy(category = "工作")).toInt()
        val result = repo.updateTask(task(title = "学习任务").copy(id = id, category = "非法分类"))
        assertTrue(result is TaskResult.Failure)
        val error = (result as TaskResult.Failure).error
        assertTrue(error is TaskError.ValidationFailed)
        assertEquals(
            listOf(TaskValidationError.INVALID_CATEGORY),
            (error as TaskError.ValidationFailed).errors
        )
        // 拒绝后数据库原值不变
        val kept = repo.getTaskById(id) as TaskResult.Success
        assertEquals("工作", kept.data.category)
    }

    // ---- V1.1 阶段 1 最终验收前修复：标题 trim 后保存，查数据库实际保存值 ----

    @Test
    fun createTask_trimsTitleBeforeSaving() = runBlocking {
        val result = repo.createTask(task(title = "  学习计划  "))
        assertTrue(result is TaskResult.Success)
        val id = (result as TaskResult.Success).data.toInt()
        val got = repo.getTaskById(id) as TaskResult.Success
        assertEquals("学习计划", got.data.title)
    }

    @Test
    fun updateTask_trimsTitleBeforeSaving() = runBlocking {
        val id = (repo.createTask(task(title = "学习计划")) as TaskResult.Success).data.toInt()
        val result = repo.updateTask(task(title = "  复习功课  ").copy(id = id))
        assertTrue(result is TaskResult.Success)
        val got = repo.getTaskById(id) as TaskResult.Success
        assertEquals("复习功课", got.data.title)
    }
}
