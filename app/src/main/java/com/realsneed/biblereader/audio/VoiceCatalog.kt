package com.realsneed.biblereader.audio

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.realsneed.biblereader.R

/** One installed text-to-speech engine. */
data class TtsEngine(val packageName: String, val label: String)

/**
 * Reads what the device's text-to-speech engines actually offer.
 *
 * Android does not expose voice gender as such, but engine voice names encode
 * it (`..._x_sfg#female_1-local`, `eng-usa-m00`), so it is parsed out here and
 * used for the Male/Female filter.
 */
object VoiceCatalog {

    private val genderPattern = Regex("[#_](m|f)[0-9]")
    private val variantPattern = Regex("#?[a-z]*_(\\d+)")

    /**
     * Installed engines, found the documented way - TextToSpeech.getEngines()
     * is a hidden API, so we resolve the TTS service intent instead.
     */
    fun engines(context: Context): List<TtsEngine> {
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        val manager = context.packageManager
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            manager.queryIntentServices(intent, 0)
        }
        return resolved.mapNotNull { info ->
            val service = info.serviceInfo ?: return@mapNotNull null
            TtsEngine(service.packageName, service.loadLabel(manager).toString())
        }.distinctBy { it.packageName }
    }

    fun engineLabel(context: Context, enginePackage: String?): String = when {
        enginePackage == null -> context.getString(R.string.voice_engine_default)
        else -> engines(context).firstOrNull { it.packageName == enginePackage }?.label
            ?: enginePackage
    }

    /** "female" / "male" / null when the name gives nothing away. */
    fun genderOf(voice: Voice): String? {
        val name = voice.name.lowercase()
        if (name.contains("female")) return "female"
        if (name.contains("male")) return "male"
        val match = genderPattern.find(name) ?: return null
        return if (match.groupValues[1] == "m") "male" else "female"
    }

    private fun variantOf(voice: Voice): String? =
        variantPattern.find(voice.name.lowercase())?.groupValues?.get(1)

    fun title(voice: Voice): String {
        val gender = genderOf(voice)
        val variant = variantOf(voice)
        if (gender == null && variant == null) return voice.locale.displayName
        return buildString {
            append(voice.locale.displayName)
            append(" \u00b7 ")
            if (gender != null) append(gender)
            if (variant != null) {
                if (gender != null) append(' ')
                append(variant)
            }
        }
    }

    fun subtitle(context: Context, voice: Voice): String {
        val quality = if (voice.isNetworkConnectionRequired) {
            context.getString(R.string.voice_needs_network)
        } else {
            context.getString(R.string.voice_on_device)
        }
        val anonymous = genderOf(voice) == null && variantOf(voice) == null
        return if (anonymous) "$quality \u00b7 ${voice.name}" else quality
    }

    /** Network voices first (they are the natural-sounding ones), then quality. */
    val best = compareByDescending<Voice> { it.isNetworkConnectionRequired }
        .thenByDescending { it.quality }
        .thenBy { it.locale.displayName }
        .thenBy { it.name }
}
