package com.wgo.navigator.debug

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.wgo.navigator.ui.map.GeoCoord
import com.wgo.navigator.ui.map.RadarProjection

/**
 * Debug builds only (this source set is not in benchmark or release): drives the FUSED provider along a polyline on a
 * real watch, where `adb emu geo fix` (tools/sim_drive.ps1) does not exist. Needs the mock-location app-op once:
 * `adb shell appops set com.wgo.navigator android:mock_location allow`.
 *
 * Extras: `points` = "lat,lon;lat,lon;..." (one point = hold that position, re-sent every interval), `speed_kmh`
 * (default 36), `interval_ms` (default 1000), `stop` (true = end the replay and leave mock mode). While a replay runs it
 * holds a partial wake lock so it keeps going with the screen off (this inflates battery numbers: test tool only); a
 * route replay stops by itself 30 s after its end. A one-point hold runs until `stop` (or 30 min at most).
 *
 * `adb shell am broadcast -a com.wgo.navigator.debug.MOCK_DRIVE -p com.wgo.navigator --es points "1.229,-77.2945"`
 */
class MockDriveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (intent.getBooleanExtra("stop", false)) {
            MockDrive.stop(app)
            return
        }
        val points = intent.getStringExtra("points")?.split(';')?.mapNotNull { pair ->
            val p = pair.split(',')
            if (p.size == 2) p[0].trim().toDoubleOrNull()?.let { lat -> p[1].trim().toDoubleOrNull()?.let { GeoCoord(lat, it) } } else null
        }.orEmpty()
        if (points.isEmpty()) return
        MockDrive.start(app, points, intent.getFloatExtra("speed_kmh", 36f).toDouble(), intent.getLongExtra("interval_ms", 1_000L))
    }
}

@SuppressLint("MissingPermission", "StaticFieldLeak") // debug tool; the app context only; mock mode needs the app-op
private object MockDrive {
    private const val TAG = "WGO_MOCK"

    /** A replay holds the destination this long after the end of the route, then stops itself. */
    private const val END_HOLD_MS = 30_000L
    private val handler = Handler(Looper.getMainLooper())
    private var tick: Runnable? = null
    private var wakeLock: PowerManager.WakeLock? = null

    fun start(context: Context, points: List<GeoCoord>, speedKmh: Double, intervalMs: Long) {
        stopLoop()
        val client = LocationServices.getFusedLocationProviderClient(context)
        client.setMockMode(true).addOnFailureListener { Log.w(TAG, "setMockMode failed: ${it.javaClass.simpleName}") }
        wakeLock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wgo:mockdrive").apply { acquire(30 * 60_000L) }
        val speed = speedKmh / 3.6
        val total = points.zipWithNext { a, b -> RadarProjection.distanceMeters(a, b) }.sum()
        var travelled = 0.0
        var heldMs = 0L
        Log.i(TAG, "start points=${points.size} m=${total.toInt()} speed_kmh=$speedKmh interval_ms=$intervalMs")
        val r = object : Runnable {
            override fun run() {
                val (p, bearing) = at(points, travelled)
                val loc = Location("fused").apply {
                    latitude = p.lat
                    longitude = p.lon
                    accuracy = 5f
                    altitude = 2527.0
                    this.speed = if (points.size > 1 && travelled < total) speed.toFloat() else 0f
                    this.bearing = bearing.toFloat()
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                }
                client.setMockLocation(loc)
                if (points.size > 1 && travelled >= total) {
                    // Hold the last point (a user standing at the destination) for a while, then stop by itself so the
                    // wake lock and mock mode never outlive the test.
                    heldMs += intervalMs
                    if (heldMs >= END_HOLD_MS) {
                        Log.i(TAG, "end of route: stopping")
                        stop(context)
                        return
                    }
                }
                travelled = minOf(total, travelled + speed * intervalMs / 1000.0)
                handler.postDelayed(this, intervalMs)
            }
        }
        tick = r
        handler.post(r)
    }

    fun stop(context: Context) {
        stopLoop()
        LocationServices.getFusedLocationProviderClient(context).setMockMode(false)
        Log.i(TAG, "stop")
    }

    private fun stopLoop() {
        tick?.let(handler::removeCallbacks)
        tick = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun at(points: List<GeoCoord>, distanceM: Double): Pair<GeoCoord, Double> {
        if (points.size == 1) return points[0] to 0.0
        var remaining = distanceM
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val seg = RadarProjection.distanceMeters(a, b)
            val bearing = RadarProjection.bearingBetween(a, b)
            if (remaining <= seg || i == points.size - 2) {
                return RadarProjection.destination(a, bearing, remaining.coerceAtMost(seg)) to bearing
            }
            remaining -= seg
        }
        return points.last() to 0.0
    }
}
