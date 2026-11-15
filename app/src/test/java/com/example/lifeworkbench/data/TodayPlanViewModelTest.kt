package com.example.lifeworkbench.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.lifeworkbench.data.entities.TaskEntity
import com.example.lifeworkbench.ui.screens.TodayPlanViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * 阶段 2.1：今日计划 ViewModel 测试（Robolectric + 真实 Room 内存数据库）。
 *
 * 覆盖指令 4.4 要求的场景：指定日期查询、空列表、多任务排序、新建成功、
 * 非法标题拒绝、编辑后数据库更新、删除后数据库消失、完成/取消状态更新、
 * 日期切换后列表更新、未知状态历史任务仍可编辑其他字段、操作失败时正确显示错误。
 */
@RunWith(RobolectricTestRunner::class)
class TodayPlanViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository
    private lateinit var viewModel: TodayPlanViewModel

    private val today = LocalDate.of(2026, 11, 15)
    private val tomorrow = LocalDate.of(2026, 11, 16)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).addMigrations(AppDatabase.MIGRATION_1_2).build()
        repo = TaskRepository(db.taskDao())
        viewModel = TodayPlanViewModel(repo) { today }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun task(
        title: String = "学习任务",
        status: String = "待开始",
        date: String = "2026-11-15",
        startMinute: Int? = null,
        endMinute: Int? = null,
        category: String = "学习"
    ) = TaskEntity(
        title = title, description = null, planDate = date, category = category,
        priority = 2, status = status, sourceId = null,
        startMinute = startMinute, endMinute = endMinute
    )

    @Test
    fun tasks_showsSelectedDateTasksOnly() = runTest {
        db.taskDao().insertTask(task(title = "今日任务", date = "2026-11-15"))
        db.taskDao().insertTask(task(title = "明日任务", date = "2026-11-16"))
        val tasks = viewModel.tasks.first { it.size == 1 }
        assertEquals("今日任务", tasks[0].title)
    }

    @Test
    fun tasks_emptyList_whenNoTasks() = runTest {
        val tasks = viewModel.tasks.first()
        assertTrue(tasks.isEmpty())
    }

    @Test
    fun tasks_sortedTimedFirstByStartMinute() = runTest {
        db.taskDao().insertTask(task(title = "无时间", date = "2026-11-15"))
        db.taskDao().insertTask(task(title = "晚任务", date = "2026-11-15", startMinute = 600, endMinute = 660))
        db.taskDao().insertTask(task(title = "早任务", date = "2026-11-15", startMinute = 540, endMinute = 600))
        val tasks = viewModel.tasks.first { it.size == 3 }
        assertEquals(listOf("早任务", "晚任务", "无时间"), tasks.map { it.title })
    }

    @Test
    fun createTask_success_addsTask() = runTest {
        viewModel.createTask(
            title = "新任务", description = "描述", planDate = "2026-11-15",
            category = "健身", priority = 1, startMinute = 480, endMinute = 540
        )
        val msg = viewModel.message.first { it != null }
        assertFalse(msg!!.isError)
        val tasks = viewModel.tasks.first { it.size == 1 }
        assertEquals("新任务", tasks[0].title)
        assertEquals("健身", tasks[0].category)
        assertEquals(480, tasks[0].startMinute)
    }

    @Test
    fun createTask_invalidTitle_rejectedAndShowsError() = runTest {
        viewModel.createTask(
            title = "   ", description = null, planDate = "2026-11-15",
            category = "学习", priority = 2, startMinute = null, endMinute = null
        )
        val msg = viewModel.message.first { it != null }
        assertTrue(msg!!.isError)
        assertTrue(msg.text.contains("标题不能为空"))
        assertEquals(0, db.taskDao().countTasksByDate("2026-11-15"))
    }

    @Test
    fun updateTask_persistsChanges() = runTest {
        val id = db.taskDao().insertTask(task(title = "原标题")).toInt()
        val original = db.taskDao().getTaskById(id)!!
        viewModel.updateTask(original.copy(title = "改后标题"))
        val msg = viewModel.message.first { it != null }
        assertFalse(msg!!.isError)
        assertEquals("改后标题", db.taskDao().getTaskById(id)?.title)
    }

    @Test
    fun deleteTask_removesFromDatabase() = runTest {
        val id = db.taskDao().insertTask(task(title = "待删任务")).toInt()
        viewModel.deleteTaskById(id)
        val msg = viewModel.message.first { it != null }
        assertFalse(msg!!.isError)
        assertNull(db.taskDao().getTaskById(id))
    }

    @Test
    fun setStatus_completed_updatesTask() = runTest {
        val id = db.taskDao().insertTask(task(title = "待办", status = "待开始")).toInt()
        val original = db.taskDao().getTaskById(id)!!
        viewModel.setStatus(original, com.example.lifeworkbench.data.TaskStatus.Completed)
        val msg = viewModel.message.first { it != null }
        assertFalse(msg!!.isError)
        assertEquals("已完成", db.taskDao().getTaskById(id)?.status)
    }

    @Test
    fun setStatus_cancelled_updatesTask() = runTest {
        val id = db.taskDao().insertTask(task(title = "待办", status = "进行中")).toInt()
        val original = db.taskDao().getTaskById(id)!!
        viewModel.setStatus(original, com.example.lifeworkbench.data.TaskStatus.Cancelled)
        val msg = viewModel.message.first { it != null }
        assertFalse(msg!!.isError)
        assertEquals("已取消", db.taskDao().getTaskById(id)?.status)
    }

    @Test
    fun selectDate_updatesList() = runTest {
        db.taskDao().insertTask(task(title = "今日任务", date = "2026-11-15"))
        db.taskDao().insertTask(task(title = "明日任务", date = "2026-11-16"))
        val first = viewModel.tasks.first { it.size == 1 }
        assertEquals("今日任务", first[0].title)
        viewModel.selectDate(tomorrow)
        val second = viewModel.tasks.first { it.size == 1 && it[0].title == "明日任务" }
        assertEquals("明日任务", second[0].title)
    }

    @Test
    fun unknownStatusTask_editOtherFields_succeedsAndPreservesStatus() = runTest {
        val id = db.taskDao().insertTask(task(title = "历史任务", status = "已删除")).toInt()
        val original = db.taskDao().getTaskById(id)!!
        viewModel.updateTask(original.copy(title = "改后标题"))
        val msg = viewModel.message.first { it != null }
        assertFalse(msg!!.isError)
        val saved = db.taskDao().getTaskById(id)!!
        assertEquals("改后标题", saved.title)
        assertEquals("已删除", saved.status)
    }

    @Test
    fun operationFailure_showsErrorNotFakeSuccess() = runTest {
        // 更新不存在的任务：Repository 返回 NotFound，ViewModel 必须展示错误而非假装成功
        val ghost = task(title = "不存在").copy(id = 9999)
        viewModel.updateTask(ghost)
        val msg = viewModel.message.first { it != null }
        assertTrue(msg!!.isError)
        assertTrue(msg.text.contains("找不到该任务"))
    }
}
