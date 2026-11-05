package com.wgo.navigator.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Design icon set: 24-grid, 2 dp rounded strokes, hand-written so the app does not depend on
 * material-icons-extended for the new pieces. Icons are tinted by the caller (the stroke color here is a placeholder).
 */
object WgoIcons {
    private fun icon(name: String, vararg paths: String): ImageVector = icon(name, 2f, *paths)

    private fun icon(name: String, strokeWidth: Float, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            paths.forEach { d ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round
                )
            }
        }.build()

    val Mic = icon(
        "Mic",
        "M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3z",
        "M5 11a7 7 0 0 0 14 0",
        "M12 18v3"
    )
    val ArrowUpward = icon("ArrowUpward", "M12 19V5", "M5 12l7-7 7 7")
    val TurnRight = turnRight()

    /** Maneuver icon with a custom stroke (the TurnCard uses 2.6). */
    fun turnRight(strokeWidth: Float = 2f): ImageVector =
        icon("TurnRight", strokeWidth, "M6 20v-8a4 4 0 0 1 4-4h8", "M14 4l4 4-4 4")
    val Check = icon("Check", "M5 12.5l4.5 4.5L19 7")
    val Close = icon("Close", "M6 6l12 12", "M18 6L6 18")
    val DirectionsCar = icon(
        "DirectionsCar",
        "M3 13l2-5a2 2 0 0 1 2-1.5h10a2 2 0 0 1 2 1.5l2 5v4H3z",
        "M3 13h18",
        "M7.5 16.5h.01",
        "M16.5 16.5h.01",
        "M6 17v2",
        "M18 17v2"
    )
    val DirectionsWalk = icon(
        "DirectionsWalk",
        "M13 5.5a1 1 0 1 0 2 0a1 1 0 1 0-2 0",
        "M14 9l-3 4 3 3v5",
        "M14 9l3 3h3",
        "M11 13l-3 1"
    )
}
