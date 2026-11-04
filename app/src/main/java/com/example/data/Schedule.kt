package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedules")
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val day: String,          // e.g., "Senin", "Selasa", "Rabu", "Kamis", "Jumat"
    val subjectName: String,  // e.g., "Bahasa Indonesia"
    val teacherName: String,  // e.g., "Roxy"
    val roomName: String,     // e.g., "Kelas X TJKT 2" or "Lapangan"
    val startTime: String,    // e.g., "07:30"
    val endTime: String,      // e.g., "08:10"
    val periodNumber: Int,    // 1 to 11, or 0 for non-subject slots (literasi / break)
    val colorHex: String,     // Hex color code to represent subject category beautifully
    val isReminderEnabled: Boolean = false
)
