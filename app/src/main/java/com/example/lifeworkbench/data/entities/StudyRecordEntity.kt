package com.example.lifeworkbench.data.entities
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "study_records")
data class StudyRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val durationMinutes: Int,
    val content: String,
    val knowledgePointId: Int?
)
