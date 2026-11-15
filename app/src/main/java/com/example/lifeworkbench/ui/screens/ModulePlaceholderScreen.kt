package com.example.lifeworkbench.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 模块占位页面（V1.1 阶段 2.2 / 2.3）。
 *
 * 五大业务模块（学习 / 健身 / 饮食 / 游戏 / 睡眠）完整功能属于后续阶段，
 * 本阶段首页摘要卡片点击后进入此明确占位页，不出现点击无反应的假入口。
 */
@Composable
fun ModulePlaceholderScreen(moduleName: String) {
    Column(
        modifier = Modifier.padding(16.dp).fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("$moduleName", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text("该模块尚未开发，敬请期待。", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text("首页摘要仍会展示该模块今日已保存的数据。", style = MaterialTheme.typography.bodyMedium)
    }
}
