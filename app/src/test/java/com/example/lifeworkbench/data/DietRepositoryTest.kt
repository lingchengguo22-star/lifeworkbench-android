package com.example.lifeworkbench.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.lifeworkbench.data.entities.DietEntity
import com.example.lifeworkbench.data.entities.DietRecordDao
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
 * DietRepository 测试（V1.1 阶段 3.0）。
 * 真实 Room 内存数据库 + Robolectric：合法数据读写、不存在的记录、
 * 更新/删除影响行数、数据库异常、Flow 数据刷新、多日期数据隔离。
 * 3.0 不做业务校验（DietValidator 在 3.3 建立，届时明确单位、精度与空值策略）。
 */
@RunWith(RobolectricTestRunner::class)
class DietRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: DietRepository

    private fun record(
        date: String = "2026-11-15",
        mealType: String = "早餐",
        foodName: String = "鸡蛋",
        weightGrams: Float = 100f,
        calories: Float = 155f,
        protein: Float = 13f,
        carbs: Float = 1.1f,
        fat: Float = 11f
    ) = DietEntity(
        date = date,
        mealType = mealType,
        foodName = foodName,
        weightGrams = weightGrams,
        calories = calories,
        protein = protein,
        carbs = carbs,
        fat = fat
    )

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = DietRepository(db.dietRecordDao())
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
        assertEquals("鸡蛋", (got as RepoResult.Success).data.foodName)
        assertEquals(155f, got.data.calories)
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
        val result = repo.updateRecord(record(weightGrams = 200f, calories = 310f).copy(id = id))
        assertTrue(result is RepoResult.Success)
        val kept = repo.getRecordById(id) as RepoResult.Success
        assertEquals(200f, kept.data.weightGrams)
        assertEquals(310f, kept.data.calories)
    }

    @Test
    fun updateRecord_missing_returnsNotFound() = runBlocking {
        val result = repo.updateRecord(record(foodName = "不存在").copy(id = 999999))
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
        val ghost = record(foodName = "不存在").copy(id = 999999)
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
        repo.createRecord(record(date = "2026-11-15", foodName = "今天A"))
        repo.createRecord(record(date = "2026-11-15", foodName = "今天B"))
        repo.createRecord(record(date = "2026-11-14", foodName = "昨天"))
        assertEquals(listOf("今天A", "今天B"), repo.getRecordsByDate("2026-11-15").first().map { it.foodName })
        assertEquals(listOf("昨天"), repo.getRecordsByDate("2026-11-14").first().map { it.foodName })
    }

    @Test
    fun getAllRecords_flowReflectsInsertsAndDeletes() = runBlocking {
        assertEquals(0, repo.getAllRecords().first().size)
        val id = (repo.createRecord(record(foodName = "A")) as RepoResult.Success).data.toInt()
        repo.createRecord(record(date = "2026-11-14", foodName = "B"))
        assertEquals(2, repo.getAllRecords().first().size)
        repo.deleteRecordById(id)
        assertEquals(listOf("B"), repo.getAllRecords().first().map { it.foodName })
    }

    /**
     * 数据库异常测试替身：DAO 抛异常时 Repository 必须返回 Database 错误，不抛到调用方。
     * （说明：Room 关闭后会重开连接而非抛异常，因此不能用“关闭数据库”模拟异常；
     * 用抛异常的 DAO 替身直接验证 Repository 的异常包装。）
     */
    private class ThrowingDietDao : DietRecordDao {
        override fun getRecordsByDate(date: String) = emptyFlow<List<DietEntity>>()
        override fun getAllRecords() = emptyFlow<List<DietEntity>>()
        override suspend fun getRecordById(id: Int): DietEntity? =
            throw RuntimeException("模拟数据库异常")
        override suspend fun insertRecord(record: DietEntity): Long =
            throw RuntimeException("模拟数据库异常")
        override suspend fun updateRecord(record: DietEntity): Int =
            throw RuntimeException("模拟数据库异常")
        override suspend fun deleteRecord(record: DietEntity): Int =
            throw RuntimeException("模拟数据库异常")
        override suspend fun deleteRecordById(id: Int): Int =
            throw RuntimeException("模拟数据库异常")
    }

    @Test
    fun createRecord_daoThrows_returnsDatabaseError() = runBlocking {
        val brokenRepo = DietRepository(ThrowingDietDao())
        val result = brokenRepo.createRecord(record())
        assertTrue(result is RepoResult.Failure)
        assertTrue((result as RepoResult.Failure).error is RepoError.Database)
    }

    @Test
    fun getRecordById_daoThrows_returnsDatabaseError() = runBlocking {
        val brokenRepo = DietRepository(ThrowingDietDao())
        val result = brokenRepo.getRecordById(1)
        assertTrue(result is RepoResult.Failure)
        assertTrue((result as RepoResult.Failure).error is RepoError.Database)
    }

    /**
     * 取消异常测试替身：DAO 抛 CancellationException 时 Repository 必须原样向上抛出，
     * 不得包装为数据库错误（否则协程取消会被吞掉，调用方无法感知取消）。
     */
    private class CancellingDietDao : DietRecordDao {
        override fun getRecordsByDate(date: String) = emptyFlow<List<DietEntity>>()
        override fun getAllRecords() = emptyFlow<List<DietEntity>>()
        override suspend fun getRecordById(id: Int): DietEntity? =
            throw CancellationException("模拟协程取消")
        override suspend fun insertRecord(record: DietEntity): Long =
            throw CancellationException("模拟协程取消")
        override suspend fun updateRecord(record: DietEntity): Int =
            throw CancellationException("模拟协程取消")
        override suspend fun deleteRecord(record: DietEntity): Int =
            throw CancellationException("模拟协程取消")
        override suspend fun deleteRecordById(id: Int): Int =
            throw CancellationException("模拟协程取消")
    }

    @Test(expected = CancellationException::class)
    fun createRecord_cancellationException_propagates() = runBlocking {
        val cancellingRepo = DietRepository(CancellingDietDao())
        cancellingRepo.createRecord(record())
    }

    @Test(expected = CancellationException::class)
    fun getRecordById_cancellationException_propagates() = runBlocking {
        val cancellingRepo = DietRepository(CancellingDietDao())
        cancellingRepo.getRecordById(1)
    }
}
