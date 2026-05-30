package com.example.data.repo

import android.content.Context
import android.content.SharedPreferences

class HikePreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("hike_prefs", Context.MODE_PRIVATE)

    val sharedPrefs: SharedPreferences get() = prefs

    var hasSignalRestoredPoint: Boolean
        get() = prefs.getBoolean("has_signal_restored_point", false)
        set(value) = prefs.edit().putBoolean("has_signal_restored_point", value).apply()

    var signalRestoredLat: Float
        get() = prefs.getFloat("signal_restored_lat", 0.0f)
        set(value) = prefs.edit().putFloat("signal_restored_lat", value).apply()

    var signalRestoredLng: Float
        get() = prefs.getFloat("signal_restored_lng", 0.0f)
        set(value) = prefs.edit().putFloat("signal_restored_lng", value).apply()

    var safetyEmail: String
        get() = safetyEmails.joinToString(", ")
        set(value) {
            val list = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            safetyEmails = list
        }

    var safetyEmails: List<String>
        get() {
            val raw = prefs.getString("safety_emails", "") ?: ""
            if (raw.isEmpty()) {
                val oldSolo = prefs.getString("safety_email", "") ?: ""
                return if (oldSolo.isNotEmpty()) listOf(oldSolo) else emptyList()
            }
            return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        set(value) {
            val joined = value.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")
            prefs.edit().putString("safety_emails", joined).apply()
            
            // Keep old single field in sync as fallback
            val oldSolo = if (value.isNotEmpty()) value.first() else ""
            prefs.edit().putString("safety_email", oldSolo).apply()
        }

    var useAutoSmtp: Boolean
        get() = prefs.getBoolean("use_auto_smtp", false)
        set(value) = prefs.edit().putBoolean("use_auto_smtp", value).apply()

    var lastSyncedTimestamp: Long
        get() = prefs.getLong("last_synced_timestamp", 0L)
        set(value) = prefs.edit().putLong("last_synced_timestamp", value).apply()

    var smtpHost: String
        get() = prefs.getString("smtp_host", "smtp.gmail.com") ?: "smtp.gmail.com"
        set(value) = prefs.edit().putString("smtp_host", value).apply()

    var smtpPort: Int
        get() = prefs.getInt("smtp_port", 465)
        set(value) = prefs.edit().putInt("smtp_port", value).apply()

    var smtpUsername: String
        get() = prefs.getString("smtp_username", "") ?: ""
        set(value) = prefs.edit().putString("smtp_username", value).apply()

    var smtpPassword: String
        get() = prefs.getString("smtp_password", "") ?: ""
        set(value) = prefs.edit().putString("smtp_password", value).apply()

    var smtpSender: String
        get() = prefs.getString("smtp_sender", "") ?: ""
        set(value) = prefs.edit().putString("smtp_sender", value).apply()
}
