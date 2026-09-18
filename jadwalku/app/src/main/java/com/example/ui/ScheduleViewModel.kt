package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Schedule
import com.example.data.ScheduleRepository
import com.example.util.ScheduleAlarmScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

class ScheduleViewModel(private val repository: ScheduleRepository) : ViewModel() {

    private val _selectedDay = MutableStateFlow(getCurrentDayNameIndonesia())
    val selectedDay: StateFlow<String> = _selectedDay.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Live local time tracker in minutes (minutes from midnight)
    private val _currentMinutesOfDay = MutableStateFlow(getCurrentMinutes())
    val currentMinutesOfDay: StateFlow<Int> = _currentMinutesOfDay.asStateFlow()

    // Ticker that updates current minutes and day name every 30 seconds
    init {
        viewModelScope.launch {
            while (true) {
                _currentMinutesOfDay.value = getCurrentMinutes()
                _selectedDay.value = getCurrentDayNameIndonesia()
                delay(30000) // 30 seconds
            }
        }
    }

    // All schedules loaded from Room
    val allSchedules: StateFlow<List<Schedule>> = repository.allSchedules
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Schedules filtered by selected day and search query
    val filteredSchedules: StateFlow<List<Schedule>> = combine(
        allSchedules,
        _selectedDay,
        _searchQuery
    ) { schedules, day, query ->
        schedules.filter { schedule ->
            schedule.day.equals(day, ignoreCase = true) &&
                    (query.isEmpty() ||
                            schedule.subjectName.contains(query, ignoreCase = true) ||
                            schedule.teacherName.contains(query, ignoreCase = true) ||
                            schedule.roomName.contains(query, ignoreCase = true))
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Current active schedule card state, based on true relative local time
    val currentClass: StateFlow<Schedule?> = combine(
        allSchedules,
        _currentMinutesOfDay
    ) { schedules, currentMins ->
        val todayName = getCurrentDayNameIndonesia()
        schedules.firstOrNull { schedule ->
            if (!schedule.day.equals(todayName, ignoreCase = true)) return@firstOrNull false
            val startMins = timeToMinutes(schedule.startTime)
            val endMins = timeToMinutes(schedule.endTime)
            currentMins in startMins..endMins
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // Upcoming schedule card state, based on true relative local time and day
    val nextClass: StateFlow<Schedule?> = combine(
        allSchedules,
        _currentMinutesOfDay
    ) { schedules, currentMins ->
        val todayName = getCurrentDayNameIndonesia()
        schedules
            .filter { it.day.equals(todayName, ignoreCase = true) }
            .filter { timeToMinutes(it.startTime) > currentMins }
            .minByOrNull { timeToMinutes(it.startTime) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // Remaining minutes of active class
    val remainingMinsText: StateFlow<String> = combine(
        currentClass,
        _currentMinutesOfDay
    ) { activeClass, currentMins ->
        if (activeClass == null) return@combine ""
        val endMins = timeToMinutes(activeClass.endTime)
        val remaining = endMins - currentMins
        if (remaining > 0) "$remaining menit tersisa" else "Selesai"
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    fun selectDay(day: String) {
        _selectedDay.value = day
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addSchedule(schedule: Schedule, context: Context) {
        viewModelScope.launch {
            val generatedId = repository.insert(schedule)
            val insertedSchedule = schedule.copy(id = generatedId.toInt())
            if (insertedSchedule.isReminderEnabled) {
                ScheduleAlarmScheduler.scheduleAlarm(context, insertedSchedule)
            }
        }
    }

    fun updateSchedule(schedule: Schedule, context: Context) {
        viewModelScope.launch {
            repository.update(schedule)
            // Synchronize system alarm if reminder flag was changed
            if (schedule.isReminderEnabled) {
                ScheduleAlarmScheduler.scheduleAlarm(context, schedule)
            } else {
                ScheduleAlarmScheduler.cancelAlarm(context, schedule)
            }
        }
    }

    fun toggleReminder(schedule: Schedule, context: Context) {
        val updated = schedule.copy(isReminderEnabled = !schedule.isReminderEnabled)
        viewModelScope.launch {
            repository.update(updated)
            if (updated.isReminderEnabled) {
                ScheduleAlarmScheduler.scheduleAlarm(context, updated)
            } else {
                ScheduleAlarmScheduler.cancelAlarm(context, updated)
            }
        }
    }

    fun deleteSchedule(schedule: Schedule, context: Context) {
        viewModelScope.launch {
            repository.delete(schedule)
            ScheduleAlarmScheduler.cancelAlarm(context, schedule)
        }
    }

    fun resetToPresetSchedules(context: Context) {
        viewModelScope.launch {
            // Cancel all existing alarms and delete all schedules
            val currentList = allSchedules.value
            currentList.forEach { schedule ->
                ScheduleAlarmScheduler.cancelAlarm(context, schedule)
                repository.delete(schedule)
            }
            // Delay slightly to yield execution and let Room settle
            delay(200)

            // Preset data (same format as initial AppDatabase prepopulation)
            val presets = listOf(
                // Senin
                Schedule(day = "Senin", subjectName = "Tadarus & Duha + Literasi", teacherName = "Sekolah", roomName = "Kelas X TJKT 2", startTime = "07:00", endTime = "07:30", periodNumber = 0, colorHex = "#78909C"),
                Schedule(day = "Senin", subjectName = "Bahasa Indonesia", teacherName = "Pak Rexy", roomName = "Kelas X TJKT 2", startTime = "07:30", endTime = "08:10", periodNumber = 1, colorHex = "#7E57C2"),
                Schedule(day = "Senin", subjectName = "Bahasa Indonesia", teacherName = "Pak Rexy", roomName = "Kelas X TJKT 2", startTime = "08:10", endTime = "08:50", periodNumber = 2, colorHex = "#7E57C2"),
                Schedule(day = "Senin", subjectName = "Bahasa Inggris", teacherName = "Haifa", roomName = "Kelas X TJKT 2", startTime = "08:50", endTime = "09:30", periodNumber = 3, colorHex = "#26A69A"),
                Schedule(day = "Senin", subjectName = "Istirahat 1", teacherName = "Semua", roomName = "Kantin", startTime = "09:30", endTime = "09:45", periodNumber = 0, colorHex = "#B0BEC5"),
                Schedule(day = "Senin", subjectName = "Bahasa Inggris", teacherName = "Haifa", roomName = "Kelas X TJKT 2", startTime = "09:45", endTime = "10:25", periodNumber = 4, colorHex = "#26A69A"),
                Schedule(day = "Senin", subjectName = "IPAS", teacherName = "Pak Urip", roomName = "Lab IPAS", startTime = "10:25", endTime = "11:05", periodNumber = 5, colorHex = "#66BB6A"),
                Schedule(day = "Senin", subjectName = "IPAS", teacherName = "Pak Urip", roomName = "Lab IPAS", startTime = "11:05", endTime = "11:45", periodNumber = 6, colorHex = "#66BB6A"),
                Schedule(day = "Senin", subjectName = "Istirahat 2", teacherName = "Semua", roomName = "Kantin", startTime = "11:45", endTime = "12:10", periodNumber = 0, colorHex = "#B0BEC5"),
                Schedule(day = "Senin", subjectName = "IPAS", teacherName = "Pak Urip", roomName = "Lab IPAS", startTime = "12:10", endTime = "12:50", periodNumber = 7, colorHex = "#66BB6A"),
                Schedule(day = "Senin", subjectName = "Dasar TJKT", teacherName = "Rizmah", roomName = "Bengkel TJKT", startTime = "12:50", endTime = "13:30", periodNumber = 8, colorHex = "#42A5F5"),
                Schedule(day = "Senin", subjectName = "Dasar TJKT", teacherName = "Rizmah", roomName = "Bengkel TJKT", startTime = "13:30", endTime = "14:10", periodNumber = 9, colorHex = "#42A5F5"),
                Schedule(day = "Senin", subjectName = "Dasar TJKT", teacherName = "Rizmah", roomName = "Bengkel TJKT", startTime = "14:10", endTime = "14:50", periodNumber = 10, colorHex = "#42A5F5"),
                Schedule(day = "Senin", subjectName = "Dasar TJKT", teacherName = "Rizmah", roomName = "Bengkel TJKT", startTime = "14:50", endTime = "15:30", periodNumber = 11, colorHex = "#42A5F5"),
                 // Selasa
                Schedule(day = "Selasa", subjectName = "Tadarus & Duha + Literasi", teacherName = "Sekolah", roomName = "Kelas X TJKT 2", startTime = "07:00", endTime = "07:30", periodNumber = 0, colorHex = "#78909C"),
                Schedule(day = "Selasa", subjectName = "Pendidikan Pancasila", teacherName = "S. Abidin", roomName = "Kelas X TJKT 2", startTime = "07:30", endTime = "08:10", periodNumber = 1, colorHex = "#FF7043"),
                Schedule(day = "Selasa", subjectName = "Pendidikan Pancasila", teacherName = "S. Abidin", roomName = "Kelas X TJKT 2", startTime = "08:10", endTime = "08:50", periodNumber = 2, colorHex = "#FF7043"),
                Schedule(day = "Selasa", subjectName = "KKA", teacherName = "Asriani", roomName = "Kelas X TJKT 2", startTime = "08:50", endTime = "09:30", periodNumber = 3, colorHex = "#26C6DA"),
                Schedule(day = "Selasa", subjectName = "Istirahat 1", teacherName = "Semua", roomName = "Kantin", startTime = "09:30", endTime = "09:45", periodNumber = 0, colorHex = "#B0BEC5"),
                Schedule(day = "Selasa", subjectName = "KKA", teacherName = "Asriani", roomName = "Kelas X TJKT 2", startTime = "09:45", endTime = "10:25", periodNumber = 4, colorHex = "#26C6DA"),
                Schedule(day = "Selasa", subjectName = "Bahasa Indonesia", teacherName = "Pak Rexy", roomName = "Kelas X TJKT 2", startTime = "10:25", endTime = "11:05", periodNumber = 5, colorHex = "#7E57C2"),
                Schedule(day = "Selasa", subjectName = "Bahasa Indonesia", teacherName = "Pak Rexy", roomName = "Kelas X TJKT 2", startTime = "11:05", endTime = "11:45", periodNumber = 6, colorHex = "#7E57C2"),
                Schedule(day = "Selasa", subjectName = "Istirahat 2", teacherName = "Semua", roomName = "Kantin", startTime = "11:45", endTime = "12:10", periodNumber = 0, colorHex = "#B0BEC5"),
                Schedule(day = "Selasa", subjectName = "Matematika", teacherName = "Ragil", roomName = "Kelas X TJKT 2", startTime = "12:10", endTime = "12:50", periodNumber = 7, colorHex = "#EC407A"),
                Schedule(day = "Selasa", subjectName = "PJOK", teacherName = "Sujito", roomName = "Lapangan", startTime = "12:50", endTime = "13:30", periodNumber = 8, colorHex = "#9CCC65"),
                Schedule(day = "Selasa", subjectName = "PJOK", teacherName = "Sujito", roomName = "Lapangan", startTime = "13:30", endTime = "14:10", periodNumber = 9, colorHex = "#9CCC65"),
                Schedule(day = "Selasa", subjectName = "PJOK", teacherName = "Sujito", roomName = "Lapangan", startTime = "14:10", endTime = "14:50", periodNumber = 10, colorHex = "#9CCC65"),
                Schedule(day = "Selasa", subjectName = "PJOK", teacherName = "Sujito", roomName = "Lapangan", startTime = "14:50", endTime = "15:30", periodNumber = 11, colorHex = "#9CCC65"),
                // Rabu
                Schedule(day = "Rabu", subjectName = "Tadarus & Duha + Literasi", teacherName = "Sekolah", roomName = "Kelas X TJKT 2", startTime = "07:00", endTime = "07:30", periodNumber = 0, colorHex = "#78909C"),
                Schedule(day = "Rabu", subjectName = "Pendidikan Agama Islam", teacherName = "Faiz", roomName = "Kelas X TJKT 2", startTime = "07:30", endTime = "08:10", periodNumber = 1, colorHex = "#26A69A"),
                Schedule(day = "Rabu", subjectName = "Pendidikan Agama Islam", teacherName = "Faiz", roomName = "Kelas X TJKT 2", startTime = "08:10", endTime = "08:50", periodNumber = 2, colorHex = "#26A69A"),
                Schedule(day = "Rabu", subjectName = "Pendidikan Agama Islam", teacherName = "Faiz", roomName = "Kelas X TJKT 2", startTime = "08:50", endTime = "09:30", periodNumber = 3, colorHex = "#26A69A"),
                Schedule(day = "Rabu", subjectName = "Istirahat 1", teacherName = "Semua", roomName = "Kantin", startTime = "09:30", endTime = "09:45", periodNumber = 0, colorHex = "#B0BEC5"),
                Schedule(day = "Rabu", subjectName = "Dasar TJKT", teacherName = "Rizmah", roomName = "Bengkel TJKT", startTime = "09:45", endTime = "10:25", periodNumber = 4, colorHex = "#42A5F5"),
                Schedule(day = "Rabu", subjectName = "Dasar TJKT", teacherName = "Rizmah", roomName = "Bengkel TJKT", startTime = "10:25", endTime = "11:05", periodNumber = 5, colorHex = "#42A5F5"),
                Schedule(day = "Rabu", subjectName = "Dasar TJKT", teacherName = "Rizmah", roomName = "Bengkel TJKT", startTime = "11:05", endTime = "11:45", periodNumber = 6, colorHex = "#42A5F5"),
                Schedule(day = "Rabu", subjectName = "Istirahat 2", teacherName = "Semua", roomName = "Kantin", startTime = "11:45", endTime = "12:10", periodNumber = 0, colorHex = "#B0BEC5"),
                Schedule(day = "Rabu", subjectName = "Dasar TJKT", teacherName = "Rizmah", roomName = "Bengkel TJKT", startTime = "12:10", endTime = "12:50", periodNumber = 7, colorHex = "#42A5F5"),
                Schedule(day = "Rabu", subjectName = "Informatika", teacherName = "Alya", roomName = "Lab Komputer", startTime = "12:50", endTime = "13:30", periodNumber = 8, colorHex = "#AB47BC"),
                Schedule(day = "Rabu", subjectName = "Informatika", teacherName = "Alya", roomName = "Lab Komputer", startTime = "13:30", endTime = "14:10", periodNumber = 9, colorHex = "#AB47BC"),
                Schedule(day = "Rabu", subjectName = "Informatika", teacherName = "Alya", roomName = "Lab Komputer", startTime = "14:10", endTime = "14:50", periodNumber = 10, colorHex = "#AB47BC"),
                Schedule(day = "Rabu", subjectName = "Informatika", teacherName = "Alya", roomName = "Lab Komputer", startTime = "14:50", endTime = "15:30", periodNumber = 11, colorHex = "#AB47BC"),
                 // Kamis
                Schedule(day = "Kamis", subjectName = "Tadarus & Duha + Literasi", teacherName = "Sekolah", roomName = "Kelas X TJKT 2", startTime = "07:00", endTime = "07:30", periodNumber = 0, colorHex = "#78909C"),
                Schedule(day = "Kamis", subjectName = "Seni Budaya", teacherName = "Untung", roomName = "Kelas X TJKT 2", startTime = "07:30", endTime = "08:10", periodNumber = 1, colorHex = "#FFA726"),
                Schedule(day = "Kamis", subjectName = "Seni Budaya", teacherName = "Untung", roomName = "Kelas X TJKT 2", startTime = "08:10", endTime = "08:50", periodNumber = 2, colorHex = "#FFA726"),
                Schedule(day = "Kamis", subjectName = "Sejarah", teacherName = "S. Abidin", roomName = "Kelas X TJKT 2", startTime = "08:50", endTime = "09:30", periodNumber = 3, colorHex = "#8D6E63"),
                Schedule(day = "Kamis", subjectName = "Istirahat 1", teacherName = "Semua", roomName = "Kantin", startTime = "09:30", endTime = "09:45", periodNumber = 0, colorHex = "#B0BEC5"),
                Schedule(day = "Kamis", subjectName = "Sejarah", teacherName = "S. Abidin", roomName = "Kelas X TJKT 2", startTime = "09:45", endTime = "10:25", periodNumber = 4, colorHex = "#8D6E63"),
                Schedule(day = "Kamis", subjectName = "Matematika", teacherName = "Ragil", roomName = "Kelas X TJKT 2", startTime = "10:25", endTime = "11:05", periodNumber = 5, colorHex = "#EC407A"),
                Schedule(day = "Kamis", subjectName = "Matematika", teacherName = "Ragil", roomName = "Kelas X TJKT 2", startTime = "11:05", endTime = "11:45", periodNumber = 6, colorHex = "#EC407A"),
                Schedule(day = "Kamis", subjectName = "Istirahat 2", teacherName = "Semua", roomName = "Kantin", startTime = "11:45", endTime = "12:10", periodNumber = 0, colorHex = "#B0BEC5"),
                Schedule(day = "Kamis", subjectName = "IPAS", teacherName = "Pak Urip", roomName = "Lab IPAS", startTime = "12:10", endTime = "12:50", periodNumber = 7, colorHex = "#66BB6A"),
                Schedule(day = "Kamis", subjectName = "IPAS", teacherName = "Pak Urip", roomName = "Lab IPAS", startTime = "12:50", endTime = "13:30", periodNumber = 8, colorHex = "#66BB6A"),
                Schedule(day = "Kamis", subjectName = "IPAS", teacherName = "Pak Urip", roomName = "Lab IPAS", startTime = "13:30", endTime = "14:10", periodNumber = 9, colorHex = "#66BB6A"),
                Schedule(day = "Kamis", subjectName = "BK", teacherName = "Nisa", roomName = "Ruang BK", startTime = "14:10", endTime = "14:50", periodNumber = 10, colorHex = "#26A69A"),
                Schedule(day = "Kamis", subjectName = "BK", teacherName = "Nisa", roomName = "Ruang BK", startTime = "14:50", endTime = "15:30", periodNumber = 11, colorHex = "#26A69A"),
                 // Jumat
                Schedule(day = "Jumat", subjectName = "Tadarus & Duha + Literasi", teacherName = "Sekolah", roomName = "Kelas X TJKT 2", startTime = "07:00", endTime = "07:30", periodNumber = 0, colorHex = "#78909C"),
                Schedule(day = "Jumat", subjectName = "Kemuh", teacherName = "Faiz", roomName = "Kelas X TJKT 2", startTime = "07:30", endTime = "08:10", periodNumber = 1, colorHex = "#26C6DA"),
                Schedule(day = "Jumat", subjectName = "Dasar TJKT", teacherName = "Rizmah", roomName = "Bengkel TJKT", startTime = "08:10", endTime = "08:50", periodNumber = 2, colorHex = "#42A5F5"),
                Schedule(day = "Jumat", subjectName = "Dasar TJKT", teacherName = "Rizmah", roomName = "Bengkel TJKT", startTime = "08:50", endTime = "09:30", periodNumber = 3, colorHex = "#42A5F5"),
                Schedule(day = "Jumat", subjectName = "Istirahat 1", teacherName = "Semua", roomName = "Kantin", startTime = "09:30", endTime = "09:45", periodNumber = 0, colorHex = "#B0BEC5"),
                Schedule(day = "Jumat", subjectName = "Dasar TJKT", teacherName = "Rizmah", roomName = "Bengkel TJKT", startTime = "09:45", endTime = "10:25", periodNumber = 4, colorHex = "#42A5F5"),
                Schedule(day = "Jumat", subjectName = "Dasar TJKT", teacherName = "Rizmah", roomName = "Bengkel TJKT", startTime = "10:25", endTime = "11:05", periodNumber = 5, colorHex = "#42A5F5"),
                Schedule(day = "Jumat", subjectName = "Bahasa Inggris", teacherName = "Haifa", roomName = "Kelas X TJKT 2", startTime = "11:05", endTime = "11:45", periodNumber = 6, colorHex = "#26A69A"),
                Schedule(day = "Jumat", subjectName = "Istirahat 2", teacherName = "Semua", roomName = "Kantin", startTime = "11:45", endTime = "12:10", periodNumber = 0, colorHex = "#B0BEC5"),
                Schedule(day = "Jumat", subjectName = "Bahasa Inggris", teacherName = "Haifa", roomName = "Kelas X TJKT 2", startTime = "12:10", endTime = "12:50", periodNumber = 7, colorHex = "#26A69A"),
                Schedule(day = "Jumat", subjectName = "Bahasa Arab", teacherName = "Sekar", roomName = "Kelas X TJKT 2", startTime = "12:50", endTime = "13:30", periodNumber = 8, colorHex = "#FF5722"),
                Schedule(day = "Jumat", subjectName = "Bahasa Jawa", teacherName = "Bu Evi", roomName = "Kelas X TJKT 2", startTime = "13:30", endTime = "14:10", periodNumber = 9, colorHex = "#9E9E9E"),
                Schedule(day = "Jumat", subjectName = "Bahasa Jawa", teacherName = "Bu Evi", roomName = "Kelas X TJKT 2", startTime = "14:10", endTime = "14:50", periodNumber = 10, colorHex = "#9E9E9E"),
                Schedule(day = "Jumat", subjectName = "Bahasa Jawa", teacherName = "Bu Evi", roomName = "Kelas X TJKT 2", startTime = "14:50", endTime = "15:30", periodNumber = 11, colorHex = "#9E9E9E")
            )
            presets.forEach { repository.insert(it) }
        }
    }

    // Helper functions
    private fun getCurrentMinutes(): Int {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }

    private fun getCurrentDayNameIndonesia(): String {
        val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return when (dayOfWeek) {
            Calendar.MONDAY -> "Senin"
            Calendar.TUESDAY -> "Selasa"
            Calendar.WEDNESDAY -> "Rabu"
            Calendar.THURSDAY -> "Kamis"
            Calendar.FRIDAY -> "Jumat"
            Calendar.SATURDAY -> "Sabtu"
            Calendar.SUNDAY -> "Minggu"
            else -> "Senin"
        }
    }

    private fun timeToMinutes(timeStr: String): Int {
        val parts = timeStr.split(":")
        if (parts.size != 2) return 0
        val h = parts[0].toIntOrNull() ?: return 0
        val m = parts[1].toIntOrNull() ?: return 0
        return h * 60 + m
    }
}

// ViewModel Factory definition
class ScheduleViewModelFactory(private val repository: ScheduleRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScheduleViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ScheduleViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
