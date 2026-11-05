package com.wgo.navigator.ui.map

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class WgoTileFilterTest {
    private val paper = Color(0.8f, 0.6f, 0.4f)
    private val ink = Color(0.1f, 0.2f, 0.3f)

    /** Applies the 4x5 matrix to a gray tile of luminance [lum] and returns the 0..1 channel [row]. */
    private fun apply(m: FloatArray, lum: Float, row: Int): Float {
        val v = m[row * 5] * lum * 255f + m[row * 5 + 1] * lum * 255f + m[row * 5 + 2] * lum * 255f + m[row * 5 + 4]
        return (v / 255f).coerceIn(0f, 1f)
    }

    @Test fun landLuminanceMapsExactlyToPaper() {
        listOf(0.25f, 0.6f, 1f).forEach { contrast ->
            val m = WgoTileFilter.duotoneArray(paper, ink, contrast)
            assertEquals(paper.red, apply(m, 0.93f, 0), 0.01f)
            assertEquals(paper.green, apply(m, 0.93f, 1), 0.01f)
            assertEquals(paper.blue, apply(m, 0.93f, 2), 0.01f)
        }
    }

    @Test fun fullContrastSendsBlackToInk() {
        val m = WgoTileFilter.duotoneArray(paper, ink, 1f)
        // luminance 0 -> t = 1 - 0.93 = 0.07: almost fully ink
        assertEquals(ink.red + (paper.red - ink.red) * 0.07f, apply(m, 0f, 0), 0.01f)
    }

    @Test fun lowerContrastKeepsDarkFeaturesCloserToPaper() {
        val quiet = WgoTileFilter.duotoneArray(paper, ink, 0.3f)
        val loud = WgoTileFilter.duotoneArray(paper, ink, 0.9f)
        val dark = 0.2f
        assertEquals(true, apply(quiet, dark, 0) > apply(loud, dark, 0))
    }

    @Test fun alphaRowIsIdentity() {
        val m = WgoTileFilter.duotoneArray(paper, ink, 0.5f)
        assertEquals(listOf(0f, 0f, 0f, 1f, 0f), m.slice(15..19))
    }

    @Test fun matrixHasTwentyEntries() = assertEquals(20, WgoTileFilter.duotoneArray(paper, ink, 0.5f).size)
}
