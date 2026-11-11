package com.example

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.mindrot.jbcrypt.BCrypt
import java.util.Date

object AuthHelper {

    fun getEncryptedSharedPreferences(context: Context): SharedPreferences {
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

    fun generateJwt(role: String): String {
        val secretKeyBytes = BuildConfig.JWT_SECRET.toByteArray(Charsets.UTF_8)
        val key = Keys.hmacShaKeyFor(secretKeyBytes)
        return Jwts.builder()
            .setSubject(role)
            .setExpiration(Date(System.currentTimeMillis() + 3600000)) // 1 hour
            .signWith(key)
            .compact()
    }

    fun verifyJwt(token: String?): String? {
        if (token == null) return null
        return try {
            val secretKeyBytes = BuildConfig.JWT_SECRET.toByteArray(Charsets.UTF_8)
            val key = Keys.hmacShaKeyFor(secretKeyBytes)
            val claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .body
            claims.subject
        } catch (e: Exception) {
            null
        }
    }

    fun hashPassword(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt())
    }

    fun checkPassword(password: String, hash: String?): Boolean {
        return try {
            if (hash == null) {
                password == BuildConfig.OWNER_PASSWORD
            } else {
                BCrypt.checkpw(password, hash)
            }
        } catch (e: Exception) {
            false
        }
    }
}
