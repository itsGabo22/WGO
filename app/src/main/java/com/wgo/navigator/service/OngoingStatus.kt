package com.wgo.navigator.service

import com.wgo.navigator.Language
import com.wgo.navigator.core.Maneuver
import com.wgo.navigator.nav.NavState
import com.wgo.navigator.route.ManeuverText
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.formatDistance
import com.wgo.navigator.ui.stringFor
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The short status line of the trip's ongoing activity / notification ("200 m · Gira a la derecha"), and when to post
 * it again. Pure Kotlin (unit-tested): the service only posts what [Throttle] lets through.
 */
object OngoingStatus {
    /** Minimum time between two posts of the notification. */
    const val MIN_INTERVAL_MS = 5_000L

    /**
     * Distance shown in the status, in coarse buckets so it changes rarely: 10 m under 100 m, 50 m under 1 km, then
     * 0.1 km (the HUD keeps the exact value).
     */
    fun bucketMeters(meters: Double): Double = when {
        meters < 100 -> (meters / 10).roundToInt() * 10.0
        meters < 1000 -> ((meters / 50).roundToInt() * 50.0).coerceAtMost(1000.0)
        else -> (meters / 100).roundToInt() * 100.0
    }

    fun text(state: NavState, language: Language): String = when (state) {
        NavState.WaitingForGps -> stringFor(StringKey.ONGOING_SEARCHING, language)
        is NavState.GpsLost -> stringFor(StringKey.ONGOING_GPS_LOST, language)
        is NavState.Rerouting, is NavState.OffRoute -> stringFor(StringKey.ONGOING_REROUTING, language)
        is NavState.LostRoute -> stringFor(StringKey.ONGOING_LOST_ROUTE, language)
        NavState.Arrived -> stringFor(StringKey.MAN_ARRIVE, language)
        is NavState.Navigating -> {
            val next = state.next
            val maneuver = stringFor(ManeuverText.keyFor(next?.maneuver ?: Maneuver.STRAIGHT), language)
            String.format(
                if (language == Language.ES) Locale("es", "CO") else Locale.US,
                stringFor(StringKey.ONGOING_STATUS, language),
                formatDistance(bucketMeters(state.distanceToNextM), language),
                maneuver
            )
        }
    }

    /** Lets a status through only when it changed and the last post is at least [minIntervalMs] old. */
    class Throttle(private val minIntervalMs: Long = MIN_INTERVAL_MS) {
        private var lastText: String? = null
        private var lastAtMs = Long.MIN_VALUE / 2

        /** Posts made so far (for the budget counters). */
        var posts = 0
            private set

        fun shouldPost(text: String, nowMs: Long): Boolean {
            if (text == lastText || nowMs - lastAtMs < minIntervalMs) return false
            lastText = text
            lastAtMs = nowMs
            posts++
            return true
        }
    }
}
