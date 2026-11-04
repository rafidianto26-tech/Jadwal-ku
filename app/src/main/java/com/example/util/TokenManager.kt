package com.example.util

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.util.Date
import javax.crypto.SecretKey
import com.example.BuildConfig

object TokenManager {
    private const val EXPIRATION_TIME = 1000 * 60 * 60 * 24 // 24 hours

    private val secretKey: SecretKey by lazy {
        val secretString = BuildConfig.SECRET_KEY
        Keys.hmacShaKeyFor(secretString.toByteArray(Charsets.UTF_8))
    }

    fun generateToken(role: String, name: String? = null): String {
        val now = System.currentTimeMillis()
        val builder = Jwts.builder()
            .claim("role", role)
            .issuedAt(Date(now))
            .expiration(Date(now + EXPIRATION_TIME))
            .signWith(secretKey, Jwts.SIG.HS256)

        name?.let { builder.claim("name", it) }

        return builder.compact()
    }

    fun validateToken(token: String): Map<String, Any>? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .payload

            val expiration = claims.expiration
            if (expiration.before(Date())) {
                null // Token expired
            } else {
                mapOf(
                    "role" to (claims["role"] as? String ?: ""),
                    "name" to (claims["name"] as? String ?: "")
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
