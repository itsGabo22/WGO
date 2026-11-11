package com.wgo.navigator.ui.map

import android.content.Context
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.SqlTileWriter
import java.io.File

/**
 * Global osmdroid setup: the app package as User-Agent (required by the OpenStreetMap tile usage policy) and a small
 * cache kept in the app cache directory, so it is evictable by the system and never touches shared storage.
 *
 * [warmUp] runs off the main thread during the cold-start intro; [ensure] is called right before a MapView is built
 * and is a no-op once the configuration is applied (if the warm-up is still running it waits for it).
 */
object WgoMapConfig {
    /**
     * Extra tile scale on top of osmdroid's density scaling (spec section 9.1, legibility): tiles are drawn this many
     * times larger and come from `log2(TILE_SCALE)` zoom levels lower, so the ground shown is unchanged while street
     * lines and labels grow, and each tile covers more ground (fewer tile bytes per km).
     */
    const val TILE_SCALE = 2.0f

    private const val DISK_CACHE_MAX_BYTES = 20L * 1024 * 1024
    private const val DISK_CACHE_TRIM_BYTES = 15L * 1024 * 1024
    private const val MEMORY_CACHE_TILES = 12

    @Volatile
    private var applied = false
    @Volatile
    private var cacheOpened = false

    fun ensure(context: Context) {
        if (applied) return
        synchronized(this) {
            if (applied) return
            apply(context.applicationContext)
            applied = true
        }
    }

    /**
     * Background warm-up: applies the configuration (shared-preferences read, cache directories) and opens the tile
     * cache database once. osmdroid keeps that database handle static, so the MapView reuses it instead of opening it
     * on the main thread. Needs no permission.
     */
    fun warmUp(context: Context) {
        ensure(context)
        if (cacheOpened) return
        synchronized(this) {
            if (cacheOpened) return
            SqlTileWriter()
            cacheOpened = true
        }
    }

    private fun apply(context: Context) {
        val config = Configuration.getInstance()
        config.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        val base = File(context.cacheDir, "osmdroid").apply { mkdirs() }
        config.osmdroidBasePath = base
        config.osmdroidTileCache = File(base, "tiles").apply { mkdirs() }
        config.userAgentValue = context.packageName
        config.tileFileSystemCacheMaxBytes = DISK_CACHE_MAX_BYTES
        config.tileFileSystemCacheTrimBytes = DISK_CACHE_TRIM_BYTES
        config.cacheMapTileCount = MEMORY_CACHE_TILES.toShort()
        config.tileDownloadThreads = 2
        config.tileFileSystemThreads = 2
    }
}
