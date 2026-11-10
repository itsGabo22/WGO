package com.wgo.navigator.core.permissions

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/** Runtime permissions WGO asks for, each lazily, the first time a feature needs it. */
enum class WgoPermission(val manifest: String) {
    /** Fine location (API 31+ lets the user downgrade to approximate in the same dialog). */
    LOCATION(Manifest.permission.ACCESS_FINE_LOCATION),
    MICROPHONE(Manifest.permission.RECORD_AUDIO),

    /**
     * The trip notification / ongoing activity (POST_NOTIFICATIONS, API 33+; written as a literal because the constant
     * is API 33). Below API 33 it does not exist and counts as granted. A denial never blocks navigation.
     */
    NOTIFICATIONS("android.permission.POST_NOTIFICATIONS")
}

enum class PermissionStatus {
    Granted,

    /** Never asked: the system dialog will show. */
    NotAsked,

    /** Denied, but the system dialog can still be shown again (retry chip). */
    Denied,

    /** "Don't ask again" (on API 30+: denied twice): only the app's settings page can grant it now. */
    PermanentlyDenied
}

/**
 * Pure decision used by [PermissionBroker]. On API 30+ a permanent denial always follows a state where the system
 * asked for a rationale, so "asked, no rationale now, but a rationale was seen before" means "don't ask again";
 * "asked, never a rationale" is a first denial or a dismissed dialog, which can still be retried.
 */
fun permissionStatus(granted: Boolean, askedBefore: Boolean, showRationale: Boolean, sawRationale: Boolean): PermissionStatus =
    when {
        granted -> PermissionStatus.Granted
        !askedBefore -> PermissionStatus.NotAsked
        showRationale -> PermissionStatus.Denied
        sawRationale -> PermissionStatus.PermanentlyDenied
        else -> PermissionStatus.Denied
    }

/**
 * One reusable, lazy permission flow. Features call [ensure] when they first need a permission; the broker asks the
 * Activity (which owns the `RequestPermission` launcher and shows the system dialog) through [requests] and resumes
 * with the outcome. It never touches an Activity itself, so it can be held by ViewModels. The Activity holds requests
 * back while the intro plays.
 */
class PermissionBroker(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("wgo_permissions", Context.MODE_PRIVATE)
    private val pending = HashMap<WgoPermission, CompletableDeferred<PermissionStatus>>()

    // Buffered so a request made while the Activity is stopped waits for it instead of being dropped.
    private val _requests = Channel<WgoPermission>(Channel.BUFFERED)

    /** Collected by the Activity, which launches the system dialog for each request. */
    val requests: Flow<WgoPermission> = _requests.receiveAsFlow()

    private val _status = MutableStateFlow(WgoPermission.entries.associateWith { initialStatus(it) })
    val status: StateFlow<Map<WgoPermission, PermissionStatus>> = _status.asStateFlow()

    fun isGranted(permission: WgoPermission): Boolean =
        (permission == WgoPermission.NOTIFICATIONS && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) ||
            ContextCompat.checkSelfPermission(app, permission.manifest) == PackageManager.PERMISSION_GRANTED

    /**
     * Returns [PermissionStatus.Granted] immediately when granted; otherwise asks (system dialog) unless the
     * permission is permanently denied, and returns the resulting status. Never throws.
     */
    suspend fun ensure(permission: WgoPermission): PermissionStatus {
        if (isGranted(permission)) return setStatus(permission, PermissionStatus.Granted)
        if (_status.value[permission] == PermissionStatus.PermanentlyDenied) return PermissionStatus.PermanentlyDenied
        val deferred = synchronized(pending) {
            pending[permission] ?: CompletableDeferred<PermissionStatus>().also {
                pending[permission] = it
                _requests.trySend(permission)
            }
        }
        return deferred.await()
    }

    /**
     * Called by the Activity with the dialog result and its current rationale flag. Two denials in a row with no
     * rationale also count as "don't ask again": that is what the system answers when the permission was fixed from
     * the settings (or its coarse half was), without showing any dialog; the settings page is then the only way.
     */
    fun onResult(permission: WgoPermission, granted: Boolean, showRationale: Boolean) {
        prefs.edit().putBoolean(askedKey(permission), true).apply()
        val bareDenials = if (granted || showRationale) 0 else prefs.getInt(bareDenialsKey(permission), 0) + 1
        prefs.edit().putInt(bareDenialsKey(permission), bareDenials).apply()
        if (bareDenials >= 2) prefs.edit().putBoolean(rationaleKey(permission), true).apply()
        val status = update(permission, granted || isGranted(permission), showRationale)
        synchronized(pending) { pending.remove(permission) }?.complete(status)
    }

    /** Called by the Activity when it starts: picks up grants/revocations made in the system settings. */
    fun refresh(permission: WgoPermission, showRationale: Boolean) {
        update(permission, isGranted(permission), showRationale)
    }

    private fun update(permission: WgoPermission, granted: Boolean, showRationale: Boolean): PermissionStatus {
        if (showRationale) prefs.edit().putBoolean(rationaleKey(permission), true).apply()
        if (granted) prefs.edit().remove(rationaleKey(permission)).remove(bareDenialsKey(permission)).apply()
        val status = permissionStatus(
            granted = granted,
            askedBefore = prefs.getBoolean(askedKey(permission), false),
            showRationale = showRationale,
            sawRationale = prefs.getBoolean(rationaleKey(permission), false)
        )
        return setStatus(permission, status)
    }

    private fun setStatus(permission: WgoPermission, status: PermissionStatus): PermissionStatus {
        _status.update { it + (permission to status) }
        return status
    }

    private fun initialStatus(permission: WgoPermission): PermissionStatus = permissionStatus(
        granted = isGranted(permission),
        askedBefore = prefs.getBoolean(askedKey(permission), false),
        showRationale = false,
        sawRationale = prefs.getBoolean(rationaleKey(permission), false)
    ).let { if (it == PermissionStatus.PermanentlyDenied) PermissionStatus.Denied else it } // refined by refresh()

    private fun askedKey(p: WgoPermission) = "asked_${p.name}"
    private fun rationaleKey(p: WgoPermission) = "rationale_${p.name}"
    private fun bareDenialsKey(p: WgoPermission) = "bare_denials_${p.name}"

    companion object {
        // Holds only the application context (taken in the constructor), which lives as long as the process.
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: PermissionBroker? = null

        /** Process-wide broker (holds only the application context). */
        fun get(context: Context): PermissionBroker =
            instance ?: synchronized(this) { instance ?: PermissionBroker(context).also { instance = it } }
    }
}
