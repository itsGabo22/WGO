package com.wgo.navigator.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.wgo.navigator.ui.theme.LocalWgoReduceMotion
import com.wgo.navigator.ui.theme.WgoClock
import com.wgo.navigator.ui.theme.WgoMapStyle
import com.wgo.navigator.ui.theme.atLeastRealDp

@Immutable
data class ProjectedBlip(val position: Offset, val kind: BlipKind)

/**
 * All radar motion and markers, in ONE Canvas. Every piece of per-frame motion (flow dashes, node breathing, blip
 * pings, user halo, sweep, clouds) reads [clock] inside the draw lambda, so the `MapView` and the composition are never
 * invalidated per frame. [route] and the points are already projected to pixels (re-computed at most once a second).
 * The style decides the look (caps, glow, stepped timing, sweep strength). With reduce motion the radar is static.
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
    modifier: Modifier = Modifier
) {
    val reduceMotion = LocalWgoReduceMotion.current
    val ringStroke = 1.2.dp.atLeastRealDp()
    val look = mapLookFor(style.id)
    val palette = style.palette

    Canvas(modifier = modifier.fillMaxSize().graphicsLayer().clearAndSetSemantics { }) {
        val u = 1.dp.toPx()
        val t = if (reduceMotion) 0L else clock.millis
        val anim = MapAnim(t, look.stepped)

        // Cloud shadow falls on the ground, under the rings and the route.
        drawMapClouds(style, u, if (reduceMotion) null else anim)

        drawRings(palette, anchor, u, ringStroke.toPx(), look)

        if (route != null) drawRoute(route, palette, look, u, flow = if (reduceMotion) 0f else anim.phase(1000))

        if (maneuver != null) {
            drawManeuverNode(palette, nodeFill, maneuver, u, breathe = if (reduceMotion) 1f else 1f + 0.06f * anim.ping(1100), look = look)
        }

        blips.forEachIndexed { index, blip ->
            val color = if (blip.kind == BlipKind.Incident) incident else poi
            val ping = if (reduceMotion) null else MapAnim(t + if (index % 2 == 1) 1200L else 0L, look.stepped).phase(2400)
            drawBlip(color, blip.position, u, ping, look)
        }

        drawUser(palette, look, anchor, u, pulse = if (reduceMotion) 0f else anim.ping(1800))

        if (!reduceMotion) {
            val turn = (t % 3600L).toFloat() / 3600f
            drawSweep(palette, look, anchor, if (look.stepped) stepValue(turn, 12) else turn)
        }
    }
}
