package com.example.healthtracker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthtracker.analytics.AnalyticsEngine
import com.example.healthtracker.model.HabitEntity
import com.example.healthtracker.model.HealthEntryEntity
import com.example.healthtracker.ui.HealthUiState
import com.example.healthtracker.ui.HealthViewModel
import com.example.ui.theme.LocalHarmonizedColors
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

data class HabitStreakStats(
    val habit: HabitEntity,
    val totalCompletions: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val brokenStreaks: Int,
    val last7DaysStatus: List<Boolean> // Sun to Sat or last 7 days
)

@Composable
fun HabitAnalyticsScreen(
    uiState: HealthUiState,
    viewModel: HealthViewModel,
    modifier: Modifier = Modifier
) {
    val colors = LocalHarmonizedColors.current
    var showAddHabitDialog by remember { mutableStateOf(false) }
    var habitToDelete by remember { mutableStateOf<HabitEntity?>(null) }
    var habitToEdit by remember { mutableStateOf<HabitEntity?>(null) }

    // Calculate streak statistics for each habit
    val streakStatsList = remember(uiState.habits, uiState.entries) {
        calculateHabitStreaks(uiState.habits, uiState.entries)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp)
    ) {
        // 1. PAGE TITLE & TOP ACTION
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Statistik & Pelacak Habit",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Pantau konsistensi rutinitas harian & rantai streak",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        fontSize = 11.5.sp
                    )
                }

                Button(
                    onClick = { showAddHabitDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.pillActiveBg,
                        contentColor = colors.pillActiveText
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("btn_add_habit_top")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tambah", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. OVERVIEW SUMMARY CARDS
        item {
            val totalHabits = uiState.habits.size
            val maxCurrentStreak = streakStatsList.maxOfOrNull { it.currentStreak } ?: 0
            val bestHabit = streakStatsList.maxByOrNull { it.currentStreak }?.habit?.name ?: "-"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Card 1: Total Habit
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                    border = BorderStroke(0.75.dp, colors.cardBorder)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "Total Habit", fontSize = 10.5.sp, color = colors.textSecondary)
                        Text(
                            text = "$totalHabits",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                }

                // Card 2: Streak Beruntun Tertinggi
                Card(
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                    border = BorderStroke(0.75.dp, colors.cardBorder)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "Streak Tertinggi", fontSize = 10.5.sp, color = colors.textSecondary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🔥 ", fontSize = 14.sp)
                            Text(
                                text = "$maxCurrentStreak Hari",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF59E0B)
                            )
                        }
                    }
                }

                // Card 3: Paling Konsisten
                Card(
                    modifier = Modifier.weight(1.4f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                    border = BorderStroke(0.75.dp, colors.cardBorder)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "Paling Konsisten", fontSize = 10.5.sp, color = colors.textSecondary)
                        Text(
                            text = bestHabit,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (streakStatsList.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "🔥", fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Belum Ada Habit Terdaftar",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tambahkan habit seperti minum air, peregangan, atau latihan relaksasi untuk melacak konsistensimu!",
                                fontSize = 11.5.sp,
                                color = colors.textSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { showAddHabitDialog = true }) {
                                Text("Tambah Habit Pertama")
                            }
                        }
                    }
                }
            }
        } else {
            items(streakStatsList, key = { it.habit.id }) { stats ->
                HabitCardItem(
                    stats = stats,
                    onEdit = { habitToEdit = stats.habit },
                    onDelete = { habitToDelete = stats.habit }
                )
            }
        }
    }

    // Edit Habit Dialog (Request 3)
    if (habitToEdit != null) {
        val habit = habitToEdit!!
        var name by remember(habit) { mutableStateOf(habit.name) }
        var emoji by remember(habit) { mutableStateOf(habit.iconEmoji) }
        var targetDays by remember(habit) { mutableStateOf(habit.targetDaysPerWeek.toString()) }

        AlertDialog(
            onDismissRequest = { habitToEdit = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = colors.pillActiveBg)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Habit Master", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama Habit (cth: Baca Buku 30 Mnt)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it },
                        label = { Text("Emoji / Icon (cth: 📖, 💧, 🏃, 😴, 🧘)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = targetDays,
                        onValueChange = { if (it.all { c -> c.isDigit() } && (it.toIntOrNull() ?: 0) <= 7) targetDays = it },
                        label = { Text("Target Hari per Minggu (1 - 7)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val days = targetDays.toIntOrNull()?.coerceIn(1, 7) ?: 7
                        viewModel.updateHabit(
                            habit.copy(
                                name = name.trim().ifBlank { habit.name },
                                iconEmoji = emoji.trim().ifBlank { habit.iconEmoji },
                                targetDaysPerWeek = days
                            )
                        )
                        viewModel.showStatus("Habit '${name.trim()}' berhasil diperbarui.")
                        habitToEdit = null
                    },
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.pillActiveBg,
                        contentColor = colors.pillActiveText
                    )
                ) {
                    Text("Simpan Perubahan", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { habitToEdit = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // Quick Add Habit Dialog
    if (showAddHabitDialog) {
        QuickAddDialog(
            title = "Tambah Habit Baru",
            extraLabel = "Icon Emoji (cth: 🧘, 💧, 🚶, 😴, ✨)",
            onDismiss = { showAddHabitDialog = false },
            onConfirm = { name, emoji ->
                viewModel.addHabit(name, if (emoji.isBlank()) "✨" else emoji)
                showAddHabitDialog = false
            }
        )
    }

    // Confirm Delete Dialog
    if (habitToDelete != null) {
        AlertDialog(
            onDismissRequest = { habitToDelete = null },
            title = { Text("Hapus Habit?") },
            text = { Text("Habit '${habitToDelete?.name}' akan dihapus dari daftar master.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        habitToDelete?.let { viewModel.deleteHabit(it) }
                        habitToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { habitToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun HabitCardItem(
    stats: HabitStreakStats,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalHarmonizedColors.current
    val habit = stats.habit

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        border = BorderStroke(0.75.dp, colors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Emoji, Name, Current Streak badge, Edit & Delete buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = habit.iconEmoji, fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = habit.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Target: ${habit.targetDaysPerWeek} hari / minggu",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (stats.currentStreak > 0) {
                            if (colors.isDark) Color(0xFF78350F) else Color(0xFFFFFBEB)
                        } else colors.pillInactiveBg,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            0.5.dp,
                            if (stats.currentStreak > 0) Color(0xFFF59E0B) else colors.cardBorder
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(text = "🔥 ", fontSize = 11.sp)
                            Text(
                                text = "${stats.currentStreak} Hari",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (stats.currentStreak > 0) Color(0xFFD97706) else colors.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Edit Habit Button
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    // Delete Habit Button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Hapus",
                            tint = colors.textMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3-Metric Stats (Requested by user: Beruntun, Putus Streak, Total Seluruhnya)
            Surface(
                color = colors.pillInactiveBg,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.5.dp, colors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Metric 1: Streak Terpanjang
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🏆 Rekor Streak", fontSize = 10.sp, color = colors.textSecondary)
                        Text(
                            text = "${stats.longestStreak} Hari",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(colors.cardBorder)
                    )

                    // Metric 2: Putus Streak
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "💔 Putus Streak", fontSize = 10.sp, color = colors.textSecondary)
                        Text(
                            text = "${stats.brokenStreaks} Kali",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (stats.brokenStreaks > 0) Color(0xFFEF4444) else colors.textPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(colors.cardBorder)
                    )

                    // Metric 3: Total Selesai
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "⭐ Total Selesai", fontSize = 10.sp, color = colors.textSecondary)
                        Text(
                            text = "${stats.totalCompletions} Hari",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Last 7 Days Mini Indicator
            Text(
                text = "Status 7 Hari Terakhir:",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val dayLabels = listOf("H-6", "H-5", "H-4", "H-3", "H-2", "Kemarin", "Hari Ini")
                stats.last7DaysStatus.forEachIndexed { i, completed ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    if (completed) Color(0xFF10B981)
                                    else colors.pillInactiveBg
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (completed) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(colors.textMuted)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = dayLabels.getOrElse(i) { "" },
                            fontSize = 8.5.sp,
                            color = colors.textSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Calculates current streak, longest streak, broken streaks, and total completions
 */
fun calculateHabitStreaks(
    habits: List<HabitEntity>,
    entries: List<HealthEntryEntity>
): List<HabitStreakStats> {
    val results = mutableListOf<HabitStreakStats>()
    val todayCal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val todayEpochDays = TimeUnit.MILLISECONDS.toDays(todayCal.timeInMillis)

    for (habit in habits) {
        // Collect all distinct days this habit was completed
        val completedDays = entries.filter { entry ->
            val ids = AnalyticsEngine.parseIdList(entry.habitIdsJson)
            habit.id in ids
        }.map { entry ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = entry.occurrenceTime
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            TimeUnit.MILLISECONDS.toDays(cal.timeInMillis)
        }.distinct().sorted()

        val totalCompletions = completedDays.size

        // Calculate streaks
        var currentStreak = 0
        var longestStreak = 0
        var brokenStreaks = 0

        if (completedDays.isNotEmpty()) {
            var tempStreak = 1
            for (i in 1 until completedDays.size) {
                if (completedDays[i] == completedDays[i - 1] + 1) {
                    tempStreak++
                } else {
                    longestStreak = maxOf(longestStreak, tempStreak)
                    if (tempStreak >= 2) {
                        brokenStreaks++
                    }
                    tempStreak = 1
                }
            }
            longestStreak = maxOf(longestStreak, tempStreak)

            // Current streak calculation (must touch today or yesterday)
            val lastDay = completedDays.last()
            if (lastDay == todayEpochDays || lastDay == todayEpochDays - 1) {
                var c = 1
                for (i in completedDays.size - 2 downTo 0) {
                    if (completedDays[i + 1] - completedDays[i] == 1L) {
                        c++
                    } else break
                }
                currentStreak = c
            }
        }

        // Calculate last 7 days status (from 6 days ago up to today)
        val last7Days = (6 downTo 0).map { offset ->
            val day = todayEpochDays - offset
            day in completedDays
        }

        results.add(
            HabitStreakStats(
                habit = habit,
                totalCompletions = totalCompletions,
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                brokenStreaks = brokenStreaks,
                last7DaysStatus = last7Days
            )
        )
    }

    return results
}
