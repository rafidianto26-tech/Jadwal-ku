package com.example.util

import androidx.core.text.HtmlCompat

object ValidationHelper {
    fun sanitize(input: String): String {
        // Strip HTML tags using HtmlCompat to prevent XSS-like issues
        return HtmlCompat.fromHtml(input, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
    }

    fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}
