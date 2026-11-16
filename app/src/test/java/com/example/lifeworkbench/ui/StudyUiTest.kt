package com.example.lifeworkbench.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
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
import com.example.lifeworkbench.data.RepoResult
import com.example.lifeworkbench.data.entities.StudyRecordEntity
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
 * 学习模块 Compose UI 自动化测试（V1.1 阶段 3.1）。
 *
 * 用 Robolectric 图形模式（@GraphicsMode(NATIVE)）在 testDebugUnitTest 里实际运行：
 * 每个场景都真实启动 MainActivity、真实点击、真实断言界面。
 * 启动的是真实 LifeWorkbenchApp + 真实 Room 文件数据库（非内存库），
 * 因此每个测试前清空 6 张表做数据隔离；非法内容不入库的断言直接读 DAO 计数。
 * 不使用 Dispatchers.setMain：UI 测试里 Dispatchers.Main 必须保持 Robolectric
 * 的真实主 looper，由 Compose 测试规则去驱动。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
@OptIn(ExperimentalTestApi::class)
class StudyUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var app: LifeWorkbenchApp
    private val today: LocalDate = LocalDate.now()
    private val todayString: String = today.toString()
    private val tomorrowString: String = today.plusDays(1).toString()

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
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

    /** 经 Repository 写入一学习记录，返回其 id（失败则抛错）。 */
    private fun insertStudyRecord(content: String, durationMinutes: Int, date: String): Long {
        val result = runBlocking {
            app.studyRepository.createRecord(
                StudyRecordEntity(
                    date = date,
                    durationMinutes = durationMinutes,
                    content = content,
                    knowledgePointId = null
                )
            )
        }
        assertTrue(result is RepoResult.Success)
        return (result as RepoResult.Success).data
    }

    private fun goToStudy() {
        composeTestRule.onNodeWithTag("moduleCard_学习").performClick()
        // “学习”二字在首页摘要卡片里也有，不能用它做到达判定；用学习页独有的新建按钮
        composeTestRule.waitUntilExactlyOneExists(hasText("新建学习记录"), 5000)
    }

    // ---- 场景 1：点首页学习卡片，进入学习页，空状态显示 ----
    @Test
    fun studyScreen_displays() {
        goToStudy()
        composeTestRule.onNodeWithText("学习").assertIsDisplayed()
        composeTestRule.onNodeWithText("暂无学习记录").assertIsDisplayed()
        composeTestRule.onNodeWithTag("newStudyButton").assertIsDisplayed()
    }

    // ---- 场景 2：点“新建学习记录”打开表单 ----
    @Test
    fun newStudyButton_opensForm() {
        goToStudy()
        composeTestRule.onNodeWithTag("newStudyButton").performClick()
        composeTestRule.onNodeWithTag("studyFormContent").assertIsDisplayed()
        composeTestRule.onNodeWithTag("studyFormDuration").assertIsDisplayed()
        composeTestRule.onNodeWithTag("studyFormDate").assertIsDisplayed()
        composeTestRule.onNodeWithTag("studyFormSave").assertIsDisplayed()
        composeTestRule.onNodeWithTag("studyFormCancel").assertIsDisplayed()
    }

    // ---- 场景 3：输入合法记录并保存，出现在列表（含时长） ----
    @Test
    fun createStudyRecord_validInput_appearsInList() {
        goToStudy()
        composeTestRule.onNodeWithTag("newStudyButton").performClick()
        composeTestRule.onNodeWithTag("studyFormContent").performTextInput("界面测试学习")
        composeTestRule.onNodeWithTag("studyFormDuration").performTextInput("90")
        composeTestRule.onNodeWithTag("studyFormSave").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("界面测试学习"), 5000)
        composeTestRule.onNodeWithText("界面测试学习").assertIsDisplayed()
        composeTestRule.onNodeWithText("学习时长：90 分钟").assertIsDisplayed()
        composeTestRule.onNodeWithText("暂无学习记录").assertDoesNotExist()
    }

    // ---- 场景 4：内容为空 → 报错且不写入数据库 ----
    @Test
    fun createStudyRecord_emptyContent_showsErrorAndNotSaved() {
        goToStudy()
        composeTestRule.onNodeWithTag("newStudyButton").performClick()
        composeTestRule.onNodeWithTag("studyFormDuration").performTextInput("60")
        composeTestRule.onNodeWithTag("studyFormSave").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("学习内容不能为空"), 5000)
        composeTestRule.onNodeWithText("学习内容不能为空").assertIsDisplayed()
        composeTestRule.onNodeWithText("暂无学习记录").assertIsDisplayed()
        val count = runBlocking { app.studyRepository.getRecordsByDate(todayString).first().size }
        assertEquals(0, count)
    }

    // ---- 场景 5：编辑记录，显示更新后的内容与时长 ----
    @Test
    fun editStudyRecord_updatesDisplayedContent() {
        val id = insertStudyRecord("原学习内容", 60, todayString)
        goToStudy()
        composeTestRule.waitUntilExactlyOneExists(hasText("原学习内容"), 5000)
        composeTestRule.onNodeWithTag("studyEdit_$id").performClick()
        composeTestRule.onNodeWithTag("studyFormContent").performTextClearance()
        composeTestRule.onNodeWithTag("studyFormContent").performTextInput("新学习内容")
        composeTestRule.onNodeWithTag("studyFormDuration").performTextClearance()
        composeTestRule.onNodeWithTag("studyFormDuration").performTextInput("45")
        composeTestRule.onNodeWithTag("studyFormSave").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("新学习内容"), 5000)
        composeTestRule.onNodeWithText("新学习内容").assertIsDisplayed()
        composeTestRule.onNodeWithText("学习时长：45 分钟").assertIsDisplayed()
        composeTestRule.onNodeWithText("原学习内容").assertDoesNotExist()
    }

    // ---- 场景 6：点删除出现确认框，确认后列表更新 ----
    @Test
    fun deleteStudyRecord_confirm_removesFromList() {
        val id = insertStudyRecord("待删学习", 30, todayString)
        goToStudy()
        composeTestRule.waitUntilExactlyOneExists(hasText("待删学习"), 5000)
        composeTestRule.onNodeWithTag("studyDelete_$id").performClick()
        composeTestRule.onNodeWithText("删除学习记录").assertIsDisplayed()
        composeTestRule.onNodeWithText("确定删除「待删学习」吗？删除后无法恢复。").assertIsDisplayed()
        composeTestRule.onNodeWithTag("studyDeleteConfirm").performClick()
        composeTestRule.waitUntilDoesNotExist(hasText("待删学习"), 5000)
        composeTestRule.onNodeWithText("暂无学习记录").assertIsDisplayed()
    }

    // ---- 场景 7：日期切换，显示该日期的记录 ----
    @Test
    fun switchDate_showsThatDaysRecords() {
        insertStudyRecord("今天学习", 60, todayString)
        insertStudyRecord("明天学习", 30, tomorrowString)
        goToStudy()
        composeTestRule.waitUntilExactlyOneExists(hasText("今天学习"), 5000)
        composeTestRule.onNodeWithText("明天学习").assertDoesNotExist()
        composeTestRule.onNodeWithTag("studyNextDay").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("明天学习"), 5000)
        composeTestRule.waitUntilDoesNotExist(hasText("今天学习"), 10000)
        composeTestRule.onNodeWithTag("studyPrevDay").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("今天学习"), 5000)
        composeTestRule.waitUntilDoesNotExist(hasText("明天学习"), 5000)
    }

    // ---- 场景 8：切到“全部历史”，两天记录都显示（带日期） ----
    @Test
    fun historyToggle_showsAllRecords() {
        insertStudyRecord("今天学习", 60, todayString)
        insertStudyRecord("昨天学习", 30, today.minusDays(1).toString())
        goToStudy()
        composeTestRule.waitUntilExactlyOneExists(hasText("今天学习"), 5000)
        composeTestRule.onNodeWithText("昨天学习").assertDoesNotExist()
        composeTestRule.onNodeWithTag("studyModeHistory").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("昨天学习"), 5000)
        composeTestRule.onNodeWithText("今天学习").assertIsDisplayed()
        composeTestRule.onNodeWithText("日期：$todayString").assertIsDisplayed()
    }
}
