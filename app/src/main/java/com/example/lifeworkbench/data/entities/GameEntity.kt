package com.example.lifeworkbench.data.entities
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "game_records")
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val gameName: String,
    val startTime: Long,
    val endTime: Long?,
    val durationMinutes: Int,
    val recordType: String // 自动计时、手动补录
)
