package com.wgo.navigator.ui.map

import android.content.Context
import org.osmdroid.config.Configuration
import java.io.File

/**
 * Global osmdroid setup: the app package as User-Agent (required by the OpenStreetMap tile usage policy) and a small
 * cache kept in the app cache directory, so it is evictable by the system and never touches shared storage.
 */
object WgoMapConfig {
    private const val DISK_CACHE_MAX_BYTES = 20L * 1024 * 1024
    private const val DISK_CACHE_TRIM_BYTES = 15L * 1024 * 1024
    private const val MEMORY_CACHE_TILES = 12

    fun apply(context: Context) {
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
