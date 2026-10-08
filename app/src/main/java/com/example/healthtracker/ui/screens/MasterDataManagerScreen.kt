package com.example.healthtracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthtracker.model.ActivityEntity
import com.example.healthtracker.model.HabitEntity
import com.example.healthtracker.model.InterventionEntity
import com.example.healthtracker.model.SymptomEntity
import com.example.healthtracker.ui.HealthUiState
import com.example.healthtracker.ui.HealthViewModel

@Composable
fun MasterDataManagerScreen(
    uiState: HealthUiState,
    viewModel: HealthViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Intervensi, 1: Gejala, 2: Aktivitas, 3: Habit

    // Dialog state for add / edit
    var editingIntervention by remember { mutableStateOf<InterventionEntity?>(null) }
    var showAddInterventionDialog by remember { mutableStateOf(false) }

    var editingSymptom by remember { mutableStateOf<SymptomEntity?>(null) }
    var showAddSymptomDialog by remember { mutableStateOf(false) }

    var editingActivity by remember { mutableStateOf<ActivityEntity?>(null) }
    var showAddActivityDialog by remember { mutableStateOf(false) }

    var editingHabit by remember { mutableStateOf<HabitEntity?>(null) }
    var showAddHabitDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.clip(RoundedCornerShape(10.dp))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Intervensi (${uiState.interventions.size})", fontWeight = FontWeight.Bold, fontSize = 11.5.sp) },
                modifier = Modifier.testTag("tab_master_interventions")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Gejala (${uiState.symptoms.size})", fontWeight = FontWeight.Bold, fontSize = 11.5.sp) },
                modifier = Modifier.testTag("tab_master_symptoms")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Aktivitas (${uiState.activities.size})", fontWeight = FontWeight.Bold, fontSize = 11.5.sp) },
                modifier = Modifier.testTag("tab_master_activities")
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("Habit (${uiState.habits.size})", fontWeight = FontWeight.Bold, fontSize = 11.5.sp) },
                modifier = Modifier.testTag("tab_master_habits")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Top add button
        Button(
            onClick = {
                when (selectedTab) {
                    0 -> showAddInterventionDialog = true
                    1 -> showAddSymptomDialog = true
                    2 -> showAddActivityDialog = true
                    3 -> showAddHabitDialog = true
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("btn_add_master_item"),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                when (selectedTab) {
                    0 -> "Tambah Intervensi Baru"
                    1 -> "Tambah Gejala Baru"
                    2 -> "Tambah Aktivitas Baru"
                    else -> "Tambah Habit Baru"
                },
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Content Lists
        when (selectedTab) {
            0 -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(uiState.interventions, key = { it.id }) { item ->
                        MasterItemCard(
                            title = item.name,
                            sub = if (item.defaultDose.isNotBlank()) "${item.category} • ${item.defaultDose}" else item.category,
                            description = item.description,
                            onEdit = { editingIntervention = item },
                            onDelete = { viewModel.deleteIntervention(item) }
                        )
                    }
                }
            }
            1 -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(uiState.symptoms, key = { it.id }) { item ->
                        MasterItemCard(
                            title = item.name,
                            sub = "Area: ${item.defaultBodyPart}",
                            description = item.description,
                            onEdit = { editingSymptom = item },
                            onDelete = { viewModel.deleteSymptom(item) }
                        )
                    }
                }
            }
            2 -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(uiState.activities, key = { it.id }) { item ->
                        MasterItemCard(
                            title = item.name,
                            sub = "Kategori: ${item.category}",
                            description = item.description,
                            onEdit = { editingActivity = item },
                            onDelete = { viewModel.deleteActivity(item) }
                        )
                    }
                }
            }
            3 -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(uiState.habits, key = { it.id }) { item ->
                        MasterItemCard(
                            title = "${item.iconEmoji} ${item.name}",
                            sub = "Target: ${item.targetDaysPerWeek} hari / minggu",
                            description = item.description,
                            onEdit = { editingHabit = item },
                            onDelete = { viewModel.deleteHabit(item) }
                        )
                    }
                }
            }
        }
    }

    // Intervensi Add/Edit Dialog
    if (showAddInterventionDialog || editingIntervention != null) {
        val isEdit = editingIntervention != null
        var name by remember { mutableStateOf(editingIntervention?.name ?: "") }
        var dose by remember { mutableStateOf(editingIntervention?.defaultDose ?: "") }
        var cat by remember { mutableStateOf(editingIntervention?.category ?: "Medis") }
        var desc by remember { mutableStateOf(editingIntervention?.description ?: "") }

        AlertDialog(
            onDismissRequest = {
                showAddInterventionDialog = false
                editingIntervention = null
            },
            title = { Text(if (isEdit) "Edit Intervensi" else "Tambah Intervensi") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama Intervensi") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dose,
                        onValueChange = { dose = it },
                        label = { Text("Dosis Default / Durasi") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = cat == "Medis", onClick = { cat = "Medis" }, label = { Text("Medis") })
                        FilterChip(selected = cat == "Non-medis", onClick = { cat = "Non-medis" }, label = { Text("Non-medis") })
                    }

                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Deskripsi / Catatan Poin") },
                        placeholder = { Text("Contoh:\nDeskripsi obat/intervensi\n- aturan minum\n- efek samping") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        FilledTonalButton(
                            onClick = {
                                desc = if (desc.isEmpty()) "- "
                                else if (desc.endsWith("\n")) "${desc}- "
                                else "${desc}\n- "
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.FormatListBulleted, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Tambah Poin (-)", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            if (isEdit) {
                                viewModel.updateIntervention(editingIntervention!!.copy(name = name, defaultDose = dose, category = cat, description = desc))
                            } else {
                                viewModel.quickAddIntervention(name, dose, cat, desc)
                            }
                            showAddInterventionDialog = false
                            editingIntervention = null
                        }
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddInterventionDialog = false
                    editingIntervention = null
                }) { Text("Batal") }
            }
        )
    }

    // Gejala Add/Edit Dialog
    if (showAddSymptomDialog || editingSymptom != null) {
        val isEdit = editingSymptom != null
        var name by remember { mutableStateOf(editingSymptom?.name ?: "") }
        var part by remember { mutableStateOf(editingSymptom?.defaultBodyPart ?: "Kepala") }
        var desc by remember { mutableStateOf(editingSymptom?.description ?: "") }

        AlertDialog(
            onDismissRequest = {
                showAddSymptomDialog = false
                editingSymptom = null
            },
            title = { Text(if (isEdit) "Edit Gejala" else "Tambah Gejala") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama Gejala") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = part,
                        onValueChange = { part = it },
                        label = { Text("Bagian Tubuh Terkait") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Deskripsi / Catatan Poin") },
                        placeholder = { Text("Contoh:\nGejala fisik yang dirasakan\n- rasa menusuk\n- berdenyut") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        FilledTonalButton(
                            onClick = {
                                desc = if (desc.isEmpty()) "- "
                                else if (desc.endsWith("\n")) "${desc}- "
                                else "${desc}\n- "
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.FormatListBulleted, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Tambah Poin (-)", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            if (isEdit) {
                                viewModel.updateSymptom(editingSymptom!!.copy(name = name, defaultBodyPart = part, description = desc))
                            } else {
                                viewModel.quickAddSymptom(name, part, desc)
                            }
                            showAddSymptomDialog = false
                            editingSymptom = null
                        }
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddSymptomDialog = false
                    editingSymptom = null
                }) { Text("Batal") }
            }
        )
    }

    // Aktivitas Add/Edit Dialog
    if (showAddActivityDialog || editingActivity != null) {
        val isEdit = editingActivity != null
        var name by remember { mutableStateOf(editingActivity?.name ?: "") }
        var cat by remember { mutableStateOf(editingActivity?.category ?: "Harian") }
        var desc by remember { mutableStateOf(editingActivity?.description ?: "") }

        AlertDialog(
            onDismissRequest = {
                showAddActivityDialog = false
                editingActivity = null
            },
            title = { Text(if (isEdit) "Edit Aktivitas" else "Tambah Aktivitas") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama Aktivitas") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = cat,
                        onValueChange = { cat = it },
                        label = { Text("Kategori (Kerja, Fisik, Santai, Diet)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Deskripsi / Catatan Poin") },
                        placeholder = { Text("Contoh:\nCatatan aktivitas ini\n- lari\n- jongkok\n- kentut") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        FilledTonalButton(
                            onClick = {
                                desc = if (desc.isEmpty()) "- "
                                else if (desc.endsWith("\n")) "${desc}- "
                                else "${desc}\n- "
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.FormatListBulleted, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Tambah Poin (-)", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            if (isEdit) {
                                viewModel.updateActivity(editingActivity!!.copy(name = name, category = cat, description = desc))
                            } else {
                                viewModel.quickAddActivity(name, cat, desc)
                            }
                            showAddActivityDialog = false
                            editingActivity = null
                        }
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddActivityDialog = false
                    editingActivity = null
                }) { Text("Batal") }
            }
        )
    }

    // Habit Add/Edit Dialog
    if (showAddHabitDialog || editingHabit != null) {
        val isEdit = editingHabit != null
        var name by remember { mutableStateOf(editingHabit?.name ?: "") }
        var emoji by remember { mutableStateOf(editingHabit?.iconEmoji ?: "✨") }
        var targetDays by remember { mutableStateOf(editingHabit?.targetDaysPerWeek?.toString() ?: "7") }
        var desc by remember { mutableStateOf(editingHabit?.description ?: "") }

        AlertDialog(
            onDismissRequest = {
                showAddHabitDialog = false
                editingHabit = null
            },
            title = { Text(if (isEdit) "Edit Habit" else "Tambah Habit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama Habit") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = emoji,
                            onValueChange = { emoji = it },
                            label = { Text("Emoji") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = targetDays,
                            onValueChange = { targetDays = it },
                            label = { Text("Target Hari/Mgg") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Deskripsi / Catatan Poin") },
                        placeholder = { Text("Contoh:\nTarget dan detail habit\n- pagi hari\n- 15 menit") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        FilledTonalButton(
                            onClick = {
                                desc = if (desc.isEmpty()) "- "
                                else if (desc.endsWith("\n")) "${desc}- "
                                else "${desc}\n- "
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.FormatListBulleted, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Tambah Poin (-)", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            val days = targetDays.toIntOrNull()?.coerceIn(1, 7) ?: 7
                            if (isEdit) {
                                viewModel.updateHabit(editingHabit!!.copy(name = name, iconEmoji = emoji.ifBlank { "✨" }, targetDaysPerWeek = days, description = desc))
                            } else {
                                viewModel.addHabit(name, emoji.ifBlank { "✨" }, days, desc)
                            }
                            showAddHabitDialog = false
                            editingHabit = null
                        }
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddHabitDialog = false
                    editingHabit = null
                }) { Text("Batal") }
            }
        )
    }
}

@Composable
fun MasterItemCard(
    title: String,
    sub: String,
    description: String = "",
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                    Text(text = sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Pill button ala Timeline: [Ikon Tulis] Lihat Catatan [v] / Tutup Catatan [^]
                    Surface(
                        color = Color(0xFFECFDF5),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                        modifier = Modifier
                            .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                            .padding(end = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(text = "📝 ", fontSize = 11.sp)
                            Text(
                                text = if (isDescriptionExpanded) "Tutup Catatan ˄" else "Lihat Catatan ˅",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF047857)
                            )
                        }
                    }

                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Expandable Mint Green Description Box with Bullet Points
            AnimatedVisibility(
                visible = isDescriptionExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = Color(0xFFECFDF5), // Latar belakang hijau mint muda pucat
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFA7F3D0)), // Garis tepi (border) tipis hijau muda
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        if (description.isBlank()) {
                            Text(
                                text = "Belum ada deskripsi / catatan poin untuk item ini. Tekan ikon edit (pensil) untuk menambahkan.",
                                fontSize = 11.5.sp,
                                color = Color(0xFF047857), // Teks hijau tua
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        } else {
                            description.lines().forEach { rawLine ->
                                val trimmed = rawLine.trim()
                                if (trimmed.startsWith("-") || trimmed.startsWith("•")) {
                                    val bulletSymbol = if (trimmed.startsWith("-")) "- " else "• "
                                    val itemText = trimmed.removePrefix("-").removePrefix("•").trim()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 1.5.dp, horizontal = 2.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = bulletSymbol,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF047857) // Teks hijau tua
                                        )
                                        Text(
                                            text = itemText,
                                            fontSize = 12.sp,
                                            color = Color(0xFF047857), // Teks hijau tua
                                            lineHeight = 16.sp
                                        )
                                    }
                                } else if (trimmed.isNotEmpty()) {
                                    Text(
                                        text = trimmed,
                                        fontSize = 12.sp,
                                        color = Color(0xFF047857), // Teks hijau tua
                                        lineHeight = 16.sp,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
