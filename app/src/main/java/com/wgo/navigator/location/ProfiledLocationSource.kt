package com.wgo.navigator.location

import com.wgo.navigator.core.Fix
import com.wgo.navigator.core.LocationProfile
import com.wgo.navigator.core.LocationSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach

/**
 * Shared profile logic for the real sources: the current [RequestSpec] is derived from the profile, the navigation
 * hint and the speed band; whenever it changes the open request is replaced (flatMapLatest), and when it becomes
 * null (IDLE/ORIGIN) or the collector goes away (app stopped, screen off) the request is removed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class ProfiledLocationSource : LocationSource {
    private data class Hint(val distanceToManeuverM: Double?, val onLongStraight: Boolean)

    private val profile = MutableStateFlow(LocationProfile.IDLE)
    private val hint = MutableStateFlow(Hint(null, false))
    private val fast = MutableStateFlow(false)

    /** Whether location permission is granted right now (checked before each request). */
    protected abstract fun permitted(): Boolean

    /** One continuous request with [spec]; removed when the returned flow's collection ends. */
    protected abstract fun updates(spec: RequestSpec): Flow<Fix>

    override fun setProfile(profile: LocationProfile) {
        this.profile.value = profile
    }

    override fun setNavigationHint(distanceToManeuverM: Double?, onLongStraight: Boolean) {
        hint.value = Hint(distanceToManeuverM, onLongStraight)
    }

    override val fixes: Flow<Fix> =
        combine(profile, hint, fast) { p, h, f -> LocationProfiles.requestSpec(p, h.distanceToManeuverM, h.onLongStraight, f) }
            .distinctUntilChanged()
            .flatMapLatest { spec -> if (spec == null || !permitted()) emptyFlow() else updates(spec) }
            .onEach { fast.value = LocationProfiles.nextFast(fast.value, it.speedMps) }
}
