package com.example.lifeworkbench.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lifeworkbench.LifeWorkbenchApp
import com.example.lifeworkbench.data.entities.StudyRecordEntity
import java.time.LocalDate

/**
 * 学习页面（V1.1 阶段 3.1）。
 *
 * 数据全部来自 StudyRepository（真实 Room 数据库），不使用硬编码数字与假数据。
 * 写入一律走 StudyViewModel → StudyRepository → StudyValidator；
 * UI 层不另写业务校验，只做输入格式解析（时长是否为整数）。
 *
 * 两种视图：按日期（当前选中日期的记录）与全部历史（全部记录，日期倒序）。
 * 首页摘要仍由 ModuleSummaryRepository 提供，本页不复制保存数据。
 * knowledgePointId 本阶段不建知识点体系：界面不展示、不编辑，原值原样保留。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen() {
    val app = LocalContext.current.applicationContext as LifeWorkbenchApp
    val viewModel: StudyViewModel = viewModel(factory = StudyViewModel.Factory(app.studyRepository))
    // 单一界面状态：日期、历史开关、记录、提示一次原子发射，避免切日期/切视图时撕裂重组
    val uiState by viewModel.uiState.collectAsState()
    val selectedDate = uiState.selectedDate
    val showHistory = uiState.showHistory
    val records = uiState.records
    val message = uiState.message

    var showForm by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<StudyRecordEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<StudyRecordEntity?>(null) }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("学习", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(12.dp))

        // 视图切换：按日期 / 全部历史
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = !showHistory,
                onClick = { viewModel.setShowHistory(false) },
                label = { Text("按日期") },
                modifier = Modifier.padding(end = 8.dp).testTag("studyModeDate")
            )
            FilterChip(
                selected = showHistory,
                onClick = { viewModel.setShowHistory(true) },
                label = { Text("全部历史") },
                modifier = Modifier.testTag("studyModeHistory")
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // 日期切换栏（仅按日期视图）
        if (!showHistory) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { viewModel.previousDay() },
                    modifier = Modifier.testTag("studyPrevDay")
                ) { Text("‹ 前一天") }
                Text(
                    text = selectedDate.format(TodayPlanViewModel.DATE_DISPLAY_FORMAT),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { viewModel.nextDay() },
                    modifier = Modifier.testTag("studyNextDay")
                ) { Text("后一天 ›") }
                TextButton(
                    onClick = { viewModel.goToToday() },
                    modifier = Modifier.testTag("studyGoToday")
                ) { Text("今天") }
            }
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            Text("全部历史（按日期倒序）", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 操作结果提示（成功或失败均展示，不假装成功）
        message?.let { msg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (msg.isError) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = msg.text, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.clearMessage() }) { Text("关闭") }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 记录列表：与今日计划页同一做法——按日期视图用 key(selectedDate) 包裹，
        // 日期切换时整列重建，旧记录语义节点彻底销毁；历史视图用固定 key("history")。
        // （之前 key(selectedDate, showHistory) 双键在 CI 第 140 次仍让旧节点滞留，
        // 改回与已验证的今日计划页完全一致的单键结构。）
        if (showHistory) {
            key("history") {
                StudyRecordList(
                    records = records,
                    showDate = true,
                    onEdit = { editingRecord = it; showForm = true },
                    onDelete = { deleteTarget = it }
                )
            }
        } else {
            key(selectedDate) {
                StudyRecordList(
                    records = records,
                    showDate = false,
                    onEdit = { editingRecord = it; showForm = true },
                    onDelete = { deleteTarget = it }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { editingRecord = null; showForm = true },
            modifier = Modifier.fillMaxWidth().testTag("newStudyButton")
        ) { Text("新建学习记录") }
    }

    // 新建 / 编辑表单
    if (showForm) {
        StudyFormDialog(
            initialRecord = editingRecord,
            defaultDate = selectedDate,
            onDismiss = { showForm = false },
            onSave = { content, durationMinutes, date ->
                if (editingRecord == null) {
                    viewModel.createRecord(content, durationMinutes, date)
                } else {
                    viewModel.updateRecord(
                        editingRecord!!.copy(
                            content = content,
                            durationMinutes = durationMinutes,
                            date = date
                        )
                    )
                }
                showForm = false
            }
        )
    }

    // 删除确认
    deleteTarget?.let { record ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除学习记录") },
            text = { Text("确定删除「${record.content}」吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteRecordById(record.id); deleteTarget = null },
                    modifier = Modifier.testTag("studyDeleteConfirm")
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteTarget = null },
                    modifier = Modifier.testTag("studyDeleteCancel")
                ) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ColumnScope.StudyRecordList(
    records: List<StudyRecordEntity>,
    showDate: Boolean,
    onEdit: (StudyRecordEntity) -> Unit,
    onDelete: (StudyRecordEntity) -> Unit
) {
    if (records.isEmpty()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("暂无学习记录", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(records, key = { it.id }) { record ->
                StudyRecordItem(
                    record = record,
                    showDate = showDate,
                    onEdit = { onEdit(record) },
                    onDelete = { onDelete(record) }
                )
            }
        }
    }
}

@Composable
private fun StudyRecordItem(
    record: StudyRecordEntity,
    showDate: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = record.content,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "学习时长：${record.durationMinutes} 分钟",
                style = MaterialTheme.typography.bodyMedium
            )
            if (showDate) {
                Text(
                    text = "日期：${record.date}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(modifier = Modifier.padding(top = 8.dp)) {
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("studyEdit_${record.id}")
                ) { Text("编辑") }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("studyDelete_${record.id}")
                ) { Text("删除") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyFormDialog(
    initialRecord: StudyRecordEntity?,
    defaultDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (String, Int, String) -> Unit
) {
    val isNew = initialRecord == null
    var content by remember { mutableStateOf(initialRecord?.content ?: "") }
    var durationText by remember {
        mutableStateOf(initialRecord?.durationMinutes?.toString() ?: "")
    }
    var date by remember { mutableStateOf(initialRecord?.date ?: defaultDate.toString()) }
    var formError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "新建学习记录" else "编辑学习记录") },
        text = {
            Column {
                OutlinedTextField(
                    value = content, onValueChange = { content = it },
                    label = { Text("学习内容") }, modifier = Modifier.fillMaxWidth().testTag("studyFormContent")
                )
                OutlinedTextField(
                    value = durationText, onValueChange = { durationText = it },
                    label = { Text("学习时长（分钟）") },
                    modifier = Modifier.fillMaxWidth().testTag("studyFormDuration")
                )
                OutlinedTextField(
                    value = date, onValueChange = { date = it },
                    label = { Text("日期（YYYY-MM-DD）") },
                    modifier = Modifier.fillMaxWidth().testTag("studyFormDate")
                )
                formError?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // 仅做输入格式解析；业务校验（内容非空/长度、时长正数/上限、日期合法）
                    // 由 Repository/Validator 执行
                    val durationMinutes = durationText.trim().toIntOrNull()
                    if (durationMinutes == null) {
                        formError = "学习时长应为正整数（分钟）"
                        return@TextButton
                    }
                    onSave(content, durationMinutes, date)
                },
                modifier = Modifier.testTag("studyFormSave")
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("studyFormCancel")
            ) { Text("取消") }
        }
    )
}
