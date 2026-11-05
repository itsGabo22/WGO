package com.wgo.navigator.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Typography
import com.wgo.navigator.R

/**
 * space_grotesk.ttf is a VARIABLE font (wght 300-700, default instance 300 = Light).
 * A static Font(..., FontWeight.Bold) would render Light, so each weight pins the axis.
 */
@OptIn(ExperimentalTextApi::class)
private fun grotesk(weight: Int) = Font(
    resId = R.font.space_grotesk,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val SpaceGrotesk = FontFamily(grotesk(500), grotesk(700))

private const val TABULAR = "tnum"

/** Type scale from docs/design/tokens.json (type.groups). Uppercase is applied at the call site. */
object WgoType {
    val numeralXl = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold,
        fontSize = 34.sp, lineHeight = 34.sp, letterSpacing = (-0.03).em, fontFeatureSettings = TABULAR
    )
    val numeralLg = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 30.sp, letterSpacing = (-0.03).em, fontFeatureSettings = TABULAR
    )
    val headlineLg = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold,
        fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.02).em
    )
    val headlineMd = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold,
        fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = (-0.02).em
    )
    val headlineSm = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold,
        fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = (-0.01).em
    )
    val bodyLg = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = 0.em
    )
    val bodyMd = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.em
    )
    val button = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold,
        fontSize = 15.sp, lineHeight = 18.sp, letterSpacing = 0.07.em
    )
    val labelMd = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold,
        fontSize = 12.sp, lineHeight = 14.sp, letterSpacing = 0.08.em
    )
    val labelSm = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold,
        fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.12.em
    )
}

/** Wear Material 2 Typography slots mapped onto the WGO scale. */
val WgoTypography = Typography(
    display1 = WgoType.numeralXl,
    display2 = WgoType.numeralLg,
    display3 = WgoType.headlineLg,
    title1 = WgoType.headlineLg,
    title2 = WgoType.headlineMd,
    title3 = WgoType.headlineSm,
    body1 = WgoType.bodyLg,
    body2 = WgoType.bodyMd,
    button = WgoType.button,
    caption1 = WgoType.bodyMd,
    caption2 = WgoType.labelMd,
    caption3 = WgoType.labelSm
)
