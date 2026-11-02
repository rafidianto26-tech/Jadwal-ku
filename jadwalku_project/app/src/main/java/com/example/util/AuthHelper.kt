package com.example.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import com.example.BuildConfig
import java.util.Date
import javax.crypto.SecretKey

object AuthHelper {
    fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            "secure_auth_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun getSecretKey(): SecretKey {
        val secret = BuildConfig.JWT_SECRET.padEnd(32, '0').substring(0, 32)
        return Keys.hmacShaKeyFor(secret.toByteArray())
    }

    fun generateJwt(role: String, name: String, nis: String): String {
        return Jwts.builder()
            .subject(name)
            .claim("role", role)
            .claim("nis", nis)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000)) // 1 day
            .signWith(getSecretKey())
            .compact()
    }

    fun validateJwt(token: String): Map<String, String>? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .payload

            mapOf(
                "role" to (claims.get("role", String::class.java) ?: ""),
                "name" to claims.subject,
                "nis" to (claims.get("nis", String::class.java) ?: "")
            )
        } catch (e: Exception) {
            null
        }
    }
}
