package com.rork.cipher.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import kotlin.math.log10

/** A finished recording, still in the clear, on its way to being sealed. */
class Recording(
    val bytes: ByteArray,
    val durationMs: Long,
    val levels: List<Int>
)

/**
 * Captures a voice note from the microphone.
 *
 * The encoder needs a seekable file to write its container, so the raw audio
 * exists on disk for exactly as long as the finger is held down: the file is
 * read into memory and deleted the moment recording stops, before anything is
 * encrypted or sent. A cancelled recording is deleted without ever being read.
 */
class VoiceRecorder(context: Context) {

    private val app = context.applicationContext
    private val dir = File(app.cacheDir, "capture").apply { mkdirs() }

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private val samples = mutableListOf<Int>()

    val isRecording: Boolean get() = recorder != null

    val elapsedMs: Long
        get() = if (startedAt == 0L) 0L else System.currentTimeMillis() - startedAt

    /** @return false when the microphone could not be opened. */
    fun start(): Boolean {
        if (recorder != null) return true
        val target = File(dir, "hold-${System.currentTimeMillis()}.m4a")
        val instance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(app)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return runCatching {
            instance.setAudioSource(MediaRecorder.AudioSource.MIC)
            instance.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            instance.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            instance.setAudioChannels(1)
            instance.setAudioSamplingRate(SAMPLE_RATE)
            instance.setAudioEncodingBitRate(BIT_RATE)
            instance.setOutputFile(target.absolutePath)
            instance.prepare()
            instance.start()
            samples.clear()
            recorder = instance
            file = target
            startedAt = System.currentTimeMillis()
            true
        }.getOrElse {
            Log.w(TAG, "could not start recording: ${it.message}")
            runCatching { instance.release() }
            runCatching { target.delete() }
            false
        }
    }

    /**
     * Reads the current microphone peak for the live waveform.
     *
     * @return a level from 0 to 15, on a decibel scale so ordinary speech uses
     *   the whole height instead of hugging the floor.
     */
    fun sample(): Int {
        val active = recorder ?: return 0
        val peak = runCatching { active.maxAmplitude }.getOrDefault(0)
        val level = normalize(peak)
        if (samples.size < SAMPLE_CAP) samples += level
        return level
    }

    /** @return null when nothing usable was captured, e.g. a tap instead of a hold. */
    fun stop(): Recording? {
        val active = recorder ?: return null
        val target = file
        val duration = elapsedMs
        recorder = null
        file = null
        startedAt = 0L

        runCatching { active.stop() }.onFailure { Log.w(TAG, "stop: ${it.message}") }
        runCatching { active.release() }
        if (target == null) return null
        if (duration < MIN_MS) {
            runCatching { target.delete() }
            return null
        }
        val bytes = runCatching { target.readBytes() }.getOrNull()
        runCatching { target.delete() }
        if (bytes == null || bytes.isEmpty()) return null
        return Recording(bytes, duration, resample(samples, BARS))
    }

    /** Throws the recording away without reading a byte of it. */
    fun cancel() {
        val active = recorder ?: return
        val target = file
        recorder = null
        file = null
        startedAt = 0L
        samples.clear()
        runCatching { active.stop() }
        runCatching { active.release() }
        runCatching { target?.delete() }
    }

    private fun normalize(peak: Int): Int {
        if (peak <= 0) return 0
        val db = 20.0 * log10(peak.toDouble() / MAX_PEAK)
        val scaled = ((db + FLOOR_DB) / FLOOR_DB).coerceIn(0.0, 1.0)
        return (scaled * 15).toInt().coerceIn(0, 15)
    }

    private companion object {
        const val TAG = "VoiceRecorder"
        const val SAMPLE_RATE = 22_050
        const val BIT_RATE = 32_000
        const val MIN_MS = 700L
        const val SAMPLE_CAP = 2_000
        const val BARS = 56
        const val MAX_PEAK = 32_767.0
        const val FLOOR_DB = 46.0
    }
}

/** Squeezes however many samples were taken into a fixed number of bars. */
internal fun resample(samples: List<Int>, bars: Int): List<Int> {
    if (samples.isEmpty() || bars <= 0) return emptyList()
    if (samples.size <= bars) return samples
    val step = samples.size.toDouble() / bars
    return (0 until bars).map { bar ->
        val from = (bar * step).toInt()
        val to = ((bar + 1) * step).toInt().coerceAtMost(samples.size)
        val slice = samples.subList(from, to.coerceAtLeast(from + 1))
        slice.max()
    }
}
