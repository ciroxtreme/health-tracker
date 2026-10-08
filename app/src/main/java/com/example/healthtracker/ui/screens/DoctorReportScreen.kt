package com.example.healthtracker.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthtracker.analytics.AnalyticsEngine
import com.example.healthtracker.analytics.TimeRangeFilter
import com.example.healthtracker.ui.HealthUiState
import com.example.healthtracker.ui.HealthViewModel
import com.example.ui.theme.SeverityMild
import com.example.ui.theme.SeveritySevere
import kotlinx.coroutines.launch

@Composable
fun DoctorReportScreen(
    uiState: HealthUiState,
    viewModel: HealthViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedFilter by remember { mutableStateOf(TimeRangeFilter.LAST_30_DAYS) }

    val summary = remember(uiState.entries, uiState.interventions, uiState.symptoms, uiState.activities, selectedFilter) {
        AnalyticsEngine.generateDoctorSummary(
            allEntries = uiState.entries,
            interventions = uiState.interventions,
            symptoms = uiState.symptoms,
            activities = uiState.activities,
            timeRange = selectedFilter
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp)
    ) {
        // Document Header
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Laporan Eksekutif Klinis",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Dokumen Konsultasi Dokter Spesialis (N-of-1 Trial)",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.MedicalInformation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Time range chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TimeRangeFilter.values().forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter.label, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Export Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val csv = viewModel.getExportCsv()
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("HealthTracker CSV", csv))
                                    viewModel.showStatus("Data riwayat CSV berhasil disalin ke clipboard.")
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("btn_export_csv"),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ekspor CSV", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = {
                                val reportText = buildString {
                                    append("LAPORAN KLINIS N-OF-1 HEALTH TRACKER PRO\n")
                                    append("Periode: ${summary.periodLabel} (Total ${summary.totalEntries} entri)\n\n")
                                    append("1. GEJALA TERBANYAK:\n")
                                    summary.topSymptoms.forEach { (s, c) -> append(" - $s: $c kejadian\n") }
                                    append("\n2. INTERVENSI EFEKTIF (Δ ≥ 2):\n")
                                    if (summary.mostEffectiveInterventions.isEmpty()) append(" - Belum ada intervensi yang mencapai signifikansi klinis\n")
                                    else summary.mostEffectiveInterventions.forEach { append(" - ${it.interventionName}: Sukses ${it.successRatePercent}%, Median Δ ${it.medianDelta}\n") }
                                    if (summary.adverseAlerts.isNotEmpty()) {
                                        append("\n3. PERINGATAN REAKSI MERUGIKAN:\n")
                                        summary.adverseAlerts.forEach { append(" - $it\n") }
                                    }
                                    append("\n4. AKTIVITAS PEMICU SIGNIFIKAN (≥ 50%):\n")
                                    if (summary.significantTriggers.isEmpty()) append(" - Belum terdeteksi pemicu berulang ≥ 50%\n")
                                    else summary.significantTriggers.forEach { append(" - ${it.activityName} ➔ ${it.symptomName} (${it.triggerPercentageInPeriod}% korelasi, lag ~${it.avgLagMinutes} mnt)\n") }
                                    append("\n5. WAWASAN MIND-BODY:\n")
                                    append(summary.mindBodyInsight)
                                }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Doctor Summary", reportText))
                                viewModel.showStatus("Ringkasan formal dokter berhasil disalin.")
                            },
                            modifier = Modifier.weight(1f).testTag("btn_copy_summary"),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Salin Ringkasan", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Section 1: Ringkasan Entri
        item {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "1. Periode & Volume Data", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Observasi mencakup ${summary.totalEntries} entri tercatat pada ${summary.periodLabel}.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Section 2: Frekuensi Gejala
        item {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "2. Frekuensi Gejala Terbanyak", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (summary.topSymptoms.isEmpty()) {
                        Text("Tidak ada gejala tercatat pada periode ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        summary.topSymptoms.forEach { (symp, count) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = symp, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(text = "$count kejadian", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Intervensi Paling Efektif & Reaksi Merugikan
        item {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "3. Efektivitas Intervensi Klinis", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (summary.mostEffectiveInterventions.isEmpty()) {
                        Text(
                            text = "Belum ada intervensi yang memenuhi kriteria efektif (median Δ ≥ 2 dari minimal 3 uji).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        summary.mostEffectiveInterventions.forEach { interv ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "✓ ${interv.interventionName}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = SeverityMild)
                                Text(text = "Sukses: ${interv.successRatePercent}% (Median Δ ${interv.medianDelta})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (summary.adverseAlerts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = SeveritySevere.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(text = "Peringatan Reaksi Merugikan (Adverse Events):", fontWeight = FontWeight.Bold, color = SeveritySevere, fontSize = 11.sp)
                                summary.adverseAlerts.forEach { alert ->
                                    Text(text = "• $alert", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = SeveritySevere)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 4: Pemicu Aktivitas Signifikan
        item {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "4. Pemicu Aktivitas Terbukti Signifikan (≥ 50%)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (summary.significantTriggers.isEmpty()) {
                        Text(
                            text = "Belum terdeteksi korelasi aktivitas yang konsisten memicu gejala di atas ambang 50%.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        summary.significantTriggers.forEach { trigger ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${trigger.activityName} ➔ ${trigger.symptomName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${trigger.triggerPercentageInPeriod}% korelasi (~${trigger.avgLagMinutes} mnt)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 5: Wawasan Mind-Body
        item {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "5. Wawasan Interaksi Pikiran-Tubuh", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = summary.mindBodyInsight,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
