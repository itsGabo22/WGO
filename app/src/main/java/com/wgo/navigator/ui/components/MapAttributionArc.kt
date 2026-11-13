package com.wgo.navigator.ui.components

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.wgo.navigator.R
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.map.TileProvider
import com.wgo.navigator.ui.string
import com.wgo.navigator.ui.theme.LocalWgoReduceMotion
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoMotion

/** Baseline radius of the attribution arc, reference dp from the dial centre (spec 9.1, P8). */
const val ATTRIBUTION_BASELINE_RADIUS = 101f

/** Halo stroke width in reference dp (the text's contrast is measured against it). */
private const val HALO_DP = 2f

/** Text opacity over the halo (Night 7.4:1, Daylight 5.7:1). */
private const val TEXT_ALPHA = 0.6f

/**
 * The map's data credit (spec section 9.1, P8): "© OpenStreetMap · © CARTO" (OSM pipeline: "© OpenStreetMap") in 7 sp,
 * drawn upright along the bottom arc of the dial at radius [ATTRIBUTION_BASELINE_RADIUS], `ink` at 60 % over a `canvas`
 * halo. Never touchable (taps pass to the controls); fades with `base` 240 ms (a cut with reduce motion), the fade read
 * in the draw phase only. Fills the whole dial; place it as a full-size overlay.
 */
@Composable
fun MapAttributionArc(visible: Boolean, provider: TileProvider, modifier: Modifier = Modifier) {
    val palette = Wgo.palette
    val reduceMotion = LocalWgoReduceMotion.current
    val alpha = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(240, easing = WgoMotion.Standard),
        label = "mapAttributionArc"
    )
    val text = listOfNotNull(
        string(StringKey.OSM_ATTRIBUTION),
        string(StringKey.MAP_CREDIT_CARTO).takeIf { provider == TileProvider.CARTO }
    ).joinToString(" · ")
    val context = LocalContext.current
    val paints = remember(context) {
        val base = ResourcesCompat.getFont(context, R.font.space_grotesk) ?: Typeface.DEFAULT
        val face = Typeface.create(base, 500, false)
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face }
        Triple(halo, fill, Path())
    }
    val haloColor = palette.canvas.toArgb()
    val inkColor = palette.ink.toArgb()
    Canvas(
        modifier
            .fillMaxSize()
            .clearAndSetSemantics { if (visible) contentDescription = text }
    ) {
        val a = alpha.value
        if (a <= 0f) return@Canvas
        val (halo, fill, path) = paints
        val u = size.minDimension / 227f
        val textPx = 7.sp.toPx()
        halo.textSize = textPx
        fill.textSize = textPx
        halo.strokeWidth = HALO_DP * u
        halo.color = haloColor
        halo.alpha = (255 * a).toInt()
        fill.color = inkColor
        fill.alpha = (255 * TEXT_ALPHA * a).toInt()
        val r = ATTRIBUTION_BASELINE_RADIUS * u
        val width = fill.measureText(text)
        // Half the text's angle; the path runs right-to-left in angle (left to right on screen) so glyph tops face
        // the centre: an upright "smile" at the bottom of the dial.
        val half = Math.toDegrees((width / 2f / r).toDouble()).toFloat()
        val cx = size.width / 2f
        val cy = size.height / 2f
        path.reset()
        path.addArc(RectF(cx - r, cy - r, cx + r, cy + r), 90f + half, -2f * half)
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawTextOnPath(text, path, 0f, 0f, halo)
            canvas.nativeCanvas.drawTextOnPath(text, path, 0f, 0f, fill)
        }
    }
}
