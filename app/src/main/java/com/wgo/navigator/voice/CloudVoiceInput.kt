package com.wgo.navigator.voice

import com.wgo.navigator.Language
import com.wgo.navigator.audio.AudioCapture
import com.wgo.navigator.core.VoiceEvent
import com.wgo.navigator.core.VoiceInput
import com.wgo.navigator.core.WgoFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Turns one in-memory WAV into text (the cloud transcription client; faked in tests). */
fun interface Transcriber {
    suspend fun transcribe(wav: ByteArray, language: Language): Transcription

    /** Opens DNS + TLS to the service while the user speaks (no key, no data); the answer is discarded. */
    suspend fun prewarm() = Unit
}

sealed interface Transcription {
    data class Text(val text: String) : Transcription

    /** No usable text; [failure] says why (offline, timeout, rate limited = [WgoFailure.Server] 429, rejected...). */
    data class Failed(val failure: WgoFailure, val reason: String) : Transcription
}

/**
 * The cloud rung of the voice ladder (P9): the app records the utterance itself with [AudioCapture] and hands the clip
 * to a [Transcriber]. [VoiceEvent.Ready] is sent at once (the screen says "speak now" at the press, while the recorder
 * opens on a background thread), then [VoiceEvent.Level] while recording, [VoiceEvent.Transcribing] once speech
 * ended, and [VoiceEvent.Final] or [VoiceEvent.Failed]: [WgoFailure.NoSpeech] for silence (nothing is sent), or the
 * transcriber's / recorder's failure. The WAV is zeroed after the upload.
 */
class CloudVoiceInput(
    private val capture: AudioCapture,
    private val transcriber: Transcriber,
    /** Where the connection prewarm runs: on its own, so it never delays the session's end. */
    private val prewarmScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : VoiceInput {
    /** Manual stop: a new press on the orb ends the capture (what was heard is still transcribed). */
    fun requestStop() = capture.requestStop()

    /** Blocks until the last capture released the microphone (before the fallback recognizer opens it). */
    fun awaitMicReleased() = capture.awaitReleased()

    companion object {
        /** Clips up to this long that the guard rejects as silence end with "No te oí" (P9 live session). */
        const val SHORT_CLIP_MS = 1_500L
        private val SILENCE_REASONS = setOf("guard_no_speech", "guard_silence_phrase", "guard_prompt_echo", "guard_empty")

        fun isShortSilence(reason: String, clipMs: Long): Boolean = clipMs <= SHORT_CLIP_MS && reason in SILENCE_REASONS
    }

    /** Nothing to do: cancelling the collector releases the microphone (AudioCapture's finally). */
    override fun cancel() = Unit

    override fun listen(language: Language): Flow<VoiceEvent> = channelFlow {
        send(VoiceEvent.Ready)
        // P9 PART 5: the first HTTPS request of a process took 2.3 s on the watch (later ones 0.2-0.4 s): warm the
        // connection up once speech is confirmed (a silent press makes no request at all). It runs on its own scope:
        // the upload never waits for it.
        val result = capture.record(
            onLevel = { trySend(VoiceEvent.Level(it)) },
            onSpeechStart = { prewarmScope.launch { transcriber.prewarm() } }
        )
        when (result) {
            AudioCapture.Result.NoSpeech -> {
                VoiceLog.event("outcome failed=NoSpeech network=false")
                send(VoiceEvent.Failed(WgoFailure.NoSpeech))
            }
            is AudioCapture.Result.Failed -> {
                VoiceLog.event("outcome failed=NotStarted reason=${result.reason}")
                send(VoiceEvent.Failed(WgoFailure.NotStarted))
            }
            is AudioCapture.Result.Speech -> {
                send(VoiceEvent.Transcribing)
                val text = try {
                    transcriber.transcribe(result.wav, language)
                } finally {
                    result.wav.fill(0)
                }
                when (text) {
                    is Transcription.Text -> {
                        VoiceLog.event("outcome final len=${text.text.length} rung=cloud")
                        send(VoiceEvent.Final(text.text))
                    }
                    is Transcription.Failed -> {
                        if (isShortSilence(text.reason, result.clipMs)) {
                            // A noise burst Whisper turned into "Gracias.": nothing was said (no fallback rung).
                            VoiceLog.event("outcome failed=NoSpeech reason=${text.reason} clip_ms=${result.clipMs} rung=cloud")
                            send(VoiceEvent.Failed(WgoFailure.NoSpeech))
                        } else {
                            VoiceLog.event("outcome failed=${text.failure} reason=${text.reason} rung=cloud")
                            send(VoiceEvent.Failed(text.failure))
                        }
                    }
                }
            }
        }
    }
}
