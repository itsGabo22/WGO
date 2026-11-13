package com.wgo.navigator.voice

import com.wgo.navigator.Language
import com.wgo.navigator.core.speechTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceLevelTest {
    @Test fun levelIsNormalisedAndClamped() {
        val s = LevelSmoother(attack = 1f, release = 1f)
        assertEquals(0f, s.push(-10f), 1e-6f)
        assertEquals(1f, s.push(30f), 1e-6f)
        assertEquals(0.5f, s.push(4f), 1e-6f)
        assertEquals(0f, s.push(Float.NaN), 1e-6f)
    }

    @Test fun attackIsFasterThanRelease() {
        val s = LevelSmoother()
        val up = s.push(10f)
        assertTrue("attack should jump most of the way", up >= 0.5f)
        val down = s.push(-2f)
        assertTrue("release should fall slowly", up - down < up * 0.2f)
    }

    @Test fun speechTags() {
        assertEquals("es-CO", Language.ES.speechTag)
        assertEquals("en-US", Language.EN.speechTag)
    }
}
