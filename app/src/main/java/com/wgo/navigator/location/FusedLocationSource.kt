package com.wgo.navigator.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import com.wgo.navigator.core.Fix
import com.wgo.navigator.core.Outcome
import com.wgo.navigator.core.WgoFailure
import com.wgo.navigator.metrics.WgoMetrics
import com.wgo.navigator.metrics.WgoMetrics.Counter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * [LocationSource] over Google Play services' FusedLocationProviderClient. A continuous request exists only while
 * [fixes] is collected AND the profile needs one; changing the profile swaps the request. Permission and provider
 * problems never throw: the flow just stays silent and [once] returns a [WgoFailure].
 */
@SuppressLint("MissingPermission") // every call is preceded by hasLocationPermission() and guarded by SecurityException
class FusedLocationSource(context: Context) : ProfiledLocationSource() {
    private val app = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(app)

    override fun permitted() = app.hasLocationPermission()

    override fun updates(spec: RequestSpec): Flow<Fix> = callbackFlow {
        val request = LocationRequest.Builder(
            if (spec.highAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            spec.intervalMs
        )
            .setMinUpdateIntervalMillis(spec.intervalMs)
            .setMinUpdateDistanceMeters(spec.minDistanceM)
            .setMaxUpdateDelayMillis(0) // no batching: navigation needs every fix as it comes
            // The builder defaults to waiting for an accurate first fix; the radar shows "searching" for coarse ones.
            .setWaitForAccurateLocation(false)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    WgoMetrics.inc(Counter.LOCATION_FIX)
                    trySend(location.toFix())
                }
            }
        }
        val started = try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            WgoMetrics.inc(Counter.LOCATION_REQ_START)
            true
        } catch (e: SecurityException) {
            false
        }
        if (!started) close()
        awaitClose {
            if (started) {
                client.removeLocationUpdates(callback)
                WgoMetrics.inc(Counter.LOCATION_REQ_STOP)
            }
        }
    }

    override suspend fun lastKnown(): Fix? {
        if (!app.hasLocationPermission()) return null
        return try {
            client.lastLocation.awaitOrNull()?.toFix()
        } catch (e: SecurityException) {
            null
        }
    }

    override suspend fun once(timeoutMs: Long): Outcome<Fix> {
        if (!app.hasLocationPermission()) return Outcome.Err(WgoFailure.NoPermission)
        if (!app.locationEnabled()) return Outcome.Err(WgoFailure.Unavailable)
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(LocationProfiles.ORIGIN_MAX_AGE_MS)
            .setDurationMillis(timeoutMs)
            .build()
        val token = CancellationTokenSource()
        WgoMetrics.inc(Counter.LOCATION_REQ_START)
        return try {
            val location = withTimeoutOrNull(timeoutMs) { client.getCurrentLocation(request, token.token).awaitOrNull() }
            if (location == null) {
                Outcome.Err(WgoFailure.Timeout)
            } else {
                WgoMetrics.inc(Counter.LOCATION_FIX)
                Outcome.Ok(location.toFix())
            }
        } catch (e: SecurityException) {
            Outcome.Err(WgoFailure.NoPermission)
        } finally {
            token.cancel()
            WgoMetrics.inc(Counter.LOCATION_REQ_STOP)
        }
    }
}

/** Awaits a Play services task; a failed or cancelled task yields null instead of throwing. */
internal suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (cont.isActive) cont.resume(if (task.isSuccessful) task.result else null)
    }
}
