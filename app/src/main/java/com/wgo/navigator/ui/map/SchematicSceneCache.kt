package com.wgo.navigator.ui.map

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.wgo.navigator.ui.theme.MapStyleId
import com.wgo.navigator.ui.theme.WgoMapStyle
import kotlin.math.roundToInt

/**
 * Rasterised static schematic scenes (ground, blocks, roads, static FX), keyed by style, theme, pixel size and crop.
 * The map picker shows up to four scenes at once (disc, outgoing disc, two neighbours) and changes them on every dial
 * step; blitting a cached bitmap replaces re-recording and replaying ~40 draw operations per scene.
 * Bounded LRU: 8 styles x (disc + thumbnail) x 2 themes = 32 entries; at Large Round sizes that is under 3 MB.
 * UI thread only.
 */
internal object SchematicSceneCache {
    private const val MAX_ENTRIES = 32

    private data class Key(val id: MapStyleId, val dark: Boolean, val w: Int, val h: Int, val crop: Rect)

    private val entries = object : LinkedHashMap<Key, ImageBitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, ImageBitmap>?): Boolean = size > MAX_ENTRIES
    }

    fun get(
        style: WgoMapStyle,
        dark: Boolean,
        size: Size,
        crop: Rect,
        density: Density,
        layoutDirection: LayoutDirection,
        draw: DrawScope.() -> Unit
    ): ImageBitmap? {
        val w = size.width.roundToInt()
        val h = size.height.roundToInt()
        if (w <= 0 || h <= 0) return null
        val key = Key(style.id, dark, w, h, crop)
        entries[key]?.let { return it }
        val bitmap = ImageBitmap(w, h)
        CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), Size(w.toFloat(), h.toFloat()), draw)
        entries[key] = bitmap
        return bitmap
    }
}
