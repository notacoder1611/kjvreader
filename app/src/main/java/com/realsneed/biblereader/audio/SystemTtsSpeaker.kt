package com.realsneed.biblereader.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.realsneed.biblereader.data.VoicePrefs
import java.util.Locale

/** The device's own text-to-speech engine, driven verse by verse. */
class SystemTtsSpeaker(
    private val context: Context,
    private val prefs: VoicePrefs
) : VerseSpeaker {

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingReady: ((Boolean) -> Unit)? = null
    private val doneCallbacks = HashMap<String, () -> Unit>()
    private var counter = 0

    override fun prepare(onReady: (Boolean) -> Unit) {
        if (ready) {
            onReady(true)
            return
        }
        pendingReady = onReady
        val engine = prefs.engine?.takeIf { wanted ->
            VoiceCatalog.engines(context).any { it.packageName == wanted }
        }
        tts = TextToSpeech(context, { status -> main.post { onInitialised(status == TextToSpeech.SUCCESS) } }, engine)
    }

    private fun onInitialised(success: Boolean) {
        ready = success
        if (ready) applyPreferences()
        val callback = pendingReady
        pendingReady = null
        callback?.invoke(ready)
    }

    private fun applyPreferences() {
        val engine = tts ?: return
        engine.setSpeechRate(prefs.speechRate)
        engine.setPitch(prefs.pitch)

        val wanted = prefs.voiceName
        val voice = wanted?.let { name -> engine.voices?.firstOrNull { it.name == name } }
        if (voice != null) {
            engine.voice = voice
        } else {
            engine.setLanguage(Locale.US)
        }

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = complete(utteranceId)
            @Deprecated("Deprecated in the platform, still required.")
            override fun onError(utteranceId: String?) = complete(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = complete(utteranceId)
        })
    }

    private fun complete(utteranceId: String?) {
        main.post { doneCallbacks.remove(utteranceId)?.invoke() }
    }

    override fun speak(text: String, onDone: () -> Unit) {
        val engine = tts
        if (!ready || engine == null) {
            onDone()
            return
        }
        val id = "verse-" + counter++
        doneCallbacks[id] = onDone
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    override fun stop() {
        tts?.stop()
        doneCallbacks.clear()
    }

    override fun reconfigure() {
        release()
    }

    override fun release() {
        tts?.shutdown()
        tts = null
        ready = false
        doneCallbacks.clear()
    }
}
