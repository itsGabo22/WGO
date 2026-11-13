package com.wgo.navigator.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import com.wgo.navigator.metrics.WgoMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service (type `location`) that keeps a trip alive with the screen off or the app in the background. It
 * holds no navigation logic: [NavigationHost] runs the trip; this only puts the process in the foreground with the
 * trip's notification, and goes away (notification included) as soon as the trip ends.
 *
 * Started by the host from the foreground app when the route is ready; calls startForeground at once in
 * [onStartCommand] (well inside the 5 s deadline). Not sticky: after process death a trip never resumes by itself.
 * Swiping the app away from Recents ends the trip ([onTaskRemoved]).
 */
class NavigationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var host: NavigationHost
    private lateinit var notification: NavigationNotification
    private var foreground = false

    override fun onCreate() {
        super.onCreate()
        host = NavigationHost.get(this)
        notification = NavigationNotification(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foreground) {
            val started = try {
                ServiceCompat.startForeground(
                    this,
                    NavigationNotification.NOTIFICATION_ID,
                    notification.build(host.destinationName.value, "", host.language),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
                true
            } catch (e: RuntimeException) {
                // SecurityException (location permission gone) or a start the platform refused.
                WgoMetrics.event("nav service_foreground_failed=${e.javaClass.simpleName}")
                false
            }
            if (!started) {
                host.end(TripEnd.ServiceLost)
                stopSelf()
                return START_NOT_STICKY
            }
            foreground = true
            WgoMetrics.event("nav service foreground")
            host.onServiceStarted()
            // The trip ended (END, arrival, permission loss): leave the foreground and stop at once.
            scope.launch {
                host.active.first { !it }
                stopNow()
            }
            // The ongoing activity's status: only when the maneuver or the distance bucket changed, at most every 5 s.
            scope.launch {
                val throttle = OngoingStatus.Throttle()
                combine(host.navState, host.destinationName) { state, name -> state to name }.collect { (state, name) ->
                    if (!host.active.value) return@collect
                    val text = OngoingStatus.text(state, host.language)
                    if (throttle.shouldPost(text, SystemClock.elapsedRealtime())) {
                        notification.update(name, text, host.language)
                        WgoMetrics.event("nav ongoing_post n=${throttle.posts}")
                    }
                }
            }
        }
        if (!host.active.value) stopNow()
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiped away from Recents: the user closed the app, so the trip ends with it.
        host.end(TripEnd.TaskRemoved)
        stopNow()
        super.onTaskRemoved(rootIntent)
    }

    private fun stopNow() {
        if (foreground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foreground = false
        }
        notification.cancel()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        if (foreground) ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        host.onServiceStopped()
        WgoMetrics.event("nav service stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
