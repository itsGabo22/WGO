package com.wgo.navigator.ui

import androidx.compose.runtime.Composable
import com.wgo.navigator.ui.theme.MapStyleId

/** Visible name of a map style in the current language (ES: COBALTO, BARRIO, ...; EN: COBALT, DISTRICT, ...). */
@Composable
fun mapStyleName(id: MapStyleId): String = string(
    when (id) {
        MapStyleId.COBALTO -> StringKey.MAP_NAME_COBALTO
        MapStyleId.BARRIO -> StringKey.MAP_NAME_BARRIO
        MapStyleId.PERGAMINO -> StringKey.MAP_NAME_PERGAMINO
        MapStyleId.NEON -> StringKey.MAP_NAME_NEON
        MapStyleId.PIXEL -> StringKey.MAP_NAME_PIXEL
        MapStyleId.CIRCUITO -> StringKey.MAP_NAME_CIRCUITO
        MapStyleId.SONAR -> StringKey.MAP_NAME_SONAR
        MapStyleId.ISLA -> StringKey.MAP_NAME_ISLA
    }
)

/** Genre line of a map style in the current language ("Ciudad abierta", "Open city", ...). */
@Composable
fun mapStyleGenre(id: MapStyleId): String = string(
    when (id) {
        MapStyleId.COBALTO -> StringKey.MAP_GENRE_COBALTO
        MapStyleId.BARRIO -> StringKey.MAP_GENRE_BARRIO
        MapStyleId.PERGAMINO -> StringKey.MAP_GENRE_PERGAMINO
        MapStyleId.NEON -> StringKey.MAP_GENRE_NEON
        MapStyleId.PIXEL -> StringKey.MAP_GENRE_PIXEL
        MapStyleId.CIRCUITO -> StringKey.MAP_GENRE_CIRCUITO
        MapStyleId.SONAR -> StringKey.MAP_GENRE_SONAR
        MapStyleId.ISLA -> StringKey.MAP_GENRE_ISLA
    }
)
