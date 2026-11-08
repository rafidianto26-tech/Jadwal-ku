package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY day, startTime ASC")
    fun getAllSchedules(): Flow<List<Schedule>>

    @Query("SELECT * FROM schedules WHERE day = :day ORDER BY startTime ASC")
    fun getSchedulesByDay(day: String): Flow<List<Schedule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: Schedule): Long

    @Update
    suspend fun updateSchedule(schedule: Schedule)

    @Delete
    suspend fun deleteSchedule(schedule: Schedule)

    @Query("DELETE FROM schedules")
    suspend fun deleteAllSchedules()

    @Query("UPDATE schedules SET teacherName = :newTeacher WHERE teacherName = :oldTeacher")
    suspend fun updateTeacherName(oldTeacher: String, newTeacher: String)

    @Query("SELECT * FROM schedules WHERE id = :id LIMIT 1")
    suspend fun getScheduleById(id: Int): Schedule?

    @Query("SELECT * FROM schedules")
    suspend fun getAllSchedulesList(): List<Schedule>
}
