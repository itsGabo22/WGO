package com.wgo.navigator.ui.map

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.wgo.navigator.R
import com.wgo.navigator.places.IndexedPlace
import com.wgo.navigator.ui.theme.Wgo

private const val TEXT_SP = 10f
private const val HALO_DP = 2.5f

/**
 * Upright landmark names over a heading-up map (spec section 6, P8): [LandmarkLabels] picks them when the camera
 * changes (never per frame); this layer only draws them, `ink` over a `canvas` halo, never rotated.
 */
@Composable
fun LandmarkLabelLayer(
    candidates: List<IndexedPlace>,
    center: GeoCoord,
    bearingDeg: Double,
    zoom: Double,
    tileSizePx: Double,
    size: IntSize,
    anchor: Offset,
    blocked: List<LabelBlock> = emptyList()
) {
    if (candidates.isEmpty() || size == IntSize.Zero) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val palette = Wgo.palette
    val paints = remember(context, density) {
        val base = ResourcesCompat.getFont(context, R.font.space_grotesk) ?: Typeface.DEFAULT
        val face = Typeface.create(base, 600, false)
        val textPx = with(density) { TEXT_SP.sp.toPx() }
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = textPx
            textAlign = Paint.Align.CENTER
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeWidth = with(density) { (HALO_DP * 2).dp.toPx() }
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = textPx
            textAlign = Paint.Align.CENTER
        }
        halo to fill
    }
    val (halo, fill) = paints
    val u = size.width / 227f
    val labels = remember(candidates, center, bearingDeg, zoom, tileSizePx, size, anchor, blocked) {
        val metrics = fill.fontMetrics
        val h = metrics.descent - metrics.ascent
        LandmarkLabels.select(
            candidates, center, bearingDeg, zoom, tileSizePx, size.width.toFloat(), size.height.toFloat(), anchor.x, anchor.y, u, blocked
        ) { text -> fill.measureText(text) to h }
    }
    halo.color = palette.canvas.toArgb()
    fill.color = palette.ink.toArgb()
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        val metrics = fill.fontMetrics
        // Baseline so the box (ascent..descent) is centred on the label's point.
        val baselineShift = -(metrics.ascent + metrics.descent) / 2f
        drawIntoCanvas { canvas ->
            for (l in labels) {
                canvas.nativeCanvas.drawText(l.text, l.x, l.y + baselineShift, halo)
                canvas.nativeCanvas.drawText(l.text, l.x, l.y + baselineShift, fill)
            }
        }
    }
}
