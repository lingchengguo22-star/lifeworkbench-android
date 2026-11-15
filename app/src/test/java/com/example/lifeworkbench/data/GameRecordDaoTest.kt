package com.example.lifeworkbench.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.lifeworkbench.data.entities.GameEntity
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
 * GameRecordDao 测试（V1.1 阶段 3.0）。
 * 真实 Room 内存数据库 + Robolectric：插入、按 ID 查询、更新、删除、
 * 按开始时间区间查询（日期归属：游戏归开始日，与首页摘要规则一致）、
 * 全部记录排序、Flow 更新、跨午夜归属。
 *
 * 固定 epoch（Asia/Shanghai 时区）：
 * 1794679200000L = 2026-11-14T18:00:00Z = 上海 2026-11-15 02:00
 * 1794636000000L = 2026-11-14T06:00:00Z = 上海 2026-11-14 14:00
 */
@RunWith(RobolectricTestRunner::class)
class GameRecordDaoTest {

    private lateinit var db: AppDatabase

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

    /** 本地日期换算为 epoch 区间 [dayStart, dayEnd)，与 GameRepository 的换算一致。 */
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
        val dao = db.gameRecordDao()
        val id = dao.insertRecord(record())
        assertTrue(id > 0)
        val found = dao.getRecordById(id.toInt())
        assertEquals("游戏A", found?.gameName)
        assertEquals(1794679200000L, found?.startTime)
        assertEquals(1794679200000L + 3600000L, found?.endTime)
        assertEquals(60, found?.durationMinutes)
        assertEquals("手动补录", found?.recordType)
    }

    @Test
    fun getRecordById_missing_returnsNull() = runBlocking {
        val dao = db.gameRecordDao()
        assertNull(dao.getRecordById(999999))
    }

    @Test
    fun updateRecord_updatesFieldsAndReturnsRowCount() = runBlocking {
        val dao = db.gameRecordDao()
        val id = dao.insertRecord(record(gameName = "原名", durationMinutes = 30))
        val saved = dao.getRecordById(id.toInt())!!
        assertEquals(1, dao.updateRecord(saved.copy(gameName = "改后名", durationMinutes = 90)))
        val updated = dao.getRecordById(id.toInt())!!
        assertEquals("改后名", updated.gameName)
        assertEquals(90, updated.durationMinutes)
        // 不存在的记录：影响 0 行，不抛异常
        assertEquals(0, dao.updateRecord(saved.copy(id = 999999, gameName = "不存在")))
    }

    @Test
    fun deleteRecord_removesRowAndReturnsRowCount() = runBlocking {
        val dao = db.gameRecordDao()
        val id = dao.insertRecord(record(gameName = "要删"))
        dao.insertRecord(record(gameName = "保留"))
        val saved = dao.getRecordById(id.toInt())!!
        assertEquals(1, dao.deleteRecord(saved))
        assertNull(dao.getRecordById(id.toInt()))
        assertEquals(listOf("保留"), dao.getAllRecords().first().map { it.gameName })
        // 已删过的同一实体再删：影响 0 行
        assertEquals(0, dao.deleteRecord(saved))
    }

    @Test
    fun deleteRecordById_removesRowAndReturnsRowCount() = runBlocking {
        val dao = db.gameRecordDao()
        val id = dao.insertRecord(record(gameName = "按ID删"))
        assertEquals(1, dao.deleteRecordById(id.toInt()))
        assertNull(dao.getRecordById(id.toInt()))
        assertEquals(0, dao.deleteRecordById(id.toInt()))
        assertEquals(0, dao.deleteRecordById(999999))
    }

    @Test
    fun getRecordsByStartTimeRange_filtersByStartDay() = runBlocking {
        val dao = db.gameRecordDao()
        dao.insertRecord(record(gameName = "11-14场", startTime = 1794636000000L, endTime = 1794636000000L + 3600000L))
        dao.insertRecord(record(gameName = "11-15场A", startTime = 1794679200000L, endTime = 1794679200000L + 3600000L))
        dao.insertRecord(record(gameName = "11-15场B", startTime = 1794679200000L + 7200000L, endTime = 1794679200000L + 10800000L))
        val (start15, end15) = dayRange("2026-11-15")
        val day15 = dao.getRecordsByStartTimeRange(start15, end15).first()
        assertEquals(listOf("11-15场A", "11-15场B"), day15.map { it.gameName })
        val (start14, end14) = dayRange("2026-11-14")
        assertEquals(listOf("11-14场"), dao.getRecordsByStartTimeRange(start14, end14).first().map { it.gameName })
        val (start16, end16) = dayRange("2026-11-16")
        assertEquals(0, dao.getRecordsByStartTimeRange(start16, end16).first().size)
    }

    @Test
    fun getRecordsByStartTimeRange_crossMidnight_belongsToStartDay() = runBlocking {
        val dao = db.gameRecordDao()
        // 跨午夜：上海 11-14 23:30 开始，11-15 01:30 结束——归 11-14（开始日）
        val crossStart = 1794668400000L // 上海 2026-11-14 23:00
        val crossEnd = 1794679200000L + 3600000L // 上海 2026-11-15 03:00
        dao.insertRecord(record(gameName = "跨夜场", startTime = crossStart, endTime = crossEnd, durationMinutes = 240))
        val (start14, end14) = dayRange("2026-11-14")
        assertEquals(listOf("跨夜场"), dao.getRecordsByStartTimeRange(start14, end14).first().map { it.gameName })
        val (start15, end15) = dayRange("2026-11-15")
        assertEquals(0, dao.getRecordsByStartTimeRange(start15, end15).first().size)
    }

    @Test
    fun getAllRecords_ordersByStartTimeAsc() = runBlocking {
        val dao = db.gameRecordDao()
        dao.insertRecord(record(gameName = "晚", startTime = 1794679200000L + 7200000L))
        dao.insertRecord(record(gameName = "早", startTime = 1794636000000L))
        dao.insertRecord(record(gameName = "中", startTime = 1794679200000L))
        val all = dao.getAllRecords().first().map { it.gameName }
        assertEquals(listOf("早", "中", "晚"), all)
    }

    @Test
    fun getAllRecords_flowReflectsInserts() = runBlocking {
        val dao = db.gameRecordDao()
        assertEquals(0, dao.getAllRecords().first().size)
        dao.insertRecord(record(gameName = "新记录"))
        assertEquals(listOf("新记录"), dao.getAllRecords().first().map { it.gameName })
    }

    @Test
    fun getAllRecords_flowReflectsDeletes() = runBlocking {
        val dao = db.gameRecordDao()
        val id = dao.insertRecord(record(gameName = "待删"))
        dao.insertRecord(record(gameName = "保留"))
        assertEquals(2, dao.getAllRecords().first().size)
        dao.deleteRecordById(id.toInt())
        assertEquals(listOf("保留"), dao.getAllRecords().first().map { it.gameName })
    }

    @Test
    fun inProgressRecord_endTimeNull_roundTrip() = runBlocking {
        val dao = db.gameRecordDao()
        // 计时中的记录：endTime 为空，durationMinutes 按约定填 0
        val id = dao.insertRecord(record(gameName = "进行中", endTime = null, durationMinutes = 0, recordType = "自动计时"))
        val found = dao.getRecordById(id.toInt())!!
        assertNull(found.endTime)
        assertEquals(0, found.durationMinutes)
        assertEquals("自动计时", found.recordType)
    }
}
