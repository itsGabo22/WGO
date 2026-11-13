package com.wgo.navigator.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.wgo.navigator.metrics.WgoMetrics
import com.wgo.navigator.voice.LevelSmoother
import com.wgo.navigator.voice.VoiceLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** A microphone that delivers 16 kHz mono PCM16 (the platform recorder, faked in tests). */
interface PcmSource {
    /** Opens and starts the microphone; false when it could not (it is then released by [release]). */
    fun start(): Boolean

    /** Blocking read of up to [length] samples; a negative value is an error. */
    fun read(buffer: ShortArray, length: Int): Int

    /** Stops and releases the microphone. Safe to call more than once. */
    fun release()
}

/** [PcmSource] over `AudioRecord` with the `VOICE_RECOGNITION` source (tuned for speech recognition, no AGC). */
class AndroidPcmSource(private val context: Context) : PcmSource {
    private var record: AudioRecord? = null

    override fun start(): Boolean {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return false
        val r = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 4, BUFFER_BYTES)
            )
        } catch (e: IllegalArgumentException) {
            return false
        } catch (e: SecurityException) {
            return false
        }
        record = r
        if (r.state != AudioRecord.STATE_INITIALIZED) return false
        try {
            r.startRecording()
        } catch (e: IllegalStateException) {
            return false
        }
        return r.recordingState == AudioRecord.RECORDSTATE_RECORDING
    }

    override fun read(buffer: ShortArray, length: Int): Int = record?.read(buffer, 0, length) ?: -1

    override fun release() {
        val r = record ?: return
        record = null
        try {
            if (r.recordingState == AudioRecord.RECORDSTATE_RECORDING) r.stop()
        } catch (e: IllegalStateException) {
            // Already stopped.
        }
        r.release()
    }

    companion object {
        const val SAMPLE_RATE = 16_000

        /** 500 ms of headroom, so a garbage-collection pause never overruns the recorder. */
        private const val BUFFER_BYTES = SAMPLE_RATE
    }
}

/**
 * Records one utterance (docs/design/WGO_DESIGN_SYSTEM.md, "Instant-capture voice input"): opens the microphone,
 * feeds 20 ms frames to a [SpeechEndpointer] and returns the clip to send as an in-memory WAV. The microphone is
 * released on EVERY exit (end of speech, silence, error, cancellation) and the PCM buffer is zeroed before returning.
 * The release itself runs on its own short-lived thread (`AudioRecord.stop` blocked 1.1 s on the Galaxy Watch5 Pro and
 * must not delay the upload); [awaitReleased] waits for it before anything else opens the microphone.
 * At most [SpeechEndpointer.Config.maxCaptureMs] of audio is kept: the whole capture fits in memory, so the 500 ms
 * pre-roll is simply the audio before the detected start.
 */
class AudioCapture(
    private val openSource: () -> PcmSource,
    private val config: SpeechEndpointer.Config = SpeechEndpointer.Config(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    /**
     * P10: the compressed upload (FLAC), fed while recording from the speech start with a [ENCODE_LAG_MS] lag so
     * nothing past the clip's end is encoded; null or any failure: the WAV of the same clip.
     */
    private val newEncoder: (() -> StreamEncoder)? = null
) {
    sealed interface Result {
        /** Speech was heard; [audio] is the clip, FLAC or WAV (the caller zeroes it after the upload). */
        class Speech(val audio: ByteArray, val clipMs: Long, val captureMs: Long, val reason: SpeechEndpointer.EndReason) : Result
        /** No speech within the no-speech window (or a manual stop before any): nothing is sent. */
        data object NoSpeech : Result
        /** The microphone could not be opened or read. */
        data class Failed(val reason: String) : Result
    }

    @Volatile
    private var stopRequested = false

    @Volatile
    private var releasing: Thread? = null

    /** Waits until the last capture's microphone is released (at most [timeoutMs]); returns at once when it is. */
    fun awaitReleased(timeoutMs: Long = RELEASE_WAIT_MS) {
        releasing?.join(timeoutMs)
    }

    /** A new press on the orb: the running capture ends at the next frame. */
    fun requestStop() {
        stopRequested = true
    }

    suspend fun record(onLevel: (Float) -> Unit = {}, onSpeechStart: () -> Unit = {}): Result = withContext(dispatcher) {
        stopRequested = false
        val endpointer = SpeechEndpointer(config)
        val frame = config.frameSamples
        val pcm = ShortArray((config.samples(config.maxCaptureMs) + frame).toInt())
        val buf = ShortArray(frame)
        val smoother = LevelSmoother(minDb = 0f, maxDb = 1f, attack = 0.6f, release = 0.2f)
        var stored = 0
        var encoder: StreamEncoder? = null
        var encodeFrom = 0
        var encoded = 0
        val lag = config.samples(ENCODE_LAG_MS).toInt()
        val source = openSource()
        WgoMetrics.inc(WgoMetrics.Counter.AUDIO_CAPTURE)
        VoiceLog.event("capture_open")
        try {
            if (!source.start()) {
                VoiceLog.event("capture_failed reason=start")
                return@withContext Result.Failed("start")
            }
            VoiceLog.event("audio_started")
            var firstBuffer = true
            var firstAudio = true
            while (true) {
                ensureActive()
                if (stopRequested) {
                    endpointer.stop()
                    break
                }
                val n = source.read(buf, frame)
                if (n < 0) {
                    VoiceLog.event("capture_failed reason=read code=$n")
                    return@withContext Result.Failed("read")
                }
                if (n == 0) continue
                if (firstBuffer) {
                    firstBuffer = false
                    VoiceLog.event("first_buffer samples=$n")
                }
                val room = minOf(n, pcm.size - stored)
                if (room > 0) System.arraycopy(buf, 0, pcm, stored, room)
                stored += room
                val step = endpointer.push(buf, n)
                if (firstAudio && endpointer.firstAudioSample >= 0) {
                    firstAudio = false
                    VoiceLog.event("first_audio audio_ms=${endpointer.msOf(endpointer.firstAudioSample)}")
                }
                onLevel(smoother.push(endpointer.level01()))
                when (step) {
                    is SpeechEndpointer.Step.SpeechStarted -> {
                        VoiceLog.event(
                            "speech_start audio_ms=${endpointer.msOf(step.startSample)} floor_db=${endpointer.floorDb.toInt()} " +
                                "threshold_db=${endpointer.thresholdDb.toInt()}"
                        )
                        onSpeechStart()
                        val e = newEncoder?.invoke()
                        if (e != null && e.start()) {
                            encoder = e
                            encodeFrom = maxOf(maxOf(endpointer.firstAudioSample, 0L), step.startSample - config.samples(config.preRollMs)).toInt()
                            encoded = encodeFrom
                        } else if (e != null) {
                            e.release()
                            VoiceLog.event("encode_fallback reason=start")
                        }
                    }
                    is SpeechEndpointer.Step.Ended -> break
                    SpeechEndpointer.Step.Continue -> Unit
                }
                // Encode what is surely inside the clip (the lag covers the silence that ends speech).
                val e = encoder
                if (e != null && stored - lag > encoded) {
                    if (e.feed(pcm, encoded, stored - lag)) encoded = stored - lag else {
                        e.release()
                        encoder = null
                        VoiceLog.event("encode_fallback reason=feed")
                    }
                }
            }
            val reason = endpointer.ended ?: SpeechEndpointer.EndReason.Manual
            val captureMs = endpointer.msOf(endpointer.totalSamples)
            val clip = endpointer.clip()
            VoiceLog.event(
                "speech_end reason=$reason capture_ms=$captureMs speech=${endpointer.hasSpeech} " +
                    "last_speech_ms=${if (endpointer.lastSpeechSample < 0) -1 else endpointer.msOf(endpointer.lastSpeechSample)}"
            )
            if (clip == null) return@withContext Result.NoSpeech
            val from = clip.first.toInt().coerceAtMost(stored)
            val to = (clip.last + 1).toInt().coerceAtMost(stored)
            val clipMs = endpointer.msOf((to - from).toLong())
            val wavBytes = WavEncoder.HEADER_BYTES + (to - from) * 2
            val compressed = encoder?.let { e -> finishEncoding(e, pcm, encodeFrom, encoded, from, to, wavBytes) }
            val audio = compressed ?: WavEncoder.encode(pcm, from, to, config.sampleRate)
            VoiceLog.event("capture_done clip_ms=$clipMs bytes=${audio.size} fmt=${if (compressed != null) "flac" else "wav"}")
            Result.Speech(audio, clipMs, captureMs, reason)
        } finally {
            encoder?.release()
            pcm.fill(0)
            buf.fill(0)
            releaseInBackground(source)
        }
    }

    /** Feeds the rest of the clip and drains the encoder; null (and a log line) when it cannot give the stream. */
    private fun finishEncoding(e: StreamEncoder, pcm: ShortArray, encodeFrom: Int, encoded: Int, from: Int, to: Int, wavBytes: Int): ByteArray? {
        if (encodeFrom != from) {
            VoiceLog.event("encode_fallback reason=range")
            return null
        }
        val t0 = System.nanoTime()
        if (encoded < to && !e.feed(pcm, encoded, to)) {
            VoiceLog.event("encode_fallback reason=feed")
            return null
        }
        val bytes = e.finish()
        val drainMs = (System.nanoTime() - t0) / 1_000_000
        if (bytes == null) {
            VoiceLog.event("encode_fallback reason=finish drain_ms=$drainMs")
            return null
        }
        VoiceLog.event("encode_done fmt=flac wav_bytes=$wavBytes bytes=${bytes.size} drain_ms=$drainMs")
        return bytes
    }

    private fun releaseInBackground(source: PcmSource) {
        val t = Thread({
            try {
                source.release()
            } finally {
                VoiceLog.event("capture_released")
            }
        }, "wgo-mic-release")
        releasing = t
        t.start()
    }

    private companion object {
        const val RELEASE_WAIT_MS = 2_000L

        /** The end of speech is found 900 ms after the last loud frame and the clip keeps 300 ms of it: 600 ms. */
        const val ENCODE_LAG_MS = 600
    }
}
