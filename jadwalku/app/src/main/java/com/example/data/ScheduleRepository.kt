package com.example.data

import kotlinx.coroutines.flow.Flow

class ScheduleRepository(private val scheduleDao: ScheduleDao) {
    val allSchedules: Flow<List<Schedule>> = scheduleDao.getAllSchedules()

    fun getSchedulesByDay(day: String): Flow<List<Schedule>> {
        return scheduleDao.getSchedulesByDay(day)
    }

    suspend fun insert(schedule: Schedule): Long {
        return scheduleDao.insertSchedule(schedule)
    }

    suspend fun update(schedule: Schedule) {
        scheduleDao.updateSchedule(schedule)
    }

    suspend fun delete(schedule: Schedule) {
        scheduleDao.deleteSchedule(schedule)
    }

    suspend fun deleteAll() {
        scheduleDao.deleteAllSchedules()
    }

    suspend fun getScheduleById(id: Int): Schedule? {
        return scheduleDao.getScheduleById(id)
    }
}
