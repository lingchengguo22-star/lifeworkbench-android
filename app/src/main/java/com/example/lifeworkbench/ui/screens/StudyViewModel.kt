package com.example.lifeworkbench.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lifeworkbench.data.RepoError
import com.example.lifeworkbench.data.RepoResult
import com.example.lifeworkbench.data.StudyRepository
import com.example.lifeworkbench.data.entities.StudyRecordEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 学习页面的操作结果消息（成功或失败均展示，不假装成功）。 */
data class StudyMessage(val text: String, val isError: Boolean)

/** 学习页面的单一界面状态（日期 + 历史开关 + 该视图记录列表 + 提示消息，一次原子发射）。 */
data class StudyUiState(
    val selectedDate: LocalDate,
    val showHistory: Boolean,
    val records: List<StudyRecordEntity>,
    val message: StudyMessage?
)

/**
 * 学习 ViewModel（V1.1 阶段 3.1）。
 *
 * 只通过 [StudyRepository] 访问数据，不直接操作 DAO 与数据库；
 * 写入一律走 Repository + StudyValidator，不在 UI 层另写一套校验。
 * 记录列表为 Room Flow，数据库变化后自动刷新。
 *
 * 两种视图：按日期（当前选中日期的记录，按 id 升序）与全部历史
 * （全部记录，日期倒序、同日按 id 倒序），由 [showHistory] 切换；
 * 历史视图与首页摘要共用同一份数据，不复制保存数据。
 *
 * [todayProvider] 供测试固定“今天”；生产环境默认取设备当天日期。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StudyViewModel(
    private val repository: StudyRepository,
    private val todayProvider: () -> LocalDate = { LocalDate.now() }
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(todayProvider())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _showHistory = MutableStateFlow(false)
    val showHistory: StateFlow<Boolean> = _showHistory.asStateFlow()

    /**
     * 当前视图的学习记录。
     * 用 Eagerly 而非 WhileSubscribed：上游含 flatMapLatest 切换查询，
     * 若订阅在重组瞬间短暂断开再恢复，WhileSubscribed 的停/启可能让旧视图
     * 的发射与新视图的发射交错，导致界面上旧记录滞留；Eagerly 让上游在
     * ViewModel 存续期间持续订阅，视图切换只产生一次干净的替换。
     */
    val records: StateFlow<List<StudyRecordEntity>> = combine(_showHistory, _selectedDate) { history, date ->
        history to date
    }.flatMapLatest { (history, date) ->
        if (history) repository.getAllRecords()
        else repository.getRecordsByDate(date.toString())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _message = MutableStateFlow<StudyMessage?>(null)
    val message: StateFlow<StudyMessage?> = _message.asStateFlow()

    /**
     * 学习页面的单一界面状态：日期、历史开关、记录列表、提示消息合并为一次原子发射，
     * 避免切日期/切视图时“新日期 + 旧记录”的撕裂重组。
     */
    val uiState: StateFlow<StudyUiState> = combine(
        selectedDate, showHistory, records, message
    ) { date, history, recordList, msg -> StudyUiState(date, history, recordList, msg) }
        .stateIn(
            viewModelScope, SharingStarted.Eagerly,
            StudyUiState(todayProvider(), false, emptyList(), null)
        )

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

    fun setShowHistory(show: Boolean) {
        _showHistory.value = show
    }

    fun clearMessage() {
        _message.value = null
    }

    /** 新建学习记录：日期默认当前选中日期；knowledgePointId 本阶段不建知识点体系，始终为空。 */
    fun createRecord(content: String, durationMinutes: Int, date: String) {
        viewModelScope.launch {
            val record = StudyRecordEntity(
                date = date,
                durationMinutes = durationMinutes,
                content = content,
                knowledgePointId = null
            )
            when (val result = repository.createRecord(record)) {
                is RepoResult.Success -> _message.value = StudyMessage("学习记录已保存", false)
                is RepoResult.Failure -> _message.value = StudyMessage(errorText(result.error), true)
            }
        }
    }

    /** 更新学习记录：原样传回实体（含 id 与原 knowledgePointId），由 Repository 校验与保存。 */
    fun updateRecord(record: StudyRecordEntity) {
        viewModelScope.launch {
            when (val result = repository.updateRecord(record)) {
                is RepoResult.Success -> _message.value = StudyMessage("学习记录已更新", false)
                is RepoResult.Failure -> _message.value = StudyMessage(errorText(result.error), true)
            }
        }
    }

    /** 删除学习记录（按 ID）。 */
    fun deleteRecordById(id: Int) {
        viewModelScope.launch {
            when (val result = repository.deleteRecordById(id)) {
                is RepoResult.Success -> _message.value = StudyMessage("学习记录已删除", false)
                is RepoResult.Failure -> _message.value = StudyMessage(errorText(result.error), true)
            }
        }
    }

    private fun errorText(error: RepoError): String = when (error) {
        is RepoError.ValidationFailed -> error.messages.joinToString("；")
        is RepoError.NotFound -> "找不到该学习记录（id=${error.id}），可能已被删除"
        is RepoError.Database -> "数据库异常：${error.cause.message ?: error.cause.javaClass.simpleName}"
    }

    class Factory(private val repository: StudyRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(StudyViewModel::class.java)) {
                return StudyViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
