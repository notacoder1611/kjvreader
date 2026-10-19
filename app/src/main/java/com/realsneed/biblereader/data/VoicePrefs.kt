package com.realsneed.biblereader.data

import android.content.Context

/** Persisted reading-voice choice. */
class VoicePrefs(context: Context) {

    private val prefs = context.getSharedPreferences("voice", Context.MODE_PRIVATE)

    /** TTS engine package name, or null for the system default. */
    var engine: String?
        get() = prefs.getString(KEY_ENGINE, null)
        set(value) = prefs.edit().putString(KEY_ENGINE, value).apply()

    /** Voice name as reported by the engine, or null to use the language default. */
    var voiceName: String?
        get() = prefs.getString(KEY_VOICE, null)
        set(value) = prefs.edit().putString(KEY_VOICE, value).apply()

    var speechRate: Float
        get() = prefs.getFloat(KEY_RATE, 1f)
        set(value) = prefs.edit().putFloat(KEY_RATE, value).apply()

    var pitch: Float
        get() = prefs.getFloat(KEY_PITCH, 1f)
        set(value) = prefs.edit().putFloat(KEY_PITCH, value).apply()

    private companion object {
        const val KEY_ENGINE = "engine"
        const val KEY_VOICE = "voice"
        const val KEY_RATE = "rate"
        const val KEY_PITCH = "pitch"
    }
}
