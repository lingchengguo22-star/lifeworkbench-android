package com.example.lifeworkbench.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.lifeworkbench.data.entities.DietEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * DietRecordDao 测试（V1.1 阶段 3.0）。
 * 真实 Room 内存数据库 + Robolectric：插入、按 ID 查询、更新、删除、
 * 按日期筛选、全部记录排序、Flow 更新、多日期数据隔离、小数精度。
 */
@RunWith(RobolectricTestRunner::class)
class DietRecordDaoTest {

    private lateinit var db: AppDatabase

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
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insert_returnsIdAndPersists() = runBlocking {
        val dao = db.dietRecordDao()
        val id = dao.insertRecord(record())
        assertTrue(id > 0)
        val found = dao.getRecordById(id.toInt())
        assertEquals("鸡蛋", found?.foodName)
        assertEquals("早餐", found?.mealType)
        assertEquals(100f, found?.weightGrams)
        assertEquals(155f, found?.calories)
        assertEquals(13f, found?.protein)
        assertEquals(1.1f, found?.carbs)
        assertEquals(11f, found?.fat)
    }

    @Test
    fun getRecordById_missing_returnsNull() = runBlocking {
        val dao = db.dietRecordDao()
        assertNull(dao.getRecordById(999999))
    }

    @Test
    fun updateRecord_updatesFieldsAndReturnsRowCount() = runBlocking {
        val dao = db.dietRecordDao()
        val id = dao.insertRecord(record(weightGrams = 100f, calories = 155f))
        val saved = dao.getRecordById(id.toInt())!!
        assertEquals(1, dao.updateRecord(saved.copy(weightGrams = 200f, calories = 310f)))
        val updated = dao.getRecordById(id.toInt())!!
        assertEquals(200f, updated.weightGrams)
        assertEquals(310f, updated.calories)
        // 不存在的记录：影响 0 行，不抛异常
        assertEquals(0, dao.updateRecord(saved.copy(id = 999999, calories = 0f)))
    }

    @Test
    fun deleteRecord_removesRowAndReturnsRowCount() = runBlocking {
        val dao = db.dietRecordDao()
        val id = dao.insertRecord(record(foodName = "要删"))
        dao.insertRecord(record(foodName = "保留"))
        val saved = dao.getRecordById(id.toInt())!!
        assertEquals(1, dao.deleteRecord(saved))
        assertNull(dao.getRecordById(id.toInt()))
        assertEquals(listOf("保留"), dao.getRecordsByDate("2026-11-15").first().map { it.foodName })
        // 已删过的同一实体再删：影响 0 行
        assertEquals(0, dao.deleteRecord(saved))
    }

    @Test
    fun deleteRecordById_removesRowAndReturnsRowCount() = runBlocking {
        val dao = db.dietRecordDao()
        val id = dao.insertRecord(record(foodName = "按ID删"))
        assertEquals(1, dao.deleteRecordById(id.toInt()))
        assertNull(dao.getRecordById(id.toInt()))
        assertEquals(0, dao.deleteRecordById(id.toInt()))
        assertEquals(0, dao.deleteRecordById(999999))
    }

    @Test
    fun getRecordsByDate_filtersByDate() = runBlocking {
        val dao = db.dietRecordDao()
        dao.insertRecord(record(date = "2026-11-14", foodName = "昨天"))
        dao.insertRecord(record(date = "2026-11-15", foodName = "今天A"))
        dao.insertRecord(record(date = "2026-11-15", foodName = "今天B"))
        dao.insertRecord(record(date = "2026-11-16", foodName = "明天"))
        val today = dao.getRecordsByDate("2026-11-15").first()
        assertEquals(listOf("今天A", "今天B"), today.map { it.foodName })
        assertEquals(0, dao.getRecordsByDate("2026-11-17").first().size)
    }

    @Test
    fun getAllRecords_ordersByDateDescThenIdDesc() = runBlocking {
        val dao = db.dietRecordDao()
        dao.insertRecord(record(date = "2026-11-14", foodName = "旧A"))
        dao.insertRecord(record(date = "2026-11-16", foodName = "新"))
        dao.insertRecord(record(date = "2026-11-14", foodName = "旧B"))
        dao.insertRecord(record(date = "2026-11-15", foodName = "中"))
        val all = dao.getAllRecords().first().map { it.foodName }
        assertEquals(listOf("新", "中", "旧B", "旧A"), all)
    }

    @Test
    fun getRecordsByDate_flowReflectsInserts() = runBlocking {
        val dao = db.dietRecordDao()
        assertEquals(0, dao.getRecordsByDate("2026-11-15").first().size)
        dao.insertRecord(record(foodName = "新记录"))
        assertEquals(listOf("新记录"), dao.getRecordsByDate("2026-11-15").first().map { it.foodName })
    }

    @Test
    fun getAllRecords_flowReflectsDeletes() = runBlocking {
        val dao = db.dietRecordDao()
        val id = dao.insertRecord(record(foodName = "待删"))
        dao.insertRecord(record(foodName = "保留"))
        assertEquals(2, dao.getAllRecords().first().size)
        dao.deleteRecordById(id.toInt())
        assertEquals(listOf("保留"), dao.getAllRecords().first().map { it.foodName })
    }

    @Test
    fun floatFields_preserveDecimalPrecision() = runBlocking {
        val dao = db.dietRecordDao()
        val id = dao.insertRecord(record(calories = 123.6f, protein = 4.25f, carbs = 0.05f))
        val found = dao.getRecordById(id.toInt())!!
        assertEquals(123.6f, found.calories, 0.001f)
        assertEquals(4.25f, found.protein, 0.001f)
        assertEquals(0.05f, found.carbs, 0.001f)
    }
}
