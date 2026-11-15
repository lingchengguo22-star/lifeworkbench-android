package com.example.lifeworkbench.ui

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.lifeworkbench.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 插桩测试（补充验收任务 2）：在模拟器/真机上验证首次安装与数据持久化。
 *
 * 只在模拟器或真机上运行（connectedDebugAndroidTest），不走 testDebugUnitTest。
 * 流程：首次启动（确认数据库文件已创建）→ 界面新建任务 → 编辑 → 完成
 * → 杀死应用进程后重新启动 → 确认任务与状态仍在 → 首页统计与今日一致。
 *
 * 前半段用 Compose 测试规则（与 MainActivityUiTest 同一套操作）；
 * 进程杀死后原规则的 Activity 已销毁，改用 UIAutomator（系统级）断言重开后的界面。
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class MainActivityInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val device: UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun firstInstall_createEditComplete_forceStop_relaunch_persists() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // ---- 首次启动：首页显示，数据库文件已创建 ----
        composeTestRule.onNodeWithText("首页总览").assertIsDisplayed()
        val dbFile = context.getDatabasePath("lifeworkbench.db")
        assertTrue("首次启动后数据库文件应存在", dbFile.exists())

        // ---- 界面新建任务 ----
        composeTestRule.onNodeWithTag("navToday").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("今日计划"), 5000)
        composeTestRule.onNodeWithTag("newTaskButton").performClick()
        composeTestRule.onNodeWithTag("formTitle").performTextInput("持久化任务")
        composeTestRule.onNodeWithTag("formSave").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("持久化任务"), 5000)

        // ---- 编辑 ----
        // 新建任务的 id 未知：列表只有这一条，用第一个“编辑”按钮
        composeTestRule.onNodeWithText("编辑").performClick()
        composeTestRule.onNodeWithTag("formTitle").performTextClearance()
        composeTestRule.onNodeWithTag("formTitle").performTextInput("持久化任务已编辑")
        composeTestRule.onNodeWithTag("formSave").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("持久化任务已编辑"), 5000)

        // ---- 完成 ----
        composeTestRule.onNodeWithText("完成").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("已完成"), 5000)

        // ---- 关闭应用（杀死进程）再重新启动 ----
        composeTestRule.activityRule.scenario.close()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand(
            "am force-stop com.example.lifeworkbench"
        )
        ActivityScenario.launch(MainActivity::class.java)

        // ---- 重开后（UIAutomator）：任务与状态仍在 ----
        assertTrue(device.wait(Until.hasObject(By.text("首页总览")), 5000))
        device.findObject(By.text("今日")).click()
        assertTrue(device.wait(Until.hasObject(By.text("持久化任务已编辑")), 5000))
        assertTrue(device.wait(Until.hasObject(By.text("已完成")), 5000))
        // 回首页：统计与今日一致（1 条任务、已完成 1 条、完成率 100%）
        device.findObject(By.text("首页")).click()
        assertTrue(device.wait(Until.hasObject(By.textContains("完成率: 100%")), 5000))
        assertTrue(device.wait(Until.hasObject(By.textContains("今日任务总数: 1")), 5000))
    }
}
