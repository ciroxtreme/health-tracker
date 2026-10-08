package com.example

import com.example.healthtracker.analytics.*
import com.example.healthtracker.model.*
import org.junit.Assert.*
import org.junit.Test

class AnalyticsEngineTest {

    @Test
    fun testSingleDrugEffectivenessLevel1() {
        val drug = InterventionEntity(id = 1, name = "Propranolol 10mg")
        val acute1 = HealthEntryEntity(id = 10, occurrenceTime = 1000000L, symptomId = 1, symptomSeverity = 8, interventionIdsJson = "[1]")
        val eval1 = HealthEntryEntity(id = 11, occurrenceTime = 1000000L + 3600000L, isEvaluationPhase = true, targetEntryId = 10, finalSeverity = 3, painDelta = 5, reactionTimeMinutes = 60)

        val acute2 = HealthEntryEntity(id = 20, occurrenceTime = 2000000L, symptomId = 1, symptomSeverity = 7, interventionIdsJson = "[1]")
        val eval2 = HealthEntryEntity(id = 21, occurrenceTime = 2000000L + 5400000L, isEvaluationPhase = true, targetEntryId = 20, finalSeverity = 4, painDelta = 3, reactionTimeMinutes = 90)

        val acute3 = HealthEntryEntity(id = 30, occurrenceTime = 3000000L, symptomId = 1, symptomSeverity = 6, interventionIdsJson = "[1]")
        val eval3 = HealthEntryEntity(id = 31, occurrenceTime = 3000000L + 7200000L, isEvaluationPhase = true, targetEntryId = 30, finalSeverity = 2, painDelta = 4, reactionTimeMinutes = 120)

        val entries = listOf(acute1, eval1, acute2, eval2, acute3, eval3)
        val results = AnalyticsEngine.evaluateSingleInterventions(entries, listOf(drug))

        assertEquals(1, results.size)
        val res = results[0]
        assertEquals(3, res.totalTrials)
        assertEquals(3, res.successfulTrials)
        assertEquals(100, res.successRatePercent)
        assertEquals(4.0, res.medianDelta, 0.01)
        assertTrue(res.isEffective)
        assertEquals(90, res.avgReactionMinutes)
    }

    @Test
    fun testFirstOnsetDelayedTriggerCausality() {
        val act = ActivityEntity(id = 5, name = "Duduk Lama")
        val symp = SymptomEntity(id = 9, name = "Sakit Kepala")

        val t0 = 10000000L
        val hour = 3600000L

        // Act at t0
        val actEntry = HealthEntryEntity(id = 1, occurrenceTime = t0, activityIdsJson = "[5]")
        // Symptom at t0 + 2h (Entry 2)
        val sympEntry1 = HealthEntryEntity(id = 2, occurrenceTime = t0 + 2 * hour, symptomId = 9, symptomSeverity = 6)
        // Same symptom again at t0 + 3h (Entry 3) - MUST NOT be double counted (First-Onset Causality)
        val sympEntry2 = HealthEntryEntity(id = 3, occurrenceTime = t0 + 3 * hour, symptomId = 9, symptomSeverity = 7)

        val entries = listOf(actEntry, sympEntry1, sympEntry2)
        val triggers = AnalyticsEngine.evaluateDelayedTriggers(
            allEntries = entries,
            activities = listOf(act),
            symptoms = listOf(symp),
            timeRange = TimeRangeFilter.ALL_TIME,
            referenceNow = t0 + 10 * hour
        )

        assertEquals(1, triggers.size)
        val trigger = triggers[0]
        assertEquals(1, trigger.totalOccurrencesInPeriod)
        // First onset causality: exactly 1 triggered count, not 2
        assertEquals(1, trigger.triggerCountInPeriod)
        assertEquals(100, trigger.triggerPercentageInPeriod)
        assertEquals(120, trigger.avgLagMinutes)
    }

    @Test
    fun testMoodPainMatrixSensitivityGap() {
        val lowMood1 = HealthEntryEntity(id = 1, occurrenceTime = 1000L, moodScore = 1, symptomSeverity = 8)
        val lowMood2 = HealthEntryEntity(id = 2, occurrenceTime = 2000L, moodScore = 2, symptomSeverity = 8)
        val highMood1 = HealthEntryEntity(id = 3, occurrenceTime = 3000L, moodScore = 4, symptomSeverity = 4)
        val highMood2 = HealthEntryEntity(id = 4, occurrenceTime = 4000L, moodScore = 5, symptomSeverity = 4)

        val analysis = AnalyticsEngine.evaluateMoodPainMatrix(listOf(lowMood1, lowMood2, highMood1, highMood2))
        assertEquals(8.0, analysis.overwhelmedAvgPain, 0.01)
        assertEquals(4.0, analysis.calmAvgPain, 0.01)
        // (8 - 4) / 4 = 100%
        assertEquals(100, analysis.sensitivityIncreasePercent)
    }
}
