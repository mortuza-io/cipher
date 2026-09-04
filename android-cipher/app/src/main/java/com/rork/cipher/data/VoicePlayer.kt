package com.rork.cipher.data

import android.media.AudioAttributes
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the bubbles need to know about the one note that is currently open. */
data class VoicePlayback(
    val blob: String,
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean,
    val loading: Boolean = false
) {
    val progress: Float
        get() = if (durationMs <= 0L) 0f
        else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/**
 * Plays one voice note at a time, straight from memory.
 *
 * The decrypted audio is handed to the player as a byte array rather than a
 * file, so opening a voice note never writes plaintext to storage. Starting a
 * second note stops the first, which is the only sane behaviour in a thread
 * full of them.
 */
class VoicePlayer {

    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<VoicePlayback?>(null)
    val state: StateFlow<VoicePlayback?> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var ticker: Job? = null

    val openBlob: String? get() = _state.value?.blob

    /**
     * Every mutation lands on the main thread.
     *
     * The player is driven from taps, from the fetch that follows one, and from
     * the burn ticker that can delete a note mid-sentence — three different
     * threads reaching for one `MediaPlayer`. `Dispatchers.Main.immediate`
     * keeps a call that is already on the main thread synchronous, so this
     * costs nothing where it matters.
     */
    private fun onMain(block: () -> Unit) {
        scope.launch { block() }
    }

    /** Marks a note as being fetched, so its bubble can show progress. */
    fun loading(blob: String, durationMs: Long) = onMain {
        release()
        _state.value = VoicePlayback(
            blob = blob,
            positionMs = 0L,
            durationMs = durationMs,
            playing = false,
            loading = true
        )
    }

    fun play(blob: String, bytes: ByteArray, durationMs: Long) = onMain {
        release()
        val instance = MediaPlayer()
        val opened = runCatching {
            instance.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            instance.setDataSource(ByteSource(bytes))
            instance.prepare()
            instance.start()
            true
        }.getOrElse {
            Log.w(TAG, "could not play a voice note: ${it.message}")
            runCatching { instance.release() }
            false
        }
        if (!opened) {
            _state.value = null
            return@onMain
        }
        instance.setOnCompletionListener { finished() }
        player = instance
        val length = if (instance.duration > 0) instance.duration.toLong() else durationMs
        _state.value = VoicePlayback(blob, 0L, length, playing = true)
        startTicking()
    }

    fun pause() = onMain {
        val active = player ?: return@onMain
        runCatching { active.pause() }
        ticker?.cancel()
        _state.value = _state.value?.copy(playing = false)
    }

    fun resume() = onMain {
        val active = player ?: return@onMain
        runCatching { active.start() }
        _state.value = _state.value?.copy(playing = true)
        startTicking()
    }

    /** @param fraction where in the note to jump to, 0..1. */
    fun seek(fraction: Float) = onMain {
        val active = player ?: return@onMain
        val current = _state.value ?: return@onMain
        val target = (current.durationMs * fraction.coerceIn(0f, 1f)).toLong()
        runCatching { active.seekTo(target.toInt()) }
        _state.value = current.copy(positionMs = target)
    }

    fun stop() = onMain {
        release()
        _state.value = null
    }

    private fun finished() {
        release()
        _state.value = _state.value?.copy(positionMs = 0L, playing = false)
    }

    private fun startTicking() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                delay(TICK_MS)
                val active = player ?: return@launch
                val position = runCatching { active.currentPosition.toLong() }.getOrNull()
                    ?: return@launch
                _state.value = _state.value?.copy(positionMs = position)
            }
        }
    }

    private fun release() {
        ticker?.cancel()
        ticker = null
        val active = player ?: return
        player = null
        runCatching { active.stop() }
        runCatching { active.release() }
    }

    private companion object {
        const val TAG = "VoicePlayer"
        const val TICK_MS = 60L
    }
}

/** Feeds an in-memory buffer to [MediaPlayer] so no plaintext file is needed. */
private class ByteSource(private val bytes: ByteArray) : MediaDataSource() {

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= bytes.size) return -1
        val available = (bytes.size - position).toInt().coerceAtMost(size)
        if (available <= 0) return -1
        System.arraycopy(bytes, position.toInt(), buffer, offset, available)
        return available
    }

    override fun getSize(): Long = bytes.size.toLong()

    override fun close() = Unit
}
