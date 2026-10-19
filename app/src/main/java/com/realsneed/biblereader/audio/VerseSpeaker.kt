package com.realsneed.biblereader.audio

/**
 * The speech backend.
 *
 * Deliberately an interface with a tiny surface (prepare, speak one chunk,
 * stop, release) so a different engine can be dropped in later without touching
 * the player, the service or the UI.
 */
interface VerseSpeaker {

    /** Initialises the engine. [onReady] reports success; may be called back later. */
    fun prepare(onReady: (Boolean) -> Unit)

    /** Speaks [text], replacing anything already queued, then calls [onDone]. */
    fun speak(text: String, onDone: () -> Unit)

    /** Stops immediately and drops any pending completion callback. */
    fun stop()

    /** Re-creates the engine so changed preferences (engine/voice/rate) apply. */
    fun reconfigure()

    fun release()
}
