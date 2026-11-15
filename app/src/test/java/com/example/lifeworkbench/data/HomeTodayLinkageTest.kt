package com.example.lifeworkbench.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.lifeworkbench.data.entities.TaskEntity
import com.example.lifeworkbench.ui.screens.HomeViewModel
import com.example.lifeworkbench.ui.screens.TodayPlanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * 阶段 2.3：首页与今日计划的联动测试（真实 Room 内存库）。
 * - 今日页切换日期不影响首页“今日”统计（首页只看当天）。
 * - 在今日页完成一个任务，首页统计随之更新（同一 Repository / 同一数据库）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeTodayLinkageTest {

    private lateinit var db: AppDatabase
    private lateinit var taskRepository: TaskRepository
    private lateinit var summaryRepository: ModuleSummaryRepository
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var todayViewModel: TodayPlanViewModel

    private val testDispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 11, 15)
    private val tomorrow = today.plusDays(1)

    @Before
    fun setUp() {
        // 与 TodayPlanViewModelTest / HomeViewModelTest 同一原因：Main 换成测试调度器。
        Dispatchers.setMain(testDispatcher)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        taskRepository = TaskRepository(db.taskDao())
        summaryRepository = ModuleSummaryRepository(
            db.studyRecordDao(), db.dietRecordDao(), db.fitnessRecordDao(),
            db.gameRecordDao(), db.sleepRecordDao()
        )
        homeViewModel = HomeViewModel(taskRepository, summaryRepository) { today }
        todayViewModel = TodayPlanViewModel(taskRepository) { today }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        // 不关闭 db：两个 ViewModel 的 StateFlow 仍在收集 Room Flow（见同类测试注释）。
    }

    @Test
    fun todayDateSwitch_doesNotChangeHomeStats() = runTest(testDispatcher) {
        taskRepository.createTask(task("今日任务", today))
        taskRepository.createTask(task("明日任务", tomorrow))

        val before = homeViewModel.todayStats.first { it != null }!!
        assertEquals(1, before.total)

        todayViewModel.selectDate(tomorrow)
        // 今日页已切到明天，首页统计仍只看今天。
        val after = homeViewModel.todayStats.first { it != null && it.total == 1 }!!
        assertEquals(1, after.total)
        assertEquals(0, after.completed)
    }

    @Test
    fun completeTaskInToday_updatesHomeStats() = runTest(testDispatcher) {
        taskRepository.createTask(task("待完成", today))
        val before = homeViewModel.todayStats.first { it != null }!!
        assertEquals(0, before.completed)

        val created = todayViewModel.tasks.first { it.size == 1 }[0]
        todayViewModel.setStatus(created, TaskStatus.Completed)

        val after = homeViewModel.todayStats.first { it != null && it.completed == 1 }!!
        assertEquals(1, after.completed)
        assertEquals(1, after.total)
    }

    private fun task(title: String, date: LocalDate) = TaskEntity(
        title = title,
        description = null,
        planDate = date.toString(),
        category = "学习",
        priority = 2,
        status = TaskStatus.Pending.dbValue,
        sourceId = null
    )
}
