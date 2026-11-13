package com.wgo.navigator.location

import com.wgo.navigator.TravelMode
import com.wgo.navigator.core.LocationProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs the debug replay source (src/debug, visible to debug unit tests) through the engine filters. */
class ReplayFilterTest {
    private var now = 1_000_000_000_000L
    private fun replay(accuracy: Float = 6f, ageMs: Long = 0L) = ReplayLocationSource(
        ReplayLocationSource.PastoBlocks, speedKmh = 36.0, intervalMs = 1L, accuracyM = accuracy, ageMs = ageMs,
        nowNanos = { now }, wallMillis = { 0L }
    )

    @Test fun idleReplayEmitsNothing() = runBlocking {
        val source = replay()
        var count = 0
        val job = launch { source.fixes.collect { count++ } }
        delay(100)
        job.cancel()
        assertEquals(0, count)
    }

    @Test fun navigatingReplayMovesAlongThePolyline() = runBlocking {
        val source = replay().apply { setProfile(LocationProfile.NAVIGATING) }
        val fixes = source.fixes.take(5).toList()
        assertEquals(5, fixes.size)
        // 10 m/s eastward on the first block: longitude grows, bearing ~90 degrees, speed 10 m/s
        assertTrue(fixes.zipWithNext().all { (a, b) -> b.lon > a.lon })
        assertEquals(90f, fixes.first().bearingDeg, 1f)
        assertEquals(10f, fixes.first().speedMps, 0.01f)
        fixes.forEach { assertEquals(FixQuality.Good, FixFilter.classify(it, TravelMode.Walk, now)) }
    }

    @Test fun coarseReplayIsSearchingWhenWalkingButUsableWhenDriving() = runBlocking {
        val fix = replay(accuracy = 70f).apply { setProfile(LocationProfile.NAVIGATING) }.fixes.first()
        assertEquals(FixQuality.Inaccurate, FixFilter.classify(fix, TravelMode.Walk, now))
        assertEquals(FixQuality.Good, FixFilter.classify(fix, TravelMode.Drive, now))
    }

    @Test fun backDatedReplayIsStale() = runBlocking {
        val fix = replay(ageMs = 12_000L).apply { setProfile(LocationProfile.NAVIGATING) }.fixes.first()
        assertEquals(FixQuality.Stale, FixFilter.classify(fix, TravelMode.Drive, now))
    }
}
