package com.example

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import com.example.receiver.AlarmReceiver
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.data.Schedule
import com.example.data.ScheduleRepository
import com.example.ui.ScheduleViewModel
import com.example.ui.ScheduleViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        val msg = if (isGranted) {
            "Notifikasi diizinkan!"
        } else {
            "Notifikasi dinonaktifkan. Pengingat tidak akan muncul di status bar."
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permissions for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Single Constructor Injection as recommended
        val database = AppDatabase.getDatabase(this, lifecycleScope)
        val repository = ScheduleRepository(database.scheduleDao())

        val viewModel: ScheduleViewModel by viewModels {
            ScheduleViewModelFactory(repository)
        }

        // Initialize and sync exact alarms for all enabled reminders at startup of the app
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val list = database.scheduleDao().getAllSchedulesList()
                list.forEach { s ->
                    if (s.isReminderEnabled) {
                        com.example.util.ScheduleAlarmScheduler.scheduleAlarm(this@MainActivity, s)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        setContent {
            MainAppContainer(viewModel = viewModel)
        }
    }
}

enum class UserRole {
    NONE, OWNER, UMUM
}

@Composable
fun MainAppContainer(viewModel: ScheduleViewModel) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("jadwalku_prefs", Context.MODE_PRIVATE) }
    val savedRoleName = remember { sharedPrefs.getString("user_role", "NONE") ?: "NONE" }
    var currentRole by remember { mutableStateOf(UserRole.valueOf(savedRoleName)) }

    MyApplicationTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            if (currentRole == UserRole.NONE) {
                LoginScreen(
                    onRoleSelected = { role ->
                        sharedPrefs.edit().putString("user_role", role.name).apply()
                        currentRole = role
                    }
                )
            } else {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    ScheduleAppContent(
                        viewModel = viewModel,
                        isOwner = currentRole == UserRole.OWNER,
                        onLogout = {
                            sharedPrefs.edit().remove("user_role").remove("login_user_name").apply()
                            currentRole = UserRole.NONE
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onRoleSelected: (UserRole) -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("jadwalku_prefs", Context.MODE_PRIVATE) }

    // Read dynamic owner password, defaulting to BuildConfig.OWNER_PASSWORD
    val defaultPassword = BuildConfig.OWNER_PASSWORD
    val currentPassword = remember { sharedPrefs.getString("owner_password", defaultPassword) ?: defaultPassword }

    var isOwnerSelected by remember { mutableStateOf(false) }
    var isMuridSelected by remember { mutableStateOf(false) }

    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isPasswordError by remember { mutableStateOf(false) }

    var nameInput by remember { mutableStateOf("") }
    var isNameError by remember { mutableStateOf(false) }
    var nisInput by remember { mutableStateOf("") }
    var isNisError by remember { mutableStateOf(false) }
    var studentLoginErrorMessage by remember { mutableStateOf<String?>(null) }

    val studentNisMap = remember {
        mapOf(
            "6745" to "AHYA DIA ALFUADI",
            "6746" to "AMANDA TSALAS CENDIKIA",
            "6747" to "AMARUL IKHSAN RIZQI NASUTION",
            "6748" to "AYU RIZMA FIRAWATI",
            "6749" to "CHANYA NUR AZIZAH",
            "6750" to "DEWI SHOLIHAH",
            "6751" to "FAHRIDZAL ALKHAFI",
            "6752" to "FAQIH LARENDA SENTANU",
            "6753" to "FIRMAN RIDHO ERSAPUTRA",
            "6754" to "HABIB MAULANA",
            "6755" to "IQMAL MAULANA",
            "6756" to "JULIA ROSSA",
            "6757" to "MUHAMMAD FATHIR VIRIGIAWAN",
            "6758" to "MUHAMMAD MIFTAKHUSSIFA",
            "6759" to "MUKTI SETIAWAN",
            "6760" to "NAZERI FANO YULIANSYAH",
            "6761" to "NUR LAILY 'AISYAH",
            "6762" to "RIFKI DWI PRASETYO",
            "6763" to "SAFRINA",
            "6764" to "SELVINA",
            "6765" to "SYAFANA AYUDYA HUMAYRA",
            "6766" to "XENA ADIKA DWITAMA",
            "6767" to "YULI PUTRA PRASETIO"
        )
    }

    fun normalizeName(name: String): String {
        return name.uppercase()
            .replace("[^A-Z0-9]".toRegex(), "")
            .replace("\\s+".toRegex(), "")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Identity Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .clip(RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "JadwalKu",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "SMK MUH 1 PURWOKERTO • X TJKT 2",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }

        // Subtitle Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Text(
                text = "Selamat datang! Silakan masukkan identitas Anda untuk masuk ke sistem jadwal kelas.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        }

        // Option Card 1: Siswa / Murid
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    isMuridSelected = true
                    isOwnerSelected = false
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isMuridSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isMuridSelected) 4.dp else 1.dp),
            border = if (isMuridSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Siswa / Murid",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Akses membaca jadwal. Harus mengisi nama sebagai identitas login.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Option Card 2: Guru / Owner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    isOwnerSelected = true
                    isMuridSelected = false
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isOwnerSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isOwnerSelected) 4.dp else 1.dp),
            border = if (isOwnerSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Guru",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Akses edit penuh. Mengubah, menambah pelajaran, dan konfigurasi.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Expanded Siswa login name form block
        AnimatedVisibility(
            visible = isMuridSelected,
            enter = expandVertically(animationSpec = spring()) + fadeIn(),
            exit = shrinkVertically(animationSpec = spring()) + fadeOut(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = {
                        nameInput = it
                        isNameError = false
                        studentLoginErrorMessage = null
                    },
                    label = { Text("Nama Lengkap Anda (Sesuai Absen)") },
                    placeholder = { Text("Contoh: AHYA DIA ALFUADI") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    isError = isNameError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("umum_name_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                if (isNameError && studentLoginErrorMessage == null) {
                    Text(
                        text = "Nama tidak boleh kosong!",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(start = 4.dp, top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = nisInput,
                    onValueChange = {
                        nisInput = it
                        isNisError = false
                        studentLoginErrorMessage = null
                    },
                    label = { Text("NIS Anda") },
                    placeholder = { Text("Masukkan 4 digit NIS (Contoh: 6745)") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    isError = isNisError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("umum_nis_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                if (isNisError && studentLoginErrorMessage == null) {
                    Text(
                        text = "NIS tidak boleh kosong!",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(start = 4.dp, top = 4.dp)
                    )
                }

                if (studentLoginErrorMessage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = studentLoginErrorMessage ?: "",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val inputName = nameInput.trim()
                        val inputNis = nisInput.trim()

                        if (inputName.isEmpty()) {
                            isNameError = true
                            studentLoginErrorMessage = "Nama lengkap wajib diisi!"
                        } else if (inputNis.isEmpty()) {
                            isNisError = true
                            studentLoginErrorMessage = "NIS wajib diisi!"
                        } else {
                            val expectedName = studentNisMap[inputNis]
                            if (expectedName == null) {
                                isNisError = true
                                studentLoginErrorMessage = "NIS $inputNis tidak terdaftar di kelas X TJKT 2!"
                            } else {
                                val normalizedInput = normalizeName(inputName)
                                val normalizedExpected = normalizeName(expectedName)
                                if (normalizedInput != normalizedExpected) {
                                    isNameError = true
                                    isNisError = true
                                    studentLoginErrorMessage = "Nama Lengkap atau NIS tidak cocok!"
                                } else {
                                    val sdf = java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.getDefault())
                                    val timestamp = sdf.format(java.util.Date())
                                    val historyString = sharedPrefs.getString("login_history", "") ?: ""
                                    val newEntry = "$expectedName (NIS $inputNis)|$timestamp"
                                    val updatedHistory = if (historyString.isEmpty()) {
                                        newEntry
                                    } else {
                                        "$historyString;$newEntry"
                                    }

                                    sharedPrefs.edit()
                                        .putString("login_user_name", expectedName)
                                        .putString("login_user_nis", inputNis)
                                        .putString("login_history", updatedHistory)
                                        .apply()

                                    onRoleSelected(UserRole.UMUM)
                                    Toast.makeText(context, "Selamat Datang, $expectedName!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("umum_login_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Masuk Sebagai Siswa", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        // Expanded password form block
        AnimatedVisibility(
            visible = isOwnerSelected,
            enter = expandVertically(animationSpec = spring()) + fadeIn(),
            exit = shrinkVertically(animationSpec = spring()) + fadeOut(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = {
                        passwordInput = it
                        isPasswordError = false
                    },
                    label = { Text("Kata Sandi Guru") },
                    placeholder = { Text("Masukkan sandi...") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.PlayArrow else Icons.Default.Lock,
                                contentDescription = if (passwordVisible) "Sembunyikan sandi" else "Tampilkan sandi"
                            )
                        }
                    },
                    isError = isPasswordError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("owner_password_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                if (isPasswordError) {
                    Text(
                        text = "Kata sandi salah! Coba lagi.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(start = 4.dp, top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Petunjuk: Masukkan kata sandi yang telah dikonfigurasi.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Start).padding(start = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (passwordInput == currentPassword) {
                            onRoleSelected(UserRole.OWNER)
                            Toast.makeText(context, "Selamat Datang, Guru!", Toast.LENGTH_SHORT).show()
                        } else {
                            isPasswordError = true
                            Toast.makeText(context, "Kata sandi salah!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("owner_login_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Masuk Sebagai Guru", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// Gorgeous Color Palette List representing Subject classifications
val PRESET_COLORS = listOf(
    "#4338CA", // Indigo Royal
    "#0EA5E9", // Sky Blue
    "#10B981", // Emerald Mint
    "#D97706", // Amber Honey
    "#EF4444", // Crimson Red
    "#8B5CF6", // Velvet Purple
    "#EC4899", // Coral Pink
    "#475569"  // Slate Gray
)

data class Homework(
    val id: String,
    val subject: String,
    val title: String,
    val description: String,
    val dueDate: String,
    val priority: String,
    val createdAt: String
)

data class StudentQuestion(
    val id: String,
    val studentName: String,
    val subject: String,
    val questionText: String,
    val timestamp: String,
    val status: String,
    val answerText: String
)

data class ClassJournal(
    val id: String,
    val date: String,
    val subject: String,
    val topic: String,
    val activity: String,
    val note: String
)

fun parseJournalList(raw: String): List<ClassJournal> {
    if (raw.isBlank()) return emptyList()
    return raw.split(";;").mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        val parts = line.split("||")
        if (parts.size >= 6) {
            ClassJournal(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5])
        } else null
    }
}

fun serializeJournalList(list: List<ClassJournal>): String {
    return list.joinToString(";;") { j ->
        "${j.id}||${j.date}||${j.subject}||${j.topic}||${j.activity}||${j.note}"
    }
}

data class StudentAttendance(
    val id: String,
    val studentName: String,
    val date: String,
    val time: String,
    val status: String,
    val note: String
)

fun parseAttendanceList(raw: String): List<StudentAttendance> {
    if (raw.isBlank()) return emptyList()
    return raw.split(";;").mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        val parts = line.split("||")
        if (parts.size >= 6) {
            StudentAttendance(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5])
        } else null
    }
}

fun serializeAttendanceList(list: List<StudentAttendance>): String {
    return list.joinToString(";;") { a ->
        "${a.id}||${a.studentName}||${a.date}||${a.time}||${a.status}||${a.note}"
    }
}

fun parseHomeworkList(raw: String): List<Homework> {
    if (raw.isBlank()) return emptyList()
    return raw.split(";;").mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        val parts = line.split("||")
        if (parts.size >= 7) {
            Homework(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5], parts[6])
        } else null
    }
}

fun serializeHomeworkList(list: List<Homework>): String {
    return list.joinToString(";;") { h ->
        "${h.id}||${h.subject}||${h.title}||${h.description}||${h.dueDate}||${h.priority}||${h.createdAt}"
    }
}

fun parseQuestionList(raw: String): List<StudentQuestion> {
    if (raw.isBlank()) return emptyList()
    return raw.split(";;").mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        val parts = line.split("||")
        if (parts.size >= 7) {
            StudentQuestion(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5], parts[6])
        } else null
    }
}

fun serializeQuestionList(list: List<StudentQuestion>): String {
    return list.joinToString(";;") { q ->
        "${q.id}||${q.studentName}||${q.subject}||${q.questionText}||${q.timestamp}||${q.status}||${q.answerText}"
    }
}

fun parseHexColor(hex: String, fallback: Color = Color(0xFF64748B)): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        fallback
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleAppContent(
    viewModel: ScheduleViewModel,
    isOwner: Boolean,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filteredSchedules by viewModel.filteredSchedules.collectAsStateWithLifecycle()

    val currentClass by viewModel.currentClass.collectAsStateWithLifecycle()
    val nextClass by viewModel.nextClass.collectAsStateWithLifecycle()
    val remainingMinsText by viewModel.remainingMinsText.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var showAdminSettingsDialog by remember { mutableStateOf(false) }
    var showNotificationSettingsDialog by remember { mutableStateOf(false) }
    var activeScheduleForDialog by remember { mutableStateOf<Schedule?>(null) }

    val sharedPrefs = remember { context.getSharedPreferences("jadwalku_prefs", Context.MODE_PRIVATE) }

    var announcementEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("announcement_enabled", true)) }
    var announcementText by remember { mutableStateOf(sharedPrefs.getString("announcement_text", "Selamat datang di JadwalKu! Hubungi Guru untuk memasang pengumuman kelas baru di sini secara langsung.") ?: "") }
    var announcementAuthor by remember { mutableStateOf(sharedPrefs.getString("announcement_author", "Wali Kelas") ?: "") }
    var announcementType by remember { mutableStateOf(sharedPrefs.getString("announcement_type", "Info") ?: "") }
    var announcementDate by remember { mutableStateOf(sharedPrefs.getString("announcement_date", "16 Juni 2026") ?: "") }

    val refreshAnnouncement = {
        announcementEnabled = sharedPrefs.getBoolean("announcement_enabled", true)
        announcementText = sharedPrefs.getString("announcement_text", "Selamat datang di JadwalKu! Hubungi Guru untuk memasang pengumuman kelas baru di sini secara langsung.") ?: ""
        announcementAuthor = sharedPrefs.getString("announcement_author", "Wali Kelas") ?: ""
        announcementType = sharedPrefs.getString("announcement_type", "Info") ?: ""
        announcementDate = sharedPrefs.getString("announcement_date", "16 Juni 2026") ?: ""
    }

    val daysList = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")

    var currentBottomTab by remember { mutableStateOf(0) }

    // Loaded Homework state
    var homeworkListString by remember { mutableStateOf(sharedPrefs.getString("classroom_homeworks", "") ?: "") }
    val homeworksList = remember(homeworkListString) { parseHomeworkList(homeworkListString) }

    // Completed homework state
    var completedHwIdsString by remember { mutableStateOf(sharedPrefs.getString("completed_homework_ids", "") ?: "") }
    val completedHwIds = remember(completedHwIdsString) { completedHwIdsString.split(",").filter { it.isNotBlank() }.toSet() }

    // Submitted questions state
    var questionsListString by remember { mutableStateOf(sharedPrefs.getString("classroom_questions", "") ?: "") }
    val questionsList = remember(questionsListString) { parseQuestionList(questionsListString) }

    // Loaded Attendance state
    var attendanceListString by remember { mutableStateOf(sharedPrefs.getString("classroom_attendance", "") ?: "") }
    val attendanceList = remember(attendanceListString) { parseAttendanceList(attendanceListString) }

    // Loaded Jurnal Mengajar state
    var journalListString by remember { mutableStateOf(sharedPrefs.getString("classroom_journals", "") ?: "") }
    val journalList = remember(journalListString) { parseJournalList(journalListString) }

    val loginName = remember { sharedPrefs.getString("login_user_name", "Siswa") ?: "Siswa" }

    var showAddHomeworkDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentBottomTab == 0,
                    onClick = { currentBottomTab = 0 },
                    icon = { Icon(imageVector = Icons.Default.DateRange, contentDescription = "Jadwal") },
                    label = { Text("Jadwal", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = currentBottomTab == 1,
                    onClick = { currentBottomTab = 1 },
                    icon = { Icon(imageVector = Icons.Default.List, contentDescription = "Tugas") },
                    label = { Text("Ruang Tugas", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = currentBottomTab == 2,
                    onClick = { currentBottomTab = 2 },
                    icon = { Icon(imageVector = Icons.Default.Person, contentDescription = "Konsultasi") },
                    label = { Text("Konsultasi", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            when (currentBottomTab) {
                0 -> {
                    val todayDate = remember { java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(java.util.Date()) }
                    val studentAttendanceToday = remember(attendanceList, loginName, todayDate) {
                        attendanceList.find { it.studentName.equals(loginName, ignoreCase = true) && it.date == todayDate }
                    }
                    var showCheckInDialog by remember { mutableStateOf(false) }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // App Headers Block (Fixed at the top)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "JadwalKu",
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.testTag("app_title")
                                        )
                                        // Dynamic access role marker badge tag
                                        Surface(
                                            color = if (isOwner) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = if (isOwner) "Guru" else "Murid",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isOwner) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    val sharedPrefs = remember { context.getSharedPreferences("jadwalku_prefs", Context.MODE_PRIVATE) }
                                    val currentStudentName = remember { sharedPrefs.getString("login_user_name", "") ?: "" }
                                    Text(
                                        text = if (!isOwner && currentStudentName.isNotEmpty()) "Siswa: $currentStudentName" else "SMK MUH 1 PURWOKERTO • X TJKT 2",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!isOwner && currentStudentName.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Notification settings button (Visible to everyone)
                                    IconButton(
                                        onClick = { showNotificationSettingsDialog = true },
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                                        ),
                                        modifier = Modifier.size(40.dp).testTag("notification_settings_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Notifications,
                                            contentDescription = "Pengaturan Notifikasi",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Quick FAB styled Add Action (Only if isOwner is true)
                                    if (isOwner) {
                                        IconButton(
                                            onClick = { showAdminSettingsDialog = true },
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                            ),
                                            modifier = Modifier.size(40.dp).testTag("admin_settings_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = "Fitur Admin & Riwayat",
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { showAddDialog = true },
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer
                                            ),
                                            modifier = Modifier.size(40.dp).testTag("add_schedule_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Tambah Jadwal Baru",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    // Logout/Switch Role Button
                                    IconButton(
                                        onClick = onLogout,
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        modifier = Modifier.size(40.dp).testTag("logout_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                            contentDescription = "Keluar / Ganti Peran",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Horizontal Days Tab Scroll Bar (Fixed at top)
                        Surface(
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(daysList) { dayName ->
                                    val isSelected = dayName.equals(selectedDay, ignoreCase = true)
                                    DaySelectionChip(
                                        dayName = dayName,
                                        isSelected = isSelected,
                                        onClick = { viewModel.selectDay(dayName) }
                                    )
                                }
                            }
                        }

                        // Real-Time Search Bar (Fixed at top)
                        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                placeholder = { Text("Telusuri pelajaran, guru, atau ruang...", fontSize = 14.sp) },
                                prefix = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Cari",
                                        modifier = Modifier.size(18.dp).padding(end = 4.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear Search",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("search_field"),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface
                                ),
                                maxLines = 1,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                            )
                        }

                        // Scrollable section (Mading banner, Student Check-In, AppLiveStatusTrackerCard, and schedules)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize().testTag("schedule_list")
                            ) {
                                // 1. Mading (Class Announcement Banner)
                                if (announcementEnabled && announcementText.isNotBlank()) {
                                    item {
                                        ClassAnnouncementBanner(
                                            enabled = announcementEnabled,
                                            text = announcementText,
                                            author = announcementAuthor,
                                            type = announcementType,
                                            date = announcementDate,
                                            isOwner = isOwner,
                                            onEditClick = { showAdminSettingsDialog = true }
                                        )
                                    }
                                }

                                // 2. Student Check-In (Absensi Mandiri)
                                if (!isOwner && loginName.isNotEmpty() && loginName != "Siswa") {
                                    item {
                                        if (studentAttendanceToday != null) {
                                            // Already Checked-In UI
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 4.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Sudah Absen",
                                                        tint = Color(0xFF10B981),
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Column {
                                                        Text(
                                                            text = "Presensi Masuk Berhasil!",
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                        Text(
                                                            text = "Status: ${studentAttendanceToday.status} • Jam ${studentAttendanceToday.time}",
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        if (studentAttendanceToday.note.isNotEmpty() && studentAttendanceToday.note != "-") {
                                                            Text(
                                                                text = "Catatan: ${studentAttendanceToday.note}",
                                                                fontSize = 10.sp,
                                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            // Pending Check-In UI
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 4.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                                                ),
                                                shape = RoundedCornerShape(16.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f))
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(16.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Info,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.secondary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Text(
                                                            text = "Absensi Mandiri Kelas Hari Ini",
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.secondary
                                                        )
                                                    }
                                                    Text(
                                                        text = "Halo $loginName, Anda belum melakukan presensi masuk hari ini ($todayDate). Yuk klik tombol di bawah ini!",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Button(
                                                        onClick = { showCheckInDialog = true },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                                        shape = RoundedCornerShape(10.dp),
                                                        modifier = Modifier.fillMaxWidth().height(38.dp),
                                                        contentPadding = PaddingValues(0.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .background(
                                                                    brush = Brush.horizontalGradient(
                                                                        colors = listOf(
                                                                            MaterialTheme.colorScheme.secondary,
                                                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                                                        )
                                                                    ),
                                                                    shape = RoundedCornerShape(10.dp)
                                                                ),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Edit,
                                                                    contentDescription = null,
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Text("Lakukan Absen Masuk", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // 3. Live Timing Dashboard Card
                                item {
                                    AppLiveStatusTrackerCard(
                                        currentClass = currentClass,
                                        nextClass = nextClass,
                                        remainingTimeText = remainingMinsText
                                    )
                                }

                                // 4. Section Title
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (searchQuery.isNotEmpty()) "Hasil Pencarian Jadwal" else "Jadwal Pelajaran $selectedDay",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (filteredSchedules.isNotEmpty()) {
                                            Text(
                                                text = "${filteredSchedules.size} Sesi",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                // 5. List of schedules or Empty State
                                if (filteredSchedules.isEmpty()) {
                                    item {
                                        EmptyStateIllustration(
                                            isOwner = isOwner,
                                            hasSearchQuery = searchQuery.isNotEmpty(),
                                            selectedDay = selectedDay,
                                            onResetSearch = { viewModel.updateSearchQuery("") },
                                            onAddSubjectClick = { showAddDialog = true }
                                        )
                                    }
                                } else {
                                    items(filteredSchedules, key = { it.id }) { schedule ->
                                        SubjectRowCard(
                                            schedule = schedule,
                                            isOwner = isOwner,
                                            onToggleAlarm = { viewModel.toggleReminder(schedule, context) },
                                            onCardClick = { activeScheduleForDialog = schedule }
                                        )
                                    }
                                }
                            }
                        }
                    }
}
                1 -> {
                    HomeworkTabContent(
                        isOwner = isOwner,
                        homeworks = homeworksList,
                        completedHwIds = completedHwIds,
                        onToggleComplete = { id ->
                            val current = completedHwIds.toMutableSet()
                            if (current.contains(id)) current.remove(id) else current.add(id)
                            val updated = current.joinToString(",")
                            sharedPrefs.edit().putString("completed_homework_ids", updated).apply()
                            completedHwIdsString = updated
                        },
                        onAddHomeworkClick = { showAddHomeworkDialog = true },
                        onDeleteHomework = { id ->
                            val remaining = homeworksList.filter { it.id != id }
                            val updated = serializeHomeworkList(remaining)
                            sharedPrefs.edit().putString("classroom_homeworks", updated).apply()
                            homeworkListString = updated
                            Toast.makeText(context, "Tugas berhasil dihapus!", Toast.LENGTH_SHORT).show()
                        },
                        allSchedules = filteredSchedules,
                        journals = journalList,
                        onAddJournal = { subject, topic, activity, note ->
                            val currentTodayDate = java.text.SimpleDateFormat("dd MMMM yyyy, HH:mm", java.util.Locale("id", "ID")).format(java.util.Date())
                            val newJ = ClassJournal(
                                id = "j_" + System.currentTimeMillis(),
                                date = currentTodayDate,
                                subject = subject,
                                topic = topic,
                                activity = activity,
                                note = note
                            )
                            val newList = journalList + newJ
                            val updated = serializeJournalList(newList)
                            sharedPrefs.edit().putString("classroom_journals", updated).apply()
                            journalListString = updated
                            Toast.makeText(context, "Jurnal belajar berhasil dicatat!", Toast.LENGTH_SHORT).show()
                        },
                        onDeleteJournal = { id ->
                            val newList = journalList.filter { it.id != id }
                            val updated = serializeJournalList(newList)
                            sharedPrefs.edit().putString("classroom_journals", updated).apply()
                            journalListString = updated
                            Toast.makeText(context, "Jurnal belajar berhasil dihapus!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                2 -> {
                    val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
                    val currentDate = sdf.format(java.util.Date())

                    ConsultationTabContent(
                        isOwner = isOwner,
                        questions = questionsList,
                        onAddQuestion = { name, mapel, question ->
                            val newQ = StudentQuestion(
                                id = "q_" + System.currentTimeMillis(),
                                studentName = name,
                                subject = mapel,
                                questionText = question,
                                timestamp = currentDate,
                                status = "Menunggu Jawaban",
                                answerText = ""
                            )
                            val newList = questionsList + newQ
                            val updated = serializeQuestionList(newList)
                            sharedPrefs.edit().putString("classroom_questions", updated).apply()
                            questionsListString = updated
                            Toast.makeText(context, "Pertanyaan berhasil dikirim ke Guru!", Toast.LENGTH_SHORT).show()
                        },
                        onAnswerQuestion = { id, answer ->
                            val newList = questionsList.map { q ->
                                if (q.id == id) q.copy(status = "DIJAWAB GURU", answerText = answer) else q
                            }
                            val updated = serializeQuestionList(newList)
                            sharedPrefs.edit().putString("classroom_questions", updated).apply()
                            questionsListString = updated
                            Toast.makeText(context, "Tanggapan Guru berhasil dikirim!", Toast.LENGTH_SHORT).show()
                        },
                        studentName = loginName
                    )
                }
            }
        }
    }

    if (showAddHomeworkDialog) {
        AddHomeworkDialog(
            onDismiss = { showAddHomeworkDialog = false },
            onSave = { subject, title, desc, due, priority ->
                val newHw = Homework(
                    id = "hw_" + System.currentTimeMillis(),
                    subject = subject,
                    title = title,
                    description = desc,
                    dueDate = due,
                    priority = priority,
                    createdAt = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                )
                val newList = homeworksList + newHw
                val updated = serializeHomeworkList(newList)
                sharedPrefs.edit().putString("classroom_homeworks", updated).apply()
                homeworkListString = updated
                showAddHomeworkDialog = false
                Toast.makeText(context, "Tugas Baru Berhasil Diumumkan!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Modal Add Subject Form Dialog
    if (showAddDialog) {
        AddEditScheduleDialog(
            scheduleToEdit = null,
            chosenDay = selectedDay,
            onDismiss = { showAddDialog = false },
            onSave = { newSchedule ->
                viewModel.addSchedule(newSchedule, context)
                showAddDialog = false
                Toast.makeText(context, "Jadwal pelajaran berhasil ditambahkan!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Modal Admin settings dialog
    if (showAdminSettingsDialog) {
        AdminSettingsDialog(
            onDismiss = {
                showAdminSettingsDialog = false
                refreshAnnouncement()
            },
            viewModel = viewModel,
            attendanceList = attendanceList,
            onAttendanceChanged = { updatedListStr ->
                attendanceListString = updatedListStr
            }
        )
    }

    // Modal Notification & Sound settings dialog
    if (showNotificationSettingsDialog) {
        NotificationSettingsDialog(
            onDismiss = { showNotificationSettingsDialog = false }
        )
    }

    // Modal Detail + Edit/Delete Dialog
    activeScheduleForDialog?.let { schedule ->
        var isEditingMode by remember { mutableStateOf(false) }

        if (!isEditingMode) {
            // View Details mode
            SubjectDetailDialog(
                schedule = schedule,
                isOwner = isOwner,
                onDismiss = { activeScheduleForDialog = null },
                onEditTrigger = { isEditingMode = true },
                onDeleteTrigger = {
                    viewModel.deleteSchedule(schedule, context)
                    activeScheduleForDialog = null
                    Toast.makeText(context, "Jadwal ${schedule.subjectName} berhasil dihapus.", Toast.LENGTH_SHORT).show()
                },
                onToggleAlarm = {
                    viewModel.toggleReminder(schedule, context)
                }
            )
        } else {
            // Edit Form mode
            AddEditScheduleDialog(
                scheduleToEdit = schedule,
                chosenDay = schedule.day,
                onDismiss = { isEditingMode = false },
                onSave = { updatedSchedule ->
                    viewModel.updateSchedule(updatedSchedule, context)
                    activeScheduleForDialog = null
                    isEditingMode = false
                    Toast.makeText(context, "Jadwal pelajaran berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

// Sub-component: Realtime live Status Tracker Dashboard Card
@Composable
fun AppLiveStatusTrackerCard(
    currentClass: Schedule?,
    nextClass: Schedule?,
    remainingTimeText: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("status_tracker_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                )
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = if (androidx.compose.foundation.isSystemInDarkTheme()) {
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                            )
                        } else {
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                            )
                        }
                    )
                )
                .padding(18.dp)
        ) {
            // Title Header with Pulsating Animation Dot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                LiveActivityPulseDot()

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "STATUS KBM HARI INI",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (currentClass == null) {
                // Free/No active class state
                Text(
                    text = "Sedang Bebas KBM 🏝️",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Tidak ada jadwal pelajaran yang aktif saat ini.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            } else {
                // Active Class state details
                Text(
                    text = currentClass.subjectName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LabelIconTracker(
                        icon = Icons.Default.Person,
                        text = "Sy. " + currentClass.teacherName,
                        tintColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )

                    LabelIconTracker(
                        icon = Icons.Default.LocationOn,
                        text = currentClass.roomName,
                        tintColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Modern highlight row for elapsed time countdown
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sisa Waktu",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Jam ke-${currentClass.periodNumber} • $remainingTimeText (${currentClass.startTime} - ${currentClass.endTime})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Divider and Next Class preview section
            if (nextClass != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Berikutnya",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "Berikutnya: ${nextClass.subjectName}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Text(
                        text = "Pukul ${nextClass.startTime}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// Indicator: Beautiful pulsating circular live dot anim
@Composable
fun LiveActivityPulseDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .background(
                color = Color(0xFF10B981).copy(alpha = alpha),
                shape = CircleShape
            )
            .border(1.dp, Color(0xFF10B981), CircleShape)
    )
}

// Sub-component: Day selection slider pill
@Composable
fun DaySelectionChip(
    dayName: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundBrush = if (isSelected) {
        Brush.horizontalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        )
    }
    val labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val borderStroke = if (isSelected) 0.dp else 1.dp

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundBrush)
            .clickable { onClick() }
            .then(
                if (borderStroke > 0.dp) Modifier.border(
                    borderStroke,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    RoundedCornerShape(14.dp)
                ) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = dayName,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = labelColor
        )
    }
}

// Helper methods for SubjectRowCard styling
fun getSubjectInitials(name: String): String {
    val clean = name.trim().replace("-", " ")
    val words = clean.split("\\s+".toRegex()).filter { it.isNotEmpty() }
    if (words.size >= 2) {
        val first = words[0].firstOrNull() ?: ' '
        val second = words[1].firstOrNull() ?: ' '
        if (first.isLetterOrDigit() && second.isLetterOrDigit()) {
            return "$first$second".uppercase()
        }
    }
    return if (clean.length >= 2) clean.take(2).uppercase() else clean.uppercase()
}

fun getSubjectIcon(name: String): ImageVector {
    val lower = name.lowercase()
    return when {
        lower.contains("matematika") -> Icons.Default.Add
        lower.contains("ipas") || lower.contains("ipa") || lower.contains("fisika") || lower.contains("kimia") || lower.contains("biologi") || lower.contains("sains") -> Icons.Default.Star
        lower.contains("tjkt") || lower.contains("jaringan") || lower.contains("komputer") || lower.contains("informatika") || lower.contains("coding") || lower.contains("pemrograman") || lower.contains("rekayasa") -> Icons.Default.Build
        lower.contains("agama") || lower.contains("islam") || lower.contains("duha") || lower.contains("tadarus") || lower.contains("kemuh") -> Icons.Default.Favorite
        lower.contains("olahraga") || lower.contains("pjok") || lower.contains("jasmani") || lower.contains("senam") -> Icons.Default.Face
        lower.contains("bahasa") || lower.contains("indonesia") || lower.contains("inggris") || lower.contains("jawa") || lower.contains("arab") || lower.contains("literasi") -> Icons.Default.List
        lower.contains("istirahat") || lower.contains("makan") || lower.contains("kantin") || lower.contains("break") -> Icons.Default.ShoppingCart
        lower.contains("sejarah") || lower.contains("pancasila") || lower.contains("pkn") || lower.contains("ips") -> Icons.Default.LocationOn
        lower.contains("seni") || lower.contains("budaya") || lower.contains("prakarya") || lower.contains("musik") || lower.contains("menggambar") -> Icons.Default.PlayArrow
        else -> Icons.Default.DateRange
    }
}

// Sub-component: Visual schedule block item Card row (Aesthetic and Intuitive version)
@Composable
fun SubjectRowCard(
    schedule: Schedule,
    isOwner: Boolean,
    onToggleAlarm: () -> Unit,
    onCardClick: () -> Unit
) {
    val barColor = parseHexColor(schedule.colorHex)

    // Dynamic time calculation
    val calendar = java.util.Calendar.getInstance()
    val currentDayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
    val todayName = when (currentDayOfWeek) {
        java.util.Calendar.MONDAY -> "Senin"
        java.util.Calendar.TUESDAY -> "Selasa"
        java.util.Calendar.WEDNESDAY -> "Rabu"
        java.util.Calendar.THURSDAY -> "Kamis"
        java.util.Calendar.FRIDAY -> "Jumat"
        java.util.Calendar.SATURDAY -> "Sabtu"
        java.util.Calendar.SUNDAY -> "Minggu"
        else -> "Senin"
    }

    val currentMins = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)

    fun parseTimeToMinutes(timeStr: String): Int {
        val parts = timeStr.split(":")
        if (parts.size != 2) return 0
        val h = parts[0].toIntOrNull() ?: return 0
        val m = parts[1].toIntOrNull() ?: return 0
        return h * 60 + m
    }

    val startMins = parseTimeToMinutes(schedule.startTime)
    val endMins = parseTimeToMinutes(schedule.endTime)
    val duration = endMins - startMins

    val isToday = schedule.day.equals(todayName, ignoreCase = true)

    val status = when {
        !isToday -> "Akan Datang" // Treats as ordinary / upcoming
        currentMins < startMins -> "Akan Datang"
        currentMins in startMins..endMins -> "Berlangsung"
        else -> "Selesai"
    }

    // Smooth scrolling & entry animation states
    val scale = remember { Animatable(0.94f) }
    val alphaAnim = remember { Animatable(0f) }
    val translateY = remember { Animatable(25f) }

    LaunchedEffect(key1 = schedule.id) {
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        launch {
            alphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
            )
        }
        launch {
            translateY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    // Define container visuals based on schedule status
    val containerColor = when (status) {
        "Berlangsung" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        "Selesai" -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.surface
    }

    val borderStroke = when (status) {
        "Berlangsung" -> BorderStroke(2.dp, barColor)
        "Selesai" -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
    }

    val contentAlpha = if (status == "Selesai") 0.6f else 1.0f

    Card(
        modifier = Modifier
            .graphicsLayer(
                scaleX = scale.value,
                scaleY = scale.value,
                alpha = alphaAnim.value * contentAlpha,
                translationY = translateY.value
            )
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("schedule_item_${schedule.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        border = borderStroke,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (status == "Berlangsung") 4.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Side: Aesthetic Subject ID circular badge using Signature Color
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(
                        color = if (status == "Berlangsung") barColor else barColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (status == "Selesai") {
                    // Done badge icon
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selesai",
                        tint = barColor,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = getSubjectIcon(schedule.subjectName),
                            contentDescription = null,
                            tint = if (status == "Berlangsung") Color.White else barColor,
                            modifier = Modifier.size(20.dp)
                        )

                        Text(
                            text = getSubjectInitials(schedule.subjectName),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (status == "Berlangsung") Color.White.copy(alpha = 0.9f) else barColor.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Middle Block: Main metadata information
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Header Row metadata
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Subject Class slot interval
                    Surface(
                        color = if (status == "Berlangsung") barColor else barColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (schedule.periodNumber > 0) "Mata Pelajaran J-${schedule.periodNumber}" else "Kegiatan Khusus",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (status == "Berlangsung") Color.White else barColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Intuitive status badge
                    val badgeColor = when (status) {
                        "Berlangsung" -> Color(0xFF10B981) // Emerald Green
                        "Selesai" -> Color(0xFF64748B)      // Cool Slate Gray
                        else -> MaterialTheme.colorScheme.primary
                    }
                    val badgeBg = when (status) {
                        "Berlangsung" -> Color(0xFF10B981).copy(alpha = 0.15f)
                        "Selesai" -> Color(0xFF64748B).copy(alpha = 0.12f)
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    }
                    val badgeLabel = when (status) {
                        "Berlangsung" -> "● Live"
                        "Selesai" -> "Selesai"
                        else -> "Nanti"
                    }

                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Subject name with modern display
                Text(
                    text = schedule.subjectName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Class Time interval & Instructor, room labels
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LabelIconTracker(
                        icon = Icons.Default.DateRange,
                        text = "${schedule.startTime} - ${schedule.endTime}",
                        tintColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (schedule.roomName.isNotEmpty()) {
                        LabelIconTracker(
                            icon = Icons.Default.LocationOn,
                            text = schedule.roomName,
                            tintColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (schedule.teacherName != "Semua" && schedule.teacherName != "Sekolah" && schedule.teacherName.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    LabelIconTracker(
                        icon = Icons.Default.Person,
                        text = "Guru: " + schedule.teacherName,
                        tintColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // If class is ongoing: show dynamic timing progress metrics
                if (status == "Berlangsung" && duration > 0) {
                    val elapsedTime = (currentMins - startMins).coerceIn(0, duration)
                    val progressValue = (elapsedTime.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Slider progress bar
                        LinearProgressIndicator(
                            progress = { progressValue },
                            color = barColor,
                            trackColor = barColor.copy(alpha = 0.15f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Berjalan ${elapsedTime}m dari ${duration}m",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = barColor
                            )
                            Text(
                                text = "${(progressValue * 100).toInt()}%",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = barColor
                            )
                        }
                    }
                }
            }

            // Right column buttons/icons
            Box(
                modifier = Modifier.padding(start = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isOwner) {
                    IconButton(
                        onClick = { onToggleAlarm() },
                        modifier = Modifier.testTag("alarm_toggle_${schedule.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Ubah Notifikasi Pengingat",
                            tint = if (schedule.isReminderEnabled) barColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Rincian Pelajaran",
                        tint = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// Sub-component Helper: Standard horizontal labels with visual system icons
@Composable
fun LabelIconTracker(
    icon: ImageVector,
    text: String,
    tintColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = tintColor.copy(alpha = 0.7f)
        )
        Text(
            text = text,
            fontSize = 12.sp,
            color = tintColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Empty State View illustration block
@Composable
fun EmptyStateIllustration(
    isOwner: Boolean,
    hasSearchQuery: Boolean,
    selectedDay: String,
    onResetSearch: () -> Unit,
    onAddSubjectClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (hasSearchQuery) Icons.Default.Search else Icons.Default.DateRange,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (hasSearchQuery) "Hasil tidak ditemukan" else "Hari ini kosong! 🎉",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (hasSearchQuery) {
                "Coba gunakan kata kunci pencarian yang berbeda untuk menemukan jadwal sekolah."
            } else {
                "Belum ada mata pelajaran atau agenda kegiatan sekolah terjadwal untuk hari $selectedDay."
            },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (hasSearchQuery) {
            Button(
                onClick = onResetSearch,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Reset Pencarian")
            }
        } else if (isOwner) {
            Button(
                onClick = onAddSubjectClick,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("empty_state_add_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tambah Jadwal $selectedDay")
            }
        } else {
            // General info when empty for public
            Text(
                text = "Silakan hubungi Guru X TJKT 2 jika Anda ingin menambahkan pelajaran baru di hari ini.",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

// Modal View Component: Subject Detail Dialog
@Composable
fun SubjectDetailDialog(
    schedule: Schedule,
    isOwner: Boolean,
    onDismiss: () -> Unit,
    onEditTrigger: () -> Unit,
    onDeleteTrigger: () -> Unit,
    onToggleAlarm: () -> Unit
) {
    val barColor = parseHexColor(schedule.colorHex)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (isOwner) {
                Button(
                    onClick = onEditTrigger,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit")
                }
            } else {
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Tutup")
                }
            }
        },
        dismissButton = {
            if (isOwner) {
                TextButton(
                    onClick = onDeleteTrigger,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hapus")
                }
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(barColor, CircleShape)
                )
                Text(
                    text = "Detail Jadwal",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Day + Hour ID Label
                DetailRowItem(label = "Hari", value = schedule.day)
                DetailRowItem(label = "Jam Ke", value = if (schedule.periodNumber > 0) "Jam ke-${schedule.periodNumber}" else "Slot Kegiatan")
                DetailRowItem(label = "Waktu", value = "${schedule.startTime} - ${schedule.endTime}")

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Subject Name Text
                Column {
                    Text("MATA PELAJARAN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(schedule.subjectName, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                }

                // Teacher
                Column {
                    Text("GURU PENGAMPU", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (schedule.teacherName.isEmpty() || schedule.teacherName == "Semua") "-" else "Sy. " + schedule.teacherName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }

                // Room Classroom Name
                Column {
                    Text("RUANGAN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (schedule.roomName.isEmpty()) "Kelas X TJKT 2" else schedule.roomName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }

                if (isOwner) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Alarm toggler setting
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .clickable { onToggleAlarm() }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val statusText = if (schedule.isReminderEnabled) "Aktif (Alarm Berbunyi)" else "Nonaktif"
                        Column {
                            Text("NOTIFIKASI PENGINGAT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(statusText, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (schedule.isReminderEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                        Switch(
                            checked = schedule.isReminderEnabled,
                            onCheckedChange = { onToggleAlarm() }
                        )
                    }
                }
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun AdminSettingsDialog(
    onDismiss: () -> Unit,
    viewModel: ScheduleViewModel,
    attendanceList: List<StudentAttendance>,
    onAttendanceChanged: (String) -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("jadwalku_prefs", Context.MODE_PRIVATE) }

    // Fetch all schedules for metrics
    val allSchedulesState = viewModel.allSchedules.collectAsStateWithLifecycle(initialValue = emptyList<Schedule>())
    val allSchedules = allSchedulesState.value

    var activeDashboardTab by remember { mutableStateOf(0) } // 0: Mading, 1: Presensi, 2: Roster Siswa, 3: Utilitas & Keamanan

    // Student Registry
    val studentRegistry = remember {
        listOf(
            "6745" to "AHYA DIA ALFUADI",
            "6746" to "AMANDA TSALAS CENDIKIA",
            "6747" to "AMARUL IKHSAN RIZQI NASUTION",
            "6748" to "AYU RIZMA FIRAWATI",
            "6749" to "CHANYA NUR AZIZAH",
            "6750" to "DEWI SHOLIHAH",
            "6751" to "FAHRIDZAL ALKHAFI",
            "6752" to "FAQIH LARENDA SENTANU",
            "6753" to "FIRMAN RIDHO ERSAPUTRA",
            "6754" to "HABIB MAULANA",
            "6755" to "IQMAL MAULANA",
            "6756" to "JULIA ROSSA",
            "6757" to "MUHAMMAD FATHIR VIRIGIAWAN",
            "6758" to "MUHAMMAD MIFTAKHUSSIFA",
            "6759" to "MUKTI SETIAWAN",
            "6760" to "NAZERI FANO YULIANSYAH",
            "6761" to "NUR LAILY 'AISYAH",
            "6762" to "RIFKI DWI PRASETYO",
            "6763" to "SAFRINA",
            "6764" to "SELVINA",
            "6765" to "SYAFANA AYUDYA HUMAYRA",
            "6766" to "XENA ADIKA DWITAMA",
            "6767" to "YULI PUTRA PRASETIO"
        )
    }

    // Tab 0: Mading state
    var activeAnnState by remember { mutableStateOf(sharedPrefs.getBoolean("announcement_enabled", true)) }
    var annTextState by remember { mutableStateOf(sharedPrefs.getString("announcement_text", "Selamat datang di JadwalKu! Hubungi Guru untuk memasang pengumuman kelas baru di sini secara langsung.") ?: "") }
    var annAuthorState by remember { mutableStateOf(sharedPrefs.getString("announcement_author", "Wali Kelas") ?: "") }
    var annTypeState by remember { mutableStateOf(sharedPrefs.getString("announcement_type", "Info") ?: "") }

    // Tab 1: Presensi live filters & search state
    var attSearchQuery by remember { mutableStateOf("") }
    var attStatusFilter by remember { mutableStateOf("Semua") }

    // Tab 2: Roster search state
    var rosterSearchQuery by remember { mutableStateOf("") }

    // Tab 3: Security Sandi state
    var newPassword by remember { mutableStateOf("") }
    var rawHistory by remember { mutableStateOf(sharedPrefs.getString("login_history", "") ?: "") }
    val historyList = remember(rawHistory) {
        if (rawHistory.isBlank()) emptyList()
        else rawHistory.split(";").mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size >= 2) parts[0] to parts[1] else null
        }.reversed()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    // Save announcement states on close
                    sharedPrefs.edit()
                        .putBoolean("announcement_enabled", activeAnnState)
                        .putString("announcement_text", annTextState.trim())
                        .putString("announcement_author", annAuthorState.trim())
                        .putString("announcement_type", annTypeState)
                        .putString("announcement_date", java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale("id", "ID")).format(java.util.Date()))
                        .apply()
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Simpan & Tutup")
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "Dashboard Guru",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "SMK MUH 1 PURWOKERTO • X TJKT 2",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // TAB LIST: Scrollable capsules row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf(
                        "📢 Mading" to 0,
                        "👤 Presensi" to 1,
                        "📋 Roster Siswa" to 2,
                        "🛠️ Utilitas" to 3
                    )
                    tabs.forEach { (label, index) ->
                        val isSelected = activeDashboardTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { activeDashboardTab = index }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // TAB CONTENTS
                Box(modifier = Modifier.weight(1f)) {
                    when (activeDashboardTab) {
                        0 -> {
                            // ==================== TAB 0: MADING ====================
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Siar Pengumuman (Mading)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text("Aktifkan agar terlihat di beranda siswa", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(
                                            checked = activeAnnState,
                                            onCheckedChange = { activeAnnState = it }
                                        )
                                    }
                                }

                                if (activeAnnState) {
                                    OutlinedTextField(
                                        value = annTextState,
                                        onValueChange = { annTextState = it },
                                        label = { Text("Isi Pengumuman Kelas", fontSize = 12.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 4
                                    )

                                    OutlinedTextField(
                                        value = annAuthorState,
                                        onValueChange = { annAuthorState = it },
                                        label = { Text("Nama Pengirim / Guru", fontSize = 12.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Kategori Kepentingan:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf("Info", "Penting", "Sakit/Berita").forEach { type ->
                                                val isSel = annTypeState == type
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (isSel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                                                        .border(1.dp, if (isSel) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                                        .clickable { annTypeState = type }
                                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                                ) {
                                                    Text(type, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            // ==================== TAB 1: PRESENSI LOG & INPUT MANUAL ====================
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Add Presensi Manual Segment
                                var showAddManualPresensiForm by remember { mutableStateOf(false) }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text("✍️", fontSize = 14.sp)
                                                Text("Catat Presensi Kelas Manual", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }
                                            IconButton(
                                                modifier = Modifier.size(24.dp),
                                                onClick = { showAddManualPresensiForm = !showAddManualPresensiForm }
                                            ) {
                                                Icon(
                                                    imageVector = if (showAddManualPresensiForm) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "Toggle Input"
                                                )
                                            }
                                        }

                                        if (showAddManualPresensiForm) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            // Simple list selection for student name
                                            var selectedStudentNameInForm by remember { mutableStateOf(studentRegistry.first().second) }
                                            var statusSelectedInForm by remember { mutableStateOf("Hadir") }
                                            var noteSelectedInForm by remember { mutableStateOf("") }

                                            // Dropdown simulation with easy horizontal lists or sliders
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text("Pilih Siswa:", fontSize = 11.sp, fontWeight = FontWeight.Bold)

                                                // Horizontal List of students
                                                LazyRow(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    items(studentRegistry) { item ->
                                                        val isSelected = selectedStudentNameInForm == item.second
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                                                .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                                                .clickable { selectedStudentNameInForm = item.second }
                                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        ) {
                                                            Text(item.second, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface)
                                                        }
                                                    }
                                                }

                                                Text("Status Kehadiran:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    listOf("Hadir", "Sakit", "Izin", "Alfa").forEach { st ->
                                                        val isSelected = statusSelectedInForm == st
                                                        val colored = when (st) {
                                                            "Hadir" -> Color(0xFF10B981)
                                                            "Sakit" -> Color(0xFFF59E0B)
                                                            "Izin" -> Color(0xFF3B82F6)
                                                            else -> Color(0xFFEF4444)
                                                        }
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(if (isSelected) colored.copy(alpha = 0.15f) else Color.Transparent)
                                                                .border(1.dp, if (isSelected) colored else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                                                .clickable { statusSelectedInForm = st }
                                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                                        ) {
                                                            Text(st, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colored)
                                                        }
                                                    }
                                                }

                                                OutlinedTextField(
                                                    value = noteSelectedInForm,
                                                    onValueChange = { noteSelectedInForm = it },
                                                    placeholder = { Text("Keterangan (e.g. Surat dokter / Pulang cepat)", fontSize = 11.sp) },
                                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                                        focusedContainerColor = MaterialTheme.colorScheme.surface
                                                    ),
                                                    singleLine = true
                                                )

                                                Button(
                                                    onClick = {
                                                        val rawFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale("id", "ID"))
                                                        val rawTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                                        val todayStr = rawFormat.format(java.util.Date())
                                                        val timeStr = rawTime.format(java.util.Date())

                                                        val newEntry = StudentAttendance(
                                                            id = "a_" + System.currentTimeMillis(),
                                                            studentName = selectedStudentNameInForm,
                                                            date = todayStr,
                                                            time = timeStr,
                                                            status = statusSelectedInForm,
                                                            note = if (noteSelectedInForm.isBlank()) "Ditulis Wali Kelas" else noteSelectedInForm.trim()
                                                        )

                                                        val updatedList = attendanceList + newEntry
                                                        val serialized = serializeAttendanceList(updatedList)
                                                        sharedPrefs.edit().putString("classroom_attendance", serialized).apply()
                                                        onAttendanceChanged(serialized)
                                                        noteSelectedInForm = ""
                                                        showAddManualPresensiForm = false
                                                        Toast.makeText(context, "Berhasil mencatat presensi!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.fillMaxWidth().height(36.dp),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text("Tambahkan Presensi", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }

                                // Quick filter components
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Filter:", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    listOf("Semua", "Hadir", "Sakit", "Izin", "Alfa").forEach { filter ->
                                        val isSel = attStatusFilter == filter
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                                                .clickable { attStatusFilter = filter }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(filter, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isSel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                                // Live logs
                                val filteredAttList = remember(attendanceList, attSearchQuery, attStatusFilter) {
                                    attendanceList.filter { att ->
                                        (attStatusFilter == "Semua" || att.status.equals(attStatusFilter, ignoreCase = true)) &&
                                        (attSearchQuery.isBlank() || att.studentName.contains(attSearchQuery, ignoreCase = true) || att.status.contains(attSearchQuery, ignoreCase = true))
                                    }.reversed()
                                }

                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(filteredAttList, key = { it.id }) { item ->
                                        val statColor = when (item.status) {
                                            "Hadir" -> Color(0xFF10B981)
                                            "Sakit" -> Color(0xFFF59E0B)
                                            "Izin" -> Color(0xFF3B82F6)
                                            else -> Color(0xFFEF4444)
                                        }
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Box(modifier = Modifier.size(6.dp).background(statColor, CircleShape))
                                                        Text(item.studentName, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    Text("${item.date} pukul ${item.time} • ${item.note}", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Surface(
                                                        color = statColor.copy(alpha = 0.15f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(item.status.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = statColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            val remaining = attendanceList.filter { it.id != item.id }
                                                            val serialized = serializeAttendanceList(remaining)
                                                            sharedPrefs.edit().putString("classroom_attendance", serialized).apply()
                                                            onAttendanceChanged(serialized)
                                                            Toast.makeText(context, "Log absensi dihapus", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.size(20.dp)
                                                    ) {
                                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Hapus Log", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(12.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        2 -> {
                            // ==================== TAB 2: ROSTER SISWA & LIVE INDIVIDUAL METRICS ====================
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = rosterSearchQuery,
                                    onValueChange = { rosterSearchQuery = it },
                                    placeholder = { Text("Cari nama siswa atau NIS...", fontSize = 11.sp) },
                                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                        focusedContainerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    singleLine = true
                                )

                                val filteredRegistry = remember(rosterSearchQuery, studentRegistry) {
                                    if (rosterSearchQuery.isBlank()) studentRegistry
                                    else studentRegistry.filter {
                                        it.first.contains(rosterSearchQuery, ignoreCase = true) ||
                                        it.second.contains(rosterSearchQuery, ignoreCase = true)
                                    }
                                }

                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(filteredRegistry) { item ->
                                        // Count statistics for this specific pupil from live check-in lists!
                                        val pupilName = item.second
                                        val pupilAtts = attendanceList.filter { it.studentName.equals(pupilName, ignoreCase = true) }
                                        val cValHadir = pupilAtts.count { it.status == "Hadir" }
                                        val cValSakit = pupilAtts.count { it.status == "Sakit" }
                                        val cValIzin = pupilAtts.count { it.status == "Izin" }
                                        val cValAlfa = pupilAtts.count { it.status == "Alfa" }

                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(pupilName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Surface(
                                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text("NIS: ${item.first}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                        }

                                                        // Active stats row
                                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            Text("H:${cValHadir}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                                            Text("S:${cValSakit}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                                                            Text("I:${cValIzin}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3B82F6))
                                                            Text("A:${cValAlfa}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                                                        }
                                                    }
                                                }

                                                IconButton(
                                                    onClick = {
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                        val clip = android.content.ClipData.newPlainText("Siswa", "NIS: ${item.first} - ${item.second}")
                                                        clipboard.setPrimaryClip(clip)
                                                        Toast.makeText(context, "Klasifikasi copied!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Default.Share, contentDescription = "Copy Record", modifier = Modifier.size(12.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        3 -> {
                            // ==================== TAB 3: UTILITAS, INSIGHTS & KEAMANAN ====================
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Dynamic Metrics Card
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("💡 Statistik Kelas X TJKT 2", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                                        val uniqueInst = allSchedules.map { it.teacherName }.distinct().size
                                        val uniqueRooms = allSchedules.map { it.roomName }.distinct().size
                                        val totalSess = allSchedules.size

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column {
                                                Text("Sesi Mingguan", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("$totalSess Jadwal", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Column {
                                                Text("Guru Pengajar", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("$uniqueInst Orang", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Column {
                                                Text("Ruangan & Lab", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("$uniqueRooms Lokasi", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // Security Change Password Form
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                            Text("Ubah Sandi Keamanan Guru", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedTextField(
                                            value = newPassword,
                                            onValueChange = { newPassword = it },
                                            placeholder = { Text("Ketik sandi guru baru di sini...", fontSize = 11.sp) },
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            visualTransformation = PasswordVisualTransformation(),
                                            singleLine = true
                                        )

                                        Button(
                                            onClick = {
                                                if (newPassword.trim().length < 4) {
                                                    Toast.makeText(context, "Sandi minimal 4 karakter!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    sharedPrefs.edit().putString("owner_code", newPassword.trim()).apply()
                                                    newPassword = ""
                                                    Toast.makeText(context, "Sandi guru berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth().height(36.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Terapkan Sandi", fontSize = 11.sp)
                                        }
                                    }
                                }

                                // Export & Import backup buttons
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Backup & Riwayat Masuk", fontSize = 12.sp, fontWeight = FontWeight.Bold)

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            Button(
                                                onClick = {
                                                    val backupData = "ATTENDANCES: ${serializeAttendanceList(attendanceList)}"
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                    val clip = android.content.ClipData.newPlainText("Presensi Backup", backupData)
                                                    clipboard.setPrimaryClip(clip)
                                                    Toast.makeText(context, "Backup presensi disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.weight(1f).height(36.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("Salin Backup", fontSize = 10.sp)
                                            }

                                            Button(
                                                onClick = {
                                                    sharedPrefs.edit().putString("classroom_attendance", "").apply()
                                                    onAttendanceChanged("")
                                                    Toast.makeText(context, "Database presensi dikosongkan!", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.weight(1f).height(36.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("Reset Absensi", fontSize = 10.sp)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Riwayat Login Guru Terakhir:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                        if (historyList.isEmpty()) {
                                            Text("Belum ada riwayat masuk tercapai.", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                        } else {
                                            historyList.take(4).forEach { (authName, timeLogged) ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(authName, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    Text(timeLogged, fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun AdminSettingsDialog_Deprecated(
    onDismiss: () -> Unit,
    viewModel: ScheduleViewModel,
    attendanceList: List<StudentAttendance>,
    onAttendanceChanged: (String) -> Unit
) {
    // Empty
}

/*
fun AdminSettingsDialog_Deprecated_Commented(
    onDismiss: () -> Unit,
    viewModel: ScheduleViewModel,
    attendanceList: List<StudentAttendance>,
    onAttendanceChanged: (String) -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("jadwalku_prefs", Context.MODE_PRIVATE) }

    // Manage local input for password
    var newPassword by remember { mutableStateOf("") }

    // Manage local announcement inputs
    var activeAnnState by remember { mutableStateOf(sharedPrefs.getBoolean("announcement_enabled", true)) }
    var annTextState by remember { mutableStateOf(sharedPrefs.getString("announcement_text", "Selamat datang di JadwalKu! Hubungi Guru untuk memasang pengumuman kelas baru di sini secara langsung.") ?: "") }
    var annAuthorState by remember { mutableStateOf(sharedPrefs.getString("announcement_author", "Wali Kelas") ?: "") }
    var annTypeState by remember { mutableStateOf(sharedPrefs.getString("announcement_type", "Info") ?: "") }

    // Manage live history list state
    var rawHistory by remember { mutableStateOf(sharedPrefs.getString("login_history", "") ?: "") }

    val historyList = remember(rawHistory) {
        if (rawHistory.isBlank()) {
            emptyList()
        } else {
            rawHistory.split(";").mapNotNull { entry ->
                val parts = entry.split("|")
                if (parts.size >= 2) {
                    parts[0] to parts[1]
                } else {
                    null
                }
            }.reversed() // Show newest first
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Tutup")
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Pengaturan Guru",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Change password
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "UBAH SANDI GURU",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            placeholder = { Text("Masukkan sandi baru...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                if (newPassword.trim().isEmpty()) {
                                    Toast.makeText(context, "Sandi tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                                } else {
                                    val trimmedSub = newPassword.trim()
                                    sharedPrefs.edit().putString("owner_password", trimmedSub).apply()
                                    Toast.makeText(context, "Sandi guru berhasil diubah!", Toast.LENGTH_SHORT).show()
                                    newPassword = ""
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Simpan Sandi")
                        }
                    }
                }

                // Section 2: Kelola Mading/Pengumuman Kelas (Mading Digital Broadcast)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "KELOLA MADING DIGITAL KELAS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tampilkan Mading", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                            Switch(
                                checked = activeAnnState,
                                onCheckedChange = { activeAnnState = it }
                            )
                        }

                        OutlinedTextField(
                            value = annTextState,
                            onValueChange = { annTextState = it },
                            label = { Text("Pesan Mading / Pengumuman") },
                            placeholder = { Text("Isi pemberitahuan untuk siswa di sini...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            maxLines = 4
                        )

                        OutlinedTextField(
                            value = annAuthorState,
                            onValueChange = { annAuthorState = it },
                            label = { Text("Pengirim") },
                            placeholder = { Text("e.g. Wali Kelas / Guru BK") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )

                        Text("Tingkat Urgensi / Tag:", fontSize = 12.sp, fontWeight = FontWeight.Bold)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Info", "Penting", "Himbauan", "Tugas").forEach { type ->
                                val isSelected = annTypeState == type
                                val chipColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(chipColor)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { annTypeState = type }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = type, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor)
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val sdf = java.text.SimpleDateFormat("dd MMMM yyyy, HH:mm", java.util.Locale("id", "ID"))
                                val dateStr = sdf.format(java.util.Date())

                                sharedPrefs.edit()
                                    .putBoolean("announcement_enabled", activeAnnState)
                                    .putString("announcement_text", annTextState.trim())
                                    .putString("announcement_author", annAuthorState.trim())
                                    .putString("announcement_type", annTypeState)
                                    .putString("announcement_date", dateStr)
                                    .apply()

                                Toast.makeText(context, "Mading Digital Berhasil Diperbarui!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Perbarui Mading")
                        }
                    }
                }

                // Section 2.5: Kelola & Monitor Presensi Siswa (Today's Attendance Panel)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "KELOLA & MONITOR PRESENSI SISWA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Summary Stats Row
                        val statsHadir = attendanceList.count { it.status == "Hadir" }
                        val statsIzin = attendanceList.count { it.status == "Izin" }
                        val statsSakit = attendanceList.count { it.status == "Sakit" }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color(0xFFECFDF5), RoundedCornerShape(8.dp))
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Hadir", fontSize = 9.sp, color = Color(0xFF047857), fontWeight = FontWeight.Bold)
                                    Text("$statsHadir", fontSize = 14.sp, color = Color(0xFF065F46), fontWeight = FontWeight.Black)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color(0xFFEFF6FF), RoundedCornerShape(8.dp))
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Izin", fontSize = 9.sp, color = Color(0xFF1D4ED8), fontWeight = FontWeight.Bold)
                                    Text("$statsIzin", fontSize = 14.sp, color = Color(0xFF1E40AF), fontWeight = FontWeight.Black)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color(0xFFFEF3C7), RoundedCornerShape(8.dp))
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Sakit", fontSize = 9.sp, color = Color(0xFFB45309), fontWeight = FontWeight.Bold)
                                    Text("$statsSakit", fontSize = 14.sp, color = Color(0xFF92400E), fontWeight = FontWeight.Black)
                                }
                            }
                        }

                        // Filter Selection
                        var filterStatus by remember { mutableStateOf("Semua") }
                        val filteredList = remember(attendanceList, filterStatus) {
                            if (filterStatus == "Semua") attendanceList
                            else attendanceList.filter { it.status == filterStatus }
                        }

                        Text("Daftar Presensi Hari ini:", fontSize = 11.sp, fontWeight = FontWeight.Bold)

                        // Select Filter Chips Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("Semua", "Hadir", "Izin", "Sakit").forEach { f ->
                                val acts = filterStatus == f
                                val containerCol = if (acts) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                                val textCol = if (acts) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(containerCol)
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                        .clickable { filterStatus = f }
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(f, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textCol)
                                }
                            }
                        }

                        // Log Items List
                        if (filteredList.isEmpty()) {
                            Text(
                                text = "Tidak ada riwayat presensi yang cocok.",
                                fontSize = 11.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 160.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                filteredList.forEach { item ->
                                    val rowBg = when (item.status) {
                                        "Hadir" -> Color(0xFFECFDF5).copy(alpha = 0.5f)
                                        "Izin" -> Color(0xFFEFF6FF).copy(alpha = 0.5f)
                                        else -> Color(0xFFFEF3C7).copy(alpha = 0.5f)
                                    }
                                    val borderCol = when (item.status) {
                                        "Hadir" -> Color(0xFF10B981)
                                        "Izin" -> Color(0xFF3B82F6)
                                        else -> Color(0xFFF59E0B)
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(rowBg, RoundedCornerShape(6.dp))
                                            .border(0.5.dp, borderCol.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                            .padding(6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(item.studentName, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Surface(
                                                    color = borderCol.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        item.status,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = borderCol,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            Text("Waktu: ${item.date} ${item.time}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (item.note.isNotEmpty() && item.note != "-") {
                                                Text("Info: ${item.note}", fontSize = 9.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                val updated = attendanceList.filter { it.id != item.id }
                                                val serialized = serializeAttendanceList(updated)
                                                sharedPrefs.edit().putString("classroom_attendance", serialized).apply()
                                                onAttendanceChanged(serialized)
                                                Toast.makeText(context, "Presensi dihapus!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Hapus Presensi",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)))

                        // Add Manual Attendance Form
                        var showAddManual by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showAddManual = !showAddManual },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Input Presensi Manual Guru", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            Icon(
                                imageVector = if (showAddManual) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }

                        if (showAddManual) {
                            var manualName by remember { mutableStateOf("") }
                            var manualStatus by remember { mutableStateOf("Hadir") }
                            var manualNote by remember { mutableStateOf("") }

                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                OutlinedTextField(
                                    value = manualName,
                                    onValueChange = { manualName = it },
                                    label = { Text("Nama Siswa", fontSize = 10.sp) },
                                    placeholder = { Text("e.g. Ahmad Dani", fontSize = 11.sp) },
                                    shape = RoundedCornerShape(6.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("Hadir", "Izin", "Sakit").forEach { s ->
                                        val isSel = manualStatus == s
                                        val col = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(col)
                                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                                .clickable { manualStatus = s }
                                                .padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(s, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = manualNote,
                                    onValueChange = { manualNote = it },
                                    label = { Text("Keterangan", fontSize = 10.sp) },
                                    placeholder = { Text("e.g. Rawat Inap, Urusan Keluarga (Opsional)", fontSize = 11.sp) },
                                    shape = RoundedCornerShape(6.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Button(
                                    onClick = {
                                        if (manualName.trim().isEmpty()) {
                                            Toast.makeText(context, "Nama Siswa tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val currentTodayDate = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                                            val currentOnlyTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())

                                            val newAtt = StudentAttendance(
                                                id = "att_" + System.currentTimeMillis(),
                                                studentName = manualName.trim(),
                                                date = currentTodayDate,
                                                time = currentOnlyTime,
                                                status = manualStatus,
                                                note = if (manualNote.trim().isEmpty()) "-" else manualNote.trim()
                                            )
                                            val updated = attendanceList + newAtt
                                            val serialized = serializeAttendanceList(updated)
                                            sharedPrefs.edit().putString("classroom_attendance", serialized).apply()
                                            onAttendanceChanged(serialized)

                                            manualName = ""
                                            manualNote = ""
                                            Toast.makeText(context, "Presensi Mandiri Disimpan Guru!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.align(Alignment.End).height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                ) {
                                    Text("Simpan Presensi", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Clear All Button
                        if (attendanceList.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    sharedPrefs.edit().remove("classroom_attendance").apply()
                                    onAttendanceChanged("")
                                    Toast.makeText(context, "Seluruh presensi hari ini dibersihkan!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Kosongkan Log Presensi", fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Section 3: Ringkasan & Statistik Jadwal Pelajaran (Schedule Insights)
                val allSchedulesList by viewModel.allSchedules.collectAsStateWithLifecycle()
                val totalSubjects = allSchedulesList.size

                // Let's calculate busy day & free day
                val dayCounts = allSchedulesList.groupBy { it.day }.mapValues { it.value.size }
                val busiestDay = dayCounts.maxByOrNull { it.value }?.key ?: "-"
                val busiestCount = dayCounts[busiestDay] ?: 0

                // Calculate total unique teachers
                val teacherCount = allSchedulesList.map { it.teacherName }.distinct().filter { it != "Sekolah" && it != "Semua" && it.isNotBlank() }.size

                // Calculate total unique rooms
                val roomCount = allSchedulesList.map { it.roomName }.distinct().filter { it != "Kantin" && it.isNotBlank() }.size

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "RINGKASAN & STATISTIK JADWAL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Column 1
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text("Total Sesi", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("$totalSubjects Sesi", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text("Guru Pengajar", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("$teacherCount Guru", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Column 2
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text("Ruang Terpakai", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("$roomCount Ruangan", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text("Hari Terpadat", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("$busiestDay ($busiestCount)", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 4: Pengembalian Data (Reset & Backup)
                var showResetConfirm by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "PEMULIHAN DATA & EKSPOR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Cadangkan semua data jadwal sebagai teks, atau pulihkan template jadwal default kelas X TJKT 2.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    val backupText = allSchedulesList.joinToString("\n") { s ->
                                        "${s.day}|${s.subjectName}|${s.teacherName}|${s.roomName}|${s.startTime}|${s.endTime}"
                                    }
                                    if (backupText.isBlank()) {
                                        Toast.makeText(context, "Tidak ada data jadwal!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("JadwalKu Backup", backupText)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Data jadwal disalin ke papan klip!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).padding(end = 4.dp)
                            ) {
                                Text("Ekspor Teks", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = { showResetConfirm = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).padding(start = 4.dp)
                            ) {
                                Text("Reset Awal", fontSize = 11.sp)
                            }
                        }
                    }
                }

                if (showResetConfirm) {
                    AlertDialog(
                        onDismissRequest = { showResetConfirm = false },
                        title = { Text("Reset ke Jadwal Awal?") },
                        text = { Text("Tindakan ini akan menghapus semua jadwal saat ini dan mengatur ulang kembali jadwal SMK Muh 1 Purwokerto kelas X TJKT 2.") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.resetToPresetSchedules(context)
                                    showResetConfirm = false
                                    Toast.makeText(context, "Semua jadwal berhasil dikembalikan!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Ya, Reset")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showResetConfirm = false }) {
                                Text("Batal")
                            }
                        }
                    )
                }

                // Section 5: Student Login History list
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RIWAYAT LOGIN SISWA / UMUM",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (historyList.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    sharedPrefs.edit().remove("login_history").apply()
                                    rawHistory = ""
                                    Toast.makeText(context, "Riwayat dibersihkan!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Bersihkan", fontSize = 11.sp)
                            }
                        }
                    }

                    if (historyList.isEmpty()) {
                        Text(
                            text = "Belum ada siswa / umum yang tercatat login.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.heightIn(max = 240.dp)
                        ) {
                            historyList.forEach { (name, time) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (name.isNotEmpty()) name.take(1).uppercase() else "?",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = name,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = time,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
*/

@Composable
fun NotificationSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("jadwalku_prefs", Context.MODE_PRIVATE) }

    var isNotificationEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("notification_enabled", true)) }
    var isVibrationEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("vibration_enabled", true)) }
    var vibrationPattern by remember { mutableStateOf(sharedPrefs.getString("vibration_pattern", "Default") ?: "Default") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    sharedPrefs.edit()
                        .putBoolean("notification_enabled", isNotificationEnabled)
                        .putBoolean("vibration_enabled", isVibrationEnabled)
                        .putString("vibration_pattern", vibrationPattern)
                        .apply()
                    Toast.makeText(context, "Pengaturan notifikasi disimpan!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Suasana & Notifikasi",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Atur pengingat pelajaran, status pemberitahuan dan getaran haptic telepon Anda.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Row 1: Enable notifications switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Aktifkan Pengingat",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Tampilkan notifikasi sebelum pelajaran dimulai.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isNotificationEnabled,
                        onCheckedChange = { isNotificationEnabled = it }
                    )
                }

                // Row 2: Enable vibration switch (only active if notification enabled)
                AnimatedVisibility(visible = isNotificationEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Efek Getar (Vibrate)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Telepon bergetar saat pengingat aktif.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isVibrationEnabled,
                                onCheckedChange = { isVibrationEnabled = it }
                            )
                        }

                        // Row 3: Vibration Pattern selection
                        AnimatedVisibility(visible = isVibrationEnabled) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Pilih Pola Getar",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                val patterns = listOf("Default", "Pendek", "Panjang", "Denyut")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    patterns.forEach { pattern ->
                                        val isSelected = vibrationPattern == pattern
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                                )
                                                .clickable {
                                                    vibrationPattern = pattern
                                                    // Trigger short haptic feedback to let user feel it
                                                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                                    if (vibrator != null && vibrator.hasVibrator()) {
                                                        val pat = when (pattern) {
                                                            "Pendek" -> longArrayOf(0, 100, 80, 100)
                                                            "Panjang" -> longArrayOf(0, 800)
                                                            "Denyut" -> longArrayOf(0, 150, 100, 150)
                                                            else -> longArrayOf(0, 250)
                                                        }
                                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                            vibrator.vibrate(
                                                                android.os.VibrationEffect.createWaveform(pat, -1)
                                                            )
                                                        } else {
                                                            @Suppress("DEPRECATION")
                                                            vibrator.vibrate(pat, -1)
                                                        }
                                                    }
                                                }
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = pattern,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 4: Live Test Notification
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Uji Coba Notifikasi",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Text(
                            text = "Tekan tombol di bawah untuk langsung mencoba notifikasi dan pola geteran yang Anda pilih saat ini.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Button(
                            onClick = {
                                // Save preferences temporarily for testing
                                sharedPrefs.edit()
                                    .putBoolean("notification_enabled", isNotificationEnabled)
                                    .putBoolean("vibration_enabled", isVibrationEnabled)
                                    .putString("vibration_pattern", vibrationPattern)
                                    .apply()

                                // Instantly trigger test notification by sending direct broadcast
                                val testIntent = Intent(context, AlarmReceiver::class.java).apply {
                                    putExtra("schedule_id", 9999)
                                    putExtra("subject_name", "Pelajaran Simulasi (Tes)")
                                    putExtra("teacher_name", "Bpk. Guru")
                                    putExtra("room_name", "Lab Komputer")
                                    putExtra("start_time", "Sekarang")
                                }
                                context.sendBroadcast(testIntent)
                                Toast.makeText(context, "Notifikasi Tes terkirim!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tes Notifikasi & Getar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun DetailRowItem(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

// Modal Form Component: Create/Update schedule details
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditScheduleDialog(
    scheduleToEdit: Schedule?,
    chosenDay: String,
    onDismiss: () -> Unit,
    onSave: (Schedule) -> Unit
) {
    val context = LocalContext.current

    // States linking values
    var subjectName by remember { mutableStateOf(scheduleToEdit?.subjectName ?: "") }
    var teacherName by remember { mutableStateOf(scheduleToEdit?.teacherName ?: "") }
    var roomName by remember { mutableStateOf(scheduleToEdit?.roomName ?: "Kelas X TJKT 2") }
    var daySelected by remember { mutableStateOf(scheduleToEdit?.day ?: chosenDay) }
    var startTime by remember { mutableStateOf(scheduleToEdit?.startTime ?: "07:30") }
    var endTime by remember { mutableStateOf(scheduleToEdit?.endTime ?: "08:10") }
    var periodNo by remember { mutableStateOf(scheduleToEdit?.periodNumber?.toString() ?: "1") }
    var selectedColorHex by remember { mutableStateOf(scheduleToEdit?.colorHex ?: PRESET_COLORS.first()) }
    var reminderOn by remember { mutableStateOf(scheduleToEdit?.isReminderEnabled ?: false) }

    // Dropdown day toggle array
    val daysList = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    if (subjectName.isBlank()) {
                        Toast.makeText(context, "Mata pelajaran tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val isHourValid = startTime.contains(":") && endTime.contains(":")
                    if (!isHourValid) {
                        Toast.makeText(context, "Format jam masuk/bubar harus berupa HH:MM!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val scheduleToSubmit = Schedule(
                        id = scheduleToEdit?.id ?: 0,
                        day = daySelected,
                        subjectName = subjectName,
                        teacherName = teacherName,
                        roomName = roomName,
                        startTime = startTime,
                        endTime = endTime,
                        periodNumber = periodNo.toIntOrNull() ?: 0,
                        colorHex = selectedColorHex,
                        isReminderEnabled = reminderOn
                    )
                    onSave(scheduleToSubmit)
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (scheduleToEdit == null) "Tambahkan" else "Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        },
        title = {
            Text(
                text = if (scheduleToEdit == null) "Tambah Mata Pelajaran" else "Edit Jadwal Pelajaran",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Outlined Input: Subject Name
                OutlinedTextField(
                    value = subjectName,
                    onValueChange = { subjectName = it },
                    label = { Text("Nama Pelajaran *") },
                    placeholder = { Text("Contoh: Matematika, IPAS, B.Ind") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("form_input_subject"),
                    singleLine = true
                )

                // Outlined Input: Teacher Name
                OutlinedTextField(
                    value = teacherName,
                    onValueChange = { teacherName = it },
                    label = { Text("Guru Pengampu") },
                    placeholder = { Text("Contoh: Roxy, Ragil, Rizmah") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("form_input_teacher"),
                    singleLine = true
                )

                // Outlined Input: Room Classroom
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text("Kelas / Ruangan") },
                    placeholder = { Text("Contoh: Kelas X TJKT 2, Lab IPAS") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("form_input_room"),
                    singleLine = true
                )

                // Day chip selector row
                Column {
                    Text("Pilih Hari:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(daysList) { dayName ->
                            val active = dayName.equals(daySelected, ignoreCase = true)
                            FilterChip(
                                selected = active,
                                onClick = { daySelected = dayName },
                                label = { Text(dayName, fontSize = 12.sp) }
                            )
                        }
                    }
                }

                // Time picker Row ( HH:MM starting clock )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Start Time trigger click button
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Jam Mulai:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                .clickable {
                                    showTimePickerDialog(context, startTime) { startTime = it }
                                }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(startTime, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }

                    // End time trigger click button
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Jam Selesai:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                .clickable {
                                    showTimePickerDialog(context, endTime) { endTime = it }
                                }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(endTime, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Row containing optional Period Id and Alarm Switch side by side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Outlined Input: Period Jam Ke-
                    OutlinedTextField(
                        value = periodNo,
                        onValueChange = { periodNo = it },
                        label = { Text("Jam Ke-") },
                        placeholder = { Text("1") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    // Switch Alert Option
                    Row(
                        modifier = Modifier
                            .weight(1.2f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .clickable { reminderOn = !reminderOn }
                            .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Notifikasi", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Switch(
                            checked = reminderOn,
                            onCheckedChange = { reminderOn = it },
                            modifier = Modifier.scale(0.85f)
                        )
                    }
                }

                // Theme color pallet picker
                Column {
                    Text("Pilih Warna Label:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 8,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PRESET_COLORS.forEach { hexColor ->
                            val color = parseHexColor(hexColor)
                            val isActive = hexColor == selectedColorHex

                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(color, CircleShape)
                                    .clickable { selectedColorHex = hexColor }
                                    .then(
                                        if (isActive) Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary,
                                            CircleShape
                                        ) else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isActive) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Terpilih",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}



// Helper: Show dialog select clock timer picker wheel
fun showTimePickerDialog(
    context: Context,
    initialTime: String,
    onTimeSelected: (String) -> Unit
) {
    val times = initialTime.split(":")
    val hh = times.getOrNull(0)?.toIntOrNull() ?: 7
    val mm = times.getOrNull(1)?.toIntOrNull() ?: 30

    TimePickerDialog(
        context,
        { _, hour, minute ->
            val formatted = String.format("%02d:%02d", hour, minute)
            onTimeSelected(formatted)
        },
        hh,
        mm,
        true
    ).show()
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Composable
fun ClassAnnouncementBanner(
    enabled: Boolean,
    text: String,
    author: String,
    type: String,
    date: String,
    isOwner: Boolean,
    onEditClick: (() -> Unit)? = null
) {
    if (!enabled || text.isBlank()) return

    var isDismissed by remember { mutableStateOf(false) }
    if (isDismissed) return

    val context = LocalContext.current

    val containerColor = when (type) {
        "Penting" -> Color(0xFFFEE2E2)
        "Himbauan" -> Color(0xFFECFDF5)
        "Tugas" -> Color(0xFFFEF3C7)
        else -> Color(0xFFEFF6FF)
    }

    val contentColor = when (type) {
        "Penting" -> Color(0xFF991B1B)
        "Himbauan" -> Color(0xFF065F46)
        "Tugas" -> Color(0xFF92400E)
        else -> Color(0xFF1E40AF)
    }

    val icon = when (type) {
        "Penting" -> Icons.Default.Warning
        "Himbauan" -> Icons.Default.Check
        "Tugas" -> Icons.Default.Star
        else -> Icons.Default.Info
    }

    val label = when (type) {
        "Penting" -> "PENTING"
        "Himbauan" -> "HIMBAUAN"
        "Tugas" -> "TUGAS KELAS"
        else -> "PENGUMUMAN"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = contentColor
                    )
                    Text(
                        text = "$label • Oleh: $author",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Pengumuman JadwalKu", text)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Pengumuman disalin!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Salin Pengumuman",
                            modifier = Modifier.size(13.dp),
                            tint = contentColor.copy(alpha = 0.8f)
                        )
                    }

                    IconButton(
                        onClick = { isDismissed = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Sembunyikan",
                            modifier = Modifier.size(13.dp),
                            tint = contentColor.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = text,
                fontSize = 12.sp,
                color = contentColor.copy(alpha = 0.9f),
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dibuat: $date",
                    fontSize = 9.sp,
                    color = contentColor.copy(alpha = 0.6f)
                )
                if (isOwner && onEditClick != null) {
                    Text(
                        text = "Kelola mading ↗",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        modifier = Modifier
                            .clickable { onEditClick() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HomeworkTabContent(
    isOwner: Boolean,
    homeworks: List<Homework>,
    completedHwIds: Set<String>,
    onToggleComplete: (String) -> Unit,
    onAddHomeworkClick: () -> Unit,
    onDeleteHomework: (String) -> Unit,
    allSchedules: List<Schedule>,
    journals: List<ClassJournal>,
    onAddJournal: (String, String, String, String) -> Unit,
    onDeleteJournal: (String) -> Unit
) {
    var activeSubTab by remember { mutableStateOf(0) } // 0: Tugas Kelas, 1: Jurnal Belajar Kelas
    var filterPriority by remember { mutableStateOf("Semua") }
    var journalSearchQuery by remember { mutableStateOf("") }
    var showAddJournalDialog by remember { mutableStateOf(false) }

    val filteredHomeworks = remember(homeworks, filterPriority) {
        if (filterPriority == "Semua") homeworks
        else homeworks.filter { it.priority.equals(filterPriority, ignoreCase = true) }
    }

    val filteredJournals = remember(journals, journalSearchQuery) {
        if (journalSearchQuery.isBlank()) journals.reversed()
        else journals.filter {
            it.subject.contains(journalSearchQuery, ignoreCase = true) ||
            it.topic.contains(journalSearchQuery, ignoreCase = true) ||
            it.activity.contains(journalSearchQuery, ignoreCase = true)
        }.reversed()
    }

    val completedCount = remember(homeworks, completedHwIds) {
        homeworks.count { completedHwIds.contains(it.id) }
    }
    val totalCount = homeworks.size
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0.0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // SEGMENT SWITCHER: Capsule Styled
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Tugas & PR Siswa" to Icons.Default.List, "Jurnal Mengajar Guru" to Icons.Default.Info).forEachIndexed { index, pair ->
                val isSelected = activeSubTab == index
                val capBg = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                val capTint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(capBg)
                        .clickable { activeSubTab = index }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = pair.second,
                            contentDescription = null,
                            tint = capTint,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = pair.first,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = capTint
                        )
                    }
                }
            }
        }

        if (activeSubTab == 0) {
            // ==================== TUGAS & PR TAB ====================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Ruang Tugas & PR",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "SMK MUH 1 PURWOKERTO • X TJKT 2",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                    if (isOwner) {
                        Button(
                            onClick = onAddHomeworkClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.secondary
                                            )
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tugas Baru", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            if (totalCount > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Progres Tugas Kelas",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$completedCount dari $totalCount Selesai",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (progress >= 1.0f) "Luar biasa! Semua tugas terselesaikan sempurna! 🎉"
                                   else "Terus pantau tugasmu agar tidak tertinggal! Semangat belajar! 💪",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Prioritas:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf("Semua", "Gawat", "Penting", "Biasa").forEach { priority ->
                    val isSelected = filterPriority == priority
                    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    val textCol = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(bg)
                            .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                            .clickable { filterPriority = priority }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(priority, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textCol)
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (filteredHomeworks.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Tidak Ada Tugas",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Nikmati harimu sepenuhnya tanpa beban PR kelas! ✨",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredHomeworks) { hw ->
                            val isDone = completedHwIds.contains(hw.id)
                            val priorityColor = when (hw.priority) {
                                "Gawat" -> Color(0xFFEF4444)
                                "Penting" -> Color(0xFFF59E0B)
                                else -> Color(0xFF3B82F6)
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("homework_item_${hw.id}"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDone) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isDone) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            else priorityColor.copy(alpha = 0.25f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = hw.subject,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                color = priorityColor.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = hw.priority.uppercase(),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = priorityColor,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }

                                            Checkbox(
                                                checked = isDone,
                                                onCheckedChange = { onToggleComplete(hw.id) },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = MaterialTheme.colorScheme.primary,
                                                    uncheckedColor = MaterialTheme.colorScheme.outline
                                                ),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = hw.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                                        textDecoration = if (isDone) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = hw.description,
                                        fontSize = 12.sp,
                                        color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 16.sp
                                    )

                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DateRange,
                                                contentDescription = null,
                                                modifier = Modifier.size(13.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "Tenggat: " + hw.dueDate,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Dibuat: " + hw.createdAt,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            if (isOwner) {
                                                IconButton(
                                                    onClick = { onDeleteHomework(hw.id) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Hapus Tugas",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ==================== JURNAL BELAJAR / AGENDA MENGAJAR TAB ====================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Jurnal Pelajaran Kelas",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Log pembelajaran & rangkuman harian terlacak",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }

                    if (isOwner) {
                        Button(
                            onClick = { showAddJournalDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Log Jurnal", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // Search Filter
            OutlinedTextField(
                value = journalSearchQuery,
                onValueChange = { journalSearchQuery = it },
                placeholder = { Text("Cari subjek pelajaran, topik materi...", fontSize = 12.sp) },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                singleLine = true
            )

            Box(modifier = Modifier.weight(1f)) {
                if (filteredJournals.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Log Jurnal Kosong",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (journalSearchQuery.isNotEmpty()) "Hasil pencarian tidak cocok dengan subjek atau materi apa pun."
                                   else "Belum ada log agenda pembelajaran dicatat oleh Guru. Hubungi Guru jika ingin merangkum materi kelas hari ini!",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredJournals, key = { it.id }) { journal ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = journal.subject,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = journal.date,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            if (isOwner) {
                                                IconButton(
                                                    onClick = { onDeleteJournal(journal.id) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Hapus Log",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "Topik Materi: " + journal.topic,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    if (journal.activity.isNotEmpty() && journal.activity != "-") {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text("📝", fontSize = 12.sp)
                                            Text(
                                                text = "Aktivitas Kelas: " + journal.activity,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (journal.note.isNotEmpty() && journal.note != "-") {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text("💡", fontSize = 12.sp)
                                            Text(
                                                text = "Catatan Guru: " + journal.note,
                                                fontSize = 12.sp,
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog: Add Journal Log
    if (showAddJournalDialog) {
        var inputSubject by remember { mutableStateOf("") }
        var inputTopic by remember { mutableStateOf("") }
        var inputActivity by remember { mutableStateOf("") }
        var inputNote by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddJournalDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Jurnal Mengajar Guru Berjalan", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = inputSubject,
                        onValueChange = { inputSubject = it },
                        label = { Text("Subjek Pelajaran") },
                        placeholder = { Text("e.g. Produktif TJKT, PPKn, MTK") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = inputTopic,
                        onValueChange = { inputTopic = it },
                        label = { Text("Topik Materi Bahasan") },
                        placeholder = { Text("e.g. Konfigurasi Mikrotik Routing, Trigono") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = inputActivity,
                        onValueChange = { inputActivity = it },
                        label = { Text("Aktivitas / Tugas yang Diberikan") },
                        placeholder = { Text("e.g. Praktikum Lab 3 Kabel Cross, PR No 1-5") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )

                    OutlinedTextField(
                        value = inputNote,
                        onValueChange = { inputNote = it },
                        label = { Text("Keterangan Tambahan (Temuan/Evaluasi)") },
                        placeholder = { Text("e.g. 3 siswa belum selesai, materi tuntas (Opsional)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputSubject.trim().isEmpty() || inputTopic.trim().isEmpty()) {
                            // Empty validation silently
                        } else {
                            onAddJournal(
                                inputSubject.trim(),
                                inputTopic.trim(),
                                if (inputActivity.trim().isEmpty()) "-" else inputActivity.trim(),
                                if (inputNote.trim().isEmpty()) "-" else inputNote.trim()
                            )
                            showAddJournalDialog = false
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Catat Jurnal")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddJournalDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun HomeworkTabContent_Deprecated(
    isOwner: Boolean,
    homeworks: List<Homework>,
    completedHwIds: Set<String>,
    onToggleComplete: (String) -> Unit,
    onAddHomeworkClick: () -> Unit,
    onDeleteHomework: (String) -> Unit,
    allSchedules: List<Schedule>
) {
    var filterPriority by remember { mutableStateOf("Semua") }

    val filteredHomeworks = remember(homeworks, filterPriority) {
        if (filterPriority == "Semua") homeworks
        else homeworks.filter { it.priority.equals(filterPriority, ignoreCase = true) }
    }

    val completedCount = remember(homeworks, completedHwIds) {
        homeworks.count { completedHwIds.contains(it.id) }
    }
    val totalCount = homeworks.size
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0.0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Ruang Tugas & PR",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "SMK MUH 1 PURWOKERTO • X TJKT 2",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
                if (isOwner) {
                    Button(
                        onClick = onAddHomeworkClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tugas Baru", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        if (totalCount > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Progres Tugas Kelas",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$completedCount dari $totalCount Selesai",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (progress >= 1.0f) "Luar biasa! Semua tugas terselesaikan sempurna! 🎉"
                               else "Terus pantau tugasmu agar tidak tertinggal! Semangat belajar! 💪",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Prioritas:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf("Semua", "Gawat", "Penting", "Biasa").forEach { priority ->
                val isSelected = filterPriority == priority
                val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                val textCol = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg)
                        .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .clickable { filterPriority = priority }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(priority, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textCol)
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (filteredHomeworks.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Tidak Ada Tugas",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Nikmati harimu sepenuhnya tanpa beban PR kelas! ✨",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredHomeworks) { hw ->
                        val isDone = completedHwIds.contains(hw.id)
                        val priorityColor = when (hw.priority) {
                            "Gawat" -> Color(0xFFEF4444)
                            "Penting" -> Color(0xFFF59E0B)
                            else -> Color(0xFF3B82F6)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleComplete(hw.id) },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                width = if (isDone) 2.dp else 1.dp,
                                color = if (isDone) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDone) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.Info,
                                            contentDescription = "Status Selesai",
                                            tint = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(24.dp).clickable { onToggleComplete(hw.id) }
                                        )
                                        Column {
                                            Text(
                                                text = hw.title,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                                                style = if (isDone) androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle.Default
                                            )
                                            Text(
                                                text = "Mapel: " + hw.subject,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Surface(
                                        color = priorityColor.copy(alpha = 0.15f),
                                        contentColor = priorityColor,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = hw.priority.uppercase(),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = hw.description,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DateRange,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Tenggat: " + hw.dueDate,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Dibuat: " + hw.createdAt,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                        if (isOwner) {
                                            IconButton(
                                                onClick = { onDeleteHomework(hw.id) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Hapus Tugas",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConsultationTabContent(
    isOwner: Boolean,
    questions: List<StudentQuestion>,
    onAddQuestion: (String, String, String) -> Unit,
    onAnswerQuestion: (String, String) -> Unit,
    studentName: String
) {
    var selectedSubject by remember { mutableStateOf("Umum") }
    var inputQuestion by remember { mutableStateOf("") }
    var inputName by remember { mutableStateOf(studentName.ifBlank { "Siswa Anonim" }) }

    var activeAnswerQuestionId by remember { mutableStateOf<String?>(null) }
    var answerInputContent by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Layanan Konsultasi & Tanya Jawab",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Kolaborasi interaktif online antara siswa dan guru SMK MUH 1 PURWOKERTO kelas X TJKT 2.",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                )
            }
        }

        if (!isOwner) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Kirim Pertanyaan Baru ke Guru",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inputName,
                            onValueChange = { inputName = it },
                            label = { Text("Nama Siswa", fontSize = 11.sp) },
                            modifier = Modifier.weight(1.3f),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = selectedSubject,
                            onValueChange = { selectedSubject = it },
                            label = { Text("Mata Pelajaran (Mapel)", fontSize = 11.sp) },
                            modifier = Modifier.weight(1.2f),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    OutlinedTextField(
                        value = inputQuestion,
                        onValueChange = { inputQuestion = it },
                        label = { Text("Detail Pertanyaan") },
                        placeholder = { Text("Tulis pertanyaan Anda secara jelas untuk direspons oleh guru...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Button(
                        onClick = {
                            if (inputQuestion.isBlank()) {
                                return@Button
                            }
                            onAddQuestion(inputName.trim(), selectedSubject.trim(), inputQuestion.trim())
                            inputQuestion = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .align(Alignment.End)
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(imageVector = Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Kirim", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = "Daftar Diskusi & Pertanyaan Kelas",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(modifier = Modifier.weight(1f)) {
            if (questions.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.MailOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Belum Ada Diskusi",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Pertanyaan yang dikirimkan oleh siswa akan terlihat di sini.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(questions.reversed()) { q ->
                        val isReplied = q.answerText.isNotBlank()

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isReplied) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (q.studentName.isNotEmpty()) q.studentName.take(1).uppercase() else "?",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = q.studentName,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Mapel: " + q.subject,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Surface(
                                        color = if (isReplied) Color(0xFFD1FAE5) else Color(0xFFFEF3C7),
                                        contentColor = if (isReplied) Color(0xFF065F46) else Color(0xFF92400E),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = if (isReplied) "DIJAWAB GURU" else "MENUNGGU",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = q.questionText,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 17.sp
                                )

                                Text(
                                    text = q.timestamp,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(top = 4.dp)
                                )

                                if (isReplied) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = "Tanggapan Guru:",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = q.answerText,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                } else if (isOwner) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    if (activeAnswerQuestionId == q.id) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            OutlinedTextField(
                                                value = answerInputContent,
                                                onValueChange = { answerInputContent = it },
                                                label = { Text("Ketik tanggapan Anda...", fontSize = 11.sp) },
                                                maxLines = 3,
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            Row(
                                                modifier = Modifier.align(Alignment.End),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                TextButton(onClick = { activeAnswerQuestionId = null }) {
                                                    Text("Batal")
                                                }
                                                Button(
                                                    onClick = {
                                                        if (answerInputContent.isNotBlank()) {
                                                            onAnswerQuestion(q.id, answerInputContent.trim())
                                                            answerInputContent = ""
                                                            activeAnswerQuestionId = null
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text("Kirim Tanggapan")
                                                }
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                activeAnswerQuestionId = q.id
                                                answerInputContent = ""
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Tulis Tanggapan Guru", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddHomeworkDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf("Dasar TJKT") }
    var dueDate by remember { mutableStateOf("Senin Depan") }
    var priority by remember { mutableStateOf("Biasa") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buat Tugas / PR Baru", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Judul Tugas") },
                    placeholder = { Text("e.g. Kerjakan LKS Hal 15") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = selectedSubject,
                    onValueChange = { selectedSubject = it },
                    label = { Text("Mata Pelajaran (Mapel)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Petunjuk / Deskripsi PR") },
                    placeholder = { Text("Tulis instruksi pengerjaan detail...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("Tenggat Pengumpulan") },
                    placeholder = { Text("e.g. Kamis, 25 Juni Pukul 07:00") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Text("Tingkat Urgensi:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Biasa", "Penting", "Gawat").forEach { p ->
                        val isSelected = priority == p
                        val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        val tc = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(bg)
                                .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .clickable { priority = p }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(p, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tc)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank() || description.isBlank()) return@Button
                    onSave(selectedSubject.trim(), title.trim(), description.trim(), dueDate.trim(), priority)
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Simpan Tugas")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
