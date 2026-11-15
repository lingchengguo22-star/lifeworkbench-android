package com.example.lifeworkbench.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lifeworkbench.LifeWorkbenchApp

/**
 * 首页总览（V1.1 阶段 2.2）。
 *
 * 今日统计与五大模块摘要全部来自真实 Room 数据库（经 ViewModel + Repository），
 * 不使用硬编码数字与假数据；完成率使用阶段 1 已验收的统一算法，不另写公式。
 * 今日任务卡片点击进入今日计划；模块摘要卡片点击进入对应模块入口
 * （模块尚未开发时为明确的占位页面，不出现点击无反应的假入口）。
 */
@Composable
fun HomeScreen(
    onNavigateToToday: () -> Unit = {},
    onNavigateToModule: (String) -> Unit = {}
) {
    val app = LocalContext.current.applicationContext as LifeWorkbenchApp
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(app.taskRepository, app.summaryRepository)
    )
    val stats by viewModel.todayStats.collectAsState()
    val summaries by viewModel.summaries.collectAsState()

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("首页总览", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // 今日任务卡片（点击进入今日计划）
        Card(
            modifier = Modifier.fillMaxWidth().clickable { onNavigateToToday() }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("今日任务", style = MaterialTheme.typography.titleMedium)
                val s = stats
                if (s == null) {
                    Text("加载中…")
                } else {
                    val pending = s.total - s.completed - s.cancelled
                    Text("完成率: ${HomeViewModel.formatRate(s.completionRate)} ｜ 待完成: $pending ｜ 已完成: ${s.completed}")
                    Text("今日任务总数: ${s.total} ｜ 已取消: ${s.cancelled}")
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text("模块摘要", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        // 五大模块摘要卡片（点击进入模块入口）
        ModuleSummaryCard(
            title = "学习",
            summaryText = summaries?.let {
                if (it.study.recordCount == 0) "暂无记录"
                else "今日 ${it.study.recordCount} 条记录，共 ${it.study.totalMinutes} 分钟"
            } ?: "加载中…",
            onClick = { onNavigateToModule("学习") }
        )
        ModuleSummaryCard(
            title = "健身",
            summaryText = summaries?.let {
                if (it.fitness.recordCount == 0) "暂无记录"
                else "今日 ${it.fitness.recordCount} 条记录，共 ${it.fitness.totalMinutes} 分钟"
            } ?: "加载中…",
            onClick = { onNavigateToModule("健身") }
        )
        ModuleSummaryCard(
            title = "饮食",
            summaryText = summaries?.let {
                if (it.diet.mealCount == 0) "暂无记录"
                else "今日 ${it.diet.mealCount} 条记录，共 ${it.diet.totalCalories.toInt()} 千卡"
            } ?: "加载中…",
            onClick = { onNavigateToModule("饮食") }
        )
        ModuleSummaryCard(
            title = "游戏",
            summaryText = summaries?.let {
                if (it.game.recordCount == 0) "暂无记录"
                else "今日 ${it.game.recordCount} 条记录，共 ${it.game.totalMinutes} 分钟"
            } ?: "加载中…",
            onClick = { onNavigateToModule("游戏") }
        )
        ModuleSummaryCard(
            title = "睡眠",
            summaryText = summaries?.let {
                if (it.sleep.recordCount == 0) "暂无记录"
                else "今日 ${it.sleep.recordCount} 条记录，共 ${it.sleep.totalMinutes} 分钟"
            } ?: "加载中…",
            onClick = { onNavigateToModule("睡眠") }
        )
    }
}

@Composable
private fun ModuleSummaryCard(title: String, summaryText: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summaryText)
        }
    }
}
