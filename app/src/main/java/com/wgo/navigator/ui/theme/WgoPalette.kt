package com.wgo.navigator.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Map colors for the radar minimap. Values come from docs/design/tokens.json (map-*). */
@Immutable
data class WgoMapPalette(
    val ground: Color,
    val building: Color,
    val park: Color,
    val water: Color,
    val road: Color,
    val roadMajor: Color,
    val roadCasing: Color,
    val route: Color,
    val routeHalo: Color,
    val player: Color,
    val rangeRing: Color
)

/**
 * Ambient (always-on, low power) colors, from docs/design/tokens.json (ambient-*). Identical in both themes: ambient
 * is always black with a short, low-intensity grey ramp (no pure white, no large filled areas).
 */
@Immutable
data class WgoAmbientPalette(
    val canvas: Color,
    val ink: Color,
    val inkMuted: Color,
    val stroke: Color
)

val AmbientPalette = WgoAmbientPalette(
    canvas = Color(0xFF000000),
    ink = Color(0xFFB0B0B0),
    inkMuted = Color(0xFF8A8A8A),
    stroke = Color(0xFF6E6E6E)
)

/** Semantic colors for one WGO theme. Values come from docs/design/tokens.json; never hard-code colors in screens. */
@Immutable
data class WgoPalette(
    val canvas: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkInverse: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryInk: Color,
    val aura: Color,
    val auraDeep: Color,
    val alert: Color,
    val onAlert: Color,
    val go: Color,
    val onGo: Color,
    val map: WgoMapPalette,
    /** Ambient mode (same in both themes). */
    val ambient: WgoAmbientPalette = AmbientPalette
)

/** Daylight: light source theme. */
val DaylightPalette = WgoPalette(
    canvas = Color(0xFFFFFFFF),
    surface = Color(0xFFF2F4F7),
    surfaceRaised = Color(0xFFE1E4EA),
    ink = Color(0xFF000000),
    inkMuted = Color(0xFF5A606B),
    inkInverse = Color(0xFFFFFFFF),
    primary = Color(0xFF0052FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryInk = Color(0xFF0052FF),
    aura = Color(0xFF00D4FF),
    auraDeep = Color(0xFF00677E),
    alert = Color(0xFFBF3003),
    onAlert = Color(0xFFFFFFFF),
    go = Color(0xFF007A55),
    onGo = Color(0xFFFFFFFF),
    map = WgoMapPalette(
        ground = Color(0xFFE9ECF1),
        building = Color(0xFFDDE1E8),
        park = Color(0xFFCBE7CF),
        water = Color(0xFFBBD9F0),
        road = Color(0xFFFFFFFF),
        roadMajor = Color(0xFFFFFFFF),
        roadCasing = Color(0xFFC9CED8),
        route = Color(0xFF0052FF),
        routeHalo = Color(0xFFFFFFFF),
        player = Color(0xFF000000),
        rangeRing = Color(0x24000000)
    )
)

/** Night: dark AMOLED theme. */
val NightPalette = WgoPalette(
    canvas = Color(0xFF000000),
    surface = Color(0xFF1E1E1E),
    surfaceRaised = Color(0xFF2E3138),
    ink = Color(0xFFFFFFFF),
    inkMuted = Color(0xFF9AA3B2),
    inkInverse = Color(0xFF000000),
    primary = Color(0xFF0052FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryInk = Color(0xFF5C8DFF),
    aura = Color(0xFF00D4FF),
    auraDeep = Color(0xFF3CD7FF),
    alert = Color(0xFFFF7A59),
    onAlert = Color(0xFF000000),
    go = Color(0xFF3DDC97),
    onGo = Color(0xFF000000),
    map = WgoMapPalette(
        ground = Color(0xFF0B1220),
        building = Color(0xFF141D30),
        park = Color(0xFF10302A),
        water = Color(0xFF0C2F52),
        road = Color(0xFF26324A),
        roadMajor = Color(0xFF3B507D),
        roadCasing = Color(0xFF0F1626),
        route = Color(0xFF00D4FF),
        routeHalo = Color(0xFF0052FF),
        player = Color(0xFFFFFFFF),
        rangeRing = Color(0x1FFFFFFF)
    )
)

val LocalWgoPalette = staticCompositionLocalOf<WgoPalette> { error("WgoTheme missing") }

object Wgo {
    val palette: WgoPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalWgoPalette.current
}
