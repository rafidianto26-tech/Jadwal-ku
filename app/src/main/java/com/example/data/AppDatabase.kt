package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Schedule::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "schedule_database"
                )
                .addCallback(AppDatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class AppDatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        prepopulateDatabase(database.scheduleDao())
                    }
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        val dao = database.scheduleDao()
                        dao.updateTeacherName("Roxy", "Pak Rexy")
                        dao.updateTeacherName("Elvi", "Bu Evi")
                        dao.updateTeacherName("Ulip", "Pak Urip")
                    }
                }
            }
        }

        private suspend fun prepopulateDatabase(dao: ScheduleDao) {
            // Monday (Senin)
            dao.insertSchedule(Schedule(day = "Senin", subjectName = "Tadarus & Duha + Literasi", teacherName = "Sekolah", roomName = "Kelas X TJKT 2", startTime = "07:00", endTime = "07:30", periodNumber = 0, colorHex = "#78909C"))
            dao.insertSchedule(Schedule(day = "Senin", subjectName = "Bahasa Indonesia", teacherName = "Pak Rexy", roomName = "Kelas X TJKT 2", startTime = "07:30", endTime = "08:10", periodNumber = 1, colorHex = "#7E57C2"))
            dao.insertSchedule(Schedule(day = "Senin", subjectName = "Bahasa Indonesia", teacherName = "Pak Rexy", roomName = "Kelas X TJKT 2", startTime = "08:10", endTime = "08:50", periodNumber = 2, colorHex = "#7E57C2"))
            dao.insertSchedule(Schedule(day = "Senin", subjectName = "Bahasa Inggris", teacherName = "Haifa", roomName = "Kelas X TJKT 2", startTime = "08:50", endTime = "09:30", periodNumber = 3, colorHex = "#26A69A"))
            dao.insertSchedule(Schedule(day = "Senin", subjectName = "Istirahat 1", teacherName = "Semua", roomName = "Kantin", startTime = "09:30", endTime = "09:45", periodNumber = 0, colorHex = "#B0BEC5"))
            dao.insertSchedule(Schedule(day = "Senin", subjectName = "Bahasa Inggris", teacherName = "Haifa", roomName = "Kelas X TJKT 2", startTime = "09:45", endTime = "10:25", periodNumber = 4, colorHex = "#26A69A"))
            dao.insertSchedule(Schedule(day = "Senin", subjectName = "IPAS", teacherName = "Pak Urip", roomName = "Lab IPAS", startTime = "10:25", endTime = "11:05", periodNumber = 5, colorHex = "#66BB6A"))
            dao.insertSchedule(Schedule(day = "Senin", subjectName = "IPAS", teacherName = "Pak Urip", roomName = "Lab IPAS", startTime = "11:05", endTime = "11:45", periodNumber = 6, colorHex = "#66BB6A"))
            dao.insertSchedule(Schedule(day = "Senin", subjectName = "Istirahat 2", teacherName = "Semua", roomName = "Kantin", startTime = "11:45", endTime = "12:10", periodNumber = 0, colorHex = "#B0BEC5"))
            dao.insertSchedule(Schedule(day = "Senin", subjectName = "IPAS", teacherName = "Pak Urip", roomName = "Lab IPAS", startTime = "12:10", endTime = "12:50", periodNumber = 7, colorHex = "#66BB6A"))
            dao.insertSchedule(Schedule(day = "Senin", subjectName = "Dasar TJKT", teacherName = "Rizmah", roomName = "Bengkel TJKT", startTime = "12:50", endTime = "13:30", periodNumber = 8, colorHex = "#42A5F5"))
            dao.insertSchedule(Schedule(day = "Senin", subjectName = "Dasar TJKT", teacherName = "Rizmah", roomName = "Bengkel TJKT", startTime = "13:30", endTime = "14:10", periodNumber = 9, colorHex = "#42A5F5"))
            dao.insertSchedule(Schedule(day = "Senin", subjectName = "Dasar TJKT", teacherName = "Rizmah", roomName = "Bengkel TJKT", startTime = "14:10", endTime = "14:50", periodNumber = 10, colorHex = "#42A5F5"))
            dao.insertSchedule(Schedule(day = "Senin", subjectName = "Dasar TJKT", teacherName = "Rizmah", roomName = "Bengkel TJKT", startTime = "14:50", endTime = "15:30", periodNumber = 11, colorHex = "#42A5F5"))

            // Tuesday (Selasa)
            dao.insertSchedule(Schedule(day = "Selasa", subjectName = "Tadarus & Duha + Literasi", teacherName = "Sekolah", roomName = "Kelas X TJKT 2", startTime = "07:00", endTime = "07:30", periodNumber = 0, colorHex = "#78909C"))
            dao.insertSchedule(Schedule(day = "Selasa", subjectName = "Pendidikan Pancasila", teacherName = "S. Abidin", roomName = "Kelas X TJKT 2", startTime = "07:30", endTime = "08:10", periodNumber = 1, colorHex = "#FF7043"))
            dao.insertSchedule(Schedule(day = "Selasa", subjectName = "Pendidikan Pancasila", teacherName = "S. Abidin", roomName = "Kelas X TJKT 2", startTime = "08:10", endTime = "08:50", periodNumber = 2, colorHex = "#FF7043"))
            dao.insertSchedule(Schedule(day = "Selasa", subjectName = "KKA", teacherName = "Asriani", roomName = "Kelas X TJKT 2", startTime = "08:50", endTime = "09:30", periodNumber = 3, colorHex = "#26C6DA"))
            dao.insertSchedule(Schedule(day = "Selasa", subjectName = "Istirahat 1", teacherName = "Semua", roomName = "Kantin", startTime = "09:30", endTime = "09:45", periodNumber = 0, colorHex = "#B0BEC5"))
            dao.insertSchedule(Schedule(day = "Selasa", subjectName = "KKA", teacherName = "Asriani", roomName = "Kelas X TJKT 2", startTime = "09:45", endTime = "10:25", periodNumber = 4, colorHex = "#26C6DA"))
            dao.insertSchedule(Schedule(day = "Selasa", subjectName = "Bahasa Indonesia", teacherName = "Pak Rexy", roomName = "Kelas X TJKT 2", startTime = "10:25", endTime = "11:05", periodNumber = 5, colorHex = "#7E57C2"))
            dao.insertSchedule(Schedule(day = "Selasa", subjectName = "Bahasa Indonesia", teacherName = "Pak Rexy", roomName = "Kelas X TJKT 2", startTime = "11:05", endTime = "11:45", periodNumber = 6, colorHex = "#7E57C2"))
            dao.insertSchedule(Schedule(day = "Selasa", subjectName = "Istirahat 2", teacherName = "Semua", roomName = "Kantin", startTime = "11:45", endTime = "12:10", periodNumber = 0, colorHex = "#B0BEC5"))
            dao.insertSchedule(Schedule(day = "Selasa", subjectName = "Matematika", teacherName = "Ragil", roomName = "Kelas X TJKT 2", startTime = "12:10", endTime = "12:50", periodNumber = 7, colorHex = "#EC407A"))
            dao.insertSchedule(Schedule(day = "Selasa", subjectName = "PJOK", teacherName = "Sujito", roomName = "Lapangan", startTime = "12:50", endTime = "13:30", periodNumber = 8, colorHex = "#9CCC65"))
            dao.insertSchedule(Schedule(day = "Selasa", subjectName = "PJOK", teacherName = "Sujito", roomName = "Lapangan", startTime = "13:30", endTime = "14:10", periodNumber = 9, colorHex = "#9CCC65"))
            dao.insertSchedule(Schedule(day = "Selasa", subjectName = "PJOK", teacherName = "Sujito", roomName = "Lapangan", startTime = "14:10", endTime = "14:50", periodNumber = 10, colorHex = "#9CCC65"))
            dao.insertSchedule(Schedule(day = "Selasa", subjectName = "PJOK", teacherName = "Sujito", roomName = "Lapangan", startTime = "14:50", endTime = "15:30", periodNumber = 11, colorHex = "#9CCC65"))

            // Wednesday (Rabu)
            dao.insertSchedule(Schedule(day = "Rabu", subjectName = "Tadarus & Duha + Literasi", teacherName = "Sekolah", roomName = "Kelas X TJKT 2", startTime = "07:00", endTime = "07:30", periodNumber = 0, colorHex = "#78909C"))
            dao.insertSchedule(Schedule(day = "Rabu", subjectName = "Pendidikan Agama Islam", teacherName = "Faiz", roomName = "Kelas X TJKT 2", startTime = "07:30", endTime = "08:10", periodNumber = 1, colorHex = "#26A69A"))
            dao.insertSchedule(Schedule(day = "Rabu", subjectName = "Pendidikan Agama Islam", teacherName = "Faiz", roomName = "Kelas X TJKT 2", startTime = "08:10", endTime = "08:50", periodNumber = 2, colorHex = "#26A69A"))
            dao.insertSchedule(Schedule(day = "Rabu", subjectName = "Pendidikan Agama Islam", teacherName = "Faiz", roomName = "Kelas X TJKT 2", startTime = "08:50", endTime = "09:30", periodNumber = 3, colorHex = "#26A69A"))
            dao.insertSchedule(Schedule(day = "Rabu", subjectName = "Istirahat 1", teacherName = "Semua", roomName = "Kantin", startTime = "09:30", endTime = "09:45", periodNumber = 0, colorHex = "#B0BEC5"))
            dao.insertSchedule(Schedule(day = "Rabu", subjectName = "Dasar TJKT", teacherName = "Rizmah", roomName = "Bengkel TJKT", startTime = "09:45", endTime = "10:25", periodNumber = 4, colorHex = "#42A5F5"))
            dao.insertSchedule(Schedule(day = "Rabu", subjectName = "Dasar TJKT", teacherName = "Rizmah", roomName = "Bengkel TJKT", startTime = "10:25", endTime = "11:05", periodNumber = 5, colorHex = "#42A5F5"))
            dao.insertSchedule(Schedule(day = "Rabu", subjectName = "Dasar TJKT", teacherName = "Rizmah", roomName = "Bengkel TJKT", startTime = "11:05", endTime = "11:45", periodNumber = 6, colorHex = "#42A5F5"))
            dao.insertSchedule(Schedule(day = "Rabu", subjectName = "Istirahat 2", teacherName = "Semua", roomName = "Kantin", startTime = "11:45", endTime = "12:10", periodNumber = 0, colorHex = "#B0BEC5"))
            dao.insertSchedule(Schedule(day = "Rabu", subjectName = "Dasar TJKT", teacherName = "Rizmah", roomName = "Bengkel TJKT", startTime = "12:10", endTime = "12:50", periodNumber = 7, colorHex = "#42A5F5"))
            dao.insertSchedule(Schedule(day = "Rabu", subjectName = "Informatika", teacherName = "Alya", roomName = "Lab Komputer", startTime = "12:50", endTime = "13:30", periodNumber = 8, colorHex = "#AB47BC"))
            dao.insertSchedule(Schedule(day = "Rabu", subjectName = "Informatika", teacherName = "Alya", roomName = "Lab Komputer", startTime = "13:30", endTime = "14:10", periodNumber = 9, colorHex = "#AB47BC"))
            dao.insertSchedule(Schedule(day = "Rabu", subjectName = "Informatika", teacherName = "Alya", roomName = "Lab Komputer", startTime = "14:10", endTime = "14:50", periodNumber = 10, colorHex = "#AB47BC"))
            dao.insertSchedule(Schedule(day = "Rabu", subjectName = "Informatika", teacherName = "Alya", roomName = "Lab Komputer", startTime = "14:50", endTime = "15:30", periodNumber = 11, colorHex = "#AB47BC"))

            // Thursday (Kamis)
            dao.insertSchedule(Schedule(day = "Kamis", subjectName = "Tadarus & Duha + Literasi", teacherName = "Sekolah", roomName = "Kelas X TJKT 2", startTime = "07:00", endTime = "07:30", periodNumber = 0, colorHex = "#78909C"))
            dao.insertSchedule(Schedule(day = "Kamis", subjectName = "Seni Budaya", teacherName = "Untung", roomName = "Kelas X TJKT 2", startTime = "07:30", endTime = "08:10", periodNumber = 1, colorHex = "#FFA726"))
            dao.insertSchedule(Schedule(day = "Kamis", subjectName = "Seni Budaya", teacherName = "Untung", roomName = "Kelas X TJKT 2", startTime = "08:10", endTime = "08:50", periodNumber = 2, colorHex = "#FFA726"))
            dao.insertSchedule(Schedule(day = "Kamis", subjectName = "Sejarah", teacherName = "S. Abidin", roomName = "Kelas X TJKT 2", startTime = "08:50", endTime = "09:30", periodNumber = 3, colorHex = "#8D6E63"))
            dao.insertSchedule(Schedule(day = "Kamis", subjectName = "Istirahat 1", teacherName = "Semua", roomName = "Kantin", startTime = "09:30", endTime = "09:45", periodNumber = 0, colorHex = "#B0BEC5"))
            dao.insertSchedule(Schedule(day = "Kamis", subjectName = "Sejarah", teacherName = "S. Abidin", roomName = "Kelas X TJKT 2", startTime = "09:45", endTime = "10:25", periodNumber = 4, colorHex = "#8D6E63"))
            dao.insertSchedule(Schedule(day = "Kamis", subjectName = "Matematika", teacherName = "Ragil", roomName = "Kelas X TJKT 2", startTime = "10:25", endTime = "11:05", periodNumber = 5, colorHex = "#EC407A"))
            dao.insertSchedule(Schedule(day = "Kamis", subjectName = "Matematika", teacherName = "Ragil", roomName = "Kelas X TJKT 2", startTime = "11:05", endTime = "11:45", periodNumber = 6, colorHex = "#EC407A"))
            dao.insertSchedule(Schedule(day = "Kamis", subjectName = "Istirahat 2", teacherName = "Semua", roomName = "Kantin", startTime = "11:45", endTime = "12:10", periodNumber = 0, colorHex = "#B0BEC5"))
            dao.insertSchedule(Schedule(day = "Kamis", subjectName = "IPAS", teacherName = "Pak Urip", roomName = "Lab IPAS", startTime = "12:10", endTime = "12:50", periodNumber = 7, colorHex = "#66BB6A"))
            dao.insertSchedule(Schedule(day = "Kamis", subjectName = "IPAS", teacherName = "Pak Urip", roomName = "Lab IPAS", startTime = "12:50", endTime = "13:30", periodNumber = 8, colorHex = "#66BB6A"))
            dao.insertSchedule(Schedule(day = "Kamis", subjectName = "IPAS", teacherName = "Pak Urip", roomName = "Lab IPAS", startTime = "13:30", endTime = "14:10", periodNumber = 9, colorHex = "#66BB6A"))
            dao.insertSchedule(Schedule(day = "Kamis", subjectName = "BK", teacherName = "Nisa", roomName = "Ruang BK", startTime = "14:10", endTime = "14:50", periodNumber = 10, colorHex = "#26A69A"))
            dao.insertSchedule(Schedule(day = "Kamis", subjectName = "BK", teacherName = "Nisa", roomName = "Ruang BK", startTime = "14:50", endTime = "15:30", periodNumber = 11, colorHex = "#26A69A"))

            // Friday (Jumat)
            dao.insertSchedule(Schedule(day = "Jumat", subjectName = "Tadarus & Duha + Literasi", teacherName = "Sekolah", roomName = "Kelas X TJKT 2", startTime = "07:00", endTime = "07:30", periodNumber = 0, colorHex = "#78909C"))
            dao.insertSchedule(Schedule(day = "Jumat", subjectName = "Kemuh", teacherName = "Faiz", roomName = "Kelas X TJKT 2", startTime = "07:30", endTime = "08:10", periodNumber = 1, colorHex = "#26C6DA"))
            dao.insertSchedule(Schedule(day = "Jumat", subjectName = "Dasar TJKT", teacherName = "Rizmah", roomName = "Bengkel TJKT", startTime = "08:10", endTime = "08:50", periodNumber = 2, colorHex = "#42A5F5"))
            dao.insertSchedule(Schedule(day = "Jumat", subjectName = "Dasar TJKT", teacherName = "Rizmah", roomName = "Bengkel TJKT", startTime = "08:50", endTime = "09:30", periodNumber = 3, colorHex = "#42A5F5"))
            dao.insertSchedule(Schedule(day = "Jumat", subjectName = "Istirahat 1", teacherName = "Semua", roomName = "Kantin", startTime = "09:30", endTime = "09:45", periodNumber = 0, colorHex = "#B0BEC5"))
            dao.insertSchedule(Schedule(day = "Jumat", subjectName = "Dasar TJKT", teacherName = "Rizmah", roomName = "Bengkel TJKT", startTime = "09:45", endTime = "10:25", periodNumber = 4, colorHex = "#42A5F5"))
            dao.insertSchedule(Schedule(day = "Jumat", subjectName = "Dasar TJKT", teacherName = "Rizmah", roomName = "Bengkel TJKT", startTime = "10:25", endTime = "11:05", periodNumber = 5, colorHex = "#42A5F5"))
            dao.insertSchedule(Schedule(day = "Jumat", subjectName = "Bahasa Inggris", teacherName = "Haifa", roomName = "Kelas X TJKT 2", startTime = "11:05", endTime = "11:45", periodNumber = 6, colorHex = "#26A69A"))
            dao.insertSchedule(Schedule(day = "Jumat", subjectName = "Istirahat 2", teacherName = "Semua", roomName = "Kantin", startTime = "11:45", endTime = "12:10", periodNumber = 0, colorHex = "#B0BEC5"))
            dao.insertSchedule(Schedule(day = "Jumat", subjectName = "Bahasa Inggris", teacherName = "Haifa", roomName = "Kelas X TJKT 2", startTime = "12:10", endTime = "12:50", periodNumber = 7, colorHex = "#26A69A"))
            dao.insertSchedule(Schedule(day = "Jumat", subjectName = "Bahasa Arab", teacherName = "Sekar", roomName = "Kelas X TJKT 2", startTime = "12:50", endTime = "13:30", periodNumber = 8, colorHex = "#FF5722"))
            dao.insertSchedule(Schedule(day = "Jumat", subjectName = "Bahasa Jawa", teacherName = "Bu Evi", roomName = "Kelas X TJKT 2", startTime = "13:30", endTime = "14:10", periodNumber = 9, colorHex = "#9E9E9E"))
            dao.insertSchedule(Schedule(day = "Jumat", subjectName = "Bahasa Jawa", teacherName = "Bu Evi", roomName = "Kelas X TJKT 2", startTime = "14:10", endTime = "14:50", periodNumber = 10, colorHex = "#9E9E9E"))
            dao.insertSchedule(Schedule(day = "Jumat", subjectName = "Bahasa Jawa", teacherName = "Bu Evi", roomName = "Kelas X TJKT 2", startTime = "14:50", endTime = "15:30", periodNumber = 11, colorHex = "#9E9E9E"))
        }
    }
}
