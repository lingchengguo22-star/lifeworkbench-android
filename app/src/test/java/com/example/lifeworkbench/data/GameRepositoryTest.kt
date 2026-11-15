package com.example.lifeworkbench.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.lifeworkbench.data.entities.GameEntity
import com.example.lifeworkbench.data.entities.GameRecordDao
import kotlinx.coroutines.CancellationException
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
 * GameRepository 测试（V1.1 阶段 3.0）。
 * 真实 Room 内存数据库 + Robolectric：合法数据读写、不存在的记录、
 * 更新/删除影响行数、数据库异常、Flow 数据刷新、多日期数据隔离、
 * 日期归属与首页摘要规则一致（游戏归开始日，含跨午夜）。
 * 3.0 不做业务校验（GameValidator 在 3.4 建立），不开发自动计时。
 *
 * 固定 epoch（Asia/Shanghai 时区）：
 * 1794679200000L = 2026-11-14T18:00:00Z = 上海 2026-11-15 02:00
 * 1794636000000L = 2026-11-14T06:00:00Z = 上海 2026-11-14 14:00
 */
@RunWith(RobolectricTestRunner::class)
class GameRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: GameRepository

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun record(
        gameName: String = "游戏A",
        startTime: Long = 1794679200000L,
        endTime: Long? = 1794679200000L + 3600000L,
        durationMinutes: Int = 60,
        recordType: String = "手动补录"
    ) = GameEntity(
        gameName = gameName,
        startTime = startTime,
        endTime = endTime,
        durationMinutes = durationMinutes,
        recordType = recordType
    )

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = GameRepository(db.gameRecordDao()) { zone }
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
        assertEquals("游戏A", (got as RepoResult.Success).data.gameName)
        assertEquals(60, got.data.durationMinutes)
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
        val result = repo.updateRecord(record(gameName = "改后名", durationMinutes = 90).copy(id = id))
        assertTrue(result is RepoResult.Success)
        val kept = repo.getRecordById(id) as RepoResult.Success
        assertEquals("改后名", kept.data.gameName)
        assertEquals(90, kept.data.durationMinutes)
    }

    @Test
    fun updateRecord_missing_returnsNotFound() = runBlocking {
        val result = repo.updateRecord(record(gameName = "不存在").copy(id = 999999))
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
        val ghost = record(gameName = "不存在").copy(id = 999999)
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
    fun getRecordsByDate_flowEmitsInsertedAndIsolatesDates() = runBlocking {
        assertEquals(0, repo.getRecordsByDate("2026-11-15").first().size)
        repo.createRecord(record(gameName = "11-15场A", startTime = 1794679200000L, endTime = 1794679200000L + 3600000L))
        repo.createRecord(record(gameName = "11-15场B", startTime = 1794679200000L + 7200000L, endTime = 1794679200000L + 10800000L))
        repo.createRecord(record(gameName = "11-14场", startTime = 1794636000000L, endTime = 1794636000000L + 3600000L))
        assertEquals(listOf("11-15场A", "11-15场B"), repo.getRecordsByDate("2026-11-15").first().map { it.gameName })
        assertEquals(listOf("11-14场"), repo.getRecordsByDate("2026-11-14").first().map { it.gameName })
    }

    @Test
    fun getRecordsByDate_crossMidnight_belongsToStartDay() = runBlocking {
        // 跨午夜：上海 11-14 23:00 开始，11-15 03:00 结束——归 11-14（开始日）
        repo.createRecord(
            record(
                gameName = "跨夜场",
                startTime = 1794668400000L,
                endTime = 1794679200000L + 3600000L,
                durationMinutes = 240
            )
        )
        assertEquals(listOf("跨夜场"), repo.getRecordsByDate("2026-11-14").first().map { it.gameName })
        assertEquals(0, repo.getRecordsByDate("2026-11-15").first().size)
    }

    @Test
    fun getRecordsByDate_matchesSummaryAttribution() = runBlocking {
        // 同一批固定 epoch 数据：Repository 的日期查询与首页摘要的归属必须一致
        repo.createRecord(record(gameName = "11-15场", startTime = 1794679200000L, endTime = 1794679200000L + 3600000L))
        repo.createRecord(record(gameName = "11-14场", startTime = 1794636000000L, endTime = 1794636000000L + 3600000L))
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
        // 摘要（11-15）只计 1 条；Repository 的 11-15 日期查询也是 1 条，且为同一条
        assertEquals(1, summaries.game.recordCount)
        assertEquals(60, summaries.game.totalMinutes)
        assertEquals(listOf("11-15场"), repo.getRecordsByDate("2026-11-15").first().map { it.gameName })
        assertEquals(listOf("11-14场"), repo.getRecordsByDate("2026-11-14").first().map { it.gameName })
    }

    @Test
    fun getAllRecords_flowReflectsInsertsAndDeletes() = runBlocking {
        assertEquals(0, repo.getAllRecords().first().size)
        val id = (repo.createRecord(record(gameName = "A")) as RepoResult.Success).data.toInt()
        repo.createRecord(record(gameName = "B", startTime = 1794636000000L, endTime = 1794636000000L + 3600000L))
        assertEquals(2, repo.getAllRecords().first().size)
        repo.deleteRecordById(id)
        assertEquals(listOf("B"), repo.getAllRecords().first().map { it.gameName })
    }

    /**
     * 数据库异常测试替身：DAO 抛异常时 Repository 必须返回 Database 错误，不抛到调用方。
     * （说明：Room 关闭后会重开连接而非抛异常，因此不能用“关闭数据库”模拟异常；
     * 用抛异常的 DAO 替身直接验证 Repository 的异常包装。）
     */
    private class ThrowingGameDao : GameRecordDao {
        override fun getAllRecords() = emptyFlow<List<GameEntity>>()
        override suspend fun getRecordById(id: Int): GameEntity? =
            throw RuntimeException("模拟数据库异常")
        override suspend fun insertRecord(record: GameEntity): Long =
            throw RuntimeException("模拟数据库异常")
        override suspend fun updateRecord(record: GameEntity): Int =
            throw RuntimeException("模拟数据库异常")
        override suspend fun deleteRecord(record: GameEntity): Int =
            throw RuntimeException("模拟数据库异常")
        override suspend fun deleteRecordById(id: Int): Int =
            throw RuntimeException("模拟数据库异常")
        override fun getRecordsByStartTimeRange(dayStart: Long, dayEnd: Long) =
            emptyFlow<List<GameEntity>>()
    }

    @Test
    fun createRecord_daoThrows_returnsDatabaseError() = runBlocking {
        val brokenRepo = GameRepository(ThrowingGameDao()) { zone }
        val result = brokenRepo.createRecord(record())
        assertTrue(result is RepoResult.Failure)
        assertTrue((result as RepoResult.Failure).error is RepoError.Database)
    }

    @Test
    fun getRecordById_daoThrows_returnsDatabaseError() = runBlocking {
        val brokenRepo = GameRepository(ThrowingGameDao()) { zone }
        val result = brokenRepo.getRecordById(1)
        assertTrue(result is RepoResult.Failure)
        assertTrue((result as RepoResult.Failure).error is RepoError.Database)
    }

    /**
     * 取消异常测试替身：DAO 抛 CancellationException 时 Repository 必须原样向上抛出，
     * 不得包装为数据库错误（否则协程取消会被吞掉，调用方无法感知取消）。
     */
    private class CancellingGameDao : GameRecordDao {
        override fun getAllRecords() = emptyFlow<List<GameEntity>>()
        override suspend fun getRecordById(id: Int): GameEntity? =
            throw CancellationException("模拟协程取消")
        override suspend fun insertRecord(record: GameEntity): Long =
            throw CancellationException("模拟协程取消")
        override suspend fun updateRecord(record: GameEntity): Int =
            throw CancellationException("模拟协程取消")
        override suspend fun deleteRecord(record: GameEntity): Int =
            throw CancellationException("模拟协程取消")
        override suspend fun deleteRecordById(id: Int): Int =
            throw CancellationException("模拟协程取消")
        override fun getRecordsByStartTimeRange(dayStart: Long, dayEnd: Long) =
            emptyFlow<List<GameEntity>>()
    }

    @Test(expected = CancellationException::class)
    fun createRecord_cancellationException_propagates() {
        runBlocking {
            val cancellingRepo = GameRepository(CancellingGameDao()) { zone }
            cancellingRepo.createRecord(record())
        }
    }

    @Test(expected = CancellationException::class)
    fun getRecordById_cancellationException_propagates() {
        runBlocking {
            val cancellingRepo = GameRepository(CancellingGameDao()) { zone }
            cancellingRepo.getRecordById(1)
        }
    }
}
