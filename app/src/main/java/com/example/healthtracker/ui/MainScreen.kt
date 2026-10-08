package com.example.healthtracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthtracker.model.HealthEntryEntity
import com.example.healthtracker.ui.screens.*
import com.example.healthtracker.util.ApkExporter
import com.example.ui.theme.LocalHarmonizedColors

enum class AppDestination(val label: String, val icon: String) {
    TIMELINE("Timeline", "📅"),
    CALENDAR("Kalender", "📆"),
    HABITS("Habit", "🔥"),
    TRIGGERS("Korelasi Aktivitas", "📈"),
    INTERVENTIONS("Efektivitas Obat", "📊"),
    MOOD_MATRIX("Mood", "🧠"),
    MASTER_DATA("Master", "🗂️"),
    SETTINGS("Setelan", "⚙️")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: HealthViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = LocalHarmonizedColors.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Navigation state
    val context = LocalContext.current
    var currentDestination by remember { mutableStateOf(AppDestination.TIMELINE) }
    var isAddingEntry by remember { mutableStateOf(false) }
    var entryForFullEdit by remember { mutableStateOf<HealthEntryEntity?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showApkExportDialog by remember { mutableStateOf(false) }
    var apkExtractedPath by remember { mutableStateOf<String?>(null) }

    // Status message snackbar
    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatus()
        }
    }

    // Dynamic UI Density Scaling (Mobile-First 70% to 125%, default 85%)
    val baseDensity = LocalDensity.current
    val customDensity = remember(uiState.uiDensityScale, baseDensity) {
        Density(
            density = baseDensity.density * uiState.uiDensityScale,
            fontScale = baseDensity.fontScale * uiState.uiDensityScale
        )
    }

    CompositionLocalProvider(LocalDensity provides customDensity) {
        if (uiState.isAppLocked) {
            PinLockScreen(viewModel = viewModel)
        } else if (isAddingEntry || entryForFullEdit != null) {
            BackHandler {
                isAddingEntry = false
                entryForFullEdit = null
            }
            AddEntryScreen(
                uiState = uiState,
                viewModel = viewModel,
                entryToEdit = entryForFullEdit,
                onNavigateBack = {
                    isAddingEntry = false
                    entryForFullEdit = null
                }
            )
        } else {
            BackHandler(enabled = currentDestination != AppDestination.TIMELINE) {
                currentDestination = AppDestination.TIMELINE
            }

            Scaffold(
                modifier = modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                containerColor = colors.background,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.headerBackground)
                            .statusBarsPadding()
                    ) {
                        // 1. TOP HEADER (HARMONIZED COLORS WITH THEME TOGGLE)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Brand: [CM] CiroMind
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.pillActiveBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "CM",
                                        color = colors.pillActiveText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "CiroMind",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = colors.textPrimary
                                )
                            }

                            // Right Action Buttons: [☀️/🌙] [⋮]
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Theme Toggle Button (Light / Dark)
                                IconButton(
                                    onClick = { viewModel.toggleThemeMode() },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("btn_toggle_theme")
                                ) {
                                    Icon(
                                        imageVector = if (colors.isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        contentDescription = "Toggle Theme",
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Overflow menu
                                Box {
                                    IconButton(
                                        onClick = { showOverflowMenu = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Menu",
                                            tint = colors.textSecondary
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showOverflowMenu,
                                        onDismissRequest = { showOverflowMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(if (colors.isDark) "☀️ Ganti ke Mode Terang" else "🌙 Ganti ke Mode Gelap") },
                                            onClick = {
                                                viewModel.toggleThemeMode()
                                                showOverflowMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("🗂️ Data Master (Obat & Gejala)") },
                                            onClick = {
                                                currentDestination = AppDestination.MASTER_DATA
                                                showOverflowMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("📆 Kalender Ringkasan") },
                                            onClick = {
                                                currentDestination = AppDestination.CALENDAR
                                                showOverflowMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("🔥 Statistik & Pelacak Habit") },
                                            onClick = {
                                                currentDestination = AppDestination.HABITS
                                                showOverflowMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("⚙️ Pengaturan & PIN") },
                                            onClick = {
                                                currentDestination = AppDestination.SETTINGS
                                                showOverflowMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("📦 Ekstrak & Bagikan APK") },
                                            onClick = {
                                                showApkExportDialog = true
                                                showOverflowMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 2. HORIZONTAL TAB NAVIGATION (HARMONIZED)
                        val destinations = AppDestination.values()
                        val selectedIndex = destinations.indexOf(currentDestination)

                        ScrollableTabRow(
                            selectedTabIndex = selectedIndex,
                            containerColor = colors.headerBackground,
                            contentColor = colors.textPrimary,
                            edgePadding = 12.dp,
                            indicator = { tabPositions ->
                                if (selectedIndex in tabPositions.indices) {
                                    TabRowDefaults.SecondaryIndicator(
                                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                                        color = colors.textPrimary,
                                        height = 2.5.dp
                                    )
                                }
                            },
                            divider = {
                                HorizontalDivider(color = colors.cardBorder, thickness = 0.75.dp)
                            }
                        ) {
                            destinations.forEach { dest ->
                                val isSelected = currentDestination == dest
                                val labelText = "${dest.icon} ${dest.label}"

                                Tab(
                                    selected = isSelected,
                                    onClick = { currentDestination = dest },
                                    text = {
                                        Text(
                                            text = labelText,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) colors.textPrimary else colors.textSecondary
                                        )
                                    },
                                    modifier = Modifier.testTag("top_tab_${dest.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(colors.background)
                ) {
                    when (currentDestination) {
                        AppDestination.TIMELINE -> TimelineScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            onNavigateToAdd = { isAddingEntry = true },
                            onEditAcuteClick = { entryForFullEdit = it }
                        )
                        AppDestination.CALENDAR -> CalendarOverviewScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            onNavigateToAdd = { isAddingEntry = true }
                        )
                        AppDestination.HABITS -> HabitAnalyticsScreen(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                        AppDestination.INTERVENTIONS -> InterventionAnalyticsScreen(
                            uiState = uiState
                        )
                        AppDestination.TRIGGERS -> TriggerAnalyticsScreen(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                        AppDestination.MOOD_MATRIX -> MoodPainMatrixScreen(
                            uiState = uiState
                        )
                        AppDestination.MASTER_DATA -> MasterDataManagerScreen(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                        AppDestination.SETTINGS -> SettingsScreen(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                }
            }

            // APK EXPORT & SHARE MODAL DIALOG
            if (showApkExportDialog) {
                AlertDialog(
                    onDismissRequest = { showApkExportDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DownloadForOffline, contentDescription = null, tint = Color(0xFF0284C7))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ekstrak & Bagikan File APK", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Pilih cara untuk mendapatkan file APK ke HP kamu tanpa komputer:",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )

                            // Option 1: Extract to Download folder
                            Button(
                                onClick = {
                                    val (success, path) = ApkExporter.extractApkToDownloads(context)
                                    showApkExportDialog = false
                                    if (success) {
                                        apkExtractedPath = path
                                    } else {
                                        viewModel.showStatus(path)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("1. Ekstrak ke Folder Download", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            // Option 2: Share via Android Share Sheet
                            OutlinedButton(
                                onClick = {
                                    showApkExportDialog = false
                                    val success = ApkExporter.shareApk(context)
                                    if (!success) viewModel.showStatus("Gagal membagikan APK via Intent.")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("2. Bagikan File APK (Share)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            // Option 3: Open upload website directly
                            Text(
                                text = "Buka Website Upload File Gratis di Browser Emulator Ini:",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textSecondary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        ApkExporter.openBrowser(context, "https://file.kiwi")
                                        showApkExportDialog = false
                                    },
                                    label = { Text("🌐 File.kiwi", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        ApkExporter.openBrowser(context, "https://filebin.net")
                                        showApkExportDialog = false
                                    },
                                    label = { Text("🌐 Filebin", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        ApkExporter.openBrowser(context, "https://tmpfiles.org")
                                        showApkExportDialog = false
                                    },
                                    label = { Text("🌐 Tmpfiles", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showApkExportDialog = false }) {
                            Text("Tutup")
                        }
                    }
                )
            }

            // APK EXTRACTED SUCCESS DIALOG
            if (apkExtractedPath != null) {
                AlertDialog(
                    onDismissRequest = { apkExtractedPath = null },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("APK Berhasil Disimpan!", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "File APK tersimpan di folder Download emulator:",
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
                                text = "Sekarang buka Chrome di emulator preview ini, lalu unggah 'HealthTracker.apk' ini ke File.kiwi untuk didownload ke HP kamu!",
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
                            Text("Buka File.kiwi")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { apkExtractedPath = null }) {
                            Text("Tutup")
                        }
                    }
                )
            }
        }
    }
}
