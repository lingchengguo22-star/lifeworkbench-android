package com.example.lifeworkbench.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.lifeworkbench.data.entities.StudyRecordEntity
import com.example.lifeworkbench.ui.screens.StudyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * 阶段 3.1：学习 ViewModel 测试（Robolectric + 真实 Room 内存数据库）。
 *
 * 覆盖：初始状态、新建成功、非法内容拒绝且不入库、编辑后数据库更新、
 * 删除后数据库消失、删除不存在的记录、日期切换后列表更新、历史视图、
 * 回到今天、操作失败时正确显示错误。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StudyViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: StudyRepository
    private lateinit var viewModel: StudyViewModel

    private val today = LocalDate.of(2026, 11, 15)
    private val yesterday = LocalDate.of(2026, 11, 14)

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
        repo = StudyRepository(db.studyRecordDao())
        viewModel = StudyViewModel(repo) { today }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        // 不关闭 db：ViewModel 的 StateFlow 仍在收集 Room Flow，关闭会触发其内部异常；
        // 内存数据库随测试结束丢弃，不关闭无影响。
    }

    @Test
    fun initialState_todayEmpty() = runTest(testDispatcher) {
        assertEquals(today, viewModel.selectedDate.value)
        assertFalse(viewModel.showHistory.value)
        assertTrue(viewModel.records.first().isEmpty())
    }

    @Test
    fun createRecord_success_addsRecordAndShowsMessage() = runTest(testDispatcher) {
        viewModel.createRecord("Kotlin 学习", 90, "2026-11-15")
        val msg = viewModel.message.first { it != null }
        assertFalse(msg!!.isError)
        assertEquals("学习记录已保存", msg.text)
        val records = viewModel.records.first { it.size == 1 }
        assertEquals("Kotlin 学习", records[0].content)
        assertEquals(90, records[0].durationMinutes)
    }

    @Test
    fun createRecord_blankContent_rejectedAndShowsError() = runTest(testDispatcher) {
        viewModel.createRecord("   ", 60, "2026-11-15")
        val msg = viewModel.message.first { it != null }
        assertTrue(msg!!.isError)
        assertTrue(msg.text.contains("学习内容不能为空"))
        assertEquals(0, repo.getRecordsByDate("2026-11-15").first().size)
    }

    @Test
    fun createRecord_invalidDuration_rejectedAndShowsError() = runTest(testDispatcher) {
        viewModel.createRecord("学习", 0, "2026-11-15")
        val msg = viewModel.message.first { it != null }
        assertTrue(msg!!.isError)
        assertTrue(msg.text.contains("学习时长必须大于 0 分钟"))
        assertEquals(0, repo.getRecordsByDate("2026-11-15").first().size)
    }

    @Test
    fun updateRecord_persistsChanges() = runTest(testDispatcher) {
        val id = (repo.createRecord(
            StudyRecordEntity(date = "2026-11-15", durationMinutes = 60, content = "原内容", knowledgePointId = null)
        ) as RepoResult.Success).data.toInt()
        val original = (repo.getRecordById(id) as RepoResult.Success).data
        viewModel.updateRecord(original.copy(content = "改后内容", durationMinutes = 45))
        val msg = viewModel.message.first { it != null }
        assertFalse(msg!!.isError)
        assertEquals("学习记录已更新", msg.text)
        val kept = (repo.getRecordById(id) as RepoResult.Success).data
        assertEquals("改后内容", kept.content)
        assertEquals(45, kept.durationMinutes)
    }

    @Test
    fun deleteRecord_removesFromDatabase() = runTest(testDispatcher) {
        val id = (repo.createRecord(
            StudyRecordEntity(date = "2026-11-15", durationMinutes = 60, content = "待删", knowledgePointId = null)
        ) as RepoResult.Success).data.toInt()
        viewModel.deleteRecordById(id)
        val msg = viewModel.message.first { it != null }
        assertFalse(msg!!.isError)
        assertEquals("学习记录已删除", msg.text)
        assertTrue(repo.getRecordById(id) is RepoResult.Failure)
    }

    @Test
    fun deleteRecord_missing_showsNotFoundError() = runTest(testDispatcher) {
        viewModel.deleteRecordById(999999)
        val msg = viewModel.message.first { it != null }
        assertTrue(msg!!.isError)
        assertTrue(msg.text.contains("找不到该学习记录"))
    }

    @Test
    fun dateSwitch_showsThatDaysRecordsOnly() = runTest(testDispatcher) {
        repo.createRecord(
            StudyRecordEntity(date = "2026-11-15", durationMinutes = 60, content = "今天", knowledgePointId = null)
        )
        repo.createRecord(
            StudyRecordEntity(date = "2026-11-14", durationMinutes = 30, content = "昨天", knowledgePointId = null)
        )
        // 切日期后用内容感知断言等待新发射：StateFlow 的当前值仍是旧日期的记录（同样 1 条），
        // 只按 size 等会立刻取到旧值（与 TodayPlanViewModelTest.selectDate_updatesList 同一做法）
        val todayRecords = viewModel.records.first { it.size == 1 && it[0].content == "今天" }
        assertEquals("今天", todayRecords[0].content)
        viewModel.previousDay()
        val yesterdayRecords = viewModel.records.first { it.size == 1 && it[0].content == "昨天" }
        assertEquals("昨天", yesterdayRecords[0].content)
        viewModel.goToToday()
        val backRecords = viewModel.records.first { it.size == 1 && it[0].content == "今天" }
        assertEquals("今天", backRecords[0].content)
    }

    @Test
    fun showHistory_listsAllRecordsDateDesc() = runTest(testDispatcher) {
        repo.createRecord(
            StudyRecordEntity(date = "2026-11-14", durationMinutes = 30, content = "昨天", knowledgePointId = null)
        )
        repo.createRecord(
            StudyRecordEntity(date = "2026-11-15", durationMinutes = 60, content = "今天", knowledgePointId = null)
        )
        viewModel.setShowHistory(true)
        val records = viewModel.records.first { it.size == 2 }
        assertEquals(listOf("今天", "昨天"), records.map { it.content })
        viewModel.setShowHistory(false)
        val dateRecords = viewModel.records.first { it.size == 1 }
        assertEquals("今天", dateRecords[0].content)
    }

    @Test
    fun clearMessage_clears() = runTest(testDispatcher) {
        viewModel.createRecord("学习", 60, "2026-11-15")
        viewModel.message.first { it != null }
        viewModel.clearMessage()
        assertEquals(null, viewModel.message.value)
    }
}
