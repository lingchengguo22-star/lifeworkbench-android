package com.example.lifeworkbench.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.lifeworkbench.data.entities.FitnessEntity
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
 * FitnessRepository 测试（V1.1 阶段 3.0）。
 * 真实 Room 内存数据库 + Robolectric：合法数据读写、不存在的记录、
 * 更新/删除影响行数、数据库异常、Flow 数据刷新、多日期数据隔离。
 * 3.0 不做业务校验（FitnessValidator 在 3.2 建立，届时区分力量/有氧/拉伸）。
 */
@RunWith(RobolectricTestRunner::class)
class FitnessRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: FitnessRepository

    private fun record(
        date: String = "2026-11-15",
        type: String = "力量",
        actionName: String = "深蹲",
        weight: Float? = 60f,
        reps: Int? = 10,
        sets: Int? = 3,
        durationMinutes: Int? = 30
    ) = FitnessEntity(
        date = date,
        type = type,
        actionName = actionName,
        weight = weight,
        reps = reps,
        sets = sets,
        durationMinutes = durationMinutes
    )

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = FitnessRepository(db.fitnessRecordDao())
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
        assertEquals("深蹲", (got as RepoResult.Success).data.actionName)
        assertEquals(60f, got.data.weight)
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
        val result = repo.updateRecord(record(weight = 70f, reps = 12).copy(id = id))
        assertTrue(result is RepoResult.Success)
        val kept = repo.getRecordById(id) as RepoResult.Success
        assertEquals(70f, kept.data.weight)
        assertEquals(12, kept.data.reps)
    }

    @Test
    fun updateRecord_missing_returnsNotFound() = runBlocking {
        val result = repo.updateRecord(record(actionName = "不存在").copy(id = 999999))
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
        val ghost = record(actionName = "不存在").copy(id = 999999)
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
        repo.createRecord(record(date = "2026-11-15", actionName = "今天A"))
        repo.createRecord(record(date = "2026-11-15", actionName = "今天B"))
        repo.createRecord(record(date = "2026-11-14", actionName = "昨天"))
        assertEquals(listOf("今天A", "今天B"), repo.getRecordsByDate("2026-11-15").first().map { it.actionName })
        assertEquals(listOf("昨天"), repo.getRecordsByDate("2026-11-14").first().map { it.actionName })
    }

    @Test
    fun getAllRecords_flowReflectsInsertsAndDeletes() = runBlocking {
        assertEquals(0, repo.getAllRecords().first().size)
        val id = (repo.createRecord(record(actionName = "A")) as RepoResult.Success).data.toInt()
        repo.createRecord(record(date = "2026-11-14", actionName = "B"))
        assertEquals(2, repo.getAllRecords().first().size)
        repo.deleteRecordById(id)
        assertEquals(listOf("B"), repo.getAllRecords().first().map { it.actionName })
    }

    @Test
    fun createRecord_databaseClosed_returnsDatabaseError() = runBlocking {
        val brokenDb = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val brokenRepo = FitnessRepository(brokenDb.fitnessRecordDao())
        brokenDb.close()
        val result = brokenRepo.createRecord(record())
        assertTrue(result is RepoResult.Failure)
        assertTrue((result as RepoResult.Failure).error is RepoError.Database)
    }

    @Test
    fun getRecordById_databaseClosed_returnsDatabaseError() = runBlocking {
        val brokenDb = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val brokenRepo = FitnessRepository(brokenDb.fitnessRecordDao())
        brokenDb.close()
        val result = brokenRepo.getRecordById(1)
        assertTrue(result is RepoResult.Failure)
        assertTrue((result as RepoResult.Failure).error is RepoError.Database)
    }
}
