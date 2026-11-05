package com.wgo.navigator.ui.map

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import com.wgo.navigator.ui.theme.MapFx
import com.wgo.navigator.ui.theme.MapStyleId
import com.wgo.navigator.ui.theme.WgoMapPalette
import com.wgo.navigator.ui.theme.WgoMapStyle
import kotlin.math.floor
import kotlin.math.sin

/**
 * Everything style-specific that is not a palette color. The same look drives the real radar and the schematic scene.
 * [stepped] (PIXEL) quantizes flow, halo, sweep and blips; [square] draws hard-edged markers; [glow] stacks strokes
 * (never a blur) around the route; [sweepAlpha] is the strength of the conic sweep.
 */
@Immutable
data class MapLook(
    val cap: StrokeCap,
    val join: StrokeJoin,
    val glow: Boolean,
    val stepped: Boolean,
    val square: Boolean,
    val sweepAlpha: Float
)

private val SoftLook = MapLook(StrokeCap.Round, StrokeJoin.Round, glow = false, stepped = false, square = false, sweepAlpha = 0.20f)

fun mapLookFor(id: MapStyleId): MapLook = when (id) {
    MapStyleId.PIXEL -> SoftLook.copy(cap = StrokeCap.Butt, join = StrokeJoin.Miter, stepped = true, square = true)
    MapStyleId.NEON -> SoftLook.copy(glow = true)
    MapStyleId.SONAR -> SoftLook.copy(glow = true, sweepAlpha = 0.55f)
    else -> SoftLook
}

/** Time source for one frame, created inside a draw lambda. With [stepped] every phase is quantized to 4 steps. */
class MapAnim(private val millis: Long, private val stepped: Boolean) {
    private fun quantize(p: Float) = if (stepped) floor(p * STEPS) / STEPS else p

    /** 0..1 sawtooth over [periodMs]. */
    fun phase(periodMs: Int): Float = quantize((millis % periodMs).toFloat() / periodMs)

    /** 0..1..0 triangle over [periodMs] per direction. */
    fun ping(periodMs: Int): Float {
        val p = (millis % (2L * periodMs)).toFloat() / periodMs
        return quantize(if (p <= 1f) p else 2f - p)
    }

    val raw: Long get() = millis

    private companion object {
        const val STEPS = 4f
    }
}

/** The light styles are dimmed to 78% brightness in Night (a 22% black overlay above the whole radar). */
fun MapStyleId.dimsInNight(): Boolean = this == MapStyleId.PERGAMINO || this == MapStyleId.PIXEL || this == MapStyleId.ISLA

const val NIGHT_DIM_ALPHA = 0.22f

/**
 * Strength of the `ground` wash over the tiles. The light styles use a stronger wash (their dark routes need a light
 * ground, also after the Night dim) so the route keeps at least 3:1 against the surrounding tile.
 */
fun MapStyleId.washAlpha(): Float = when (this) {
    MapStyleId.PERGAMINO -> 0.70f
    MapStyleId.PIXEL -> 0.60f
    MapStyleId.ISLA -> 0.80f
    else -> 0.25f
}

/** Quantizes [value] 0..1 to [steps] levels; used by the sweep of stepped styles. */
fun stepValue(value: Float, steps: Int): Float = floor(value * steps) / steps

// ---------------------------------------------------------------------------------------------------------------
// Static effects (no clock): drawn once into a cached layer. `u` = pixels per reference dp.
// ---------------------------------------------------------------------------------------------------------------

fun DrawScope.drawMapFxStatic(style: WgoMapStyle, u: Float) {
    val p = style.palette
    when (style.fx) {
        MapFx.NONE -> Unit
        MapFx.VIGNETTE -> {
            // BARRIO: dark vignette plus a bold rim ring.
            vignette(p.roadMajor.copy(alpha = 0.55f), 0.55f)
            drawCircle(p.roadMajor, size.minDimension / 2f - 2f * u, size.center, style = Stroke(4f * u))
        }
        MapFx.PAPER -> {
            // PERGAMINO: paper hatch, contour lines, burnt-edge vignette and compass ticks on the rim.
            hatch(p.roadCasing.copy(alpha = 0.10f), 5f * u, 0.8f * u, diagonal = true)
            contours(p.roadCasing.copy(alpha = 0.16f), u)
            vignette(p.roadCasing.copy(alpha = 0.45f), 0.5f)
            compassTicks(p.roadCasing.copy(alpha = 0.8f), u)
        }
        MapFx.SCANLINES -> {
            var y = 0f
            while (y < size.height) {
                drawLine(p.roadCasing.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), 1f * u)
                y += 3f * u
            }
        }
        MapFx.PIXEL_GRID -> {
            val step = 3f * u
            val c = p.roadCasing.copy(alpha = 0.18f)
            var x = 0f
            while (x < size.width) { drawLine(c, Offset(x, 0f), Offset(x, size.height), 0.5f * u); x += step }
            var y = 0f
            while (y < size.height) { drawLine(c, Offset(0f, y), Offset(size.width, y), 0.5f * u); y += step }
        }
        MapFx.HATCH -> hatch(p.roadMajor.copy(alpha = 0.07f), 7f * u, 1f * u, diagonal = true)
        MapFx.SONAR -> vignette(p.roadCasing.copy(alpha = 0.85f), 0.45f)
        MapFx.CLOUDS -> Unit // animated: see drawMapClouds
    }
}

private fun DrawScope.vignette(edge: Color, innerStop: Float) {
    drawRect(
        brush = Brush.radialGradient(
            innerStop to Color.Transparent,
            1f to edge,
            center = size.center,
            radius = size.minDimension * 0.62f
        )
    )
}

private fun DrawScope.hatch(color: Color, spacing: Float, width: Float, diagonal: Boolean) {
    if (!diagonal) return
    var x = -size.height
    while (x < size.width) {
        drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), width)
        x += spacing
    }
}

/** Soft, wavy topographic contour lines (open paths, no fill). */
private fun DrawScope.contours(color: Color, u: Float) {
    for (k in 0 until 5) {
        val path = Path()
        val baseY = size.height * (0.14f + 0.18f * k)
        var x = 0f
        path.moveTo(0f, baseY)
        while (x <= size.width) {
            path.lineTo(x, baseY + 7f * u * sin(x / (28f * u) + k * 1.3f))
            x += 6f * u
        }
        drawPath(path, color, style = Stroke(0.8f * u))
    }
}

private fun DrawScope.compassTicks(color: Color, u: Float) {
    val r = size.minDimension / 2f
    val c = size.center
    for (i in 0 until 4) {
        rotate(90f * i, pivot = c) {
            drawLine(color, Offset(c.x, c.y - r + 1f * u), Offset(c.x, c.y - r + 7f * u), 1.6f * u)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Animated effect: ISLA cloud shadow (9 s loop), three stacked ellipses of decreasing alpha, no blur.
// ---------------------------------------------------------------------------------------------------------------

fun DrawScope.drawMapClouds(style: WgoMapStyle, u: Float, anim: MapAnim?) {
    if (style.fx != MapFx.CLOUDS) return
    val travel = size.width + 160f * u
    val x = -80f * u + travel * (anim?.phase(9000) ?: 0.3f)
    val y = size.height * 0.38f
    val color = style.palette.player
    listOf(Triple(62f, 38f, 0.03f), Triple(48f, 30f, 0.035f), Triple(34f, 21f, 0.04f)).forEach { (rx, ry, a) ->
        drawOval(color.copy(alpha = a), Offset(x - rx * u, y - ry * u), Size(2 * rx * u, 2 * ry * u))
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Shared markers, used by the real radar overlays and by the schematic scene.
// ---------------------------------------------------------------------------------------------------------------

fun DrawScope.drawRings(palette: WgoMapPalette, anchor: Offset, u: Float, strokePx: Float, look: MapLook) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(4f * u, 4f * u))
    listOf(38f, 76f, 114f).forEach { r ->
        drawCircle(palette.rangeRing, r * u, anchor, style = Stroke(strokePx, cap = if (look.square) StrokeCap.Butt else StrokeCap.Round, pathEffect = dash))
    }
}

/** Halo 12 u under line 6.5 u, optional stacked-stroke glow (no blur), plus moving flow dashes. */
fun DrawScope.drawRoute(route: Path, palette: WgoMapPalette, look: MapLook, u: Float, flow: Float) {
    if (look.glow) {
        // glow: three wider strokes of decreasing alpha in the route color
        listOf(16f to 0.10f, 12f to 0.16f, 9f to 0.26f).forEach { (w, a) ->
            drawPath(route, palette.route.copy(alpha = a), style = Stroke(w * u + 6.5f * u, cap = look.cap, join = look.join))
        }
    }
    drawPath(route, palette.routeHalo, style = Stroke(12f * u, cap = look.cap, join = look.join))
    drawPath(route, palette.route, style = Stroke(6.5f * u, cap = look.cap, join = look.join))
    val on = 3f * u
    val off = 12f * u
    drawPath(
        route, palette.routeHalo,
        style = Stroke(2.4f * u, cap = StrokeCap.Butt, join = look.join,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(on, off), -flow * (on + off)))
    )
}

fun DrawScope.drawManeuverNode(palette: WgoMapPalette, fill: Color, at: Offset, u: Float, breathe: Float, look: MapLook) {
    scale(breathe, breathe, pivot = at) {
        if (look.square) {
            drawRect(fill, Offset(at.x - 6f * u, at.y - 6f * u), Size(12f * u, 12f * u))
            drawRect(palette.route, Offset(at.x - 6f * u, at.y - 6f * u), Size(12f * u, 12f * u), style = Stroke(3f * u))
        } else {
            drawCircle(fill, 6f * u, at)
            drawCircle(palette.route, 6f * u, at, style = Stroke(3f * u))
        }
    }
}

fun DrawScope.drawBlip(color: Color, at: Offset, u: Float, ping: Float?, look: MapLook) {
    val r = 3.2f * u
    if (look.square) drawRect(color, Offset(at.x - r, at.y - r), Size(2 * r, 2 * r)) else drawCircle(color, r, at)
    if (ping != null) {
        val ring = color.copy(alpha = 0.6f * (1f - ping))
        val rr = r * (1f + 1.2f * ping)
        if (look.square) drawRect(ring, Offset(at.x - rr, at.y - rr), Size(2 * rr, 2 * rr), style = Stroke(1.2f * u))
        else drawCircle(ring, rr, at, style = Stroke(1.2f * u))
    }
}

/** User chevron (never rotated) with its 15 u halo: pulse 0..1. */
fun DrawScope.drawUser(palette: WgoMapPalette, look: MapLook, anchor: Offset, u: Float, pulse: Float) {
    val haloColor = palette.route.copy(alpha = 0.24f)
    val haloR = 15f * u * (1f + 0.2f * pulse)
    if (look.square) drawRect(haloColor, Offset(anchor.x - haloR, anchor.y - haloR), Size(2 * haloR, 2 * haloR))
    else drawCircle(haloColor, haloR, anchor)
    val chevron = Path().apply {
        moveTo(anchor.x, anchor.y - 13f * u)
        lineTo(anchor.x + 10f * u, anchor.y + 11f * u)
        lineTo(anchor.x, anchor.y + 6f * u)
        lineTo(anchor.x - 10f * u, anchor.y + 11f * u)
        close()
    }
    drawPath(chevron, palette.ground, style = Stroke(2f * u, join = look.join))
    drawPath(chevron, palette.player)
}

/** Conic radar sweep from [anchor]; the leading edge rotates with [turn] (0..1). */
fun DrawScope.drawSweep(palette: WgoMapPalette, look: MapLook, anchor: Offset, turn: Float) {
    val brush = Brush.sweepGradient(
        0f to Color.Transparent,
        0.7f to Color.Transparent,
        1f to palette.route.copy(alpha = look.sweepAlpha),
        center = anchor
    )
    rotate(degrees = 360f * turn, pivot = anchor) {
        drawCircle(brush, radius = size.width + size.height, center = anchor)
    }
}
