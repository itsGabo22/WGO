package com.wgo.navigator.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color

val WgoLightColors = Colors(
    primary = WgoPrimary,
    primaryVariant = WgoPrimary,
    secondary = WgoPrimary,
    secondaryVariant = WgoPrimary,
    error = Color(0xFFB3261E),
    onPrimary = WgoWhite,
    onSecondary = WgoWhite,
    onError = WgoWhite,
    background = WgoWhite,
    onBackground = WgoBlack,
    surface = WgoWhite,
    onSurface = WgoBlack,
    onSurfaceVariant = WgoBlack
)

val WgoTypography = Typography(
    display1 = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 40.sp),
    display2 = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 34.sp),
    display3 = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 30.sp),
    title1 = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    title2 = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    title3 = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    body1 = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    body2 = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    button = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.sp),
    caption1 = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    caption2 = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium, fontSize = 12.sp)
)

@Composable
fun WgoTheme(
    content: @Composable () -> Unit
) {
    // Explicitly forcing Light Theme by passing the same WgoLightColors
    MaterialTheme(
        colors = WgoLightColors,
        typography = WgoTypography,
        content = content
    )
}
