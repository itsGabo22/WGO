package com.wgo.navigator.audio

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** Deterministic synthetic 16 kHz mono PCM for the voice tests: zeros, noise and a voice-like tone. */
object SyntheticPcm {
    const val RATE = 16_000

    fun samples(ms: Int) = RATE * ms / 1000

    fun zeros(ms: Int) = ShortArray(samples(ms))

    /** Uniform noise of peak [amp] from a fixed-seed LCG (rms about amp / 1.73). */
    fun noise(ms: Int, amp: Int, seed: Long = 7L): ShortArray {
        var x = seed
        return ShortArray(samples(ms)) {
            x = (x * 6364136223846793005L + 1442695040888963407L)
            val u = ((x ushr 33).toDouble() / (1L shl 31).toDouble()) * 2.0 - 1.0
            (u * amp).roundToInt().toShort()
        }
    }

    /** A 220 Hz tone of peak [amp] (rms about amp / 1.41) standing in for speech. */
    fun tone(ms: Int, amp: Int, hz: Double = 220.0) = ShortArray(samples(ms)) { i ->
        (sin(2 * PI * hz * i / RATE) * amp).roundToInt().toShort()
    }

    /** Speech-like: 200 ms syllables at [amp] with 60 ms gaps 20 dB quieter (real speech always has such gaps). */
    fun speech(ms: Int, amp: Int): ShortArray {
        val out = ShortArray(samples(ms))
        val syllable = samples(200)
        val period = samples(260)
        for (i in out.indices) {
            val a = if (i % period < syllable) amp else amp / 10
            out[i] = (sin(2 * PI * 220.0 * i / RATE) * a).roundToInt().toShort()
        }
        return out
    }

    fun concat(vararg parts: ShortArray): ShortArray {
        val out = ShortArray(parts.sumOf { it.size })
        var p = 0
        for (part in parts) {
            part.copyInto(out, p)
            p += part.size
        }
        return out
    }

    /** Feeds [pcm] in 20 ms frames until the endpointer ends (or the audio does); returns every step. */
    fun run(endpointer: SpeechEndpointer, pcm: ShortArray): List<SpeechEndpointer.Step> {
        val frame = samples(20)
        val steps = mutableListOf<SpeechEndpointer.Step>()
        var i = 0
        while (i < pcm.size) {
            val n = minOf(frame, pcm.size - i)
            val step = endpointer.push(pcm.copyOfRange(i, i + n), n)
            steps += step
            if (step is SpeechEndpointer.Step.Ended) break
            i += n
        }
        return steps
    }
}
