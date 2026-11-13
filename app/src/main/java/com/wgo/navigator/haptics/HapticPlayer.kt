package com.wgo.navigator.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Plays [HapticEvent]s with the predefined vibration effects. Never used from composables.
 * Built once off the main thread during the cold-start intro: the vibrator service lookup and the four predefined
 * effects are resolved up front, so [play] allocates nothing.
 */
class HapticPlayer(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private val canVibrate = vibrator?.hasVibrator() == true

    private val effects: Array<VibrationEffect> = Array(HapticEvent.entries.size) { i ->
        VibrationEffect.createPredefined(
            when (HapticEvent.entries[i]) {
                HapticEvent.Tick -> VibrationEffect.EFFECT_TICK
                HapticEvent.Click -> VibrationEffect.EFFECT_CLICK
                HapticEvent.HeavyClick -> VibrationEffect.EFFECT_HEAVY_CLICK
                HapticEvent.DoubleClick -> VibrationEffect.EFFECT_DOUBLE_CLICK
            }
        )
    }

    fun play(event: HapticEvent) {
        val v = vibrator ?: return
        if (!canVibrate) return
        v.vibrate(effects[event.ordinal])
    }
}
