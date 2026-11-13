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

/** The light styles are dimmed in Night by a black overlay above the whole radar (see [nightDimAlpha]). */
fun MapStyleId.dimsInNight(): Boolean = this == MapStyleId.PERGAMINO || this == MapStyleId.PIXEL || this == MapStyleId.ISLA

/**
 * Strength of the Night dim overlay. PIXEL keeps 78% brightness; PERGAMINO and ISLA are dimmed less because their
 * route already sits close to the 3.4:1 floor over the rendered tile (their paper effects darken the surroundings).
 */
fun MapStyleId.nightDimAlpha(): Float = when (this) {
    MapStyleId.PERGAMINO -> 0.10f
    MapStyleId.ISLA -> 0.14f
    else -> 0.22f
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

/** Cloud ellipses: radius x, radius y (reference dp) and alpha, largest first. */
private val CloudRx = floatArrayOf(62f, 48f, 34f)
private val CloudRy = floatArrayOf(38f, 30f, 21f)
private val CloudAlpha = floatArrayOf(0.03f, 0.035f, 0.04f)

fun DrawScope.drawMapClouds(style: WgoMapStyle, u: Float, anim: MapAnim?) {
    if (style.fx != MapFx.CLOUDS) return
    val travel = size.width + 160f * u
    val x = -80f * u + travel * (anim?.phase(9000) ?: 0.3f)
    val y = size.height * 0.38f
    val color = style.palette.player
    for (i in CloudRx.indices) {
        val rx = CloudRx[i]
        val ry = CloudRy[i]
        drawOval(color, Offset(x - rx * u, y - ry * u), Size(2 * rx * u, 2 * ry * u), alpha = CloudAlpha[i])
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Shared markers, used by the real radar overlays and by the schematic scene.
// ---------------------------------------------------------------------------------------------------------------

private val RingRadii = floatArrayOf(38f, 76f, 114f)
private val GlowWidths = floatArrayOf(16f, 12f, 9f)
private val GlowAlphas = floatArrayOf(0.10f, 0.16f, 0.26f)

/**
 * Every object the radar markers reuse per frame (strokes, dash effects, the chevron path, the sweep shader), built
 * ONCE per size/style/anchor from a `drawWithCache` block so the per-frame draw allocates nothing native.
 * The flow dashes use [FLOW_STEPS] precomputed phases (under 1 px apart on every supported screen).
 */
class RadarPaint(palette: WgoMapPalette, val look: MapLook, val u: Float, val anchor: Offset, ringStrokePx: Float) {
    val ringStroke = Stroke(
        ringStrokePx,
        cap = if (look.square) StrokeCap.Butt else StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * u, 4f * u))
    )
    val glowColors = Array(GlowAlphas.size) { palette.route.copy(alpha = GlowAlphas[it]) }
    val glowStrokes = Array(GlowWidths.size) { Stroke(GlowWidths[it] * u + 6.5f * u, cap = look.cap, join = look.join) }
    val haloStroke = Stroke(12f * u, cap = look.cap, join = look.join)
    val lineStroke = Stroke(6.5f * u, cap = look.cap, join = look.join)
    val flowStrokes: Array<Stroke> = run {
        val on = 3f * u
        val off = 12f * u
        Array(FLOW_STEPS) { k ->
            Stroke(
                2.4f * u, cap = StrokeCap.Butt, join = look.join,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(on, off), -(k.toFloat() / FLOW_STEPS) * (on + off))
            )
        }
    }
    val haloColor = palette.route.copy(alpha = 0.24f)
    val chevron = Path().apply {
        moveTo(anchor.x, anchor.y - 13f * u)
        lineTo(anchor.x + 10f * u, anchor.y + 11f * u)
        lineTo(anchor.x, anchor.y + 6f * u)
        lineTo(anchor.x - 10f * u, anchor.y + 11f * u)
        close()
    }
    val chevronOutline = Stroke(2f * u, join = look.join)
    val sweepBrush = Brush.sweepGradient(
        0f to Color.Transparent,
        0.7f to Color.Transparent,
        1f to palette.route.copy(alpha = look.sweepAlpha),
        center = anchor
    )

    companion object {
        const val FLOW_STEPS = 32
    }
}

fun DrawScope.drawRings(palette: WgoMapPalette, paint: RadarPaint) {
    for (r in RingRadii) drawCircle(palette.rangeRing, r * paint.u, paint.anchor, style = paint.ringStroke)
}

/** Halo 12 u under line 6.5 u, optional stacked-stroke glow (no blur), plus moving flow dashes ([flow] 0..1). */
fun DrawScope.drawRoute(route: Path, palette: WgoMapPalette, paint: RadarPaint, flow: Float) {
    if (paint.look.glow) {
        // glow: three wider strokes of decreasing alpha in the route color
        for (i in paint.glowStrokes.indices) drawPath(route, paint.glowColors[i], style = paint.glowStrokes[i])
    }
    drawPath(route, palette.routeHalo, style = paint.haloStroke)
    drawPath(route, palette.route, style = paint.lineStroke)
    val k = (flow * RadarPaint.FLOW_STEPS).toInt().coerceIn(0, RadarPaint.FLOW_STEPS - 1)
    drawPath(route, palette.routeHalo, style = paint.flowStrokes[k])
}

/** P11: another known route, lighter than the active one and never animated (the route colour at 45 %, 3 dp). */
fun DrawScope.drawAlternative(path: Path, palette: WgoMapPalette, u: Float) {
    drawPath(
        path, palette.route.copy(alpha = ALTERNATIVE_ALPHA),
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 3f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round
        )
    )
}

/** P11: the alternatives' alpha (docs/design, "Best route, alternatives and live reroute"). */
const val ALTERNATIVE_ALPHA = 0.45f

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

/**
 * User chevron at the paint's anchor with its 15 u halo: pulse 0..1. [degrees] turns the chevron (north-up maps, P8);
 * 0 in heading-up, where the map turns instead.
 */
fun DrawScope.drawUser(palette: WgoMapPalette, paint: RadarPaint, pulse: Float, degrees: Float = 0f) {
    val anchor = paint.anchor
    val haloR = 15f * paint.u * (1f + 0.2f * pulse)
    if (paint.look.square) drawRect(paint.haloColor, Offset(anchor.x - haloR, anchor.y - haloR), Size(2 * haloR, 2 * haloR))
    else drawCircle(paint.haloColor, haloR, anchor)
    rotate(degrees, pivot = anchor) {
        drawPath(paint.chevron, palette.ground, style = paint.chevronOutline)
        drawPath(paint.chevron, palette.player)
    }
}

/** Conic radar sweep from the paint's anchor; the leading edge rotates with [turn] (0..1). */
fun DrawScope.drawSweep(paint: RadarPaint, turn: Float) {
    val anchor = paint.anchor
    rotate(degrees = 360f * turn, pivot = anchor) {
        drawCircle(paint.sweepBrush, radius = size.width + size.height, center = anchor)
    }
}
