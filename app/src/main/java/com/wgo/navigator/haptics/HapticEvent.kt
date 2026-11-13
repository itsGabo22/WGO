package com.wgo.navigator.haptics

import kotlinx.coroutines.flow.SharedFlow

/** Haptic pulses emitted by ViewModels; played by [HapticPlayer] from the Activity. */
enum class HapticEvent { Tick, Click, HeavyClick, DoubleClick }

/** Implemented by ViewModels that emit haptic events. */
interface HapticSource {
    val haptics: SharedFlow<HapticEvent>
}
