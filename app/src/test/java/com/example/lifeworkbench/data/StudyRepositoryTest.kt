package com.example.lifeworkbench.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.lifeworkbench.data.entities.StudyRecordDao
import com.example.lifeworkbench.data.entities.StudyRecordEntity
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

/**
 * StudyRepository 测试（V1.1 阶段 3.0）。
 * 真实 Room 内存数据库 + Robolectric：合法数据读写、不存在的记录、
 * 更新/删除影响行数、数据库异常、Flow 数据刷新、多日期数据隔离。
 * 3.0 不做业务校验（StudyValidator 在 3.1 建立）。
 */
@RunWith(RobolectricTestRunner::class)
class StudyRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: StudyRepository

    private fun record(
        date: String = "2026-11-15",
        durationMinutes: Int = 60,
        content: String = "学习内容"
    ) = StudyRecordEntity(
        date = date,
        durationMinutes = durationMinutes,
        content = content,
        knowledgePointId = null
    )

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = StudyRepository(db.studyRecordDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun createRecord_valid_returnsIdAndPersists() = runBlocking {
        val result = repo.createRecord(record(content = "Kotlin 学习", durationMinutes = 90))
        assertTrue(result is RepoResult.Success)
        val id = (result as RepoResult.Success).data
        assertTrue(id > 0)
        val got = repo.getRecordById(id.toInt())
        assertTrue(got is RepoResult.Success)
        assertEquals("Kotlin 学习", (got as RepoResult.Success).data.content)
        assertEquals(90, got.data.durationMinutes)
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
        val result = repo.updateRecord(record(content = "改后内容", durationMinutes = 45).copy(id = id))
        assertTrue(result is RepoResult.Success)
        val kept = repo.getRecordById(id) as RepoResult.Success
        assertEquals("改后内容", kept.data.content)
        assertEquals(45, kept.data.durationMinutes)
    }

    @Test
    fun updateRecord_missing_returnsNotFound() = runBlocking {
        val result = repo.updateRecord(record(content = "不存在").copy(id = 999999))
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
        val ghost = record(content = "不存在").copy(id = 999999)
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
        repo.createRecord(record(date = "2026-11-15", content = "今天A"))
        repo.createRecord(record(date = "2026-11-15", content = "今天B"))
        repo.createRecord(record(date = "2026-11-14", content = "昨天"))
        assertEquals(listOf("今天A", "今天B"), repo.getRecordsByDate("2026-11-15").first().map { it.content })
        assertEquals(listOf("昨天"), repo.getRecordsByDate("2026-11-14").first().map { it.content })
    }

    @Test
    fun getAllRecords_flowReflectsInsertsAndDeletes() = runBlocking {
        assertEquals(0, repo.getAllRecords().first().size)
        val id = (repo.createRecord(record(content = "A")) as RepoResult.Success).data.toInt()
        repo.createRecord(record(date = "2026-11-14", content = "B"))
        assertEquals(2, repo.getAllRecords().first().size)
        repo.deleteRecordById(id)
        assertEquals(listOf("B"), repo.getAllRecords().first().map { it.content })
    }

    /**
     * 数据库异常测试替身：DAO 抛异常时 Repository 必须返回 Database 错误，不抛到调用方。
     * （说明：Room 关闭后会重开连接而非抛异常，因此不能用“关闭数据库”模拟异常；
     * 用抛异常的 DAO 替身直接验证 Repository 的异常包装。）
     */
    private class ThrowingStudyDao : StudyRecordDao {
        override fun getRecordsByDate(date: String) = emptyFlow<List<StudyRecordEntity>>()
        override fun getAllRecords() = emptyFlow<List<StudyRecordEntity>>()
        override suspend fun getRecordById(id: Int): StudyRecordEntity? =
            throw RuntimeException("模拟数据库异常")
        override suspend fun insertRecord(record: StudyRecordEntity): Long =
            throw RuntimeException("模拟数据库异常")
        override suspend fun updateRecord(record: StudyRecordEntity): Int =
            throw RuntimeException("模拟数据库异常")
        override suspend fun deleteRecord(record: StudyRecordEntity): Int =
            throw RuntimeException("模拟数据库异常")
        override suspend fun deleteRecordById(id: Int): Int =
            throw RuntimeException("模拟数据库异常")
    }

    @Test
    fun createRecord_daoThrows_returnsDatabaseError() = runBlocking {
        val brokenRepo = StudyRepository(ThrowingStudyDao())
        val result = brokenRepo.createRecord(record())
        assertTrue(result is RepoResult.Failure)
        assertTrue((result as RepoResult.Failure).error is RepoError.Database)
    }

    @Test
    fun getRecordById_daoThrows_returnsDatabaseError() = runBlocking {
        val brokenRepo = StudyRepository(ThrowingStudyDao())
        val result = brokenRepo.getRecordById(1)
        assertTrue(result is RepoResult.Failure)
        assertTrue((result as RepoResult.Failure).error is RepoError.Database)
    }

    /**
     * 取消异常测试替身：DAO 抛 CancellationException 时 Repository 必须原样向上抛出，
     * 不得包装为数据库错误（否则协程取消会被吞掉，调用方无法感知取消）。
     */
    private class CancellingStudyDao : StudyRecordDao {
        override fun getRecordsByDate(date: String) = emptyFlow<List<StudyRecordEntity>>()
        override fun getAllRecords() = emptyFlow<List<StudyRecordEntity>>()
        override suspend fun getRecordById(id: Int): StudyRecordEntity? =
            throw CancellationException("模拟协程取消")
        override suspend fun insertRecord(record: StudyRecordEntity): Long =
            throw CancellationException("模拟协程取消")
        override suspend fun updateRecord(record: StudyRecordEntity): Int =
            throw CancellationException("模拟协程取消")
        override suspend fun deleteRecord(record: StudyRecordEntity): Int =
            throw CancellationException("模拟协程取消")
        override suspend fun deleteRecordById(id: Int): Int =
            throw CancellationException("模拟协程取消")
    }

    @Test(expected = CancellationException::class)
    fun createRecord_cancellationException_propagates() = runBlocking {
        val cancellingRepo = StudyRepository(CancellingStudyDao())
        cancellingRepo.createRecord(record())
    }

    @Test(expected = CancellationException::class)
    fun getRecordById_cancellationException_propagates() = runBlocking {
        val cancellingRepo = StudyRepository(CancellingStudyDao())
        cancellingRepo.getRecordById(1)
    }
}
