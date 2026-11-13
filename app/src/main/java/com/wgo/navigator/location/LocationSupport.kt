package com.wgo.navigator.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.wgo.navigator.core.Fix
import com.wgo.navigator.core.LocationSource
import com.wgo.navigator.metrics.WgoMetrics

internal fun Location.toFix() = Fix(
    lat = latitude,
    lon = longitude,
    accuracyM = if (hasAccuracy()) accuracy else Float.NaN,
    speedMps = if (hasSpeed()) speed else Float.NaN,
    bearingDeg = if (hasBearing()) bearing else Float.NaN,
    timeMillis = time,
    elapsedNanos = elapsedRealtimeNanos
)

internal fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

internal fun Context.locationEnabled(): Boolean =
    getSystemService(LocationManager::class.java)?.isLocationEnabled == true

object LocationSources {
    /** Fused provider when Google Play services is usable, else the platform LocationManager (GPS + network). */
    fun create(context: Context): LocationSource {
        val app = context.applicationContext
        val gms = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(app) == ConnectionResult.SUCCESS
        WgoMetrics.event("location_impl=${if (gms) "fused" else "platform"}")
        return if (gms) FusedLocationSource(app) else PlatformLocationSource(app)
    }
}
