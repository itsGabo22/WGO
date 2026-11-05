package com.wgo.navigator.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Plays [HapticEvent]s with the predefined vibration effects. Never used from composables. */
class HapticPlayer(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    fun play(event: HapticEvent) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = when (event) {
            HapticEvent.Tick -> VibrationEffect.EFFECT_TICK
            HapticEvent.Click -> VibrationEffect.EFFECT_CLICK
            HapticEvent.HeavyClick -> VibrationEffect.EFFECT_HEAVY_CLICK
            HapticEvent.DoubleClick -> VibrationEffect.EFFECT_DOUBLE_CLICK
        }
        v.vibrate(VibrationEffect.createPredefined(effect))
    }
}
