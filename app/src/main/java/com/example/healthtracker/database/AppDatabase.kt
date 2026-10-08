package com.example.healthtracker.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.healthtracker.model.ActivityEntity
import com.example.healthtracker.model.HabitEntity
import com.example.healthtracker.model.HealthEntryEntity
import com.example.healthtracker.model.InterventionEntity
import com.example.healthtracker.model.SymptomEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        InterventionEntity::class,
        SymptomEntity::class,
        ActivityEntity::class,
        HabitEntity::class,
        HealthEntryEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun healthDao(): HealthDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "health_tracker_pro.db"
                ).fallbackToDestructiveMigration()
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed initial master data and clinical trial records in background
                        CoroutineScope(Dispatchers.IO).launch {
                            val dao = getInstance(context).healthDao()
                            seedInitialData(dao)
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun seedInitialData(dao: HealthDao) {
            val medInterventions = listOf(
                InterventionEntity(1, "Propranolol 10mg", "1 tablet", "Medis", "Pill"),
                InterventionEntity(2, "Efisol Lozenges", "1 tablet isap", "Medis", "Pill"),
                InterventionEntity(3, "Muscle Relaxation (PMR)", "15 menit", "Non-medis", "SelfImprovement"),
                InterventionEntity(4, "Latihan Nafas Dalam (4-7-8)", "10 menit", "Non-medis", "Air"),
                InterventionEntity(5, "Kompres Hangat Tengkuk", "20 menit", "Non-medis", "HotTub"),
                InterventionEntity(6, "Paracetamol 500mg", "1 tablet", "Medis", "Pill")
            )
            dao.insertInterventions(medInterventions)

            val symptoms = listOf(
                SymptomEntity(1, "Sakit Kepala Tegang", "Psychology", "Kepala"),
                SymptomEntity(2, "Kekakuan & Nyeri Tengkuk", "FitnessCenter", "Leher"),
                SymptomEntity(3, "Refluks / Gerd Perih Dada", "LocalHospital", "Dada"),
                SymptomEntity(4, "Palpitasi / Jantung Berdebar", "Favorite", "Dada")
            )
            dao.insertSymptoms(symptoms)

            val activities = listOf(
                ActivityEntity(1, "Musik Keras / Bising", "VolumeUp", "Lingkungan"),
                ActivityEntity(2, "Duduk Lama > 3 Jam", "Weekend", "Kerja"),
                ActivityEntity(3, "Kurang Tidur (< 5 Jam)", "Bedtime", "Fisiologis"),
                ActivityEntity(4, "Konsumsi Kopi Dobel", "LocalCafe", "Diet"),
                ActivityEntity(5, "Olahraga Ringan / Jalan", "DirectionsRun", "Fisik")
            )
            dao.insertActivities(activities)

            val habits = listOf(
                HabitEntity(1, "Gantung Diri / Streching", "🧘", 7),
                HabitEntity(2, "Latihan Relaksasi / PMR", "🧎", 7),
                HabitEntity(3, "Minum Air 2L", "💧", 7),
                HabitEntity(4, "Tidur Teratur", "😴", 7),
                HabitEntity(5, "Jalan Santai 15 Menit", "🚶", 7)
            )
            dao.insertHabits(habits)

            // Seed realistic N-of-1 trial entries across the past 7 days to demonstrate all analytics modules
            val now = System.currentTimeMillis()
            val hourMs = 3600 * 1000L
            val dayMs = 24 * hourMs

            // Day -5: Activity Duduk Lama -> 2 hours later Head Pain -> Propranolol + Relaxation -> Evaluated 1.5h later (Delta = +5)
            val d5Time = now - 5 * dayMs
            val d5ActId = dao.insertEntry(
                HealthEntryEntity(
                    occurrenceTime = d5Time,
                    activityIdsJson = "[2]",
                    activityNotes = "Coding maraton 4 jam tanpa jeda",
                    moodScore = 2,
                    moodNotes = "Stres deadline proyek"
                )
            )
            val d5AcuteId = dao.insertEntry(
                HealthEntryEntity(
                    occurrenceTime = d5Time + 2 * hourMs,
                    symptomId = 1,
                    symptomSeverity = 8,
                    symptomNotes = "Kepala berdenyut tegang di kedua pelipis",
                    interventionIdsJson = "[1, 3]",
                    interventionNotes = "Minum Propranolol + relaksasi otot",
                    moodScore = 2
                )
            )
            dao.insertEntry(
                HealthEntryEntity(
                    occurrenceTime = d5Time + 3 * hourMs + 30 * 60 * 1000L,
                    isEvaluationPhase = true,
                    targetEntryId = d5AcuteId,
                    symptomId = 1,
                    finalSeverity = 3,
                    painDelta = 5,
                    reactionTimeMinutes = 90,
                    effectNotes = "Nyeri berkurang signifikan, kepala terasa jauh lebih ringan",
                    moodScore = 4
                )
            )

            // Day -4: Kurang Tidur -> Sakit Kepala Tegang -> Propranolol only -> Evaluated (Delta = +3)
            val d4Time = now - 4 * dayMs
            dao.insertEntry(
                HealthEntryEntity(
                    occurrenceTime = d4Time,
                    activityIdsJson = "[3]",
                    activityNotes = "Tidur jam 2 pagi bangun jam 6",
                    moodScore = 1,
                    moodNotes = "Kapasitas mental habis total"
                )
            )
            val d4AcuteId = dao.insertEntry(
                HealthEntryEntity(
                    occurrenceTime = d4Time + 3 * hourMs,
                    symptomId = 1,
                    symptomSeverity = 7,
                    symptomNotes = "Mata berat, kepala belakang tegang",
                    interventionIdsJson = "[1]",
                    interventionNotes = "Propranolol 10mg",
                    moodScore = 2
                )
            )
            dao.insertEntry(
                HealthEntryEntity(
                    occurrenceTime = d4Time + 4 * hourMs + 15 * 60 * 1000L,
                    isEvaluationPhase = true,
                    targetEntryId = d4AcuteId,
                    symptomId = 1,
                    finalSeverity = 4,
                    painDelta = 3,
                    reactionTimeMinutes = 75,
                    effectNotes = "Denyut mereda perlahan",
                    moodScore = 3
                )
            )

            // Day -3: Duduk Lama + Kopi -> Nyeri Tengkuk -> Muscle Relaxation + Kompres -> Evaluated (Delta = +4)
            val d3Time = now - 3 * dayMs
            dao.insertEntry(
                HealthEntryEntity(
                    occurrenceTime = d3Time,
                    activityIdsJson = "[2, 4]",
                    activityNotes = "Meeting zoom beruntun minum espresso ganda",
                    moodScore = 2
                )
            )
            val d3AcuteId = dao.insertEntry(
                HealthEntryEntity(
                    occurrenceTime = d3Time + 2 * hourMs,
                    symptomId = 2,
                    symptomSeverity = 7,
                    symptomNotes = "Bahu kaku dan leher tidak bisa menoleh penuh",
                    interventionIdsJson = "[3, 5]",
                    interventionNotes = "PMR + Kompres Hangat Tengkuk",
                    moodScore = 3
                )
            )
            dao.insertEntry(
                HealthEntryEntity(
                    occurrenceTime = d3Time + 3 * hourMs + 10 * 60 * 1000L,
                    isEvaluationPhase = true,
                    targetEntryId = d3AcuteId,
                    symptomId = 2,
                    finalSeverity = 3,
                    painDelta = 4,
                    reactionTimeMinutes = 70,
                    effectNotes = "Otot bahu terasa melemas",
                    moodScore = 4
                )
            )

            // Day -2: Musik Keras -> Palpitasi -> Nafas 4-7-8 -> Evaluated (Delta = +3)
            val d2Time = now - 2 * dayMs
            dao.insertEntry(
                HealthEntryEntity(
                    occurrenceTime = d2Time,
                    activityIdsJson = "[1]",
                    activityNotes = "Suara speaker acara sebelah sangat keras",
                    moodScore = 2
                )
            )
            val d2AcuteId = dao.insertEntry(
                HealthEntryEntity(
                    occurrenceTime = d2Time + 1 * hourMs,
                    symptomId = 4,
                    symptomSeverity = 6,
                    symptomNotes = "Dada berdebar kencang saat bising",
                    interventionIdsJson = "[4]",
                    interventionNotes = "Latihan Nafas Dalam 4-7-8",
                    moodScore = 2
                )
            )
            dao.insertEntry(
                HealthEntryEntity(
                    occurrenceTime = d2Time + 1 * hourMs + 45 * 60 * 1000L,
                    isEvaluationPhase = true,
                    targetEntryId = d2AcuteId,
                    symptomId = 4,
                    finalSeverity = 3,
                    painDelta = 3,
                    reactionTimeMinutes = 45,
                    effectNotes = "Detak kembali ritmis dan tenang",
                    moodScore = 4
                )
            )

            // Day -1: Duduk Lama -> Sakit Kepala Tegang -> Propranolol + Relaxation -> Evaluated (Delta = +5)
            val d1Time = now - 1 * dayMs
            dao.insertEntry(
                HealthEntryEntity(
                    occurrenceTime = d1Time,
                    activityIdsJson = "[2]",
                    activityNotes = "Revisi laporan tanpa jeda",
                    moodScore = 2
                )
            )
            val d1AcuteId = dao.insertEntry(
                HealthEntryEntity(
                    occurrenceTime = d1Time + 2 * hourMs + 30 * 60 * 1000L,
                    symptomId = 1,
                    symptomSeverity = 8,
                    symptomNotes = "Sakit kepala tegang hebat",
                    interventionIdsJson = "[1, 3]",
                    interventionNotes = "Propranolol + Relaksasi PMR",
                    moodScore = 2
                )
            )
            dao.insertEntry(
                HealthEntryEntity(
                    occurrenceTime = d1Time + 4 * hourMs,
                    isEvaluationPhase = true,
                    targetEntryId = d1AcuteId,
                    symptomId = 1,
                    finalSeverity = 2,
                    painDelta = 6,
                    reactionTimeMinutes = 90,
                    effectNotes = "Sangat reda, mampu beristirahat dengan baik",
                    moodScore = 5
                )
            )
        }
    }
}
