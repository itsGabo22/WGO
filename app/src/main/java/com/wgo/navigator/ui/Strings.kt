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
    NAV_INSTRUCTION, GO_STRAIGHT
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
    StringKey.GO_STRAIGHT to "Continuar recto"
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
    StringKey.GO_STRAIGHT to "Go straight"
)
