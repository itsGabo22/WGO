package com.wgo.navigator.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class MapStyleId { COBALTO, BARRIO, PERGAMINO, NEON, PIXEL, CIRCUITO, SONAR, ISLA }

enum class MapFx { NONE, VIGNETTE, PAPER, SCANLINES, PIXEL_GRID, HATCH, SONAR, CLOUDS }

@Immutable
data class WgoMapStyle(
    val id: MapStyleId,
    val palette: WgoMapPalette,
    val accent: Color,
    val fx: MapFx
)

/** Range rings are the player color at 18% alpha. */
private const val RANGE_RING_ALPHA = 0.18f

private fun staticMap(
    ground: Color, building: Color, park: Color, water: Color, road: Color,
    roadMajor: Color, roadCasing: Color, route: Color, routeHalo: Color, player: Color
) = WgoMapPalette(
    ground = ground, building = building, park = park, water = water, road = road,
    roadMajor = roadMajor, roadCasing = roadCasing, route = route, routeHalo = routeHalo,
    player = player, rangeRing = player.copy(alpha = RANGE_RING_ALPHA)
)

// ms-<style>-<role> tokens from docs/design/tokens.json (identical in Daylight and Night).
private val BarrioMap = staticMap(
    ground = Color(0xFF5D6A55),
    building = Color(0xFF53604B),
    park = Color(0xFF6F8A5B),
    water = Color(0xFF3F6F8F),
    road = Color(0xFF3A3F3A),
    roadMajor = Color(0xFF23262A),
    roadCasing = Color(0xFF8D9A82),
    route = Color(0xFFFFD23F),
    routeHalo = Color(0xFF23262A),
    player = Color(0xFFFFFFFF)
)

private val PergaminoMap = staticMap(
    ground = Color(0xFFEAD9B0),
    building = Color(0xFFDCC590),
    park = Color(0xFFBCCB8C),
    water = Color(0xFF8FB7B0),
    road = Color(0xFFF6ECD2),
    roadMajor = Color(0xFFFFF6DD),
    roadCasing = Color(0xFF8A6A3B),
    route = Color(0xFFB3261E),
    routeHalo = Color(0xFFFFF6DD),
    player = Color(0xFF2B1D0E)
)

private val NeonMap = staticMap(
    ground = Color(0xFF12002A),
    building = Color(0xFF1D0840),
    park = Color(0xFF1A0A4A),
    water = Color(0xFF0A1F6B),
    road = Color(0xFF4A1A7A),
    roadMajor = Color(0xFFFF2BD6),
    roadCasing = Color(0xFF12002A),
    route = Color(0xFF00F0FF),
    routeHalo = Color(0xFF12002A),
    player = Color(0xFFFFFFFF)
)

private val PixelMap = staticMap(
    ground = Color(0xFF9BBC0F),
    building = Color(0xFF8BAC0F),
    park = Color(0xFF8BAC0F),
    water = Color(0xFF306230),
    road = Color(0xFF8BAC0F),
    roadMajor = Color(0xFF306230),
    roadCasing = Color(0xFF306230),
    route = Color(0xFF0F380F),
    routeHalo = Color(0xFF9BBC0F),
    player = Color(0xFF0F380F)
)

private val CircuitoMap = staticMap(
    ground = Color(0xFF15171C),
    building = Color(0xFF1D2027),
    park = Color(0xFF1A2420),
    water = Color(0xFF12263A),
    road = Color(0xFF4A5163),
    roadMajor = Color(0xFFE8ECF2),
    roadCasing = Color(0xFF0D0F13),
    route = Color(0xFFFFD400),
    routeHalo = Color(0xFF0D0F13),
    player = Color(0xFFFF6B6B)
)

private val SonarMap = staticMap(
    ground = Color(0xFF02100E),
    building = Color(0xFF04201C),
    park = Color(0xFF04201C),
    water = Color(0xFF031A1F),
    road = Color(0xFF0E4B43),
    roadMajor = Color(0xFF19A08F),
    roadCasing = Color(0xFF02100E),
    route = Color(0xFF7DFFE0),
    routeHalo = Color(0xFF02100E),
    player = Color(0xFFFFFFFF)
)

private val IslaMap = staticMap(
    ground = Color(0xFFC9DFA0),
    building = Color(0xFFBCD392),
    park = Color(0xFF8FC16B),
    water = Color(0xFF5FB4E6),
    road = Color(0xFFF3E9C6),
    roadMajor = Color(0xFFFFF7DA),
    roadCasing = Color(0xFFA89A6A),
    route = Color(0xFFB83A00),
    routeHalo = Color(0xFFFFF7DA),
    player = Color(0xFF10240A)
)

val MapStyleId.fx: MapFx
    get() = when (this) {
    MapStyleId.COBALTO -> MapFx.NONE
    MapStyleId.BARRIO -> MapFx.VIGNETTE
    MapStyleId.PERGAMINO -> MapFx.PAPER
    MapStyleId.NEON -> MapFx.SCANLINES
    MapStyleId.PIXEL -> MapFx.PIXEL_GRID
    MapStyleId.CIRCUITO -> MapFx.HATCH
    MapStyleId.SONAR -> MapFx.SONAR
    MapStyleId.ISLA -> MapFx.CLOUDS
    }

val MapStyleId.accent: Color
    get() = when (this) {
    MapStyleId.COBALTO -> Color(0xFF1A63FF)
    MapStyleId.BARRIO -> Color(0xFFC47700)
    MapStyleId.PERGAMINO -> Color(0xFFB5651D)
    MapStyleId.NEON -> Color(0xFFE020C0)
    MapStyleId.PIXEL -> Color(0xFF5F8F0A)
    MapStyleId.CIRCUITO -> Color(0xFFE03A3A)
    MapStyleId.SONAR -> Color(0xFF00A58F)
    MapStyleId.ISLA -> Color(0xFF1B9AD6)
    }

/**
 * Resolves a map style. COBALTO uses the active theme's map-* palette ([themePalette].map);
 * the other seven are theme-independent.
 */
fun wgoMapStyle(id: MapStyleId, themePalette: WgoPalette): WgoMapStyle = WgoMapStyle(
    id = id,
    palette = when (id) {
        MapStyleId.COBALTO -> themePalette.map
    MapStyleId.BARRIO -> BarrioMap
    MapStyleId.PERGAMINO -> PergaminoMap
    MapStyleId.NEON -> NeonMap
    MapStyleId.PIXEL -> PixelMap
    MapStyleId.CIRCUITO -> CircuitoMap
    MapStyleId.SONAR -> SonarMap
    MapStyleId.ISLA -> IslaMap
    },
    accent = id.accent,
    fx = id.fx
)
