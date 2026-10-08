package com.example.healthtracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthtracker.analytics.AnalyticsEngine
import com.example.healthtracker.analytics.MoodPainMatrixCell
import com.example.healthtracker.ui.HealthUiState
import com.example.ui.theme.SeverityMild
import com.example.ui.theme.SeverityModerate
import com.example.ui.theme.SeveritySevere

@Composable
fun MoodPainMatrixScreen(
    uiState: HealthUiState,
    modifier: Modifier = Modifier
) {
    val analysis = remember(uiState.entries) {
        AnalyticsEngine.evaluateMoodPainMatrix(uiState.entries)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp)
    ) {
        item {
            // Header explanation
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Matriks Silang Mood & Persepsi Nyeri",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Menganalisis seberapa kuat kapasitas mental (ambang batas ketahanan) memodulasi intensitas nyeri fisik yang dirasakan.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            // Sensitivity Gap Card
            Card(
                modifier = Modifier.fillMaxWidth().testTag("sensitivity_gap_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Lonjakan Sensitivitas Nyeri (Modulasi Kognitif)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Vertically stacked metrics (not side-by-side)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Rerata Nyeri Saat Krisis
                        Surface(
                            color = SeveritySevere.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, SeveritySevere.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = "Rerata Nyeri Saat Krisis", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(text = "Kapasitas Mental Habis (Skor 1-2)", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    text = "${analysis.overwhelmedAvgPain} / 10",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SeveritySevere
                                )
                            }
                        }

                        // 2. Perbedaan Nyeri (Lonjakan Sensitivitas)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = "Perbedaan Nyeri (Lonjakan Sensitivitas)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(text = "Selisih persepsi saat stres vs tenang", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Surface(
                                    color = SeveritySevere.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.TrendingUp, contentDescription = null, tint = SeveritySevere, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "+${analysis.sensitivityIncreasePercent}%",
                                            fontWeight = FontWeight.Bold,
                                            color = SeveritySevere,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Rerata Nyeri Saat Tenang
                        Surface(
                            color = SeverityMild.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, SeverityMild.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = "Rerata Nyeri Saat Tenang", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(text = "Kapasitas Mental Stabil & Resilien (Skor 4-5)", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    text = "${analysis.calmAvgPain} / 10",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SeverityMild
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = analysis.cognitiveModulationInsight,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        item {
            // Visual Heatmap Table
            Card(
                modifier = Modifier.fillMaxWidth().testTag("mood_heatmap_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Distribusi Tingkat Nyeri per Skor Mental",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Table Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Skor", modifier = Modifier.weight(1.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Ringan (1-3)", modifier = Modifier.weight(1.1f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SeverityMild)
                        Text(text = "Sedang (4-6)", modifier = Modifier.weight(1.1f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SeverityModerate)
                        Text(text = "Parah (7-10)", modifier = Modifier.weight(1.1f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SeveritySevere)
                        Text(text = "Rerata", modifier = Modifier.weight(0.9f), fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    for (cell in analysis.matrixCells) {
                        HeatmapRow(cell = cell)
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

@Composable
fun HeatmapRow(cell: MoodPainMatrixCell) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1.8f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = getMoodEmoji(cell.moodScore), fontSize = 16.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(text = "Skor ${cell.moodScore}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(text = cell.moodLabel, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Mild Count Box
        Box(
            modifier = Modifier
                .weight(1.1f)
                .padding(horizontal = 2.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (cell.mildCount > 0) SeverityMild.copy(alpha = (0.2f + (cell.mildCount * 0.15f)).coerceAtMost(0.8f))
                    else Color.Transparent
                )
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "${cell.mildCount}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }

        // Moderate Count Box
        Box(
            modifier = Modifier
                .weight(1.1f)
                .padding(horizontal = 2.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (cell.moderateCount > 0) SeverityModerate.copy(alpha = (0.2f + (cell.moderateCount * 0.15f)).coerceAtMost(0.8f))
                    else Color.Transparent
                )
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "${cell.moderateCount}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }

        // Severe Count Box
        Box(
            modifier = Modifier
                .weight(1.1f)
                .padding(horizontal = 2.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (cell.severeCount > 0) SeveritySevere.copy(alpha = (0.2f + (cell.severeCount * 0.15f)).coerceAtMost(0.8f))
                    else Color.Transparent
                )
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "${cell.severeCount}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }

        // Avg Pain
        Text(
            text = "${cell.avgPain}",
            modifier = Modifier.weight(0.9f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}
