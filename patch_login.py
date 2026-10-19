import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# Default admin password from BuildConfig
# Replace val currentPassword = remember { sharedPrefs.getString("owner_password", "admin123") ?: "admin123" }
# With logic to handle BCrypt.

pwd_code = """
    val defaultPassword = BuildConfig.ADMIN_PASSWORD
    val storedPassword = remember { sharedPrefs.getString("owner_password", defaultPassword) ?: defaultPassword }

    // Rate Limiting Logic
    var loginAttempts by remember { mutableStateOf(sharedPrefs.getInt("login_attempts", 0)) }
    var lockoutTime by remember { mutableStateOf(sharedPrefs.getLong("lockout_time", 0L)) }
"""

content = content.replace('val currentPassword = remember { sharedPrefs.getString("owner_password", "admin123") ?: "admin123" }', pwd_code)

# Fix login check
# if (passwordInput == currentPassword) {
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

# Change password save:
# if (newPassword.trim().length < 4) { ... } else { sharedPrefs.edit().putString("owner_code", newPassword.trim()).apply(); newPassword = "" ... }
# Wait, let's search for the actual password saving line.

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
