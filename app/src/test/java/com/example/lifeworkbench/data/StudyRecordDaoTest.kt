package com.example.lifeworkbench.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.lifeworkbench.data.entities.StudyRecordEntity
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
 * StudyRecordDao 测试（V1.1 阶段 3.0）。
 * 真实 Room 内存数据库 + Robolectric：插入、按 ID 查询、更新、删除、
 * 按日期筛选、全部记录排序、Flow 更新、多日期数据隔离。
 */
@RunWith(RobolectricTestRunner::class)
class StudyRecordDaoTest {

    private lateinit var db: AppDatabase

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
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insert_returnsIdAndPersists() = runBlocking {
        val dao = db.studyRecordDao()
        val id = dao.insertRecord(record(content = "Kotlin 学习", durationMinutes = 90))
        assertTrue(id > 0)
        val found = dao.getRecordById(id.toInt())
        assertEquals("Kotlin 学习", found?.content)
        assertEquals(90, found?.durationMinutes)
        assertEquals("2026-11-15", found?.date)
        assertNull(found?.knowledgePointId)
    }

    @Test
    fun getRecordById_missing_returnsNull() = runBlocking {
        val dao = db.studyRecordDao()
        assertNull(dao.getRecordById(999999))
    }

    @Test
    fun updateRecord_updatesFieldsAndReturnsRowCount() = runBlocking {
        val dao = db.studyRecordDao()
        val id = dao.insertRecord(record(content = "原内容", durationMinutes = 30))
        val saved = dao.getRecordById(id.toInt())!!
        assertEquals(1, dao.updateRecord(saved.copy(content = "改后内容", durationMinutes = 45)))
        val updated = dao.getRecordById(id.toInt())!!
        assertEquals("改后内容", updated.content)
        assertEquals(45, updated.durationMinutes)
        // 不存在的记录：影响 0 行，不抛异常
        assertEquals(0, dao.updateRecord(saved.copy(id = 999999, content = "不存在")))
    }

    @Test
    fun deleteRecord_removesRowAndReturnsRowCount() = runBlocking {
        val dao = db.studyRecordDao()
        val id = dao.insertRecord(record(content = "要删"))
        dao.insertRecord(record(content = "保留"))
        val saved = dao.getRecordById(id.toInt())!!
        assertEquals(1, dao.deleteRecord(saved))
        assertNull(dao.getRecordById(id.toInt()))
        assertEquals(listOf("保留"), dao.getRecordsByDate("2026-11-15").first().map { it.content })
        // 已删过的同一实体再删：影响 0 行
        assertEquals(0, dao.deleteRecord(saved))
    }

    @Test
    fun deleteRecordById_removesRowAndReturnsRowCount() = runBlocking {
        val dao = db.studyRecordDao()
        val id = dao.insertRecord(record(content = "按ID删"))
        assertEquals(1, dao.deleteRecordById(id.toInt()))
        assertNull(dao.getRecordById(id.toInt()))
        assertEquals(0, dao.deleteRecordById(id.toInt()))
        assertEquals(0, dao.deleteRecordById(999999))
    }

    @Test
    fun getRecordsByDate_filtersByDate() = runBlocking {
        val dao = db.studyRecordDao()
        dao.insertRecord(record(date = "2026-11-14", content = "昨天"))
        dao.insertRecord(record(date = "2026-11-15", content = "今天A"))
        dao.insertRecord(record(date = "2026-11-15", content = "今天B"))
        dao.insertRecord(record(date = "2026-11-16", content = "明天"))
        val today = dao.getRecordsByDate("2026-11-15").first()
        assertEquals(listOf("今天A", "今天B"), today.map { it.content })
        assertEquals(0, dao.getRecordsByDate("2026-11-17").first().size)
    }

    @Test
    fun getAllRecords_ordersByDateDescThenIdDesc() = runBlocking {
        val dao = db.studyRecordDao()
        dao.insertRecord(record(date = "2026-11-14", content = "旧A"))
        dao.insertRecord(record(date = "2026-11-16", content = "新"))
        dao.insertRecord(record(date = "2026-11-14", content = "旧B"))
        dao.insertRecord(record(date = "2026-11-15", content = "中"))
        val all = dao.getAllRecords().first().map { it.content }
        assertEquals(listOf("新", "中", "旧B", "旧A"), all)
    }

    @Test
    fun getRecordsByDate_flowReflectsInserts() = runBlocking {
        val dao = db.studyRecordDao()
        assertEquals(0, dao.getRecordsByDate("2026-11-15").first().size)
        dao.insertRecord(record(content = "新记录"))
        assertEquals(listOf("新记录"), dao.getRecordsByDate("2026-11-15").first().map { it.content })
    }

    @Test
    fun getAllRecords_flowReflectsDeletes() = runBlocking {
        val dao = db.studyRecordDao()
        val id = dao.insertRecord(record(content = "待删"))
        dao.insertRecord(record(content = "保留"))
        assertEquals(2, dao.getAllRecords().first().size)
        dao.deleteRecordById(id.toInt())
        assertEquals(listOf("保留"), dao.getAllRecords().first().map { it.content })
    }

    @Test
    fun knowledgePointId_nullableRoundTrip() = runBlocking {
        val dao = db.studyRecordDao()
        val id = dao.insertRecord(record().copy(knowledgePointId = 7))
        assertEquals(7, dao.getRecordById(id.toInt())?.knowledgePointId)
        val id2 = dao.insertRecord(record(content = "无知识点"))
        assertNull(dao.getRecordById(id2.toInt())?.knowledgePointId)
    }
}
