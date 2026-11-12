package com.wgo.navigator.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.wgo.navigator.ui.theme.ClockRate
import com.wgo.navigator.ui.theme.LocalWgoReduceMotion
import com.wgo.navigator.ui.theme.MapFx
import com.wgo.navigator.ui.theme.WgoClock
import com.wgo.navigator.ui.theme.WgoMapStyle
import com.wgo.navigator.ui.theme.atLeastRealDp

@Immutable
data class ProjectedBlip(val position: Offset, val kind: BlipKind)

/**
 * All radar motion and markers. Three render nodes:
 * 1. clouds (ISLA only; the only style effect that moves),
 * 2. the static range rings (never read the clock, so the display list is recorded once and replayed),
 * 3. route, node, blips, user and sweep: they read [clock] inside the draw lambda only, and every stroke, dash effect,
 *    path and shader they need is built once in `drawWithCache` ([RadarPaint]), so a frame allocates nothing native.
 * The `MapView` and the composition are never invalidated per frame. [route] and the points are already projected to
 * pixels (re-computed when the location changes). With reduce motion the radar is static.
 */
@Composable
fun RadarOverlays(
    style: WgoMapStyle,
    nodeFill: Color,
    incident: Color,
    poi: Color,
    anchor: Offset,
    route: Path?,
    maneuver: Offset?,
    blips: List<ProjectedBlip>,
    clock: WgoClock,
    modifier: Modifier = Modifier,
    /** The chevron's rotation in degrees (north-up maps, P8). */
    chevronDegrees: Float = 0f
) {
    val reduceMotion = LocalWgoReduceMotion.current
    val ringStroke = 1.2.dp.atLeastRealDp()
    val look = mapLookFor(style.id)
    val palette = style.palette
    // Radar sweep, flow, halo and clouds are slow ambience: 30 fps is enough.
    clock.Use(ClockRate.Ambience)

    Box(modifier = modifier.fillMaxSize().clearAndSetSemantics { }) {
        if (style.fx == MapFx.CLOUDS && !reduceMotion) {
            Spacer(
                Modifier.fillMaxSize().graphicsLayer().drawBehind {
                    drawMapClouds(style, 1.dp.toPx(), MapAnim(clock.millis, look.stepped))
                }
            )
        } else if (style.fx == MapFx.CLOUDS) {
            Spacer(Modifier.fillMaxSize().graphicsLayer().drawBehind { drawMapClouds(style, 1.dp.toPx(), null) })
        }

        Spacer(
            Modifier.fillMaxSize().graphicsLayer().drawWithCache {
                val paint = RadarPaint(palette, look, 1.dp.toPx(), anchor, ringStroke.toPx())
                onDrawBehind { drawRings(palette, paint) }
            }
        )

        Spacer(
            Modifier.fillMaxSize().graphicsLayer().drawWithCache {
                val paint = RadarPaint(palette, look, 1.dp.toPx(), anchor, ringStroke.toPx())
                onDrawBehind {
                    val u = paint.u
                    val t = if (reduceMotion) 0L else clock.millis
                    val anim = MapAnim(t, look.stepped)

                    if (route != null) drawRoute(route, palette, paint, flow = if (reduceMotion) 0f else anim.phase(1000))

                    if (maneuver != null) {
                        drawManeuverNode(palette, nodeFill, maneuver, u, breathe = if (reduceMotion) 1f else 1f + 0.06f * anim.ping(1100), look = look)
                    }

                    for (index in blips.indices) {
                        val blip = blips[index]
                        val color = if (blip.kind == BlipKind.Incident) incident else poi
                        val ping = if (reduceMotion) null else MapAnim(t + if (index % 2 == 1) 1200L else 0L, look.stepped).phase(2400)
                        drawBlip(color, blip.position, u, ping, look)
                    }

                    drawUser(palette, paint, pulse = if (reduceMotion) 0f else anim.ping(1800), degrees = chevronDegrees)

                    if (!reduceMotion) {
                        val turn = (t % 3600L).toFloat() / 3600f
                        drawSweep(paint, if (look.stepped) stepValue(turn, 12) else turn)
                    }
                }
            }
        )
    }
}
