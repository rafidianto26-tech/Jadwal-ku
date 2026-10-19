import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# Add imports
imports = """
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.mindrot.jbcrypt.BCrypt
"""

if "import org.mindrot.jbcrypt.BCrypt" not in content:
    content = content.replace("import androidx.compose.ui.text.input.PasswordVisualTransformation", "import androidx.compose.ui.text.input.PasswordVisualTransformation\n" + imports)

# Add getSecurePrefs function
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

if "fun getSecurePrefs" not in content:
    content = content.replace("class MainActivity : ComponentActivity() {", secure_prefs_func + "\nclass MainActivity : ComponentActivity() {")

# Replace context.getSharedPreferences
content = content.replace('context.getSharedPreferences("jadwalku_prefs", Context.MODE_PRIVATE)', 'getSecurePrefs(context)')

# Fix password check
# 1. val currentPassword = remember { sharedPrefs.getString("owner_password", "admin123") ?: "admin123" }
# Wait, let's use BuildConfig.ADMIN_PASSWORD but since it might not be set, fallback to "admin123"
# The default hash for "admin123" is BCrypt.hashpw("admin123", BCrypt.gensalt())
# But we can just check if the stored password starts with "$2a$" to know if it's hashed.

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
