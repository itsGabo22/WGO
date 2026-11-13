package com.wgo.navigator.ui.map

import com.wgo.navigator.places.IndexedPlace
import com.wgo.navigator.ui.screens.HudMode
import com.wgo.navigator.ui.screens.landmarkLabelBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** P8 heading-up landmark labels (spec section 6, "Map orientation and upright labels"). */
class LandmarkLabelsTest {
    private val center = GeoCoord(1.2170, -77.2885)
    private val size = 454f // 227 dp at u = 2
    private val u = 2f
    private val anchorX = size / 2f
    private val anchorY = size * USER_ANCHOR_Y_FRACTION

    private fun place(name: String, category: String, dLat: Double, dLon: Double, conf: Float = 0.9f) = IndexedPlace(
        name, listOf(name.lowercase()), emptyList(), emptyList(), category, "", GeoCoord(center.lat + dLat, center.lon + dLon), conf, "test"
    )

    /** A fixed-width font: 6 px per character, 20 px high. */
    private val measure: (String) -> Pair<Float, Float> = { it.length * 6f to 20f }

    private fun select(c: List<IndexedPlace>, zoom: Double = 17.0, bearing: Double = 0.0, blocked: List<LabelBlock> = emptyList()) =
        LandmarkLabels.select(c, center, bearing, zoom, 512.0, size, size, anchorX, anchorY, u, blocked, measure)

    private val ring = (0 until 16).map { i ->
        val a = Math.toRadians(i * 22.5)
        place("Landmark $i", "park", 0.0012 * kotlin.math.cos(a) + 0.0006, 0.0012 * kotlin.math.sin(a))
    }

    @Test fun onlyTrustedLandmarksOncePerName() {
        val all = listOf(
            place("Universidad Mariana", "college_university", 0.001, 0.0),
            place("Universidad Mariana", "college_university", 0.0011, 0.0), // same name, 11 m away
            place("Drogueria Central", "pharmacy", 0.0, 0.001),
            place("Parque Bolivar", "park", 0.002, 0.0, conf = 0.4f),
            place("Parqueadero Norte", "parking", 0.0, -0.001)
        )
        assertEquals(listOf("Universidad Mariana"), LandmarkLabels.candidates(all).map { it.name })
    }

    @Test fun universitiesRankBeforeParks() {
        val c = LandmarkLabels.candidates(listOf(place("Parque A", "park", 0.0, 0.0), place("U B", "college_university", 0.0, 0.0)))
        assertEquals("U B", c.first().name)
    }

    @Test fun nothingBelowZoom16() {
        assertTrue(select(ring, zoom = 15.0).isEmpty())
        assertTrue(select(ring, zoom = 16.0).isNotEmpty())
    }

    @Test fun atMostSixInsideTheDialClearOfTheChevronAndEachOther() {
        for (bearing in listOf(0.0, 90.0, 180.0, 305.0)) {
            val labels = select(ring, bearing = bearing)
            assertTrue(labels.size in 1..LandmarkLabels.MAX)
            for (l in labels) {
                val corners = listOf(l.x - l.w / 2 to l.y - l.h / 2, l.x + l.w / 2 to l.y + l.h / 2, l.x - l.w / 2 to l.y + l.h / 2, l.x + l.w / 2 to l.y - l.h / 2)
                assertTrue(corners.all { (x, y) -> hypot(x - size / 2, y - size / 2) <= 103f * u + 0.01f })
                val nx = anchorX.coerceIn(l.x - l.w / 2, l.x + l.w / 2)
                val ny = anchorY.coerceIn(l.y - l.h / 2, l.y + l.h / 2)
                assertTrue(hypot(nx - anchorX, ny - anchorY) >= 26f * u)
            }
            for (i in labels.indices) for (j in i + 1 until labels.size) {
                val a = labels[i]
                val b = labels[j]
                val overlap = kotlin.math.abs(a.x - b.x) < (a.w + b.w) / 2 && kotlin.math.abs(a.y - b.y) < (a.h + b.h) / 2
                assertFalse("$a / $b", overlap)
            }
        }
    }

    @Test fun labelsKeepClearOfTheControls() {
        for (hud in HudMode.entries) {
            val blocks = landmarkLabelBlocks(hud, exploring = true)
            for (l in select(ring, blocked = blocks)) for (k in blocks) {
                val overlap = l.x - l.w / 2 < k.right * u && l.x + l.w / 2 > k.left * u && l.y - l.h / 2 < k.bottom * u && l.y + l.h / 2 > k.top * u
                assertFalse("$l under $k", overlap)
            }
        }
    }

    @Test fun theExpandedHudBlocksMoreThanTheCompactPill() {
        val compact = landmarkLabelBlocks(HudMode.Compact, exploring = false)
        val expanded = landmarkLabelBlocks(HudMode.Expanded, exploring = false)
        assertTrue(expanded.first().bottom > compact.first().bottom)
        assertEquals(compact.size + 1, landmarkLabelBlocks(HudMode.Compact, exploring = true).size)
    }

    @Test fun longNamesAreShortened() {
        val short = LandmarkLabels.shorten("Universidad Cooperativa de Colombia")
        assertTrue(short, short.endsWith("…") && short.length <= LandmarkLabels.MAX_CHARS)
        assertTrue(short.startsWith("Universidad Cooperati"))
        assertEquals("UCC Pasto", LandmarkLabels.shorten("UCC Pasto"))
    }
}
