package com.example.healthtracker.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.healthtracker.ui.HealthUiState
import com.example.healthtracker.ui.HealthViewModel
import com.example.healthtracker.util.ApkExporter
import com.example.ui.theme.LocalHarmonizedColors
import com.example.ui.theme.SeverityMild
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    uiState: HealthUiState,
    viewModel: HealthViewModel,
    modifier: Modifier = Modifier
) {
    val colors = LocalHarmonizedColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Dialog states
    var showPinDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var apkExtractedPath by remember { mutableStateOf<String?>(null) }

    // SAF Document Picker to save .ciro backup file
    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val json = viewModel.getBackupJson()
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(json.toByteArray(Charsets.UTF_8))
                    }
                    viewModel.showStatus("File backup .ciro berhasil disimpan ke folder.")
                } catch (e: Exception) {
                    viewModel.showStatus("Gagal menyimpan file: ${e.message}")
                }
            }
        }
    }

    // SAF Document Picker to restore .ciro backup file
    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (!content.isNullOrBlank()) {
                        viewModel.restoreBackupJson(content)
                    } else {
                        viewModel.showStatus("File backup kosong atau tidak dapat dibaca.")
                    }
                } catch (e: Exception) {
                    viewModel.showStatus("Gagal membaca file: ${e.message}")
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. TEMA TAMPILAN (DARK / LIGHT MODE SELECTOR)
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            border = BorderStroke(0.75.dp, colors.cardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Tema Tampilan (Dark / Light Mode)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Sesuaikan kontras tampilan agar serasi dan nyaman di mata",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }

                    Icon(
                        imageVector = if (colors.isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = null,
                        tint = if (colors.isDark) Color(0xFF38BDF8) else Color(0xFFF59E0B),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val themes = listOf(
                        "light" to "☀️ Terang",
                        "dark" to "🌙 Gelap",
                        "system" to "📱 Sistem"
                    )

                    themes.forEach { (mode, label) ->
                        val isSelected = uiState.themeMode == mode
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 2. EKSTRAK & BAGIKAN FILE APK (TANPA PC)
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            border = BorderStroke(1.dp, if (colors.isDark) Color(0xFF0284C7) else Color(0xFFBAE6FD)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "📦 Ekstrak & Bagikan File APK (.apk)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0369A1)
                        )
                        Text(
                            text = "Unduh APK langsung di HP tanpa perlu buka komputer!",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.DownloadForOffline,
                        contentDescription = null,
                        tint = if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Langkah: Klik 'Ekstrak APK' di bawah, lalu buka web upload gratis lewat browser emulator ini untuk upload & download langsung ke HP kamu.",
                    fontSize = 10.5.sp,
                    color = colors.textSecondary,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val (success, message) = ApkExporter.extractApkToDownloads(context)
                            if (success) {
                                apkExtractedPath = message
                            } else {
                                viewModel.showStatus(message)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f).testTag("btn_extract_apk")
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ekstrak APK", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val success = ApkExporter.shareApk(context)
                            if (!success) {
                                viewModel.showStatus("Gagal membagikan APK via Intent.")
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("btn_share_apk")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bagikan (Share)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Buka Web Upload File Gratis (Buka di browser emulator ini):",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = false,
                        onClick = { ApkExporter.openBrowser(context, "https://file.kiwi") },
                        label = { Text("🌐 File.kiwi", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = false,
                        onClick = { ApkExporter.openBrowser(context, "https://filebin.net") },
                        label = { Text("🌐 Filebin.net", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = false,
                        onClick = { ApkExporter.openBrowser(context, "https://tmpfiles.org") },
                        label = { Text("🌐 Tmpfiles", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. UI DENSITY & ZOOM SCALING (MOBILE-FIRST)
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            border = BorderStroke(0.75.dp, colors.cardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Kepadatan Tampilan (UI Density / Zoom)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Skala font & komponen agar pas dan padat di layar ponsel",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }

                    Surface(
                        color = colors.pillInactiveBg,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.5.dp, colors.cardBorder)
                    ) {
                        Text(
                            text = "${(uiState.uiDensityScale * 100).toInt()}%",
                            fontWeight = FontWeight.Bold,
                            color = colors.pillInactiveText,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Slider(
                    value = uiState.uiDensityScale,
                    onValueChange = { viewModel.setUiDensityScale(it) },
                    valueRange = 0.70f..1.25f,
                    steps = 10,
                    modifier = Modifier.fillMaxWidth().testTag("slider_ui_density")
                )

                // Quick presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val presets = listOf(
                        0.70f to "70%",
                        0.85f to "85% (HP)",
                        1.00f to "100%",
                        1.15f to "115%",
                        1.25f to "125%"
                    )

                    presets.forEach { (scale, label) ->
                        FilterChip(
                            selected = (uiState.uiDensityScale * 100).toInt() == (scale * 100).toInt(),
                            onClick = { viewModel.setUiDensityScale(scale) },
                            label = { Text(label, fontSize = 10.sp) },
                            modifier = Modifier.padding(horizontal = 1.dp)
                        )
                    }
                }
            }
        }

        // 4. KEAMANAN & PRIVASI (PIN 4-DIGIT SHA-256)
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            border = BorderStroke(0.75.dp, colors.cardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Keamanan & Kunci Aplikasi",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = "Proteksi PIN 4-digit dengan hashing SHA-256 untuk privasi data medis.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (uiState.isPinConfigured) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if (uiState.isPinConfigured) SeverityMild else colors.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.isPinConfigured) "Kunci PIN Aktif" else "Kunci PIN Nonaktif",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary
                        )
                    }

                    if (uiState.isPinConfigured) {
                        OutlinedButton(
                            onClick = { viewModel.disablePin() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Matikan PIN", fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = { showPinDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.pillActiveBg,
                                contentColor = colors.pillActiveText
                            ),
                            modifier = Modifier.testTag("btn_setup_pin")
                        ) {
                            Text("Atur PIN 4-Digit", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 5. DATA SAMPEL & DEMONSTRASI (REQUEST 1)
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            border = BorderStroke(1.dp, if (colors.isDark) Color(0xFF6366F1) else Color(0xFFC7D2FE)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "🧪 Data Sampel & Demonstrasi Klinis",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (colors.isDark) Color(0xFFA5B4FC) else Color(0xFF4338CA)
                        )
                        Text(
                            text = "Isi simulasi 7 hari untuk melihat grafik, korelasi, habit & kalender",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = if (colors.isDark) Color(0xFFA5B4FC) else Color(0xFF6366F1),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.seedSampleData() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (colors.isDark) Color(0xFF4F46E5) else Color(0xFF6366F1),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1.3f).testTag("btn_seed_sample_data")
                    ) {
                        Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Muat Data Sampel (7 Hari)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { showClearConfirmDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f).testTag("btn_clear_all_entries")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bersihkan Log", fontSize = 11.5.sp)
                    }
                }
            }
        }

        // 6. CADANGAN DATA MURNI FILE (.CIRO) (REQUEST 2)
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            border = BorderStroke(0.75.dp, colors.cardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Cadangan Data File Kustom (.ciro)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = "Simpan & pulihkan data klinis murni dalam format berkas .ciro pribadi tanpa salin-tempel teks.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                val todayDateTag = remember {
                    SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(Date())
                }
                val defaultBackupName = "Backup_$todayDateTag.ciro"

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Option 1: Simpan berkas .ciro ke Folder via SAF
                    Button(
                        onClick = {
                            createDocumentLauncher.launch(defaultBackupName)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.pillActiveBg,
                            contentColor = colors.pillActiveText
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("btn_save_ciro_folder")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simpan File ($defaultBackupName)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Option 2: Share / Kirim berkas .ciro langsung ke Telegram
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val json = viewModel.getBackupJson()
                                    val file = File(context.cacheDir, defaultBackupName)
                                    file.writeText(json)
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/octet-stream"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, defaultBackupName)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Kirim Berkas $defaultBackupName ke Telegram"))
                                } catch (e: Exception) {
                                    viewModel.showStatus("Gagal membagikan file: ${e.message}")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("btn_share_ciro_telegram")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF0284C7))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kirim / Bagikan File .ciro ke Telegram", fontSize = 12.sp, color = colors.textPrimary)
                    }

                    // Option 3: Pulihkan langsung dari file .ciro (File Picker)
                    OutlinedButton(
                        onClick = {
                            openDocumentLauncher.launch(arrayOf("*/*"))
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("btn_restore_ciro_file")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pulihkan dari Berkas (.ciro)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 6. FILOSOFI LOCAL-FIRST & N-OF-1
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            border = BorderStroke(0.75.dp, colors.cardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0F172A),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Filosofi Local-First & N-of-1",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• 100% Offline-First: Database SQLite tersimpan di perangkat Anda tanpa tracking pihak ketiga.\n" +
                            "• N-of-1 Clinical Trial: Anda adalah subjek uji mandiri. Sistem mengevaluasi intervensi berdasarkan respon tubuh Anda sendiri, bukan rata-rata populasi umum.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )
            }
        }
    }

    // Modal APK Extracted Success Dialog
    if (apkExtractedPath != null) {
        AlertDialog(
            onDismissRequest = { apkExtractedPath = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("APK Berhasil Diekstrak!", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "File installer telah disalin ke folder Download:",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                    Surface(
                        color = colors.pillInactiveBg,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = apkExtractedPath ?: "",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Text(
                        text = "Sekarang kamu bisa buka Chrome di preview ini lalu upload file 'HealthTracker.apk' ini ke File.kiwi untuk didownload ke HP kamu!",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        ApkExporter.openBrowser(context, "https://file.kiwi")
                        apkExtractedPath = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.White)
                ) {
                    Text("Buka Web File.kiwi")
                }
            },
            dismissButton = {
                TextButton(onClick = { apkExtractedPath = null }) {
                    Text("Tutup")
                }
            }
        )
    }

    // Modal Set PIN
    if (showPinDialog) {
        var pinInput by remember { mutableStateOf("") }
        var confirmPinInput by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Atur PIN 4-Digit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Masukkan 4 digit angka:", fontSize = 12.sp)
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinInput = it },
                        label = { Text("PIN Baru") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirmPinInput = it },
                        label = { Text("Konfirmasi PIN") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (err != null) {
                        Text(text = err ?: "", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length != 4) {
                            err = "PIN harus tepat 4 digit angka."
                        } else if (pinInput != confirmPinInput) {
                            err = "Konfirmasi PIN tidak cocok."
                        } else {
                            viewModel.configurePin(pinInput)
                            showPinDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.pillActiveBg,
                        contentColor = colors.pillActiveText
                    )
                ) {
                    Text("Simpan PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Batal") }
            }
        )
    }

    // Modal Konfirmasi Bersihkan Seluruh Riwayat Log
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Bersihkan Seluruh Riwayat?", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "Semua riwayat entri log (rutinitas, pemicu, evaluasi obat) akan dihapus secara permanen dari database. Data master (obat & gejala) tetap tersimpan.",
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllEntries()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    )
                ) {
                    Text("Hapus Riwayat", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) { Text("Batal") }
            }
        )
    }
}
