package com.example.healthtracker.repository

import com.example.healthtracker.database.HealthDao
import com.example.healthtracker.model.ActivityEntity
import com.example.healthtracker.model.HabitEntity
import com.example.healthtracker.model.HealthEntryEntity
import com.example.healthtracker.model.InterventionEntity
import com.example.healthtracker.model.SymptomEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class HealthRepository(private val dao: HealthDao) {

    val allEntries: Flow<List<HealthEntryEntity>> = dao.getAllEntriesFlow()
    val allInterventions: Flow<List<InterventionEntity>> = dao.getAllInterventionsFlow()
    val allSymptoms: Flow<List<SymptomEntity>> = dao.getAllSymptomsFlow()
    val allActivities: Flow<List<ActivityEntity>> = dao.getAllActivitiesFlow()
    val allHabits: Flow<List<HabitEntity>> = dao.getAllHabitsFlow()

    suspend fun insertEntry(entry: HealthEntryEntity): Long = dao.insertEntry(entry)
    suspend fun updateEntry(entry: HealthEntryEntity) = dao.updateEntry(entry)
    suspend fun deleteEntry(entry: HealthEntryEntity) = dao.deleteEntry(entry)
    suspend fun deleteEntryById(id: Long) = dao.deleteEntryById(id)
    suspend fun getEntryById(id: Long): HealthEntryEntity? = dao.getEntryById(id)

    // Master Data CRUD
    suspend fun insertIntervention(item: InterventionEntity) = dao.insertIntervention(item)
    suspend fun updateIntervention(item: InterventionEntity) = dao.updateIntervention(item)
    suspend fun deleteIntervention(item: InterventionEntity) = dao.deleteIntervention(item)

    suspend fun insertSymptom(item: SymptomEntity) = dao.insertSymptom(item)
    suspend fun updateSymptom(item: SymptomEntity) = dao.updateSymptom(item)
    suspend fun deleteSymptom(item: SymptomEntity) = dao.deleteSymptom(item)

    suspend fun insertActivity(item: ActivityEntity) = dao.insertActivity(item)
    suspend fun updateActivity(item: ActivityEntity) = dao.updateActivity(item)
    suspend fun deleteActivity(item: ActivityEntity) = dao.deleteActivity(item)

    suspend fun insertHabit(item: HabitEntity) = dao.insertHabit(item)
    suspend fun updateHabit(item: HabitEntity) = dao.updateHabit(item)
    suspend fun deleteHabit(item: HabitEntity) = dao.deleteHabit(item)

    suspend fun getAllInterventions(): List<InterventionEntity> = dao.getAllInterventions()
    suspend fun getAllSymptoms(): List<SymptomEntity> = dao.getAllSymptoms()
    suspend fun getAllActivities(): List<ActivityEntity> = dao.getAllActivities()
    suspend fun getAllHabits(): List<HabitEntity> = dao.getAllHabits()

    // Backup to JSON string
    suspend fun exportToJson(entriesParam: List<HealthEntryEntity>? = null): String {
        val root = JSONObject()
        val interventions = dao.getAllInterventions()
        val symptoms = dao.getAllSymptoms()
        val activities = dao.getAllActivities()
        val habits = dao.getAllHabits()
        // Always query database directly to guarantee all entries are exported, fallback to entriesParam if non-empty
        val dbEntries = dao.getAllEntries()
        val entries = if (dbEntries.isNotEmpty()) dbEntries else (entriesParam ?: emptyList())

        val intervArray = JSONArray()
        for (i in interventions) {
            val obj = JSONObject().apply {
                put("id", i.id)
                put("name", i.name)
                put("defaultDose", i.defaultDose)
                put("category", i.category)
                put("iconName", i.iconName)
                put("description", i.description)
            }
            intervArray.put(obj)
        }

        val sympArray = JSONArray()
        for (s in symptoms) {
            val obj = JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("iconName", s.iconName)
                put("defaultBodyPart", s.defaultBodyPart)
                put("description", s.description)
            }
            sympArray.put(obj)
        }

        val actArray = JSONArray()
        for (a in activities) {
            val obj = JSONObject().apply {
                put("id", a.id)
                put("name", a.name)
                put("iconName", a.iconName)
                put("category", a.category)
                put("description", a.description)
            }
            actArray.put(obj)
        }

        val habitArray = JSONArray()
        for (h in habits) {
            val obj = JSONObject().apply {
                put("id", h.id)
                put("name", h.name)
                put("iconEmoji", h.iconEmoji)
                put("targetDaysPerWeek", h.targetDaysPerWeek)
                put("description", h.description)
            }
            habitArray.put(obj)
        }

        val entriesArray = JSONArray()
        for (e in entries) {
            val obj = JSONObject().apply {
                put("id", e.id)
                put("occurrenceTime", e.occurrenceTime)
                put("createdAt", e.createdAt)
                put("symptomId", e.symptomId ?: JSONObject.NULL)
                put("symptomIdsJson", e.symptomIdsJson)
                put("symptomSeveritiesJson", e.symptomSeveritiesJson)
                put("symptomSeverity", e.symptomSeverity ?: JSONObject.NULL)
                put("symptomNotes", e.symptomNotes ?: JSONObject.NULL)
                put("interventionIdsJson", e.interventionIdsJson)
                put("interventionNotes", e.interventionNotes ?: JSONObject.NULL)
                put("activityIdsJson", e.activityIdsJson)
                put("activityNotes", e.activityNotes ?: JSONObject.NULL)
                put("habitIdsJson", e.habitIdsJson)
                put("moodScore", e.moodScore ?: JSONObject.NULL)
                put("moodNotes", e.moodNotes ?: JSONObject.NULL)
                put("isEvaluationPhase", e.isEvaluationPhase)
                put("targetEntryId", e.targetEntryId ?: JSONObject.NULL)
                put("finalSeverity", e.finalSeverity ?: JSONObject.NULL)
                put("painDelta", e.painDelta ?: JSONObject.NULL)
                put("reactionTimeMinutes", e.reactionTimeMinutes ?: JSONObject.NULL)
                put("effectNotes", e.effectNotes ?: JSONObject.NULL)
            }
            entriesArray.put(obj)
        }

        root.put("version", 3)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("interventions", intervArray)
        root.put("symptoms", sympArray)
        root.put("activities", actArray)
        root.put("habits", habitArray)
        root.put("entries", entriesArray)

        return root.toString(2)
    }

    // Helper to find a JSONArray matching potential keys
    private fun findJsonArray(root: JSONObject, vararg keys: String): JSONArray? {
        for (k in keys) {
            if (root.has(k) && !root.isNull(k)) {
                val value = root.get(k)
                if (value is JSONArray) return value
            }
        }
        return null
    }

    private fun optStringTrim(obj: JSONObject, vararg keys: String): String? {
        for (k in keys) {
            if (obj.has(k) && !obj.isNull(k)) {
                val str = obj.optString(k, "").trim()
                if (str.isNotBlank()) return str
            }
        }
        return null
    }

    // Restore from JSON string
    suspend fun importFromJson(jsonString: String): Boolean {
        return try {
            val trimmed = jsonString.trim().removePrefix("\uFEFF") // Remove BOM if present
            val root = JSONObject(trimmed)

            // Cache existing master items so if backup lacks "description", we don't lose current descriptions
            val existingInterventions = dao.getAllInterventions().associateBy { it.id }
            val existingSymptoms = dao.getAllSymptoms().associateBy { it.id }
            val existingActivities = dao.getAllActivities().associateBy { it.id }
            val existingHabits = dao.getAllHabits().associateBy { it.id }

            // 1. Interventions Master
            val intervArray = findJsonArray(root, "interventions", "intervention", "intervensi")
            if (intervArray != null && intervArray.length() > 0) {
                dao.clearAllInterventions()
                for (i in 0 until intervArray.length()) {
                    val obj = intervArray.optJSONObject(i) ?: continue
                    val id = obj.optLong("id", 0L)
                    val name = optStringTrim(obj, "name", "nama", "title") ?: "Intervensi #${i + 1}"
                    val dose = optStringTrim(obj, "defaultDose", "dose", "dosis") ?: ""
                    val cat = optStringTrim(obj, "category", "kategori") ?: "Medis"
                    val icon = optStringTrim(obj, "iconName", "icon") ?: "Medication"
                    val desc = optStringTrim(obj, "description", "deskripsi", "catatan", "notes")
                        ?: existingInterventions[id]?.description
                        ?: ""

                    val item = InterventionEntity(
                        id = id,
                        name = name,
                        defaultDose = dose,
                        category = cat,
                        iconName = icon,
                        description = desc
                    )
                    dao.insertIntervention(item)
                }
            }

            // 2. Symptoms Master
            val sympArray = findJsonArray(root, "symptoms", "symptom", "gejala")
            if (sympArray != null && sympArray.length() > 0) {
                dao.clearAllSymptoms()
                for (i in 0 until sympArray.length()) {
                    val obj = sympArray.optJSONObject(i) ?: continue
                    val id = obj.optLong("id", 0L)
                    val name = optStringTrim(obj, "name", "nama", "title") ?: "Gejala #${i + 1}"
                    val icon = optStringTrim(obj, "iconName", "icon") ?: "Warning"
                    val bodyPart = optStringTrim(obj, "defaultBodyPart", "bodyPart", "bagianTubuh") ?: "Kepala"
                    val desc = optStringTrim(obj, "description", "deskripsi", "catatan", "notes")
                        ?: existingSymptoms[id]?.description
                        ?: ""

                    val item = SymptomEntity(
                        id = id,
                        name = name,
                        iconName = icon,
                        defaultBodyPart = bodyPart,
                        description = desc
                    )
                    dao.insertSymptom(item)
                }
            }

            // 3. Activities Master
            val actArray = findJsonArray(root, "activities", "activity", "aktivitas", "aktifitas")
            if (actArray != null && actArray.length() > 0) {
                dao.clearAllActivities()
                for (i in 0 until actArray.length()) {
                    val obj = actArray.optJSONObject(i) ?: continue
                    val id = obj.optLong("id", 0L)
                    val name = optStringTrim(obj, "name", "nama", "title") ?: "Aktivitas #${i + 1}"
                    val icon = optStringTrim(obj, "iconName", "icon") ?: "FitnessCenter"
                    val cat = optStringTrim(obj, "category", "kategori") ?: "Harian"
                    val desc = optStringTrim(obj, "description", "deskripsi", "catatan", "notes")
                        ?: existingActivities[id]?.description
                        ?: ""

                    val item = ActivityEntity(
                        id = id,
                        name = name,
                        iconName = icon,
                        category = cat,
                        description = desc
                    )
                    dao.insertActivity(item)
                }
            }

            // 4. Habits Master
            val habitsArray = findJsonArray(root, "habits", "habit", "kebiasaan")
            if (habitsArray != null && habitsArray.length() > 0) {
                dao.clearAllHabits()
                for (i in 0 until habitsArray.length()) {
                    val obj = habitsArray.optJSONObject(i) ?: continue
                    val id = obj.optLong("id", 0L)
                    val name = optStringTrim(obj, "name", "nama", "title") ?: "Habit #${i + 1}"
                    val emoji = optStringTrim(obj, "iconEmoji", "emoji", "icon") ?: "✨"
                    val target = if (obj.has("targetDaysPerWeek")) obj.optInt("targetDaysPerWeek", 7) else obj.optInt("target", 7)
                    val desc = optStringTrim(obj, "description", "deskripsi", "catatan", "notes")
                        ?: existingHabits[id]?.description
                        ?: ""

                    val habit = HabitEntity(
                        id = id,
                        name = name,
                        iconEmoji = emoji,
                        targetDaysPerWeek = target,
                        description = desc
                    )
                    dao.insertHabit(habit)
                }
            }

            // 5. Entries (Timeline)
            val entriesArray = findJsonArray(root, "entries", "entry", "timeline", "logs", "riwayat")
            if (entriesArray != null && entriesArray.length() > 0) {
                dao.clearAllEntries()
                for (i in 0 until entriesArray.length()) {
                    val obj = entriesArray.optJSONObject(i) ?: continue
                    val id = obj.optLong("id", 0L)
                    val occTime = if (obj.has("occurrenceTime")) obj.optLong("occurrenceTime", System.currentTimeMillis()) else obj.optLong("timestamp", System.currentTimeMillis())
                    val createdAt = obj.optLong("createdAt", occTime)

                    val sId = if (obj.has("symptomId") && !obj.isNull("symptomId")) obj.getLong("symptomId") else null
                    val sIdsJson = when {
                        obj.has("symptomIdsJson") && !obj.isNull("symptomIdsJson") && obj.optString("symptomIdsJson").isNotBlank() ->
                            obj.getString("symptomIdsJson")
                        obj.has("symptomIds") && obj.get("symptomIds") is JSONArray ->
                            obj.getJSONArray("symptomIds").toString()
                        sId != null -> "[$sId]"
                        else -> "[]"
                    }

                    val sSev = if (obj.has("symptomSeverity") && !obj.isNull("symptomSeverity")) obj.getInt("symptomSeverity") else if (obj.has("severity") && !obj.isNull("severity")) obj.getInt("severity") else null
                    val sSevsJson = when {
                        obj.has("symptomSeveritiesJson") && !obj.isNull("symptomSeveritiesJson") && obj.optString("symptomSeveritiesJson").isNotBlank() ->
                            obj.getString("symptomSeveritiesJson")
                        obj.has("symptomSeverities") && obj.get("symptomSeverities") is JSONObject ->
                            obj.getJSONObject("symptomSeverities").toString()
                        sId != null && sSev != null -> "{\"$sId\": $sSev}"
                        else -> "{}"
                    }

                    val intervIdsJson = when {
                        obj.has("interventionIdsJson") && !obj.isNull("interventionIdsJson") && obj.optString("interventionIdsJson").isNotBlank() ->
                            obj.getString("interventionIdsJson")
                        obj.has("interventionIds") && obj.get("interventionIds") is JSONArray ->
                            obj.getJSONArray("interventionIds").toString()
                        obj.has("interventionId") && !obj.isNull("interventionId") ->
                            "[${obj.getLong("interventionId")}]"
                        else -> "[]"
                    }

                    val actIdsJson = when {
                        obj.has("activityIdsJson") && !obj.isNull("activityIdsJson") && obj.optString("activityIdsJson").isNotBlank() ->
                            obj.getString("activityIdsJson")
                        obj.has("activityIds") && obj.get("activityIds") is JSONArray ->
                            obj.getJSONArray("activityIds").toString()
                        obj.has("activityId") && !obj.isNull("activityId") ->
                            "[${obj.getLong("activityId")}]"
                        else -> "[]"
                    }

                    val habitIdsJson = when {
                        obj.has("habitIdsJson") && !obj.isNull("habitIdsJson") && obj.optString("habitIdsJson").isNotBlank() ->
                            obj.getString("habitIdsJson")
                        obj.has("habitIds") && obj.get("habitIds") is JSONArray ->
                            obj.getJSONArray("habitIds").toString()
                        else -> "[]"
                    }

                    val moodScore = if (obj.has("moodScore") && !obj.isNull("moodScore")) obj.getInt("moodScore") else if (obj.has("mood") && !obj.isNull("mood")) obj.getInt("mood") else null
                    val isEval = obj.optBoolean("isEvaluationPhase", false)
                    val targetEntryId = if (obj.has("targetEntryId") && !obj.isNull("targetEntryId")) obj.getLong("targetEntryId") else null
                    val finalSeverity = if (obj.has("finalSeverity") && !obj.isNull("finalSeverity")) obj.getInt("finalSeverity") else null
                    val painDelta = if (obj.has("painDelta") && !obj.isNull("painDelta")) obj.getInt("painDelta") else null
                    val reactionTime = if (obj.has("reactionTimeMinutes") && !obj.isNull("reactionTimeMinutes")) obj.getInt("reactionTimeMinutes") else null

                    val entry = HealthEntryEntity(
                        id = id,
                        occurrenceTime = occTime,
                        createdAt = createdAt,
                        symptomId = sId,
                        symptomIdsJson = sIdsJson,
                        symptomSeveritiesJson = sSevsJson,
                        symptomSeverity = sSev,
                        symptomNotes = optStringTrim(obj, "symptomNotes", "catatanGejala"),
                        interventionIdsJson = intervIdsJson,
                        interventionNotes = optStringTrim(obj, "interventionNotes", "catatanIntervensi"),
                        activityIdsJson = actIdsJson,
                        activityNotes = optStringTrim(obj, "activityNotes", "catatanAktivitas"),
                        habitIdsJson = habitIdsJson,
                        moodScore = moodScore,
                        moodNotes = optStringTrim(obj, "moodNotes", "catatanMood"),
                        isEvaluationPhase = isEval,
                        targetEntryId = targetEntryId,
                        finalSeverity = finalSeverity,
                        painDelta = painDelta,
                        reactionTimeMinutes = reactionTime,
                        effectNotes = optStringTrim(obj, "effectNotes", "catatanEfek", "catatanEvaluasi")
                    )
                    dao.insertEntry(entry)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun clearAllEntries() = dao.clearAllEntries()
    suspend fun clearAllInterventions() = dao.clearAllInterventions()
    suspend fun clearAllSymptoms() = dao.clearAllSymptoms()
    suspend fun clearAllActivities() = dao.clearAllActivities()
    suspend fun clearAllHabits() = dao.clearAllHabits()
}
