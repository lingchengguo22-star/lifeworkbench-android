package com.example.lifeworkbench.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.lifeworkbench.data.entities.SleepEntity
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
import java.time.LocalDate
import java.time.ZoneId

/**
 * SleepRecordDao 测试（V1.1 阶段 3.0）。
 * 真实 Room 内存数据库 + Robolectric：插入、按 ID 查询、更新、删除、
 * 按日期区间查询（归属规则：已结束归起床日，计时中归入睡日，与首页摘要规则一致）、
 * 全部记录排序、Flow 更新、跨午夜归属。
 *
 * 固定 epoch（Asia/Shanghai 时区）：
 * 1794668400000L = 上海 2026-11-14 23:00（入睡），1794697200000L = 上海 2026-11-15 07:00（起床）
 * 1794582000000L = 上海 2026-11-13 23:00（入睡），1794610800000L = 上海 2026-11-14 07:00（起床）
 * 1794751200000L = 上海 2026-11-15 22:00（计时中入睡）
 */
@RunWith(RobolectricTestRunner::class)
class SleepRecordDaoTest {

    private lateinit var db: AppDatabase

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

    /** 本地日期换算为 epoch 区间 [dayStart, dayEnd)，与 SleepRepository 的换算一致。 */
    private fun dayRange(date: String): Pair<Long, Long> {
        val day = LocalDate.parse(date)
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

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
        val dao = db.sleepRecordDao()
        val id = dao.insertRecord(record())
        assertTrue(id > 0)
        val found = dao.getRecordById(id.toInt())
        assertEquals(1794668400000L, found?.startTime)
        assertEquals(1794697200000L, found?.endTime)
        assertEquals("好", found?.quality)
        assertEquals(1, found?.wakeUpCount)
        assertEquals("已完成", found?.status)
    }

    @Test
    fun getRecordById_missing_returnsNull() = runBlocking {
        val dao = db.sleepRecordDao()
        assertNull(dao.getRecordById(999999))
    }

    @Test
    fun updateRecord_updatesFieldsAndReturnsRowCount() = runBlocking {
        val dao = db.sleepRecordDao()
        val id = dao.insertRecord(record(quality = "一般", wakeUpCount = 2))
        val saved = dao.getRecordById(id.toInt())!!
        assertEquals(1, dao.updateRecord(saved.copy(quality = "很好", wakeUpCount = 0)))
        val updated = dao.getRecordById(id.toInt())!!
        assertEquals("很好", updated.quality)
        assertEquals(0, updated.wakeUpCount)
        // 不存在的记录：影响 0 行，不抛异常
        assertEquals(0, dao.updateRecord(saved.copy(id = 999999, quality = "差")))
    }

    @Test
    fun deleteRecord_removesRowAndReturnsRowCount() = runBlocking {
        val dao = db.sleepRecordDao()
        val id = dao.insertRecord(record())
        dao.insertRecord(record(startTime = 1794582000000L, endTime = 1794610800000L))
        val saved = dao.getRecordById(id.toInt())!!
        assertEquals(1, dao.deleteRecord(saved))
        assertNull(dao.getRecordById(id.toInt()))
        assertEquals(1, dao.getAllRecords().first().size)
        // 已删过的同一实体再删：影响 0 行
        assertEquals(0, dao.deleteRecord(saved))
    }

    @Test
    fun deleteRecordById_removesRowAndReturnsRowCount() = runBlocking {
        val dao = db.sleepRecordDao()
        val id = dao.insertRecord(record())
        assertEquals(1, dao.deleteRecordById(id.toInt()))
        assertNull(dao.getRecordById(id.toInt()))
        assertEquals(0, dao.deleteRecordById(id.toInt()))
        assertEquals(0, dao.deleteRecordById(999999))
    }

    @Test
    fun getRecordsByDateRange_completed_belongsToWakeUpDay() = runBlocking {
        val dao = db.sleepRecordDao()
        // 11-13 23:00 入睡 → 11-14 07:00 起床：归 11-14（起床日）
        dao.insertRecord(record(startTime = 1794582000000L, endTime = 1794610800000L))
        // 11-14 23:00 入睡 → 11-15 07:00 起床：归 11-15（起床日）
        dao.insertRecord(record(startTime = 1794668400000L, endTime = 1794697200000L))
        val (start15, end15) = dayRange("2026-11-15")
        val day15 = dao.getRecordsByDateRange(start15, end15).first()
        assertEquals(1, day15.size)
        assertEquals(1794668400000L, day15[0].startTime)
        val (start14, end14) = dayRange("2026-11-14")
        val day14 = dao.getRecordsByDateRange(start14, end14).first()
        assertEquals(1, day14.size)
        assertEquals(1794582000000L, day14[0].startTime)
        val (start16, end16) = dayRange("2026-11-16")
        assertEquals(0, dao.getRecordsByDateRange(start16, end16).first().size)
    }

    @Test
    fun getRecordsByDateRange_inProgress_belongsToStartDay() = runBlocking {
        val dao = db.sleepRecordDao()
        // 计时中：11-15 22:00 入睡，endTime 为空——归 11-15（入睡日）
        dao.insertRecord(record(startTime = 1794751200000L, endTime = null, quality = null, status = "计时中"))
        val (start15, end15) = dayRange("2026-11-15")
        assertEquals(1, dao.getRecordsByDateRange(start15, end15).first().size)
        val (start16, end16) = dayRange("2026-11-16")
        assertEquals(0, dao.getRecordsByDateRange(start16, end16).first().size)
    }

    @Test
    fun getAllRecords_ordersByStartTimeAsc() = runBlocking {
        val dao = db.sleepRecordDao()
        dao.insertRecord(record(startTime = 1794668400000L, endTime = 1794697200000L))
        dao.insertRecord(record(startTime = 1794582000000L, endTime = 1794610800000L))
        dao.insertRecord(record(startTime = 1794751200000L, endTime = null, status = "计时中"))
        val all = dao.getAllRecords().first().map { it.startTime }
        assertEquals(listOf(1794582000000L, 1794668400000L, 1794751200000L), all)
    }

    @Test
    fun getAllRecords_flowReflectsInserts() = runBlocking {
        val dao = db.sleepRecordDao()
        assertEquals(0, dao.getAllRecords().first().size)
        dao.insertRecord(record())
        assertEquals(1, dao.getAllRecords().first().size)
    }

    @Test
    fun getAllRecords_flowReflectsDeletes() = runBlocking {
        val dao = db.sleepRecordDao()
        val id = dao.insertRecord(record())
        dao.insertRecord(record(startTime = 1794582000000L, endTime = 1794610800000L))
        assertEquals(2, dao.getAllRecords().first().size)
        dao.deleteRecordById(id.toInt())
        assertEquals(listOf(1794582000000L), dao.getAllRecords().first().map { it.startTime })
    }

    @Test
    fun quality_nullableRoundTrip() = runBlocking {
        val dao = db.sleepRecordDao()
        val id = dao.insertRecord(record(quality = null))
        assertNull(dao.getRecordById(id.toInt())?.quality)
        val id2 = dao.insertRecord(record(startTime = 1794582000000L, endTime = 1794610800000L, quality = "差"))
        assertEquals("差", dao.getRecordById(id2.toInt())?.quality)
    }
}
