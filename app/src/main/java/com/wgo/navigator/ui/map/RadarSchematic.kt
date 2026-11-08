package com.wgo.navigator.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.wgo.navigator.ui.theme.ClockRate
import com.wgo.navigator.ui.theme.LocalWgoReduceMotion
import com.wgo.navigator.ui.theme.MapStyleId
import com.wgo.navigator.ui.theme.NightPalette
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoClock
import com.wgo.navigator.ui.theme.WgoMapPalette
import com.wgo.navigator.ui.theme.WgoMapStyle
import com.wgo.navigator.ui.theme.rememberWgoClock

/** Default thumbnail window: 140 x 140 reference dp centered at (113.5, 105). */
val DefaultSchematicCrop = Rect(43.5f, 35f, 183.5f, 175f)

/** The whole 227 x 227 reference scene. */
val FullSchematicCrop = Rect(0f, 0f, 227f, 227f)

private const val USER_X = 113.5f
private const val USER_Y = 150f

private val Buildings = listOf(
    floatArrayOf(122f, 78f, 60f, 26f), floatArrayOf(40f, 76f, 66f, 28f), floatArrayOf(198f, 76f, 24f, 26f),
    floatArrayOf(122f, 116f, 30f, 36f), floatArrayOf(164f, 116f, 22f, 56f), floatArrayOf(40f, 24f, 66f, 38f),
    floatArrayOf(122f, 24f, 60f, 38f), floatArrayOf(198f, 24f, 24f, 38f), floatArrayOf(40f, 196f, 40f, 18f)
)
private val Parks = listOf(floatArrayOf(40f, 116f, 66f, 68f, 10f), floatArrayOf(122f, 196f, 52f, 30f, 8f))
private val MinorRoads = listOf(
    floatArrayOf(0f, 18f, 227f, 18f), floatArrayOf(0f, 110f, 227f, 110f), floatArrayOf(0f, 190f, 227f, 190f),
    floatArrayOf(34f, 0f, 34f, 227f), floatArrayOf(192f, 0f, 192f, 227f), floatArrayOf(34f, 190f, 90f, 227f)
)
private val MajorRoads = listOf(floatArrayOf(113.5f, 0f, 113.5f, 227f), floatArrayOf(0f, 70f, 227f, 70f))

/**
 * A small SCHEMATIC radar drawn entirely in one Compose Canvas from the style palette (no MapView), in the 227 x 227
 * reference space scaled to the composable size. [crop] selects the window of the scene to show (default: the 140 x 140
 * window of the round thumbnails). With [live] = false it draws one still frame and never reads the clock; with
 * [live] = true flow dashes, the user halo and the blips animate from the shared [clock].
 * The style effects (hatch, scanlines, grid, vignette, clouds) come from the same code as the real radar.
 * Schematic-only styling: outline-only buildings (SONAR), thin roads (SONAR), wider major roads (CIRCUITO), dashed
 * centre lines (BARRIO, PERGAMINO, CIRCUITO, ISLA), building outlines (BARRIO, PERGAMINO), rounded buildings (ISLA).
 */
@Composable
fun RadarSchematic(
    style: WgoMapStyle,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    clock: WgoClock? = null,
    crop: Rect = DefaultSchematicCrop,
    showBlips: Boolean = true
) {
    val theme = Wgo.palette
    val dark = theme === NightPalette
    val reduceMotion = LocalWgoReduceMotion.current
    val animated = live && !reduceMotion
    val sharedClock = if (animated) clock ?: rememberWgoClock() else null
    // Live only in the picker disc, which the user is manipulating: full rate.
    sharedClock?.Use(ClockRate.Full)
    val look = mapLookFor(style.id)

    // Two layers, each promoted to its own render node: the static scene (never reads the clock, so its display list
    // is recorded once and only replayed) and the dynamic markers (re-recorded per frame when live).
    Box(modifier = modifier.clearAndSetSemantics { }) {
        Spacer(
            Modifier.fillMaxSize().graphicsLayer().drawWithCache {
                // The static scene is rasterised once per style/theme/size/crop and then only blitted.
                val scene = SchematicSceneCache.get(style, dark, size, crop, this, layoutDirection) {
                    val u = size.width / crop.width
                    fun p(x: Float, y: Float) = Offset((x - crop.left) * u, (y - crop.top) * u)
                    val palette = style.palette
                    drawRect(palette.ground)
                    drawScene(style.id, palette, look, u, ::p)
                    drawMapFxStatic(style, u)
                }
                onDrawBehind { if (scene != null) drawImage(scene) }
            }
        )

        Spacer(
            Modifier.fillMaxSize().graphicsLayer().drawWithCache {
                // Built once per size/style: positions, the route path and every stroke/effect/shader (RadarPaint).
                val u = size.width / crop.width
                fun p(x: Float, y: Float) = Offset((x - crop.left) * u, (y - crop.top) * u)
                val palette = style.palette
                val paint = RadarPaint(palette, look, u, p(USER_X, USER_Y), 1.2f * u)
                val node = p(USER_X, 70f)
                val incidentAt = p(158f, 132f)
                val poiAt = p(64f, 158f)
                val route = Path().apply {
                    val a = paint.anchor; val c = p(240f, 70f)
                    moveTo(a.x, a.y); lineTo(node.x, node.y); lineTo(c.x, c.y)
                }
                onDrawBehind {
                    val anim = sharedClock?.let { MapAnim(it.millis, look.stepped) }

                    drawMapClouds(style, u, anim)

                    // Rings, route, node, blips, user: the same helpers the real radar uses.
                    drawRings(palette, paint)
                    drawRoute(route, palette, paint, flow = anim?.phase(1000) ?: 0f)
                    drawManeuverNode(palette, palette.ground, node, u, 1f, look)
                    if (showBlips) {
                        drawBlip(theme.alert, incidentAt, u, anim?.phase(2400), look)
                        drawBlip(theme.go, poiAt, u, anim?.let { MapAnim(it.raw + 1200L, look.stepped).phase(2400) }, look)
                    }
                    drawUser(palette, paint, pulse = anim?.ping(1800) ?: 0f)

                    if (dark && style.id.dimsInNight()) drawRect(theme.canvas.copy(alpha = NIGHT_DIM_ALPHA))
                }
            }
        )
    }
}

private fun DrawScope.drawScene(
    id: MapStyleId, palette: WgoMapPalette, look: MapLook, u: Float, p: (Float, Float) -> Offset
) {
    val outlineOnly = id == MapStyleId.SONAR
    val outlines = id == MapStyleId.BARRIO || id == MapStyleId.PERGAMINO
    val buildingRadius = when (id) { MapStyleId.PIXEL -> 0f; MapStyleId.ISLA -> 6f; else -> 3f }
    val outlineColor = if (outlineOnly) palette.roadMajor else palette.roadCasing

    // water
    val water = Path().apply {
        val a = p(-10f, 228f); moveTo(a.x, a.y)
        val c1 = p(40f, 212f); val c2 = p(70f, 236f); val e1 = p(112f, 224f)
        cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)
        val d1 = p(154f, 212f); val d2 = p(182f, 198f); val e2 = p(240f, 208f)
        cubicTo(d1.x, d1.y, d2.x, d2.y, e2.x, e2.y)
    }
    drawPath(water, palette.water, style = Stroke(16f * u, cap = look.cap))

    // parks and buildings (outline-only for SONAR)
    Parks.forEach { r ->
        val tl = p(r[0], r[1]); val sz = Size(r[2] * u, r[3] * u); val cr = CornerRadius(if (look.square) 0f else r[4] * u)
        if (outlineOnly) drawRoundRect(outlineColor, tl, sz, cr, style = Stroke(1.2f * u)) else drawRoundRect(palette.park, tl, sz, cr)
    }
    Buildings.forEach { b ->
        val tl = p(b[0], b[1]); val sz = Size(b[2] * u, b[3] * u); val cr = CornerRadius(buildingRadius * u)
        if (outlineOnly) {
            drawRoundRect(outlineColor, tl, sz, cr, style = Stroke(1.2f * u))
        } else {
            drawRoundRect(palette.building, tl, sz, cr)
            if (outlines) drawRoundRect(outlineColor.copy(alpha = 0.7f), tl, sz, cr, style = Stroke(0.8f * u))
        }
    }

    // roads: casings first, then cores, then the dashed centre line of the major roads
    val thin = outlineOnly
    val minorCasing = if (thin) 3.5f else 7f
    val majorCasing = when { thin -> 5f; id == MapStyleId.CIRCUITO -> 16f; else -> 12f }
    val minorCore = if (thin) 1.8f else 5f
    val majorCore = when { thin -> 3f; id == MapStyleId.CIRCUITO -> 12f; else -> 9f }
    val cap = if (look.square) StrokeCap.Butt else StrokeCap.Round

    fun line(r: FloatArray, color: Color, w: Float, effect: PathEffect? = null) {
        val a = p(r[0], r[1]); val b = p(r[2], r[3])
        drawLine(color, a, b, w * u, cap, effect)
    }
    MinorRoads.forEach { line(it, palette.roadCasing, minorCasing) }
    MajorRoads.forEach { line(it, palette.roadCasing, majorCasing) }
    MinorRoads.forEach { line(it, palette.road, minorCore) }
    MajorRoads.forEach { line(it, palette.roadMajor, majorCore) }
    if (id == MapStyleId.BARRIO || id == MapStyleId.PERGAMINO || id == MapStyleId.CIRCUITO || id == MapStyleId.ISLA) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f * u, 6f * u))
        MajorRoads.forEach { line(it, palette.roadCasing, 1.2f, dash) }
    }
}
