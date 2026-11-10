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

    @Test fun ambientIsBalancedEveryTenSeconds() =
        assertEquals(RequestSpec(false, 10_000L, 5f), LocationProfiles.requestSpec(LocationProfile.AMBIENT))

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
