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
    /** Gear: center hub, outer ring and eight teeth. */
    val Settings = icon(
        "Settings",
        "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z",
        "M12 6a6 6 0 1 0 0 12 6 6 0 0 0 0-12z",
        "M12 2.5v3.5 M12 18v3.5 M2.5 12H6 M18 12h3.5",
        "M5.3 5.3l2.5 2.5 M16.2 16.2l2.5 2.5 M5.3 18.7l2.5-2.5 M16.2 7.8l2.5-2.5"
    )
    /** Map pin: teardrop outline with a centre ring. */
    val LocationPin = icon(
        "LocationPin",
        "M12 21s-6.5-6.2-6.5-11a6.5 6.5 0 0 1 13 0c0 4.8-6.5 11-6.5 11z",
        "M12 7.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z"
    )
    val ChevronRight = icon("ChevronRight", "M9 6l6 6-6 6")
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
