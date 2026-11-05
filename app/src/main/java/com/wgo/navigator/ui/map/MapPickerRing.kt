package com.wgo.navigator.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.wgo.navigator.ui.theme.MapStyleId
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.accent

private const val RING_RADIUS = 106f
private const val ACTIVE_STROKE = 7f
private const val INACTIVE_STROKE = 4f
private const val INACTIVE_ALPHA = 0.42f

/**
 * Bezel ring of 8 arcs (one per style, in its `accent`), 38 degrees each on radius 106 reference dp, with a FIXED
 * pointer at 12 o'clock (11 x 9, tip at y 13). The whole ring rotates by `-k * 45` degrees so the active arc always
 * sits under the pointer. [index] is the continuous index `k`; it is read only inside the draw lambda, so rotating
 * never recomposes. Decorative: the name and genre next to it carry the meaning, never the arc color alone.
 */
@Composable
fun MapPickerRing(index: () -> Float, modifier: Modifier = Modifier) {
    val pointerColor = Wgo.palette.ink
    Canvas(modifier = modifier.fillMaxSize().graphicsLayer().clearAndSetSemantics { }) {
        val u = size.width / 227f
        val center = size.center
        val k = index()
        val active = MapPickerMath.activeIndex(k)
        val radius = RING_RADIUS * u

        rotate(MapPickerMath.ringRotation(k), pivot = center) {
            MapStyleId.entries.forEachIndexed { i, id ->
                val isActive = i == active
                val width = (if (isActive) ACTIVE_STROKE else INACTIVE_STROKE) * u
                val color = id.accent.let { if (isActive) it else it.copy(alpha = INACTIVE_ALPHA) }
                drawArc(
                    color = color,
                    startAngle = -90f + i * MapPickerMath.DEGREES_PER_STYLE - MapPickerMath.ARC_SWEEP_DEGREES / 2f,
                    sweepAngle = MapPickerMath.ARC_SWEEP_DEGREES,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(2 * radius, 2 * radius),
                    style = Stroke(width, cap = StrokeCap.Round)
                )
            }
        }

        // Fixed pointer: triangle 11 wide x 9 high, base at y 4, tip at y 13.
        val pointer = Path().apply {
            moveTo(center.x - 5.5f * u, 4f * u)
            lineTo(center.x + 5.5f * u, 4f * u)
            lineTo(center.x, 13f * u)
            close()
        }
        drawPath(pointer, pointerColor)
    }
}
