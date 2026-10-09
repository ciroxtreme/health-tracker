@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.healthtracker.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthtracker.analytics.AnalyticsEngine
import com.example.healthtracker.model.HabitEntity
import com.example.healthtracker.model.HealthEntryEntity
import com.example.healthtracker.model.InterventionEntity
import com.example.healthtracker.model.SymptomEntity
import com.example.healthtracker.ui.HealthUiState
import com.example.healthtracker.ui.HealthViewModel
import com.example.ui.theme.LocalHarmonizedColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@Composable
fun TimelineScreen(
    uiState: HealthUiState,
    viewModel: HealthViewModel,
    onNavigateToAdd: () -> Unit,
    onEditAcuteClick: (HealthEntryEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalHarmonizedColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var entryToEvaluate by remember { mutableStateOf<HealthEntryEntity?>(null) }
    var evalEntryToEdit by remember { mutableStateOf<HealthEntryEntity?>(null) }
    var entryForFollowUp by remember { mutableStateOf<HealthEntryEntity?>(null) }
    var previousSeverityForFollowUp by remember { mutableStateOf(5) }
    var entryToDelete by remember { mutableStateOf<HealthEntryEntity?>(null) }
    var highlightedEntryId by remember { mutableStateOf<Long?>(null) }

    // Search, Category and Date Filter states
    var searchQuery by remember { mutableStateOf("") }
    var selectedDateFilter by remember { mutableStateOf<Long?>(null) }
    var selectedCategoryFilter by remember { mutableStateOf("SEMUA") } // SEMUA, GEJALA, EVALUASI, RUTINITAS
    var isSearchExpanded by remember { mutableStateOf(false) }

    // Daily Mode Navigation state (Defaults to today in millis)
    var selectedDailyDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    val intervMap = remember(uiState.interventions) { uiState.interventions.associateBy { it.id } }
    val sympMap = remember(uiState.symptoms) { uiState.symptoms.associateBy { it.id } }
    val actMap = remember(uiState.activities) { uiState.activities.associateBy { it.id } }
    val habitMap = remember(uiState.habits) { uiState.habits.associateBy { it.id } }
    val entryMap = remember(uiState.entries) { uiState.entries.associateBy { it.id } }
    val evalsByTargetId = remember(uiState.entries) {
        uiState.entries.filter { it.isEvaluationPhase && it.targetEntryId != null }
            .groupBy { it.targetEntryId!! }
            .mapValues { (_, evals) -> evals.sortedBy { it.occurrenceTime } }
    }

    val dateHeaderFormat = remember { SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")) }
    val filterDateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")) }

    // Evaluated target IDs to identify acute entries missing an evaluation response
    val evaluatedTargetIds = remember(uiState.entries) {
        uiState.entries.filter { it.isEvaluationPhase }.mapNotNull { it.targetEntryId }.toSet()
    }
    val pendingEvaluations = remember(uiState.entries, evaluatedTargetIds) {
        uiState.entries.filter { entry ->
            !entry.isEvaluationPhase &&
            ((entry.symptomId != null) || (entry.symptomSeverity != null && entry.symptomSeverity > 0) || (entry.interventionIdsJson != "[]" && entry.interventionIdsJson.isNotBlank())) &&
            !evaluatedTargetIds.contains(entry.id)
        }
    }

    // Filtered entries based on search, date filter, and timeline mode (Normal vs Daily)
    val filteredEntries = remember(uiState.entries, searchQuery, selectedDateFilter, uiState.timelineMode, selectedDailyDateMillis) {
        uiState.entries.filter { entry ->
            val matchesDate = if (uiState.timelineMode == "daily") {
                val calEntry = Calendar.getInstance().apply { timeInMillis = entry.occurrenceTime }
                val calDaily = Calendar.getInstance().apply { timeInMillis = selectedDailyDateMillis }
                calEntry.get(Calendar.YEAR) == calDaily.get(Calendar.YEAR) &&
                calEntry.get(Calendar.DAY_OF_YEAR) == calDaily.get(Calendar.DAY_OF_YEAR)
            } else {
                if (selectedDateFilter == null) true else {
                    val calEntry = Calendar.getInstance().apply { timeInMillis = entry.occurrenceTime }
                    val calFilter = Calendar.getInstance().apply { timeInMillis = selectedDateFilter!! }
                    calEntry.get(Calendar.YEAR) == calFilter.get(Calendar.YEAR) &&
                    calEntry.get(Calendar.DAY_OF_YEAR) == calFilter.get(Calendar.DAY_OF_YEAR)
                }
            }

            val matchesSearch = if (searchQuery.isBlank()) true else {
                val q = searchQuery.trim().lowercase()
                val sIds = AnalyticsEngine.parseIdList(entry.symptomIdsJson)
                val sNames = sIds.mapNotNull { sympMap[it]?.name?.lowercase() }
                val sSingleName = entry.symptomId?.let { sympMap[it]?.name?.lowercase() } ?: ""
                val sNotes = entry.symptomNotes?.lowercase() ?: ""
                val aNotes = entry.activityNotes?.lowercase() ?: ""
                val eNotes = entry.effectNotes?.lowercase() ?: ""
                val mNotes = entry.moodNotes?.lowercase() ?: ""
                val actNames = AnalyticsEngine.parseIdList(entry.activityIdsJson).mapNotNull { actMap[it]?.name?.lowercase() }
                val intervNames = AnalyticsEngine.parseIdList(entry.interventionIdsJson).mapNotNull { intervMap[it]?.name?.lowercase() }
                val habitNames = AnalyticsEngine.parseIdList(entry.habitIdsJson).mapNotNull { habitMap[it]?.name?.lowercase() }

                sSingleName.contains(q) || sNames.any { it.contains(q) } || sNotes.contains(q) || aNotes.contains(q) ||
                eNotes.contains(q) || mNotes.contains(q) ||
                actNames.any { it.contains(q) } || intervNames.any { it.contains(q) } ||
                habitNames.any { it.contains(q) }
            }

            matchesDate && matchesSearch
        }
    }

    // Category Filter: Semua, Gejala, Evaluasi, Rutinitas
    val categoryFilteredEntries = remember(filteredEntries, selectedCategoryFilter) {
        when (selectedCategoryFilter) {
            "GEJALA" -> filteredEntries.filter {
                !it.isEvaluationPhase && (it.symptomId != null || AnalyticsEngine.parseIdList(it.symptomIdsJson).isNotEmpty() || (it.symptomSeverity ?: 0) > 0)
            }
            "EVALUASI" -> filteredEntries.filter { it.isEvaluationPhase }
            "RUTINITAS" -> filteredEntries.filter {
                !it.isEvaluationPhase && it.symptomId == null && AnalyticsEngine.parseIdList(it.symptomIdsJson).isEmpty() &&
                (it.symptomSeverity == null || it.symptomSeverity == 0) && (it.interventionIdsJson == "[]" || it.interventionIdsJson.isBlank())
            }
            else -> filteredEntries
        }
    }

    // Jump and glow highlight function
    fun jumpToEntry(targetId: Long) {
        selectedCategoryFilter = "SEMUA"
        coroutineScope.launch {
            delay(50)
            val index = categoryFilteredEntries.indexOfFirst { it.id == targetId }
            if (index != -1) {
                listState.animateScrollToItem(index)
                highlightedEntryId = targetId
                delay(2000)
                if (highlightedEntryId == targetId) {
                    highlightedEntryId = null
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToAdd,
                icon = { Icon(Icons.Default.Add, contentDescription = "Tambah Entri") },
                text = { Text("Catat Entri", fontWeight = FontWeight.Bold) },
                containerColor = colors.pillActiveBg,
                contentColor = colors.pillActiveText,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.testTag("fab_add_entry")
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            // Header stats bar with Click-to-Search / Filter (Request 1)
            TimelineHeaderStats(
                totalCount = uiState.entries.size,
                filteredCount = filteredEntries.size,
                entries = uiState.entries,
                isSearchExpanded = isSearchExpanded,
                onToggleSearch = { isSearchExpanded = !isSearchExpanded },
                onPickDateFilter = {
                    val cal = Calendar.getInstance().apply {
                        if (selectedDateFilter != null) timeInMillis = selectedDateFilter!!
                    }
                    DatePickerDialog(
                        context,
                        { _, y, m, d ->
                            cal.set(Calendar.YEAR, y)
                            cal.set(Calendar.MONTH, m)
                            cal.set(Calendar.DAY_OF_MONTH, d)
                            selectedDateFilter = cal.timeInMillis
                            isSearchExpanded = true
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }
            )

            // Reminder Banner: Gejala yang Lupa/Belum Dicatat Evaluasi Responnya (Klik untuk scroll)
            if (pendingEvaluations.isNotEmpty()) {
                Surface(
                    color = if (colors.isDark) Color(0xFF451A03) else Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (colors.isDark) Color(0xFFB45309) else Color(0xFFF59E0B)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 4.dp)
                        .clickable {
                            val firstPending = pendingEvaluations.firstOrNull()
                            if (firstPending != null) {
                                jumpToEntry(firstPending.id)
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${pendingEvaluations.size} Gejala Belum Dievaluasi Respon",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (colors.isDark) Color(0xFFFDE68A) else Color(0xFF92400E)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Scroll ke Sini ↓",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (colors.isDark) Color(0xFFFCD34D) else Color(0xFFB45309)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = if (colors.isDark) Color(0xFFFCD34D) else Color(0xFFB45309),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            // Daily Mode Navigation Header (Active only in Daily Mode)
            if (uiState.timelineMode == "daily") {
                val dailyCal = remember(selectedDailyDateMillis) {
                    Calendar.getInstance().apply { timeInMillis = selectedDailyDateMillis }
                }
                val isToday = remember(selectedDailyDateMillis) {
                    val now = Calendar.getInstance()
                    now.get(Calendar.YEAR) == dailyCal.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) == dailyCal.get(Calendar.DAY_OF_YEAR)
                }

                Surface(
                    color = colors.cardBackground,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = {
                                selectedDailyDateMillis = Calendar.getInstance().apply {
                                    timeInMillis = selectedDailyDateMillis
                                    add(Calendar.DAY_OF_MONTH, -1)
                                }.timeInMillis
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Hari Sebelumnya", modifier = Modifier.size(16.dp))
                            Text("Sebelumnya", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val newCal = Calendar.getInstance().apply {
                                                set(Calendar.YEAR, y)
                                                set(Calendar.MONTH, m)
                                                set(Calendar.DAY_OF_MONTH, d)
                                            }
                                            selectedDailyDateMillis = newCal.timeInMillis
                                        },
                                        dailyCal.get(Calendar.YEAR),
                                        dailyCal.get(Calendar.MONTH),
                                        dailyCal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = dateHeaderFormat.format(Date(selectedDailyDateMillis)),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            if (isToday) {
                                Text(
                                    text = "• Hari Ini •",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textSecondary
                                )
                            } else {
                                Text(
                                    text = "(Klik untuk pilih tanggal)",
                                    fontSize = 9.5.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                selectedDailyDateMillis = Calendar.getInstance().apply {
                                    timeInMillis = selectedDailyDateMillis
                                    add(Calendar.DAY_OF_MONTH, 1)
                                }.timeInMillis
                            },
                            enabled = !isToday,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Selanjutnya", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ArrowForward, contentDescription = "Hari Selanjutnya", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Kategori Filter Tombol: Urutkan/Saring berdasarkan Semua, Gejala, Evaluasi, Rutinitas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val filterCounts = remember(filteredEntries) {
                    val gejalaCount = filteredEntries.count { !it.isEvaluationPhase && (it.symptomId != null || AnalyticsEngine.parseIdList(it.symptomIdsJson).isNotEmpty() || (it.symptomSeverity ?: 0) > 0) }
                    val evalCount = filteredEntries.count { it.isEvaluationPhase }
                    val rutinitasCount = filteredEntries.count { !it.isEvaluationPhase && it.symptomId == null && AnalyticsEngine.parseIdList(it.symptomIdsJson).isEmpty() && (it.symptomSeverity == null || it.symptomSeverity == 0) && (it.interventionIdsJson == "[]" || it.interventionIdsJson.isBlank()) }
                    Triple(gejalaCount, evalCount, rutinitasCount)
                }
                val filters = remember(filteredEntries.size, filterCounts) {
                    listOf(
                        "SEMUA" to "Semua (${filteredEntries.size})",
                        "GEJALA" to "⚡ Gejala (${filterCounts.first})",
                        "EVALUASI" to "💊 Evaluasi (${filterCounts.second})",
                        "RUTINITAS" to "🌿 Rutinitas (${filterCounts.third})"
                    )
                }
                filters.forEach { (key, label) ->
                    val isSelected = selectedCategoryFilter == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategoryFilter = key },
                        label = { Text(text = label, fontSize = 10.5.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.pillActiveBg,
                            selectedLabelColor = colors.pillActiveText
                        )
                    )
                }
            }

            // Search and Date Filter Box (Expandable)
            AnimatedVisibility(
                visible = isSearchExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = colors.cardBackground,
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Cari keluhan, obat, aktivitas...", fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Hapus", modifier = Modifier.size(14.dp))
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )

                            // Pick Date Button
                            OutlinedButton(
                                onClick = {
                                    val cal = Calendar.getInstance().apply {
                                        if (selectedDateFilter != null) timeInMillis = selectedDateFilter!!
                                    }
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            cal.set(Calendar.YEAR, y)
                                            cal.set(Calendar.MONTH, m)
                                            cal.set(Calendar.DAY_OF_MONTH, d)
                                            selectedDateFilter = cal.timeInMillis
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (selectedDateFilter != null) filterDateFormat.format(Date(selectedDateFilter!!)) else "Pilih Tgl",
                                    fontSize = 11.5.sp
                                )
                            }
                        }

                        // Filter tags and clear button if active
                        if (selectedDateFilter != null || searchQuery.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (selectedDateFilter != null) {
                                        Surface(
                                            color = colors.pillActiveBg,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.clickable { selectedDateFilter = null }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "📅 ${filterDateFormat.format(Date(selectedDateFilter!!))}",
                                                    fontSize = 10.5.sp,
                                                    color = colors.pillActiveText
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Hapus",
                                                    modifier = Modifier.size(11.dp),
                                                    tint = colors.pillActiveText
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = "Hasil: ${filteredEntries.size} entri",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textSecondary
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        searchQuery = ""
                                        selectedDateFilter = null
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Text("Reset Filter", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            if (categoryFilteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (uiState.entries.isEmpty()) "Belum Ada Riwayat Entri" else "Tidak Ada Entri yang Cocok",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (uiState.entries.isEmpty())
                                "Mulai catat rutinitas harian, pemicu gejala, atau evaluasi respon intervensi."
                            else if (uiState.timelineMode == "daily")
                                "Tidak ada catatan kesehatan pada ${dateHeaderFormat.format(Date(selectedDailyDateMillis))}. Gunakan tombol '◀ Sebelumnya' untuk melihat hari terdahulu."
                            else "Coba gunakan kata kunci lain, reset tanggal, atau ubah kategori filter.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (selectedDateFilter != null || searchQuery.isNotBlank() || selectedCategoryFilter != "SEMUA") {
                            Button(
                                onClick = {
                                    searchQuery = ""
                                    selectedDateFilter = null
                                    selectedCategoryFilter = "SEMUA"
                                }
                            ) {
                                Text("Tampilkan Semua Log")
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onNavigateToAdd,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.pillActiveBg,
                                        contentColor = colors.pillActiveText
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("empty_add_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Buat Entri")
                                }

                                OutlinedButton(
                                    onClick = { viewModel.seedSampleData() },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("empty_seed_button")
                                ) {
                                    Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.pillActiveBg)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Muat Data Sampel")
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp)
                ) {
                    itemsIndexed(categoryFilteredEntries, key = { _, entry -> entry.id }) { index, entry ->
                        // Request 3: Date Divider Line when date changes
                        val currentDateStr = dateHeaderFormat.format(Date(entry.occurrenceTime))
                        val showDateDivider = if (index == 0) {
                            true
                        } else {
                            val prevDateStr = dateHeaderFormat.format(Date(categoryFilteredEntries[index - 1].occurrenceTime))
                            currentDateStr != prevDateStr
                        }

                        if (showDateDivider) {
                            TimelineDateDivider(dateStr = currentDateStr)
                        }

                        // Chain of all evaluations for this acute entry sorted chronologically
                        val chainEvals = remember(entry.id, evalsByTargetId) {
                            if (!entry.isEvaluationPhase) {
                                evalsByTargetId[entry.id] ?: emptyList()
                            } else emptyList()
                        }
                        val existingEval = chainEvals.firstOrNull()

                        // If evaluation entry, determine if it is the latest in its chain
                        val isLatestInChain = remember(entry.id, entry.targetEntryId, evalsByTargetId) {
                            if (entry.isEvaluationPhase && entry.targetEntryId != null) {
                                val siblings = evalsByTargetId[entry.targetEntryId] ?: emptyList()
                                siblings.lastOrNull()?.id == entry.id
                            } else true
                        }

                        // If evaluation entry, look up its parent acute entry
                        val parentAcute = remember(entry.targetEntryId, entryMap) {
                            if (entry.isEvaluationPhase && entry.targetEntryId != null) {
                                entryMap[entry.targetEntryId]
                            } else null
                        }

                        // Multiple symptoms resolution
                        val entrySymptomIds = remember(entry.id, entry.symptomIdsJson, entry.symptomId) {
                            val list = AnalyticsEngine.parseIdList(entry.symptomIdsJson)
                            if (list.isNotEmpty()) list else listOfNotNull(entry.symptomId)
                        }
                        val entrySymptomNames = remember(entrySymptomIds, sympMap) {
                            entrySymptomIds.mapNotNull { sympMap[it]?.name }
                        }
                        val severityMap = remember(entry.id, entry.symptomSeveritiesJson) {
                            AnalyticsEngine.parseSeverityMap(entry.symptomSeveritiesJson)
                        }
                        val symptomsWithSeverity = remember(entrySymptomIds, severityMap, sympMap, entry.symptomSeverity, entrySymptomNames) {
                            val mapped = entrySymptomIds.mapNotNull { id ->
                                val name = sympMap[id]?.name ?: return@mapNotNull null
                                val sev = severityMap[id] ?: entry.symptomSeverity ?: 5
                                Triple(id, name, sev)
                            }
                            if (mapped.isNotEmpty()) mapped else entrySymptomNames.map { Triple(entry.symptomId ?: 0L, it, entry.symptomSeverity ?: 5) }
                        }

                        val parentSymptomNames = remember(parentAcute?.id, parentAcute?.symptomIdsJson, parentAcute?.symptomId, sympMap) {
                            if (parentAcute == null) emptyList<String>()
                            else {
                                val list = AnalyticsEngine.parseIdList(parentAcute.symptomIdsJson).mapNotNull { sympMap[it]?.name }
                                if (list.isNotEmpty()) list
                                else listOfNotNull(parentAcute.symptomId?.let { sympMap[it]?.name })
                            }
                        }

                        // Memoized lookups for habits, interventions, activities
                        val entryHabits = remember(entry.id, entry.habitIdsJson, habitMap) {
                            AnalyticsEngine.parseIdList(entry.habitIdsJson).mapNotNull { habitMap[it] }
                        }
                        val entryIntervNames = remember(entry.id, entry.interventionIdsJson, intervMap) {
                            AnalyticsEngine.parseIdList(entry.interventionIdsJson).mapNotNull { intervMap[it]?.name }
                        }
                        val entryActNames = remember(entry.id, entry.activityIdsJson, actMap) {
                            AnalyticsEngine.parseIdList(entry.activityIdsJson).mapNotNull { actMap[it]?.name }
                        }
                        val parentAcuteIntervs = remember(parentAcute?.id, parentAcute?.interventionIdsJson, intervMap) {
                            AnalyticsEngine.parseIdList(parentAcute?.interventionIdsJson ?: "").mapNotNull { intervMap[it]?.name }
                        }

                        EntryCard(
                            entry = entry,
                            sympNames = entrySymptomNames,
                            symptomsWithSeverity = symptomsWithSeverity,
                            intervNames = entryIntervNames,
                            actNames = entryActNames,
                            habits = entryHabits,
                            parentAcute = parentAcute,
                            parentAcuteSymptoms = parentSymptomNames,
                            parentAcuteIntervs = parentAcuteIntervs,
                            existingEval = existingEval,
                            chainEvals = chainEvals,
                            isLatestInChain = isLatestInChain,
                            isHighlighted = entry.id == highlightedEntryId,
                            onJumpToEntry = { jumpToEntry(it) },
                            onEvaluateClick = { entryToEvaluate = entry },
                            onFollowUpClick = { target, prevSev ->
                                entryForFollowUp = target
                                previousSeverityForFollowUp = prevSev
                            },
                            onEditClick = {
                                if (entry.isEvaluationPhase) {
                                    evalEntryToEdit = entry
                                } else {
                                    onEditAcuteClick(entry)
                                }
                            },
                            onDeleteClick = { entryToDelete = entry }
                        )
                    }
                }
            }
        }
    }

    // Modal Dialog: Catat Evaluasi Respon Obat
    if (entryToEvaluate != null) {
        val target = entryToEvaluate!!
        val targetSympIds = AnalyticsEngine.parseIdList(target.symptomIdsJson).ifEmpty { listOfNotNull(target.symptomId) }
        val initSevMap = AnalyticsEngine.parseSeverityMap(target.symptomSeveritiesJson)
        val symptomsWithInitial = if (targetSympIds.isNotEmpty()) {
            targetSympIds.map { id ->
                val name = sympMap[id]?.name ?: "Gejala #$id"
                val initSev = initSevMap[id] ?: target.symptomSeverity ?: 5
                Triple(id, name, initSev)
            }
        } else {
            val singleName = target.symptomId?.let { sympMap[it]?.name } ?: "Keluhan Utama"
            listOf(Triple(target.symptomId ?: 0L, singleName, target.symptomSeverity ?: 5))
        }

        RecordEvaluationDialog(
            targetEntry = target,
            targetSymptoms = symptomsWithInitial,
            onDismiss = { entryToEvaluate = null },
            onConfirm = { finalSev, finalMap, notes, mood, customEvalTime ->
                viewModel.saveEvaluationEntry(
                    targetEntry = target,
                    evaluationTime = customEvalTime,
                    finalSeverity = finalSev,
                    effectNotes = notes,
                    moodScore = mood,
                    finalSymptomSeverities = finalMap
                )
                entryToEvaluate = null
            }
        )
    }

    // Modal Dialog: Edit Evaluasi Respon
    if (evalEntryToEdit != null) {
        val target = evalEntryToEdit!!
        val acuteParent = if (target.targetEntryId != null) {
            uiState.entries.firstOrNull { it.id == target.targetEntryId }
        } else null

        val targetSympIds = AnalyticsEngine.parseIdList(acuteParent?.symptomIdsJson ?: target.symptomIdsJson)
            .ifEmpty { listOfNotNull(acuteParent?.symptomId ?: target.symptomId) }
        val initSevMap = AnalyticsEngine.parseSeverityMap(acuteParent?.symptomSeveritiesJson ?: target.symptomSeveritiesJson)
        val symptomsWithInitial = if (targetSympIds.isNotEmpty()) {
            targetSympIds.map { id ->
                val name = sympMap[id]?.name ?: "Gejala #$id"
                val initSev = initSevMap[id] ?: acuteParent?.symptomSeverity ?: target.symptomSeverity ?: 5
                Triple(id, name, initSev)
            }
        } else {
            val singleName = target.symptomId?.let { sympMap[it]?.name } ?: "Keluhan Utama"
            listOf(Triple(target.symptomId ?: 0L, singleName, acuteParent?.symptomSeverity ?: target.symptomSeverity ?: 5))
        }

        EditEvaluationModalDialog(
            entry = target,
            acuteParent = acuteParent,
            targetSymptoms = symptomsWithInitial,
            onDismiss = { evalEntryToEdit = null },
            onSave = { updatedEntry ->
                viewModel.updateEntry(updatedEntry)
                evalEntryToEdit = null
            }
        )
    }

    // Modal Dialog: Tambah Intervensi Lanjutan
    if (entryForFollowUp != null) {
        val target = entryForFollowUp!!
        val rootAcute = remember(target, uiState.entries) {
            if (target.isEvaluationPhase) {
                uiState.entries.firstOrNull { it.id == target.targetEntryId } ?: target
            } else target
        }
        FollowUpInterventionDialog(
            targetEntry = target,
            previousSeverity = previousSeverityForFollowUp,
            availableInterventions = uiState.interventions,
            onDismiss = { entryForFollowUp = null },
            onConfirm = { newIntervIds, newSev, notes, mood, followUpTime ->
                viewModel.saveFollowUpIntervention(
                    targetEntry = rootAcute,
                    previousSeverity = previousSeverityForFollowUp,
                    followUpTime = followUpTime,
                    newInterventionIds = newIntervIds,
                    newSeverity = newSev,
                    effectNotes = notes,
                    moodScore = mood
                )
                entryForFollowUp = null
            }
        )
    }

    // Confirmation Dialog: Delete
    if (entryToDelete != null) {
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Hapus Entri?") },
            text = { Text("Entri kesehatan ini akan dihapus secara permanen dari database lokal.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        entryToDelete?.id?.let { viewModel.deleteEntry(it) }
                        entryToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

// REQUEST 3: DATE DIVIDER LINE FOR TIMELINE
@Composable
fun TimelineDateDivider(dateStr: String) {
    val colors = LocalHarmonizedColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = colors.cardBorder)
        Surface(
            color = colors.pillInactiveBg,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(0.5.dp, colors.cardBorder),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(text = "📅 ", fontSize = 11.sp)
                Text(
                    text = dateStr,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary
                )
            }
        }
        HorizontalDivider(modifier = Modifier.weight(1f), color = colors.cardBorder)
    }
}

// REQUEST 1: CLICKABLE TOTAL LOG HEADER STATS
@Composable
fun TimelineHeaderStats(
    totalCount: Int,
    filteredCount: Int,
    entries: List<HealthEntryEntity>,
    isSearchExpanded: Boolean,
    onToggleSearch: () -> Unit,
    onPickDateFilter: () -> Unit
) {
    val colors = LocalHarmonizedColors.current
    val routineCount = entries.count { !it.isEvaluationPhase && it.symptomId == null && (it.symptomSeverity == null || it.symptomSeverity == 0) && (it.interventionIdsJson == "[]" || it.interventionIdsJson.isBlank()) }
    val pemicuCount = entries.count { !it.isEvaluationPhase } - routineCount
    val evalCount = entries.count { it.isEvaluationPhase }

    val pains = entries.mapNotNull { it.symptomSeverity ?: it.finalSeverity }
    val avgPain = if (pains.isNotEmpty()) (pains.average() * 10).toInt() / 10.0 else 0.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 0.dp, bottom = 3.dp)
            .clickable { onToggleSearch() },
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        border = BorderStroke(0.75.dp, colors.cardBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Total Log: $totalCount entri",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = if (isSearchExpanded) colors.pillActiveBg else colors.pillInactiveBg,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.clickable { onToggleSearch() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Cari",
                                modifier = Modifier.size(11.dp),
                                tint = if (isSearchExpanded) colors.pillActiveText else colors.pillInactiveText
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Cari",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSearchExpanded) colors.pillActiveText else colors.pillInactiveText
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        color = colors.pillInactiveBg,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.clickable { onPickDateFilter() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = "Pilih Tanggal",
                                modifier = Modifier.size(11.dp),
                                tint = colors.pillInactiveText
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Pilih Tgl",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.pillInactiveText
                            )
                        }
                    }
                }
                Text(
                    text = "$pemicuCount Pemicu • $evalCount Evaluasi • $routineCount Rutinitas",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(getSeverityColor(avgPain.toInt()))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Rerata: $avgPain/10",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
            }
        }
    }
}

// MINI PAIN SPARKLINE CHART FOR FOLLOW-UP PROGRESSION
@Composable
fun MiniPainSparkline(
    painPoints: List<Int>,
    title: String = "📈 Tren Penurunan Nyeri:",
    modifier: Modifier = Modifier
) {
    val colors = LocalHarmonizedColors.current
    Surface(
        color = colors.cardBackground,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.75.dp, colors.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = painPoints.joinToString(" ➔ ") { "$it" },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (painPoints.lastOrNull() == 0) Color(0xFF059669) else colors.pillActiveBg
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                if (painPoints.size < 2) return@Canvas
                val width = size.width
                val height = size.height
                val padX = 20f
                val padY = 8f
                val usableWidth = width - (2 * padX)
                val usableHeight = height - (2 * padY)
                val stepX = usableWidth / (painPoints.size - 1)

                val points = painPoints.mapIndexed { idx, value ->
                    val x = padX + (idx * stepX)
                    val y = padY + (usableHeight * (10 - value.coerceIn(0, 10)) / 10f)
                    Offset(x, y)
                }

                // Draw gradient under line
                val fillPath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                    lineTo(points.last().x, height)
                    lineTo(points.first().x, height)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            colors.pillActiveBg.copy(alpha = 0.22f),
                            Color.Transparent
                        )
                    )
                )

                // Draw line
                val linePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                }
                drawPath(
                    path = linePath,
                    color = colors.pillActiveBg,
                    style = Stroke(
                        width = 2.5f,
                        cap = StrokeCap.Round
                    )
                )

                // Draw circles for data points
                points.forEachIndexed { idx, pt ->
                    val ptColor = if (painPoints[idx] == 0) Color(0xFF059669) else getSeverityColor(painPoints[idx])
                    drawCircle(
                        color = Color.White,
                        radius = 4.5f,
                        center = pt
                    )
                    drawCircle(
                        color = ptColor,
                        radius = 3.5f,
                        center = pt
                    )
                }
            }
        }
    }
}

@Composable
fun EntryCard(
    entry: HealthEntryEntity,
    sympNames: List<String>,
    symptomsWithSeverity: List<Triple<Long, String, Int>>,
    intervNames: List<String>,
    actNames: List<String>,
    habits: List<HabitEntity>,
    parentAcute: HealthEntryEntity?,
    parentAcuteSymptoms: List<String>,
    parentAcuteIntervs: List<String>,
    existingEval: HealthEntryEntity?,
    chainEvals: List<HealthEntryEntity>,
    isLatestInChain: Boolean,
    isHighlighted: Boolean,
    onJumpToEntry: (Long) -> Unit,
    onEvaluateClick: () -> Unit,
    onFollowUpClick: (HealthEntryEntity, Int) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val colors = LocalHarmonizedColors.current
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale("id", "ID")) }
    val timeStr = dateFormat.format(Date(entry.occurrenceTime))

    val isEval = entry.isEvaluationPhase
    val isRoutine = !isEval && entry.symptomId == null && (entry.symptomSeverity == null || entry.symptomSeverity == 0) && (entry.interventionIdsJson == "[]" || entry.interventionIdsJson.isBlank())

    // State for Gaya A: Expandable Notes
    var isNotesOpen by remember { mutableStateOf(false) }

    // Consolidated note text
    val noteText = remember(entry) {
        if (entry.isEvaluationPhase) {
            entry.effectNotes?.takeIf { it.isNotBlank() }
        } else {
            val list = mutableListOf<String>()
            entry.symptomNotes?.takeIf { it.isNotBlank() }?.let { list.add("Keluhan: $it") }
            entry.interventionNotes?.takeIf { it.isNotBlank() }?.let { list.add("Dosis/Obat: $it") }
            entry.activityNotes?.takeIf { it.isNotBlank() }?.let { list.add(it) }
            entry.moodNotes?.takeIf { it.isNotBlank() }?.let { list.add(it) }
            list.joinToString("\n").takeIf { it.isNotBlank() }
        }
    }

    // Border with glow effect when jumped to
    val borderWidth by animateDpAsState(
        targetValue = if (isHighlighted) 2.5.dp else 0.75.dp,
        animationSpec = tween(durationMillis = 300)
    )
    val borderColor = if (isHighlighted) {
        if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    } else {
        colors.cardBorder
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("entry_card_${entry.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) {
                if (colors.isDark) Color(0xFF1E293B) else Color(0xFFEFF6FF)
            } else {
                colors.cardBackground
            }
        ),
        border = BorderStroke(borderWidth, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isHighlighted) 3.dp else 0.5.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Type badge, Date, and Actions (Edit ✏️ & Delete ✕)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isEval) {
                        Surface(
                            color = if (colors.isDark) Color(0xFF14532D) else Color(0xFFF0FDF4),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "EVALUASI RESPON",
                                color = if (colors.isDark) Color(0xFF86EFAC) else Color(0xFF15803D),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    } else if (isRoutine) {
                        Surface(
                            color = if (colors.isDark) Color(0xFF0C4A6E) else Color(0xFFE0F2FE),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "RUTINITAS",
                                color = if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = colors.pillInactiveBg,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "PEMICU",
                                color = colors.pillInactiveText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        fontSize = 11.sp
                    )
                }

                // Action buttons: Edit ✏️ and Delete ✕
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(24.dp).testTag("edit_entry_${entry.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(24.dp).testTag("delete_entry_${entry.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Hapus",
                            tint = colors.textMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // GABUNGAN OPSI 2 + 3: Mini Quote Card for Evaluation pointing to Parent Acute Entry
            if (isEval && parentAcute != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = colors.pillInactiveBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onJumpToEntry(parentAcute.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Reply,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val parentSympText = if (parentAcuteSymptoms.isNotEmpty()) parentAcuteSymptoms.joinToString(" • ") else "Gejala"
                            Text(
                                text = "Merespon: $parentSympText (${timeFormat.format(Date(parentAcute.occurrenceTime))})${if (parentAcuteIntervs.isNotEmpty()) " • " + parentAcuteIntervs.joinToString() else ""}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Buka entri asal",
                            modifier = Modifier.size(12.dp),
                            tint = colors.textMuted
                        )
                    }
                }
            }

            // REQUEST 2: HABIT CHIPS (ACCORDING TO SCREENSHOT AT TOP OF NOTES)
            if (habits.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    habits.forEach { h ->
                        Surface(
                            color = colors.pillInactiveBg,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.5.dp, colors.cardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = h.iconEmoji, fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = h.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // VIEW BRANCHING: EVALUATION vs ROUTINE vs PEMICU
            if (isEval) {
                // EVALUATION VIEW (Displays all treated symptoms)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val evalSympTitle = if (sympNames.isNotEmpty()) sympNames.joinToString(" • ") else "Gejala Terkait"
                        Text(
                            text = evalSympTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Nyeri Akhir: ${entry.finalSeverity ?: 0}/10 • Jeda: ${entry.reactionTimeMinutes ?: 0} mnt",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )
                    }

                    // Delta Badge
                    val delta = entry.painDelta ?: 0
                    val isImprovement = delta >= 2
                    val isAdverse = delta < 0
                    val badgeBg = when {
                        isImprovement -> if (colors.isDark) Color(0xFF064E3B) else Color(0xFFECFDF5)
                        isAdverse -> if (colors.isDark) Color(0xFF7F1D1D) else Color(0xFFFEF2F2)
                        else -> if (colors.isDark) Color(0xFF78350F) else Color(0xFFFFFBEB)
                    }
                    val badgeTextColor = when {
                        isImprovement -> if (colors.isDark) Color(0xFF6EE7B7) else Color(0xFF059669)
                        isAdverse -> if (colors.isDark) Color(0xFFFCA5A5) else Color(0xFFDC2626)
                        else -> if (colors.isDark) Color(0xFFFCD34D) else Color(0xFFD97706)
                    }

                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isImprovement) Icons.Default.TrendingDown
                                else if (isAdverse) Icons.Default.TrendingUp
                                else Icons.Default.LinearScale,
                                contentDescription = null,
                                tint = badgeTextColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Delta: ${if (delta > 0) "+$delta" else "$delta"}",
                                color = badgeTextColor,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Multi-gejala evaluation breakdown if multiple symptoms exist
                val evalSymptomIds = remember(entry, parentAcute) {
                    val fromEval = AnalyticsEngine.parseIdList(entry.symptomIdsJson)
                    if (fromEval.isNotEmpty()) fromEval
                    else {
                        val fromParent = AnalyticsEngine.parseIdList(parentAcute?.symptomIdsJson ?: "")
                        if (fromParent.isNotEmpty()) fromParent
                        else listOfNotNull(entry.symptomId ?: parentAcute?.symptomId)
                    }
                }
                val parentInitMap = remember(parentAcute) {
                    AnalyticsEngine.parseSeverityMap(parentAcute?.symptomSeveritiesJson)
                }
                val evalSevMap = remember(entry) {
                    AnalyticsEngine.parseSeverityMap(entry.symptomSeveritiesJson)
                }

                if (evalSymptomIds.size > 1) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        evalSymptomIds.forEachIndexed { idx, sId ->
                            val sName = parentAcuteSymptoms.getOrNull(idx) ?: sympNames.getOrNull(idx) ?: "Gejala"
                            val initSev = parentInitMap[sId] ?: parentAcute?.symptomSeverity ?: 5
                            val finalSev = evalSevMap[sId] ?: entry.finalSeverity ?: 0
                            val sDelta = initSev - finalSev
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = colors.pillInactiveBg,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.5.dp, colors.cardBorder)
                                ) {
                                    Text(
                                        text = "⚡ $sName",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "$initSev ➔ $finalSev",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = getSeverityColor(finalSev)
                                    )
                                    val dText = if (sDelta >= 0) "+$sDelta" else "$sDelta"
                                    val dColor = if (sDelta > 0) Color(0xFF059669) else if (sDelta == 0) Color(0xFFD97706) else Color(0xFFDC2626)
                                    val dBg = if (sDelta > 0) (if (colors.isDark) Color(0xFF064E3B) else Color(0xFFECFDF5))
                                    else if (sDelta == 0) (if (colors.isDark) Color(0xFF78350F) else Color(0xFFFFFBEB))
                                    else (if (colors.isDark) Color(0xFF7F1D1D) else Color(0xFFFEF2F2))
                                    Surface(
                                        color = dBg,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "($dText)",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = dColor,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Intervensi Lanjutan action if finalSeverity > 0 and isLatestInChain
                if ((entry.finalSeverity ?: 0) > 0 && isLatestInChain) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onFollowUpClick(entry, entry.finalSeverity ?: 0) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0284C7),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.fillMaxWidth().testTag("btn_followup_eval_${entry.id}")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tambah Intervensi Lanjutan (Nyeri Belum 0)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if ((entry.finalSeverity ?: 0) == 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = if (colors.isDark) Color(0xFF064E3B) else Color(0xFFECFDF5),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (colors.isDark) Color(0xFF059669) else Color(0xFFA7F3D0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "✓ Respon Selesai (Bebas Nyeri 0/10)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF059669)
                            )
                        }
                    }
                }
            } else if (isRoutine) {
                // ROUTINE VIEW (ALA DAYLIO)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = getMoodEmoji(entry.moodScore),
                        fontSize = 26.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = getMoodLabel(entry.moodScore),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            fontSize = 15.sp
                        )
                        if (actNames.isNotEmpty()) {
                            Text(
                                text = actNames.joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            } else {
                // PEMICU / TRIGGER VIEW (Vertical stacked rows for multi-symptom & multi-pain with individual sparkline charts)
                if (symptomsWithSeverity.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        symptomsWithSeverity.forEach { (sId, sName, sev) ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Row 1: [⚡ Gejala] ........................ [Label Nyeri]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Surface(
                                        color = colors.pillInactiveBg,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(0.5.dp, colors.cardBorder)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(text = "⚡ ", fontSize = 11.5.sp)
                                            Text(
                                                text = sName,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.textPrimary
                                            )
                                        }
                                    }

                                    val sevColor = getSeverityColor(sev)
                                    Surface(
                                        color = sevColor.copy(alpha = if (colors.isDark) 0.25f else 0.12f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (sev == 0) "0 (Bebas Nyeri)" else "Nyeri: $sev/10 (${getSeverityTierName(sev)})",
                                            color = sevColor,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                // Grafik Tren Penurunan tepat di bawah gejala ini jika memiliki evaluasi
                                if (chainEvals.isNotEmpty()) {
                                    val evalProgression = chainEvals.mapNotNull { eval ->
                                        val sMap = AnalyticsEngine.parseSeverityMap(eval.symptomSeveritiesJson)
                                        sMap[sId] ?: eval.finalSeverity
                                    }
                                    val symptomPainPoints = listOf(sev) + evalProgression
                                    if (symptomPainPoints.size >= 2) {
                                        MiniPainSparkline(
                                            painPoints = symptomPainPoints,
                                            title = "📈 Tren Penurunan ($sName):"
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (sympNames.isNotEmpty() || entry.symptomSeverity != null) {
                    val sName = sympNames.firstOrNull() ?: "Gejala Fisik"
                    val sev = entry.symptomSeverity ?: 0
                    val sevColor = getSeverityColor(sev)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                color = colors.pillInactiveBg,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.5.dp, colors.cardBorder)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(text = "⚡ ", fontSize = 11.5.sp)
                                    Text(
                                        text = sName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                }
                            }

                            Surface(
                                color = sevColor.copy(alpha = if (colors.isDark) 0.25f else 0.12f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (sev == 0) "0 (Bebas Nyeri)" else "Nyeri: $sev/10 (${getSeverityTierName(sev)})",
                                    color = sevColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }

                        if (chainEvals.isNotEmpty()) {
                            val evalProgression = chainEvals.mapNotNull { it.finalSeverity }
                            val symptomPainPoints = listOf(sev) + evalProgression
                            if (symptomPainPoints.size >= 2) {
                                MiniPainSparkline(
                                    painPoints = symptomPainPoints,
                                    title = "📈 Tren Penurunan ($sName):"
                                )
                            }
                        }
                    }
                }

                // If not yet evaluated, display a prominent quick-evaluate indicator!
                if (existingEval == null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = if (colors.isDark) Color(0xFF451A03) else Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.75.dp, if (colors.isDark) Color(0xFFB45309) else Color(0xFFF59E0B)),
                        modifier = Modifier.clickable { onEvaluateClick() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Belum Dievaluasi • Catat Respon Sekarang ❯",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (colors.isDark) Color(0xFFFDE68A) else Color(0xFF92400E)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = if (colors.isDark) Color(0xFF064E3B) else Color(0xFFECFDF5),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.5.dp, if (colors.isDark) Color(0xFF047857) else Color(0xFFA7F3D0)),
                        modifier = Modifier.clickable { onJumpToEntry(existingEval.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Evaluasi Selesai (Delta: ${if ((existingEval.painDelta ?: 0) >= 0) "+" else ""}${existingEval.painDelta ?: 0}) • Lihat ❯",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (colors.isDark) Color(0xFF6EE7B7) else Color(0xFF047857)
                            )
                        }
                    }
                }

                // Interventions taken
                if (intervNames.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Medication,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                        )
                        Text(
                            text = "Intervensi:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                        )
                        Text(
                            text = intervNames.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Activities logged
                if (actNames.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsRun,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (colors.isDark) Color(0xFF2DD4BF) else Color(0xFF0D9488)
                        )
                        Text(
                            text = "Aktivitas:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (colors.isDark) Color(0xFF2DD4BF) else Color(0xFF0D9488)
                        )
                        Text(
                            text = actNames.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Mood for non-routine if present
            if (!isRoutine && entry.moodScore != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = getMoodEmoji(entry.moodScore),
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Kapasitas Mental: ${getMoodLabel(entry.moodScore)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            }

            // GAYA A: EXPANDABLE SLIDE-DOWN NOTES (KEEPS CARD AESTHETIC & CLEAN!)
            if (!noteText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = colors.pillInactiveBg,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, colors.cardBorder),
                    modifier = Modifier.clickable { isNotesOpen = !isNotesOpen }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(text = "📝 ", fontSize = 10.sp)
                        Text(
                            text = if (isNotesOpen) "Tutup Catatan ˄" else "Lihat Catatan ˅",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.pillInactiveText
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isNotesOpen,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        color = colors.pillInactiveBg,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.75.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        Row(modifier = Modifier.padding(8.dp)) {
                            Text(text = "💬 ", fontSize = 12.sp)
                            Text(
                                text = noteText,
                                fontSize = 11.5.sp,
                                color = colors.textPrimary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // ACTION: "CATAT RESPON OBAT" / "INTERVENSI LANJUTAN" (ONLY FOR PEMICU WITH INTERVENTIONS)
            if (!isEval && !isRoutine && intervNames.isNotEmpty() && entry.symptomSeverity != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (existingEval != null) {
                        val latestEval = chainEvals.lastOrNull() ?: existingEval
                        val latestSeverity = latestEval.finalSeverity ?: 0
                        if (latestSeverity == 0) {
                            // SUDAH TUNTAS KE 0: Teks [✓ Respon Selesai] tanpa icon bulat di depannya
                            Surface(
                                color = if (colors.isDark) Color(0xFF064E3B) else Color(0xFFECFDF5),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (colors.isDark) Color(0xFF059669) else Color(0xFFA7F3D0)),
                                modifier = Modifier.clickable { onJumpToEntry(latestEval.id) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "✓ Respon Selesai",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF059669)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = "Lihat evaluasi",
                                        tint = Color(0xFF059669),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        } else {
                            // BELUM MENCAPAI 0: Tampilkan opsi Tambah Intervensi Lanjutan
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = if (colors.isDark) Color(0xFF451A03) else Color(0xFFFEF3C7),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (colors.isDark) Color(0xFFB45309) else Color(0xFFF59E0B)),
                                    modifier = Modifier.clickable { onJumpToEntry(latestEval.id) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Nyeri: $latestSeverity/10 ❯",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (colors.isDark) Color(0xFFFDE68A) else Color(0xFFB45309)
                                        )
                                    }
                                }

                                Button(
                                    onClick = { onFollowUpClick(latestEval, latestSeverity) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0284C7),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("btn_followup_from_acute_${entry.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Intervensi Lanjutan",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    } else {
                        Button(
                            onClick = onEvaluateClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                contentColor = Color.White
                            ),
                            border = null,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_eval_${entry.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Catat Respon Obat",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

// EDIT EVALUATION MODAL DIALOG (FAST MODAL FOR EVALUATION ENTRIES)
@Composable
fun EditEvaluationModalDialog(
    entry: HealthEntryEntity,
    acuteParent: HealthEntryEntity?,
    targetSymptoms: List<Triple<Long, String, Int>>,
    onDismiss: () -> Unit,
    onSave: (HealthEntryEntity) -> Unit
) {
    val colors = LocalHarmonizedColors.current
    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance().apply { timeInMillis = entry.occurrenceTime } }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")) }

    var occurrenceTimestamp by remember { mutableStateOf(entry.occurrenceTime) }
    var notesInput by remember { mutableStateOf(entry.effectNotes ?: "") }
    var selectedMood by remember { mutableStateOf(entry.moodScore ?: 4) }

    val existingSevMap = remember(entry) {
        AnalyticsEngine.parseSeverityMap(entry.symptomSeveritiesJson)
    }
    val symptomSeverities = remember(targetSymptoms, existingSevMap) {
        mutableStateMapOf<Long, Int>().apply {
            targetSymptoms.forEach { (id, _, initSev) ->
                put(id, existingSevMap[id] ?: entry.finalSeverity ?: (initSev - 3).coerceAtLeast(0))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Edit Evaluasi Respon",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colors.textPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Time editor
                Surface(
                    color = colors.pillInactiveBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Waktu Evaluasi:", fontSize = 10.sp, color = colors.textSecondary)
                            Text(
                                text = dateFormat.format(Date(occurrenceTimestamp)),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }

                        TextButton(
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
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Ubah Waktu", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Per-symptom severity sliders
                Text(
                    text = "Pengaturan Nyeri Per Gejala:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )

                if (targetSymptoms.size > 1) {
                    // Quick presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = if (colors.isDark) Color(0xFF064E3B) else Color(0xFFECFDF5),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.75.dp, Color(0xFF10B981)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    targetSymptoms.forEach { (id, _, _) -> symptomSeverities[id] = 0 }
                                }
                        ) {
                            Text(
                                text = "✓ Bebas Semua (0)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF059669),
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp)
                            )
                        }
                        Surface(
                            color = colors.pillInactiveBg,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.5.dp, colors.cardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    targetSymptoms.forEach { (id, _, initSev) ->
                                        symptomSeverities[id] = (initSev - 3).coerceAtLeast(0)
                                    }
                                }
                        ) {
                            Text(
                                text = "📉 Semua -3",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp)
                            )
                        }
                    }
                }

                targetSymptoms.forEach { (sId, sName, initSev) ->
                    val curSev = symptomSeverities[sId] ?: (initSev - 3).coerceAtLeast(0)
                    val sDelta = initSev - curSev
                    Surface(
                        color = colors.pillInactiveBg,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.5.dp, colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⚡ $sName (Awal: $initSev/10)",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                val sDeltaColor = if (sDelta > 0) Color(0xFF059669) else if (sDelta == 0) Color(0xFFD97706) else Color(0xFFDC2626)
                                Text(
                                    text = if (sDelta > 0) "+$sDelta (Membaik)" else if (sDelta == 0) "0 (Tetap)" else "$sDelta (Memburuk)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = sDeltaColor
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Slider(
                                    value = curSev.toFloat(),
                                    onValueChange = { symptomSeverities[sId] = it.roundToInt() },
                                    valueRange = 0f..10f,
                                    steps = 9,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    color = getSeverityColor(curSev),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "$curSev",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Notes field
                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text("Catatan Perubahan Kondisi") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                // Mood selector
                Text(
                    text = "Kapasitas Mental:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (m in 1..5) {
                        val isSelected = selectedMood == m
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) colors.pillActiveBg.copy(alpha = 0.2f)
                                    else colors.pillInactiveBg
                                )
                                .clickable { selectedMood = m },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = getMoodEmoji(m), fontSize = 16.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalMap = symptomSeverities.toMap()
                    val maxSeverity = finalMap.values.maxOrNull() ?: entry.finalSeverity ?: 0
                    val initial = acuteParent?.symptomSeverity ?: 0
                    val delta = initial - maxSeverity
                    val lagMins = if (acuteParent != null) {
                        ((occurrenceTimestamp - acuteParent.occurrenceTime) / (60 * 1000L)).toInt().coerceAtLeast(0)
                    } else entry.reactionTimeMinutes

                    val updated = entry.copy(
                        occurrenceTime = occurrenceTimestamp,
                        finalSeverity = maxSeverity,
                        symptomSeveritiesJson = AnalyticsEngine.formatSeverityMap(finalMap),
                        painDelta = delta,
                        reactionTimeMinutes = lagMins,
                        effectNotes = notesInput,
                        moodScore = selectedMood
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.pillActiveBg,
                    contentColor = colors.pillActiveText
                )
            ) {
                Text("Simpan Perubahan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun RecordEvaluationDialog(
    targetEntry: HealthEntryEntity,
    targetSymptoms: List<Triple<Long, String, Int>>,
    onDismiss: () -> Unit,
    onConfirm: (finalSeverity: Int, finalSymptomSeverities: Map<Long, Int>, notes: String, moodScore: Int?, customEvalTime: Long) -> Unit
) {
    val colors = LocalHarmonizedColors.current
    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance() }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")) }

    val defaultLagMs = 60 * 60 * 1000L
    var evaluationTimestamp by remember {
        mutableStateOf(
            if (targetEntry.occurrenceTime + defaultLagMs <= System.currentTimeMillis()) {
                targetEntry.occurrenceTime + defaultLagMs
            } else {
                System.currentTimeMillis()
            }
        )
    }

    val symptomSeverities = remember(targetSymptoms) {
        mutableStateMapOf<Long, Int>().apply {
            targetSymptoms.forEach { (id, _, initSev) ->
                put(id, (initSev - 3).coerceAtLeast(0))
            }
        }
    }
    var effectNotes by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf<Int?>(4) }

    val initialMax = targetSymptoms.map { it.third }.maxOrNull() ?: (targetEntry.symptomSeverity ?: 0)
    val curMax = symptomSeverities.values.maxOrNull() ?: 0
    val overallDelta = initialMax - curMax

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Evaluasi Respon Intervensi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = "Atur perubahan skala nyeri mandiri untuk tiap keluhan paska intervensi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    fontSize = 11.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. DATE & TIME PICKER
                Surface(
                    color = colors.cardBackground,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.75.dp, colors.cardBorder)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Waktu Evaluasi / Cek Efek:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary
                                )
                                Text(
                                    text = dateFormat.format(Date(evaluationTimestamp)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }

                            TextButton(
                                onClick = {
                                    calendar.timeInMillis = evaluationTimestamp
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
                                                    evaluationTimestamp = calendar.timeInMillis
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
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.EditCalendar, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ubah Waktu", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Quick duration presets
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Jeda paska intervensi:",
                            fontSize = 10.sp,
                            color = colors.textSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val presets = listOf(
                                15 to "+15 mnt",
                                30 to "+30 mnt",
                                60 to "+1 jam",
                                90 to "+1.5 jam",
                                120 to "+2 jam",
                                180 to "+3 jam"
                            )
                            presets.forEach { (mins, label) ->
                                val targetTime = targetEntry.occurrenceTime + (mins * 60 * 1000L)
                                val isSelected = (evaluationTimestamp - targetEntry.occurrenceTime) / (60 * 1000L) == mins.toLong()
                                Surface(
                                    color = if (isSelected) colors.pillActiveBg else colors.pillInactiveBg,
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(0.5.dp, colors.cardBorder),
                                    modifier = Modifier.clickable { evaluationTimestamp = targetTime }
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 9.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) colors.pillActiveText else colors.pillInactiveText,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. MULTI-SYMPTOM EVALUATION SECTION
                Text(
                    text = "Keparahan Nyeri Tiap Gejala (Skala 0 - 10):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )

                // Quick Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = if (colors.isDark) Color(0xFF064E3B) else Color(0xFFECFDF5),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.75.dp, Color(0xFF10B981)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                targetSymptoms.forEach { (id, _, _) -> symptomSeverities[id] = 0 }
                            }
                    ) {
                        Text(
                            text = "✓ Semua Bebas Nyeri (0)",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669),
                            modifier = Modifier.padding(vertical = 5.dp, horizontal = 6.dp)
                        )
                    }

                    Surface(
                        color = colors.pillInactiveBg,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.5.dp, colors.cardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                targetSymptoms.forEach { (id, _, initSev) ->
                                    symptomSeverities[id] = (initSev - 3).coerceAtLeast(0)
                                }
                            }
                    ) {
                        Text(
                            text = "📉 Semua Mereda (-3)",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(vertical = 5.dp, horizontal = 6.dp)
                        )
                    }
                }

                // Individual Slider Cards for Each Symptom
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    targetSymptoms.forEach { (sId, sName, initSev) ->
                        val curSev = symptomSeverities[sId] ?: (initSev - 3).coerceAtLeast(0)
                        val sDelta = initSev - curSev
                        Surface(
                            color = colors.pillInactiveBg,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.5.dp, colors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "⚡", fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = sName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "(Awal: $initSev/10)",
                                            fontSize = 11.sp,
                                            color = colors.textSecondary
                                        )
                                    }

                                    val sDeltaColor = if (sDelta > 0) Color(0xFF059669) else if (sDelta == 0) Color(0xFFD97706) else Color(0xFFDC2626)
                                    val sDeltaBg = if (sDelta > 0) (if (colors.isDark) Color(0xFF064E3B) else Color(0xFFECFDF5))
                                    else if (sDelta == 0) (if (colors.isDark) Color(0xFF78350F) else Color(0xFFFFFBEB))
                                    else (if (colors.isDark) Color(0xFF7F1D1D) else Color(0xFFFEF2F2))

                                    Surface(
                                        color = sDeltaBg,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = if (sDelta > 0) "+$sDelta (Membaik)" else if (sDelta == 0) "0 (Tetap)" else "$sDelta (Memburuk)",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = sDeltaColor,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Slider(
                                        value = curSev.toFloat(),
                                        onValueChange = { symptomSeverities[sId] = it.roundToInt() },
                                        valueRange = 0f..10f,
                                        steps = 9,
                                        modifier = Modifier.weight(1f).testTag("eval_slider_$sId")
                                    )
                                    Surface(
                                        color = getSeverityColor(curSev),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (curSev == 0) "0 (Bebas)" else "$curSev (${getSeverityTierName(curSev)})",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
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
                                        val isSelected = curSev == score
                                        Surface(
                                            color = if (isSelected) (if (score == 0) Color(0xFF059669) else getSeverityColor(score)) else colors.cardBg,
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(
                                                if (isSelected) 1.dp else 0.5.dp,
                                                if (isSelected) Color.Transparent else colors.cardBorder
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { symptomSeverities[sId] = score }
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(vertical = 3.dp)
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
                            }
                        }
                    }
                }

                // Delta indicator banner
                val deltaBg = if (overallDelta >= 2) (if (colors.isDark) Color(0xFF064E3B) else Color(0xFFECFDF5))
                else if (overallDelta < 0) (if (colors.isDark) Color(0xFF7F1D1D) else Color(0xFFFEF2F2))
                else (if (colors.isDark) Color(0xFF78350F) else Color(0xFFFFFBEB))

                val deltaText = if (overallDelta >= 2) (if (colors.isDark) Color(0xFF6EE7B7) else Color(0xFF059669))
                else if (overallDelta < 0) (if (colors.isDark) Color(0xFFFCA5A5) else Color(0xFFDC2626))
                else (if (colors.isDark) Color(0xFFFCD34D) else Color(0xFFD97706))

                Surface(
                    color = deltaBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (overallDelta >= 2) Icons.Default.CheckCircle
                            else if (overallDelta < 0) Icons.Default.Warning
                            else Icons.Default.Info,
                            contentDescription = null,
                            tint = deltaText,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (overallDelta >= 2) "Delta: +$overallDelta (Intervensi Berhasil)"
                            else if (overallDelta < 0) "Delta: $overallDelta (Peringatan: Nyeri Bertambah)"
                            else "Delta: +$overallDelta (Belum Signifikan < 2)",
                            fontWeight = FontWeight.Bold,
                            color = deltaText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Notes
                OutlinedTextField(
                    value = effectNotes,
                    onValueChange = { effectNotes = it },
                    label = { Text("Catatan Perubahan Kondisi") },
                    placeholder = { Text("cth. Nyeri bahu turun, punggung masih kaku...") },
                    modifier = Modifier.fillMaxWidth().testTag("eval_notes_field"),
                    maxLines = 2
                )

                // Mood selector
                Text(
                    text = "Kapasitas Mental Saat Ini:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (m in 1..5) {
                        val isSelected = selectedMood == m
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) colors.pillActiveBg.copy(alpha = 0.2f)
                                    else colors.pillInactiveBg
                                )
                                .clickable { selectedMood = m },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = getMoodEmoji(m), fontSize = 18.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalMap = symptomSeverities.toMap()
                    val maxSev = finalMap.values.maxOrNull() ?: 0
                    onConfirm(maxSev, finalMap, effectNotes, selectedMood, evaluationTimestamp)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("btn_save_evaluation")
            ) {
                Text("Simpan Evaluasi", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

// MODAL DIALOG: TAMBAH INTERVENSI LANJUTAN
@Composable
fun FollowUpInterventionDialog(
    targetEntry: HealthEntryEntity,
    previousSeverity: Int,
    availableInterventions: List<InterventionEntity>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>, Int, String?, Int?, Long) -> Unit
) {
    val colors = LocalHarmonizedColors.current
    val context = LocalContext.current
    var followUpTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")) }

    val selectedInterventionIds = remember { mutableStateListOf<Long>() }
    var newSeverity by remember { mutableStateOf((previousSeverity - 1).coerceAtLeast(0)) }
    var notes by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf(3) }

    val delta = previousSeverity - newSeverity
    val isComplete = newSeverity == 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AddCircleOutline,
                        contentDescription = null,
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Tambah Intervensi Lanjutan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Nyeri sebelumnya: $previousSeverity/10. Pilih intervensi baru dan catat tingkat nyeri terbaru.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    fontSize = 11.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Waktu Catatan
                Surface(
                    color = colors.pillInactiveBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, colors.cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val cal = Calendar.getInstance().apply { timeInMillis = followUpTimestamp }
                            DatePickerDialog(context, { _, y, m, d ->
                                cal.set(Calendar.YEAR, y)
                                cal.set(Calendar.MONTH, m)
                                cal.set(Calendar.DAY_OF_MONTH, d)
                                TimePickerDialog(context, { _, h, min ->
                                    cal.set(Calendar.HOUR_OF_DAY, h)
                                    cal.set(Calendar.MINUTE, min)
                                    followUpTimestamp = cal.timeInMillis
                                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.textMuted)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Waktu Respon:", fontSize = 11.5.sp, color = colors.textSecondary)
                        }
                        Text(dateFormat.format(Date(followUpTimestamp)), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    }
                }

                // Pilih Strategi Intervensi Baru
                Column {
                    Text(
                        text = "Pilih Intervensi Lanjutan / Obat Baru:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (availableInterventions.isEmpty()) {
                        Text("Belum ada master intervensi.", fontSize = 11.sp, color = colors.textMuted)
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableInterventions.forEach { interv ->
                                val isSelected = selectedInterventionIds.contains(interv.id)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (isSelected) selectedInterventionIds.remove(interv.id)
                                        else selectedInterventionIds.add(interv.id)
                                    },
                                    label = { Text(interv.name, fontSize = 11.sp) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(13.dp)) }
                                    } else null
                                )
                            }
                        }
                    }
                }

                // Slider Skor Nyeri Baru
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Skor Nyeri Baru:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Surface(
                            color = if (isComplete) Color(0xFF059669) else getSeverityColor(newSeverity),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (isComplete) "0 (Bebas Nyeri ✓)" else "$newSeverity/10 (${getSeverityTierName(newSeverity)})",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Deretan Tombol Angka Nyeri (0 - 10) untuk pemilihan instan dan presisi
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        (0..10).forEach { score ->
                            val isSelected = newSeverity == score
                            Surface(
                                color = if (isSelected) (if (score == 0) Color(0xFF059669) else getSeverityColor(score)) else colors.pillInactiveBg,
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(
                                    if (isSelected) 1.dp else 0.5.dp,
                                    if (isSelected) Color.Transparent else colors.cardBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { newSeverity = score }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "$score",
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else colors.textPrimary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Slider(
                        value = newSeverity.toFloat(),
                        onValueChange = { newSeverity = it.roundToInt() },
                        valueRange = 0f..10f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth().testTag("followup_severity_slider")
                    )
                }

                // Indicator Delta Penurunan
                val deltaBg = if (isComplete) (if (colors.isDark) Color(0xFF064E3B) else Color(0xFFECFDF5))
                else if (delta > 0) (if (colors.isDark) Color(0xFF0C4A6E) else Color(0xFFE0F2FE))
                else if (delta == 0) (if (colors.isDark) Color(0xFF78350F) else Color(0xFFFFFBEB))
                else (if (colors.isDark) Color(0xFF7F1D1D) else Color(0xFFFEF2F2))

                val deltaText = if (isComplete) Color(0xFF059669)
                else if (delta > 0) (if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7))
                else if (delta == 0) (if (colors.isDark) Color(0xFFFCD34D) else Color(0xFFD97706))
                else (if (colors.isDark) Color(0xFFFCA5A5) else Color(0xFFDC2626))

                Surface(
                    color = deltaBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isComplete) Icons.Default.CheckCircle
                            else if (delta > 0) Icons.Default.TrendingDown
                            else if (delta == 0) Icons.Default.LinearScale
                            else Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = deltaText,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isComplete) "🎉 Respon Selesai: Nyeri tuntas ke 0 (Bebas Nyeri)"
                            else if (delta > 0) "Penurunan Nyeri: $previousSeverity ➔ $newSeverity (Δ +$delta)"
                            else if (delta == 0) "Nyeri tetap di $newSeverity (Belum ada penurunan)"
                            else "Peringatan: Nyeri meningkat (Δ $delta)",
                            fontWeight = FontWeight.Bold,
                            color = deltaText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Catatan Intervensi Lanjutan
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan Intervensi Lanjutan") },
                    placeholder = { Text("cth. Ganti obat kompres hangat, istirahat...") },
                    modifier = Modifier.fillMaxWidth().testTag("followup_notes_field"),
                    maxLines = 2
                )

                // Mood selector
                Text(
                    text = "Kapasitas Mental Saat Ini:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (m in 1..5) {
                        val isSelected = selectedMood == m
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) colors.pillActiveBg.copy(alpha = 0.2f)
                                    else colors.pillInactiveBg
                                )
                                .clickable { selectedMood = m },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = getMoodEmoji(m), fontSize = 17.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(selectedInterventionIds.toList(), newSeverity, notes.ifBlank { null }, selectedMood, followUpTimestamp)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isComplete) Color(0xFF059669) else Color(0xFF0284C7),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("btn_save_followup")
            ) {
                if (isComplete) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("✓ Selesai (Nyeri 0)", fontWeight = FontWeight.Bold)
                } else {
                    Text("Simpan Intervensi Lanjutan", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

fun getSeverityColor(sev: Int): Color = when {
    sev == 0 -> Color(0xFF0D9488)         // Teal (Bebas Nyeri)
    sev in 1..3 -> Color(0xFF10B981)      // Green
    sev in 4..6 -> Color(0xFFF59E0B)      // Amber
    else -> Color(0xFFEF4444)            // Red
}

fun getSeverityTierName(sev: Int): String = when {
    sev == 0 -> "Bebas Nyeri"
    sev in 1..3 -> "Ringan"
    sev in 4..6 -> "Sedang"
    else -> "Parah"
}

fun getMoodEmoji(score: Int?): String = when (score) {
    1 -> "😫"
    2 -> "😟"
    3 -> "😐"
    4 -> "🙂"
    5 -> "😌"
    else -> "😐"
}

fun getMoodLabel(score: Int?): String = when (score) {
    1 -> "Habis Total / Krisis"
    2 -> "Tertekan / Rendah"
    3 -> "Netral / Cukup"
    4 -> "Stabil / Baik"
    5 -> "Tenang & Resilien"
    else -> "Netral"
}
