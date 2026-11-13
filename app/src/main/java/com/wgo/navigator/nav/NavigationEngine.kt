package com.wgo.navigator.nav

import com.wgo.navigator.TravelMode
import com.wgo.navigator.core.Fix
import com.wgo.navigator.core.Maneuver
import com.wgo.navigator.core.Route
import com.wgo.navigator.ui.map.GeoCoord

/** Per-mode thresholds (metres). */
data class NavConfig(
    /** Fixes less accurate than this only keep the "searching" state. */
    val maxAccuracyM: Float,
    /** Off route when the snapped distance exceeds this for [NavigationEngine.OFF_ROUTE_FIXES] fixes in a row. */
    val offRouteM: Double,
    /** Arrived within this distance of the destination. */
    val arrivalM: Double,
    /** How far ahead of the last matched position the forward-only snapping window can reach (after a long gap). */
    val lookaheadM: Double,
    /** Fastest plausible speed: between two fixes the window reaches at most this far plus [NavigationEngine.SLACK_M]. */
    val maxSpeedMps: Double,
    /** P11: the wrong-way rule needs at least this speed (a reliable GPS course). */
    val wrongWaySpeedMps: Float = 3f
) {
    /** Back on the route when within half the off-route distance (hysteresis). */
    val backOnRouteM: Double get() = offRouteM / 2

    companion object {
        val WALK = NavConfig(maxAccuracyM = 50f, offRouteM = 35.0, arrivalM = 15.0, lookaheadM = 150.0, maxSpeedMps = 4.0, wrongWaySpeedMps = 1f)
        val DRIVE = NavConfig(maxAccuracyM = 80f, offRouteM = 50.0, arrivalM = 25.0, lookaheadM = 400.0, maxSpeedMps = 40.0, wrongWaySpeedMps = 3f)

        fun forMode(mode: TravelMode): NavConfig = if (mode == TravelMode.Drive) DRIVE else WALK
    }
}

/**
 * Follows the user along a [Route]. Pure Kotlin with no Android, Compose, Context or lifecycle reference, so a
 * foreground service can host it later unchanged; time comes only from [clock] (and the fixes' own elapsed time).
 *
 * Feed it with [onFix] for every fix and [tick] about once per second (GPS-lost and reroute timing work without
 * fixes). It publishes [state] and calls [onEvent] for one-off events; when it decides a new route is needed it calls
 * [onRerouteRequest] with the user's position and waits for [onRerouteResult]. One thread at a time.
 *
 * Rules (constants below and in [NavConfig]):
 * - Usable fix: accuracy <= 50 m walking / 80 m driving and not older than 10 s; others never move anything.
 * - Snapping: nearest segment inside a forward window around the last match (60 m back; ahead, the distance a fast
 *   walker (4 m/s) or driver (40 m/s) could cover since the last fix plus 40 m, at most 150 m / 400 m), so the cost
 *   per fix does not grow with the route and a loop's later pass is never picked. A full search runs only for
 *   the first fix and while confirmed off route (to rejoin anywhere).
 * - Progress never goes back except on a confirmed U-turn: 3 usable fixes in a row matched more than 10 m behind.
 * - Off route: snapped distance > 35 m (walk) / 50 m (drive) for 3 usable fixes in a row; back on route within half.
 * - Reroute (P11): requested on off route from the current fix, with the GPS course as a bearing hint above
 *   [HINT_MIN_SPEED_MPS]; at least [REROUTE_MIN_INTERVAL_MS] apart, the wait doubling after each failed request (max
 *   [REROUTE_MAX_INTERVAL_MS]); 3 requests in a row without being back on a route -> [NavState.LostRoute]. A failed
 *   request keeps guiding on the old route; an offline answer ([onRerouteResult] with offline) is not a failure: the
 *   state is [NavState.OffRoute] with offline set, and the next request goes out as soon as the session has a network.
 * - Wrong way (P11): on the route but the course more than [WRONG_WAY_DEG] from its direction, at >=
 *   [NavConfig.wrongWaySpeedMps], for [WRONG_WAY_FIXES] fixes -> off route at once (a driver turning back on the same road
 *   is never [NavConfig.offRouteM] from it); a course along the route again clears it.
 * - Arrival: within 15 m (walk) / 25 m (drive) of the destination, or snapped to the very end of the route.
 * - GPS lost: no usable fix for 10 s after the first one, or for two expected fix intervals plus 2 s when the owner
 *   asked for slower fixes ([expectedFixIntervalMs], screen-off power policy); any usable fix recovers.
 * - ETA: the route's own remaining duration divided by an exponentially smoothed ratio of observed to expected speed,
 *   clamped to 0.5x-2x; stationary fixes do not update the ratio (the estimate holds while waiting at a light).
 * - Turn events: spec section 8, approaching at 200 m and "now" at 50 m, once per maneuver, for real turns only
 *   (not for "continue", the departure or the arrival).
 * - Alternatives (P11, docs/ARCHITECTURE.md "Routing and reroute"): the routes OSRM offered besides the active one.
 *   A fix more than [ALT_LEAVE_M] from the active route and within [ALT_NEAR_M] of one alternative, for
 *   [ALT_FIXES] usable fixes in a row on the same alternative, makes it the active route at once (no request;
 *   [NavEvent.Rerouted]); [selectRoute] does the same on the user's choice. The old active route joins the alternatives.
 */
class NavigationEngine(
    route: Route,
    private val clock: NavClock,
    private val onEvent: (NavEvent) -> Unit = {},
    /** The user's position and, above [HINT_MIN_SPEED_MPS], the GPS course in degrees (else null). */
    private val onRerouteRequest: (GeoCoord, Double?) -> Unit = { _, _ -> },
    alternatives: List<Route> = emptyList()
) {
    val config: NavConfig = NavConfig.forMode(route.mode)

    var geometry: RouteGeometry = RouteGeometry(route)
        private set

    var state: NavState = NavState.WaitingForGps
        private set

    /** P11: the known routes other than the active one (each with its steps), best first. */
    var alternatives: List<Route> = alternatives.filter { it.polyline.size >= 2 }.take(MAX_ALTERNATIVES)
        private set
    private var altGeometries: List<RouteGeometry> = this.alternatives.map(::RouteGeometry)
    private var altCandidate = -1
    private var altCount = 0

    // ---- matching ----
    private var matched = false
    private var progress = 0.0
    private var seg = 0
    private var behindCount = 0
    private var nextIdx = 0
    private var approachAnnounced = BooleanArray(geometry.maneuvers.size)
    private var atAnnounced = BooleanArray(geometry.maneuvers.size)

    /** When the last fix was matched (bounds how far ahead the next one may land). */
    private var lastMatchMs = 0L
    private var windowAheadM = 0.0

    // Result of the last match (fields, not objects: no allocation per fix).
    private var candSeg = 0
    private var candAlong = 0.0
    private var candDist = 0.0

    // ---- off route / reroute ----
    private var offCount = 0
    private var offRoute = false
    private var rerouteAttempts = 0
    private var rerouteInFlight = false
    private var lastRerouteMs = Long.MIN_VALUE / 2

    /** P11: failed requests in a row (the back-off), and whether the last answer was "no network". */
    private var rerouteFailures = 0
    private var offline = false

    /** P11: fixes in a row going against the route, and the latched wrong-way state. */
    private var wrongWayCount = 0
    private var wrongWay = false

    // ---- GPS ----
    private var everUsable = false
    private var lastUsableMs = 0L
    private var lastFix: Fix? = null

    // ---- ETA ----
    private var smoothedSpeed = Double.NaN
    private var speedRatio = 1.0
    private var lastProgressMs = 0L
    private var lastProgressAlong = 0.0

    /** The last guidance computed (kept through off-route, rerouting and GPS-lost states). */
    var lastNavigating: NavState.Navigating? = null
        private set

    fun onFix(fix: Fix) {
        if (state == NavState.Arrived) return
        val now = clock.elapsedMs()
        if (now - fix.elapsedNanos / 1_000_000L > STALE_MS) return
        if (fix.accuracyM.isNaN() || fix.accuracyM > config.maxAccuracyM) return

        if (state is NavState.GpsLost) onEvent(NavEvent.GpsRecovered)
        everUsable = true
        lastUsableMs = now
        lastFix = fix
        process(fix, now)
    }

    /** Call about once per second: GPS-lost detection and reroute retries need no fix. */
    /**
     * The interval the owner's location request currently asks for (0 = the normal 1-3 s). A slow request (up to 10 s
     * with the screen off) must not look like a lost GPS: see [gpsLostAfterMs].
     */
    var expectedFixIntervalMs: Long = 0L

    fun tick() {
        if (state == NavState.Arrived || !everUsable) return
        val now = clock.elapsedMs()
        if (state !is NavState.GpsLost && now - lastUsableMs >= gpsLostAfterMs(expectedFixIntervalMs)) {
            state = NavState.GpsLost(lastNavigating)
            onEvent(NavEvent.GpsLost)
            return
        }
        if (state !is NavState.GpsLost) maybeReroute(now)
    }

    /**
     * Fixes stopped on purpose (app in the background, screen off): restart the GPS-lost timer instead of reporting a
     * loss for the time nobody was listening.
     */
    fun onResumed() {
        if (everUsable && state !is NavState.GpsLost) lastUsableMs = clock.elapsedMs()
    }

    /**
     * The answer to [onRerouteRequest]: a new route, or null when the request failed. Returns false (and ignores it)
     * when no request was pending, for example because the user came back on the old route meanwhile.
     */
    fun onRerouteResult(newRoute: Route?, newAlternatives: List<Route> = emptyList(), offline: Boolean = false): Boolean {
        if (!rerouteInFlight) return false
        rerouteInFlight = false
        val last = lastNavigating
        if (newRoute == null || newRoute.polyline.size < 2) {
            if (offline) {
                // No network: nothing was sent; it is not a failure (no back-off, no step toward LOST ROUTE).
                rerouteAttempts = (rerouteAttempts - 1).coerceAtLeast(0)
                this.offline = true
            } else {
                rerouteFailures++
                this.offline = false
            }
            if (offRoute && last != null) state = offRouteState(last)
            return true
        }
        rerouteFailures = 0
        this.offline = false
        wrongWay = false
        wrongWayCount = 0
        setAlternatives(newAlternatives)
        activate(RouteGeometry(newRoute))
        return true
    }

    /**
     * P11: the user picked the known route [index] of [alternatives] (the RUTA chip): it becomes active at once, with no
     * request; the old active route joins the alternatives. False when there is no such route or the trip is over.
     */
    fun selectRoute(index: Int): Boolean {
        if (state == NavState.Arrived || index !in altGeometries.indices) return false
        switchTo(index)
        return true
    }

    private fun setAlternatives(routes: List<Route>) {
        alternatives = routes.filter { it.polyline.size >= 2 }.take(MAX_ALTERNATIVES)
        altGeometries = alternatives.map(::RouteGeometry)
        altCandidate = -1
        altCount = 0
    }

    /** Makes the alternative [index] active; the active route takes its place among the alternatives. */
    private fun switchTo(index: Int) {
        val chosen = altGeometries[index]
        val old = geometry
        altGeometries = altGeometries.toMutableList().also { it[index] = old }
        alternatives = altGeometries.map { it.route }
        altCandidate = -1
        altCount = 0
        // A reroute answer still on its way is for the old situation: ignore it.
        rerouteInFlight = false
        rerouteAttempts = 0
        rerouteFailures = 0
        offline = false
        wrongWay = false
        wrongWayCount = 0
        activate(chosen)
    }

    /** Follows [g] from now on (reroute or switch): matching and announcements start over on it. */
    private fun activate(g: RouteGeometry) {
        geometry = g
        matched = false
        progress = 0.0
        seg = 0
        behindCount = 0
        nextIdx = 0
        approachAnnounced = BooleanArray(geometry.maneuvers.size)
        atAnnounced = BooleanArray(geometry.maneuvers.size)
        offCount = 0
        offRoute = false
        smoothedSpeed = Double.NaN
        speedRatio = 1.0
        onEvent(NavEvent.Rerouted)
        val fix = lastFix
        if (fix != null) process(fix, clock.elapsedMs()) else state = NavState.WaitingForGps
    }

    /** LOST ROUTE -> retry: a fresh series of reroutes, starting now. */
    fun retryReroute() {
        if (state !is NavState.LostRoute) return
        rerouteAttempts = 0
        rerouteFailures = 0
        lastRerouteMs = Long.MIN_VALUE / 2
        maybeReroute(clock.elapsedMs())
    }

    // ---------------------------------------------------------------------------------------------------------------

    private fun process(fix: Fix, now: Long) {
        val g = geometry
        val px = g.x(fix.lon)
        val py = g.y(fix.lat)

        val wasOff = offRoute
        windowAheadM = if (lastMatchMs == 0L) config.lookaheadM
        else minOf(config.lookaheadM, config.maxSpeedMps * (now - lastMatchMs) / 1000.0 + SLACK_M)
        lastMatchMs = now
        when {
            !matched -> matchFull(px, py)
            offRoute -> {
                // Off route: rejoin near the old position if possible, else anywhere on the route.
                matchWindow(px, py)
                if (candDist > config.backOnRouteM) matchFull(px, py)
            }
            else -> matchWindow(px, py)
        }

        // Arrival: near the destination, or snapped to the very end while near the route.
        val dEnd = RouteGeometry.hypot(px - g.xs[g.size - 1], py - g.ys[g.size - 1])
        if (dEnd <= config.arrivalM || (candAlong >= g.total - END_EPSILON_M && candDist <= config.offRouteM && matched)) {
            arrive()
            return
        }

        // P11: on a known alternative (and away from the active route) for ALT_FIXES fixes: it becomes active.
        if (followsAlternative(fix)) {
            switchTo(altCandidate)
            return
        }

        // P11 wrong way: on the route but moving against it.
        checkWrongWay(fix)

        // Off-route counting with hysteresis.
        when {
            candDist > config.offRouteM -> offCount++
            candDist <= config.backOnRouteM && !wrongWay -> {
                offCount = 0
                offRoute = false
                offline = false
                rerouteAttempts = 0 // back on a route: a fresh series of reroutes is allowed again
                rerouteFailures = 0
            }
            else -> if (!offRoute) offCount = 0
        }
        if (!offRoute && (offCount >= OFF_ROUTE_FIXES || wrongWay)) {
            offRoute = true
            onEvent(NavEvent.OffRouteDetected)
        }

        if (!offRoute && candDist <= config.offRouteM) {
            val rejoin = wasOff || !matched
            applyProgress(rejoin)
            matched = true
        }
        updateSpeed(fix, now)

        val nav = guidance(candDist)
        lastNavigating = nav
        if (offRoute) {
            state = offRouteState(nav)
            maybeReroute(now)
        } else {
            state = nav
            announce(nav)
        }
    }

    /** Counts the fixes in a row near the same alternative while away from the active route; true at [ALT_FIXES]. */
    private fun followsAlternative(fix: Fix): Boolean {
        if (altGeometries.isEmpty() || candDist <= ALT_LEAVE_M) {
            altCandidate = -1
            altCount = 0
            return false
        }
        var best = -1
        var bestD = ALT_NEAR_M
        altGeometries.forEachIndexed { i, a ->
            val d = a.distanceTo(fix.lat, fix.lon)
            if (d <= bestD) { bestD = d; best = i }
        }
        if (best < 0) {
            altCandidate = -1
            altCount = 0
            return false
        }
        if (best == altCandidate) altCount++ else { altCandidate = best; altCount = 1 }
        return altCount >= ALT_FIXES
    }

    private fun offRouteState(nav: NavState.Navigating): NavState = when {
        rerouteInFlight -> NavState.Rerouting(nav, rerouteAttempts)
        rerouteAttempts >= MAX_REROUTES -> NavState.LostRoute(nav)
        else -> NavState.OffRoute(nav, offline)
    }

    /** P11: the wait before the next request: 8 s, doubled after each failure in a row, at most 60 s. */
    fun rerouteIntervalMs(): Long =
        minOf(REROUTE_MAX_INTERVAL_MS, REROUTE_MIN_INTERVAL_MS shl rerouteFailures.coerceAtMost(4))

    private fun maybeReroute(now: Long) {
        if (!offRoute || rerouteInFlight || rerouteAttempts >= MAX_REROUTES) {
            lastNavigating?.let { if (offRoute) state = offRouteState(it) }
            return
        }
        if (now - lastRerouteMs < rerouteIntervalMs()) return
        val fix = lastFix ?: return
        rerouteInFlight = true
        rerouteAttempts++
        lastRerouteMs = now
        lastNavigating?.let { state = NavState.Rerouting(it, rerouteAttempts) }
        onRerouteRequest(GeoCoord(fix.lat, fix.lon), courseHint(fix))
    }

    /** P11: the fix's course as the router's bearing hint, only when moving fast enough for it to be reliable. */
    private fun courseHint(fix: Fix): Double? =
        if (!fix.bearingDeg.isNaN() && !fix.speedMps.isNaN() && fix.speedMps >= HINT_MIN_SPEED_MPS) fix.bearingDeg.toDouble() else null

    /** P11: counts the fixes on the route moving against its direction; latches [wrongWay] at [WRONG_WAY_FIXES]. */
    private fun checkWrongWay(fix: Fix) {
        if (!matched || candDist > config.offRouteM || fix.bearingDeg.isNaN() || fix.speedMps.isNaN() ||
            fix.speedMps < config.wrongWaySpeedMps
        ) return
        val g = geometry
        val routeDeg = Math.toDegrees(Math.atan2(g.xs[candSeg + 1] - g.xs[candSeg], g.ys[candSeg + 1] - g.ys[candSeg]))
        var diff = Math.abs(fix.bearingDeg - routeDeg) % 360.0
        if (diff > 180) diff = 360 - diff
        when {
            diff > WRONG_WAY_DEG -> if (++wrongWayCount >= WRONG_WAY_FIXES) wrongWay = true
            diff < ALONG_DEG -> {
                wrongWayCount = 0
                wrongWay = false
            }
        }
    }

    private fun arrive() {
        state = NavState.Arrived
        onEvent(NavEvent.Arrived)
    }

    /** Forward-only window around the last match. */
    private fun matchWindow(px: Double, py: Double) {
        val g = geometry
        var start = seg
        while (start > 0 && g.cum[start] > progress - LOOKBACK_M) start--
        val limit = progress + windowAheadM
        var bestCost = Double.MAX_VALUE
        var s = start
        while (s < g.size - 1 && g.cum[s] <= limit) {
            val cost = cost(s, px, py)
            if (cost < bestCost) {
                bestCost = cost
                keep(s, px, py)
            }
            s++
        }
    }

    /** Whole route: the closest segment, preferring the earliest one among near-ties (a loop's first pass). */
    private fun matchFull(px: Double, py: Double) {
        val g = geometry
        var best = Double.MAX_VALUE
        var bestSeg = 0
        for (s in 0 until g.size - 1) {
            val d = g.distanceTo(s, g.projectT(s, px, py), px, py)
            if (d < best - FULL_TIE_M) {
                best = d
                bestSeg = s
            }
        }
        keep(bestSeg, px, py)
    }

    /** Distance to segment [s], with a small penalty when the match would lie behind the progress (prefer forward). */
    private fun cost(s: Int, px: Double, py: Double): Double {
        val g = geometry
        val t = g.projectT(s, px, py)
        val d = g.distanceTo(s, t, px, py)
        val along = g.cum[s] + t * (g.cum[s + 1] - g.cum[s])
        return if (along < progress - JITTER_M) d + BEHIND_PENALTY_M else d
    }

    private fun keep(s: Int, px: Double, py: Double) {
        val g = geometry
        val t = g.projectT(s, px, py)
        candSeg = s
        candDist = g.distanceTo(s, t, px, py)
        candAlong = g.cum[s] + t * (g.cum[s + 1] - g.cum[s])
    }

    private fun applyProgress(rejoin: Boolean) {
        when {
            rejoin -> {
                progress = candAlong
                seg = candSeg
                behindCount = 0
                resetNextIndex()
            }
            candAlong >= progress - JITTER_M -> {
                if (candAlong > progress) {
                    progress = candAlong
                    seg = candSeg
                }
                behindCount = 0
            }
            else -> {
                behindCount++
                if (behindCount >= U_TURN_FIXES) {
                    progress = candAlong
                    seg = candSeg
                    behindCount = 0
                    resetNextIndex()
                }
            }
        }
    }

    private fun resetNextIndex() {
        nextIdx = 0
        advanceNext()
    }

    private fun advanceNext() {
        val along = geometry.maneuverAlong
        while (nextIdx < along.size && along[nextIdx] <= progress + PASSED_M) nextIdx++
    }

    private fun updateSpeed(fix: Fix, now: Long) {
        val expected = expectedSpeed()
        val observed = when {
            !fix.speedMps.isNaN() -> fix.speedMps.toDouble()
            now > lastProgressMs && lastProgressMs > 0 -> (progress - lastProgressAlong) * 1000.0 / (now - lastProgressMs)
            else -> Double.NaN
        }
        lastProgressMs = now
        lastProgressAlong = progress
        if (observed.isNaN() || observed < STATIONARY_MPS || expected <= 0.0) return
        smoothedSpeed = if (smoothedSpeed.isNaN()) observed else smoothedSpeed + SPEED_ALPHA * (observed - smoothedSpeed)
        speedRatio = (smoothedSpeed / expected).coerceIn(MIN_SPEED_RATIO, MAX_SPEED_RATIO)
    }

    private fun expectedSpeed(): Double {
        val r = geometry.route
        return if (r.durationS > 0) r.distanceM / r.durationS else 0.0
    }

    private fun guidance(distanceFromRoute: Double): NavState.Navigating {
        val g = geometry
        advanceNext()
        val remaining = (g.total - progress).coerceAtLeast(0.0)
        val remainingDuration = if (g.total > 0) g.route.durationS * remaining / g.total else 0.0
        val next = g.maneuvers.getOrNull(nextIdx)
        val then = g.maneuvers.getOrNull(nextIdx + 1)
        val toNext = if (next != null) (g.maneuverAlong[nextIdx] - progress).coerceAtLeast(0.0) else remaining
        return NavState.Navigating(
            progressM = progress,
            remainingM = remaining,
            etaS = remainingDuration / speedRatio,
            next = next,
            distanceToNextM = toNext,
            then = then,
            distanceFromRouteM = distanceFromRoute,
            snapped = g.pointAt(progress)
        )
    }

    private fun announce(nav: NavState.Navigating) {
        val step = nav.next ?: return
        if (step.maneuver in SILENT_MANEUVERS) return
        val i = nextIdx
        val d = nav.distanceToNextM
        if (d <= AT_TURN_M && !atAnnounced[i]) {
            atAnnounced[i] = true
            approachAnnounced[i] = true
            onEvent(NavEvent.AtTurn(step, d))
        } else if (d <= APPROACH_M && !approachAnnounced[i]) {
            approachAnnounced[i] = true
            onEvent(NavEvent.ApproachingTurn(step, d))
        }
    }

    companion object {
        const val STALE_MS = 10_000L
        const val GPS_LOST_MS = 10_000L

        /** GPS-lost threshold for a request expected to deliver a fix every [expectedIntervalMs]. */
        fun gpsLostAfterMs(expectedIntervalMs: Long): Long = maxOf(GPS_LOST_MS, expectedIntervalMs * 2 + 2_000L)
        const val OFF_ROUTE_FIXES = 3
        const val U_TURN_FIXES = 3

        /** P11: alternatives kept besides the active route (OSRM gives at most 3 routes). */
        const val MAX_ALTERNATIVES = 2

        /** P11: a fix within this of an alternative counts as following it... */
        const val ALT_NEAR_M = 20.0

        /** ...only while it is more than this from the active route... */
        const val ALT_LEAVE_M = 40.0

        /** ...for this many usable fixes in a row on the same alternative. */
        const val ALT_FIXES = 3
        /** P11: at least this long between reroute requests (doubled after each failure, up to the max). */
        const val REROUTE_MIN_INTERVAL_MS = 8_000L
        const val REROUTE_MAX_INTERVAL_MS = 60_000L
        const val MAX_REROUTES = 3

        /** P11: the GPS course is sent as a bearing hint only at or above this speed. */
        const val HINT_MIN_SPEED_MPS = 3f

        /** P11 wrong way: a course this far from the route's direction, for [WRONG_WAY_FIXES] fixes in a row. */
        const val WRONG_WAY_DEG = 135.0
        const val WRONG_WAY_FIXES = 3

        /** P11: a course within this of the route's direction clears the wrong way. */
        const val ALONG_DEG = 45.0

        /** Spec section 8: turn at 200 m (Tick) and at 50 m (DoubleClick). */
        const val APPROACH_M = 200.0
        const val AT_TURN_M = 50.0

        /** Window behind the last match (jitter, U-turn detection). */
        const val LOOKBACK_M = 60.0

        /** A match this far behind the progress is jitter, not a U-turn candidate. */
        const val JITTER_M = 10.0
        const val BEHIND_PENALTY_M = 5.0
        const val FULL_TIE_M = 3.0
        const val PASSED_M = 2.0
        const val SLACK_M = 40.0
        const val END_EPSILON_M = 0.5

        const val STATIONARY_MPS = 0.5
        const val SPEED_ALPHA = 0.3
        const val MIN_SPEED_RATIO = 0.5
        const val MAX_SPEED_RATIO = 2.0

        /** Maneuvers that produce no turn event. */
        val SILENT_MANEUVERS = setOf(Maneuver.DEPART, Maneuver.STRAIGHT, Maneuver.ARRIVE)
    }
}
