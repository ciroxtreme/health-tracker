package com.example.healthtracker.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthtracker.analytics.AnalyticsEngine
import com.example.healthtracker.model.ActivityEntity
import com.example.healthtracker.model.HealthEntryEntity
import com.example.healthtracker.model.InterventionEntity
import com.example.healthtracker.model.SymptomEntity
import com.example.healthtracker.ui.HealthUiState
import com.example.healthtracker.ui.HealthViewModel
import com.example.ui.theme.LocalHarmonizedColors
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEntryScreen(
    uiState: HealthUiState,
    viewModel: HealthViewModel,
    onNavigateBack: () -> Unit,
    entryToEdit: HealthEntryEntity? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalHarmonizedColors.current
    val context = LocalContext.current
    val calendar = remember {
        Calendar.getInstance().apply {
            if (entryToEdit != null) timeInMillis = entryToEdit.occurrenceTime
        }
    }

    // Determine initial mode: 0 for Rutinitas, 1 for Pemicu
    val isEditMode = entryToEdit != null
    val isRoutineEdit = isEditMode && entryToEdit!!.symptomId == null && (entryToEdit.symptomSeverity == null || entryToEdit.symptomSeverity == 0) && entryToEdit.interventionIdsJson == "[]"

    var selectedTabMode by remember { mutableStateOf(if (isRoutineEdit) 0 else 1) } // 0: Rutinitas, 1: Pemicu

    // State for backdating
    var occurrenceTimestamp by remember { mutableStateOf(entryToEdit?.occurrenceTime ?: System.currentTimeMillis()) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")) }

    // Component states: Multiple symptoms supported
    val initialSymptomIds = remember(entryToEdit) {
        val fromJson = AnalyticsEngine.parseIdList(entryToEdit?.symptomIdsJson ?: "")
        if (fromJson.isNotEmpty()) fromJson
        else entryToEdit?.symptomId?.let { listOf(it) } ?: emptyList()
    }
    val selectedSymptomIds = remember { mutableStateListOf<Long>().apply { addAll(initialSymptomIds) } }
    val symptomSeverities = remember {
        mutableStateMapOf<Long, Int>().apply {
            val parsed = AnalyticsEngine.parseSeverityMap(entryToEdit?.symptomSeveritiesJson)
            if (parsed.isNotEmpty()) {
                putAll(parsed)
            } else if (entryToEdit?.symptomSeverity != null && initialSymptomIds.isNotEmpty()) {
                initialSymptomIds.forEach { put(it, entryToEdit.symptomSeverity) }
            }
        }
    }
    var symptomSeverity by remember { mutableStateOf(entryToEdit?.symptomSeverity ?: 5) }
    var symptomNotes by remember { mutableStateOf(entryToEdit?.symptomNotes ?: "") }

    val initialInterventionIds = remember(entryToEdit) {
        AnalyticsEngine.parseIdList(entryToEdit?.interventionIdsJson ?: "")
    }
    val selectedInterventionIds = remember { mutableStateListOf<Long>().apply { addAll(initialInterventionIds) } }
    var interventionNotes by remember { mutableStateOf(entryToEdit?.interventionNotes ?: "") }

    val initialActivityIds = remember(entryToEdit) {
        AnalyticsEngine.parseIdList(entryToEdit?.activityIdsJson ?: "")
    }
    val selectedActivityIds = remember { mutableStateListOf<Long>().apply { addAll(initialActivityIds) } }
    var activityNotes by remember { mutableStateOf(entryToEdit?.activityNotes ?: "") }

    val initialHabitIds = remember(entryToEdit) {
        AnalyticsEngine.parseIdList(entryToEdit?.habitIdsJson ?: "")
    }
    val selectedHabitIds = remember { mutableStateListOf<Long>().apply { addAll(initialHabitIds) } }

    // Habits completed earlier today on this date
    val habitsCompletedTodayElsewhere = remember(uiState.entries, occurrenceTimestamp, entryToEdit) {
        val entryCal = Calendar.getInstance().apply { timeInMillis = occurrenceTimestamp }
        val y = entryCal.get(Calendar.YEAR)
        val d = entryCal.get(Calendar.DAY_OF_YEAR)

        val completed = mutableSetOf<Long>()
        uiState.entries.filter { entry ->
            if (entryToEdit != null && entry.id == entryToEdit.id) return@filter false
            val c = Calendar.getInstance().apply { timeInMillis = entry.occurrenceTime }
            c.get(Calendar.YEAR) == y && c.get(Calendar.DAY_OF_YEAR) == d
        }.forEach { entry ->
            completed.addAll(AnalyticsEngine.parseIdList(entry.habitIdsJson))
        }
        completed
    }

    var selectedMoodScore by remember { mutableStateOf<Int?>(entryToEdit?.moodScore ?: 4) }
    var moodNotes by remember { mutableStateOf(entryToEdit?.moodNotes ?: "") }

    // Quick-Add dialog states
    var showQuickAddIntervention by remember { mutableStateOf(false) }
    var showQuickAddSymptom by remember { mutableStateOf(false) }
    var showQuickAddActivity by remember { mutableStateOf(false) }
    var showQuickAddHabit by remember { mutableStateOf(false) }

    // Time Slot Shortcut Helper for Rutinitas
    fun applyTimeSlotShortcut(startHour: Int, endHour: Int, label: String, headerText: String) {
        val cal = Calendar.getInstance().apply { timeInMillis = occurrenceTimestamp }
        val targetHour = (startHour + endHour) / 2
        cal.set(Calendar.HOUR_OF_DAY, targetHour)
        cal.set(Calendar.MINUTE, 0)
        occurrenceTimestamp = cal.timeInMillis

        // Prepend header to notes if not already present
        if (!activityNotes.contains(headerText)) {
            val existing = activityNotes.trim()
            activityNotes = if (existing.isEmpty()) {
                "$headerText\n- "
            } else {
                "$headerText\n- \n\n$existing"
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) {
                            if (selectedTabMode == 0) "Edit Rutinitas Harian" else "Edit Entri Pemicu"
                        } else {
                            if (selectedTabMode == 0) "Catat Rutinitas Harian" else "Catat Entri Pemicu"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("nav_back_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = colors.textPrimary)
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (selectedTabMode == 0) {
                                // Save as Rutinitas Harian (no symptom, no medication)
                                if (isEditMode && entryToEdit != null) {
                                    val updated = entryToEdit.copy(
                                        occurrenceTime = occurrenceTimestamp,
                                        symptomId = null,
                                        symptomSeverity = null,
                                        symptomNotes = null,
                                        interventionIdsJson = "[]",
                                        interventionNotes = null,
                                        activityIdsJson = selectedActivityIds.toList().toString(),
                                        activityNotes = activityNotes.ifBlank { null },
                                        habitIdsJson = selectedHabitIds.toList().toString(),
                                        moodScore = selectedMoodScore,
                                        moodNotes = moodNotes.ifBlank { null }
                                    )
                                    viewModel.updateEntry(updated)
                                } else {
                                    viewModel.saveAcuteEntry(
                                        occurrenceTime = occurrenceTimestamp,
                                        symptomId = null,
                                        symptomSeverity = null,
                                        symptomNotes = null,
                                        interventionIds = emptyList(),
                                        interventionNotes = null,
                                        activityIds = selectedActivityIds.toList(),
                                        activityNotes = activityNotes.ifBlank { null },
                                        habitIds = selectedHabitIds.toList(),
                                        moodScore = selectedMoodScore,
                                        moodNotes = moodNotes.ifBlank { null }
                                    )
                                }
                                onNavigateBack()
                            } else {
                                // Save as Pemicu Gejala (Multiple Symptoms Supported)
                                val hasData = selectedSymptomIds.isNotEmpty() ||
                                        selectedInterventionIds.isNotEmpty() ||
                                        selectedActivityIds.isNotEmpty() ||
                                        selectedMoodScore != null

                                if (hasData) {
                                    val maxSev = if (selectedSymptomIds.isNotEmpty()) {
                                        symptomSeverities.values.maxOrNull() ?: symptomSeverity
                                    } else null
                                    val severitiesJson = AnalyticsEngine.formatSeverityMap(symptomSeverities.toMap())

                                    if (isEditMode && entryToEdit != null) {
                                        val updated = entryToEdit.copy(
                                            occurrenceTime = occurrenceTimestamp,
                                            symptomId = selectedSymptomIds.firstOrNull(),
                                            symptomIdsJson = selectedSymptomIds.toList().toString(),
                                            symptomSeveritiesJson = severitiesJson,
                                            symptomSeverity = maxSev,
                                            symptomNotes = symptomNotes.ifBlank { null },
                                            interventionIdsJson = selectedInterventionIds.toList().toString(),
                                            interventionNotes = interventionNotes.ifBlank { null },
                                            activityIdsJson = selectedActivityIds.toList().toString(),
                                            activityNotes = activityNotes.ifBlank { null },
                                            habitIdsJson = selectedHabitIds.toList().toString(),
                                            moodScore = selectedMoodScore,
                                            moodNotes = moodNotes.ifBlank { null }
                                        )
                                        viewModel.updateEntry(updated)
                                    } else {
                                        viewModel.saveAcuteEntry(
                                            occurrenceTime = occurrenceTimestamp,
                                            symptomId = selectedSymptomIds.firstOrNull(),
                                            symptomSeverity = maxSev,
                                            symptomNotes = symptomNotes.ifBlank { null },
                                            interventionIds = selectedInterventionIds.toList(),
                                            interventionNotes = interventionNotes.ifBlank { null },
                                            activityIds = selectedActivityIds.toList(),
                                            activityNotes = activityNotes.ifBlank { null },
                                            habitIds = selectedHabitIds.toList(),
                                            moodScore = selectedMoodScore,
                                            moodNotes = moodNotes.ifBlank { null },
                                            symptomIds = selectedSymptomIds.toList(),
                                            symptomSeveritiesJson = severitiesJson
                                        )
                                    }
                                    onNavigateBack()
                                } else {
                                    viewModel.showStatus("Pilih setidaknya 1 gejala, intervensi, aktivitas, atau mood.")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.pillActiveBg,
                            contentColor = colors.pillActiveText
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("btn_save_entry")
                    ) {
                        Text(
                            text = if (isEditMode) "Simpan" else "SIMPAN",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.headerBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. DUAL MODE SWITCHER: [🌿 Rutinitas Harian] vs [⚡ Pemicu Gejala]
            Surface(
                color = colors.pillInactiveBg,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Rutinitas Harian Tab
                    Surface(
                        color = if (selectedTabMode == 0) colors.cardBackground else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = if (selectedTabMode == 0) 1.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTabMode = 0 }
                            .testTag("tab_mode_routine")
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "🌿 ", fontSize = 13.sp)
                                Text(
                                    text = "Rutinitas Harian",
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTabMode == 0) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTabMode == 0) colors.textPrimary else colors.textSecondary
                                )
                            }
                        }
                    }

                    // Pemicu Gejala Tab
                    Surface(
                        color = if (selectedTabMode == 1) colors.cardBackground else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = if (selectedTabMode == 1) 1.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTabMode = 1 }
                            .testTag("tab_mode_trigger")
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "⚡ ", fontSize = 13.sp)
                                Text(
                                    text = "Pemicu Gejala",
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTabMode == 1) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTabMode == 1) colors.textPrimary else colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            // 2. KHUSUS RUTINITAS HARIAN: MENU PINTASAN RENTANG WAKTU (PAGI, SIANG, SORE, MALAM)
            if (selectedTabMode == 0) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Pintasan Jam Rutinitas (Sekali Klik):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val timeSlots = listOf(
                                Triple("🌅 Pagi", "Jam 6 - 8 Pagi:", 6 to 8),
                                Triple("☀️ Siang", "Jam 9 - 12 Siang:", 9 to 12),
                                Triple("⛅ Sore", "Jam 12 - 5 Sore:", 12 to 17),
                                Triple("🌙 Malam", "Jam 6 - 12 Malam:", 18 to 24)
                            )

                            timeSlots.forEach { (title, header, range) ->
                                Surface(
                                    color = colors.pillInactiveBg,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(0.75.dp, colors.cardBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            applyTimeSlotShortcut(range.first, range.second, title, header)
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = title,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                        Text(
                                            text = "${range.first}-${if (range.second == 24) 12 else range.second}",
                                            fontSize = 9.5.sp,
                                            color = colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. WAKTU KEJADIAN (DATE & TIME PICKER)
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                border = BorderStroke(0.75.dp, colors.cardBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Waktu Catatan:",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary
                        )
                        Text(
                            text = dateFormat.format(Date(occurrenceTimestamp)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            calendar.timeInMillis = occurrenceTimestamp
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    calendar.set(Calendar.YEAR, y)
                                    calendar.set(Calendar.MONTH, m)
                                    calendar.set(Calendar.DAY_OF_MONTH, d)
                                    TimePickerDialog(
                                        context,
                                        { _, hour, minute ->
                                            calendar.set(Calendar.HOUR_OF_DAY, hour)
                                            calendar.set(Calendar.MINUTE, minute)
                                            occurrenceTimestamp = calendar.timeInMillis
                                        },
                                        calendar.get(Calendar.HOUR_OF_DAY),
                                        calendar.get(Calendar.MINUTE),
                                        true
                                    ).show()
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("btn_pick_datetime")
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ubah Waktu", fontSize = 11.5.sp)
                    }
                }
            }

            // ==========================================
            // IF MODE 0: RUTINITAS HARIAN (ALA DAYLIO)
            // ==========================================
            if (selectedTabMode == 0) {
                // KAPASITAS MENTAL / MOOD
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                    border = BorderStroke(0.75.dp, colors.cardBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Emosi & Kapasitas Mental:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val moods = listOf(
                                1 to "😫",
                                2 to "😟",
                                3 to "😐",
                                4 to "🙂",
                                5 to "😌"
                            )
                            for ((score, emoji) in moods) {
                                val isSelected = selectedMoodScore == score
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { selectedMoodScore = score }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) colors.pillActiveBg
                                                else colors.pillInactiveBg
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = emoji, fontSize = 22.sp)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = getMoodLabel(score).split("/").first().trim(),
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) colors.textPrimary else colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // AKTIVITAS HARIAN
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                    border = BorderStroke(0.75.dp, colors.cardBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Aktivitas yang Dilakukan:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            TextButton(
                                onClick = { showQuickAddActivity = true },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Aktivitas Baru", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (act in uiState.activities) {
                                val isSelected = selectedActivityIds.contains(act.id)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (isSelected) selectedActivityIds.remove(act.id)
                                        else selectedActivityIds.add(act.id)
                                    },
                                    label = { Text(act.name, fontSize = 11.5.sp) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null
                                )
                            }
                        }
                    }
                }

                // HABIT / TARGET HARIAN (DIATAS CATATAN SESUAI SCREENSHOT)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                    border = BorderStroke(0.75.dp, colors.cardBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "🔥 ", fontSize = 14.sp)
                                Text(
                                    text = "Habit & Rutinitas Kunci:",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }
                            TextButton(
                                onClick = { showQuickAddHabit = true },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Habit Baru", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(13.dp), tint = colors.textSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Habit dicatat 1x sehari (pagi). Di entri selanjutnya otomatis aktif & tidak tercatat ganda. Klik ✕ untuk membatalkan.",
                                fontSize = 10.5.sp,
                                color = colors.textSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        if (uiState.habits.isEmpty()) {
                            Text(
                                text = "Belum ada habit. Tekan '+ Habit Baru' untuk menambahkan target harianmu!",
                                fontSize = 11.sp,
                                color = colors.textSecondary
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (habit in uiState.habits) {
                                    val isCompletedEarlier = habitsCompletedTodayElsewhere.contains(habit.id)
                                    val isSelectedInEntry = selectedHabitIds.contains(habit.id)
                                    val isHabitActive = isSelectedInEntry || isCompletedEarlier

                                    FilterChip(
                                        selected = isHabitActive,
                                        onClick = {
                                            if (isCompletedEarlier) {
                                                viewModel.cancelHabitForDay(habit.id, occurrenceTimestamp)
                                                selectedHabitIds.remove(habit.id)
                                            } else {
                                                if (isSelectedInEntry) selectedHabitIds.remove(habit.id)
                                                else selectedHabitIds.add(habit.id)
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = "${habit.iconEmoji} ${habit.name}" + (if (isCompletedEarlier) " (Aktif 1x Pagi ✓)" else ""),
                                                fontSize = 11.5.sp
                                            )
                                        },
                                        leadingIcon = if (isHabitActive) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                        } else null,
                                        trailingIcon = if (isCompletedEarlier) {
                                            { Icon(Icons.Default.Close, contentDescription = "Batalkan", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                }

                // CATATAN HARIAN / JOURNAL (PRE-FILLED WITH TIME SLOTS)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                    border = BorderStroke(0.75.dp, colors.cardBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Catatan Rutinitas & Jurnal:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = activityNotes,
                            onValueChange = { activityNotes = it },
                            placeholder = {
                                Text(
                                    "cth:\n- Makan Siang: Enoki + Telur\n- Tiduran\n- Musikan\n- Game Cozy",
                                    fontSize = 11.5.sp
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 110.dp),
                            maxLines = 10
                        )
                    }
                }
            }

            // ==========================================
            // IF MODE 1: PEMICU GEJALA (LENGKAP SEPERTI BIASA)
            // ==========================================
            if (selectedTabMode == 1) {
                // GEJALA & SKALA NYERI
                SectionCard(
                    title = "1. Gejala Fisik (Bisa Pilih > 1 Gejala)",
                    icon = Icons.Default.Healing,
                    onAddQuickClick = { showQuickAddSymptom = true }
                ) {
                    if (uiState.symptoms.isEmpty()) {
                        Text("Belum ada data gejala.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(
                            text = "Pilih gejala yang dirasakan (bisa pilih lebih dari satu):",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (symp in uiState.symptoms) {
                                val isSelected = selectedSymptomIds.contains(symp.id)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (isSelected) {
                                            selectedSymptomIds.remove(symp.id)
                                            symptomSeverities.remove(symp.id)
                                        } else {
                                            selectedSymptomIds.add(symp.id)
                                            if (!symptomSeverities.containsKey(symp.id)) {
                                                symptomSeverities[symp.id] = 5
                                            }
                                        }
                                    },
                                    label = { Text(symp.name, fontSize = 11.5.sp) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    if (selectedSymptomIds.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = colors.pillActiveBg.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.5.dp, colors.cardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.pillActiveBg)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${selectedSymptomIds.size} Gejala Dipilih: " + selectedSymptomIds.mapNotNull { id -> uiState.symptoms.firstOrNull { it.id == id }?.name }.joinToString(", "),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Tingkat Nyeri Masing-Masing Gejala (0 - 10):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            selectedSymptomIds.forEach { sympId ->
                                val symp = uiState.symptoms.firstOrNull { it.id == sympId }
                                val sName = symp?.name ?: "Gejala #$sympId"
                                val currentSev = symptomSeverities[sympId] ?: 5

                                Surface(
                                    color = colors.cardBackground,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(0.75.dp, colors.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(text = "⚡ ", fontSize = 12.sp)
                                                Text(
                                                    text = sName,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.textPrimary
                                                )
                                            }

                                            Surface(
                                                color = getSeverityColor(currentSev),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = if (currentSev == 0) "0 (Bebas Nyeri)" else "$currentSev (${getSeverityTierName(currentSev)})",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            (0..10).forEach { score ->
                                                val isSelected = currentSev == score
                                                Surface(
                                                    color = if (isSelected) (if (score == 0) Color(0xFF059669) else getSeverityColor(score)) else colors.pillInactiveBg,
                                                    shape = RoundedCornerShape(4.dp),
                                                    border = BorderStroke(
                                                        if (isSelected) 1.dp else 0.5.dp,
                                                        if (isSelected) Color.Transparent else colors.cardBorder
                                                    ),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable { symptomSeverities[sympId] = score }
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.padding(vertical = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = "$score",
                                                            fontSize = 10.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isSelected) Color.White else colors.textPrimary
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Slider(
                                            value = currentSev.toFloat(),
                                            onValueChange = { symptomSeverities[sympId] = it.roundToInt() },
                                            valueRange = 0f..10f,
                                            steps = 9,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("slider_symptom_${sympId}")
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = symptomNotes,
                            onValueChange = { symptomNotes = it },
                            label = { Text("Catatan Gejala (lokasi / sensasi)") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2
                        )
                    }
                }

                // 2. INTERVENSI / OBAT
                SectionCard(
                    title = "2. Intervensi / Obat yang Diminum",
                    icon = Icons.Default.Medication,
                    onAddQuickClick = { showQuickAddIntervention = true }
                ) {
                    if (uiState.interventions.isEmpty()) {
                        Text("Belum ada data intervensi.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (interv in uiState.interventions) {
                                val isSelected = selectedInterventionIds.contains(interv.id)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (isSelected) selectedInterventionIds.remove(interv.id)
                                        else selectedInterventionIds.add(interv.id)
                                    },
                                    label = { Text("${interv.name} (${interv.defaultDose})", fontSize = 11.5.sp) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    if (selectedInterventionIds.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = interventionNotes,
                            onValueChange = { interventionNotes = it },
                            label = { Text("Catatan Tambahan Obat / Dosis") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2
                        )
                    }
                }

                // 3. AKTIVITAS & KAPASITAS MENTAL
                SectionCard(
                    title = "3. Aktivitas Sebelumnya & Mental",
                    icon = Icons.Default.DirectionsRun,
                    onAddQuickClick = { showQuickAddActivity = true }
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (act in uiState.activities) {
                            val isSelected = selectedActivityIds.contains(act.id)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) selectedActivityIds.remove(act.id)
                                    else selectedActivityIds.add(act.id)
                                },
                                label = { Text(act.name, fontSize = 11.5.sp) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Kapasitas Mental saat Gejala Muncul:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val moods = listOf(
                            1 to "😫",
                            2 to "😟",
                            3 to "😐",
                            4 to "🙂",
                            5 to "😌"
                        )
                        for ((score, emoji) in moods) {
                            val isSelected = selectedMoodScore == score
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) colors.pillActiveBg
                                        else colors.pillInactiveBg
                                    )
                                    .clickable { selectedMoodScore = score },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 20.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = activityNotes,
                        onValueChange = { activityNotes = it },
                        label = { Text("Catatan Pemicu / Aktivitas") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                }
            }
        }
    }

    // Quick-Add Dialogs
    if (showQuickAddSymptom) {
        QuickAddDialog(
            title = "Tambah Gejala Baru",
            onDismiss = { showQuickAddSymptom = false },
            onConfirm = { name, extra ->
                viewModel.quickAddSymptom(name, extra)
                showQuickAddSymptom = false
            },
            extraLabel = "Bagian Tubuh (cth: Kepala, Leher)"
        )
    }

    if (showQuickAddIntervention) {
        QuickAddDialog(
            title = "Tambah Intervensi / Obat Baru",
            onDismiss = { showQuickAddIntervention = false },
            onConfirm = { name, extra ->
                viewModel.quickAddIntervention(name, extra, "Medis")
                showQuickAddIntervention = false
            },
            extraLabel = "Dosis Default (cth: 1 tablet, 15 menit)"
        )
    }

    if (showQuickAddActivity) {
        QuickAddDialog(
            title = "Tambah Aktivitas Baru",
            onDismiss = { showQuickAddActivity = false },
            onConfirm = { name, extra ->
                viewModel.quickAddActivity(name, extra.ifBlank { "Harian" })
                showQuickAddActivity = false
            },
            extraLabel = "Kategori (cth: Kerja, Diet, Fisik, Rutinitas)"
        )
    }

    if (showQuickAddHabit) {
        QuickAddDialog(
            title = "Tambah Habit / Kebiasaan Baru",
            onDismiss = { showQuickAddHabit = false },
            onConfirm = { name, extra ->
                viewModel.addHabit(name, if (extra.isBlank()) "✨" else extra)
                showQuickAddHabit = false
            },
            extraLabel = "Icon Emoji (cth: 🧘, 💧, 🚶, 😴, ✨)"
        )
    }
}

@Composable
fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onAddQuickClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalHarmonizedColors.current
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        border = BorderStroke(0.75.dp, colors.cardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = colors.pillActiveBg, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                }

                TextButton(onClick = onAddQuickClick, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Tambah", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun QuickAddDialog(
    title: String,
    extraLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, extra: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var extra by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = extra,
                    onValueChange = { extra = it },
                    label = { Text(extraLabel) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), extra.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text("Tambah")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
