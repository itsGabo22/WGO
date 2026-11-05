package com.wgo.navigator.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.wgo.navigator.Language
import com.wgo.navigator.ui.LocalLanguage
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.string
import com.wgo.navigator.ui.theme.LocalWgoReduceMotion
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoMotion
import com.wgo.navigator.ui.theme.WgoType

private val SwitchSize = 40.dp
private val HitSize = 48.dp
private const val PRESSED_SCALE = 0.94f

/**
 * 40 dp circle with a 1.5 dp `ink` border showing the current language code. Pressing inverts it INSTANTLY
 * (ink/inkInverse) with scale .94; when [language] changes the code rolls upward with a spring.
 * The touch area is 48 dp. The click haptic belongs to the ViewModel behind [onToggle].
 */
@Composable
fun LanguageSwitch(
    language: Language,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = Wgo.palette
    val reduceMotion = LocalWgoReduceMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        animationSpec = if (pressed || reduceMotion) snap() else WgoMotion.pop(),
        label = "languageSwitchScale"
    )
    val background = if (pressed) palette.ink else androidx.compose.ui.graphics.Color.Transparent
    val contentColor = if (pressed) palette.inkInverse else palette.ink

    val description = string(
        if (language == Language.ES) StringKey.LANGUAGE_SWITCH_TO_EN else StringKey.LANGUAGE_SWITCH_TO_ES
    )

    Box(
        modifier = modifier
            .size(HitSize)
            .semantics { contentDescription = description }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onToggle
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(SwitchSize)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(background)
                .border(1.5.dp, palette.ink, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = language,
                transitionSpec = {
                    if (reduceMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        val roll = spring<IntOffset>(dampingRatio = 0.7f)
                        slideInVertically(roll) { it } togetherWith slideOutVertically(roll) { -it }
                    }
                },
                label = "languageCode"
            ) { lang ->
                Text(
                    text = lang.name,
                    color = contentColor,
                    style = WgoType.labelMd.copy(fontSize = 13.sp)
                )
            }
        }
    }
}

@Composable
private fun LanguagePreview(dark: Boolean, language: Language) {
    WgoPreviewFrame(dark) {
        CompositionLocalProvider(LocalLanguage provides language) {
            LanguageSwitch(language = language, onToggle = {})
        }
    }
}

@WgoPreview
@Composable
private fun LanguageSwitchEsDaylight() = LanguagePreview(false, Language.ES)

@WgoPreview
@Composable
private fun LanguageSwitchEnDaylight() = LanguagePreview(false, Language.EN)

@WgoPreview
@Composable
private fun LanguageSwitchEsNight() = LanguagePreview(true, Language.ES)

@WgoPreview
@Composable
private fun LanguageSwitchEnNight() = LanguagePreview(true, Language.EN)
