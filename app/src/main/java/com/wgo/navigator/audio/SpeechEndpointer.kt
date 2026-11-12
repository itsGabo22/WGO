package com.wgo.navigator.audio

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Energy voice-activity detector for one utterance (docs/design/WGO_DESIGN_SYSTEM.md, "Instant-capture voice input").
 * Pure Kotlin, no framework and no clock: time is counted in samples, so the same PCM always gives the same result.
 *
 * Feed it consecutive 20 ms frames of 16 kHz mono PCM16 with [push]. Leading all-zero frames (the microphone ramp) are
 * skipped; the first 300 ms of real audio calibrate the noise floor (capped, so talking over the calibration still
 * leaves room); speech starts after 150 ms above floor + margin and ends after 900 ms below it. [clip] tells which
 * samples to send: from 500 ms before the start (pre-roll) to 300 ms after the last loud frame. A steady loud
 * background is followed with minimum statistics (the quietest frame of the last 1.5 s), so speech still ends over it.
 */
class SpeechEndpointer(private val config: Config = Config()) {
    data class Config(
        val sampleRate: Int = 16_000,
        val frameMs: Int = 20,
        val calibrationMs: Int = 300,
        val floorCapDb: Float = -55f,
        val marginDb: Float = 10f,
        val minThresholdDb: Float = -62f,
        val startMs: Int = 150,
        val preRollMs: Int = 500,
        val endSilenceMs: Int = 900,
        val maxCaptureMs: Int = 10_000,
        val noSpeechMs: Int = 6_000,
        val tailMs: Int = 300,
        val floorDown: Float = 0.2f,
        val floorUp: Float = 0.02f,
        /** Minimum statistics: a steady background louder than the floor by [steadyRiseDb] for [steadyWindowMs]. */
        val steadyWindowMs: Int = 1_500,
        val steadyRiseDb: Float = 6f,
        val steadyBelowDb: Float = 3f
    ) {
        val frameSamples: Int get() = sampleRate * frameMs / 1000
        fun samples(ms: Int): Long = sampleRate.toLong() * ms / 1000
    }

    enum class EndReason { SpeechEnded, MaxLength, NoSpeech, Manual }

    /** What one frame changed. */
    sealed interface Step {
        data object Continue : Step
        /** Speech was confirmed; [startSample] is where it began (before the pre-roll). */
        data class SpeechStarted(val startSample: Long) : Step
        data class Ended(val reason: EndReason) : Step
    }

    /** Samples seen so far (zero frames included), i.e. the capture length. */
    var totalSamples = 0L
        private set

    /** First sample of real (non-zero) audio, or -1. */
    var firstAudioSample = -1L
        private set

    var speechStartSample = -1L
        private set

    /** End (exclusive) of the last frame above the threshold during speech. */
    var lastSpeechSample = -1L
        private set

    var floorDb = Float.NaN
        private set

    /** The last frame's level in dBFS. */
    var levelDb = SILENCE_DB
        private set

    var ended: EndReason? = null
        private set

    val hasSpeech: Boolean get() = speechStartSample >= 0

    private var calibrationFrames = 0
    private var calibrationSum = 0.0
    private var runFrames = 0
    private var runStart = -1L
    private var gapUsed = false
    private var silenceSamples = 0L

    private val calibrationTarget = max(1, config.calibrationMs / config.frameMs)
    private val recent = FloatArray(max(1, config.steadyWindowMs / config.frameMs))
    private var recentCount = 0
    private val startFrames = (config.startMs + config.frameMs - 1) / config.frameMs

    /** The threshold now (NaN before calibration). */
    val thresholdDb: Float get() = if (floorDb.isNaN()) Float.NaN else max(floorDb + config.marginDb, config.minThresholdDb)

    fun push(frame: ShortArray, length: Int = frame.size): Step {
        if (ended != null) return Step.Ended(ended!!)
        val frameStart = totalSamples
        totalSamples += length
        var sum = 0.0
        var allZero = true
        for (i in 0 until length) {
            val v = frame[i].toInt()
            if (v != 0) allZero = false
            sum += v.toDouble() * v
        }
        val db = if (length == 0) SILENCE_DB else dbfs(sqrt(sum / length))
        levelDb = db
        var step: Step = Step.Continue

        when {
            firstAudioSample < 0 && allZero -> Unit // the microphone ramp: not audio yet
            calibrationFrames < calibrationTarget -> {
                if (firstAudioSample < 0) firstAudioSample = frameStart
                calibrationFrames++
                calibrationSum += db
                if (calibrationFrames == calibrationTarget) {
                    floorDb = min((calibrationSum / calibrationFrames).toFloat(), config.floorCapDb)
                }
            }
            !hasSpeech -> {
                if (db > thresholdDb) {
                    if (runFrames == 0) runStart = frameStart
                    runFrames++
                    if (runFrames >= startFrames) {
                        speechStartSample = runStart
                        lastSpeechSample = totalSamples
                        silenceSamples = 0
                        step = Step.SpeechStarted(runStart)
                    }
                } else if (runFrames > 0 && !gapUsed) {
                    gapUsed = true // one quiet frame inside the run is tolerated (a consonant, a breath)
                } else {
                    runFrames = 0
                    gapUsed = false
                    adaptFloor(db)
                }
            }
            else -> {
                if (db > thresholdDb) {
                    lastSpeechSample = totalSamples
                    silenceSamples = 0
                } else {
                    silenceSamples += length
                    if (silenceSamples >= config.samples(config.endSilenceMs)) step = end(EndReason.SpeechEnded)
                }
            }
        }

        if (!floorDb.isNaN() && firstAudioSample >= 0 && calibrationFrames == calibrationTarget) followSteadyBackground(db)

        if (ended == null) {
            if (!hasSpeech && totalSamples >= config.samples(config.noSpeechMs)) step = end(EndReason.NoSpeech)
            else if (totalSamples >= config.samples(config.maxCaptureMs)) step = end(if (hasSpeech) EndReason.MaxLength else EndReason.NoSpeech)
        }
        return step
    }

    /** Manual stop (a new press on the orb): the capture ends now. */
    fun stop(): EndReason = ended ?: end(EndReason.Manual).reason

    /** The samples to send, [start, end), or null when no speech was heard. */
    fun clip(): LongRange? {
        if (!hasSpeech) return null
        val from = max(max(firstAudioSample, 0L), speechStartSample - config.samples(config.preRollMs))
        val to = min(totalSamples, lastSpeechSample + config.samples(config.tailMs))
        return if (to > from) from until to else null
    }

    /** UI level 0..1 for the voice bars: 3..33 dB above the floor. */
    fun level01(): Float {
        val floor = if (floorDb.isNaN()) config.floorCapDb else floorDb
        return ((levelDb - floor - 3f) / 30f).coerceIn(0f, 1f)
    }

    fun msOf(samples: Long): Long = samples * 1000 / config.sampleRate

    private fun end(reason: EndReason): Step.Ended {
        ended = reason
        return Step.Ended(reason)
    }

    private fun adaptFloor(db: Float) {
        val k = if (db < floorDb) config.floorDown else config.floorUp
        floorDb += (db - floorDb) * k
    }

    /** Speech has quieter gaps between words; a steady background has none: when even the quietest recent frame is
     * well above the floor, the floor moves up to just below it. */
    private fun followSteadyBackground(db: Float) {
        recent[recentCount % recent.size] = db
        recentCount++
        if (recentCount < recent.size) return
        var quietest = recent[0]
        for (v in recent) if (v < quietest) quietest = v
        if (quietest > floorDb + config.steadyRiseDb) floorDb = quietest - config.steadyBelowDb
    }

    companion object {
        const val SILENCE_DB = -96f

        fun dbfs(rms: Double): Float = if (rms < 1.0) SILENCE_DB else (20.0 * log10(rms / 32768.0)).toFloat()
    }
}
