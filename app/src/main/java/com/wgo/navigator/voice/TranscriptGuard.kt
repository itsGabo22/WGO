package com.wgo.navigator.voice

import java.text.Normalizer

/**
 * Whisper invents text for silence and noise (P9 STEP 0 on the live account: silence with the landmark prompt came back
 * as the prompt itself, without a prompt as "Gracias."). [reject] returns why a transcription must be treated as
 * "heard nothing", or null when it is usable (docs/design/WGO_DESIGN_SYSTEM.md, "Hallucination guard").
 */
object TranscriptGuard {
    /** One verbose_json segment: its share of the audio and Whisper's own confidence numbers. */
    data class Segment(
        val start: Double,
        val end: Double,
        val noSpeechProb: Double,
        val avgLogprob: Double,
        val compressionRatio: Double
    )

    const val MAX_NO_SPEECH = 0.6
    const val MIN_AVG_LOGPROB = -1.0
    const val MAX_COMPRESSION = 2.4

    /** Phrases Whisper produces for silence (normalised: lowercase, no accents or punctuation). */
    private val SILENCE_PHRASES = setOf(
        "gracias", "muchas gracias", "gracias por ver", "gracias por ver el video", "gracias por vernos",
        "suscribete", "thank you", "thank you very much", "thanks for watching", "thank you for watching", "you", "bye"
    )
    private val SILENCE_PREFIXES = listOf("subtitulos", "subtitulado", "subtitles by", "amara org")

    private val GLUE = setOf("de", "del", "la", "el", "y", "a", "en", "los", "las")

    fun reject(text: String, segments: List<Segment>, prompt: String?): String? {
        val norm = normalize(text)
        if (norm.isEmpty()) return "empty"
        if (segments.isNotEmpty()) {
            val total = segments.sumOf { (it.end - it.start).coerceAtLeast(0.0) }
            fun weighted(f: (Segment) -> Double): Double =
                if (total <= 0.0) segments.map(f).average() else segments.sumOf { f(it) * (it.end - it.start).coerceAtLeast(0.0) } / total
            if (weighted { it.noSpeechProb } > MAX_NO_SPEECH) return "no_speech"
            if (weighted { it.avgLogprob } < MIN_AVG_LOGPROB) return "low_logprob"
            if (segments.any { it.compressionRatio > MAX_COMPRESSION }) return "compression"
        }
        if (norm in SILENCE_PHRASES || SILENCE_PREFIXES.any { norm.startsWith(it) } || "amara org" in norm) return "silence_phrase"
        val words = norm.split(' ')
        if (words.size >= 4) {
            val top = words.groupingBy { it }.eachCount().maxOf { it.value }
            if (top >= 4 && top >= words.size * 0.6) return "repeated"
        }
        if (prompt != null && echoesPrompt(text, words, prompt)) return "prompt_echo"
        return null
    }

    /**
     * The prompt's own list read back: every word comes from the prompt and it lists two or more items (commas) or runs
     * six words or more. One landmark said alone ("Unicentro", "Plaza de Nariño") is a real request and passes.
     */
    private fun echoesPrompt(raw: String, words: List<String>, prompt: String): Boolean {
        val vocabulary = normalize(prompt).split(' ').toSet() + GLUE
        if (!words.all { it in vocabulary }) return false
        val items = raw.split(',', ';').count { normalize(it).isNotEmpty() }
        return items >= 2 || words.size >= 6
    }

    /** Lowercase, accents removed, anything but letters and digits turned into single spaces. */
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
}
