package com.example.lifeworkbench.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.lifeworkbench.data.entities.*

@Database(entities = [
    TaskEntity::class, 
    StudyRecordEntity::class, 
    DietEntity::class, 
    FitnessEntity::class, 
    GameEntity::class, 
    SleepEntity::class
], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
}
