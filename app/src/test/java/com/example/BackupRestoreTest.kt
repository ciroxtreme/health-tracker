package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.healthtracker.database.AppDatabase
import com.example.healthtracker.model.*
import com.example.healthtracker.repository.HealthRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupRestoreTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: HealthRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = HealthRepository(db.healthDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testExportAndRestoreAllMasterDataAndNotes() = runBlocking {
        // Insert test master items with notes/description
        val intervId = repository.insertIntervention(
            InterventionEntity(
                name = "Ibuprofen 400mg",
                defaultDose = "1 kaplet",
                category = "Medis",
                iconName = "Medication",
                description = "- Diminum sesudah makan\n- Efektif untuk migrain"
            )
        )
        val sympId = repository.insertSymptom(
            SymptomEntity(
                name = "Migrain Kanan",
                iconName = "Warning",
                defaultBodyPart = "Kepala",
                description = "- Berdenyut di pelipis kanan\n- Sensitif cahaya"
            )
        )
        val actId = repository.insertActivity(
            ActivityEntity(
                name = "Bekerja di Depan Layar",
                iconName = "Computer",
                category = "Kerja",
                description = "- Monitor tanpa kacamata anti radiasi"
            )
        )
        val habitId = repository.insertHabit(
            HabitEntity(
                name = "Peregangan Otot",
                iconEmoji = "🧘",
                targetDaysPerWeek = 5,
                description = "- Dilakukan tiap pagi 10 menit"
            )
        )

        // Insert acute entry with multi symptoms and notes
        val acuteEntry = HealthEntryEntity(
            id = 101,
            occurrenceTime = 1700000000000L,
            symptomId = sympId,
            symptomIdsJson = "[$sympId]",
            symptomSeveritiesJson = "{\"$sympId\": 8}",
            symptomSeverity = 8,
            symptomNotes = "Nyeri hebat",
            interventionIdsJson = "[$intervId]",
            interventionNotes = "Minum 1 kaplet",
            activityIdsJson = "[$actId]",
            habitIdsJson = "[$habitId]",
            moodScore = 2,
            isEvaluationPhase = false
        )
        repository.insertEntry(acuteEntry)

        // Insert evaluation entry linked to acuteEntry
        val evalEntry = HealthEntryEntity(
            id = 102,
            occurrenceTime = 1700003600000L,
            isEvaluationPhase = true,
            targetEntryId = 101,
            symptomId = sympId,
            finalSeverity = 2,
            painDelta = 6,
            reactionTimeMinutes = 60,
            effectNotes = "Nyeri mereda jauh"
        )
        repository.insertEntry(evalEntry)

        // Export to JSON
        val allEntries = listOf(acuteEntry, evalEntry)
        val backupJson = repository.exportToJson(allEntries)

        assertTrue("JSON must contain interventions", backupJson.contains("Ibuprofen 400mg"))
        assertTrue("JSON must contain intervention description", backupJson.contains("Diminum sesudah makan"))
        assertTrue("JSON must contain symptom description", backupJson.contains("Sensitif cahaya"))
        assertTrue("JSON must contain activity description", backupJson.contains("anti radiasi"))
        assertTrue("JSON must contain habit description", backupJson.contains("tiap pagi 10 menit"))
        assertTrue("JSON must contain symptomIdsJson", backupJson.contains("symptomIdsJson"))
        assertTrue("JSON must contain symptomSeveritiesJson", backupJson.contains("symptomSeveritiesJson"))

        // Clear database completely
        repository.clearAllEntries()
        repository.clearAllInterventions()
        repository.clearAllSymptoms()
        repository.clearAllActivities()
        repository.clearAllHabits()

        assertEquals(0, repository.getAllInterventions().size)
        assertEquals(0, repository.getAllSymptoms().size)
        assertEquals(0, repository.getAllActivities().size)
        assertEquals(0, repository.getAllHabits().size)

        // Restore from JSON
        val restoreSuccess = repository.importFromJson(backupJson)
        assertTrue(restoreSuccess)

        // Verify master data restored
        val restoredIntervs = repository.getAllInterventions()
        assertEquals(1, restoredIntervs.size)
        assertEquals("Ibuprofen 400mg", restoredIntervs[0].name)
        assertEquals("- Diminum sesudah makan\n- Efektif untuk migrain", restoredIntervs[0].description)

        val restoredSymps = repository.getAllSymptoms()
        assertEquals(1, restoredSymps.size)
        assertEquals("Migrain Kanan", restoredSymps[0].name)
        assertEquals("- Berdenyut di pelipis kanan\n- Sensitif cahaya", restoredSymps[0].description)

        val restoredActs = repository.getAllActivities()
        assertEquals(1, restoredActs.size)
        assertEquals("Bekerja di Depan Layar", restoredActs[0].name)
        assertEquals("- Monitor tanpa kacamata anti radiasi", restoredActs[0].description)

        val restoredHabits = repository.getAllHabits()
        assertEquals(1, restoredHabits.size)
        assertEquals("Peregangan Otot", restoredHabits[0].name)
        assertEquals("- Dilakukan tiap pagi 10 menit", restoredHabits[0].description)

        // Verify entries and IDs restored
        val restoredAcute = db.healthDao().getEntryById(101)
        assertNotNull("Acute entry must be restored with original ID", restoredAcute)
        assertEquals(8, restoredAcute!!.symptomSeverity)
        assertEquals("[$sympId]", restoredAcute.symptomIdsJson)
        assertEquals("{\"$sympId\": 8}", restoredAcute.symptomSeveritiesJson)

        val restoredEval = db.healthDao().getEntryById(102)
        assertNotNull("Evaluation entry must be restored with original ID", restoredEval)
        assertEquals(101L, restoredEval!!.targetEntryId)
        assertEquals(2, restoredEval.finalSeverity)
        assertEquals(6, restoredEval.painDelta)
    }

    @Test
    fun testImportOlderBackupFormat() = runBlocking {
        // Simulates an older backup JSON that lacked "description" and "symptomIdsJson"
        val oldJson = """
        {
          "version": 2,
          "interventions": [
            {
              "id": 1,
              "name": "Paracetamol 500mg",
              "defaultDose": "1 tablet",
              "category": "Medis",
              "iconName": "Pill"
            }
          ],
          "symptoms": [
            {
              "id": 2,
              "name": "Sakit Punggung",
              "iconName": "Warning",
              "defaultBodyPart": "Punggung"
            }
          ],
          "activities": [
            {
              "id": 3,
              "name": "Duduk Lama",
              "iconName": "Weekend",
              "category": "Kerja"
            }
          ],
          "habits": [
            {
              "id": 4,
              "name": "Minum Air 2L",
              "iconEmoji": "💧",
              "targetDaysPerWeek": 7
            }
          ],
          "entries": [
            {
              "id": 10,
              "occurrenceTime": 1700000000000,
              "symptomId": 2,
              "symptomSeverity": 7,
              "interventionIdsJson": "[1]",
              "activityIdsJson": "[3]",
              "habitIdsJson": "[4]"
            }
          ]
        }
        """.trimIndent()

        val success = repository.importFromJson(oldJson)
        assertTrue(success)

        val intervs = repository.getAllInterventions()
        assertEquals(1, intervs.size)
        assertEquals("Paracetamol 500mg", intervs[0].name)

        val symps = repository.getAllSymptoms()
        assertEquals(1, symps.size)
        assertEquals("Sakit Punggung", symps[0].name)

        val entry = db.healthDao().getEntryById(10)
        assertNotNull(entry)
        assertEquals(2L, entry!!.symptomId)
        assertEquals("[2]", entry.symptomIdsJson)
        assertEquals("{\"2\": 7}", entry.symptomSeveritiesJson)
    }

    @Test
    fun testImportIndonesianKeysAndVariedFormat() = runBlocking {
        val indonesianJson = """
        {
          "version": 3,
          "intervensi": [
            {
              "id": 1,
              "nama": "Paracetamol 500mg",
              "dosis": "1 tablet",
              "kategori": "Medis",
              "catatan": "Minum sesudah makan"
            }
          ],
          "gejala": [
            {
              "id": 2,
              "nama": "Sakit Punggung",
              "bagianTubuh": "Punggung",
              "deskripsi": "Nyeri di bagian bawah"
            }
          ],
          "aktivitas": [
            {
              "id": 3,
              "nama": "Kerja Laptop",
              "kategori": "Pekerjaan",
              "catatan": "Duduk lebih dari 4 jam"
            }
          ],
          "kebiasaan": [
            {
              "id": 4,
              "nama": "Minum Air 2L",
              "emoji": "💧",
              "targetDaysPerWeek": 7,
              "catatan": "Target 8 gelas per hari"
            }
          ],
          "timeline": [
            {
              "id": 20,
              "occurrenceTime": 1700000000000,
              "symptomId": 2,
              "symptomSeverity": 8,
              "catatanGejala": "Nyeri terasa menjalar",
              "interventionIds": [1],
              "catatanIntervensi": "Diminum dengan air hangat",
              "activityIds": [3],
              "habitIds": [4],
              "mood": 2
            }
          ]
        }
        """.trimIndent()

        val success = repository.importFromJson(indonesianJson)
        assertTrue(success)

        val intervs = repository.getAllInterventions()
        assertEquals(1, intervs.size)
        assertEquals("Paracetamol 500mg", intervs[0].name)
        assertEquals("Minum sesudah makan", intervs[0].description)

        val symps = repository.getAllSymptoms()
        assertEquals(1, symps.size)
        assertEquals("Sakit Punggung", symps[0].name)
        assertEquals("Nyeri di bagian bawah", symps[0].description)

        val acts = repository.getAllActivities()
        assertEquals(1, acts.size)
        assertEquals("Kerja Laptop", acts[0].name)
        assertEquals("Duduk lebih dari 4 jam", acts[0].description)

        val habits = repository.getAllHabits()
        assertEquals(1, habits.size)
        assertEquals("Minum Air 2L", habits[0].name)
        assertEquals("Target 8 gelas per hari", habits[0].description)

        val entry = db.healthDao().getEntryById(20)
        assertNotNull(entry)
        assertEquals(2L, entry!!.symptomId)
        assertEquals(8, entry.symptomSeverity)
        assertEquals("Nyeri terasa menjalar", entry.symptomNotes)
        assertEquals("[1]", entry.interventionIdsJson)
        assertEquals("Diminum dengan air hangat", entry.interventionNotes)
        assertEquals("[3]", entry.activityIdsJson)
        assertEquals("[4]", entry.habitIdsJson)
    }
}
