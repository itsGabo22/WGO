package com.wgo.navigator.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.wgo.navigator.Language

val LocalLanguage = compositionLocalOf { Language.ES }

@Composable
fun string(key: StringKey): String {
    val language = LocalLanguage.current
    return when (language) {
        Language.ES -> es[key] ?: key.name
        Language.EN -> en[key] ?: key.name
    }
}

enum class StringKey {
    TITLE_WHERE_TO, TITLE_ROUTING, BTN_CANCEL, BTN_DRIVE, BTN_WALK,
    DESTINATION_LONG, SETTINGS, THEME, LANGUAGE, LIGHT, DARK,
    SPANISH, ENGLISH, ACTIVATE_MIC, ZOOM_IN, ZOOM_OUT, VOICE_ON,
    NAV_INSTRUCTION, GO_STRAIGHT,
    // Settings and map styles
    SETTINGS_HEADER, MAP_LABEL, USE,
    LISTENING_LABEL, DESTINATION_NAME, DESTINATION_ADDRESS, ETA_MIN_UNIT,
    MODE_WALK_DESC, MODE_DRIVE_DESC, MINUTES_WORD,
    UNIT_M, UNIT_KM, MIC_STATE_IDLE, MIC_STATE_LISTENING, MIC_STATE_PROCESSING, TURN_IMMINENT,
    LANGUAGE_SWITCH_TO_EN, LANGUAGE_SWITCH_TO_ES,
    MAP_NAME_COBALTO, MAP_GENRE_COBALTO,
    MAP_NAME_BARRIO, MAP_GENRE_BARRIO,
    MAP_NAME_PERGAMINO, MAP_GENRE_PERGAMINO,
    MAP_NAME_NEON, MAP_GENRE_NEON,
    MAP_NAME_PIXEL, MAP_GENRE_PIXEL,
    MAP_NAME_CIRCUITO, MAP_GENRE_CIRCUITO,
    MAP_NAME_SONAR, MAP_GENRE_SONAR,
    MAP_NAME_ISLA, MAP_GENRE_ISLA
}

private val es = mapOf(
    StringKey.TITLE_WHERE_TO to "¿A dónde vamos?",
    StringKey.TITLE_ROUTING to "TRAZANDO RUTA...",
    StringKey.BTN_CANCEL to "CANCELAR",
    StringKey.BTN_DRIVE to "CONDUCIR",
    StringKey.BTN_WALK to "CAMINAR",
    StringKey.DESTINATION_LONG to "Calle 18 #24-15, Centro Histórico",
    StringKey.SETTINGS to "Configuración",
    StringKey.THEME to "Tema",
    StringKey.LANGUAGE to "Idioma",
    StringKey.LIGHT to "Claro",
    StringKey.DARK to "Oscuro",
    StringKey.SPANISH to "Español",
    StringKey.ENGLISH to "Inglés",
    StringKey.ACTIVATE_MIC to "Activar micrófono",
    StringKey.ZOOM_IN to "Acercar mapa",
    StringKey.ZOOM_OUT to "Alejar mapa",
    StringKey.VOICE_ON to "VOICE ON",
    StringKey.NAV_INSTRUCTION to "Gira a la derecha en Calle 18",
    StringKey.GO_STRAIGHT to "Continuar recto",
    StringKey.LISTENING_LABEL to "ESCUCHANDO…",
    StringKey.DESTINATION_NAME to "Centro Histórico",
    StringKey.DESTINATION_ADDRESS to "Calle 18 #24-15",
    StringKey.ETA_MIN_UNIT to "min",
    StringKey.MODE_WALK_DESC to "Ir a pie",
    StringKey.MODE_DRIVE_DESC to "Ir en carro",
    StringKey.MINUTES_WORD to "minutos",
    StringKey.UNIT_M to "m",
    StringKey.UNIT_KM to "km",
    StringKey.MIC_STATE_IDLE to "En espera",
    StringKey.MIC_STATE_LISTENING to "Escuchando",
    StringKey.MIC_STATE_PROCESSING to "Trazando ruta",
    StringKey.TURN_IMMINENT to "Giro inminente",
    StringKey.SETTINGS_HEADER to "AJUSTES",
    StringKey.MAP_LABEL to "MAPA",
    StringKey.USE to "USAR",
    StringKey.LANGUAGE_SWITCH_TO_EN to "Idioma: Español. Toca para cambiar a English",
    StringKey.LANGUAGE_SWITCH_TO_ES to "Idioma: English. Toca para cambiar a Español",
    StringKey.MAP_NAME_COBALTO to "COBALTO",
    StringKey.MAP_GENRE_COBALTO to "WGO original",
    StringKey.MAP_NAME_BARRIO to "BARRIO",
    StringKey.MAP_GENRE_BARRIO to "Ciudad abierta",
    StringKey.MAP_NAME_PERGAMINO to "PERGAMINO",
    StringKey.MAP_GENRE_PERGAMINO to "Aventura clásica",
    StringKey.MAP_NAME_NEON to "NEÓN",
    StringKey.MAP_GENRE_NEON to "Arcade ciberpunk",
    StringKey.MAP_NAME_PIXEL to "PÍXEL",
    StringKey.MAP_GENRE_PIXEL to "Retro 8 bits",
    StringKey.MAP_NAME_CIRCUITO to "CIRCUITO",
    StringKey.MAP_GENRE_CIRCUITO to "Carreras",
    StringKey.MAP_NAME_SONAR to "SONAR",
    StringKey.MAP_GENRE_SONAR to "Sigilo submarino",
    StringKey.MAP_NAME_ISLA to "ISLA",
    StringKey.MAP_GENRE_ISLA to "Aventura tropical"
)

private val en = mapOf(
    StringKey.TITLE_WHERE_TO to "Where to?",
    StringKey.TITLE_ROUTING to "ROUTING...",
    StringKey.BTN_CANCEL to "CANCEL",
    StringKey.BTN_DRIVE to "DRIVE",
    StringKey.BTN_WALK to "WALK",
    StringKey.DESTINATION_LONG to "18th Street #24-15, Historic Center",
    StringKey.SETTINGS to "Settings",
    StringKey.THEME to "Theme",
    StringKey.LANGUAGE to "Language",
    StringKey.LIGHT to "Light",
    StringKey.DARK to "Dark",
    StringKey.SPANISH to "Spanish",
    StringKey.ENGLISH to "English",
    StringKey.ACTIVATE_MIC to "Activate microphone",
    StringKey.ZOOM_IN to "Zoom in",
    StringKey.ZOOM_OUT to "Zoom out",
    StringKey.VOICE_ON to "VOICE ON",
    StringKey.NAV_INSTRUCTION to "Turn right on 18th Street",
    StringKey.GO_STRAIGHT to "Go straight",
    StringKey.LISTENING_LABEL to "LISTENING…",
    StringKey.DESTINATION_NAME to "Historic Center",
    StringKey.DESTINATION_ADDRESS to "18th Street #24-15",
    StringKey.ETA_MIN_UNIT to "min",
    StringKey.MODE_WALK_DESC to "Walk",
    StringKey.MODE_DRIVE_DESC to "Drive",
    StringKey.MINUTES_WORD to "minutes",
    StringKey.UNIT_M to "m",
    StringKey.UNIT_KM to "km",
    StringKey.MIC_STATE_IDLE to "Idle",
    StringKey.MIC_STATE_LISTENING to "Listening",
    StringKey.MIC_STATE_PROCESSING to "Routing",
    StringKey.TURN_IMMINENT to "Turn imminent",
    StringKey.SETTINGS_HEADER to "SETTINGS",
    StringKey.MAP_LABEL to "MAP",
    StringKey.USE to "USE",
    StringKey.LANGUAGE_SWITCH_TO_EN to "Language: Spanish. Tap to switch to English",
    StringKey.LANGUAGE_SWITCH_TO_ES to "Language: English. Tap to switch to Español",
    StringKey.MAP_NAME_COBALTO to "COBALT",
    StringKey.MAP_GENRE_COBALTO to "WGO original",
    StringKey.MAP_NAME_BARRIO to "DISTRICT",
    StringKey.MAP_GENRE_BARRIO to "Open city",
    StringKey.MAP_NAME_PERGAMINO to "PARCHMENT",
    StringKey.MAP_GENRE_PERGAMINO to "Classic quest",
    StringKey.MAP_NAME_NEON to "NEON",
    StringKey.MAP_GENRE_NEON to "Cyber arcade",
    StringKey.MAP_NAME_PIXEL to "PIXEL",
    StringKey.MAP_GENRE_PIXEL to "Retro 8-bit",
    StringKey.MAP_NAME_CIRCUITO to "CIRCUIT",
    StringKey.MAP_GENRE_CIRCUITO to "Racing",
    StringKey.MAP_NAME_SONAR to "SONAR",
    StringKey.MAP_GENRE_SONAR to "Stealth ops",
    StringKey.MAP_NAME_ISLA to "ISLAND",
    StringKey.MAP_GENRE_ISLA to "Tropical quest"
)
