package com.wgo.navigator.core

import com.wgo.navigator.Language
import com.wgo.navigator.TravelMode
import com.wgo.navigator.ui.map.GeoCoord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOf

/** Location fake driven by the test: [emit] pushes fixes, [profiles] records every profile change. */
class FakeLocationSource(
    var last: Fix? = null,
    var onceResult: Outcome<Fix> = Outcome.Err(WgoFailure.Unavailable)
) : LocationSource {
    private val flow = MutableSharedFlow<Fix>(extraBufferCapacity = 64)
    val profiles = mutableListOf<LocationProfile>()

    override val fixes: Flow<Fix> = flow.asSharedFlow()
    override suspend fun lastKnown(): Fix? = last
    override suspend fun once(timeoutMs: Long): Outcome<Fix> = onceResult
    override fun setProfile(profile: LocationProfile) { profiles += profile }

    fun emit(fix: Fix) { check(flow.tryEmit(fix)) }
}

/** Voice fake: replays [events] for every session. */
class FakeVoiceInput(private val events: List<VoiceEvent> = listOf(VoiceEvent.Failed(WgoFailure.Unavailable))) : VoiceInput {
    var cancelled = 0
    override fun listen(language: Language): Flow<VoiceEvent> = flowOf(*events.toTypedArray())
    override fun cancel() { cancelled++ }
}

/** Interpreter fake: a fixed answer (default: nothing understood). */
class FakeIntentInterpreter(private val answer: Outcome<DestinationIntent> = Outcome.Err(WgoFailure.NotFound)) : IntentInterpreter {
    override suspend fun interpret(transcript: String, language: Language) = answer
}

/** Geocoder fake: a fixed answer (default: offline). */
class FakeGeocoder(private val answer: Outcome<List<Place>> = Outcome.Err(WgoFailure.Offline)) : Geocoder {
    override suspend fun search(query: String, near: GeoCoord?, limit: Int) = answer
}

/** Router fake: a fixed answer (default: server error). */
class FakeRouter(private val answer: Outcome<Route> = Outcome.Err(WgoFailure.Server(503))) : Router {
    override suspend fun route(from: GeoCoord, to: GeoCoord, mode: TravelMode) = answer
}

fun fix(
    lat: Double = 1.2136, lon: Double = -77.2811, accuracyM: Float = 8f, speedMps: Float = 0f,
    bearingDeg: Float = Float.NaN, timeMillis: Long = 0L, elapsedNanos: Long = 0L
) = Fix(lat, lon, accuracyM, speedMps, bearingDeg, timeMillis, elapsedNanos)
