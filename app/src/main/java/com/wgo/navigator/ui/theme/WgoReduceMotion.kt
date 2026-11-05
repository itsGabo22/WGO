package com.wgo.navigator.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** True when the user turned animations off (Settings.Global.ANIMATOR_DURATION_SCALE == 0). */
val LocalWgoReduceMotion = compositionLocalOf { false }

/**
 * Shared reduce-motion helper: reads ANIMATOR_DURATION_SCALE and stays in sync if it changes.
 * [WgoTheme] provides the result through [LocalWgoReduceMotion]; pieces only read the local.
 */
@Composable
fun rememberWgoReduceMotion(): Boolean {
    val context = LocalContext.current
    var reduce by remember { mutableStateOf(readReduceMotion(context.contentResolver)) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = readReduceMotion(resolver)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduce
}

private fun readReduceMotion(resolver: android.content.ContentResolver): Boolean =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Transitions become a 120 ms cut when motion is reduced. */
fun <T> FiniteAnimationSpec<T>.orCut(reduceMotion: Boolean): FiniteAnimationSpec<T> =
    if (reduceMotion) tween(WgoMotion.SNAP, easing = WgoMotion.Standard) else this
