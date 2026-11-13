package com.example.lifeworkbench.ui.screens
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen() {
    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("首页总览", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("今日任务", style = MaterialTheme.typography.titleMedium)
                Text("完成率: 0% | 待完成: 0 | 已完成: 0")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("模块摘要", style = MaterialTheme.typography.titleMedium)
        // 学习、健身、饮食、游戏、睡眠摘要
    }
}
