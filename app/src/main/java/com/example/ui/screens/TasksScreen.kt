package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DailyTask
import com.example.data.model.TaskCategory
import com.example.data.model.TaskPriority
import com.example.ui.DataUiState
import com.example.ui.SathiViewModel
import com.example.ui.theme.CrimsonCoral
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.WarmAmber

@Composable
fun TasksScreen(
    viewModel: SathiViewModel,
    modifier: Modifier = Modifier
) {
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val smartPlan by viewModel.smartPlanText.collectAsStateWithLifecycle()
    val isGeneratingPlan by viewModel.isGeneratingPlan.collectAsStateWithLifecycle()

    var selectedCategory by remember { mutableStateOf("ALL") }
    var showTaskDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<DailyTask?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingTask = null
                    showTaskDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_task_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "নতুন কাজ যোগ করুন")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
            ) {
                when (val state = tasksState) {
                    is DataUiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is DataUiState.Error -> {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        ) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    is DataUiState.Success -> {
                        val allTasks = state.data
                        val completedCount = allTasks.count { it.completed }
                        val totalCount = allTasks.size
                        val filteredTasks = remember(allTasks, selectedCategory) {
                            when (selectedCategory) {
                                "ALL" -> allTasks
                                "PENDING" -> allTasks.filter { !it.completed }
                                "DONE" -> allTasks.filter { it.completed }
                                else -> allTasks.filter { it.category == selectedCategory }
                            }
                        }

                        // Daily Progress + Smart Day Plan Header
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 10.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "আজকের কাজের অগ্রগতি",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (totalCount == 0) "এখনো কোনো কাজ যোগ হয়নি"
                                            else "$totalCount টি কাজের মধ্যে $completedCount টি সম্পন্ন",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Button(
                                        onClick = { viewModel.generateSmartPlan() },
                                        enabled = !isGeneratingPlan && allTasks.any { !it.completed },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondary,
                                            contentColor = MaterialTheme.colorScheme.onSecondary
                                        ),
                                        modifier = Modifier.testTag("smart_plan_button")
                                    ) {
                                        if (isGeneratingPlan) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onSecondary
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.AutoAwesome,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Text("AI দিন সাজান")
                                    }
                                }

                                LinearProgressIndicator(
                                    progress = {
                                        if (totalCount == 0) 0f else completedCount.toFloat() / totalCount
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surface
                                )
                            }
                        }

                        AnimatedVisibility(visible = smartPlan != null) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.AutoAwesome,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "AI প্রস্তাবিত দৈনন্দিন রুটিন",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.clearSmartPlan() },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = "রুটিন বন্ধ করুন",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    Text(
                                        text = smartPlan.orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        // Filter Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val filters = listOf(
                                "ALL" to "সব (${allTasks.size})",
                                "PENDING" to "বাকি (${allTasks.count { !it.completed }})",
                                "DONE" to "সম্পন্ন ($completedCount)"
                            ) + TaskCategory.entries.map { it.key to it.bnLabel }

                            filters.forEach { (key, label) ->
                                FilterChip(
                                    selected = selectedCategory == key,
                                    onClick = { selectedCategory = key },
                                    label = { Text(label) }
                                )
                            }
                        }

                        if (filteredTasks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = 72.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.TaskAlt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                        modifier = Modifier.size(52.dp)
                                    )
                                    Text(
                                        text = "কোনো কাজ নেই",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "নিচের + বাটনে ট্যাপ করুন অথবা সাথী চ্যাটে বলুন\n“সকাল ৮টায় মিটিং যোগ করো”",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(bottom = 88.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filteredTasks, key = { it.id }) { task ->
                                    TaskItemCard(
                                        task = task,
                                        onToggle = { viewModel.toggleTask(task) },
                                        onEdit = {
                                            editingTask = task
                                            showTaskDialog = true
                                        },
                                        onDelete = { viewModel.deleteTask(task.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTaskDialog) {
        TaskEditorDialog(
            existing = editingTask,
            onDismiss = { showTaskDialog = false },
            onSave = { title, note, category, priority, time ->
                viewModel.addOrUpdateTask(editingTask, title, note, category, priority, time)
                showTaskDialog = false
            }
        )
    }
}

@Composable
private fun TaskItemCard(
    task: DailyTask,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val priorityEnum = TaskPriority.fromKey(task.priority)
    val categoryEnum = TaskCategory.fromKey(task.category)
    val priorityColor = when (priorityEnum) {
        TaskPriority.HIGH -> CrimsonCoral
        TaskPriority.MEDIUM -> WarmAmber
        TaskPriority.LOW -> EmeraldSuccess
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (task.completed) 0.dp else 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = if (task.completed) 0.18f else 0.35f),
                RoundedCornerShape(18.dp)
            )
            .clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(if (task.completed) priorityColor.copy(alpha = 0.3f) else priorityColor)
            )
            Spacer(Modifier.width(10.dp))

            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (task.completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (task.completed) "অসম্পূর্ণ করুন" else "সম্পন্ন করুন",
                    tint = if (task.completed) EmeraldSuccess else MaterialTheme.colorScheme.primary
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
                if (task.note.isNotBlank()) {
                    Text(
                        text = task.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = categoryEnum.bnLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = priorityColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = priorityEnum.bnLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = priorityColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (task.scheduledTime.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = task.scheduledTime,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "সম্পাদনা করুন",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "মুছে ফেলুন",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun TaskEditorDialog(
    existing: DailyTask?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var timeLabel by remember { mutableStateOf(existing?.scheduledTime.orEmpty()) }
    var category by remember { mutableStateOf(existing?.category ?: TaskCategory.PERSONAL.key) }
    var priority by remember { mutableStateOf(existing?.priority ?: TaskPriority.MEDIUM.key) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existing == null) "নতুন কাজ যোগ করুন" else "কাজ সম্পাদনা করুন",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("কাজের নাম *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_title_input")
                )
                OutlinedTextField(
                    value = timeLabel,
                    onValueChange = { timeLabel = it },
                    label = { Text("সময় (যেমন: 08:30 AM)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("বিস্তারিত নোট") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("ক্যাটাগরি", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TaskCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat.key,
                            onClick = { category = cat.key },
                            label = { Text(cat.bnLabel) }
                        )
                    }
                }

                Text("গুরুত্ব", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TaskPriority.entries.forEach { prio ->
                        FilterChip(
                            selected = priority == prio.key,
                            onClick = { priority = prio.key },
                            label = { Text(prio.bnLabel) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, note, category, priority, timeLabel) },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("save_task_button")
            ) {
                Text("সংরক্ষণ")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}
