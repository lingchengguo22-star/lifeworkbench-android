package com.example.lifeworkbench.data.entities
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "fitness_records")
data class FitnessEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val type: String, // 力量、有氧、拉伸
    val actionName: String,
    val weight: Float?,
    val reps: Int?,
    val sets: Int?,
    val durationMinutes: Int?
)
