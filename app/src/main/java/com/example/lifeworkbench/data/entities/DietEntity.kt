package com.example.lifeworkbench.data.entities
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "diet_records")
data class DietEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val mealType: String, // 早餐、午餐、晚餐、加餐
    val foodName: String,
    val weightGrams: Float,
    val calories: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float
)
