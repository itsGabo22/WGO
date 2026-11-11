package com.wgo.navigator.ui.map

import android.graphics.drawable.Drawable
import com.wgo.navigator.BuildConfig
import com.wgo.navigator.metrics.WgoMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.MapTileIndex
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest

/** Who serves the map tiles (spec section 9.1, map layers). */
enum class TileProvider(val base: TileBase) {
    /** CARTO Dark Matter without labels (recoloured) + CARTO labels at 2x (neutral stretch). Needs `MAP_TILE_KEY`. */
    CARTO(TileBase.DARK_MATTER),

    /** The P6-B pipeline: OpenStreetMap standard tiles, labels inside, OSM filter weights. */
    OSM(TileBase.OSM)
}

/**
 * The map tile sources and which provider is active. CARTO is used when `MAP_TILE_KEY` is set; it switches to OSM for
 * the rest of the process the first time CARTO refuses the key. CARTO answers a missing or wrong key with HTTP 200 and
 * a small "API KEY REQUIRED" image (measured 2026-11-11), so the refusal is recognised by those bytes. The key is only
 * put in the tile URL: never logged, never in a source name (the cache namespace uses a short hash of it).
 */
object MapTiles {
    private val key: String = BuildConfig.MAP_TILE_KEY.trim()

    private val _provider = MutableStateFlow(if (key.isEmpty()) TileProvider.OSM else TileProvider.CARTO)
    val provider: StateFlow<TileProvider> = _provider.asStateFlow()

    init {
        WgoMetrics.event("tiles provider=${_provider.value.name.lowercase()} reason=${if (key.isEmpty()) "no_key" else "key"}")
    }

    private val keyTag: String by lazy {
        MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).take(4).joinToString("") { "%02x".format(it) }
    }

    /** Dark Matter without labels, 1x (recoloured by the style filter, so extra resolution buys nothing). */
    val cartoBase: ITileSource by lazy { CartoTileSource("wgo-carto-dm-nl-$keyTag", "dark_nolabels", retina = false, key) }

    /** Labels only, 2x: the text is drawn at TILE_SCALE 2, so the 512 px tile keeps it sharp. */
    val cartoLabels: ITileSource by lazy { CartoTileSource("wgo-carto-lab2x-$keyTag", "light_only_labels", retina = true, key) }

    val osmBase: ITileSource get() = TileSourceFactory.MAPNIK
}

/**
 * One CARTO raster layer (`https://basemaps.cartocdn.com/<layer>/{z}/{x}/{y}[@2x].png?key=`). At most two downloads at a
 * time, no bulk download, no preventive fetching. A key-required image is never drawn: it switches the map to OSM.
 */
private class CartoTileSource(name: String, private val layer: String, private val retina: Boolean, private val key: String) :
    OnlineTileSourceBase(
        name, 0, 20, 256, ".png", arrayOf("https://basemaps.cartocdn.com/"), "© OpenStreetMap contributors, © CARTO",
        TileSourcePolicy(
            2,
            TileSourcePolicy.FLAG_NO_BULK or TileSourcePolicy.FLAG_NO_PREVENTIVE or TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL
        )
    ) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + layer + "/" + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) + (if (retina) "@2x" else "") + ".png?key=" + key
}
