package com.wgo.navigator.location

import com.wgo.navigator.TravelMode
import com.wgo.navigator.core.fix
import org.junit.Assert.assertEquals
import org.junit.Test

class FixFilterTest {
    private val now = 100_000_000_000L // 100 s on the elapsed-realtime clock
    private fun agedMs(ms: Long) = now - ms * 1_000_000L

    @Test fun freshAccurateFixIsGood() =
        assertEquals(FixQuality.Good, FixFilter.classify(fix(accuracyM = 12f, elapsedNanos = agedMs(500)), TravelMode.Walk, now))

    @Test fun olderThanTenSecondsIsStale() {
        assertEquals(FixQuality.Good, FixFilter.classify(fix(elapsedNanos = agedMs(10_000)), TravelMode.Walk, now))
        assertEquals(FixQuality.Stale, FixFilter.classify(fix(elapsedNanos = agedMs(10_001)), TravelMode.Walk, now))
        // stale wins even when accurate
        assertEquals(FixQuality.Stale, FixFilter.classify(fix(accuracyM = 3f, elapsedNanos = agedMs(60_000)), TravelMode.Drive, now))
    }

    @Test fun accuracyLimitDependsOnMode() {
        assertEquals(FixQuality.Good, FixFilter.classify(fix(accuracyM = 50f, elapsedNanos = now), TravelMode.Walk, now))
        assertEquals(FixQuality.Inaccurate, FixFilter.classify(fix(accuracyM = 51f, elapsedNanos = now), TravelMode.Walk, now))
        assertEquals(FixQuality.Good, FixFilter.classify(fix(accuracyM = 80f, elapsedNanos = now), TravelMode.Drive, now))
        assertEquals(FixQuality.Inaccurate, FixFilter.classify(fix(accuracyM = 81f, elapsedNanos = now), TravelMode.Drive, now))
        // no mode yet: the stricter walking limit
        assertEquals(FixQuality.Inaccurate, FixFilter.classify(fix(accuracyM = 60f, elapsedNanos = now), null, now))
    }

    @Test fun unknownAccuracyIsNotTrusted() =
        assertEquals(FixQuality.Inaccurate, FixFilter.classify(fix(accuracyM = Float.NaN, elapsedNanos = now), TravelMode.Walk, now))
}
