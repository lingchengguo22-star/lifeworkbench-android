package com.example.lifeworkbench.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lifeworkbench.data.TaskError
import com.example.lifeworkbench.data.TaskRepository
import com.example.lifeworkbench.data.TaskResult
import com.example.lifeworkbench.data.TaskStatus
import com.example.lifeworkbench.data.TaskValidator
import com.example.lifeworkbench.data.entities.TaskEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 今日计划页面的操作结果消息（成功或失败均展示，不假装成功）。 */
data class TodayPlanMessage(val text: String, val isError: Boolean)

/**
 * 今日计划 ViewModel（V1.1 阶段 2.1）。
 *
 * 只通过 [TaskRepository] 访问数据，不直接操作 DAO 与数据库；
 * 写入一律走 Repository + TaskValidator，不在 UI 层另写一套校验。
 * 任务列表为 Room Flow，数据库变化后自动刷新。
 *
 * [todayProvider] 供测试固定“今天”；生产环境默认取设备当天日期。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayPlanViewModel(
    private val repository: TaskRepository,
    private val todayProvider: () -> LocalDate = { LocalDate.now() }
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(todayProvider())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    /** 当前选中日期的任务（DAO 已按“有时间在前、开始时间升序、无时间在后”排好序）。 */
    val tasks: StateFlow<List<TaskEntity>> = _selectedDate
        .flatMapLatest { date -> repository.getTasksByDate(date.toIsoDate()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<TodayPlanMessage?>(null)
    val message: StateFlow<TodayPlanMessage?> = _message.asStateFlow()

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun previousDay() {
        _selectedDate.value = _selectedDate.value.minusDays(1)
    }

    fun nextDay() {
        _selectedDate.value = _selectedDate.value.plusDays(1)
    }

    fun goToToday() {
        _selectedDate.value = todayProvider()
    }

    fun clearMessage() {
        _message.value = null
    }

    /** 新建任务：状态固定为待开始，日期默认当前选中日期。 */
    fun createTask(
        title: String,
        description: String?,
        planDate: String,
        category: String,
        priority: Int,
        startMinute: Int?,
        endMinute: Int?
    ) {
        viewModelScope.launch {
            val task = TaskEntity(
                title = title,
                description = description?.ifBlank { null },
                planDate = planDate,
                category = category,
                priority = priority,
                status = TaskStatus.Pending.dbValue,
                sourceId = null,
                startMinute = startMinute,
                endMinute = endMinute
            )
            when (val result = repository.createTask(task)) {
                is TaskResult.Success -> _message.value = TodayPlanMessage("任务已保存", false)
                is TaskResult.Failure -> _message.value = TodayPlanMessage(errorText(result.error), true)
            }
        }
    }

    /** 更新任务：原样传回实体（含 id 与原状态），由 Repository 按编辑规则校验与保存。 */
    fun updateTask(task: TaskEntity) {
        viewModelScope.launch {
            when (val result = repository.updateTask(task)) {
                is TaskResult.Success -> _message.value = TodayPlanMessage("任务已更新", false)
                is TaskResult.Failure -> _message.value = TodayPlanMessage(errorText(result.error), true)
            }
        }
    }

    /** 删除任务（按 ID）。 */
    fun deleteTaskById(id: Int) {
        viewModelScope.launch {
            when (val result = repository.deleteTaskById(id)) {
                is TaskResult.Success -> _message.value = TodayPlanMessage("任务已删除", false)
                is TaskResult.Failure -> _message.value = TodayPlanMessage(errorText(result.error), true)
            }
        }
    }

    /** 修改任务状态（进行中 / 已完成 / 已取消等）。未知状态任务改成合法状态时允许；改成非法状态时 Repository 拒绝并提示。 */
    fun setStatus(task: TaskEntity, status: TaskStatus) {
        val dbValue = status.toDbValue() ?: return
        updateTask(task.copy(status = dbValue))
    }

    private fun errorText(error: TaskError): String = when (error) {
        is TaskError.ValidationFailed -> error.errors.joinToString("；") { it.message }
        is TaskError.NotFound -> "找不到该任务（id=${error.id}），可能已被删除"
        is TaskError.Database -> "数据库异常：${error.cause.message ?: error.cause.javaClass.simpleName}"
    }

    class Factory(private val repository: TaskRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TodayPlanViewModel::class.java)) {
                return TodayPlanViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    companion object {
        /** 选中日期的中文展示：2026年11月15日 星期日。 */
        val DATE_DISPLAY_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy年M月d日 E", Locale.CHINA)

        fun LocalDate.toIsoDate(): String = this.toString()

        /** 分钟数转 HH:MM（如 570 → 09:30）。 */
        fun formatMinute(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

        /** 合法分类（与 TaskValidator.LEGAL_CATEGORIES 一致，供下拉框使用，不另立规则）。 */
        val LEGAL_CATEGORIES: List<String> = TaskValidator.LEGAL_CATEGORIES.sortedBy {
            listOf("学习", "健身", "饮食", "游戏", "睡眠", "其他").indexOf(it)
        }
    }
}
