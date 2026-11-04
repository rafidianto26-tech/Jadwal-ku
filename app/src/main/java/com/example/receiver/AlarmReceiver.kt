package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.util.ScheduleAlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sharedPrefs = context.getSharedPreferences("jadwalku_prefs", Context.MODE_PRIVATE)
        val isNotificationEnabled = sharedPrefs.getBoolean("notification_enabled", true)

        // If notifications are disabled globally in the app settings, do not show
        if (!isNotificationEnabled) {
            return
        }

        val isVibrateEnabled = sharedPrefs.getBoolean("vibration_enabled", true)
        val vibrationPatternName = sharedPrefs.getString("vibration_pattern", "Default") ?: "Default"

        val subjectName = intent.getStringExtra("subject_name") ?: "Pelajaran"
        val teacherName = intent.getStringExtra("teacher_name") ?: ""
        val roomName = intent.getStringExtra("room_name") ?: ""
        val startTime = intent.getStringExtra("start_time") ?: ""
        val notificationId = intent.getIntExtra("schedule_id", -1)

        if (notificationId == -1) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val vibrationPattern = if (isVibrateEnabled) {
            when (vibrationPatternName) {
                "Pendek" -> longArrayOf(0, 100, 80, 100)
                "Panjang" -> longArrayOf(0, 800, 400, 800)
                "Denyut" -> longArrayOf(0, 150, 100, 150, 100, 150, 100, 150)
                else -> longArrayOf(0, 250, 250, 250) // Default
            }
        } else {
            null
        }

        val channelId = "jadwal_pelajaran_alerts_v1"
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Create Channel for Android O (API 26) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Pengingat Jadwal Pelajaran",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Saluran untuk pengingat jadwal sekolah agar tidak terlambat"
                enableVibration(isVibrateEnabled)
                if (isVibrateEnabled && vibrationPattern != null) {
                    this.vibrationPattern = vibrationPattern
                }
                setSound(alarmSound, null)
                enableLights(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val descriptionText = when {
            roomName.isNotEmpty() && teacherName.isNotEmpty() -> "Mulai pukul $startTime di $roomName bersama Guru $teacherName"
            roomName.isNotEmpty() -> "Mulai pukul $startTime di $roomName"
            teacherName.isNotEmpty() -> "Mulai pukul $startTime bersama Guru $teacherName"
            else -> "Pelajaran baru akan dimulai pukul $startTime!"
        }

        // Standard alarm icon as a drawable fallback
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Waktunya Masuk: $subjectName!")
            .setContentText(descriptionText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(alarmSound)
            .setContentIntent(pendingIntent)

        if (isVibrateEnabled && vibrationPattern != null) {
            builder.setVibrate(vibrationPattern)
        } else {
            builder.setVibrate(longArrayOf(0))
        }

        notificationManager.notify(notificationId, builder.build())

        // Reschedule next week's occurrence of this alarm to maintain exact timing robustly
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context, this)
                val schedule = db.scheduleDao().getScheduleById(notificationId)
                if (schedule != null && schedule.isReminderEnabled) {
                    ScheduleAlarmScheduler.scheduleAlarm(context, schedule)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
