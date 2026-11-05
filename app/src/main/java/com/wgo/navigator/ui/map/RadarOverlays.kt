package com.wgo.navigator.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.wgo.navigator.ui.theme.LocalWgoReduceMotion
import com.wgo.navigator.ui.theme.WgoClock
import com.wgo.navigator.ui.theme.WgoMapPalette
import com.wgo.navigator.ui.theme.atLeastRealDp
import kotlin.math.hypot

@Immutable
data class ProjectedBlip(val position: Offset, val kind: BlipKind)

private val RingRadiiDp = floatArrayOf(38f, 76f, 114f)

/**
 * All radar motion and markers, in ONE Canvas. Every piece of per-frame motion (flow dashes, node breathing, blip
 * pings, user halo, sweep) reads [clock] inside the draw lambda, so the `MapView` and the composition are never
 * invalidated per frame. [route] and the points are already projected to pixels (re-computed at most once a second).
 * With reduce motion the radar is static: no sweep, flow, halo or pings.
 */
@Composable
fun RadarOverlays(
    palette: WgoMapPalette,
    nodeFill: Color,
    incident: Color,
    poi: Color,
    anchor: Offset,
    route: Path?,
    maneuver: Offset?,
    blips: List<ProjectedBlip>,
    clock: WgoClock,
    modifier: Modifier = Modifier
) {
    val reduceMotion = LocalWgoReduceMotion.current
    val ringStroke = 1.2.dp.atLeastRealDp()
    val sweepBrush = remember(anchor, palette.route) {
        Brush.sweepGradient(
            0f to Color.Transparent,
            0.7f to Color.Transparent,
            1f to palette.route.copy(alpha = 0.20f),
            center = anchor
        )
    }

    Canvas(modifier = modifier.fillMaxSize().clearAndSetSemantics { }) {
        // Range rings centered on the user (1.2 dp dashed).
        val ringDash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        RingRadiiDp.forEach { r ->
            drawCircle(
                color = palette.rangeRing,
                radius = r.dp.toPx(),
                center = anchor,
                style = Stroke(width = ringStroke.toPx(), pathEffect = ringDash)
            )
        }

        // Route: halo 12 dp under line 6.5 dp, round caps, plus moving flow dashes (3 on / 12 off, 2.4 dp, 1000 ms).
        if (route != null) {
            drawPath(route, palette.routeHalo, style = Stroke(12.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(route, palette.route, style = Stroke(6.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val on = 3.dp.toPx()
            val off = 12.dp.toPx()
            val phase = if (reduceMotion) 0f else -(clock.phase(1000) * (on + off))
            drawPath(
                route, palette.routeHalo,
                style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Butt, join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(on, off), phase))
            )
        }

        // Maneuver node: 6 dp radius, canvas fill, route stroke 3 dp, breathing 1 -> 1.06.
        if (maneuver != null) {
            val breathe = if (reduceMotion) 1f else 1f + 0.06f * clock.pingPong(1100)
            scale(breathe, breathe, pivot = maneuver) {
                drawCircle(nodeFill, 6.dp.toPx(), maneuver)
                drawCircle(palette.route, 6.dp.toPx(), maneuver, style = Stroke(3.dp.toPx()))
            }
        }

        // Blips: 3.2 dp dot with a ping ring (scale 1 -> 2.2, fade, 2400 ms; every second blip offset by 1200 ms).
        blips.forEachIndexed { index, blip ->
            val color = if (blip.kind == BlipKind.Incident) incident else poi
            drawCircle(color, 3.2.dp.toPx(), blip.position)
            if (!reduceMotion) {
                val p = ((clock.millis + if (index % 2 == 1) 1200L else 0L) % 2400L).toFloat() / 2400f
                drawCircle(
                    color = color.copy(alpha = 0.6f * (1f - p)),
                    radius = 3.2.dp.toPx() * (1f + 1.2f * p),
                    center = blip.position,
                    style = Stroke(1.2.dp.toPx())
                )
            }
        }

        // User: pulsing 15 dp halo, then the chevron, which is NEVER rotated (the map rotates instead).
        val pulse = if (reduceMotion) 0f else clock.pingPong(1800)
        drawCircle(palette.route.copy(alpha = 0.24f), 15.dp.toPx() * (1f + 0.2f * pulse), anchor)
        drawChevron(palette, anchor)

        // Conic radar sweep (3600 ms per turn), `map-route` at 20%.
        if (!reduceMotion) {
            rotate(degrees = 360f * clock.phase(3600), pivot = anchor) {
                drawCircle(sweepBrush, radius = hypot(size.width, size.height), center = anchor)
            }
        }
    }
}

private fun DrawScope.drawChevron(palette: WgoMapPalette, anchor: Offset) {
    val w = 7.dp.toPx()
    val tip = 10.dp.toPx()
    val tail = 8.dp.toPx()
    val notch = 4.dp.toPx()
    val chevron = Path().apply {
        moveTo(anchor.x, anchor.y - tip)
        lineTo(anchor.x + w, anchor.y + tail)
        lineTo(anchor.x, anchor.y + notch)
        lineTo(anchor.x - w, anchor.y + tail)
        close()
    }
    drawPath(chevron, palette.ground, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
    drawPath(chevron, palette.player)
}
