package com.example.healthtracker.analytics

import com.example.healthtracker.model.ActivityEntity
import com.example.healthtracker.model.HealthEntryEntity
import com.example.healthtracker.model.InterventionEntity
import com.example.healthtracker.model.SymptomEntity
import kotlin.math.roundToInt

// --- DATA TRANSFER OBJECTS FOR ANALYTICS ---

data class SingleDrugEvaluation(
    val interventionId: Long,
    val interventionName: String,
    val totalTrials: Int,
    val successfulTrials: Int, // Delta >= 2
    val successRatePercent: Int,
    val medianDelta: Double,
    val meanDelta: Double,
    val avgReactionMinutes: Int,
    val adverseReactions: Int, // Delta < 0
    val confidence: String, // "High confidence", "Low confidence", "Belum cukup data (< 3 sampel)"
    val isEffective: Boolean // median delta >= 2 && totalTrials >= 3
)

data class FastReliefDrugStats(
    val id: String, // e.g. "single_1" or "combo_1_2"
    val name: String, // "Paracetamol 500mg" or "Paracetamol + PMR"
    val isCombination: Boolean,
    val componentNames: List<String>,
    val totalTrials: Int, // Total respon dicatat dalam rentang < 24 jam
    val zeroSuccessCount: Int, // Berapa kali nyeri berhasil turun sampai 0
    val zeroSuccessRatePercent: Int, // Persentase berhasil ke 0
    val fastestMinutesToZero: Int?, // Rekor tercepat menuju skala 0 (menit)
    val fastestRecordDetail: String?, // cth: "20 Mnt (Skala 7 ➔ 0)"
    val avgMinutesToZero: Int?, // Rata-rata waktu reaksi menuju skala 0 (menit)
    val avgOverallMinutes: Int, // Rata-rata waktu reaksi seluruh respon (menit)
    val avgDelta: Double, // Rata-rata penurunan poin nyeri
    val singleCount: Int = 0, // Dipakai sendiri
    val comboCount: Int = 0, // Dipakai dalam kombinasi
    val rank: Int = 1
)

data class CombinationAblationEvaluation(
    val combinationName: String,
    val interventionIds: List<Long>,
    val totalTrials: Int,
    val successRatePercent: Int,
    val avgDelta: Double,
    val keyContributor: String?,
    val ablationDetails: List<AblationComponent>
)

data class AblationComponent(
    val componentName: String,
    val successRateWith: Int,
    val successRateWithout: Int,
    val dropPercent: Int,
    val isKeyContributor: Boolean
)

data class TriggerCorrelation(
    val activityId: Long,
    val activityName: String,
    val symptomId: Long,
    val symptomName: String,
    val totalOccurrencesInPeriod: Int,
    val totalOccurrencesAllTime: Int,
    val triggerCountInPeriod: Int,
    val triggerPercentageInPeriod: Int, // (triggerCount / totalOccurrencesInPeriod) * 100
    val avgLagMinutes: Int,
    val trendBadge: String // "🚀 Pemicu Baru", "📈 Meningkat", "📉 Berkurang", "🔄 Stabil"
)

data class MoodPainMatrixCell(
    val moodScore: Int, // 1 to 5
    val moodLabel: String,
    val mildCount: Int, // Severity 1-3
    val moderateCount: Int, // Severity 4-6
    val severeCount: Int, // Severity 7-10
    val totalEntries: Int,
    val avgPain: Double
)

data class MoodPainAnalysis(
    val matrixCells: List<MoodPainMatrixCell>,
    val overwhelmedAvgPain: Double, // Mood 1-2
    val calmAvgPain: Double, // Mood 4-5
    val sensitivityIncreasePercent: Int, // Difference in pain between low capacity vs high capacity
    val cognitiveModulationInsight: String
)

data class DoctorExecutiveSummary(
    val periodLabel: String,
    val totalEntries: Int,
    val topSymptoms: List<Pair<String, Int>>,
    val mostEffectiveInterventions: List<SingleDrugEvaluation>,
    val adverseAlerts: List<String>,
    val significantTriggers: List<TriggerCorrelation>, // >= 50%
    val mindBodyInsight: String
)

enum class TimeRangeFilter(val label: String, val days: Int?) {
    LAST_7_DAYS("7 Hari Terakhir", 7),
    LAST_30_DAYS("30 Hari Terakhir", 30),
    LAST_90_DAYS("3 Bulan Terakhir", 90),
    ALL_TIME("Semua Waktu", null)
}

object AnalyticsEngine {

    /**
     * Parses JSON array string like "[1, 2, 3]" into List<Long>
     */
    fun parseIdList(json: String?): List<Long> {
        if (json.isNullOrBlank()) return emptyList()
        val cleaned = json.replace("[", "").replace("]", "").trim()
        if (cleaned.isEmpty()) return emptyList()
        return cleaned.split(",").mapNotNull { it.trim().toLongOrNull() }
    }

    /**
     * Parses JSON map string like "{\"1\": 8, \"2\": 4}" or "1:8, 2:4" into Map<Long, Int>
     */
    fun parseSeverityMap(json: String?): Map<Long, Int> {
        if (json.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<Long, Int>()
        val cleaned = json.trim().removePrefix("{").removeSuffix("}").removePrefix("[").removeSuffix("]").trim()
        if (cleaned.isEmpty()) return emptyMap()
        cleaned.split(",").forEach { pair ->
            val parts = pair.split(":")
            if (parts.size == 2) {
                val key = parts[0].trim().replace("\"", "").replace("'", "").toLongOrNull()
                val value = parts[1].trim().replace("\"", "").replace("'", "").toIntOrNull()
                if (key != null && value != null) {
                    result[key] = value
                }
            }
        }
        return result
    }

    /**
     * Formats Map<Long, Int> into JSON string like "{\"1\": 8, \"2\": 4}"
     */
    fun formatSeverityMap(map: Map<Long, Int>): String {
        if (map.isEmpty()) return "{}"
        return "{" + map.entries.joinToString(", ") { "\"${it.key}\": ${it.value}" } + "}"
    }

    // =========================================================================
    // MODULE A: LEVEL 1 SINGLE DRUG EFFECTIVENESS
    // =========================================================================
    fun evaluateSingleInterventions(
        allEntries: List<HealthEntryEntity>,
        interventions: List<InterventionEntity>
    ): List<SingleDrugEvaluation> {
        val entryMap = allEntries.associateBy { it.id }
        // Find all evaluation phase entries that have valid target entry and pain delta
        val evaluations = allEntries.filter { it.isEvaluationPhase && it.targetEntryId != null && it.painDelta != null }

        val interventionMap = interventions.associateBy { it.id }
        val results = mutableListOf<SingleDrugEvaluation>()

        for (interv in interventions) {
            val trials = mutableListOf<Pair<HealthEntryEntity, HealthEntryEntity>>() // Pair(Acute, Eval)

            for (eval in evaluations) {
                val acute = entryMap[eval.targetEntryId] ?: continue
                val takenInterventions = parseIdList(acute.interventionIdsJson)
                if (interv.id in takenInterventions) {
                    // Check biological window: 15 minutes to 4 hours (900000ms to 14400000ms)
                    val diffMs = eval.occurrenceTime - acute.occurrenceTime
                    val minMs = 15 * 60 * 1000L
                    val maxMs = 4 * 60 * 60 * 1000L
                    if (diffMs in minMs..maxMs || (eval.reactionTimeMinutes ?: 0) in 15..240) {
                        trials.add(Pair(acute, eval))
                    }
                }
            }

            val totalCount = trials.size
            if (totalCount == 0) {
                results.add(
                    SingleDrugEvaluation(
                        interventionId = interv.id,
                        interventionName = interv.name,
                        totalTrials = 0,
                        successfulTrials = 0,
                        successRatePercent = 0,
                        medianDelta = 0.0,
                        meanDelta = 0.0,
                        avgReactionMinutes = 0,
                        adverseReactions = 0,
                        confidence = "Belum cukup data (< 3 sampel)",
                        isEffective = false
                    )
                )
                continue
            }

            val deltas = trials.mapNotNull { it.second.painDelta }
            val successfulCount = deltas.count { it >= 2 }
            val adverseCount = deltas.count { it < 0 }
            val successRate = if (totalCount > 0) ((successfulCount.toDouble() / totalCount) * 100).roundToInt() else 0

            val meanDelta = if (deltas.isNotEmpty()) deltas.average() else 0.0
            val sortedDeltas = deltas.sorted()
            val medianDelta = if (sortedDeltas.isNotEmpty()) {
                val mid = sortedDeltas.size / 2
                if (sortedDeltas.size % 2 == 1) {
                    sortedDeltas[mid].toDouble()
                } else {
                    (sortedDeltas[mid - 1] + sortedDeltas[mid]) / 2.0
                }
            } else 0.0

            val avgReactionMinutes = if (trials.isNotEmpty()) {
                trials.map { (acute, eval) ->
                    eval.reactionTimeMinutes ?: ((eval.occurrenceTime - acute.occurrenceTime) / (60 * 1000)).toInt()
                }.average().roundToInt()
            } else 0

            val isEffective = totalCount >= 3 && medianDelta >= 2.0
            val confidence = when {
                totalCount < 3 -> "Belum cukup data (< 3 sampel)"
                totalCount >= 5 && successRate >= 70 -> "High confidence"
                else -> "Low confidence"
            }

            results.add(
                SingleDrugEvaluation(
                    interventionId = interv.id,
                    interventionName = interv.name,
                    totalTrials = totalCount,
                    successfulTrials = successfulCount,
                    successRatePercent = successRate,
                    medianDelta = medianDelta,
                    meanDelta = (meanDelta * 10).roundToInt() / 10.0,
                    avgReactionMinutes = avgReactionMinutes,
                    adverseReactions = adverseCount,
                    confidence = confidence,
                    isEffective = isEffective
                )
            )
        }

        return results.sortedWith(compareByDescending<SingleDrugEvaluation> { it.totalTrials }.thenByDescending { it.successRatePercent })
    }

    // =========================================================================
    // MODULE: FAST-RELIEF & ZERO-PAIN TARGET ANALYTICS (< 24 HOURS)
    // =========================================================================
    fun evaluateFastReliefAnalytics(
        allEntries: List<HealthEntryEntity>,
        interventions: List<InterventionEntity>,
        forCombinationsOnly: Boolean
    ): List<FastReliefDrugStats> {
        val entryMap = allEntries.associateBy { it.id }
        val intervMap = interventions.associateBy { it.id }

        // Filter evaluations with valid target entry
        val evaluations = allEntries.filter { it.isEvaluationPhase && it.targetEntryId != null }
        val maxLagMs = 24 * 60 * 60 * 1000L

        data class TrialRecord(
            val acute: HealthEntryEntity,
            val eval: HealthEntryEntity,
            val initialSev: Int,
            val finalSev: Int,
            val delta: Int,
            val reactionMins: Int,
            val reachedZero: Boolean,
            val takenIds: List<Long>
        )

        val validTrials = mutableListOf<TrialRecord>()

        for (eval in evaluations) {
            val acute = entryMap[eval.targetEntryId] ?: continue
            val diffMs = eval.occurrenceTime - acute.occurrenceTime
            val statedMins = eval.reactionTimeMinutes ?: (diffMs / (60 * 1000L)).toInt()

            // Filter strictly < 24 hours
            if (diffMs !in 0..maxLagMs && statedMins !in 1..(24 * 60)) continue

            val reactionMins = statedMins.coerceIn(1, 24 * 60)
            val initialSev = acute.symptomSeverity ?: 0
            val finalSev = eval.finalSeverity ?: (initialSev - (eval.painDelta ?: 0)).coerceAtLeast(0)
            val delta = eval.painDelta ?: (initialSev - finalSev)
            val reachedZero = (finalSev == 0)
            val takenIds = parseIdList(acute.interventionIdsJson)

            if (takenIds.isNotEmpty()) {
                validTrials.add(
                    TrialRecord(
                        acute = acute,
                        eval = eval,
                        initialSev = initialSev,
                        finalSev = finalSev,
                        delta = delta,
                        reactionMins = reactionMins,
                        reachedZero = reachedZero,
                        takenIds = takenIds
                    )
                )
            }
        }

        val rawList = mutableListOf<FastReliefDrugStats>()

        if (forCombinationsOnly) {
            // Group by distinct combinations (>= 2 interventions)
            val comboGroups = validTrials.filter { it.takenIds.size >= 2 }
                .groupBy { it.takenIds.sorted() }

            for ((comboIds, trials) in comboGroups) {
                val total = trials.size
                if (total == 0) continue

                val zeroTrials = trials.filter { it.reachedZero }
                val zeroCount = zeroTrials.size
                val zeroPercent = ((zeroCount.toDouble() / total) * 100).roundToInt()

                val fastestTrial = zeroTrials.minByOrNull { it.reactionMins }
                val fastestMins = fastestTrial?.reactionMins
                val fastestDetail = fastestTrial?.let {
                    "${it.reactionMins} Mnt (Skala ${it.initialSev} ➔ 0)"
                }

                val avgZeroMins = if (zeroTrials.isNotEmpty()) {
                    zeroTrials.map { it.reactionMins }.average().roundToInt()
                } else null

                val avgOverallMins = trials.map { it.reactionMins }.average().roundToInt()
                val avgDelta = (trials.map { it.delta }.average() * 10).roundToInt() / 10.0

                val compNames = comboIds.mapNotNull { intervMap[it]?.name }
                val name = compNames.joinToString(" + ")

                rawList.add(
                    FastReliefDrugStats(
                        id = "combo_" + comboIds.joinToString("_"),
                        name = name,
                        isCombination = true,
                        componentNames = compNames,
                        totalTrials = total,
                        zeroSuccessCount = zeroCount,
                        zeroSuccessRatePercent = zeroPercent,
                        fastestMinutesToZero = fastestMins,
                        fastestRecordDetail = fastestDetail,
                        avgMinutesToZero = avgZeroMins,
                        avgOverallMinutes = avgOverallMins,
                        avgDelta = avgDelta,
                        comboCount = total
                    )
                )
            }
        } else {
            // Per Individual Drug
            for (interv in interventions) {
                val trials = validTrials.filter { interv.id in it.takenIds }
                val total = trials.size
                if (total == 0) continue

                val zeroTrials = trials.filter { it.reachedZero }
                val zeroCount = zeroTrials.size
                val zeroPercent = ((zeroCount.toDouble() / total) * 100).roundToInt()

                val fastestTrial = zeroTrials.minByOrNull { it.reactionMins }
                val fastestMins = fastestTrial?.reactionMins
                val fastestDetail = fastestTrial?.let {
                    "${it.reactionMins} Mnt (Skala ${it.initialSev} ➔ 0)"
                }

                val avgZeroMins = if (zeroTrials.isNotEmpty()) {
                    zeroTrials.map { it.reactionMins }.average().roundToInt()
                } else null

                val avgOverallMins = trials.map { it.reactionMins }.average().roundToInt()
                val avgDelta = (trials.map { it.delta }.average() * 10).roundToInt() / 10.0

                val singleCount = trials.count { it.takenIds.size == 1 }
                val comboCount = trials.count { it.takenIds.size > 1 }

                rawList.add(
                    FastReliefDrugStats(
                        id = "single_${interv.id}",
                        name = interv.name,
                        isCombination = false,
                        componentNames = listOf(interv.name),
                        totalTrials = total,
                        zeroSuccessCount = zeroCount,
                        zeroSuccessRatePercent = zeroPercent,
                        fastestMinutesToZero = fastestMins,
                        fastestRecordDetail = fastestDetail,
                        avgMinutesToZero = avgZeroMins,
                        avgOverallMinutes = avgOverallMins,
                        avgDelta = avgDelta,
                        singleCount = singleCount,
                        comboCount = comboCount
                    )
                )
            }
        }

        // Sort by fastest to 0 and highest zero success rate
        val sorted = rawList.sortedWith(
            compareByDescending<FastReliefDrugStats> { it.zeroSuccessCount > 0 }
                .thenByDescending { it.zeroSuccessRatePercent }
                .thenBy { it.fastestMinutesToZero ?: 9999 }
                .thenBy { it.avgMinutesToZero ?: 9999 }
                .thenByDescending { it.totalTrials }
        )

        return sorted.mapIndexed { index, stat ->
            stat.copy(rank = index + 1)
        }
    }

    // =========================================================================
    // MODULE B: LEVEL 2 COMBINATION & ABLATION ANALYSIS
    // =========================================================================
    fun evaluateCombinations(
        allEntries: List<HealthEntryEntity>,
        interventions: List<InterventionEntity>
    ): List<CombinationAblationEvaluation> {
        val entryMap = allEntries.associateBy { it.id }
        val evaluations = allEntries.filter { it.isEvaluationPhase && it.targetEntryId != null && it.painDelta != null }
        val intervMap = interventions.associateBy { it.id }

        // Group trials by set of intervention IDs
        val comboTrials = mutableMapOf<Set<Long>, MutableList<Pair<HealthEntryEntity, HealthEntryEntity>>>()

        for (eval in evaluations) {
            val acute = entryMap[eval.targetEntryId] ?: continue
            val ids = parseIdList(acute.interventionIdsJson).toSet()
            if (ids.size >= 2) {
                comboTrials.getOrPut(ids) { mutableListOf() }.add(Pair(acute, eval))
            }
        }

        val results = mutableListOf<CombinationAblationEvaluation>()

        for ((comboIds, trials) in comboTrials) {
            val totalTrials = trials.size
            if (totalTrials < 2) continue // Need at least 2 occurrences of this combo to evaluate
            val successful = trials.count { (it.second.painDelta ?: 0) >= 2 }
            val successRate = ((successful.toDouble() / totalTrials) * 100).roundToInt()
            val avgDelta = (trials.mapNotNull { it.second.painDelta }.average() * 10).roundToInt() / 10.0

            val comboName = comboIds.mapNotNull { intervMap[it]?.name }.joinToString(" + ")

            // Ablation analysis: For each component X in comboIds, find trials WITHOUT X
            val ablationList = mutableListOf<AblationComponent>()
            var topDrop = -999
            var keyContributorName: String? = null

            for (componentId in comboIds) {
                val compName = intervMap[componentId]?.name ?: "Komponen #$componentId"
                // Find trials where the remaining components (comboIds - componentId) were used without componentId
                val withoutComponentIds = comboIds - componentId
                val withoutTrials = evaluations.mapNotNull { eval ->
                    val acute = entryMap[eval.targetEntryId] ?: return@mapNotNull null
                    val ids = parseIdList(acute.interventionIdsJson).toSet()
                    if (ids.containsAll(withoutComponentIds) && !ids.contains(componentId)) {
                        eval
                    } else null
                }

                val rateWithout = if (withoutTrials.isNotEmpty()) {
                    val succWithout = withoutTrials.count { (it.painDelta ?: 0) >= 2 }
                    ((succWithout.toDouble() / withoutTrials.size) * 100).roundToInt()
                } else {
                    // If no explicit subtraction found, check single component average
                    0
                }

                val drop = successRate - rateWithout
                val isKey = drop >= 25 && rateWithout < successRate
                if (drop > topDrop && isKey) {
                    topDrop = drop
                    keyContributorName = compName
                }

                ablationList.add(
                    AblationComponent(
                        componentName = compName,
                        successRateWith = successRate,
                        successRateWithout = rateWithout,
                        dropPercent = drop,
                        isKeyContributor = isKey
                    )
                )
            }

            results.add(
                CombinationAblationEvaluation(
                    combinationName = comboName,
                    interventionIds = comboIds.toList(),
                    totalTrials = totalTrials,
                    successRatePercent = successRate,
                    avgDelta = avgDelta,
                    keyContributor = keyContributorName,
                    ablationDetails = ablationList
                )
            )
        }

        return results.sortedByDescending { it.totalTrials }
    }

    // =========================================================================
    // MODULE C: DELAYED TRIGGER ANALYSIS (Activity -> Symptom)
    // =========================================================================
    fun evaluateDelayedTriggers(
        allEntries: List<HealthEntryEntity>,
        activities: List<ActivityEntity>,
        symptoms: List<SymptomEntity>,
        timeRange: TimeRangeFilter,
        referenceNow: Long = System.currentTimeMillis()
    ): List<TriggerCorrelation> {
        val actMap = activities.associateBy { it.id }
        val sympMap = symptoms.associateBy { it.id }

        // Entries sorted chronologically ASCENDING for causal sequence
        val sortedAsc = allEntries.sortedBy { it.occurrenceTime }

        val filterStartTime = timeRange.days?.let { days ->
            referenceNow - (days * 24 * 3600 * 1000L)
        } ?: 0L

        val results = mutableListOf<TriggerCorrelation>()

        for (activity in activities) {
            // Find all activity entries
            val activityEntriesAllTime = sortedAsc.filter { entry ->
                activity.id in parseIdList(entry.activityIdsJson)
            }

            val totalAllTime = activityEntriesAllTime.size
            val activityEntriesInPeriod = activityEntriesAllTime.filter { it.occurrenceTime >= filterStartTime }
            val totalInPeriod = activityEntriesInPeriod.size

            if (totalAllTime == 0) continue

            // Evaluate against each symptom
            for (symptom in symptoms) {
                var triggeredCountInPeriod = 0
                val lagMinutesList = mutableListOf<Int>()

                // FIRST-ONSET CAUSALITY WITHIN N ENTRIES (default N=4) & STRICTLY <= 24 HOURS
                val maxSequentialEntries = 4
                val maxBioWindowMs = 24 * 3600 * 1000L

                for (actEntry in activityEntriesInPeriod) {
                    val actIndex = sortedAsc.indexOf(actEntry)
                    if (actIndex == -1) continue

                    // Look ahead up to N entries
                    val endIndex = minOf(sortedAsc.size - 1, actIndex + maxSequentialEntries)
                    var foundFirstOnset = false

                    for (nextIdx in (actIndex + 1)..endIndex) {
                        val nextEntry = sortedAsc[nextIdx]
                        val diffMs = nextEntry.occurrenceTime - actEntry.occurrenceTime

                        // Strict biological window: must be strictly > 0 and <= 24 hours
                        if (diffMs in 1..maxBioWindowMs) {
                            if (nextEntry.symptomId == symptom.id && !nextEntry.isEvaluationPhase) {
                                triggeredCountInPeriod++
                                lagMinutesList.add((diffMs / (60 * 1000L)).toInt())
                                foundFirstOnset = true
                                break // FIRST-ONSET CAUSALITY: count at most 1 time per activity session
                            }
                        }
                    }
                }

                if (triggeredCountInPeriod > 0 || totalInPeriod > 0) {
                    val percentage = if (totalInPeriod > 0) {
                        ((triggeredCountInPeriod.toDouble() / totalInPeriod) * 100).roundToInt()
                    } else 0

                    val avgLag = if (lagMinutesList.isNotEmpty()) lagMinutesList.average().roundToInt() else 0

                    // Dynamic trend badge calculation
                    val trendBadge = when {
                        totalAllTime > 0 && totalInPeriod == totalAllTime && totalAllTime <= 2 -> "🚀 Pemicu Baru"
                        percentage >= 60 -> "📈 Meningkat"
                        percentage <= 25 && totalInPeriod >= 3 -> "📉 Berkurang"
                        else -> "🔄 Stabil"
                    }

                    results.add(
                        TriggerCorrelation(
                            activityId = activity.id,
                            activityName = activity.name,
                            symptomId = symptom.id,
                            symptomName = symptom.name,
                            totalOccurrencesInPeriod = totalInPeriod,
                            totalOccurrencesAllTime = totalAllTime,
                            triggerCountInPeriod = triggeredCountInPeriod,
                            triggerPercentageInPeriod = percentage,
                            avgLagMinutes = avgLag,
                            trendBadge = trendBadge
                        )
                    )
                }
            }
        }

        return results.sortedWith(
            compareByDescending<TriggerCorrelation> { it.triggerPercentageInPeriod }
                .thenByDescending { it.triggerCountInPeriod }
        )
    }

    // =========================================================================
    // MODULE D: MOOD-PAIN CROSS MATRIX (Heatmap & Sensitivity Gap)
    // =========================================================================
    fun evaluateMoodPainMatrix(allEntries: List<HealthEntryEntity>): MoodPainAnalysis {
        val moodLabels = mapOf(
            1 to "Krisis / Habis Total",
            2 to "Tertekan / Rendah",
            3 to "Netral / Cukup",
            4 to "Stabil / Baik",
            5 to "Tenang & Resilien"
        )

        // Only evaluate entries that have a mood score and recorded symptom severity
        val entriesWithMoodAndPain = allEntries.filter { it.moodScore != null && (it.symptomSeverity != null || it.finalSeverity != null) }

        val cells = mutableListOf<MoodPainMatrixCell>()
        val lowCapacityPainList = mutableListOf<Int>() // Mood 1 & 2
        val highCapacityPainList = mutableListOf<Int>() // Mood 4 & 5

        for (score in 1..5) {
            val scoreEntries = entriesWithMoodAndPain.filter { it.moodScore == score }
            var mild = 0
            var moderate = 0
            var severe = 0
            val painScores = mutableListOf<Int>()

            for (e in scoreEntries) {
                val pain = e.symptomSeverity ?: e.finalSeverity ?: continue
                painScores.add(pain)
                when {
                    pain in 1..3 -> mild++
                    pain in 4..6 -> moderate++
                    pain in 7..10 -> severe++
                }

                if (score <= 2) {
                    lowCapacityPainList.add(pain)
                } else if (score >= 4) {
                    highCapacityPainList.add(pain)
                }
            }

            val avgPain = if (painScores.isNotEmpty()) {
                (painScores.average() * 10).roundToInt() / 10.0
            } else 0.0

            cells.add(
                MoodPainMatrixCell(
                    moodScore = score,
                    moodLabel = moodLabels[score] ?: "Skor $score",
                    mildCount = mild,
                    moderateCount = moderate,
                    severeCount = severe,
                    totalEntries = scoreEntries.size,
                    avgPain = avgPain
                )
            )
        }

        val lowAvg = if (lowCapacityPainList.isNotEmpty()) {
            (lowCapacityPainList.average() * 10).roundToInt() / 10.0
        } else 0.0

        val highAvg = if (highCapacityPainList.isNotEmpty()) {
            (highCapacityPainList.average() * 10).roundToInt() / 10.0
        } else 0.0

        val sensitivityIncrease = if (highAvg > 0.0 && lowAvg >= highAvg) {
            (((lowAvg - highAvg) / highAvg) * 100).roundToInt()
        } else 0

        val insight = when {
            sensitivityIncrease >= 40 ->
                "💡 **Pengaruh Pikiran Sangat Kuat (+${sensitivityIncrease}%)**\nNyeri fisik terasa jauh lebih menyiksa saat kamu lagi stres/capek mental. Selain pengobatan fisik, penting banget buat kamu ngelakuin relaksasi atau istirahat pikiran!"
            sensitivityIncrease in 15..39 ->
                "💡 **Stres Mulai Memperparah Nyeri (+${sensitivityIncrease}%)**\nBeban emosional/stres terbukti menambah rasa sakit yang dirasakan. Jaga suasana hati agar ambang tahan nyeri tubuhmu tetap tinggi."
            else ->
                "💡 **Nyeri Murni Masalah Fisik**\nTingkat rasa sakitmu stabil dan tidak terlalu dipengaruhi oleh mood. Penanganan medis/fisik dan obat-obatan adalah fokus utamanya."
        }

        return MoodPainAnalysis(
            matrixCells = cells,
            overwhelmedAvgPain = lowAvg,
            calmAvgPain = highAvg,
            sensitivityIncreasePercent = sensitivityIncrease,
            cognitiveModulationInsight = insight
        )
    }

    // =========================================================================
    // MODULE E: DOCTOR EXECUTIVE SUMMARY
    // =========================================================================
    fun generateDoctorSummary(
        allEntries: List<HealthEntryEntity>,
        interventions: List<InterventionEntity>,
        symptoms: List<SymptomEntity>,
        activities: List<ActivityEntity>,
        timeRange: TimeRangeFilter = TimeRangeFilter.LAST_30_DAYS
    ): DoctorExecutiveSummary {
        val sympMap = symptoms.associateBy { it.id }

        // Filter entries
        val now = System.currentTimeMillis()
        val filterStart = timeRange.days?.let { now - (it * 24 * 3600 * 1000L) } ?: 0L
        val periodEntries = allEntries.filter { it.occurrenceTime >= filterStart }

        // Top symptoms
        val symptomCounts = mutableMapOf<String, Int>()
        for (e in periodEntries) {
            val sId = e.symptomId ?: continue
            val name = sympMap[sId]?.name ?: "Gejala #$sId"
            symptomCounts[name] = (symptomCounts[name] ?: 0) + 1
        }
        val topSymptoms = symptomCounts.toList().sortedByDescending { it.second }.take(5)

        // Single drug evaluations
        val drugEvaluations = evaluateSingleInterventions(periodEntries, interventions)
        val effectiveDrugs = drugEvaluations.filter { it.isEffective }

        // Adverse alerts
        val adverseAlerts = drugEvaluations
            .filter { it.adverseReactions > 0 }
            .map { "${it.interventionName}: Tercatat ${it.adverseReactions} kali kenaikan nyeri paska konsumsi." }

        // Delayed triggers >= 50%
        val allTriggers = evaluateDelayedTriggers(allEntries, activities, symptoms, timeRange, now)
        val significantTriggers = allTriggers.filter { it.triggerPercentageInPeriod >= 50 && it.totalOccurrencesInPeriod >= 2 }

        // Mind-body insight
        val moodAnalysis = evaluateMoodPainMatrix(periodEntries)

        return DoctorExecutiveSummary(
            periodLabel = timeRange.label,
            totalEntries = periodEntries.size,
            topSymptoms = topSymptoms,
            mostEffectiveInterventions = effectiveDrugs,
            adverseAlerts = adverseAlerts,
            significantTriggers = significantTriggers,
            mindBodyInsight = moodAnalysis.cognitiveModulationInsight
        )
    }

    // =========================================================================
    // EXPORT UTILITIES: CSV & CLINICAL TEXT
    // =========================================================================
    fun exportEntriesToCsv(
        entries: List<HealthEntryEntity>,
        interventions: List<InterventionEntity>,
        symptoms: List<SymptomEntity>,
        activities: List<ActivityEntity>
    ): String {
        val intervMap = interventions.associateBy { it.id }
        val sympMap = symptoms.associateBy { it.id }
        val actMap = activities.associateBy { it.id }

        val sb = StringBuilder()
        sb.append("ID,Waktu_Kejadian,Fase,Gejala,Keparahan_Awal,Keparahan_Akhir,Delta_Nyeri,Intervensi,Aktivitas,Skor_Mental,Catatan\n")

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())

        for (e in entries.sortedByDescending { it.occurrenceTime }) {
            val dateStr = sdf.format(java.util.Date(e.occurrenceTime))
            val phaseStr = if (e.isEvaluationPhase) "Evaluasi Respon" else "Akut/Pencatatan"
            val sympName = e.symptomId?.let { sympMap[it]?.name } ?: ""
            val initSev = e.symptomSeverity?.toString() ?: ""
            val finSev = e.finalSeverity?.toString() ?: ""
            val delta = e.painDelta?.toString() ?: ""

            val intervNames = parseIdList(e.interventionIdsJson).mapNotNull { intervMap[it]?.name }.joinToString(" + ")
            val actNames = parseIdList(e.activityIdsJson).mapNotNull { actMap[it]?.name }.joinToString(" + ")
            val mood = e.moodScore?.toString() ?: ""
            val note = (e.symptomNotes ?: e.effectNotes ?: e.activityNotes ?: e.moodNotes ?: "").replace(",", ";")

            sb.append("${e.id},\"$dateStr\",\"$phaseStr\",\"$sympName\",$initSev,$finSev,$delta,\"$intervNames\",\"$actNames\",$mood,\"$note\"\n")
        }

        return sb.toString()
    }
}
