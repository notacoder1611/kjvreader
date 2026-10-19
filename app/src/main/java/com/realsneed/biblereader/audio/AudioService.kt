package com.realsneed.biblereader.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.realsneed.biblereader.R
import com.realsneed.biblereader.data.BibleMeta
import com.realsneed.biblereader.data.BibleRepository
import com.realsneed.biblereader.data.VoicePrefs
import com.realsneed.biblereader.ui.MainActivity

/**
 * Keeps the reading alive with the screen off.
 *
 * Owns the [AudioPlayer] and a media-playback foreground notification, so
 * playback survives the reader being backgrounded and can be paused or stopped
 * from the notification shade.
 */
class AudioService : Service() {

    inner class LocalBinder : Binder() {
        fun service(): AudioService = this@AudioService
    }

    private val binder = LocalBinder()
    private val main = Handler(Looper.getMainLooper())

    private lateinit var prefs: VoicePrefs
    private lateinit var player: AudioPlayer
    private lateinit var audioManager: AudioManager
    private var uiListener: ((AudioPlayer.State) -> Unit)? = null
    private var foregroundStarted = false
    private var focusRequest: AudioFocusRequest? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player.pause()
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        prefs = VoicePrefs(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createChannel()
        player = AudioPlayer(
            repository = BibleRepository.get(assets),
            // The device's own speech engine. [VerseSpeaker] is an interface, so a
            // different backend can be added later without touching the player or
            // this service.
            speaker = SystemTtsSpeaker(this, prefs),
            onChanged = { state -> onPlayerState(state) }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val book = BibleMeta.byAssetIndex(intent.getIntExtra(EXTRA_BOOK, -1))
                if (book == null) {
                    stopEverything()
                } else {
                    ensureForeground(player.currentState())
                    if (requestFocus()) {
                        player.start(
                            book,
                            intent.getIntExtra(EXTRA_CHAPTER, 1),
                            intent.getIntExtra(EXTRA_VERSE, 1)
                        )
                    } else {
                        stopEverything()
                    }
                }
            }
            ACTION_TOGGLE -> player.toggle()
            ACTION_NEXT -> player.next()
            ACTION_PREVIOUS -> player.previous()
            ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        player.release()
        abandonFocus()
        super.onDestroy()
    }

    // -------------------------------------------------------------- the player

    /** Exposed to the reader so it can follow along and drive the transport. */
    fun setListener(listener: ((AudioPlayer.State) -> Unit)?) {
        uiListener = listener
        listener?.let { player.currentState().let(it) }
    }

    fun toggle() = player.toggle()

    fun next() = player.next()

    fun previous() = player.previous()

    /** Stops the reader outright and takes the notification and bar away. */
    fun stopPlayback() {
        player.stop()
        stopEverything()
    }

    /** Auditions the currently selected voice. */
    fun previewVoice(sample: String) {
        player.preview(sample)
    }

    /** Re-creates the TTS engine so a changed voice or engine takes effect at once. */
    fun applyVoiceChange() {
        player.reconfigureSpeaker()
    }

    private fun onPlayerState(state: AudioPlayer.State) {
        if (state.active) {
            ensureForeground(state)
        } else {
            stopEverything()
            return
        }
        if (!state.playing) abandonFocus()
        uiListener?.invoke(state)
    }

    // ------------------------------------------------------------ notification

    private fun ensureForeground(state: AudioPlayer.State) {
        val manager = getSystemService(NotificationManager::class.java)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(state),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
        )
        foregroundStarted = true
        manager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: AudioPlayer.State): Notification {
        val reference = if (state.bookName != null) {
            getString(R.string.reference_title, state.bookName, state.chapter, state.verse)
        } else {
            getString(R.string.app_name)
        }
        val openReader = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_volume)
            .setContentTitle(reference)
            .setContentText(getString(R.string.app_name))
            .setContentIntent(openReader)
            .setOngoing(state.playing)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                if (state.playing) R.drawable.ic_pause else R.drawable.ic_play,
                getString(if (state.playing) R.string.pause_reading else R.string.start_reading),
                serviceIntent(ACTION_TOGGLE, 1)
            )
            .addAction(
                R.drawable.ic_stop,
                getString(R.string.stop_reading),
                serviceIntent(ACTION_STOP, 2)
            )
            .build()
    }

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, AudioService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.reading_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.setShowBadge(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    // ------------------------------------------------------------- audio focus

    private fun requestFocus(): Boolean {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = focusRequest ?: AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(focusListener)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
                .also { focusRequest = it }
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        return granted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    // -------------------------------------------------------------------- misc

    private fun stopEverything() {
        if (foregroundStarted) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        abandonFocus()
        uiListener?.invoke(player.currentState())
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.realsneed.biblereader.action.START"
        const val ACTION_TOGGLE = "com.realsneed.biblereader.action.TOGGLE"
        const val ACTION_NEXT = "com.realsneed.biblereader.action.NEXT"
        const val ACTION_PREVIOUS = "com.realsneed.biblereader.action.PREVIOUS"
        const val ACTION_STOP = "com.realsneed.biblereader.action.STOP"
        const val EXTRA_BOOK = "book"
        const val EXTRA_CHAPTER = "chapter"
        const val EXTRA_VERSE = "verse"

        private const val CHANNEL_ID = "reading"
        private const val NOTIFICATION_ID = 41

        fun startIntent(
            context: Context,
            bookAssetIndex: Int,
            chapter: Int,
            verse: Int
        ): Intent = Intent(context, AudioService::class.java)
            .setAction(ACTION_START)
            .putExtra(EXTRA_BOOK, bookAssetIndex)
            .putExtra(EXTRA_CHAPTER, chapter)
            .putExtra(EXTRA_VERSE, verse)
    }
}
