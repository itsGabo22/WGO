package com.wgo.navigator.ui.map

import android.graphics.ColorMatrixColorFilter
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.wgo.navigator.metrics.WgoMetrics
import android.graphics.Color
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.util.TileSystem
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import kotlin.math.roundToInt

/** Where the user sits on the 227 dp canvas: (113.5, 150), i.e. below the center so the road ahead has room. */
const val USER_ANCHOR_Y_FRACTION = 150f / 227f

/** Zoom levels the tiles come from below the radar's zoom: log2 of [WgoMapConfig.TILE_SCALE]. */
private val TILE_ZOOM_SHIFT = kotlin.math.ln(WgoMapConfig.TILE_SCALE.toDouble()) / kotlin.math.ln(2.0)

/**
 * Raster tiles only (the overlays are Compose layers above it), clipped to a circle (spec section 9.1, map layers):
 * - the base layer ([provider]'s base) recoloured by [filter]; with CARTO a second tiles overlay draws the labels above
 *   it with [labelFilter] (transparent while loading); the route and every other Compose layer sit above both;
 * - the user is held at the fixed anchor with `setMapCenterOffset` and the map is re-centered on [center];
 * - heading-up: `mapOrientation = -bearing`, so the map turns while Compose draws the chevron unrotated.
 * The `MapView` is touched only when one of the inputs changes (about once per second), never per frame, and it is
 * tied to the lifecycle (`onResume` / `onPause` / `onDetach`). No zoom controls, no touch gestures.
 * [onTileSize] reports osmdroid's real tile size in px so overlay math can match it.
 */
@Composable
fun RadarMapView(
    center: GeoCoord,
    bearing: Float,
    zoom: Double,
    filter: ColorMatrixColorFilter,
    modifier: Modifier = Modifier,
    provider: TileProvider = TileProvider.OSM,
    labelFilter: ColorMatrixColorFilter? = null,
    onTileSize: (Double) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var size by remember { mutableStateOf(IntSize.Zero) }

    // The labels layer (CARTO only): its own provider on the shared tile cache; nothing drawn while a tile loads.
    val labels = remember {
        TilesOverlay(MapTileProviderBasic(context.applicationContext, MapTiles.cartoLabels), context.applicationContext).apply {
            loadingBackgroundColor = Color.TRANSPARENT
            loadingLineColor = Color.TRANSPARENT
            setHorizontalWrapEnabled(false)
            setVerticalWrapEnabled(false)
        }
    }

    val mapView = remember {
        WgoMapConfig.ensure(context) // no-op after the intro warm-up
        MapView(context).apply {
            setMultiTouchControls(false)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            tilesScaleFactor = WgoMapConfig.TILE_SCALE
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            // Static radar: swallow touches so the map cannot be panned.
            setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_UP) view.performClick()
                true
            }
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            // The MapView detaches the overlays it holds; the labels layer only needs it when it was left out (OSM).
            val labelsAttached = mapView.overlayManager.contains(labels)
            mapView.onDetach()
            if (!labelsAttached) labels.onDetach(mapView)
        }
    }

    // The provider decides the base source, its loading colour (the base's land, so the filter shows `ground`) and
    // whether the labels layer exists. Runs again only if the provider changes (the OSM fallback).
    DisposableEffect(mapView, provider) {
        val base = if (provider == TileProvider.CARTO) MapTiles.cartoBase else MapTiles.osmBase
        mapView.setTileSource(base)
        mapView.overlayManager.tilesOverlay.loadingBackgroundColor = provider.base.loadingArgb
        mapView.overlayManager.tilesOverlay.loadingLineColor = provider.base.loadingArgb
        if (provider == TileProvider.CARTO) {
            if (!mapView.overlayManager.contains(labels)) mapView.overlayManager.add(labels)
        } else {
            mapView.overlayManager.remove(labels)
        }
        mapView.invalidate()
        WgoMetrics.event("tiles map provider=${provider.name.lowercase()}")
        onDispose { }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
            .fillMaxSize()
            .clip(CircleShape)
            .onSizeChanged { size = it },
        update = { map ->
            WgoMetrics.inc(WgoMetrics.Counter.MAP_INVALIDATE)
            if (size.height > 0) {
                // Offset of the map center from the view center: puts the user at (center x, 150/227 of the height).
                val offsetY = (size.height * USER_ANCHOR_Y_FRACTION - size.height / 2f).roundToInt()
                map.setMapCenterOffset(0, offsetY)
            }
            map.overlayManager.tilesOverlay.setColorFilter(filter)
            labels.setColorFilter(labelFilter)
            // Larger tiles from a lower zoom: the same ground on screen (see WgoMapConfig.TILE_SCALE).
            map.controller.setZoom(zoom - TILE_ZOOM_SHIFT)
            map.mapOrientation = -bearing
            map.controller.setCenter(GeoPoint(center.lat, center.lon))
            // The overlays project with the radar's zoom, so they get the tile size that zoom implies.
            onTileSize(TileSystem.getTileSize() / WgoMapConfig.TILE_SCALE.toDouble())
        }
    )
}
