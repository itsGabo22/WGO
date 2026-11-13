package com.wgo.navigator.location

import com.wgo.navigator.core.Fix
import com.wgo.navigator.core.LocationProfile
import com.wgo.navigator.core.LocationSource
import com.wgo.navigator.core.Outcome
import com.wgo.navigator.ui.map.GeoCoord
import com.wgo.navigator.ui.map.RadarProjection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/**
 * Debug-only [LocationSource] that drives along [polyline] at [speedKmh], one fix every [intervalMs], with a fixed
 * [accuracyM]. Like the real sources it emits only while the profile asks for continuous updates and somebody
 * collects. [nowNanos] is the elapsed-realtime clock (injectable for unit tests); [ageMs] back-dates every fix to
 * exercise the stale filter. The adb-driven alternative for the real sources is tools/sim_drive.ps1.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReplayLocationSource(
    private val polyline: List<GeoCoord>,
    private val speedKmh: Double = 30.0,
    private val intervalMs: Long = 1_000L,
    private val accuracyM: Float = 6f,
    private val ageMs: Long = 0L,
    private val nowNanos: () -> Long = { System.nanoTime() },
    private val wallMillis: () -> Long = { System.currentTimeMillis() }
) : LocationSource {
    init {
        require(polyline.size >= 2) { "a replay needs at least two points" }
    }

    private val profile = MutableStateFlow(LocationProfile.IDLE)
    private var last: Fix? = null

    override fun setProfile(profile: LocationProfile) {
        this.profile.value = profile
    }

    override val fixes: Flow<Fix> = profile.flatMapLatest { p ->
        if (LocationProfiles.requestSpec(p) == null) emptyFlow() else flow {
            val speedMps = speedKmh / 3.6
            var travelled = 0.0
            val total = polyline.zipWithNext { a, b -> RadarProjection.distanceMeters(a, b) }.sum()
            while (travelled <= total) {
                emit(fixAt(travelled, speedMps.toFloat()).also { last = it })
                delay(intervalMs)
                travelled += speedMps * intervalMs / 1000.0
            }
        }
    }

    private fun fixAt(distanceM: Double, speedMps: Float): Fix {
        var remaining = distanceM
        for (i in 0 until polyline.size - 1) {
            val a = polyline[i]
            val b = polyline[i + 1]
            val segment = RadarProjection.distanceMeters(a, b)
            if (remaining <= segment || i == polyline.size - 2) {
                val bearing = RadarProjection.bearingBetween(a, b)
                val p = RadarProjection.destination(a, bearing, remaining.coerceAtMost(segment))
                return Fix(p.lat, p.lon, accuracyM, speedMps, bearing.toFloat(), wallMillis() - ageMs, nowNanos() - ageMs * 1_000_000L)
            }
            remaining -= segment
        }
        error("unreachable")
    }

    override suspend fun lastKnown(): Fix? = last

    override suspend fun once(timeoutMs: Long): Outcome<Fix> = Outcome.Ok(last ?: fixAt(0.0, 0f))

    companion object {
        /** Around Plaza de Nariño, Pasto: two blocks with one right turn. */
        val PastoBlocks = listOf(
            GeoCoord(1.2120, -77.2830),
            GeoCoord(1.2120, -77.2808),
            GeoCoord(1.2142, -77.2808)
        )
    }
}
