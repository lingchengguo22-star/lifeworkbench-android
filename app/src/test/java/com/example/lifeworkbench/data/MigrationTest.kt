package com.example.lifeworkbench.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * V1 → V2 迁移测试（V1.1 阶段 1）。
 * 用真实旧版本 Schema（app/schemas/.../1.json，Room 生成）创建 V1 数据库，
 * 写入旧数据后执行 MIGRATION_1_2，验证历史数据保留、新列为 NULL，
 * 并校验迁移后结构与 V2 Schema 一致。
 * 不用当前版本数据库冒充旧版本数据库；不使用 destructive migration。
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2_preservesDataAndAddsNullTimeColumns() {
        // 1. 用真实 V1 Schema 创建数据库，写入两条旧数据（一条待开始，一条已完成）
        val db = helper.createDatabase(TEST_DB_NAME, 1)
        db.execSQL(
            "INSERT INTO tasks (title, description, planDate, category, priority, status, sourceId) " +
                "VALUES ('旧任务', '旧描述', '2026-11-14', '学习', 1, '待开始', NULL)"
        )
        db.execSQL(
            "INSERT INTO tasks (title, description, planDate, category, priority, status, sourceId) " +
                "VALUES ('已完成任务', NULL, '2026-11-13', '健身', 2, '已完成', NULL)"
        )
        db.close()

        // 2. 执行迁移并校验迁移后结构与 V2 Schema 一致（不匹配会抛异常）
        val migrated = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 2, true, AppDatabase.MIGRATION_1_2
        )

        // 3. 验证历史数据保留、新列为 NULL、状态中文值不变
        val cursor = migrated.query(
            "SELECT title, description, planDate, category, priority, status, sourceId, startMinute, endMinute " +
                "FROM tasks ORDER BY id ASC"
        )
        assertEquals(2, cursor.count)
        assertTrue(cursor.moveToFirst())
        assertEquals("旧任务", cursor.getString(0))
        assertEquals("旧描述", cursor.getString(1))
        assertEquals("2026-11-14", cursor.getString(2))
        assertEquals("学习", cursor.getString(3))
        assertEquals(1, cursor.getInt(4))
        assertEquals("待开始", cursor.getString(5))
        assertTrue(cursor.isNull(6))
        assertTrue(cursor.isNull(7)) // startMinute 为 NULL
        assertTrue(cursor.isNull(8)) // endMinute 为 NULL
        assertTrue(cursor.moveToNext())
        assertEquals("已完成任务", cursor.getString(0))
        assertTrue(cursor.isNull(1))
        assertEquals("2026-11-13", cursor.getString(2))
        assertEquals("健身", cursor.getString(3))
        assertEquals(2, cursor.getInt(4))
        assertEquals("已完成", cursor.getString(5))
        assertTrue(cursor.isNull(6))
        assertTrue(cursor.isNull(7))
        assertTrue(cursor.isNull(8))
        cursor.close()
        migrated.close()
    }

    companion object {
        private const val TEST_DB_NAME = "migration-test"
    }
}
