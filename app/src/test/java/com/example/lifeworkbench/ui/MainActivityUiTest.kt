package com.example.lifeworkbench.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.lifeworkbench.LifeWorkbenchApp
import com.example.lifeworkbench.MainActivity
import com.example.lifeworkbench.data.TaskResult
import com.example.lifeworkbench.data.entities.TaskEntity
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose UI 自动化测试（补充验收任务 1）。
 *
 * 用 Robolectric 图形模式（@GraphicsMode(NATIVE)）在 testDebugUnitTest 里实际运行，
 * 不是 ViewModel 测试的替代：每个场景都真实启动 MainActivity、真实点击、真实断言界面。
 *
 * 注意点：
 * - 启动的是真实 LifeWorkbenchApp + 真实 Room 文件数据库（非内存库），
 *   因此每个测试前清空 6 张表做数据隔离；非法标题不入库的断言直接读 DAO 计数。
 * - 不使用 Dispatchers.setMain：UI 测试里 Dispatchers.Main 必须保持 Robolectric
 *   的真实主 looper，由 Compose 测试规则去驱动；setMain 会让规则的 waitForIdle
 *   与 viewModelScope 的调度脱节。
 * - Room Flow 的重新发射是异步的：等待列表变化一律用 waitUntil 系列，
 *   不只用 waitForIdle。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
@OptIn(ExperimentalTestApi::class)
class MainActivityUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var app: LifeWorkbenchApp
    private val today: LocalDate = LocalDate.now()
    private val todayString: String = today.toString()
    private val tomorrowString: String = today.plusDays(1).toString()

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        // 清空 6 张表（在事务里执行，Room 失效通知会触发已订阅的 Flow 重新发射）
        runBlocking {
            withContext(Dispatchers.IO) {
                app.database.runInTransaction {
                    val db = app.database.openHelper.writableDatabase
                    db.execSQL("DELETE FROM tasks")
                    db.execSQL("DELETE FROM study_records")
                    db.execSQL("DELETE FROM diet_records")
                    db.execSQL("DELETE FROM fitness_records")
                    db.execSQL("DELETE FROM game_records")
                    db.execSQL("DELETE FROM sleep_records")
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    /** 经 Repository 写入一任务，返回其 id（失败则抛错）。 */
    private fun insertTask(title: String, planDate: String): Long {
        val result = runBlocking {
            app.taskRepository.createTask(
                TaskEntity(
                    title = title, description = null, planDate = planDate,
                    category = "学习", priority = 2, status = "待开始", sourceId = null
                )
            )
        }
        assertTrue(result is TaskResult.Success)
        return (result as TaskResult.Success).data
    }

    private fun goToToday() {
        composeTestRule.onNodeWithTag("navToday").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("今日计划"), 5000)
    }

    // ---- 场景 1：首页正常显示 ----
    @Test
    fun homeScreen_displays() {
        composeTestRule.onNodeWithText("首页总览").assertIsDisplayed()
        composeTestRule.onNodeWithText("今日任务").assertIsDisplayed()
        composeTestRule.onNodeWithText("模块摘要").assertIsDisplayed()
        composeTestRule.onNodeWithTag("todayCard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("moduleCard_学习").assertIsDisplayed()
        composeTestRule.onNodeWithTag("moduleCard_睡眠").assertIsDisplayed()
    }

    // ---- 场景 2：今日页面正常显示 ----
    @Test
    fun todayScreen_displays() {
        goToToday()
        composeTestRule.onNodeWithText("今日计划").assertIsDisplayed()
        composeTestRule.onNodeWithText("暂无任务").assertIsDisplayed()
        composeTestRule.onNodeWithTag("newTaskButton").assertIsDisplayed()
    }

    // ---- 场景 3：点“新建任务”打开表单 ----
    @Test
    fun newTaskButton_opensForm() {
        goToToday()
        composeTestRule.onNodeWithTag("newTaskButton").performClick()
        composeTestRule.onNodeWithTag("formTitle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("formSave").assertIsDisplayed()
        composeTestRule.onNodeWithTag("formCancel").assertIsDisplayed()
    }

    // ---- 场景 4：输入合法任务并保存，出现在列表 ----
    @Test
    fun createTask_validInput_appearsInList() {
        goToToday()
        composeTestRule.onNodeWithTag("newTaskButton").performClick()
        composeTestRule.onNodeWithTag("formTitle").performTextInput("界面测试任务")
        composeTestRule.onNodeWithTag("formSave").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("界面测试任务"), 5000)
        composeTestRule.onNodeWithText("界面测试任务").assertIsDisplayed()
        composeTestRule.onNodeWithText("暂无任务").assertDoesNotExist()
    }

    // ---- 场景 5：非法标题（空标题）→ 报错且不写入数据库 ----
    @Test
    fun createTask_emptyTitle_showsErrorAndNotSaved() {
        goToToday()
        composeTestRule.onNodeWithTag("newTaskButton").performClick()
        // 标题留空，直接保存
        composeTestRule.onNodeWithTag("formSave").performClick()
        // 表单关闭，今日页显示校验错误（ViewModel 的 message 卡片）
        composeTestRule.waitUntilExactlyOneExists(hasText("标题不能为空"), 5000)
        composeTestRule.onNodeWithText("标题不能为空").assertIsDisplayed()
        composeTestRule.onNodeWithText("暂无任务").assertIsDisplayed()
        // 直接读数据库：今天 0 条
        val count = runBlocking { app.database.taskDao().countTasksByDate(todayString) }
        assertEquals(0, count)
    }

    // ---- 场景 6：编辑任务，显示更新后的结果 ----
    @Test
    fun editTask_updatesDisplayedTitle() {
        val id = insertTask("原标题任务", todayString)
        goToToday()
        composeTestRule.waitUntilExactlyOneExists(hasText("原标题任务"), 5000)
        composeTestRule.onNodeWithTag("taskEdit_$id").performClick()
        composeTestRule.onNodeWithTag("formTitle").performTextClearance()
        composeTestRule.onNodeWithTag("formTitle").performTextInput("新标题任务")
        composeTestRule.onNodeWithTag("formSave").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("新标题任务"), 5000)
        composeTestRule.onNodeWithText("新标题任务").assertIsDisplayed()
        composeTestRule.onNodeWithText("原标题任务").assertDoesNotExist()
    }

    // ---- 场景 7：点删除，出现确认框 ----
    @Test
    fun deleteTask_showsConfirmDialog() {
        val id = insertTask("待删任务", todayString)
        goToToday()
        composeTestRule.waitUntilExactlyOneExists(hasText("待删任务"), 5000)
        composeTestRule.onNodeWithTag("taskDelete_$id").performClick()
        composeTestRule.onNodeWithText("删除任务").assertIsDisplayed()
        composeTestRule.onNodeWithText("确定删除「待删任务」吗？删除后无法恢复。").assertIsDisplayed()
        composeTestRule.onNodeWithTag("deleteConfirm").assertIsDisplayed()
        composeTestRule.onNodeWithTag("deleteCancel").assertIsDisplayed()
    }

    // ---- 场景 8：确认删除，列表更新 ----
    @Test
    fun deleteTask_confirm_removesFromList() {
        val id = insertTask("待删任务", todayString)
        goToToday()
        composeTestRule.waitUntilExactlyOneExists(hasText("待删任务"), 5000)
        composeTestRule.onNodeWithTag("taskDelete_$id").performClick()
        composeTestRule.onNodeWithTag("deleteConfirm").performClick()
        composeTestRule.waitUntilDoesNotExist(hasText("待删任务"), 5000)
        composeTestRule.onNodeWithText("暂无任务").assertIsDisplayed()
    }

    // ---- 场景 9：点完成，任务状态更新 ----
    @Test
    fun completeTask_statusUpdates() {
        val id = insertTask("待完成任务", todayString)
        goToToday()
        composeTestRule.waitUntilExactlyOneExists(hasText("待完成任务"), 5000)
        composeTestRule.onNodeWithText("待开始").assertIsDisplayed()
        composeTestRule.onNodeWithTag("taskComplete_$id").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("已完成"), 5000)
        composeTestRule.onNodeWithText("已完成").assertIsDisplayed()
        composeTestRule.onNodeWithText("待开始").assertDoesNotExist()
    }

    // ---- 场景 10：日期切换，显示该日期的任务 ----
    @Test
    fun switchDate_showsThatDaysTasks() {
        insertTask("今天任务", todayString)
        insertTask("明天任务", tomorrowString)
        goToToday()
        composeTestRule.waitUntilExactlyOneExists(hasText("今天任务"), 5000)
        composeTestRule.onNodeWithText("明天任务").assertDoesNotExist()
        composeTestRule.onNodeWithTag("nextDay").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("明天任务"), 5000)
        composeTestRule.onNodeWithText("今天任务").assertDoesNotExist()
        // 回到今天
        composeTestRule.onNodeWithTag("prevDay").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("今天任务"), 5000)
    }

    // ---- 场景 11：点首页今日卡片，跳到今日计划 ----
    @Test
    fun homeTodayCard_navigatesToToday() {
        composeTestRule.onNodeWithTag("todayCard").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("今日计划"), 5000)
        composeTestRule.onNodeWithText("今日计划").assertIsDisplayed()
        composeTestRule.onNodeWithTag("newTaskButton").assertIsDisplayed()
    }

    // ---- 场景 12：底部导航选中态与当前路由一致 ----
    @Test
    fun bottomNav_selectedStateMatchesRoute() {
        // 初始在首页
        composeTestRule.onNodeWithTag("navHome").assertIsSelected()
        composeTestRule.onNodeWithTag("navToday").assertIsNotSelected()
        composeTestRule.onNodeWithTag("navSettings").assertIsNotSelected()
        // 切到今日
        composeTestRule.onNodeWithTag("navToday").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("今日计划"), 5000)
        composeTestRule.onNodeWithTag("navToday").assertIsSelected()
        composeTestRule.onNodeWithTag("navHome").assertIsNotSelected()
        composeTestRule.onNodeWithTag("navSettings").assertIsNotSelected()
        // 切到设置
        composeTestRule.onNodeWithTag("navSettings").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("数据与设置"), 5000)
        composeTestRule.onNodeWithTag("navSettings").assertIsSelected()
        composeTestRule.onNodeWithTag("navHome").assertIsNotSelected()
        composeTestRule.onNodeWithTag("navToday").assertIsNotSelected()
        // 进入模块占位页：三项均不选中
        composeTestRule.onNodeWithTag("navHome").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("首页总览"), 5000)
        composeTestRule.onNodeWithTag("moduleCard_学习").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("该模块尚未开发，敬请期待。"), 5000)
        composeTestRule.onNodeWithTag("navHome").assertIsNotSelected()
        composeTestRule.onNodeWithTag("navToday").assertIsNotSelected()
        composeTestRule.onNodeWithTag("navSettings").assertIsNotSelected()
    }

    // ---- 场景 13：点五大模块摘要卡片，进入对应占位页 ----
    @Test
    fun moduleCard_navigatesToPlaceholder() {
        composeTestRule.onNodeWithTag("moduleCard_健身").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("该模块尚未开发，敬请期待。"), 5000)
        composeTestRule.onNodeWithText("健身").assertIsDisplayed()
    }
}
