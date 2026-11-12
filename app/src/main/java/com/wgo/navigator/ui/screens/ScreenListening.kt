package com.wgo.navigator.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import com.wgo.navigator.metrics.WgoMetrics
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.wgo.navigator.Language
import com.wgo.navigator.ui.LocalLanguage
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.components.BezelGauge
import com.wgo.navigator.ui.components.BezelStyle
import com.wgo.navigator.ui.components.LanguageSwitch
import com.wgo.navigator.ui.components.MicOrb
import com.wgo.navigator.ui.components.MicState
import com.wgo.navigator.ui.components.StatusBadge
import com.wgo.navigator.ui.components.StatusBadgeVariant
import com.wgo.navigator.ui.components.WgoChip
import com.wgo.navigator.ui.components.WgoIcons
import com.wgo.navigator.ui.string
import com.wgo.navigator.ui.theme.ClockRate
import com.wgo.navigator.ui.theme.LocalWgoReduceMotion
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoClock
import com.wgo.navigator.ui.theme.WgoMotion
import com.wgo.navigator.ui.theme.WgoType
import com.wgo.navigator.ui.theme.rememberWgoClock
import com.wgo.navigator.ui.theme.wgoMinTouch
import com.wgo.navigator.ui.theme.wgoTouchSide
import com.wgo.navigator.ui.theme.wgoRiseIn

private const val REFERENCE_SIZE = 227f

/** What the title slot shows; a change of [language] with the same [state] rolls, anything else cross-fades. */
private data class TitleState(val state: MicState, val language: Language)

/**
 * Screen 01. Positions follow the spec (`top` in dp on a 227 dp canvas) expressed as fractions of the actual
 * screen so they also fit Small Round. One [WgoClock] is shared by every looping piece.
 * [transcript] is the live (partial, then final) transcript, one line at @54 per spec section 7; [level] is the live
 * voice level read only in the orb's draw phase. (Failed sessions go to the pipeline's error surface.)
 */
@Composable
fun ScreenListening(
    micState: MicState,
    onMicClick: () -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleLanguage: () -> Unit,
    modifier: Modifier = Modifier,
    transcript: String = "",
    level: (() -> Float)? = null,
    clock: WgoClock = rememberWgoClock(),
    onMicPress: (() -> Unit)? = null,
    sayAgain: Boolean = false
) {
    val palette = Wgo.palette
    val language = LocalLanguage.current
    val reduceMotion = LocalWgoReduceMotion.current
    if (micState == MicState.Preparing) {
        // Debug metric: when the preparing state is first on screen (compare with the ViewModel's voice_press).
        LaunchedEffect(Unit) { withFrameNanos { WgoMetrics.event("voice_preparing_drawn at=${SystemClock.elapsedRealtime()}") } }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(palette.canvas)) {
        val k = maxWidth.value / REFERENCE_SIZE
        fun dial(value: Float): Dp = (value * k).dp

        // Bezel ring: idle comet at rest, two comets while listening.
        BezelGauge(
            modifier = Modifier.wgoRiseIn(delayMs = 420, rise = 0.dp),
            style = if (micState == MicState.Listening) BezelStyle.ListeningComets else BezelStyle.IdleComet,
            clock = clock
        )

        // Header: badge @16, title / state label @38, transcript @54.
        StatusBadge(
            text = "WGO • NAV",
            variant = StatusBadgeVariant.Neutral,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(16f)).wgoRiseIn()
        )

        AnimatedContent(
            targetState = TitleState(micState, language),
            transitionSpec = {
                when {
                    reduceMotion -> fadeIn(tween(WgoMotion.SNAP)) togetherWith fadeOut(tween(WgoMotion.SNAP))
                    initialState.state == targetState.state ->
                        slideInVertically(spring(dampingRatio = 0.7f)) { it } togetherWith
                            slideOutVertically(spring(dampingRatio = 0.7f)) { -it }
                    else -> fadeIn(tween(WgoMotion.SNAP)) togetherWith fadeOut(tween(WgoMotion.SNAP))
                }
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = dial(38f))
                .width(dial(150f))
                .height(52.dp) // two headline-md lines at textS 0.9 on Small Round need 51 dp (box ends at 90, orb starts at 92)
                .wgoRiseIn(delayMs = WgoMotion.STAGGER),
            contentAlignment = Alignment.TopCenter,
            label = "listeningTitle"
        ) { title ->
            // Resolve strings with the language of this state so the outgoing title keeps its old text while rolling.
            CompositionLocalProvider(LocalLanguage provides title.language) {
                when (title.state) {
                    MicState.Idle -> Text(
                        text = string(StringKey.TITLE_WHERE_TO),
                        color = palette.ink,
                        style = WgoType.headlineMd,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                    // 130 wide, up to 2 lines (fit 93.5 with two lines @38, like the Working stage label).
                    MicState.Preparing -> Text(
                        text = string(StringKey.VOICE_PREPARING),
                        color = palette.primaryInk,
                        style = WgoType.labelMd,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.width(130.dp).semantics { liveRegion = LiveRegionMode.Polite }
                    )
                    MicState.Waiting -> Text(
                        text = string(StringKey.LISTENING_SYSTEM),
                        color = palette.primaryInk,
                        style = WgoType.labelMd,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
                    )
                    // Speak-now cue: "HABLA AHORA" ("DILO DE NUEVO" on the P9 fallback rung) until the first words
                    // arrive, then "ESCUCHANDO…".
                    MicState.Listening -> Text(
                        text = string(
                            when {
                                transcript.isNotEmpty() -> StringKey.LISTENING_LABEL
                                sayAgain -> StringKey.SAY_IT_AGAIN
                                else -> StringKey.SPEAK_NOW
                            }
                        ),
                        color = palette.primaryInk,
                        style = WgoType.labelMd,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
                    )
                    // P9: the captured speech is being transcribed.
                    MicState.Processing -> Text(
                        text = string(StringKey.STAGE_INTERPRETING),
                        color = palette.primaryInk,
                        style = WgoType.labelMd,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
        }

        val listening = micState == MicState.Listening
        if (listening || (micState == MicState.Processing && transcript.isNotEmpty())) {
            if (listening) clock.Use(ClockRate.Ambience) // blinking caret
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = dial(54f))
                    .width(dial(150f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                if (transcript.isNotEmpty()) {
                    Text(
                        text = transcript,
                        color = palette.ink,
                        style = WgoType.bodyMd,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                // Caret blinks with hard steps (1 s period), read in the draw phase only; static with reduce motion.
                if (listening) Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(14.dp)
                        .graphicsLayer {
                            alpha = if (reduceMotion || (clock.millis / 500L) % 2L == 0L) 1f else 0f
                        }
                        .background(palette.primaryInk)
                )
            }
        }

        // Mic orb: 64 dp, top 92 (center y 124).
        MicOrb(
            state = micState,
            onClick = onMicClick,
            onPress = onMicPress,
            level = level,
            clock = clock,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(92f)).wgoRiseIn(delayMs = WgoMotion.STAGGER)
        )

        // Hit boxes grow to 48 REAL dp around the visuals; centre them on the spec positions.
        val sideExtra = (wgoTouchSide(40.dp) - 40.dp) / 2
        val chipExtra = (wgoTouchSide(32.dp) - 32.dp) / 2

        // CANCEL chip 104 x 32 @176.
        if (micState != MicState.Idle) {
            WgoChip(
                label = string(StringKey.BTN_CANCEL),
                onClick = onCancel,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(176f) - chipExtra).wgoRiseIn()
            )
        }

        // Mirrored side buttons: 40 dp visual, 12 dp from the edges, top 94 (48 dp touch boxes).
        LanguageSwitch(
            language = language,
            onToggle = onToggleLanguage,
            modifier = Modifier.align(Alignment.TopStart).offset(x = 12.dp - sideExtra, y = dial(94f) - sideExtra).wgoRiseIn(delayMs = 2 * WgoMotion.STAGGER)
        )
        SettingsButton(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = -(12.dp - sideExtra), y = dial(94f) - sideExtra).wgoRiseIn(delayMs = 2 * WgoMotion.STAGGER)
        )
    }
}

/** Ghost 40 dp button with a 22 dp gear, 48 dp touch area; pressing inverts instantly. */
@Composable
private fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = Wgo.palette
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val description = string(StringKey.SETTINGS)

    Box(
        modifier = modifier
            .wgoMinTouch()
            .semantics { contentDescription = description }
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (pressed) palette.ink else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = WgoIcons.Settings,
                contentDescription = null,
                tint = if (pressed) palette.inkInverse else palette.ink,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
