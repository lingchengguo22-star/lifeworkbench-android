package com.example.lifeworkbench.data

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.lifeworkbench.ui.screens.TodayPlanViewModel
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * 诊断测试：用真实 Main 调度器（不 setMain）复现今日计划日期切换，
 * 定位“旧任务滞留”是 ViewModel 的 tasks 流问题还是 Compose 界面层问题。
 *
 * 背景：MainActivityUiTest.switchDate_showsThatDaysTasks 在 Robolectric 图形模式下失败——
 * 切到明天后“明天任务”出现，但“今天任务”10 秒仍不消失；
 * 而数据层诊断已证明 DAO 与 Repository 对今天/明天各返回正确 1 条。
 * 本测试不用 Compose 规则，只驱动 ViewModel + 真实 Main looper，
 * 若此处 tasks 仍含今天任务，问题在 ViewModel 的 flatMapLatest/stateIn 链；
 * 若此处正确，问题在 Compose 界面层。
 */
@RunWith(RobolectricTestRunner::class)
class TodayPlanViewModelRealMainTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TaskRepository
    private lateinit var viewModel: TodayPlanViewModel
    private var collectJob: Job? = null
    private val today: LocalDate = LocalDate.now()
    private val todayString: String = today.toString()
    private val tomorrowString: String = today.plusDays(1).toString()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TaskRepository(db.taskDao())
        viewModel = TodayPlanViewModel(repository) { today }
        // stateIn(WhileSubscribed) 需有订阅者才会启动上游：后台收集 tasks 保持订阅
        collectJob = CoroutineScope(Dispatchers.Default).launch { viewModel.tasks.collect() }
    }

    @After
    fun tearDown() {
        collectJob?.cancel()
        db.close()
    }

    private fun insertTask(title: String, planDate: String) {
        runBlocking {
            repository.createTask(
                com.example.lifeworkbench.data.entities.TaskEntity(
                    title = title, description = null, planDate = planDate,
                    category = "学习", priority = 2, status = "待开始", sourceId = null
                )
            )
        }
    }

    /** 反复驱动 Main looper，直到条件成立或超时（毫秒）。 */
    private fun pumpUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return true
            Thread.sleep(50)
        }
        shadowOf(Looper.getMainLooper()).idle()
        return condition()
    }

    @Test
    fun nextDay_withRealMain_tasksSwitchToTomorrowOnly() {
        insertTask("今天任务", todayString)
        insertTask("明天任务", tomorrowString)
        // 等初始订阅拿到今天的任务
        assertEquals(
            true,
            pumpUntil(5000) { viewModel.tasks.value.map { it.title } == listOf("今天任务") }
        )
        assertEquals(listOf("今天任务"), viewModel.tasks.value.map { it.title })
        viewModel.nextDay()
        // 切到明天后，tasks 应只含明天任务
        assertEquals(
            true,
            pumpUntil(5000) { viewModel.tasks.value.map { it.title } == listOf("明天任务") }
        )
        assertEquals(listOf("明天任务"), viewModel.tasks.value.map { it.title })
    }
}
