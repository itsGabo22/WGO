package com.wgo.navigator.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Text
import com.wgo.navigator.core.Maneuver
import com.wgo.navigator.core.RouteStep
import com.wgo.navigator.nav.NavState
import com.wgo.navigator.route.ManeuverText
import com.wgo.navigator.service.guidance
import com.wgo.navigator.ui.LocalLanguage
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.formatDistance
import com.wgo.navigator.ui.minutesOf
import com.wgo.navigator.ui.string
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoType

/** What the platform says about the ambient display (AmbientLifecycleObserver.AmbientDetails). */
@Immutable
data class AmbientInfo(val lowBit: Boolean = false, val burnInProtection: Boolean = false)

/**
 * Placements of the ambient navigation screen, in reference dp on the 227 canvas (`top`, width, height), checked
 * against the fit rule by AmbientNavigationTest. Every element stays >= 10 dp inside the dial (fit <= 103), so the
 * system's own burn-in shift never clips it; the app does not shift anything itself (current Wear OS guidance).
 */
object AmbientLayout {
    const val ICON_TOP = 30f
    const val ICON = 40f
    const val DISTANCE_TOP = 76f
    const val DISTANCE_W = 150f
    const val DISTANCE_H = 30f
    const val TEXT_TOP = 110f
    const val TEXT_W = 160f
    const val TEXT_H = 36f
    const val TOTALS_TOP = 158f
    const val TOTALS_W = 140f
    const val TOTALS_H = 14f

    /** Thin strokes only: the maneuver icon is outlined at this width. */
    const val ICON_STROKE = 1.5f
}

/**
 * When the ambient screen redraws: on the system's ambient tick (about once a minute) and at once when the next
 * maneuver or the guidance state changes (a new turn, GPS lost, rerouting). Between those it keeps the last values, so
 * a fix every second does not redraw the low-power screen. Pure (unit-tested).
 */
class AmbientSnapshot(initial: NavState) {
    var shown: NavState = initial
        private set
    private var lastTick = 0

    /** Called for every new state and with the current ambient tick count; true when [shown] changed. */
    fun update(state: NavState, tick: Int): Boolean {
        val refresh = tick != lastTick || keyOf(state) != keyOf(shown)
        lastTick = tick
        if (refresh && state != shown) {
            shown = state
            return true
        }
        return false
    }

    companion object {
        /** The state kind plus the next maneuver: what must never be stale on the ambient screen. */
        fun keyOf(state: NavState): Any = state.javaClass to state.guidance()?.next?.let { it.maneuver to it.location }
    }
}

private val TURN_RIGHT = PathParser().parsePathString("M6 20v-8a4 4 0 0 1 4-4h8 M14 4l4 4-4 4").toPath()
private val STRAIGHT = PathParser().parsePathString("M12 19V5 M5 12l7-7 7 7").toPath()
private val STRAIGHT_LIKE = setOf(Maneuver.STRAIGHT, Maneuver.DEPART, Maneuver.ARRIVE)
private val LEFT_LIKE = setOf(Maneuver.SLIGHT_LEFT, Maneuver.LEFT, Maneuver.SHARP_LEFT, Maneuver.U_TURN)

/**
 * Minimal always-on navigation screen (P6-A), shown only while a trip runs and the system is in ambient: black, the
 * next maneuver as a thin outline icon, the distance to it, the instruction (or the GPS / reroute status), and the
 * remaining distance with the ETA. No radar, map, animation, frame loop or gradient; all colours are ambient-* tokens
 * (light grey, never pure white). In low-bit ambient the icon is drawn without anti-aliasing.
 */
@Composable
fun AmbientNavigationScreen(navState: NavState, modifier: Modifier = Modifier, info: AmbientInfo = AmbientInfo()) {
    val colors = Wgo.palette.ambient
    val language = LocalLanguage.current
    val nav = navState.guidance()
    val next: RouteStep? = nav?.next
    val status = when (navState) {
        NavState.WaitingForGps -> StringKey.ONGOING_SEARCHING
        is NavState.GpsLost -> StringKey.ONGOING_GPS_LOST
        is NavState.OffRoute, is NavState.Rerouting -> StringKey.ONGOING_REROUTING
        is NavState.LostRoute -> StringKey.ONGOING_LOST_ROUTE
        else -> null
    }
    Box(modifier.fillMaxSize().background(colors.canvas)) {
        // Next maneuver: outline only, thin stroke.
        val maneuver = next?.maneuver
        Canvas(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = AmbientLayout.ICON_TOP.dp)
                .size(AmbientLayout.ICON.dp)
        ) {
            val scale = size.minDimension / 24f
            val paint = Paint().apply {
                color = colors.ink
                style = PaintingStyle.Stroke
                strokeWidth = AmbientLayout.ICON_STROKE.dp.toPx() / scale
                strokeCap = StrokeCap.Round
                strokeJoin = StrokeJoin.Round
                isAntiAlias = !info.lowBit
            }
            drawIntoCanvas { canvas ->
                canvas.save()
                if (maneuver in LEFT_LIKE) {
                    canvas.translate(size.width, 0f)
                    canvas.scale(-scale, scale)
                } else {
                    canvas.scale(scale, scale)
                }
                canvas.drawPath(if (maneuver == null || maneuver in STRAIGHT_LIKE) STRAIGHT else TURN_RIGHT, paint)
                canvas.restore()
            }
        }
        Text(
            text = nav?.let { formatDistance(it.distanceToNextM, language) } ?: "--",
            color = colors.ink,
            style = WgoType.numeralLg,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = AmbientLayout.DISTANCE_TOP.dp)
                .width(AmbientLayout.DISTANCE_W.dp)
        )
        Text(
            text = status?.let { string(it) } ?: next?.let { ManeuverText.instruction(it, language) } ?: "",
            color = colors.ink,
            style = WgoType.bodyMd,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = AmbientLayout.TEXT_TOP.dp)
                .width(AmbientLayout.TEXT_W.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        )
        if (nav != null) Text(
            text = String.format(string(StringKey.NAV_TOTALS), formatDistance(nav.remainingM, language), minutesOf(nav.etaS))
                .uppercase(),
            color = colors.inkMuted,
            style = WgoType.labelMd,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = AmbientLayout.TOTALS_TOP.dp)
                .width(AmbientLayout.TOTALS_W.dp)
        )
    }
}
