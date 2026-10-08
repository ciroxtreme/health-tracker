package com.example.healthtracker.database

import androidx.room.*
import com.example.healthtracker.model.ActivityEntity
import com.example.healthtracker.model.HabitEntity
import com.example.healthtracker.model.HealthEntryEntity
import com.example.healthtracker.model.InterventionEntity
import com.example.healthtracker.model.SymptomEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthDao {

    // --- ENTRIES ---
    @Query("SELECT * FROM health_entries ORDER BY occurrenceTime DESC")
    fun getAllEntriesFlow(): Flow<List<HealthEntryEntity>>

    @Query("SELECT * FROM health_entries ORDER BY occurrenceTime DESC")
    suspend fun getAllEntries(): List<HealthEntryEntity>

    @Query("SELECT * FROM health_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: Long): HealthEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: HealthEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: HealthEntryEntity)

    @Delete
    suspend fun deleteEntry(entry: HealthEntryEntity)

    @Query("DELETE FROM health_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    @Query("SELECT * FROM health_entries WHERE occurrenceTime BETWEEN :startTime AND :endTime ORDER BY occurrenceTime ASC")
    suspend fun getEntriesInRange(startTime: Long, endTime: Long): List<HealthEntryEntity>

    // --- INTERVENTIONS ---
    @Query("SELECT * FROM interventions ORDER BY name ASC")
    fun getAllInterventionsFlow(): Flow<List<InterventionEntity>>

    @Query("SELECT * FROM interventions ORDER BY name ASC")
    suspend fun getAllInterventions(): List<InterventionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntervention(intervention: InterventionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInterventions(list: List<InterventionEntity>)

    @Update
    suspend fun updateIntervention(intervention: InterventionEntity)

    @Delete
    suspend fun deleteIntervention(intervention: InterventionEntity)

    // --- SYMPTOMS ---
    @Query("SELECT * FROM symptoms ORDER BY name ASC")
    fun getAllSymptomsFlow(): Flow<List<SymptomEntity>>

    @Query("SELECT * FROM symptoms ORDER BY name ASC")
    suspend fun getAllSymptoms(): List<SymptomEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSymptom(symptom: SymptomEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSymptoms(list: List<SymptomEntity>)

    @Update
    suspend fun updateSymptom(symptom: SymptomEntity)

    @Delete
    suspend fun deleteSymptom(symptom: SymptomEntity)

    // --- ACTIVITIES ---
    @Query("SELECT * FROM activities ORDER BY name ASC")
    fun getAllActivitiesFlow(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities ORDER BY name ASC")
    suspend fun getAllActivities(): List<ActivityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(activity: ActivityEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivities(list: List<ActivityEntity>)

    @Update
    suspend fun updateActivity(activity: ActivityEntity)

    @Delete
    suspend fun deleteActivity(activity: ActivityEntity)

    // --- HABITS ---
    @Query("SELECT * FROM habits ORDER BY name ASC")
    fun getAllHabitsFlow(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY name ASC")
    suspend fun getAllHabits(): List<HabitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: HabitEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabits(list: List<HabitEntity>)

    @Update
    suspend fun updateHabit(habit: HabitEntity)

    @Delete
    suspend fun deleteHabit(habit: HabitEntity)

    @Query("DELETE FROM interventions")
    suspend fun clearAllInterventions()

    @Query("DELETE FROM symptoms")
    suspend fun clearAllSymptoms()

    @Query("DELETE FROM activities")
    suspend fun clearAllActivities()

    @Query("DELETE FROM habits")
    suspend fun clearAllHabits()

    @Query("DELETE FROM health_entries")
    suspend fun clearAllEntries()
}
