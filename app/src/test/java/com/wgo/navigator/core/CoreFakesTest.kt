package com.wgo.navigator.core

import com.wgo.navigator.Language
import com.wgo.navigator.TravelMode
import com.wgo.navigator.ui.map.GeoCoord
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The default fakes fail with a typed failure instead of throwing, which is the contract for every service. */
class CoreFakesTest {
    private val pasto = GeoCoord(1.2136, -77.2811)

    @Test fun defaultFakesReturnTypedFailures() = runBlocking {
        assertEquals(IntentKind.Unknown, FakeIntentInterpreter().interpret("x", Language.ES).kind)
        assertEquals(Outcome.Err(WgoFailure.Offline), FakeGeocoder().search("x", pasto))
        assertEquals(Outcome.Err(WgoFailure.Server(503)), FakeRouter().route(pasto, pasto, TravelMode.Walk))
        assertEquals(Outcome.Err(WgoFailure.Unavailable), FakeLocationSource().once(1000))
        assertNull(FakeLocationSource().lastKnown())
        assertEquals(listOf(VoiceEvent.Failed(WgoFailure.Unavailable)), FakeVoiceInput().listen(Language.EN).toList())
    }

    @Test fun outcomeMapKeepsFailures() {
        val ok: Outcome<Int> = Outcome.Ok(2)
        val err: Outcome<Int> = Outcome.Err(WgoFailure.Timeout)
        assertEquals(Outcome.Ok(4), ok.map { it * 2 })
        assertEquals(err, err.map { it * 2 })
        assertEquals(2, ok.valueOrNull())
        assertNull(err.valueOrNull())
    }

    @Test fun locationFakeRecordsProfiles() {
        val source = FakeLocationSource()
        source.setProfile(LocationProfile.NAVIGATING)
        source.setProfile(LocationProfile.IDLE)
        assertEquals(listOf(LocationProfile.NAVIGATING, LocationProfile.IDLE), source.profiles)
    }
}
