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

    /** CARTO refused the key: OSM for the rest of the process (logged once). */
    fun reject(reason: String) {
        if (_provider.compareAndSet(TileProvider.CARTO, TileProvider.OSM)) {
            WgoMetrics.event("tiles provider=osm reason=$reason")
        }
    }

    private val keyTag: String by lazy {
        MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).take(4).joinToString("") { "%02x".format(it) }
    }

    /** Dark Matter without labels, 1x (recoloured by the style filter, so extra resolution buys nothing). */
    val cartoBase: ITileSource by lazy { CartoTileSource("wgo-carto-dm-nl-$keyTag", "dark_nolabels", retina = false, key) }

    /**
     * Labels only, 1x (measured on the watch: 2x labels cost 109 KB/km, 1x 80.8 KB/km, under the 100 KB/km budget; the
     * text drawn at TILE_SCALE 2 is a little softer and equally readable).
     */
    val cartoLabels: ITileSource by lazy { CartoTileSource("wgo-carto-lab1x-$keyTag", "light_only_labels", retina = false, key) }

    val osmBase: ITileSource get() = TileSourceFactory.MAPNIK

    /** SHA-256 of CARTO's four "API KEY REQUIRED" images (dark/light, nolabels/only_labels), 2005-2513 bytes. */
    private val PLACEHOLDERS = setOf(
        "cc3f31364a735d9a8179af36b3db7617cdff8bdb0248654ebcc3df91607abedc",
        "a6d428bc422a74fe7ad9b023c85335248653c9d142939ae98b476068ce51fde4",
        "69be4aecf9f814ba45d585801a5aa41e79b16fc88eafec5b1954a9f06a69e6be",
        "d8602cc7a1bd26a5c3316859ed99655f0d06064cfc02a016b403c0b6cfb19843"
    )
    private const val PLACEHOLDER_MIN_BYTES = 1_900
    private const val PLACEHOLDER_MAX_BYTES = 2_600

    /** True when [bytes] is CARTO's key-required image. */
    fun isPlaceholder(bytes: ByteArray): Boolean {
        if (bytes.size !in PLACEHOLDER_MIN_BYTES..PLACEHOLDER_MAX_BYTES) return false
        val hex = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return hex in PLACEHOLDERS
    }
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

    override fun getDrawable(aFileInputStream: InputStream): Drawable? {
        val bytes = aFileInputStream.readBytes()
        if (MapTiles.isPlaceholder(bytes)) {
            MapTiles.reject("key_refused")
            return null
        }
        return super.getDrawable(ByteArrayInputStream(bytes))
    }
}
