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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthtracker.analytics.AnalyticsEngine
import com.example.healthtracker.model.ActivityEntity
import com.example.healthtracker.model.HealthEntryEntity
import com.example.healthtracker.model.SymptomEntity
import com.example.healthtracker.ui.HealthUiState
import com.example.healthtracker.ui.HealthViewModel
import com.example.ui.theme.LocalHarmonizedColors
import java.util.*
import java.util.concurrent.TimeUnit

enum class ActivityComparePeriod(val title: String, val currName: String, val prevName: String) {
    DAY("Hari", "Hari Ini", "Kemarin"),
    WEEK("Minggu", "Minggu Ini", "Minggu Lalu"),
    MONTH("Bulan", "Bulan Ini", "Bulan Lalu"),
    YEAR("Tahun", "Tahun Ini", "Tahun Lalu")
}

data class ActivitySymptomBreakdown(
    val symptomId: Long,
    val symptomName: String,
    val count: Int,
    val percentage: Int,
    val color: Color
)

data class ActivityPeriodStats(
    val activity: ActivityEntity,
    val totalCount: Int,
    val safeCount: Int,
    val safePercentage: Int,
    val symptomBreakdowns: List<ActivitySymptomBreakdown>,
    val prevTotalCount: Int,
    val prevSafePercentage: Int,
    val deltaSafePercentage: Int // current safe% - previous safe%
)

@Composable
fun TriggerAnalyticsScreen(
    uiState: HealthUiState,
    viewModel: HealthViewModel,
    modifier: Modifier = Modifier
) {
    val colors = LocalHarmonizedColors.current
    var selectedPeriod by remember { mutableStateOf(ActivityComparePeriod.MONTH) }

    val sympMap = remember(uiState.symptoms) { uiState.symptoms.associateBy { it.id } }

    // Calculate activity correlation statistics per user's custom formula
    val activityStats = remember(uiState.activities, uiState.entries, uiState.symptoms, selectedPeriod) {
        calculateActivityComparisons(
            activities = uiState.activities,
            entries = uiState.entries,
            symptoms = uiState.symptoms,
            period = selectedPeriod,
            isDark = colors.isDark
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp)
    ) {
        // 1. PAGE HEADER
        item {
            Column {
                Text(
                    text = "Korelasi Aktivitas terhadap Gejala",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    fontSize = 18.sp
                )
                Text(
                    text = "Perbandingan frekuensi aman vs memicu gejala antar periode waktu",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    fontSize = 11.5.sp
                )
            }
        }

        // 2. PERIOD COMPARISON SELECTOR: [Hari] [Minggu] [Bulan] [Tahun]
        item {
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
                    ActivityComparePeriod.values().forEach { period ->
                        val isSelected = selectedPeriod == period
                        Surface(
                            color = if (isSelected) colors.cardBackground else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            shadowElevation = if (isSelected) 1.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedPeriod = period }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = period.title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) colors.textPrimary else colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. PERIOD DESCRIPTION & OVERVIEW
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                border = BorderStroke(0.75.dp, colors.cardBorder)
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
                            text = "Membandingkan:",
                            fontSize = 10.5.sp,
                            color = colors.textSecondary
                        )
                        Text(
                            text = "${selectedPeriod.currName} vs ${selectedPeriod.prevName}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = colors.textPrimary
                        )
                    }

                    val activeActivitiesCount = activityStats.count { it.totalCount > 0 }
                    Surface(
                        color = colors.pillInactiveBg,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "$activeActivitiesCount Aktivitas Aktif",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.pillInactiveText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // 4. ACTIVITY BREAKDOWN CARDS
        if (activityStats.isEmpty() || activityStats.none { it.totalCount > 0 || it.prevTotalCount > 0 }) {
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
                            Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Belum Ada Aktivitas di Periode Ini",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Catat rutinitas harian atau pemicu gejala untuk melihat analisis korelasi aktivitas.",
                                fontSize = 11.5.sp,
                                color = colors.textSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            items(activityStats.filter { it.totalCount > 0 || it.prevTotalCount > 0 }, key = { it.activity.id }) { stats ->
                ActivityCorrelationCard(stats = stats, period = selectedPeriod)
            }
        }
    }
}

@Composable
fun ActivityCorrelationCard(
    stats: ActivityPeriodStats,
    period: ActivityComparePeriod
) {
    val colors = LocalHarmonizedColors.current

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        border = BorderStroke(0.75.dp, colors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Activity Name, Category, and Comparison Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stats.activity.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Kategori: ${stats.activity.category} • ${stats.totalCount} entri di ${period.currName}",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }

                // Trend Comparison Badge
                if (stats.prevTotalCount == 0) {
                    Surface(
                        color = colors.pillInactiveBg,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Baru",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    val delta = stats.deltaSafePercentage
                    val isImprovement = delta > 0
                    val isWorse = delta < 0
                    val badgeBg = when {
                        isImprovement -> if (colors.isDark) Color(0xFF064E3B) else Color(0xFFECFDF5)
                        isWorse -> if (colors.isDark) Color(0xFF7F1D1D) else Color(0xFFFEF2F2)
                        else -> colors.pillInactiveBg
                    }
                    val badgeColor = when {
                        isImprovement -> Color(0xFF059669)
                        isWorse -> Color(0xFFDC2626)
                        else -> colors.textSecondary
                    }

                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.5.dp, badgeColor.copy(alpha = 0.5f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    isImprovement -> Icons.Default.TrendingUp
                                    isWorse -> Icons.Default.TrendingDown
                                    else -> Icons.Default.LinearScale
                                },
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = when {
                                    isImprovement -> "+${delta}% Lebih Aman"
                                    isWorse -> "${delta}% Lebih Berisiko"
                                    else -> "Stabil"
                                },
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Multi-segment Visual Progress Bar (Aman vs Gejala-Gejala)
            if (stats.totalCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.pillInactiveBg)
                ) {
                    // Safe / Rutinitas segment (Green)
                    if (stats.safePercentage > 0) {
                        Box(
                            modifier = Modifier
                                .weight(stats.safePercentage.toFloat().coerceAtLeast(0.1f))
                                .fillMaxHeight()
                                .background(Color(0xFF10B981))
                        )
                    }

                    // Symptom segments
                    stats.symptomBreakdowns.forEach { breakdown ->
                        if (breakdown.percentage > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(breakdown.percentage.toFloat().coerceAtLeast(0.1f))
                                    .fillMaxHeight()
                                    .background(breakdown.color)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Breakdown legend table
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // 1. Aman / Rutinitas
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Aman / Rutinitas (Bebas Gejala)",
                                fontSize = 11.sp,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "${stats.safeCount}x (${stats.safePercentage}%)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }

                    // 2. Symptoms breakdowns
                    stats.symptomBreakdowns.forEach { b ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(b.color)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Memicu: ${b.symptomName}",
                                    fontSize = 11.sp,
                                    color = colors.textPrimary
                                )
                            }
                            Text(
                                text = "${b.count}x (${b.percentage}%)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = b.color
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Tidak ada entri untuk aktivitas ini di ${period.currName}.",
                    fontSize = 11.sp,
                    color = colors.textMuted
                )
            }

            // Previous Period Note
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = colors.cardBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pembanding (${period.prevName}):",
                    fontSize = 10.5.sp,
                    color = colors.textSecondary
                )
                Text(
                    text = if (stats.prevTotalCount > 0)
                        "${stats.prevTotalCount}x log (${stats.prevSafePercentage}% Aman)"
                    else "Tidak ada data di ${period.prevName}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary
                )
            }
        }
    }
}

/**
 * Calculates custom period comparisons (Day, Week, Month, Year) per user's specification
 */
fun calculateActivityComparisons(
    activities: List<ActivityEntity>,
    entries: List<HealthEntryEntity>,
    symptoms: List<SymptomEntity>,
    period: ActivityComparePeriod,
    isDark: Boolean
): List<ActivityPeriodStats> {
    val sympMap = symptoms.associateBy { it.id }

    // Determine current and previous time boundaries
    val now = System.currentTimeMillis()
    val currCal = Calendar.getInstance().apply { timeInMillis = now }

    val (currStart, currEnd, prevStart, prevEnd) = when (period) {
        ActivityComparePeriod.DAY -> {
            val cStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val cEnd = cStart + 24 * 3600 * 1000L
            val pStart = cStart - 24 * 3600 * 1000L
            val pEnd = cStart
            Tuple4(cStart, cEnd, pStart, pEnd)
        }
        ActivityComparePeriod.WEEK -> {
            val cEnd = now
            val cStart = now - 7 * 24 * 3600 * 1000L
            val pEnd = cStart
            val pStart = cStart - 7 * 24 * 3600 * 1000L
            Tuple4(cStart, cEnd, pStart, pEnd)
        }
        ActivityComparePeriod.MONTH -> {
            val cCal = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val cStart = cCal.timeInMillis
            val cEnd = now
            cCal.add(Calendar.MONTH, -1)
            val pStart = cCal.timeInMillis
            val pEnd = cStart
            Tuple4(cStart, cEnd, pStart, pEnd)
        }
        ActivityComparePeriod.YEAR -> {
            val cCal = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val cStart = cCal.timeInMillis
            val cEnd = now
            cCal.add(Calendar.YEAR, -1)
            val pStart = cCal.timeInMillis
            val pEnd = cStart
            Tuple4(cStart, cEnd, pStart, pEnd)
        }
    }

    val currentEntries = entries.filter { it.occurrenceTime in currStart..currEnd }
    val prevEntries = entries.filter { it.occurrenceTime in prevStart until prevEnd }

    val symptomColors = listOf(
        Color(0xFFEF4444), // Red
        Color(0xFFF59E0B), // Amber
        Color(0xFF8B5CF6), // Purple
        Color(0xFFEC4899), // Pink
        Color(0xFF06B6D4)  // Cyan
    )

    val entryActivityIds = entries.associate { entry ->
        entry.id to AnalyticsEngine.parseIdList(entry.activityIdsJson).toSet()
    }

    return activities.map { act ->
        val currMatching = currentEntries.filter { entry ->
            act.id in (entryActivityIds[entry.id] ?: emptySet())
        }
        val totalCount = currMatching.size
        val safeCount = currMatching.count { it.symptomId == null || (it.symptomSeverity ?: 0) == 0 }
        val safePercentage = if (totalCount > 0) ((safeCount.toDouble() / totalCount) * 100).toInt() else 0

        // Symptom breakdowns
        val symptomGroups = currMatching.filter { it.symptomId != null && (it.symptomSeverity ?: 0) > 0 }
            .groupBy { it.symptomId!! }

        val breakdowns = symptomGroups.entries.mapIndexed { index, (sId, list) ->
            val count = list.size
            val pct = if (totalCount > 0) ((count.toDouble() / totalCount) * 100).toInt() else 0
            ActivitySymptomBreakdown(
                symptomId = sId,
                symptomName = sympMap[sId]?.name ?: "Gejala #$sId",
                count = count,
                percentage = pct,
                color = symptomColors[index % symptomColors.size]
            )
        }.sortedByDescending { it.count }

        // Previous period
        val prevMatching = prevEntries.filter { entry ->
            act.id in (entryActivityIds[entry.id] ?: emptySet())
        }
        val prevTotalCount = prevMatching.size
        val prevSafeCount = prevMatching.count { it.symptomId == null || (it.symptomSeverity ?: 0) == 0 }
        val prevSafePercentage = if (prevTotalCount > 0) ((prevSafeCount.toDouble() / prevTotalCount) * 100).toInt() else 0

        val delta = if (totalCount > 0 && prevTotalCount > 0) safePercentage - prevSafePercentage else 0

        ActivityPeriodStats(
            activity = act,
            totalCount = totalCount,
            safeCount = safeCount,
            safePercentage = safePercentage,
            symptomBreakdowns = breakdowns,
            prevTotalCount = prevTotalCount,
            prevSafePercentage = prevSafePercentage,
            deltaSafePercentage = delta
        )
    }.sortedByDescending { it.totalCount }
}

data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
