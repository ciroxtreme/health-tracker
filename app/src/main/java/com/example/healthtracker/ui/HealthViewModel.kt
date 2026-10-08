package com.example.healthtracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthtracker.analytics.*
import com.example.healthtracker.database.AppDatabase
import com.example.healthtracker.model.ActivityEntity
import com.example.healthtracker.model.HabitEntity
import com.example.healthtracker.model.HealthEntryEntity
import com.example.healthtracker.model.InterventionEntity
import com.example.healthtracker.model.SymptomEntity
import com.example.healthtracker.repository.HealthRepository
import com.example.healthtracker.security.SecurityAndPrefsManager
import java.util.Calendar
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HealthUiState(
    val entries: List<HealthEntryEntity> = emptyList(),
    val interventions: List<InterventionEntity> = emptyList(),
    val symptoms: List<SymptomEntity> = emptyList(),
    val activities: List<ActivityEntity> = emptyList(),
    val habits: List<HabitEntity> = emptyList(),
    val timeRangeFilter: TimeRangeFilter = TimeRangeFilter.LAST_30_DAYS,
    val uiDensityScale: Float = 0.85f,
    val themeMode: String = "light", // "light", "dark", "system"
    val timelineMode: String = "normal", // "normal", "daily"
    val isAppLocked: Boolean = false,
    val isPinConfigured: Boolean = false,
    val statusMessage: String? = null
)

class HealthViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = HealthRepository(db.healthDao())
    val prefsManager = SecurityAndPrefsManager(application)

    private val _timeRangeFilter = MutableStateFlow(TimeRangeFilter.LAST_30_DAYS)
    private val _uiDensityScale = MutableStateFlow(prefsManager.getUiDensityScale())
    private val _themeMode = MutableStateFlow(prefsManager.getThemeMode())
    private val _timelineMode = MutableStateFlow(prefsManager.getTimelineMode())
    private val _isAppLocked = MutableStateFlow(prefsManager.isPinEnabled())
    private val _statusMessage = MutableStateFlow<String?>(null)

    private val masterDataFlow = combine(
        repository.allEntries,
        repository.allInterventions,
        repository.allSymptoms,
        repository.allActivities,
        repository.allHabits
    ) { entries, interventions, symptoms, activities, habits ->
        listOf(entries, interventions, symptoms, activities, habits)
    }

    private val settingsFlow = combine(
        combine(_timeRangeFilter, _uiDensityScale, _themeMode) { filter, density, theme ->
            Triple(filter, density, theme)
        },
        combine(_timelineMode, _isAppLocked, _statusMessage) { timelineMode, isLocked, msg ->
            Triple(timelineMode, isLocked, msg)
        }
    ) { (filter, density, theme), (timelineMode, isLocked, msg) ->
        SettingsData(filter, density, theme, timelineMode, isLocked, msg)
    }

    val uiState: StateFlow<HealthUiState> = combine(
        masterDataFlow,
        settingsFlow
    ) { master, settings ->
        @Suppress("UNCHECKED_CAST")
        val entries = master[0] as List<HealthEntryEntity>
        @Suppress("UNCHECKED_CAST")
        val interventions = master[1] as List<InterventionEntity>
        @Suppress("UNCHECKED_CAST")
        val symptoms = master[2] as List<SymptomEntity>
        @Suppress("UNCHECKED_CAST")
        val activities = master[3] as List<ActivityEntity>
        @Suppress("UNCHECKED_CAST")
        val habits = master[4] as List<HabitEntity>

        HealthUiState(
            entries = entries,
            interventions = interventions,
            symptoms = symptoms,
            activities = activities,
            habits = habits,
            timeRangeFilter = settings.filter,
            uiDensityScale = settings.density,
            themeMode = settings.theme,
            timelineMode = settings.timelineMode,
            isAppLocked = settings.isLocked,
            isPinConfigured = prefsManager.isPinEnabled(),
            statusMessage = settings.msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HealthUiState(
            uiDensityScale = prefsManager.getUiDensityScale(),
            themeMode = prefsManager.getThemeMode(),
            timelineMode = prefsManager.getTimelineMode(),
            isAppLocked = prefsManager.isPinEnabled(),
            isPinConfigured = prefsManager.isPinEnabled()
        )
    )

    private data class SettingsData(
        val filter: TimeRangeFilter,
        val density: Float,
        val theme: String,
        val timelineMode: String,
        val isLocked: Boolean,
        val msg: String?
    )

    fun unlockApp(pin: String): Boolean {
        val success = prefsManager.verifyPin(pin)
        if (success) {
            _isAppLocked.value = false
        }
        return success
    }

    fun configurePin(pin: String) {
        prefsManager.setPin(pin)
        showStatus("PIN 4-digit berhasil dikonfigurasi.")
    }

    fun disablePin() {
        prefsManager.disablePin()
        _isAppLocked.value = false
        showStatus("Kunci PIN dinonaktifkan.")
    }

    fun setUiDensityScale(scale: Float) {
        val validScale = scale.coerceIn(0.70f, 1.25f)
        prefsManager.setUiDensityScale(validScale)
        _uiDensityScale.value = validScale
    }

    fun setThemeMode(mode: String) {
        prefsManager.setThemeMode(mode)
        _themeMode.value = mode
        showStatus(if (mode == "dark") "Mode Gelap (Dark Mode) aktif." else "Mode Terang (Light Mode) aktif.")
    }

    fun setTimelineMode(mode: String) {
        prefsManager.setTimelineMode(mode)
        _timelineMode.value = mode
        showStatus(if (mode == "daily") "Mode Timeline Harian (Daily) aktif." else "Mode Timeline Normal (Semua Histori) aktif.")
    }

    fun toggleThemeMode() {
        val nextMode = if (_themeMode.value == "dark") "light" else "dark"
        setThemeMode(nextMode)
    }

    fun setTimeRangeFilter(filter: TimeRangeFilter) {
        _timeRangeFilter.value = filter
    }

    fun showStatus(message: String) {
        _statusMessage.value = message
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    // --- LOGGING & ENTRY ACTIONS ---
    fun saveAcuteEntry(
        occurrenceTime: Long,
        symptomId: Long?,
        symptomSeverity: Int?,
        symptomNotes: String?,
        interventionIds: List<Long>,
        interventionNotes: String?,
        activityIds: List<Long>,
        activityNotes: String?,
        habitIds: List<Long> = emptyList(),
        moodScore: Int?,
        moodNotes: String?,
        symptomIds: List<Long> = emptyList(),
        symptomSeveritiesJson: String = "{}"
    ) {
        viewModelScope.launch {
            val resolvedSymptomIds = if (symptomIds.isNotEmpty()) symptomIds else listOfNotNull(symptomId)
            val entry = HealthEntryEntity(
                occurrenceTime = occurrenceTime,
                symptomId = resolvedSymptomIds.firstOrNull(),
                symptomIdsJson = resolvedSymptomIds.toString(),
                symptomSeveritiesJson = symptomSeveritiesJson,
                symptomSeverity = symptomSeverity,
                symptomNotes = symptomNotes,
                interventionIdsJson = interventionIds.toString(),
                interventionNotes = interventionNotes,
                activityIdsJson = activityIds.toString(),
                activityNotes = activityNotes,
                habitIdsJson = habitIds.toString(),
                moodScore = moodScore,
                moodNotes = moodNotes,
                isEvaluationPhase = false
            )
            repository.insertEntry(entry)
            showStatus("Entri kesehatan berhasil disimpan.")
        }
    }

    fun saveEvaluationEntry(
        targetEntry: HealthEntryEntity,
        evaluationTime: Long,
        finalSeverity: Int,
        effectNotes: String?,
        moodScore: Int?,
        interventionIds: List<Long> = emptyList(),
        finalSymptomSeverities: Map<Long, Int> = emptyMap()
    ) {
        viewModelScope.launch {
            val initialSeverity = targetEntry.symptomSeverity ?: 0
            val delta = initialSeverity - finalSeverity
            val reactionMinutes = ((evaluationTime - targetEntry.occurrenceTime) / (60 * 1000L)).toInt().coerceAtLeast(0)

            val resolvedInterventions = if (interventionIds.isNotEmpty()) {
                interventionIds.toString()
            } else {
                targetEntry.interventionIdsJson
            }

            val resolvedSymptomSeverities = if (finalSymptomSeverities.isNotEmpty()) {
                AnalyticsEngine.formatSeverityMap(finalSymptomSeverities)
            } else {
                targetEntry.symptomSeveritiesJson
            }

            val evalEntry = HealthEntryEntity(
                occurrenceTime = evaluationTime,
                isEvaluationPhase = true,
                targetEntryId = targetEntry.id,
                symptomId = targetEntry.symptomId,
                symptomIdsJson = targetEntry.symptomIdsJson,
                symptomSeveritiesJson = resolvedSymptomSeverities,
                interventionIdsJson = resolvedInterventions,
                finalSeverity = finalSeverity,
                painDelta = delta,
                reactionTimeMinutes = reactionMinutes,
                effectNotes = effectNotes,
                moodScore = moodScore
            )
            repository.insertEntry(evalEntry)
            showStatus("Evaluasi efek intervensi (Delta: ${if (delta >= 0) "+$delta" else "$delta"}) berhasil dicatat.")
        }
    }

    fun saveFollowUpIntervention(
        targetEntry: HealthEntryEntity,
        previousSeverity: Int,
        followUpTime: Long,
        newInterventionIds: List<Long>,
        newSeverity: Int,
        effectNotes: String?,
        moodScore: Int?,
        finalSymptomSeverities: Map<Long, Int> = emptyMap()
    ) {
        viewModelScope.launch {
            val delta = previousSeverity - newSeverity
            val reactionMinutes = ((followUpTime - targetEntry.occurrenceTime) / (60 * 1000L)).toInt().coerceAtLeast(0)

            val resolvedSymptomSeverities = if (finalSymptomSeverities.isNotEmpty()) {
                AnalyticsEngine.formatSeverityMap(finalSymptomSeverities)
            } else {
                targetEntry.symptomSeveritiesJson
            }

            val followUpEntry = HealthEntryEntity(
                occurrenceTime = followUpTime,
                isEvaluationPhase = true,
                targetEntryId = targetEntry.id,
                symptomId = targetEntry.symptomId,
                symptomIdsJson = targetEntry.symptomIdsJson,
                symptomSeveritiesJson = resolvedSymptomSeverities,
                interventionIdsJson = newInterventionIds.toString(),
                finalSeverity = newSeverity,
                painDelta = delta,
                reactionTimeMinutes = reactionMinutes,
                effectNotes = effectNotes,
                moodScore = moodScore
            )
            repository.insertEntry(followUpEntry)
            showStatus(
                if (newSeverity == 0) "Intervensi lanjutan berhasil! Nyeri tuntas ke 0 (Bebas Nyeri ✓)"
                else "Intervensi lanjutan dicatat (Nyeri: $newSeverity/10, Delta: ${if (delta >= 0) "+$delta" else "$delta"})."
            )
        }
    }

    fun cancelHabitForDay(habitId: Long, timestamp: Long) {
        viewModelScope.launch {
            val targetCal = Calendar.getInstance().apply { timeInMillis = timestamp }
            val y = targetCal.get(Calendar.YEAR)
            val d = targetCal.get(Calendar.DAY_OF_YEAR)

            uiState.value.entries.forEach { entry ->
                val c = Calendar.getInstance().apply { timeInMillis = entry.occurrenceTime }
                if (c.get(Calendar.YEAR) == y && c.get(Calendar.DAY_OF_YEAR) == d) {
                    val habitList = AnalyticsEngine.parseIdList(entry.habitIdsJson).toMutableList()
                    if (habitList.remove(habitId)) {
                        repository.updateEntry(entry.copy(habitIdsJson = habitList.toString()))
                    }
                }
            }
            showStatus("Habit dibatalkan untuk hari ini.")
        }
    }

    fun updateEntry(entry: HealthEntryEntity) {
        viewModelScope.launch {
            repository.updateEntry(entry)
            showStatus("Perubahan entri berhasil disimpan.")
        }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            repository.deleteEntryById(id)
            showStatus("Entri telah dihapus.")
        }
    }

    // --- QUICK ADD MASTER DATA FROM LOGGING FORM ---
    fun quickAddIntervention(name: String, dose: String, category: String, description: String = ""): Long {
        var newId = 0L
        viewModelScope.launch {
            val item = InterventionEntity(name = name, defaultDose = dose, category = category, description = description)
            newId = repository.insertIntervention(item)
            showStatus("Intervensi '$name' ditambahkan ke data master.")
        }
        return newId
    }

    fun quickAddSymptom(name: String, bodyPart: String, description: String = "") {
        viewModelScope.launch {
            val item = SymptomEntity(name = name, defaultBodyPart = bodyPart, description = description)
            repository.insertSymptom(item)
            showStatus("Gejala '$name' ditambahkan ke data master.")
        }
    }

    fun quickAddActivity(name: String, category: String, description: String = "") {
        viewModelScope.launch {
            val item = ActivityEntity(name = name, category = category, description = description)
            repository.insertActivity(item)
            showStatus("Aktivitas '$name' ditambahkan ke data master.")
        }
    }

    // --- FULL MASTER DATA CRUD ---
    fun updateIntervention(item: InterventionEntity) = viewModelScope.launch { repository.updateIntervention(item) }
    fun deleteIntervention(item: InterventionEntity) = viewModelScope.launch { repository.deleteIntervention(item) }

    fun updateSymptom(item: SymptomEntity) = viewModelScope.launch { repository.updateSymptom(item) }
    fun deleteSymptom(item: SymptomEntity) = viewModelScope.launch { repository.deleteSymptom(item) }

    fun updateActivity(item: ActivityEntity) = viewModelScope.launch { repository.updateActivity(item) }
    fun deleteActivity(item: ActivityEntity) = viewModelScope.launch { repository.deleteActivity(item) }

    fun addHabit(name: String, emoji: String = "✨", targetDays: Int = 7, description: String = "") = viewModelScope.launch {
        repository.insertHabit(HabitEntity(name = name, iconEmoji = emoji, targetDaysPerWeek = targetDays, description = description))
        showStatus("Habit '$name' berhasil ditambahkan.")
    }
    fun updateHabit(item: HabitEntity) = viewModelScope.launch { repository.updateHabit(item) }
    fun deleteHabit(item: HabitEntity) = viewModelScope.launch { repository.deleteHabit(item) }

    // --- EXPORT & RESTORE ---
    suspend fun getExportCsv(): String {
        val s = uiState.value
        return AnalyticsEngine.exportEntriesToCsv(s.entries, s.interventions, s.symptoms, s.activities)
    }

    suspend fun getBackupJson(): String {
        return repository.exportToJson(uiState.value.entries)
    }

    fun restoreBackupJson(jsonString: String) {
        viewModelScope.launch {
            val success = repository.importFromJson(jsonString)
            if (success) {
                showStatus("Seluruh data cadangan (Master Data, Catatan & Timeline) berhasil dipulihkan lengkap.")
            } else {
                showStatus("Gagal membaca atau memulihkan file cadangan .ciro.")
            }
        }
    }

    fun clearAllEntries() {
        viewModelScope.launch {
            repository.clearAllEntries()
            showStatus("Seluruh riwayat log berhasil dibersihkan.")
        }
    }

    fun seedSampleData() {
        viewModelScope.launch {
            // 1. Ensure master data exists
            var sympList = repository.getAllSymptoms()
            if (sympList.isEmpty()) {
                repository.insertSymptom(com.example.healthtracker.model.SymptomEntity(name = "Sakit Punggung", defaultBodyPart = "Punggung"))
                repository.insertSymptom(com.example.healthtracker.model.SymptomEntity(name = "Jantung Berdebar", defaultBodyPart = "Dada"))
                repository.insertSymptom(com.example.healthtracker.model.SymptomEntity(name = "Sakit Kepala", defaultBodyPart = "Kepala"))
            }

            var intervList = repository.getAllInterventions()
            if (intervList.isEmpty()) {
                repository.insertIntervention(com.example.healthtracker.model.InterventionEntity(name = "Paracetamol 500mg", defaultDose = "1 tablet", category = "Medis"))
                repository.insertIntervention(com.example.healthtracker.model.InterventionEntity(name = "PMR (Relaksasi Otot)", defaultDose = "15 menit", category = "Non-Medis"))
                repository.insertIntervention(com.example.healthtracker.model.InterventionEntity(name = "Propranolol 10mg", defaultDose = "1 tablet", category = "Medis"))
                repository.insertIntervention(com.example.healthtracker.model.InterventionEntity(name = "Nafas Perut", defaultDose = "10 menit", category = "Non-Medis"))
            }

            var actList = repository.getAllActivities()
            if (actList.isEmpty()) {
                repository.insertActivity(com.example.healthtracker.model.ActivityEntity(name = "Kerja Laptop", category = "Pekerjaan"))
                repository.insertActivity(com.example.healthtracker.model.ActivityEntity(name = "Olahraga Pagi", category = "Fisik"))
                repository.insertActivity(com.example.healthtracker.model.ActivityEntity(name = "Lembur Malam", category = "Pekerjaan"))
                repository.insertActivity(com.example.healthtracker.model.ActivityEntity(name = "Jalan Kaki", category = "Fisik"))
                repository.insertActivity(com.example.healthtracker.model.ActivityEntity(name = "Meditasi Santai", category = "Relaksasi"))
            }

            var habitList = repository.getAllHabits()
            if (habitList.isEmpty()) {
                repository.insertHabit(com.example.healthtracker.model.HabitEntity(name = "Minum Air 2L", iconEmoji = "💧", targetDaysPerWeek = 7))
                repository.insertHabit(com.example.healthtracker.model.HabitEntity(name = "Baca Buku", iconEmoji = "📖", targetDaysPerWeek = 5))
                repository.insertHabit(com.example.healthtracker.model.HabitEntity(name = "Tidur 8 Jam", iconEmoji = "😴", targetDaysPerWeek = 7))
                repository.insertHabit(com.example.healthtracker.model.HabitEntity(name = "Meditasi 10 Mnt", iconEmoji = "🧘", targetDaysPerWeek = 5))
            }

            // Refresh lists
            sympList = repository.getAllSymptoms()
            intervList = repository.getAllInterventions()
            actList = repository.getAllActivities()
            habitList = repository.getAllHabits()

            val punggungId = sympList.firstOrNull { it.name.contains("Punggung", ignoreCase = true) }?.id ?: 1L
            val jantungId = sympList.firstOrNull { it.name.contains("Jantung", ignoreCase = true) }?.id ?: 2L
            val paracetamolId = intervList.firstOrNull { it.name.contains("Paracetamol", ignoreCase = true) }?.id ?: 1L
            val pmrId = intervList.firstOrNull { it.name.contains("PMR", ignoreCase = true) }?.id ?: 2L
            val propranololId = intervList.firstOrNull { it.name.contains("Propranolol", ignoreCase = true) }?.id ?: 3L

            val kerjaId = actList.firstOrNull { it.name.contains("Laptop", ignoreCase = true) }?.id ?: 1L
            val lemburId = actList.firstOrNull { it.name.contains("Lembur", ignoreCase = true) }?.id ?: 2L
            val olahragaId = actList.firstOrNull { it.name.contains("Olahraga", ignoreCase = true) }?.id ?: 3L
            val jalanId = actList.firstOrNull { it.name.contains("Jalan", ignoreCase = true) }?.id ?: 4L

            val airHabit = habitList.firstOrNull { it.name.contains("Air", ignoreCase = true) }?.id ?: 1L
            val bukuHabit = habitList.firstOrNull { it.name.contains("Buku", ignoreCase = true) }?.id ?: 2L
            val tidurHabit = habitList.firstOrNull { it.name.contains("Tidur", ignoreCase = true) }?.id ?: 3L
            val meditasiHabit = habitList.firstOrNull { it.name.contains("Meditasi", ignoreCase = true) }?.id ?: 4L

            val now = System.currentTimeMillis()
            val dayMs = 24 * 3600 * 1000L

            // Clear old entries first
            repository.clearAllEntries()

            // 7 Days of realistic entries
            for (dayOffset in 6 downTo 0) {
                val dayTime = now - (dayOffset * dayMs)
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = dayTime }

                // Pagi (07:30) - Rutinitas
                cal.set(java.util.Calendar.HOUR_OF_DAY, 7)
                cal.set(java.util.Calendar.MINUTE, 30)
                repository.insertEntry(
                    com.example.healthtracker.model.HealthEntryEntity(
                        occurrenceTime = cal.timeInMillis,
                        activityIdsJson = listOf(olahragaId, jalanId).toString(),
                        activityNotes = "Jam 6 - 8 Pagi:\n- Jalan santai 20 menit keliling komplek\n- Sarapan buah dan oatmeal",
                        habitIdsJson = listOf(tidurHabit, airHabit).toString(),
                        moodScore = if (dayOffset % 2 == 0) 5 else 4,
                        moodNotes = "Badan segar, siap mulai hari",
                        isEvaluationPhase = false
                    )
                )

                // Siang (12:45) - Rutinitas
                cal.set(java.util.Calendar.HOUR_OF_DAY, 12)
                cal.set(java.util.Calendar.MINUTE, 45)
                repository.insertEntry(
                    com.example.healthtracker.model.HealthEntryEntity(
                        occurrenceTime = cal.timeInMillis,
                        activityIdsJson = listOf(kerjaId).toString(),
                        activityNotes = "Jam 9 - 12 Siang:\n- Fokus coding & review dokumen",
                        habitIdsJson = listOf(airHabit, bukuHabit).toString(),
                        moodScore = 4,
                        moodNotes = "Konsentrasi terjaga dengan baik",
                        isEvaluationPhase = false
                    )
                )

                // Malam (21:00) - Rutinitas
                cal.set(java.util.Calendar.HOUR_OF_DAY, 21)
                cal.set(java.util.Calendar.MINUTE, 0)
                repository.insertEntry(
                    com.example.healthtracker.model.HealthEntryEntity(
                        occurrenceTime = cal.timeInMillis,
                        activityIdsJson = listOf(jalanId).toString(),
                        activityNotes = "Jam 6 - 12 Malam:\n- Santai bersama keluarga\n- Baca buku 20 halaman",
                        habitIdsJson = if (dayOffset != 3) listOf(bukuHabit, airHabit, meditasiHabit).toString() else listOf(airHabit).toString(),
                        moodScore = if (dayOffset == 4) 2 else 5,
                        moodNotes = if (dayOffset == 4) "Kelelahan setelah hari yang panjang" else "Pikiran tenang, siap istirahat",
                        isEvaluationPhase = false
                    )
                )

                // Specific Clinical Trigger + Drug Evaluation events:
                if (dayOffset == 4) {
                    // 4 days ago: Lembur Malam -> Sakit Punggung (skala 7) + Paracetamol & PMR
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 15)
                    cal.set(java.util.Calendar.MINUTE, 10)
                    val acuteId = repository.insertEntry(
                        com.example.healthtracker.model.HealthEntryEntity(
                            occurrenceTime = cal.timeInMillis,
                            symptomId = punggungId,
                            symptomSeverity = 7,
                            symptomNotes = "Punggung bawah pegal dan kaku tajam saat duduk lama di depan laptop.",
                            interventionIdsJson = listOf(paracetamolId, pmrId).toString(),
                            interventionNotes = "Paracetamol 500mg (1 tablet) + Latihan PMR 15 menit",
                            activityIdsJson = listOf(kerjaId, lemburId).toString(),
                            activityNotes = "Bekerja nonstop sejak jam 10 pagi",
                            moodScore = 2,
                            moodNotes = "Cemas karena deadline",
                            isEvaluationPhase = false
                        )
                    )

                    // 30 minutes later -> Evaluation response (skala 0, delta +7, tuntas bebas nyeri!)
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 15)
                    cal.set(java.util.Calendar.MINUTE, 40)
                    repository.insertEntry(
                        com.example.healthtracker.model.HealthEntryEntity(
                            occurrenceTime = cal.timeInMillis,
                            isEvaluationPhase = true,
                            targetEntryId = acuteId,
                            symptomId = punggungId,
                            finalSeverity = 0,
                            painDelta = 7,
                            reactionTimeMinutes = 30,
                            effectNotes = "Nyeri hilang total (0/10), otot punggung bawah rileks berkat kombinasi PMR dan paracetamol.",
                            moodScore = 5
                        )
                    )
                }

                if (dayOffset == 2) {
                    // 2 days ago: Kerja Laptop -> Jantung Berdebar (skala 6) + Propranolol
                    val nafasPerutId = intervList.firstOrNull { it.name.contains("Nafas", ignoreCase = true) }?.id ?: pmrId
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 10)
                    cal.set(java.util.Calendar.MINUTE, 20)
                    val acuteId = repository.insertEntry(
                        com.example.healthtracker.model.HealthEntryEntity(
                            occurrenceTime = cal.timeInMillis,
                            symptomId = jantungId,
                            symptomSeverity = 6,
                            symptomNotes = "Jantung berdetak kencang dan dada agak sesak saat presentasi online.",
                            interventionIdsJson = listOf(propranololId, nafasPerutId).toString(),
                            interventionNotes = "Propranolol 10mg + Latihan Nafas Perut",
                            activityIdsJson = listOf(kerjaId).toString(),
                            activityNotes = "Rapat kerja bertekanan tinggi",
                            moodScore = 2,
                            moodNotes = "Stres dan gelisah",
                            isEvaluationPhase = false
                        )
                    )

                    // 25 minutes later -> Evaluation response (skala 0, delta +6, tuntas bebas nyeri!)
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 10)
                    cal.set(java.util.Calendar.MINUTE, 45)
                    repository.insertEntry(
                        com.example.healthtracker.model.HealthEntryEntity(
                            occurrenceTime = cal.timeInMillis,
                            isEvaluationPhase = true,
                            targetEntryId = acuteId,
                            symptomId = jantungId,
                            finalSeverity = 0,
                            painDelta = 6,
                            reactionTimeMinutes = 25,
                            effectNotes = "Detak jantung normal 68 bpm, sensasi berdebar tuntas hilang (skala 0).",
                            moodScore = 5
                        )
                    )
                }

                if (dayOffset == 1) {
                    // 1 day ago: Olahraga -> Sakit Kepala (skala 5) + Paracetamol (Tunggal)
                    val kepalaId = sympList.firstOrNull { it.name.contains("Kepala", ignoreCase = true) }?.id ?: punggungId
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 16)
                    cal.set(java.util.Calendar.MINUTE, 0)
                    val acuteId = repository.insertEntry(
                        com.example.healthtracker.model.HealthEntryEntity(
                            occurrenceTime = cal.timeInMillis,
                            symptomId = kepalaId,
                            symptomSeverity = 5,
                            symptomNotes = "Kepala terasa tegang dan berdenyut setelah aktivitas seharian.",
                            interventionIdsJson = listOf(paracetamolId).toString(),
                            interventionNotes = "Paracetamol 500mg (1 tablet)",
                            activityIdsJson = listOf(olahragaId).toString(),
                            activityNotes = "Aktivitas di luar ruangan",
                            moodScore = 3,
                            moodNotes = "Agak lelah",
                            isEvaluationPhase = false
                        )
                    )

                    // 40 minutes later -> Evaluation response (skala 0, delta +5, tuntas!)
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 16)
                    cal.set(java.util.Calendar.MINUTE, 40)
                    repository.insertEntry(
                        com.example.healthtracker.model.HealthEntryEntity(
                            occurrenceTime = cal.timeInMillis,
                            isEvaluationPhase = true,
                            targetEntryId = acuteId,
                            symptomId = kepalaId,
                            finalSeverity = 0,
                            painDelta = 5,
                            reactionTimeMinutes = 40,
                            effectNotes = "Pusing reda sepenuhnya (skala 0), kepala kembali ringan.",
                            moodScore = 4
                        )
                    )
                }
            }

            showStatus("Data sampel klinis 7 hari berhasil dimuat secara lengkap!")
        }
    }
}
