package com.wgo.navigator.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Text
import com.wgo.navigator.ui.theme.NightPalette
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoClock
import com.wgo.navigator.ui.theme.WgoMapStyle
import com.wgo.navigator.ui.theme.WgoType

/**
 * Circular, heading-up radar minimap (docs/design section 6). From bottom to top: tiles, `map-ground` wash, range
 * rings, route, maneuver node, blips, user chevron + halo, radar sweep, optional north marker.
 *
 * The overlays read their colors from [style]. They are re-projected only when the location, bearing, zoom or size
 * change (the data source ticks about once per second); per-frame motion reads [clock] in draw lambdas only.
 * [tileLayer] replaces the real `MapView` (used by previews, which have no network).
 */
@Composable
fun RadarMinimap(
    style: WgoMapStyle,
    userLocation: GeoCoord,
    bearing: Float,
    route: List<GeoCoord>,
    blips: List<RadarBlip>,
    clock: WgoClock,
    modifier: Modifier = Modifier,
    zoom: Double = 17.0,
    maneuver: GeoCoord? = null,
    showNorth: Boolean = false,
    tileLayer: (@Composable (Modifier) -> Unit)? = null
) {
    val palette = Wgo.palette
    val dark = palette === NightPalette
    val filter = remember(style.id, dark) { WgoTileFilter.filterFor(style, dark) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var tileSize by remember { mutableDoubleStateOf(RadarProjection.DEFAULT_TILE_SIZE_PX) }

    val anchor = Offset(size.width / 2f, size.height * USER_ANCHOR_Y_FRACTION)

    // Re-projection happens only when one of these inputs changes (about once per second).
    val projectedRoute = remember(route, userLocation, bearing, zoom, size, tileSize) {
        if (size == IntSize.Zero || route.size < 2) null else Path().apply {
            route.forEachIndexed { i, point ->
                val p = RadarProjection.toScreen(point, userLocation, bearing.toDouble(), zoom, anchor.x, anchor.y, tileSize)
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
        }
    }
    val projectedManeuver = remember(maneuver, userLocation, bearing, zoom, size, tileSize) {
        maneuver?.let {
            RadarProjection.toScreen(it, userLocation, bearing.toDouble(), zoom, anchor.x, anchor.y, tileSize)
                .let { p -> Offset(p.x, p.y) }
        }
    }
    val projectedBlips = remember(blips, userLocation, bearing, zoom, size, tileSize) {
        blips.map {
            val p = RadarProjection.toScreen(it.position, userLocation, bearing.toDouble(), zoom, anchor.x, anchor.y, tileSize)
            ProjectedBlip(Offset(p.x, p.y), it.kind)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(style.palette.ground)
            .onSizeChanged { size = it }
    ) {
        if (tileLayer != null) {
            tileLayer(Modifier.fillMaxSize())
        } else {
            RadarMapView(
                center = userLocation,
                bearing = bearing,
                zoom = zoom,
                filter = filter,
                onTileSize = { tileSize = it }
            )
        }

        // Wash so the tiles never fight the route.
        Box(Modifier.fillMaxSize().background(style.palette.ground.copy(alpha = style.id.washAlpha())))

        // Static style effects (hatch, scanlines, grid, vignette...): no clock read, so this layer is cached.
        Box(Modifier.fillMaxSize().graphicsLayer().drawBehind { drawMapFxStatic(style, 1.dp.toPx()) })

        RadarOverlays(
            style = style,
            nodeFill = palette.canvas,
            incident = palette.alert,
            poi = palette.go,
            anchor = anchor,
            route = projectedRoute,
            maneuver = projectedManeuver,
            blips = projectedBlips,
            clock = clock
        )

        if (dark && style.id.dimsInNight()) {
            Box(Modifier.fillMaxSize().background(palette.canvas.copy(alpha = NIGHT_DIM_ALPHA)))
        }

        if (showNorth) {
            // North marker: 18 dp disc, left 12, top 104 (fractions of the 227 dp canvas).
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 12.dp, y = maxHeight * (104f / 227f))
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(palette.ink)
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "N", color = palette.inkInverse, style = WgoType.labelSm)
            }
        }
    }
}
