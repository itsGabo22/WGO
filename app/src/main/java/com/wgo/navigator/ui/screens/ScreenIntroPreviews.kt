package com.wgo.navigator.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.wgo.navigator.Language
import com.wgo.navigator.ui.LocalLanguage
import com.wgo.navigator.ui.components.WgoPreviewFrame
import com.wgo.navigator.ui.components.WgoWatchPreviews

/** The intro frozen at [t] ms (see IntroTimeline), Daylight or Night, ES by default. */
@Composable
private fun IntroAt(t: Long, dark: Boolean, language: Language = Language.ES) {
    CompositionLocalProvider(LocalLanguage provides language) {
        WgoPreviewFrame(dark) {
            ScreenIntro(onLockup = {}, onFinished = {}, onSkip = {}, frozenAtMs = t)
        }
    }
}

// Phase A: WHERE alone, WHERE + WE, all three words with the "?" landing.
@WgoWatchPreviews @Composable private fun IntroWhereDaylight() = IntroAt(400, false)
@WgoWatchPreviews @Composable private fun IntroWhereNight() = IntroAt(400, true)
@WgoWatchPreviews @Composable private fun IntroWeDaylight() = IntroAt(1000, false)
@WgoWatchPreviews @Composable private fun IntroWeNight() = IntroAt(1000, true)
@WgoWatchPreviews @Composable private fun IntroQuestionDaylight() = IntroAt(1850, false)
@WgoWatchPreviews @Composable private fun IntroQuestionNight() = IntroAt(1850, true)

// Phase B: mid-merge (letters collapsing, W's sliding, ring leaving the dot).
@WgoWatchPreviews @Composable private fun IntroMergeDaylight() = IntroAt(2250, false)
@WgoWatchPreviews @Composable private fun IntroMergeNight() = IntroAt(2250, true)

// Phase C: the lockup (also the reduce-motion and skip frame), ES with tagline and EN without.
@WgoWatchPreviews @Composable private fun IntroLockupDaylight() = IntroAt(IntroTimeline.STATIC_T, false)
@WgoWatchPreviews @Composable private fun IntroLockupNight() = IntroAt(IntroTimeline.STATIC_T, true)
@WgoWatchPreviews @Composable private fun IntroLockupEnDaylight() = IntroAt(IntroTimeline.STATIC_T, false, Language.EN)
@WgoWatchPreviews @Composable private fun IntroLockupEnNight() = IntroAt(IntroTimeline.STATIC_T, true, Language.EN)
