package com.wgo.navigator.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.wgo.navigator.Language
import com.wgo.navigator.R
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.stringFor

/**
 * The foreground-service notification of a trip: one low-importance "Navigation" channel (silent, no badge), an ongoing
 * notification in the navigation category whose touch returns to the app exactly like the launcher icon does.
 */
class NavigationNotification(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    /** Creates (or renames, when the language changed) the channel. Cheap and idempotent. */
    fun ensureChannel(language: Language) {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(stringFor(StringKey.NOTIF_CHANNEL_NAV, language))
                .setDescription(stringFor(StringKey.NOTIF_CHANNEL_NAV_DESC, language))
                .setShowBadge(false)
                .setVibrationEnabled(false)
                .setSound(null, null)
                .build()
        )
    }

    /** The app's launch intent: brings the existing task (and its live trip) to the front instead of a new one. */
    fun touchIntent(): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent().setPackage(context.packageName)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(
            context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun builder(title: String, status: String, language: Language): NotificationCompat.Builder {
        ensureChannel(language)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wgo_nav)
            .setContentTitle(title.ifBlank { stringFor(StringKey.NOTIF_NAVIGATING, language) })
            .setContentText(status)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(touchIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    }

    fun build(title: String, status: String, language: Language): Notification = builder(title, status, language).build()

    fun cancel() = manager.cancel(NOTIFICATION_ID)

    companion object {
        const val CHANNEL_ID = "navigation"
        const val NOTIFICATION_ID = 1001
    }
}
