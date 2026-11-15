package com.example.lifeworkbench.ui.screens

import androidx.compose.foundation.clickable
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
import com.example.lifeworkbench.data.TaskStatus
import com.example.lifeworkbench.data.entities.TaskEntity
import com.example.lifeworkbench.data.taskStatus
import java.time.LocalDate

/**
 * 今日计划页面（V1.1 阶段 2.1）。
 *
 * 数据全部来自 TaskRepository（真实 Room 数据库），不使用硬编码数字与假数据。
 * 写入一律走 TodayPlanViewModel → TaskRepository → TaskValidator；
 * UI 层不另写业务校验，只做输入格式解析（时间 HH:MM）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayPlanScreen() {
    val app = LocalContext.current.applicationContext as LifeWorkbenchApp
    val viewModel: TodayPlanViewModel = viewModel(factory = TodayPlanViewModel.Factory(app.taskRepository))
    // 单一界面状态：日期、任务、提示一次原子发射，避免切日期时“新日期 + 旧任务”的撕裂重组
    val uiState by viewModel.uiState.collectAsState()
    val selectedDate = uiState.selectedDate
    val tasks = uiState.tasks
    val message = uiState.message

    var showForm by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var detailTask by remember { mutableStateOf<TaskEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<TaskEntity?>(null) }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("今日计划", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(12.dp))

        // 日期切换栏
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { viewModel.previousDay() },
                modifier = Modifier.testTag("prevDay")
            ) { Text("‹ 前一天") }
            Text(
                text = selectedDate.format(TodayPlanViewModel.DATE_DISPLAY_FORMAT),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { viewModel.nextDay() },
                modifier = Modifier.testTag("nextDay")
            ) { Text("后一天 ›") }
            TextButton(
                onClick = { viewModel.goToToday() },
                modifier = Modifier.testTag("goToday")
            ) { Text("今天") }
        }
        Spacer(modifier = Modifier.height(8.dp))

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

        // 任务列表：用 key(selectedDate) 包裹，日期切换时整列重建，
        // 保证旧日期的任务节点（含语义节点）被彻底销毁，而非在 LazyColumn 内复用/滞留。
        // 真机上行为不变（新日期本就应显示全新列表）；Robolectric 下旧节点不再滞留。
        key(selectedDate) {
            val timedTasks = tasks.filter { it.startMinute != null }
            val untimedTasks = tasks.filter { it.startMinute == null }
            if (tasks.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("暂无任务", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (timedTasks.isNotEmpty()) {
                        item { Text("有时间任务", style = MaterialTheme.typography.titleMedium) }
                        items(timedTasks, key = { it.id }) { task ->
                            TaskItem(
                                task = task,
                                onDetail = { detailTask = task },
                                onEdit = { editingTask = task; showForm = true },
                                onDelete = { deleteTarget = task },
                                onSetStatus = { status -> viewModel.setStatus(task, status) }
                            )
                        }
                    }
                    if (untimedTasks.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("无时间任务", style = MaterialTheme.typography.titleMedium)
                        }
                        items(untimedTasks, key = { it.id }) { task ->
                            TaskItem(
                                task = task,
                                onDetail = { detailTask = task },
                                onEdit = { editingTask = task; showForm = true },
                                onDelete = { deleteTarget = task },
                                onSetStatus = { status -> viewModel.setStatus(task, status) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { editingTask = null; showForm = true },
            modifier = Modifier.fillMaxWidth().testTag("newTaskButton")
        ) { Text("新建任务") }
    }

    // 新建 / 编辑表单
    if (showForm) {
        TaskFormDialog(
            initialTask = editingTask,
            defaultDate = selectedDate,
            onDismiss = { showForm = false },
            onSave = { task, isNew ->
                if (isNew) {
                    viewModel.createTask(
                        title = task.title,
                        description = task.description,
                        planDate = task.planDate,
                        category = task.category,
                        priority = task.priority,
                        startMinute = task.startMinute,
                        endMinute = task.endMinute
                    )
                } else {
                    viewModel.updateTask(task)
                }
                showForm = false
            }
        )
    }

    // 详情
    detailTask?.let { task ->
        TaskDetailDialog(
            task = task,
            onDismiss = { detailTask = null },
            onEdit = { detailTask = null; editingTask = task; showForm = true },
            onDelete = { detailTask = null; deleteTarget = task }
        )
    }

    // 删除确认
    deleteTarget?.let { task ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除任务") },
            text = { Text("确定删除「${task.title}」吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteTaskById(task.id); deleteTarget = null },
                    modifier = Modifier.testTag("deleteConfirm")
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteTarget = null },
                    modifier = Modifier.testTag("deleteCancel")
                ) { Text("取消") }
            }
        )
    }
}

@Composable
private fun TaskItem(
    task: TaskEntity,
    onDetail: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetStatus: (TaskStatus) -> Unit
) {
    val status = task.taskStatus()
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onDetail() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (task.startMinute != null && task.endMinute != null) {
                    Text(
                        text = "${TodayPlanViewModel.formatMinute(task.startMinute)}-${TodayPlanViewModel.formatMinute(task.endMinute)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(text = status.displayName, style = MaterialTheme.typography.bodyMedium)
            }
            if (!task.description.isNullOrBlank()) {
                Text(text = task.description, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = "分类：${task.category} ｜ 优先级：${task.priority}",
                style = MaterialTheme.typography.bodySmall
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("taskEdit_${task.id}")
                ) { Text("编辑") }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("taskDelete_${task.id}")
                ) { Text("删除") }
                // 状态操作：仅对未完成/未取消的任务展示（未知状态也可改成合法状态）
                val showActions = status is TaskStatus.Pending ||
                    status is TaskStatus.InProgress ||
                    status is TaskStatus.Unknown
                if (showActions) {
                    if (status is TaskStatus.Pending || status is TaskStatus.Unknown) {
                        TextButton(
                            onClick = { onSetStatus(TaskStatus.InProgress) },
                            modifier = Modifier.testTag("taskStart_${task.id}")
                        ) { Text("进行中") }
                    }
                    TextButton(
                        onClick = { onSetStatus(TaskStatus.Completed) },
                        modifier = Modifier.testTag("taskComplete_${task.id}")
                    ) { Text("完成") }
                    TextButton(
                        onClick = { onSetStatus(TaskStatus.Cancelled) },
                        modifier = Modifier.testTag("taskCancel_${task.id}")
                    ) { Text("取消") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskFormDialog(
    initialTask: TaskEntity?,
    defaultDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (TaskEntity, Boolean) -> Unit
) {
    val isNew = initialTask == null
    var title by remember { mutableStateOf(initialTask?.title ?: "") }
    var description by remember { mutableStateOf(initialTask?.description ?: "") }
    var planDate by remember { mutableStateOf(initialTask?.planDate ?: defaultDate.toString()) }
    var category by remember { mutableStateOf(initialTask?.category ?: "学习") }
    var priority by remember { mutableStateOf(initialTask?.priority ?: 2) }
    var hasTime by remember { mutableStateOf(initialTask?.startMinute != null) }
    var startText by remember {
        mutableStateOf(initialTask?.startMinute?.let { TodayPlanViewModel.formatMinute(it) } ?: "")
    }
    var endText by remember {
        mutableStateOf(initialTask?.endMinute?.let { TodayPlanViewModel.formatMinute(it) } ?: "")
    }
    // 编辑时状态：null 表示保持原值（未知状态原样保留）；选了合法值则改为该值
    var statusOverride by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }

    val initialStatus = initialTask?.taskStatus()
    val statusIsUnknown = initialStatus is TaskStatus.Unknown

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "新建任务" else "编辑任务") },
        text = {
            Column {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("标题") }, modifier = Modifier.fillMaxWidth().testTag("formTitle")
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("描述（可选）") }, modifier = Modifier.fillMaxWidth().testTag("formDescription")
                )
                OutlinedTextField(
                    value = planDate, onValueChange = { planDate = it },
                    label = { Text("日期（YYYY-MM-DD）") }, modifier = Modifier.fillMaxWidth().testTag("formDate")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("分类", style = MaterialTheme.typography.bodyMedium)
                CategoryDropdown(
                    current = category,
                    onSelect = { category = it }
                )
                if (!isNew && initialTask != null && initialTask.category !in TodayPlanViewModel.LEGAL_CATEGORIES) {
                    Text(
                        text = "历史分类「${initialTask.category}」将保留；如需修改请选择上方合法分类。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("优先级", style = MaterialTheme.typography.bodyMedium)
                Row {
                    listOf(1, 2, 3).forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text("$p") },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hasTime, onCheckedChange = { hasTime = it })
                    Text("设置时间")
                }
                if (hasTime) {
                    OutlinedTextField(
                        value = startText, onValueChange = { startText = it },
                        label = { Text("开始时间（HH:MM）") }, modifier = Modifier.fillMaxWidth().testTag("formStartTime")
                    )
                    OutlinedTextField(
                        value = endText, onValueChange = { endText = it },
                        label = { Text("结束时间（HH:MM）") }, modifier = Modifier.fillMaxWidth().testTag("formEndTime")
                    )
                }
                if (!isNew && initialTask != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (statusIsUnknown) {
                        Text(
                            text = "当前状态：${initialTask.status}（未知状态，将保留）",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text("如需修改状态，请选择：", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("状态", style = MaterialTheme.typography.bodyMedium)
                    }
                    StatusDropdown(
                        current = statusOverride ?: initialTask.status,
                        showKeepOriginal = statusIsUnknown,
                        onSelect = { statusOverride = it }
                    )
                }
                formError?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                // 仅做输入格式解析；业务校验（成对、范围、先后、分类、标题等）由 Repository/Validator 执行
                val startMinute: Int?
                val endMinute: Int?
                if (!hasTime) {
                    startMinute = null
                    endMinute = null
                } else {
                    startMinute = parseMinute(startText)
                    endMinute = parseMinute(endText)
                    if (startMinute == null || endMinute == null) {
                        formError = "时间格式应为 HH:MM（例如 09:30）"
                        return@TextButton
                    }
                }
                val newStatus = if (isNew) {
                    TaskStatus.Pending.dbValue
                } else {
                    statusOverride ?: initialTask!!.status
                }
                val task = (initialTask ?: TaskEntity(
                    title = "", description = null, planDate = planDate,
                    category = category, priority = priority, status = newStatus, sourceId = null
                )).copy(
                    title = title,
                    description = description.ifBlank { null },
                    planDate = planDate,
                    category = category,
                    priority = priority,
                    status = newStatus,
                    startMinute = startMinute,
                    endMinute = endMinute
                )
                onSave(task, isNew)
            },
                modifier = Modifier.testTag("formSave")
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("formCancel")
            ) { Text("取消") }
        }
    )
}

/** 解析 HH:MM 为分钟数；格式不对返回 null（仅输入格式解析，非业务校验）。 */
private fun parseMinute(text: String): Int? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(current: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TodayPlanViewModel.LEGAL_CATEGORIES.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c) },
                    onClick = { onSelect(c); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusDropdown(
    current: String,
    showKeepOriginal: Boolean,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    // 四种合法状态的中文数据库值（与 TaskStatus 的 dbValue 一致）
    val legal = listOf("待开始", "进行中", "已完成", "已取消")
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (showKeepOriginal) {
                DropdownMenuItem(
                    text = { Text("保持原值（$current）") },
                    onClick = { onSelect(current); expanded = false }
                )
            }
            legal.forEach { value ->
                DropdownMenuItem(
                    text = { Text(value) },
                    onClick = { onSelect(value); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun TaskDetailDialog(
    task: TaskEntity,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val status = task.taskStatus()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(task.title) },
        text = {
            Column {
                Text("状态：${status.displayName}")
                Text("日期：${task.planDate}")
                if (task.startMinute != null && task.endMinute != null) {
                    Text(
                        "时间：${TodayPlanViewModel.formatMinute(task.startMinute)}-" +
                            "${TodayPlanViewModel.formatMinute(task.endMinute)}"
                    )
                }
                Text("分类：${task.category}")
                Text("优先级：${task.priority}")
                if (!task.description.isNullOrBlank()) {
                    Text("描述：${task.description}")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onEdit) { Text("编辑") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("删除") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}
