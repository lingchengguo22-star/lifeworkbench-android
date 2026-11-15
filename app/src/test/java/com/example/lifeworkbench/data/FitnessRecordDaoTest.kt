package com.example.lifeworkbench.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.lifeworkbench.data.entities.FitnessEntity
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
 * FitnessRecordDao 测试（V1.1 阶段 3.0）。
 * 真实 Room 内存数据库 + Robolectric：插入、按 ID 查询、更新、删除、
 * 按日期筛选、全部记录排序、Flow 更新、多日期数据隔离、可空字段往返。
 */
@RunWith(RobolectricTestRunner::class)
class FitnessRecordDaoTest {

    private lateinit var db: AppDatabase

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
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insert_returnsIdAndPersists() = runBlocking {
        val dao = db.fitnessRecordDao()
        val id = dao.insertRecord(record())
        assertTrue(id > 0)
        val found = dao.getRecordById(id.toInt())
        assertEquals("深蹲", found?.actionName)
        assertEquals("力量", found?.type)
        assertEquals(60f, found?.weight)
        assertEquals(10, found?.reps)
        assertEquals(3, found?.sets)
        assertEquals(30, found?.durationMinutes)
    }

    @Test
    fun getRecordById_missing_returnsNull() = runBlocking {
        val dao = db.fitnessRecordDao()
        assertNull(dao.getRecordById(999999))
    }

    @Test
    fun updateRecord_updatesFieldsAndReturnsRowCount() = runBlocking {
        val dao = db.fitnessRecordDao()
        val id = dao.insertRecord(record(weight = 60f, reps = 10))
        val saved = dao.getRecordById(id.toInt())!!
        assertEquals(1, dao.updateRecord(saved.copy(weight = 70f, reps = 12)))
        val updated = dao.getRecordById(id.toInt())!!
        assertEquals(70f, updated.weight)
        assertEquals(12, updated.reps)
        // 不存在的记录：影响 0 行，不抛异常
        assertEquals(0, dao.updateRecord(saved.copy(id = 999999, weight = 80f)))
    }

    @Test
    fun deleteRecord_removesRowAndReturnsRowCount() = runBlocking {
        val dao = db.fitnessRecordDao()
        val id = dao.insertRecord(record(actionName = "要删"))
        dao.insertRecord(record(actionName = "保留"))
        val saved = dao.getRecordById(id.toInt())!!
        assertEquals(1, dao.deleteRecord(saved))
        assertNull(dao.getRecordById(id.toInt()))
        assertEquals(listOf("保留"), dao.getRecordsByDate("2026-11-15").first().map { it.actionName })
        // 已删过的同一实体再删：影响 0 行
        assertEquals(0, dao.deleteRecord(saved))
    }

    @Test
    fun deleteRecordById_removesRowAndReturnsRowCount() = runBlocking {
        val dao = db.fitnessRecordDao()
        val id = dao.insertRecord(record(actionName = "按ID删"))
        assertEquals(1, dao.deleteRecordById(id.toInt()))
        assertNull(dao.getRecordById(id.toInt()))
        assertEquals(0, dao.deleteRecordById(id.toInt()))
        assertEquals(0, dao.deleteRecordById(999999))
    }

    @Test
    fun getRecordsByDate_filtersByDate() = runBlocking {
        val dao = db.fitnessRecordDao()
        dao.insertRecord(record(date = "2026-11-14", actionName = "昨天"))
        dao.insertRecord(record(date = "2026-11-15", actionName = "今天A"))
        dao.insertRecord(record(date = "2026-11-15", actionName = "今天B"))
        dao.insertRecord(record(date = "2026-11-16", actionName = "明天"))
        val today = dao.getRecordsByDate("2026-11-15").first()
        assertEquals(listOf("今天A", "今天B"), today.map { it.actionName })
        assertEquals(0, dao.getRecordsByDate("2026-11-17").first().size)
    }

    @Test
    fun getAllRecords_ordersByDateDescThenIdDesc() = runBlocking {
        val dao = db.fitnessRecordDao()
        dao.insertRecord(record(date = "2026-11-14", actionName = "旧A"))
        dao.insertRecord(record(date = "2026-11-16", actionName = "新"))
        dao.insertRecord(record(date = "2026-11-14", actionName = "旧B"))
        dao.insertRecord(record(date = "2026-11-15", actionName = "中"))
        val all = dao.getAllRecords().first().map { it.actionName }
        assertEquals(listOf("新", "中", "旧B", "旧A"), all)
    }

    @Test
    fun getRecordsByDate_flowReflectsInserts() = runBlocking {
        val dao = db.fitnessRecordDao()
        assertEquals(0, dao.getRecordsByDate("2026-11-15").first().size)
        dao.insertRecord(record(actionName = "新记录"))
        assertEquals(listOf("新记录"), dao.getRecordsByDate("2026-11-15").first().map { it.actionName })
    }

    @Test
    fun getAllRecords_flowReflectsDeletes() = runBlocking {
        val dao = db.fitnessRecordDao()
        val id = dao.insertRecord(record(actionName = "待删"))
        dao.insertRecord(record(actionName = "保留"))
        assertEquals(2, dao.getAllRecords().first().size)
        dao.deleteRecordById(id.toInt())
        assertEquals(listOf("保留"), dao.getAllRecords().first().map { it.actionName })
    }

    @Test
    fun nullableFields_roundTrip() = runBlocking {
        val dao = db.fitnessRecordDao()
        // 有氧训练不填重量、次数、组数：可空字段原样保存为 null
        val id = dao.insertRecord(
            record(type = "有氧", actionName = "跑步", weight = null, reps = null, sets = null, durationMinutes = 40)
        )
        val found = dao.getRecordById(id.toInt())!!
        assertNull(found.weight)
        assertNull(found.reps)
        assertNull(found.sets)
        assertEquals(40, found.durationMinutes)
    }
}
