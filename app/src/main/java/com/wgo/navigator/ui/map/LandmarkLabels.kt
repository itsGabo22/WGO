package com.wgo.navigator.ui.map

import com.wgo.navigator.places.IndexedPlace
import com.wgo.navigator.places.PlaceCategories
import com.wgo.navigator.places.PlaceText
import kotlin.math.hypot

/** A box the labels must avoid (a control), in reference dp of the 227 dp canvas. */
data class LabelBlock(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** A landmark label placed on screen: [text] centred at ([x], [y]) in px, box [w] x [h] px. Always upright. */
data class PlacedLabel(val text: String, val x: Float, val y: Float, val w: Float, val h: Float)

/**
 * Which landmark names the heading-up map draws, and where (spec section 6, "Map orientation and upright labels", P8).
 * Pure: projection, ranking and a greedy collision check, so the rules are unit-tested.
 */
object LandmarkLabels {
    const val MAX = 6
    const val MIN_ZOOM = 16.0
    const val MAX_CHARS = 22
    const val MIN_CONFIDENCE = 0.6f
    private const val SAME_NAME_M = 300.0
    private const val DIAL_RADIUS_DP = 103f
    private const val CHEVRON_CLEAR_DP = 26f
    private const val MARGIN_DP = 4f

    /** Category priority: universities, hospitals, malls, transport hubs, stadiums, government, parks and plazas. */
    fun priority(category: String): Int {
        val c = category.lowercase()
        return when {
            c.contains("university") || c.contains("college") -> 0
            c.contains("hospital") -> 1
            c.contains("mall") || c.contains("shopping_center") -> 2
            c.contains("bus_station") || c.contains("airport") || c.contains("aerodrome") || c.contains("train_station") -> 3
            c.contains("stadium") -> 4
            c.contains("government") || c.contains("town_hall") || c.contains("townhall") || c.contains("city_hall") -> 5
            else -> 6
        }
    }

    /** The landmark rows worth labelling: landmark categories, confidence >= 0.6, one per name within 300 m. */
    fun candidates(all: List<IndexedPlace>): List<IndexedPlace> {
        val ranked = all.filter { it.confidence >= MIN_CONFIDENCE && PlaceCategories.isLandmark(it.category) }
            .sortedWith(compareBy<IndexedPlace> { priority(it.category) }.thenByDescending { it.confidence })
        val kept = ArrayList<IndexedPlace>()
        for (p in ranked) {
            val n = PlaceText.normalize(p.name)
            if (kept.none { PlaceText.normalize(it.name) == n && RadarProjection.distanceMeters(it.coord, p.coord) <= SAME_NAME_M }) kept += p
        }
        return kept
    }

    /** Names longer than [MAX_CHARS] end with "…". */
    fun shorten(name: String): String =
        if (name.length <= MAX_CHARS) name else name.take(MAX_CHARS - 1).trimEnd() + "…"

    /**
     * Up to [MAX] labels for the view: [center] at the anchor ([anchorX], [anchorY]) with the map turned by [bearingDeg].
     * [u] = px per reference dp; [width] x [height] the radar in px; [measure] gives a text's box in px. Ranked by
     * priority, confidence, then nearness to the view centre; a label is kept when its whole box is inside the dial,
     * clear of the chevron, of every [blocked] control and of the boxes already placed. Nothing below [MIN_ZOOM].
     */
    fun select(
        candidates: List<IndexedPlace>,
        center: GeoCoord,
        bearingDeg: Double,
        zoom: Double,
        tileSizePx: Double,
        width: Float,
        height: Float,
        anchorX: Float,
        anchorY: Float,
        u: Float,
        blocked: List<LabelBlock> = emptyList(),
        measure: (String) -> Pair<Float, Float>
    ): List<PlacedLabel> {
        if (zoom < MIN_ZOOM || candidates.isEmpty() || width <= 0f) return emptyList()
        val cx = width / 2f
        val cy = height / 2f
        val radius = DIAL_RADIUS_DP * u
        val projected = candidates.mapNotNull { p ->
            val s = RadarProjection.toScreen(p.coord, center, bearingDeg, zoom, anchorX, anchorY, tileSizePx)
            if (hypot(s.x - cx, s.y - cy) > radius) null else Triple(p, s, hypot(s.x - cx, s.y - cy))
        }.sortedWith(
            compareBy<Triple<IndexedPlace, ScreenPoint, Float>> { priority(it.first.category) }
                .thenByDescending { it.first.confidence }
                .thenBy { it.third }
        )
        val placed = ArrayList<PlacedLabel>()
        val margin = MARGIN_DP * u
        for ((place, s, _) in projected) {
            if (placed.size == MAX) break
            val text = shorten(place.name)
            val (w, h) = measure(text)
            // Centred on the place, else just above, else just below it (still read as that place's name).
            for (dy in floatArrayOf(0f, -1.2f * h, 1.2f * h)) {
                val y = s.y + dy
                if (fits(s.x, y, w, h, cx, cy, radius, anchorX, anchorY, u, blocked, placed, margin)) {
                    placed += PlacedLabel(text, s.x, y, w, h)
                    break
                }
            }
        }
        return placed
    }

    private fun fits(
        x: Float, y: Float, w: Float, h: Float, cx: Float, cy: Float, radius: Float, anchorX: Float, anchorY: Float, u: Float,
        blocked: List<LabelBlock>, placed: List<PlacedLabel>, margin: Float
    ): Boolean {
        val l = x - w / 2f
        val t = y - h / 2f
        val r = l + w
        val b = t + h
        if (listOf(l to t, r to t, l to b, r to b).any { (px, py) -> hypot(px - cx, py - cy) > radius }) return false
        val onControl = blocked.any { k ->
            l < (k.right + MARGIN_DP) * u && r > (k.left - MARGIN_DP) * u && t < (k.bottom + MARGIN_DP) * u && b > (k.top - MARGIN_DP) * u
        }
        if (onControl) return false
        val nx = anchorX.coerceIn(l, r)
        val ny = anchorY.coerceIn(t, b)
        if (hypot(nx - anchorX, ny - anchorY) < CHEVRON_CLEAR_DP * u) return false
        return placed.none { o ->
            l < o.x + o.w / 2f + margin && r > o.x - o.w / 2f - margin && t < o.y + o.h / 2f + margin && b > o.y - o.h / 2f - margin
        }
    }
}
