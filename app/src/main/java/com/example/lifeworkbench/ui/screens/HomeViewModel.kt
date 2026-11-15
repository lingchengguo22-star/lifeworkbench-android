package com.example.lifeworkbench.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lifeworkbench.data.ModuleSummaries
import com.example.lifeworkbench.data.ModuleSummaryRepository
import com.example.lifeworkbench.data.TaskCompletionStats
import com.example.lifeworkbench.data.TaskRepository
import com.example.lifeworkbench.data.completionStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * 首页 ViewModel（V1.1 阶段 2.2）。
 *
 * 今日统计：直接用 TaskRepository 的任务 Flow + 阶段 1 已验收的统一算法
 * [TaskCompletionStats]（completionStats()），不在此复制另一套完成率公式。
 * 五大模块摘要：ModuleSummaryRepository 的只读 Flow，数据变化后自动刷新。
 * 首页统计始终取设备当天日期；今日计划里切换日期不影响首页。
 *
 * [todayProvider] 供测试固定“今天”；生产环境默认取设备当天日期。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    taskRepository: TaskRepository,
    summaryRepository: ModuleSummaryRepository,
    todayProvider: () -> LocalDate = { LocalDate.now() }
) : ViewModel() {

    private val today: LocalDate = todayProvider()

    /** 今日任务统计（总数、已完成、待完成、完成率；未知状态计入分母与待完成）。 */
    val todayStats: StateFlow<TaskCompletionStats?> =
        taskRepository.getTasksByDate(today.toString())
            .map { tasks -> tasks.completionStats() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 五大模块今日摘要（只读）。 */
    val summaries: StateFlow<ModuleSummaries?> =
        summaryRepository.getTodaySummaries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    class Factory(
        private val taskRepository: TaskRepository,
        private val summaryRepository: ModuleSummaryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel(taskRepository, summaryRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    companion object {
        /** 完成率展示：整数不带小数（如 50%），否则保留一位小数（如 11.1%）。 */
        fun formatRate(rate: Double): String {
            val rounded = Math.round(rate * 10.0) / 10.0
            return if (rounded == kotlin.math.floor(rounded)) "${rounded.toInt()}%" else "$rounded%"
        }
    }
}
