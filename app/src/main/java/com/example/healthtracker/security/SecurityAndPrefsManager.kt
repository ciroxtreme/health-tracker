package com.example.healthtracker.security

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

class SecurityAndPrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("health_tracker_secure_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_ENABLED = "pin_enabled"
        private const val KEY_UI_DENSITY = "ui_density_scale"
        private const val KEY_THEME_MODE = "theme_mode" // "light", "dark", "system"
        private const val KEY_TIMELINE_MODE = "timeline_mode" // "normal", "daily"
        private const val DEFAULT_UI_DENSITY = 0.85f // Mobile-first default dense UI
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isPinEnabled(): Boolean {
        return prefs.getBoolean(KEY_PIN_ENABLED, false) && prefs.getString(KEY_PIN_HASH, null) != null
    }

    fun setPin(pin: String) {
        val hash = hashPin(pin)
        prefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putBoolean(KEY_PIN_ENABLED, true)
            .apply()
    }

    fun disablePin() {
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .putBoolean(KEY_PIN_ENABLED, false)
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val savedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return savedHash == hashPin(pin)
    }

    fun getUiDensityScale(): Float {
        return prefs.getFloat(KEY_UI_DENSITY, DEFAULT_UI_DENSITY)
    }

    fun setUiDensityScale(scale: Float) {
        prefs.edit()
            .putFloat(KEY_UI_DENSITY, scale.coerceIn(0.70f, 1.25f))
            .apply()
    }

    fun getThemeMode(): String {
        return prefs.getString(KEY_THEME_MODE, "light") ?: "light" // Default to clean Light mode matching user's favorite screenshot
    }

    fun setThemeMode(mode: String) {
        prefs.edit()
            .putString(KEY_THEME_MODE, mode)
            .apply()
    }

    fun getTimelineMode(): String {
        return prefs.getString(KEY_TIMELINE_MODE, "normal") ?: "normal"
    }

    fun setTimelineMode(mode: String) {
        prefs.edit()
            .putString(KEY_TIMELINE_MODE, mode)
            .apply()
    }
}
