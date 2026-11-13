package com.example.lifeworkbench.ui.screens
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("数据与设置", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { /* 备份逻辑 */ }) { Text("手动备份数据") }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { /* 恢复逻辑 */ }) { Text("从备份恢复") }
    }
}
