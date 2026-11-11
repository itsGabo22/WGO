package com.wgo.navigator.ui.theme

import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.pow

/** The ambient palette matches docs/design/tokens.json (ambient-*) and keeps text readable on black. */
class AmbientPaletteTest {
    private fun luminance(c: Color): Double {
        fun ch(v: Float) = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (l1, l2) = luminance(a) to luminance(b)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun hex(c: Color) = String.format("#%06x", (c.value shr 32).toLong() and 0xFFFFFF)

    @Test
    fun matchesTokensInBothThemes() {
        val file = listOf(File("../docs/design/tokens.json"), File("docs/design/tokens.json")).first { it.isFile }
        val tokens = JSONObject(file.readText()).getJSONObject("color").getJSONArray("tokens")
        val expected = mapOf(
            "ambient-canvas" to AmbientPalette.canvas,
            "ambient-ink" to AmbientPalette.ink,
            "ambient-ink-muted" to AmbientPalette.inkMuted,
            "ambient-stroke" to AmbientPalette.stroke
        )
        val found = HashSet<String>()
        for (i in 0 until tokens.length()) {
            val t = tokens.getJSONObject(i)
            val color = expected[t.getString("name")] ?: continue
            val value = t.getJSONObject("value")
            assertEquals(t.getString("name"), hex(color), value.getString("light"))
            assertEquals(t.getString("name"), hex(color), value.getString("dark"))
            found += t.getString("name")
        }
        assertEquals(expected.keys, found)
        assertEquals(AmbientPalette, DaylightPalette.ambient)
        assertEquals(AmbientPalette, NightPalette.ambient)
    }

    @Test
    fun textIsReadableAndNotPureWhite() {
        assertTrue(contrast(AmbientPalette.ink, AmbientPalette.canvas) >= 4.5)
        assertTrue(contrast(AmbientPalette.inkMuted, AmbientPalette.canvas) >= 4.5)
        assertTrue(contrast(AmbientPalette.stroke, AmbientPalette.canvas) >= 3.0)
        assertTrue(luminance(AmbientPalette.ink) < 0.6) // a low-intensity grey, never #ffffff
        assertEquals(0.0, luminance(AmbientPalette.canvas), 0.0)
    }
}
