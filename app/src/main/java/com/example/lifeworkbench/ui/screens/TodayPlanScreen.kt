package com.example.lifeworkbench.ui.screens
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TodayPlanScreen() {
    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("今日计划", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text("时间轴视图 - 暂无任务")
    }
}
