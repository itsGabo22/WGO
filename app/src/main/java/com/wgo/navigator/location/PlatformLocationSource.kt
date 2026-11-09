package com.wgo.navigator.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import com.wgo.navigator.core.Fix
import com.wgo.navigator.core.LocationProfile
import com.wgo.navigator.core.LocationSource
import com.wgo.navigator.core.Outcome
import com.wgo.navigator.core.WgoFailure
import com.wgo.navigator.metrics.WgoMetrics
import com.wgo.navigator.metrics.WgoMetrics.Counter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Fallback [LocationSource] over the platform LocationManager, for watches without Google Play services.
 * High accuracy uses the GPS and network providers together; balanced uses the network provider only.
 * Same contract as [FusedLocationSource]: a request exists only while collected and needed; nothing throws.
 */
@SuppressLint("MissingPermission") // every call is preceded by hasLocationPermission() and guarded by SecurityException
@OptIn(ExperimentalCoroutinesApi::class)
class PlatformLocationSource(context: Context) : LocationSource {
    private val app = context.applicationContext
    private val manager: LocationManager? = app.getSystemService(LocationManager::class.java)
    private val profile = MutableStateFlow(LocationProfile.IDLE)

    override fun setProfile(profile: LocationProfile) {
        this.profile.value = profile
    }

    override val fixes: Flow<Fix> = profile
        .map { LocationProfiles.requestSpec(it) }
        .distinctUntilChanged()
        .flatMapLatest { spec -> if (spec == null || !app.hasLocationPermission()) emptyFlow() else updates(spec) }

    private fun providers(highAccuracy: Boolean): List<String> {
        val lm = manager ?: return emptyList()
        val all = lm.allProviders
        return buildList {
            if (highAccuracy && LocationManager.GPS_PROVIDER in all && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (LocationManager.NETWORK_PROVIDER in all && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
        }
    }

    private fun updates(spec: RequestSpec): Flow<Fix> = callbackFlow {
        val lm = manager
        val providers = providers(spec.highAccuracy)
        val listener = LocationListener { location ->
            WgoMetrics.inc(Counter.LOCATION_FIX)
            trySend(location.toFix())
        }
        val started = lm != null && providers.isNotEmpty() && try {
            providers.forEach { lm.requestLocationUpdates(it, spec.intervalMs, spec.minDistanceM, listener, Looper.getMainLooper()) }
            WgoMetrics.inc(Counter.LOCATION_REQ_START)
            true
        } catch (e: SecurityException) {
            lm.removeUpdates(listener)
            false
        }
        if (!started) close()
        awaitClose {
            if (started) {
                lm?.removeUpdates(listener)
                WgoMetrics.inc(Counter.LOCATION_REQ_STOP)
            }
        }
    }

    override suspend fun lastKnown(): Fix? {
        val lm = manager ?: return null
        if (!app.hasLocationPermission()) return null
        return try {
            lm.allProviders.mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.elapsedRealtimeNanos }?.toFix()
        } catch (e: SecurityException) {
            null
        }
    }

    override suspend fun once(timeoutMs: Long): Outcome<Fix> {
        val lm = manager ?: return Outcome.Err(WgoFailure.Unavailable)
        if (!app.hasLocationPermission()) return Outcome.Err(WgoFailure.NoPermission)
        if (!lm.isLocationEnabled) return Outcome.Err(WgoFailure.Unavailable)
        // A cached fix younger than 60 s is good enough for the origin.
        lastKnown()?.let { cached ->
            val ageMs = (SystemClock.elapsedRealtimeNanos() - cached.elapsedNanos) / 1_000_000
            if (ageMs in 0..LocationProfiles.ORIGIN_MAX_AGE_MS) return Outcome.Ok(cached)
        }
        val provider = providers(highAccuracy = true).firstOrNull() ?: return Outcome.Err(WgoFailure.Unavailable)
        val signal = CancellationSignal()
        WgoMetrics.inc(Counter.LOCATION_REQ_START)
        return try {
            val location = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    lm.getCurrentLocation(provider, signal, app.mainExecutor) { if (cont.isActive) cont.resume(it) }
                }
            }
            if (location == null) {
                Outcome.Err(WgoFailure.Timeout)
            } else {
                WgoMetrics.inc(Counter.LOCATION_FIX)
                Outcome.Ok(location.toFix())
            }
        } catch (e: SecurityException) {
            Outcome.Err(WgoFailure.NoPermission)
        } finally {
            signal.cancel()
            WgoMetrics.inc(Counter.LOCATION_REQ_STOP)
        }
    }
}
