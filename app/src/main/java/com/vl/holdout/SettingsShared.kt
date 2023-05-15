package com.vl.holdout

import android.content.Context

class SettingsShared(context: Context) {
    companion object {
        private const val PREFERENCES = "settings"
        private const val EXTRA_SOUND_ENABLED = "soundEnabled"
    }

    private val sharedPreferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    var isSoundEnabled: Boolean
        get() = sharedPreferences.getBoolean(EXTRA_SOUND_ENABLED, true)
        set(value) {
            if (!sharedPreferences.edit().putBoolean(EXTRA_SOUND_ENABLED, value).commit())
                throw RuntimeException("Couldn't save setting: isSoundEnabled")
        }
}