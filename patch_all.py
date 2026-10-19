import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# 1. Imports
imports = """import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.mindrot.jbcrypt.BCrypt
import com.example.BuildConfig
"""
content = content.replace("package com.example", "package com.example\n\n" + imports)

# 2. Add getSecurePrefs
secure_prefs_func = """
fun getSecurePrefs(context: Context): SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        context,
        "secure_jadwalku_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}
"""
content = content.replace("class MainActivity : ComponentActivity() {", secure_prefs_func + "\nclass MainActivity : ComponentActivity() {")

# 3. Replace SharedPreferences usage
content = content.replace('context.getSharedPreferences("jadwalku_prefs", Context.MODE_PRIVATE)', 'getSecurePrefs(context)')

# 4. Login rate limiting and Bcrypt checking
pwd_code = """
    val defaultPassword = BuildConfig.ADMIN_PASSWORD
    val storedPassword = remember { sharedPrefs.getString("owner_password", defaultPassword) ?: defaultPassword }

    // Rate Limiting Logic
    var loginAttempts by remember { mutableStateOf(sharedPrefs.getInt("login_attempts", 0)) }
    var lockoutTime by remember { mutableStateOf(sharedPrefs.getLong("lockout_time", 0L)) }
"""
content = content.replace('val currentPassword = remember { sharedPrefs.getString("owner_password", "admin123") ?: "admin123" }', pwd_code)

check_code = """
                        val currentTime = System.currentTimeMillis()
                        if (currentTime < lockoutTime) {
                            Toast.makeText(context, "Terlalu banyak percobaan. Tunggu sebentar.", Toast.LENGTH_SHORT).show()
                        } else {
                            val isMatch = try {
                                if (storedPassword.startsWith("$2a$")) {
                                    BCrypt.checkpw(passwordInput, storedPassword)
                                } else {
                                    passwordInput == storedPassword // legacy fallback
                                }
                            } catch (e: Exception) { false }

                            if (isMatch) {
                                sharedPrefs.edit().putInt("login_attempts", 0).putLong("lockout_time", 0L).apply()
                                loginAttempts = 0
                                lockoutTime = 0L
                                onRoleSelected(UserRole.OWNER)
                                Toast.makeText(context, "Selamat Datang, Guru!", Toast.LENGTH_SHORT).show()
                            } else {
                                isPasswordError = true
                                val attempts = loginAttempts + 1
                                loginAttempts = attempts
                                sharedPrefs.edit().putInt("login_attempts", attempts).apply()
                                if (attempts >= 3) {
                                    val lockTime = currentTime + 60000 // 1 minute
                                    lockoutTime = lockTime
                                    sharedPrefs.edit().putLong("lockout_time", lockTime).apply()
                                    Toast.makeText(context, "Akun terkunci sementara selama 1 menit.", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Kata sandi salah! Sisa percobaan: ${3 - attempts}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
"""
content = re.sub(r'if \(\s*passwordInput == currentPassword\s*\) \{[\s\S]*?Toast\.makeText\(context, "Kata sandi salah!", Toast\.LENGTH_SHORT\)\.show\(\)\n\s*\}', check_code.strip(), content)

# 5. Fix password changing to use BCrypt and "owner_password" (also fixing the owner_code bug)
content = content.replace('sharedPrefs.edit().putString("owner_code", newPassword.trim()).apply()', 'val hashed = BCrypt.hashpw(newPassword.trim(), BCrypt.gensalt()); sharedPrefs.edit().putString("owner_password", hashed).apply()')
content = content.replace('sharedPrefs.edit().putString("owner_password", trimmedSub).apply()', 'val hashed = BCrypt.hashpw(trimmedSub, BCrypt.gensalt()); sharedPrefs.edit().putString("owner_password", hashed).apply()')

# 6. Global Error Handler
handler_code = """
        // Global Error Handler
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            android.util.Log.e("GlobalErrorHandler", "Uncaught Exception in thread ${thread.name}: ${exception.message}", exception)
        }
"""
content = re.sub(r'(override fun onCreate\(savedInstanceState: Bundle\?\) \{[^}]*super\.onCreate\(savedInstanceState\))', r'\1\n' + handler_code, content)

# 7. AddHomeworkDialog length validation
content = content.replace('if (title.isBlank() || description.isBlank()) return@Button', 'if (title.isBlank() || description.isBlank() || title.length > 255 || description.length > 1000) return@Button')

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
