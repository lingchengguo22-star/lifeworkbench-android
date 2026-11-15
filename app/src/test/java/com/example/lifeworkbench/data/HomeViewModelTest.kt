package com.example.lifeworkbench.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.lifeworkbench.data.entities.TaskEntity
import com.example.lifeworkbench.ui.screens.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * 阶段 2.2：首页 ViewModel 测试（Robolectric + 真实 Room 内存数据库）。
 *
 * 覆盖：今日统计（总数/已完成/待完成/完成率，含未知状态计入分母与待完成、
 * 分母为零时完成率为 0）、任务完成后续统计自动刷新、摘要随数据刷新、
 * 完成率展示格式。统计使用阶段 1 已验收的统一算法，不另写公式。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var taskRepo: TaskRepository
    private lateinit var summaryRepo: ModuleSummaryRepository
    private lateinit var viewModel: HomeViewModel

    private val today = LocalDate.of(2026, 11, 15)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        // ViewModel 的 viewModelScope 用 Dispatchers.Main；Robolectric 下真实 Main looper
        // 处于暂停状态，Room IO 挂起后恢复不到 Main，导致 runTest 10 秒超时。
        // 把 Main 换成测试调度器，runTest(testDispatcher) 统一驱动。
        Dispatchers.setMain(testDispatcher)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).addMigrations(AppDatabase.MIGRATION_1_2).build()
        taskRepo = TaskRepository(db.taskDao())
        summaryRepo = ModuleSummaryRepository(
            studyDao = db.studyRecordDao(),
            dietDao = db.dietRecordDao(),
            fitnessDao = db.fitnessRecordDao(),
            gameDao = db.gameRecordDao(),
            sleepDao = db.sleepRecordDao()
        ) { today }
        viewModel = HomeViewModel(taskRepo, summaryRepo) { today }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        // 不关闭 db：ViewModel 的 StateFlow 仍在收集 Room Flow，关闭会触发其内部异常；
        // 内存数据库随测试结束丢弃，不关闭无影响。
    }

    private fun task(title: String, status: String, date: String = "2026-11-15") =
        TaskEntity(
            title = title, description = null, planDate = date, category = "学习",
            priority = 2, status = status, sourceId = null
        )

    @Test
    fun todayStats_countsCorrectly_unknownInDenominatorAndPending() = runTest(testDispatcher) {
        db.taskDao().insertTask(task("已完成", "已完成"))
        db.taskDao().insertTask(task("已取消", "已取消"))
        db.taskDao().insertTask(task("待开始", "待开始"))
        db.taskDao().insertTask(task("未知状态", "已删除"))
        val stats = viewModel.todayStats.first { it != null }!!
        assertEquals(4, stats.total)
        assertEquals(1, stats.completed)
        assertEquals(1, stats.cancelled)
        assertEquals(1, stats.unknown)
        // 待完成 = 总数 - 已完成 - 已取消 = 2（待开始 + 未知状态）
        assertEquals(2, stats.total - stats.completed - stats.cancelled)
        // 完成率 = 1 / (4 - 1) = 33.33%
        assertEquals(100.0 / 3.0, stats.completionRate, 0.001)
    }

    @Test
    fun todayStats_denominatorZero_returnsZeroRate() = runTest(testDispatcher) {
        db.taskDao().insertTask(task("已取消1", "已取消"))
        db.taskDao().insertTask(task("已取消2", "已取消"))
        val stats = viewModel.todayStats.first { it != null }!!
        assertEquals(2, stats.total)
        assertEquals(0.0, stats.completionRate, 0.001)
    }

    @Test
    fun todayStats_empty_returnsZero() = runTest(testDispatcher) {
        val stats = viewModel.todayStats.first { it != null }!!
        assertEquals(0, stats.total)
        assertEquals(0, stats.completed)
        assertEquals(0.0, stats.completionRate, 0.001)
    }

    @Test
    fun todayStats_updatesWhenTaskCompleted() = runTest(testDispatcher) {
        val id = db.taskDao().insertTask(task("待办", "待开始")).toInt()
        val before = viewModel.todayStats.first { it != null }!!
        assertEquals(0, before.completed)
        val original = db.taskDao().getTaskById(id)!!
        taskRepo.updateTask(original.copy(status = "已完成"))
        val after = viewModel.todayStats.first { it != null && it.completed == 1 }!!
        assertEquals(1, after.completed)
        assertEquals(100.0, after.completionRate, 0.001)
    }

    @Test
    fun summaries_reflectSavedData() = runTest(testDispatcher) {
        val before = viewModel.summaries.first { it != null }!!
        assertEquals(0, before.study.recordCount)
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO study_records (date, durationMinutes, content, knowledgePointId) VALUES ('2026-11-15', 45, '学习内容', NULL)"
        )
        val after = viewModel.summaries.first { it != null && it.study.recordCount == 1 }!!
        assertEquals(45, after.study.totalMinutes)
    }

    @Test
    fun formatRate_formatsCorrectly() {
        assertEquals("50%", HomeViewModel.formatRate(50.0))
        assertEquals("0%", HomeViewModel.formatRate(0.0))
        assertEquals("100%", HomeViewModel.formatRate(100.0))
        assertEquals("11.1%", HomeViewModel.formatRate(100.0 / 9.0))
    }
}
