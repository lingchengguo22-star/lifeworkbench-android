package com.example.lifeworkbench.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.example.lifeworkbench.LifeWorkbenchApp
import com.example.lifeworkbench.data.entities.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 阶段 2.0：数据库生产环境接入验证（Robolectric + 真实 Room）。
 *
 * 覆盖：
 * - Application 提供唯一的数据库实例与 TaskRepository（重复获取为同一实例）。
 * - Application 提供的 Repository 能实际读取 Room 数据。
 * - 关闭再打开后数据仍存在（持久化）。
 * - 生产构建入口 AppDatabase.build() 能把真实 V1 数据库文件升级到 V2 且不丢失历史数据
 *   （V1.0 APK 从不创建数据库——见下方说明——因此用真实 V1 Schema 建库文件做夹具）。
 *
 * 关于 V1.0 APK 与迁移的说明（阶段 2.0 审计结论）：
 * 基线 V1.0（b96c4ef6）的生产代码中没有任何调用 Room 构建器的地方
 * （AppDatabase 当时连 build() 入口都没有，UI 也不触碰数据库），
 * 因此真实 V1.0 APK 在用户手机上根本不会创建数据库文件，升级时无 V1 数据库可迁移，
 * 首次打开直接创建 V2 数据库。本测试用真实 V1 Schema（1.json，身份哈希
 * 5aa61ea14f184e975886f9ff91dee775）建库文件，验证生产构建入口的迁移路径本身正确。
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseProductionTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getDatabasePath(DB_NAME).delete()
    }

    @After
    fun tearDown() {
        context.getDatabasePath(DB_NAME).delete()
    }

    @Test
    fun application_providesSingleDatabaseAndRepositoryInstance() {
        val app = ApplicationProvider.getApplicationContext<LifeWorkbenchApp>()
        assertSame(app.database, app.database)
        assertSame(app.taskRepository, app.taskRepository)
    }

    @Test
    fun applicationRepository_readsRealRoomData() = runTest {
        val app = ApplicationProvider.getApplicationContext<LifeWorkbenchApp>()
        val id = app.database.taskDao().insertTask(
            TaskEntity(
                title = "学习任务", description = null, planDate = "2026-11-15",
                category = "学习", priority = 2, status = "待开始", sourceId = null
            )
        )
        val result = app.taskRepository.getTaskById(id.toInt())
        assertTrue(result is TaskResult.Success)
        assertEquals("学习任务", (result as TaskResult.Success).data.title)
    }

    @Test
    fun database_persistsAcrossCloseAndReopen() = runTest {
        val db1 = AppDatabase.build(context)
        val repo = TaskRepository(db1.taskDao())
        val id = (repo.createTask(
            TaskEntity(
                title = "持久化任务", description = null, planDate = "2026-11-15",
                category = "学习", priority = 1, status = "待开始", sourceId = null
            )
        ) as TaskResult.Success).data
        db1.close()
        val db2 = AppDatabase.build(context)
        try {
            val got = db2.taskDao().getTaskById(id.toInt())
            assertEquals("持久化任务", got?.title)
        } finally {
            db2.close()
        }
    }

    @Test
    fun productionBuilder_migratesRealV1Database() = runTest {
        createV1DatabaseFile()
        // 生产构建入口（与应用启动时同一路径）：打开 V1 文件，执行 MIGRATION_1_2
        val db = AppDatabase.build(context)
        try {
            val tasks = db.taskDao().getTasksByDate("2026-11-15").first()
            assertEquals(2, tasks.size)
            assertEquals("旧任务", tasks[0].title)
            assertEquals("待开始", tasks[0].status)
            assertNull(tasks[0].startMinute)
            assertNull(tasks[0].endMinute)
            assertEquals("已完成任务", tasks[1].title)
            assertEquals("已完成", tasks[1].status)
            assertNull(tasks[1].startMinute)
            assertNull(tasks[1].endMinute)
        } finally {
            db.close()
        }
    }

    /**
     * 按真实 V1 Schema（1.json）创建版本 1 的数据库文件，含 room_master_table 与 V1 身份哈希，
     * 模拟一台装有真实 V1 Room 数据库的设备。
     */
    private fun createV1DatabaseFile() {
        val path = context.getDatabasePath(DB_NAME)
        path.parentFile?.mkdirs()
        if (path.exists()) path.delete()
        val helper = object : SQLiteOpenHelper(context, DB_NAME, null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS tasks (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `description` TEXT, `planDate` TEXT NOT NULL, `category` TEXT NOT NULL, `priority` INTEGER NOT NULL, `status` TEXT NOT NULL, `sourceId` INTEGER)")
                db.execSQL("CREATE TABLE IF NOT EXISTS study_records (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `durationMinutes` INTEGER NOT NULL, `content` TEXT NOT NULL, `knowledgePointId` INTEGER)")
                db.execSQL("CREATE TABLE IF NOT EXISTS diet_records (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `mealType` TEXT NOT NULL, `foodName` TEXT NOT NULL, `weightGrams` REAL NOT NULL, `calories` REAL NOT NULL, `protein` REAL NOT NULL, `carbs` REAL NOT NULL, `fat` REAL NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS fitness_records (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `type` TEXT NOT NULL, `actionName` TEXT NOT NULL, `weight` REAL, `reps` INTEGER, `sets` INTEGER, `durationMinutes` INTEGER)")
                db.execSQL("CREATE TABLE IF NOT EXISTS game_records (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `gameName` TEXT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER, `durationMinutes` INTEGER NOT NULL, `recordType` TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sleep_records (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER, `quality` TEXT, `wakeUpCount` INTEGER NOT NULL, `status` TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
                db.execSQL("INSERT INTO room_master_table (id, identity_hash) VALUES (42, '5aa61ea14f184e975886f9ff91dee775')")
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }
        val db = helper.writableDatabase
        db.execSQL("INSERT INTO tasks (title, description, planDate, category, priority, status, sourceId) VALUES ('旧任务', NULL, '2026-11-15', '学习', 2, '待开始', NULL)")
        db.execSQL("INSERT INTO tasks (title, description, planDate, category, priority, status, sourceId) VALUES ('已完成任务', '描述', '2026-11-15', '健身', 1, '已完成', NULL)")
        db.close()
        helper.close()
    }

    companion object {
        private const val DB_NAME = "lifeworkbench.db"
    }
}
