package com.wgo.navigator.location

import com.wgo.navigator.core.Fix
import com.wgo.navigator.core.LocationProfile
import com.wgo.navigator.core.fix
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationProfilesTest {
    @Test fun idleAndOriginKeepNoRequest() {
        assertNull(LocationProfiles.requestSpec(LocationProfile.IDLE))
        assertNull(LocationProfiles.requestSpec(LocationProfile.ORIGIN, distanceToManeuverM = 10.0, fast = true))
    }

    @Test fun navigatingAdaptsItsInterval() {
        val base = LocationProfiles.requestSpec(LocationProfile.NAVIGATING)!!
        assertEquals(RequestSpec(true, 2_000L, 0f), base)
        assertEquals(1_000L, LocationProfiles.requestSpec(LocationProfile.NAVIGATING, distanceToManeuverM = 79.0)!!.intervalMs)
        assertEquals(2_000L, LocationProfiles.requestSpec(LocationProfile.NAVIGATING, distanceToManeuverM = 80.0)!!.intervalMs)
        assertEquals(3_000L, LocationProfiles.requestSpec(LocationProfile.NAVIGATING, 900.0, onLongStraight = true, fast = true)!!.intervalMs)
        // fast but not on a long straight, or a long straight but slow: base 2 s
        assertEquals(2_000L, LocationProfiles.requestSpec(LocationProfile.NAVIGATING, 900.0, onLongStraight = false, fast = true)!!.intervalMs)
        assertEquals(2_000L, LocationProfiles.requestSpec(LocationProfile.NAVIGATING, 900.0, onLongStraight = true, fast = false)!!.intervalMs)
        // a close maneuver wins over speed
        assertEquals(1_000L, LocationProfiles.requestSpec(LocationProfile.NAVIGATING, 50.0, onLongStraight = true, fast = true)!!.intervalMs)
    }

    @Test fun ambientKeepsHighAccuracyWithTheAdaptiveInterval() {
        // Unknown distance/speed: the normal 2 s.
        assertEquals(RequestSpec(true, 2_000L, 0f), LocationProfiles.requestSpec(LocationProfile.AMBIENT))
        assertEquals(10_000L, LocationProfiles.requestSpec(LocationProfile.AMBIENT, 900.0, speedMps = 1.4f)!!.intervalMs)
    }

    @Test fun adaptiveIntervalFollowsTheFormula() {
        val f = LocationProfiles::adaptiveIntervalMs
        // Inside 80 m of a maneuver: 1 s, whatever the speed.
        assertEquals(1_000L, f(79.0, 10f))
        assertEquals(1_000L, f(10.0, Float.NaN))
        // Unknown distance or speed: the fixed 2 s.
        assertEquals(2_000L, f(null, 10f))
        assertEquals(2_000L, f(500.0, Float.NaN))
        // Standing still: the longest step.
        assertEquals(10_000L, f(500.0, 0f))
        // Driving 10 m/s: (d - 250) / 10 / 2 s, clamped to 2-10 s and rounded down to a step.
        assertEquals(10_000L, f(1_000.0, 10f)) // 37.5 s -> 10
        assertEquals(10_000L, f(450.0, 10f)) // 10.0 s
        assertEquals(8_000L, f(440.0, 10f)) // 9.5 s -> 8
        assertEquals(6_000L, f(380.0, 10f)) // 6.5 s -> 6
        assertEquals(4_000L, f(340.0, 10f)) // 4.5 s -> 4
        assertEquals(3_000L, f(310.0, 10f)) // 3.0 s
        assertEquals(2_000L, f(290.0, 10f)) // 2.0 s
        assertEquals(2_000L, f(200.0, 10f)) // negative -> 2 s floor
        // Walking 1.4 m/s: 10 s until about 280 m.
        assertEquals(10_000L, f(300.0, 1.4f)) // 17.9 s -> 10
        assertEquals(6_000L, f(270.0, 1.4f)) // 7.1 s -> 6
        // Never longer than the time to reach the lead point, halved (so >= 2 fixes before the 250 m mark).
        for (d in 251..2_000 step 7) for (v in listOf(1.4f, 5f, 10f, 20f)) {
            val ms = f(d.toDouble(), v)
            assertTrue("d=$d v=$v ms=$ms", ms in 2_000L..10_000L)
            assertTrue("d=$d v=$v ms=$ms", ms <= maxOf(2_000.0, (d - 250.0) / v / 2 * 1000))
        }
    }

    /**
     * Same drive under both policies (screen off the whole time): a Pasto-like route with a maneuver every 120-600 m,
     * at 36 km/h and at 5 km/h. The adaptive policy must need clearly fewer fixes per minute than the fixed 2 s one.
     */
    @Test fun adaptivePolicyNeedsFewerFixesThanTheFixedOne() {
        val city = listOf(180.0, 420.0, 540.0, 1_140.0, 1_300.0, 1_900.0, 2_020.0, 2_600.0, 3_000.0, 3_600.0)
        val avenue = listOf(1_200.0, 2_500.0, 3_600.0) // long straights between maneuvers
        fun perMinute(maneuvers: List<Double>, speed: Float, policy: (Double) -> Long): Double {
            var t = 0.0
            var pos = 0.0
            var fixes = 0
            while (pos < maneuvers.last()) {
                val ms = policy(maneuvers.first { it > pos } - pos)
                fixes++
                t += ms / 1000.0
                pos += speed * ms / 1000.0
            }
            return fixes / (t / 60.0)
        }
        for ((name, route) in listOf("city" to city, "avenue" to avenue)) for (speed in listOf(10f, 1.39f)) {
            val adaptive = perMinute(route, speed) { LocationProfiles.adaptiveIntervalMs(it, speed) }
            val fixed = perMinute(route, speed) { 2_000L }
            val screenOn = perMinute(route, speed) { LocationProfiles.requestSpec(LocationProfile.NAVIGATING, it)!!.intervalMs }
            println(
                "route=$name speed=$speed m/s fixes/min adaptive=${"%.1f".format(adaptive)} " +
                    "fixed2s=${"%.1f".format(fixed)} screenOnP5D=${"%.1f".format(screenOn)}"
            )
            assertTrue("$name $speed adaptive=$adaptive fixed=$fixed", adaptive < fixed)
            assertTrue("$name $speed adaptive=$adaptive screenOn=$screenOn", adaptive < screenOn)
            // The big saving is on long straights (avenue); with a turn every few hundred metres the 250 m lead and the
            // 1 s window near each maneuver keep the rate close to the fixed policy (reported, not hidden).
            if (name == "avenue") assertTrue("$name $speed adaptive=$adaptive", adaptive < fixed * 0.75)
        }
    }

    @Test fun speedBandHasHysteresis() {
        assertTrue(LocationProfiles.nextFast(false, 6.0f)) // 21.6 km/h
        assertTrue(LocationProfiles.nextFast(true, 5.2f)) // 18.7 km/h: still fast
        assertFalse(LocationProfiles.nextFast(true, 4.9f)) // 17.6 km/h
        assertFalse(LocationProfiles.nextFast(false, 5.2f))
        assertTrue(LocationProfiles.nextFast(true, Float.NaN))
    }

    /** Records the requests the shared profile logic opens and closes. */
    private class RecordingSource : ProfiledLocationSource() {
        val opened = mutableListOf<RequestSpec>()
        var closed = 0
        var sink: ((Fix) -> Unit)? = null
        override fun permitted() = true
        override fun updates(spec: RequestSpec): Flow<Fix> = callbackFlow {
            opened += spec
            sink = { trySend(it) }
            awaitClose { closed++; sink = null }
        }
        override suspend fun lastKnown(): Fix? = null
        override suspend fun once(timeoutMs: Long) = throw UnsupportedOperationException()
    }

    /** Polls [condition] for up to 3 s (the first flow cancellations in a cold test JVM can take > 50 ms). */
    private suspend fun awaitUntil(condition: () -> Boolean) {
        repeat(300) {
            if (condition()) return
            delay(10)
        }
        throw AssertionError("condition not met within 3 s")
    }

    @Test fun requestsFollowProfileHintAndCollection() = runBlocking {
        val source = RecordingSource()
        val received = mutableListOf<Fix>()
        val job = launch { source.fixes.collect { received += it } }
        delay(50)
        assertEquals("IDLE opens nothing", 0, source.opened.size)

        source.setProfile(LocationProfile.ORIGIN)
        delay(50)
        assertEquals("ORIGIN opens nothing", 0, source.opened.size)

        source.setProfile(LocationProfile.NAVIGATING)
        awaitUntil { source.opened.size == 1 && source.sink != null }
        assertEquals(listOf(RequestSpec(true, 2_000L, 0f)), source.opened)
        source.sink!!(fix(speedMps = 1f))
        awaitUntil { received.size == 1 }

        source.setNavigationHint(distanceToManeuverM = 40.0, onLongStraight = false)
        awaitUntil { source.opened.size == 2 }
        assertEquals(1_000L, source.opened.last().intervalMs)
        assertEquals("the old request was replaced", 1, source.closed)

        source.setProfile(LocationProfile.IDLE)
        awaitUntil { source.closed == 2 }
        delay(100)
        assertEquals("IDLE opens nothing new", 2, source.opened.size)

        source.setProfile(LocationProfile.NAVIGATING)
        awaitUntil { source.opened.size == 3 }
        job.cancel()
        job.join()
        assertEquals("cancelling the collector removes the request", 3, source.closed)
    }
}
