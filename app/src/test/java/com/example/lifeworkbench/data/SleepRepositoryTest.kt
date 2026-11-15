package com.example.lifeworkbench.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.lifeworkbench.data.entities.SleepEntity
import com.example.lifeworkbench.data.entities.SleepRecordDao
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

/**
 * SleepRepository 测试（V1.1 阶段 3.0）。
 * 真实 Room 内存数据库 + Robolectric：合法数据读写、不存在的记录、
 * 更新/删除影响行数、数据库异常、Flow 数据刷新、多日期数据隔离、
 * 日期归属与首页摘要规则一致（已结束归起床日，计时中归入睡日，含跨午夜）。
 * 3.0 不做业务校验（SleepValidator 在 3.5 建立），不开发自动计时。
 *
 * 固定 epoch（Asia/Shanghai 时区）：
 * 1794668400000L = 上海 2026-11-14 23:00（入睡），1794697200000L = 上海 2026-11-15 07:00（起床）
 * 1794582000000L = 上海 2026-11-13 23:00（入睡），1794610800000L = 上海 2026-11-14 07:00（起床）
 * 1794751200000L = 上海 2026-11-15 22:00（计时中入睡）
 */
@RunWith(RobolectricTestRunner::class)
class SleepRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: SleepRepository

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun record(
        startTime: Long = 1794668400000L,
        endTime: Long? = 1794697200000L,
        quality: String? = "好",
        wakeUpCount: Int = 1,
        status: String = "已完成"
    ) = SleepEntity(
        startTime = startTime,
        endTime = endTime,
        quality = quality,
        wakeUpCount = wakeUpCount,
        status = status
    )

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = SleepRepository(db.sleepRecordDao()) { zone }
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun createRecord_valid_returnsIdAndPersists() = runBlocking {
        val result = repo.createRecord(record())
        assertTrue(result is RepoResult.Success)
        val id = (result as RepoResult.Success).data
        assertTrue(id > 0)
        val got = repo.getRecordById(id.toInt())
        assertTrue(got is RepoResult.Success)
        assertEquals(1794668400000L, (got as RepoResult.Success).data.startTime)
        assertEquals("已完成", got.data.status)
    }

    @Test
    fun getRecordById_missing_returnsNotFound() = runBlocking {
        val result = repo.getRecordById(999999)
        assertTrue(result is RepoResult.Failure)
        assertEquals(RepoError.NotFound(999999), (result as RepoResult.Failure).error)
    }

    @Test
    fun updateRecord_existing_returnsSuccessAndPersists() = runBlocking {
        val id = (repo.createRecord(record()) as RepoResult.Success).data.toInt()
        val result = repo.updateRecord(record(quality = "很好", wakeUpCount = 0).copy(id = id))
        assertTrue(result is RepoResult.Success)
        val kept = repo.getRecordById(id) as RepoResult.Success
        assertEquals("很好", kept.data.quality)
        assertEquals(0, kept.data.wakeUpCount)
    }

    @Test
    fun updateRecord_missing_returnsNotFound() = runBlocking {
        val result = repo.updateRecord(record(quality = "差").copy(id = 999999))
        assertTrue(result is RepoResult.Failure)
        assertEquals(RepoError.NotFound(999999), (result as RepoResult.Failure).error)
    }

    @Test
    fun deleteRecord_existing_returnsSuccess() = runBlocking {
        val id = (repo.createRecord(record()) as RepoResult.Success).data.toInt()
        val saved = (repo.getRecordById(id) as RepoResult.Success).data
        val result = repo.deleteRecord(saved)
        assertTrue(result is RepoResult.Success)
        assertTrue(repo.getRecordById(id) is RepoResult.Failure)
    }

    @Test
    fun deleteRecord_missing_returnsNotFound() = runBlocking {
        val ghost = record().copy(id = 999999)
        val result = repo.deleteRecord(ghost)
        assertTrue(result is RepoResult.Failure)
        assertEquals(RepoError.NotFound(999999), (result as RepoResult.Failure).error)
    }

    @Test
    fun deleteRecordById_missing_returnsNotFound() = runBlocking {
        val result = repo.deleteRecordById(999999)
        assertTrue(result is RepoResult.Failure)
        assertEquals(RepoError.NotFound(999999), (result as RepoResult.Failure).error)
    }

    @Test
    fun getRecordsByDate_completed_belongsToWakeUpDay() = runBlocking {
        // 11-13 23:00 入睡 → 11-14 07:00 起床：归 11-14；11-14 23:00 入睡 → 11-15 07:00 起床：归 11-15
        repo.createRecord(record(startTime = 1794582000000L, endTime = 1794610800000L))
        repo.createRecord(record(startTime = 1794668400000L, endTime = 1794697200000L))
        val day15 = repo.getRecordsByDate("2026-11-15").first()
        assertEquals(1, day15.size)
        assertEquals(1794668400000L, day15[0].startTime)
        val day14 = repo.getRecordsByDate("2026-11-14").first()
        assertEquals(1, day14.size)
        assertEquals(1794582000000L, day14[0].startTime)
        assertEquals(0, repo.getRecordsByDate("2026-11-16").first().size)
    }

    @Test
    fun getRecordsByDate_inProgress_belongsToStartDay() = runBlocking {
        // 计时中：11-15 22:00 入睡，endTime 为空——归 11-15（入睡日）
        repo.createRecord(record(startTime = 1794751200000L, endTime = null, quality = null, status = "计时中"))
        assertEquals(1, repo.getRecordsByDate("2026-11-15").first().size)
        assertEquals(0, repo.getRecordsByDate("2026-11-16").first().size)
    }

    @Test
    fun getRecordsByDate_matchesSummaryAttribution() = runBlocking {
        // 同一批固定 epoch 数据：Repository 的日期查询与首页摘要的归属必须一致
        repo.createRecord(record(startTime = 1794582000000L, endTime = 1794610800000L))
        repo.createRecord(record(startTime = 1794668400000L, endTime = 1794697200000L))
        repo.createRecord(record(startTime = 1794751200000L, endTime = null, quality = null, status = "计时中"))
        val summaryRepo = ModuleSummaryRepository(
            studyDao = db.studyRecordDao(),
            dietDao = db.dietRecordDao(),
            fitnessDao = db.fitnessRecordDao(),
            gameDao = db.gameRecordDao(),
            sleepDao = db.sleepRecordDao(),
            todayProvider = { LocalDate.of(2026, 11, 15) },
            zoneProvider = { zone }
        )
        val summaries = summaryRepo.getTodaySummaries().first()
        // 摘要（11-15）计 2 条：11-14 23:00→11-15 07:00 的已完成睡眠 + 11-15 22:00 开始的计时中睡眠
        assertEquals(2, summaries.sleep.recordCount)
        // Repository 的 11-15 日期查询也是 2 条，且 startTime 与摘要归属的记录一致
        val day15 = repo.getRecordsByDate("2026-11-15").first()
        assertEquals(2, day15.size)
        assertEquals(listOf(1794668400000L, 1794751200000L), day15.map { it.startTime })
        // 11-14 只有 11-13 23:00→11-14 07:00 的那一条
        val day14 = repo.getRecordsByDate("2026-11-14").first()
        assertEquals(1, day14.size)
        assertEquals(1794582000000L, day14[0].startTime)
    }

    @Test
    fun getAllRecords_flowReflectsInsertsAndDeletes() = runBlocking {
        assertEquals(0, repo.getAllRecords().first().size)
        val id = (repo.createRecord(record()) as RepoResult.Success).data.toInt()
        repo.createRecord(record(startTime = 1794582000000L, endTime = 1794610800000L))
        assertEquals(2, repo.getAllRecords().first().size)
        repo.deleteRecordById(id)
        assertEquals(listOf(1794582000000L), repo.getAllRecords().first().map { it.startTime })
    }

    /**
     * 数据库异常测试替身：DAO 抛异常时 Repository 必须返回 Database 错误，不抛到调用方。
     * （说明：Room 关闭后会重开连接而非抛异常，因此不能用“关闭数据库”模拟异常；
     * 用抛异常的 DAO 替身直接验证 Repository 的异常包装。）
     */
    private class ThrowingSleepDao : SleepRecordDao {
        override fun getAllRecords() = emptyFlow<List<SleepEntity>>()
        override suspend fun getRecordById(id: Int): SleepEntity? =
            throw RuntimeException("模拟数据库异常")
        override suspend fun insertRecord(record: SleepEntity): Long =
            throw RuntimeException("模拟数据库异常")
        override suspend fun updateRecord(record: SleepEntity): Int =
            throw RuntimeException("模拟数据库异常")
        override suspend fun deleteRecord(record: SleepEntity): Int =
            throw RuntimeException("模拟数据库异常")
        override suspend fun deleteRecordById(id: Int): Int =
            throw RuntimeException("模拟数据库异常")
        override fun getRecordsByDateRange(dayStart: Long, dayEnd: Long) =
            emptyFlow<List<SleepEntity>>()
    }

    @Test
    fun createRecord_daoThrows_returnsDatabaseError() = runBlocking {
        val brokenRepo = SleepRepository(ThrowingSleepDao()) { zone }
        val result = brokenRepo.createRecord(record())
        assertTrue(result is RepoResult.Failure)
        assertTrue((result as RepoResult.Failure).error is RepoError.Database)
    }

    @Test
    fun getRecordById_daoThrows_returnsDatabaseError() = runBlocking {
        val brokenRepo = SleepRepository(ThrowingSleepDao()) { zone }
        val result = brokenRepo.getRecordById(1)
        assertTrue(result is RepoResult.Failure)
        assertTrue((result as RepoResult.Failure).error is RepoError.Database)
    }
}
