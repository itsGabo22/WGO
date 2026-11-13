package com.wgo.navigator.ui.theme

import androidx.compose.ui.graphics.Color

private const val DEPRECATION = "Use LocalWgoPalette / Wgo.palette (see ui/theme/WgoPalette.kt)."

@Deprecated(DEPRECATION, ReplaceWith("Wgo.palette.primary"))
val WgoPrimary = Color(0xFF0052FF)

@Deprecated(DEPRECATION, ReplaceWith("Wgo.palette.ink"))
val WgoBlack = Color(0xFF000000)

@Deprecated(DEPRECATION, ReplaceWith("Wgo.palette.canvas"))
val WgoWhite = Color(0xFFFFFFFF)

@Deprecated(DEPRECATION, ReplaceWith("Wgo.palette.surface"))
val WgoSurface = Color(0xFFF2F4F7)

@Deprecated(DEPRECATION, ReplaceWith("Wgo.palette.surfaceRaised"))
val WgoSurfaceOutline = Color(0xFFE1E4EA)
