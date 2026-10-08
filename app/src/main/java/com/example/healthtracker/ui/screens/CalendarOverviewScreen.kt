package com.example.healthtracker.ui.screens

import androidx.compose.animation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthtracker.analytics.AnalyticsEngine
import com.example.healthtracker.model.HealthEntryEntity
import com.example.healthtracker.ui.HealthUiState
import com.example.healthtracker.ui.HealthViewModel
import com.example.ui.theme.LocalHarmonizedColors
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

data class MonthPerformanceData(
    val year: Int,
    val month: Int,
    val label: String,
    val score: Int,
    val avgMood: Double,
    val positiveCount: Int,
    val negativeCount: Int,
    val symptomCount: Int,
    val totalEntries: Int
)

@Composable
fun CalendarOverviewScreen(
    uiState: HealthUiState,
    viewModel: HealthViewModel,
    onNavigateToAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalHarmonizedColors.current

    // Calendar navigation state
    val displayedCalendar = remember {
        mutableStateOf(Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
        })
    }

    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale("id", "ID")) }
    val selectedDateFormat = remember { SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale("id", "ID")) }

    val sympMap = remember(uiState.symptoms) { uiState.symptoms.associateBy { it.id } }
    val intervMap = remember(uiState.interventions) { uiState.interventions.associateBy { it.id } }
    val actMap = remember(uiState.activities) { uiState.activities.associateBy { it.id } }
    val habitMap = remember(uiState.habits) { uiState.habits.associateBy { it.id } }

    // Entries in displayed month
    val currentMonthEntries = remember(uiState.entries, displayedCalendar.value) {
        val cal = displayedCalendar.value
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)

        uiState.entries.filter { entry ->
            val eCal = Calendar.getInstance().apply { timeInMillis = entry.occurrenceTime }
            eCal.get(Calendar.YEAR) == year && eCal.get(Calendar.MONTH) == month
        }
    }

    // Monthly summary stats - 5 detailed emotional levels
    val moodCounts = remember(currentMonthEntries) {
        (1..5).associateWith { m -> currentMonthEntries.count { it.moodScore == m } }
    }
    val positiveMoodCount = remember(moodCounts) { (moodCounts[4] ?: 0) + (moodCounts[5] ?: 0) }
    val negativeMoodCount = remember(moodCounts) { (moodCounts[1] ?: 0) + (moodCounts[2] ?: 0) }
    val totalMoodCount = remember(moodCounts) { moodCounts.values.sum() }

    val symptomEntries = remember(currentMonthEntries) {
        currentMonthEntries.filter { it.symptomId != null && (it.symptomSeverity ?: 0) > 0 }
    }
    val topSymptoms = remember(symptomEntries, sympMap) {
        symptomEntries.groupBy { it.symptomId }
            .mapNotNull { (id, list) ->
                id?.let { (sympMap[it]?.name ?: "Gejala") to list.size }
            }
            .sortedByDescending { it.second }
    }

    // All months performance calculation (for best, worst, and monthly delta %)
    val allMonthsPerformance = remember(uiState.entries) {
        val cal = Calendar.getInstance()
        val format = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))
        val grouped = uiState.entries.groupBy { entry ->
            cal.timeInMillis = entry.occurrenceTime
            cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH)
        }

        grouped.map { (key, entries) ->
            val (year, month) = key
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val label = format.format(cal.time).replaceFirstChar { it.uppercase() }

            val moods = entries.mapNotNull { it.moodScore }
            val avgMood = if (moods.isNotEmpty()) moods.average() else 3.0
            val posCount = entries.count { (it.moodScore ?: 0) >= 4 }
            val negCount = entries.count { (it.moodScore ?: 0) in 1..2 }
            val sympList = entries.filter { it.symptomId != null && (it.symptomSeverity ?: 0) > 0 }
            val sympCount = sympList.size
            val avgPain = if (sympList.isNotEmpty()) sympList.map { it.symptomSeverity ?: 0 }.average() else 0.0

            // Formula: Mood gives up to 70%, Freedom from symptoms gives up to 30%
            val moodScorePart = ((avgMood / 5.0) * 70).roundToInt()
            val painDeduction = (sympCount * 2.5 + avgPain * 1.5).coerceAtMost(30.0)
            val painScorePart = (30 - painDeduction).coerceIn(0.0, 30.0).roundToInt()
            val totalScore = (moodScorePart + painScorePart).coerceIn(10, 100)

            MonthPerformanceData(
                year = year,
                month = month,
                label = label,
                score = totalScore,
                avgMood = (avgMood * 10).roundToInt() / 10.0,
                positiveCount = posCount,
                negativeCount = negCount,
                symptomCount = sympCount,
                totalEntries = entries.size
            )
        }
    }

    val bestMonth = remember(allMonthsPerformance) {
        allMonthsPerformance.maxByOrNull { it.score }
    }
    val worstMonth = remember(allMonthsPerformance) {
        if (allMonthsPerformance.size > 1) {
            allMonthsPerformance.minByOrNull { it.score }
        } else null
    }

    val currentMonthPerf = remember(allMonthsPerformance, displayedCalendar.value) {
        val curCal = displayedCalendar.value
        allMonthsPerformance.find { it.year == curCal.get(Calendar.YEAR) && it.month == curCal.get(Calendar.MONTH) }
    }

    val prevMonthPerf = remember(allMonthsPerformance, displayedCalendar.value) {
        val prevCal = (displayedCalendar.value.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
        allMonthsPerformance.find { it.year == prevCal.get(Calendar.YEAR) && it.month == prevCal.get(Calendar.MONTH) }
    }

    // Entries for selected date
    val selectedDateEntries = remember(uiState.entries, selectedDateMillis) {
        val selCal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        uiState.entries.filter { entry ->
            val eCal = Calendar.getInstance().apply { timeInMillis = entry.occurrenceTime }
            eCal.get(Calendar.YEAR) == selCal.get(Calendar.YEAR) &&
            eCal.get(Calendar.DAY_OF_YEAR) == selCal.get(Calendar.DAY_OF_YEAR)
        }.sortedByDescending { it.occurrenceTime }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp)
    ) {
        // 1. MONTH NAVIGATION HEADER
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = displayedCalendar.value.timeInMillis
                        add(Calendar.MONTH, -1)
                    }
                    displayedCalendar.value = cal
                }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Bulan Sebelumnya")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = monthFormat.format(displayedCalendar.value.time).replaceFirstChar { it.uppercase() },
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = colors.pillInactiveBg,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.clickable {
                            val today = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
                            displayedCalendar.value = today
                            selectedDateMillis = System.currentTimeMillis()
                        }
                    ) {
                        Text(
                            text = "Hari Ini",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.pillInactiveText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                IconButton(onClick = {
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = displayedCalendar.value.timeInMillis
                        add(Calendar.MONTH, 1)
                    }
                    displayedCalendar.value = cal
                }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Bulan Berikutnya")
                }
            }
        }

        // 2. MONTHLY SUMMARY BAR (5 DETAIL EMOSI & GEJALA FISIK)
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                border = BorderStroke(0.75.dp, colors.cardBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Distribusi 5 Spektrum Mental Bulan Ini:",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        if (totalMoodCount > 0) {
                            Text(
                                text = "$totalMoodCount catatan mood",
                                fontSize = 10.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // 5 Detail Emosi Cards
                    val moodLevels = listOf(
                        Triple(1, "😫", "Krisis"),
                        Triple(2, "😟", "Rendah"),
                        Triple(3, "😐", "Netral"),
                        Triple(4, "🙂", "Baik"),
                        Triple(5, "😌", "Tenang")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        moodLevels.forEach { (score, emoji, label) ->
                            val count = moodCounts[score] ?: 0
                            val mColor = getMoodColor(score)
                            Surface(
                                color = if (colors.isDark) mColor.copy(alpha = 0.18f) else mColor.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(0.75.dp, mColor.copy(alpha = if (count > 0) 0.6f else 0.25f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(text = emoji, fontSize = 13.sp)
                                    Text(
                                        text = label,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = mColor,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "$count",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (count > 0) (if (colors.isDark) Color.White else colors.textPrimary) else colors.textSecondary.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }

                    // Segmented Color Distribution Bar
                    if (totalMoodCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        ) {
                            moodLevels.forEach { (score, _, _) ->
                                val count = moodCounts[score] ?: 0
                                if (count > 0) {
                                    val weight = count.toFloat() / totalMoodCount.toFloat()
                                    Box(
                                        modifier = Modifier
                                            .weight(weight)
                                            .fillMaxHeight()
                                            .background(getMoodColor(score))
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Gejala Fisik Summary Row
                    Surface(
                        color = colors.pillInactiveBg,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.5.dp, colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "⚠️", fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Keluhan Gejala Fisik:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary
                                )
                            }
                            Text(
                                text = "${symptomEntries.size} kejadian",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (symptomEntries.isNotEmpty()) Color(0xFFEF4444) else colors.textSecondary
                            )
                        }
                    }

                    if (topSymptoms.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = "Fokus Gejala:", fontSize = 10.5.sp, color = colors.textSecondary)
                            topSymptoms.take(3).forEach { (name, count) ->
                                Surface(
                                    color = if (colors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.5.dp, colors.cardBorder)
                                ) {
                                    Text(
                                        text = "$name: ${count}x",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.textPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2B. PERFORMA & KOMPARASI BULANAN (BULAN TERBAIK, BULAN TERBURUK, TREN % BULAN INI)
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                border = BorderStroke(0.75.dp, colors.cardBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "📊 ", fontSize = 14.sp)
                            Text(
                                text = "Komparasi & Tren Bulanan",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }

                        if (currentMonthPerf != null) {
                            Surface(
                                color = colors.pillInactiveBg,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Skor: ${currentMonthPerf.score}%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.pillInactiveText,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 1. TREN BULAN INI VS BULAN LALU (PERCENTAGE DELTA)
                    val comparisonDelta = remember(currentMonthPerf, prevMonthPerf) {
                        if (currentMonthPerf != null && prevMonthPerf != null && prevMonthPerf.score > 0) {
                            val diff = currentMonthPerf.score - prevMonthPerf.score
                            val pct = ((abs(diff).toDouble() / prevMonthPerf.score) * 100).roundToInt()
                            diff to pct
                        } else null
                    }

                    val trendBg = when {
                        comparisonDelta == null -> colors.pillInactiveBg
                        comparisonDelta.first > 0 -> if (colors.isDark) Color(0xFF064E3B) else Color(0xFFECFDF5)
                        comparisonDelta.first < 0 -> if (colors.isDark) Color(0xFF7F1D1D) else Color(0xFFFEF2F2)
                        else -> colors.pillInactiveBg
                    }

                    val trendText = when {
                        comparisonDelta == null -> if (prevMonthPerf == null && currentMonthPerf != null) {
                            "✨ Bulan awal pencatatan (Belum ada pembanding bulan lalu)"
                        } else {
                            "Belum ada catatan kesehatan di bulan ini"
                        }
                        comparisonDelta.first > 0 -> "🚀 Bulan ini ${comparisonDelta.second}% Jauh Lebih Baik dari bulan lalu!"
                        comparisonDelta.first < 0 -> "📉 Bulan ini ${comparisonDelta.second}% Lebih Rendah dari bulan lalu"
                        else -> "⚖️ Kondisi stabil sama dengan bulan lalu (0% perubahan)"
                    }

                    val trendTextColor = when {
                        comparisonDelta == null -> colors.textSecondary
                        comparisonDelta.first > 0 -> Color(0xFF059669)
                        comparisonDelta.first < 0 -> Color(0xFFDC2626)
                        else -> colors.textPrimary
                    }

                    Surface(
                        color = trendBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = trendText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = trendTextColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. GRID BULAN TERBAIK & BULAN TERBURUK
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Bulan Terbaik
                        Surface(
                            color = if (colors.isDark) Color(0xFF1F1A0A) else Color(0xFFFFFBEB),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.75.dp, Color(0xFFF59E0B)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "🌟 ", fontSize = 11.sp)
                                    Text(
                                        text = "Bulan Terbaik",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD97706)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = bestMonth?.label ?: "-",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = if (bestMonth != null) "Skor: ${bestMonth.score}% • Mood: ${bestMonth.avgMood}/5" else "Belum cukup data",
                                    fontSize = 9.5.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        // Bulan Terburuk
                        Surface(
                            color = if (colors.isDark) Color(0xFF261818) else Color(0xFFFEF2F2),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.75.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "🌧️ ", fontSize = 11.sp)
                                    Text(
                                        text = "Bulan Terburuk",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFDC2626)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = worstMonth?.label ?: (if (allMonthsPerformance.isNotEmpty()) "Belum ada variasi" else "-"),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = if (worstMonth != null) "Skor: ${worstMonth.score}% • ${worstMonth.symptomCount}x Nyeri" else "Semua bulan stabil",
                                    fontSize = 9.5.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. CALENDAR MONTH GRID
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                border = BorderStroke(0.75.dp, colors.cardBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Day of week headers
                    val dayNames = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
                    Row(modifier = Modifier.fillMaxWidth()) {
                        dayNames.forEach { dayName ->
                            Text(
                                text = dayName,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Compute grid days
                    val monthCal = Calendar.getInstance().apply {
                        timeInMillis = displayedCalendar.value.timeInMillis
                        set(Calendar.DAY_OF_MONTH, 1)
                    }
                    val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    // In Java Calendar, SUNDAY=1, MONDAY=2, ... SATURDAY=7
                    val firstDayOfWeek = (monthCal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Convert to Monday=0 .. Sunday=6

                    val totalCells = ((firstDayOfWeek + daysInMonth + 6) / 7) * 7

                    val todayCal = Calendar.getInstance()
                    val selectedCal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }

                    val rows = totalCells / 7
                    for (r in 0 until rows) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (c in 0 until 7) {
                                val cellIndex = r * 7 + c
                                val dayNumber = cellIndex - firstDayOfWeek + 1

                                if (dayNumber in 1..daysInMonth) {
                                    val cellDateCal = Calendar.getInstance().apply {
                                        timeInMillis = monthCal.timeInMillis
                                        set(Calendar.DAY_OF_MONTH, dayNumber)
                                    }

                                    val isSelected = cellDateCal.get(Calendar.YEAR) == selectedCal.get(Calendar.YEAR) &&
                                            cellDateCal.get(Calendar.DAY_OF_YEAR) == selectedCal.get(Calendar.DAY_OF_YEAR)

                                    val isToday = cellDateCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                                            cellDateCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)

                                    // Check day's logs
                                    val dayEntries = currentMonthEntries.filter { entry ->
                                        val eCal = Calendar.getInstance().apply { timeInMillis = entry.occurrenceTime }
                                        eCal.get(Calendar.DAY_OF_MONTH) == dayNumber
                                    }

                                    val dayMoods = dayEntries.mapNotNull { it.moodScore }.distinct().sorted()
                                    val hasSymptoms = dayEntries.any { it.symptomId != null && (it.symptomSeverity ?: 0) > 0 }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .padding(2.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) colors.pillActiveBg
                                                else if (isToday) colors.pillInactiveBg
                                                else Color.Transparent
                                            )
                                            .clickable {
                                                selectedDateMillis = cellDateCal.timeInMillis
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "$dayNumber",
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) colors.pillActiveText else colors.textPrimary
                                            )

                                            // Mini Indicator Dots for 5 Emotions & Symptoms
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                dayMoods.forEach { score ->
                                                    Box(
                                                        modifier = Modifier
                                                            .size(4.dp)
                                                            .clip(CircleShape)
                                                            .background(if (isSelected) Color.White else getMoodColor(score))
                                                    )
                                                }
                                                if (hasSymptoms) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(4.dp)
                                                            .clip(CircleShape)
                                                            .background(if (isSelected) Color.White else Color(0xFFEF4444))
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // Legenda 5 Emosi & Gejala Fisik
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = colors.pillInactiveBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text(
                                text = "Keterangan Warna Emosi & Gejala:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(
                                    1 to "😫 Krisis",
                                    2 to "😟 Rendah",
                                    3 to "😐 Netral",
                                    4 to "🙂 Baik",
                                    5 to "😌 Tenang"
                                ).forEach { (score, label) ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(getMoodColor(score))
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(text = label, fontSize = 9.sp, color = colors.textSecondary)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFEF4444))
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(text = "⚡ Gejala", fontSize = 9.sp, color = colors.textSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. ENTRIES ON SELECTED DATE (BELOW CALENDAR)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Riwayat ${selectedDateFormat.format(Date(selectedDateMillis))}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "${selectedDateEntries.size} entri tercatat",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }

                Button(
                    onClick = onNavigateToAdd,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.pillActiveBg,
                        contentColor = colors.pillActiveText
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Catat Hari Ini", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (selectedDateEntries.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tidak ada catatan atau keluhan pada tanggal ini.",
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(selectedDateEntries, key = { it.id }) { entry ->
                CalendarEntryMiniCard(
                    entry = entry,
                    sympName = entry.symptomId?.let { sympMap[it]?.name },
                    intervNames = AnalyticsEngine.parseIdList(entry.interventionIdsJson).mapNotNull { intervMap[it]?.name },
                    actNames = AnalyticsEngine.parseIdList(entry.activityIdsJson).mapNotNull { actMap[it]?.name },
                    habits = AnalyticsEngine.parseIdList(entry.habitIdsJson).mapNotNull { habitMap[it] }
                )
            }
        }
    }
}

@Composable
fun CalendarEntryMiniCard(
    entry: HealthEntryEntity,
    sympName: String?,
    intervNames: List<String>,
    actNames: List<String>,
    habits: List<com.example.healthtracker.model.HabitEntity>
) {
    val colors = LocalHarmonizedColors.current
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale("id", "ID")) }
    val timeStr = timeFormat.format(Date(entry.occurrenceTime))

    val isEval = entry.isEvaluationPhase
    val isRoutine = !isEval && entry.symptomId == null && (entry.symptomSeverity == null || entry.symptomSeverity == 0) && (entry.interventionIdsJson == "[]" || entry.interventionIdsJson.isBlank())

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        border = BorderStroke(0.75.dp, colors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = when {
                            isEval -> if (colors.isDark) Color(0xFF14532D) else Color(0xFFF0FDF4)
                            isRoutine -> if (colors.isDark) Color(0xFF0C4A6E) else Color(0xFFE0F2FE)
                            else -> colors.pillInactiveBg
                        },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = when {
                                isEval -> "EVALUASI"
                                isRoutine -> "RUTINITAS"
                                else -> "PEMICU"
                            },
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isEval -> if (colors.isDark) Color(0xFF86EFAC) else Color(0xFF15803D)
                                isRoutine -> if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                                else -> colors.pillInactiveText
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = timeStr, fontSize = 11.sp, color = colors.textMuted)
                }

                if (entry.moodScore != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = getMoodEmoji(entry.moodScore), fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = getMoodLabel(entry.moodScore), fontSize = 11.sp, color = colors.textSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (isEval) {
                Text(
                    text = "${sympName ?: "Gejala"} • Nyeri Akhir: ${entry.finalSeverity ?: 0}/10 (Delta: ${if ((entry.painDelta ?: 0) >= 0) "+${entry.painDelta}" else "${entry.painDelta}"})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
            } else if (isRoutine) {
                if (actNames.isNotEmpty()) {
                    Text(
                        text = "Aktivitas: ${actNames.joinToString(", ")}",
                        fontSize = 11.5.sp,
                        color = colors.textPrimary
                    )
                }
            } else {
                Text(
                    text = "${sympName ?: "Gejala"} (Nyeri: ${entry.symptomSeverity ?: 0}/10)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                if (intervNames.isNotEmpty()) {
                    Text(
                        text = "Obat: ${intervNames.joinToString(", ")}",
                        fontSize = 11.sp,
                        color = if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                    )
                }
            }

            // Habits if any
            if (habits.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    habits.forEach { h ->
                        Surface(
                            color = colors.pillInactiveBg,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "${h.iconEmoji} ${h.name}",
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // Notes preview if any
            val note = entry.effectNotes ?: entry.symptomNotes ?: entry.activityNotes ?: entry.moodNotes
            if (!note.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "💬 $note",
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

fun getMoodColor(score: Int): Color = when (score) {
    1 -> Color(0xFFDC2626) // Krisis (Merah)
    2 -> Color(0xFFF97316) // Rendah (Oranye)
    3 -> Color(0xFFEAB308) // Netral (Kuning-Amber)
    4 -> Color(0xFF10B981) // Baik (Hijau)
    5 -> Color(0xFF06B6D4) // Tenang (Cyan/Teal)
    else -> Color(0xFF9CA3AF)
}

