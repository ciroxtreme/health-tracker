package com.example.healthtracker.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "interventions")
data class InterventionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val defaultDose: String = "",
    val category: String = "Medis", // "Medis" or "Non-medis"
    val iconName: String = "Medication",
    val description: String = ""
)

@Entity(tableName = "symptoms")
data class SymptomEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val iconName: String = "Warning",
    val defaultBodyPart: String = "Kepala",
    val description: String = ""
)

@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val iconName: String = "FitnessCenter",
    val category: String = "Harian",
    val description: String = ""
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val iconEmoji: String = "✨",
    val targetDaysPerWeek: Int = 7,
    val description: String = ""
)

@Entity(tableName = "health_entries")
data class HealthEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val occurrenceTime: Long, // Flexible backdating timestamp in ms
    val createdAt: Long = System.currentTimeMillis(),
    
    // Symptom data (supports single or multiple symptoms)
    val symptomId: Long? = null,
    val symptomIdsJson: String = "[]",
    val symptomSeveritiesJson: String = "{}", // Map of symptomId -> severity (0-10)
    val symptomSeverity: Int? = null, // 0-10: 0 Bebas Nyeri, 1-3 Mild, 4-6 Moderate, 7-10 Severe
    val symptomNotes: String? = null,
    
    // Interventions taken during this entry (JSON array of Long IDs or comma-separated)
    val interventionIdsJson: String = "[]",
    val interventionNotes: String? = null,
    
    // Activities logged (JSON array of Long IDs or comma-separated)
    val activityIdsJson: String = "[]",
    val activityNotes: String? = null,

    // Habits checked during this entry (JSON array of Long IDs)
    val habitIdsJson: String = "[]",
    
    // Mood & Mental Capacity (1 to 5)
    val moodScore: Int? = null, // 1: Habis Total/Krisis, 2: Tertekan, 3: Netral, 4: Baik, 5: Tenang & Resilien
    val moodNotes: String? = null,
    
    // Delayed Evaluation / Effect phase (Follow-up)
    val isEvaluationPhase: Boolean = false,
    val targetEntryId: Long? = null, // Links to acute entry with symptom & intervention
    val finalSeverity: Int? = null, // Post-intervention pain level (0-10)
    val painDelta: Int? = null, // Initial severity - Final severity (e.g. 8 - 4 = +4 improvement)
    val reactionTimeMinutes: Int? = null, // Duration from intervention to evaluation
    val effectNotes: String? = null
)
