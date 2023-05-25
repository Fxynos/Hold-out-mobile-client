package com.vl.holdout

import android.content.Context

class SettingsShared(context: Context) {
    companion object {
        private const val PREFERENCES = "settings"
        private const val EXTRA_SOUND_ENABLED = "soundEnabled"
        private const val EXTRA_HOST = "host"
        private const val DEFAULT_HOST = "192.168.0.10"
    }

    private val sharedPreferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    var isSoundEnabled: Boolean
        get() = sharedPreferences.getBoolean(EXTRA_SOUND_ENABLED, true)
        set(value) {
            if (!sharedPreferences.edit().putBoolean(EXTRA_SOUND_ENABLED, value).commit())
                throw RuntimeException("Couldn't save setting: isSoundEnabled")
        }

    var host: String
        get() = sharedPreferences.getString(EXTRA_HOST, DEFAULT_HOST)!!
        set(value) {
            if (!sharedPreferences.edit().putString(EXTRA_HOST, value.takeIf(String::isNotBlank) ?: DEFAULT_HOST).commit())
                throw RuntimeException("Couldn't save setting: host")
        }
}