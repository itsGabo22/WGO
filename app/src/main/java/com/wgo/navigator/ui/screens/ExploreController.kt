package com.wgo.navigator.ui.screens

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wgo.navigator.ui.map.ExploreBounds
import com.wgo.navigator.ui.map.GeoCoord
import com.wgo.navigator.ui.map.RadarProjection

/** The explore-mode timing rule (spec section 7), pure. */
object ExploreRules {
    const val AUTO_RETURN_MS = 10_000L
    const val RETURN_GLIDE_MS = 420

    /** Milliseconds until the map returns to following by itself; null while a finger is down (the timer is paused). */
    fun autoReturnInMs(lastTouchMs: Long, touching: Boolean, nowMs: Long): Long? =
        if (touching) null else (AUTO_RETURN_MS - (nowMs - lastTouchMs)).coerceAtLeast(0L)

    /** Shortest-way interpolation between two headings in degrees. */
    fun lerpBearing(from: Float, to: Float, f: Float): Float {
        val delta = ((to - from + 540f) % 360f) - 180f
        return (from + delta * f + 360f) % 360f
    }

    fun lerp(a: GeoCoord, b: GeoCoord, f: Float): GeoCoord =
        GeoCoord(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)
}

/**
 * UI-only state of explore mode (spec section 7): the view centre and the frozen heading while the user pans, the
 * finger state for the auto-return timer, and the glide back. Nothing here reaches the engine, the service or the
 * location request. Created per navigation screen, so it is dropped when the trip ends or the screen enters ambient.
 */
@Stable
class ExploreController(private val now: () -> Long) {
    /** The view centre while exploring; null while following the user. */
    var center by mutableStateOf<GeoCoord?>(null)
        private set
    var bearing by mutableFloatStateOf(0f)
        private set
    var touching by mutableStateOf(false)
        private set
    var lastTouchMs by mutableLongStateOf(0L)
        private set

    /** The glide back to the user: its start and progress 0..1 (null: not gliding). */
    var glideFrom by mutableStateOf<GeoCoord?>(null)
        private set
    private var glideFromBearing = 0f
    var glide by mutableFloatStateOf(0f)

    private var bounds: ExploreBounds? = null

    val exploring: Boolean get() = center != null

    /** A finger went down on (or left) the map. While exploring, any touch restarts the 10 s timer. */
    fun onTouch(down: Boolean) {
        touching = down
        lastTouchMs = now()
    }

    /**
     * A drag of ([dxPx], [dyPx]) on the map. The first one starts exploring from the user's place and heading and fixes
     * the pan clamp (the route still ahead plus the user, grown by 400 m).
     */
    fun pan(dxPx: Float, dyPx: Float, user: GeoCoord, userBearing: Float, route: List<GeoCoord>, zoom: Double, tileSize: Double) {
        val from = center ?: run {
            bearing = userBearing
            bounds = ExploreBounds.around(route + user)
            glideFrom = null
            user
        }
        val next = RadarProjection.panCenter(from, dxPx.toDouble(), dyPx.toDouble(), bearing.toDouble(), zoom, tileSize)
        center = bounds?.clamp(next) ?: next
        lastTouchMs = now()
    }

    /** Recenter (button, HUD tap or the timer): start the glide back; [ExploreController.finish] ends it. */
    fun startReturn() {
        val c = center ?: return
        if (glideFrom != null) return
        glideFrom = c
        glideFromBearing = bearing
        glide = 0f
    }

    /** Back to following (end of the glide, or at once with reduce motion). */
    fun finish() {
        center = null
        glideFrom = null
        bounds = null
        glide = 0f
    }

    /** Where the map is centred now (null: on the user). */
    fun viewCenter(user: GeoCoord): GeoCoord? {
        val from = glideFrom ?: return center
        return ExploreRules.lerp(from, user, glide)
    }

    fun viewBearing(userBearing: Float): Float? {
        if (center == null) return null
        return if (glideFrom != null) ExploreRules.lerpBearing(glideFromBearing, userBearing, glide) else bearing
    }
}
