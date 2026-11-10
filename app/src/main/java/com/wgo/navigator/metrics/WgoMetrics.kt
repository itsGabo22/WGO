package com.wgo.navigator.metrics

import android.app.Activity
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Window
import com.wgo.navigator.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray

/**
 * Debug-only counters for the resource budget. Every entry point is guarded by [BuildConfig.DEBUG], a compile-time
 * constant, so release builds pay one constant branch at most and R8 strips the bodies when minification is on.
 * Nothing here schedules frames: frames are counted from the window's FrameMetrics callback.
 */
object WgoMetrics {
    enum class Counter {
        LOCATION_FIX, LOCATION_REQ_START, LOCATION_REQ_STOP, RECOGNIZER_SESSION,
        AI_CALL, AI_COOLDOWN, REROUTE, MAP_INVALIDATE
    }

    enum class Net { GEOCODE, ROUTE, AI }

    const val TAG = "WGO_METRICS"
    private const val PERIOD_MS = 30_000L

    private val counters = AtomicLongArray(Counter.entries.size)
    private val netCount = AtomicLongArray(Net.entries.size)
    private val netBytes = AtomicLongArray(Net.entries.size)
    private val frames = ConcurrentHashMap<String, AtomicLong>()

    /** Name of the screen currently shown; frames are attributed to it. */
    @Volatile
    var screen: String = "-"

    fun inc(counter: Counter) {
        if (BuildConfig.DEBUG) counters.incrementAndGet(counter.ordinal)
    }

    fun net(kind: Net, bytes: Long) {
        if (!BuildConfig.DEBUG) return
        netCount.incrementAndGet(kind.ordinal)
        netBytes.addAndGet(kind.ordinal, bytes)
    }

    /** One-off debug event line (for example which voice strategy ran). Never pass secrets or transcripts here. */
    fun event(text: String) {
        if (!BuildConfig.DEBUG) return
        try {
            Log.i(TAG, "event $text")
        } catch (e: RuntimeException) {
            // JVM unit tests: android.util.Log is a stub there.
        }
    }

    fun get(counter: Counter): Long = counters.get(counter.ordinal)

    const val ROUTE_TAG = "WGO_ROUTE"

    /**
     * Debug only: dumps the active route polyline under [ROUTE_TAG], one `lat,lon` per line between `begin` and `end`
     * markers, so tools/sim_drive.ps1 -FromLogcat can replay the real route on the emulator. Coordinates of the route
     * only (no fix, no destination name).
     */
    fun route(points: List<com.wgo.navigator.ui.map.GeoCoord>) {
        if (!BuildConfig.DEBUG) return
        try {
            Log.i(ROUTE_TAG, "begin n=${points.size}")
            points.forEach { Log.i(ROUTE_TAG, String.format(java.util.Locale.US, "%.6f,%.6f", it.lat, it.lon)) }
            Log.i(ROUTE_TAG, "end")
        } catch (e: RuntimeException) {
            // JVM unit tests: android.util.Log is a stub there.
        }
    }

    private val engineNanos = AtomicLong()
    private val engineFixes = AtomicLong()

    /** Debug only: time spent in the navigation engine for one fix (reported as an average in [snapshot]). */
    fun engineFix(nanos: Long) {
        if (!BuildConfig.DEBUG) return
        engineNanos.addAndGet(nanos)
        engineFixes.incrementAndGet()
    }

    fun snapshot(): String = buildString {
        append("screen=").append(screen)
        append(" frames={")
        frames.entries.sortedBy { it.key }.joinTo(this, ",") { "${it.key}:${it.value.get()}" }
        append('}')
        Counter.entries.forEach { append(' ').append(it.name.lowercase()).append('=').append(counters.get(it.ordinal)) }
        append(" locActive=").append(get(Counter.LOCATION_REQ_START) - get(Counter.LOCATION_REQ_STOP))
        val fixes = engineFixes.get()
        append(" engine_fixes=").append(fixes)
        append(" engine_ns_avg=").append(if (fixes == 0L) 0 else engineNanos.get() / fixes)
        Net.entries.forEach {
            append(" net_").append(it.name.lowercase()).append('=').append(netCount.get(it.ordinal))
                .append('/').append(netBytes.get(it.ordinal)).append('B')
        }
    }

    private fun frame() {
        frames.getOrPut(screen) { AtomicLong() }.incrementAndGet()
    }

    /** Debug only: counts drawn frames of [activity]'s window and logs one [TAG] line every 30 s while [scope] lives. */
    fun install(activity: Activity, scope: CoroutineScope) {
        if (!BuildConfig.DEBUG) return
        val thread = HandlerThread("wgo-metrics").apply { start() }
        val listener = Window.OnFrameMetricsAvailableListener { _, _, _ -> frame() }
        activity.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper))
        scope.launch {
            try {
                while (isActive) {
                    delay(PERIOD_MS)
                    Log.i(TAG, snapshot())
                }
            } finally {
                activity.window.removeOnFrameMetricsAvailableListener(listener)
                thread.quitSafely()
            }
        }
    }
}
