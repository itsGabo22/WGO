package com.wgo.navigator.voice

import com.wgo.navigator.Language
import com.wgo.navigator.voice.TranscriptGuard.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranscriptGuardTest {
    private val prompt = TranscriptionClient.promptFor(Language.ES)
    private fun seg(nsp: Double = 0.01, alp: Double = -0.2, cr: Double = 0.8, start: Double = 0.0, end: Double = 2.0) =
        Segment(start, end, nsp, alp, cr)

    @Test fun realRequestsPass() {
        for (text in listOf(
            "Llévame a la cooperativa.", "Ir a la plaza.", "Navegar a la terminal.", "Quiero ir a Unicentro.",
            "Plaza de Nariño", "Unicentro", "Take me to the stadium", "Gracias, llévame a la plaza"
        )) {
            assertNull(text, TranscriptGuard.reject(text, listOf(seg()), prompt))
        }
    }

    @Test fun confidenceNumbersRejectSilenceAndGarble() {
        assertEquals("no_speech", TranscriptGuard.reject("Hola", listOf(seg(nsp = 0.8)), prompt))
        assertEquals("low_logprob", TranscriptGuard.reject("Hola", listOf(seg(alp = -1.4)), prompt))
        assertEquals("compression", TranscriptGuard.reject("Hola", listOf(seg(cr = 2.9)), prompt))
    }

    @Test fun noSpeechIsWeightedByDuration() {
        // A short silent tail does not reject a long clear phrase.
        val segments = listOf(seg(nsp = 0.0, end = 2.5), seg(nsp = 0.9, start = 2.5, end = 3.0))
        assertNull(TranscriptGuard.reject("Llévame a la terminal", segments, prompt))
    }

    @Test fun knownSilencePhrasesAndRepeatsAreRejected() {
        for (text in listOf("Gracias.", "¡Gracias por ver!", "Suscríbete", "Thank you.", "you", "Subtítulos por la comunidad de Amara.org")) {
            assertEquals(text, "silence_phrase", TranscriptGuard.reject(text, listOf(seg()), prompt))
        }
        assertEquals("repeated", TranscriptGuard.reject("la la la la la", listOf(seg()), prompt))
        assertEquals("empty", TranscriptGuard.reject(" ... ", listOf(seg()), prompt))
    }

    @Test fun promptEchoIsRejected() {
        assertEquals("prompt_echo", TranscriptGuard.reject("Unicentro, Plaza de Nariño, Terminal de Transportes, Bomboná.", emptyList(), prompt))
        assertEquals("prompt_echo", TranscriptGuard.reject("Pasto, Nariño.", emptyList(), prompt))
        assertNull(TranscriptGuard.reject("Plaza de Nariño", emptyList(), prompt))
    }

    @Test fun normalizeDropsAccentsAndPunctuation() {
        assertEquals("subtitulos amara org", TranscriptGuard.normalize("¡Subtítulos!  Amara.org"))
        assertEquals("narino", TranscriptGuard.normalize("Nariño"))
    }
}
