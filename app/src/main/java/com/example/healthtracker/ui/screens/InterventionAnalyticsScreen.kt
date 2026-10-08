package com.example.healthtracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthtracker.analytics.AnalyticsEngine
import com.example.healthtracker.analytics.FastReliefDrugStats
import com.example.healthtracker.ui.HealthUiState
import com.example.ui.theme.LocalHarmonizedColors

@Composable
fun InterventionAnalyticsScreen(
    uiState: HealthUiState,
    modifier: Modifier = Modifier
) {
    val colors = LocalHarmonizedColors.current
    var selectedTab by remember { mutableStateOf(0) } // 0: Kombinasi Protokol, 1: Per Obat (Individu)

    val comboStats = remember(uiState.entries, uiState.interventions) {
        AnalyticsEngine.evaluateFastReliefAnalytics(uiState.entries, uiState.interventions, forCombinationsOnly = true)
    }

    val singleStats = remember(uiState.entries, uiState.interventions) {
        AnalyticsEngine.evaluateFastReliefAnalytics(uiState.entries, uiState.interventions, forCombinationsOnly = false)
    }

    val activeList = if (selectedTab == 0) comboStats else singleStats
    val champion = activeList.firstOrNull { it.zeroSuccessCount > 0 }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp)
    ) {
        // 1. HEADER TITLE & SUBTITLE
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Efektivitas Obat & Rekor Bebas Nyeri",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        fontSize = 17.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Mencari terapi tercepat melumpuhkan gejala hingga skala 0 dalam rentang < 24 jam.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.5.sp,
                    color = colors.textSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        // 2. SEGMENTED TAB SWITCHER: KOMBINASI PROTOKOL VS PER OBAT INDIVIDU
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
                    // Tab 0: Kombinasi Protokol
                    Surface(
                        color = if (selectedTab == 0) colors.cardBackground else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = if (selectedTab == 0) 1.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = 0 }
                            .testTag("tab_combo_protocol")
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 9.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🤝 Kombinasi Protokol (${comboStats.size})",
                                fontSize = 11.5.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 0) colors.textPrimary else colors.textSecondary
                            )
                        }
                    }

                    // Tab 1: Per Obat Individu
                    Surface(
                        color = if (selectedTab == 1) colors.cardBackground else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = if (selectedTab == 1) 1.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = 1 }
                            .testTag("tab_single_drug")
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 9.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "💊 Per Obat Individu (${singleStats.size})",
                                fontSize = 11.5.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 1) colors.textPrimary else colors.textSecondary
                            )
                        }
                    }
                }
            }
        }

        // 3. EXPLANATORY CRITERIA CARD
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                border = BorderStroke(0.75.dp, colors.cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🎯 ", fontSize = 13.sp)
                        Text(
                            text = "Metrik Analisis (< 24 Jam Paska Input):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = colors.textPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Target Skala 0: Menghitung berapa kali obat/protokol berhasil melenyapkan nyeri hingga 0 (bebas nyeri total).\n" +
                                "• ⚡ Rekor Tercepat: Durasi tercepat yang pernah dicapai obat menuju skala 0.\n" +
                                "• Rata-rata Waktu Reaksi: Kecepatan respon rata-rata dihitung dari evaluasi dalam 24 jam pertama.",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // 4. CHAMPION HERO CARD (JUARA TERCEPAT)
        if (champion != null) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (colors.isDark) Color(0xFF2E2207) else Color(0xFFFFFBEB)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "🏆 ", fontSize = 20.sp)
                                Column {
                                    Text(
                                        text = if (selectedTab == 0) "Kombinasi Tercepat ke Skala 0" else "Obat Tercepat ke Skala 0",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFB45309)
                                    )
                                    Text(
                                        text = champion.name,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (colors.isDark) Color(0xFFFDE68A) else Color(0xFF78350F)
                                    )
                                }
                            }

                            Surface(
                                color = Color(0xFFF59E0B),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = champion.fastestRecordDetail?.split(" ")?.firstOrNull() ?: "${champion.fastestMinutesToZero}m",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Box 1: Rekor Tercepat
                            Surface(
                                color = if (colors.isDark) Color(0xFF1F1A0A) else Color(0xFFFEF3C7),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(text = "⚡ Rekor Tercepat", fontSize = 9.5.sp, color = Color(0xFFB45309))
                                    Text(
                                        text = champion.fastestRecordDetail ?: "-",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (colors.isDark) Color(0xFFFDE68A) else Color(0xFF78350F)
                                    )
                                }
                            }

                            // Box 2: Sukses Bebas Nyeri (0)
                            Surface(
                                color = if (colors.isDark) Color(0xFF1F1A0A) else Color(0xFFFEF3C7),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(text = "🎯 Sukses ke 0", fontSize = 9.5.sp, color = Color(0xFFB45309))
                                    Text(
                                        text = "${champion.zeroSuccessCount}/${champion.totalTrials}x (${champion.zeroSuccessRatePercent}%)",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (colors.isDark) Color(0xFFFDE68A) else Color(0xFF78350F)
                                    )
                                }
                            }

                            // Box 3: Rerata Waktu ke 0
                            Surface(
                                color = if (colors.isDark) Color(0xFF1F1A0A) else Color(0xFFFEF3C7),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(text = "⏱️ Rerata ke 0", fontSize = 9.5.sp, color = Color(0xFFB45309))
                                    Text(
                                        text = "${champion.avgMinutesToZero ?: champion.avgOverallMinutes} Mnt",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (colors.isDark) Color(0xFFFDE68A) else Color(0xFF78350F)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. LIST OF DRUGS / COMBINATIONS
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedTab == 0) "Daftar Kombinasi Terapi (${comboStats.size})" else "Daftar Efektivitas Obat (${singleStats.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = "Urutan: Sukses ke 0 ➔ Rekor Tercepat",
                    fontSize = 10.sp,
                    color = colors.textMuted
                )
            }
        }

        if (activeList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.75.dp, colors.cardBorder)
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "💊", fontSize = 32.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (selectedTab == 0) "Belum Ada Kombinasi yang Dievaluasi" else "Belum Ada Evaluasi Obat (< 24 Jam)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Saat mencatat pemicu gejala di Timeline, gunakan tombol 'Evaluasi Efek Obat' dalam rentang 15 menit – 24 jam untuk melacak kecepatan obat melumpuhkan nyeri hingga skala 0.",
                                fontSize = 11.sp,
                                color = colors.textSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            items(activeList, key = { it.id }) { stat ->
                FastReliefCardItem(stat = stat)
            }
        }
    }
}

@Composable
fun FastReliefCardItem(
    stat: FastReliefDrugStats
) {
    val colors = LocalHarmonizedColors.current

    val rankIcon = when (stat.rank) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "#${stat.rank}"
    }

    val rankBadgeBg = when (stat.rank) {
        1 -> if (colors.isDark) Color(0xFF451A03) else Color(0xFFFEF3C7)
        2 -> if (colors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
        3 -> if (colors.isDark) Color(0xFF3B1D0F) else Color(0xFFFFEDD5)
        else -> colors.pillInactiveBg
    }

    val rankBadgeText = when (stat.rank) {
        1 -> Color(0xFFD97706)
        2 -> Color(0xFF64748B)
        3 -> Color(0xFFC2410C)
        else -> colors.textSecondary
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        border = BorderStroke(
            if (stat.rank == 1) 1.25.dp else 0.75.dp,
            if (stat.rank == 1) Color(0xFFF59E0B) else colors.cardBorder
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().testTag("item_relief_${stat.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Rank badge, Name, Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        color = rankBadgeBg,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.5.dp, rankBadgeText.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = rankIcon,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = rankBadgeText,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = stat.name,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )

                        Text(
                            text = if (stat.isCombination) "Kombinasi ${stat.componentNames.size} Terapi"
                            else "Tunggal: ${stat.singleCount}x • Kombinasi: ${stat.comboCount}x",
                            fontSize = 10.5.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                // Zero pain success badge
                Surface(
                    color = if (stat.zeroSuccessCount > 0) (if (colors.isDark) Color(0xFF064E3B) else Color(0xFFECFDF5))
                    else colors.pillInactiveBg,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(
                        0.5.dp,
                        if (stat.zeroSuccessCount > 0) Color(0xFF10B981) else colors.cardBorder
                    )
                ) {
                    Text(
                        text = if (stat.zeroSuccessCount > 0) "🎯 ${stat.zeroSuccessRatePercent}% Bebas Nyeri"
                        else "Belum Capai 0",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (stat.zeroSuccessCount > 0) Color(0xFF059669) else colors.textMuted,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4-Metric Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Metric 1: Sukses ke 0
                Surface(
                    color = colors.pillInactiveBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, colors.cardBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(text = "🎯 Sukses ke 0", fontSize = 10.sp, color = colors.textSecondary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${stat.zeroSuccessCount} / ${stat.totalTrials} Kali",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (stat.zeroSuccessCount > 0) Color(0xFF10B981) else colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (stat.zeroSuccessRatePercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = Color(0xFF10B981),
                            trackColor = colors.cardBorder
                        )
                    }
                }

                // Metric 2: Rekor Tercepat
                Surface(
                    color = colors.pillInactiveBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, colors.cardBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(text = "⚡ Rekor Tercepat", fontSize = 10.sp, color = colors.textSecondary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stat.fastestRecordDetail ?: "Belum ke 0",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (stat.fastestMinutesToZero != null) Color(0xFFD97706) else colors.textMuted
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (stat.fastestMinutesToZero != null) "Personal Best" else "Drop ke 0",
                            fontSize = 9.5.sp,
                            color = colors.textMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Metric 3: Rerata Waktu Reaksi
                Surface(
                    color = colors.pillInactiveBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, colors.cardBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(text = "⏱️ Rerata Waktu Reaksi", fontSize = 10.sp, color = colors.textSecondary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (stat.avgMinutesToZero != null) "${stat.avgMinutesToZero} Menit (ke 0)"
                            else "${stat.avgOverallMinutes} Menit",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                }

                // Metric 4: Rata-rata Penurunan (Delta)
                Surface(
                    color = colors.pillInactiveBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, colors.cardBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(text = "📉 Rata-rata Penurunan", fontSize = 10.sp, color = colors.textSecondary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Turun -${stat.avgDelta} Poin",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (stat.avgDelta >= 2.0) Color(0xFF10B981) else colors.textPrimary
                        )
                    }
                }
            }
        }
    }
}
