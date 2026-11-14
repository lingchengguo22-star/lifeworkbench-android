package com.example.lifeworkbench.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.lifeworkbench.data.entities.*

@Database(entities = [
    TaskEntity::class, 
    StudyRecordEntity::class, 
    DietEntity::class, 
    FitnessEntity::class, 
    GameEntity::class, 
    SleepEntity::class
], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        /**
         * V1 → V2 迁移（V1.1 阶段 1）：tasks 表新增开始/结束时间列。
         * 两列均为可空、无默认值，历史数据行迁移后自动为 NULL，不丢失任何历史数据。
         * 不使用 destructive migration。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN startMinute INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN endMinute INTEGER")
            }
        }

        /** 应用内统一的数据库构建入口（已注册 V1 → V2 迁移）。 */
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "lifeworkbench.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
