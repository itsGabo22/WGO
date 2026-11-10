package com.wgo.navigator.ai

import com.wgo.navigator.Language
import com.wgo.navigator.TravelMode
import com.wgo.navigator.TravelMode.Drive
import com.wgo.navigator.TravelMode.Walk
import com.wgo.navigator.core.IntentKind
import com.wgo.navigator.core.Interpretation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Table-driven: phrase -> expected kind, place, mode and whether it is accepted locally (confidence >= 0.75). */
class LocalIntentParserTest {
    private data class Case(
        val phrase: String,
        val kind: IntentKind,
        val place: String? = null,
        val mode: TravelMode? = null,
        val local: Boolean = true,
        val lang: Language = Language.ES
    )

    private val nav = IntentKind.Navigate

    private val cases = listOf(
        // Spanish verbs, Colombian usage
        Case("llévame a la Universidad de Nariño a pie", nav, "Universidad de Nariño", Walk),
        Case("Llévame a la Universidad de Nariño", nav, "Universidad de Nariño"),
        Case("llevame al Éxito", nav, "Éxito"),
        Case("llévame al terminal en bus", nav, "Terminal de Transportes de Pasto", Drive), // Pasto nickname (PlaceAliases)
        Case("quiero ir a la Plaza de Nariño", nav, "Plaza de Nariño"),
        Case("quiero ir al Unicentro en carro", nav, "Unicentro", Drive),
        Case("ir a la Catedral de Pasto", nav, "Catedral"),
        Case("vamos a Unicentro", nav, "Unicentro"),
        Case("vamos al estadio Libertad en moto", nav, "Estadio Departamental Libertad", Drive), // nickname
        Case("cómo llego a la Plaza del Carnaval", nav, "Plaza del Carnaval"),
        Case("¿Cómo llego al parque Infantil?", nav, "parque Infantil"),
        Case("navega a la Universidad Mariana", nav, "Universidad Mariana"),
        Case("necesito ir al Hospital Departamental", nav, "Hospital Departamental de Nariño"),
        Case("por favor llévame al aeropuerto Antonio Nariño", nav, "aeropuerto Antonio Nariño"),
        Case("llévame a la iglesia de San Felipe por favor", nav, "iglesia de San Felipe"),
        Case("quisiera ir a la Terminal de Transportes de Pasto", nav, "Terminal de Transportes de Pasto"),
        Case("quiero ir caminando a la plaza de Nariño", nav, "Plaza de Nariño", Walk),
        Case("a pie, llévame al parque Bolívar", nav, "parque Bolívar", Walk),
        Case("oye llévame andando al Centro Comercial Unicentro", nav, "Centro Comercial Unicentro", Walk),
        Case("tengo que ir a la clínica Fátima manejando", nav, "clínica Fátima", Drive),
        Case("me llevas a la Avenida de los Estudiantes", nav, "Avenida de los Estudiantes"),
        Case("vámonos al barrio Obrero en taxi", nav, "barrio Obrero", Drive),
        Case("llévame a la Calle 18 # 24-15", nav, "Calle 18 # 24-15"),
        Case("guíame a la Alcaldía de Pasto", nav, "Alcaldía"),
        Case("ruta al Santuario de Las Lajas en carro", nav, "Santuario de Las Lajas", Drive),
        // English
        Case("take me to the bus terminal in Pasto", nav, "Terminal de Transportes de Pasto", lang = Language.EN),
        Case("take me to the University of Nariño walking", nav, "University of Nariño", Walk, lang = Language.EN),
        Case("go to the main square", nav, "Plaza de Nariño", lang = Language.EN),
        Case("navigate to Unicentro mall by car", nav, "Unicentro mall", Drive, lang = Language.EN),
        Case("directions to the cathedral", nav, "cathedral", lang = Language.EN),
        Case("please take me to the airport", nav, "Aeropuerto Antonio Nariño", lang = Language.EN),
        Case("walk to the Bolivar park", nav, "Bolivar park", Walk, lang = Language.EN),
        Case("drive to the stadium", nav, "Estadio Departamental Libertad", Drive, lang = Language.EN),
        Case("how do I get to the hospital on foot", nav, "hospital", Walk, lang = Language.EN),
        Case("I want to go to the Carnival Museum", nav, "Carnival Museum", lang = Language.EN),
        // Bare places (screen 01 means "where to?")
        Case("el Éxito", nav, "Éxito"),
        Case("Unicentro", nav, "Unicentro"),
        Case("la Universidad de Nariño", nav, "Universidad de Nariño"),
        Case("Plaza de Nariño en Pasto", nav, "Plaza de Nariño"),
        Case("Terminal de transportes Pasto", nav, "Terminal de Transportes de Pasto"),
        Case("the cathedral", nav, "cathedral", lang = Language.EN),
        // Vague or long: left to the remote interpreter when one is configured
        Case("llévame donde venden los helados frente a la plaza", nav, "donde venden los helados frente a la plaza", local = false),
        Case("take me to that place near the stadium where they sell coffee", nav,
            "that place near the stadium where they sell coffee", local = false, lang = Language.EN),
        Case("dónde queda el Éxito", nav, "dónde queda el Éxito", local = false),
        Case("la tienda grande de la avenida de los estudiantes al lado del parque", nav,
            "tienda grande de la avenida de los estudiantes al lado del parque", local = false),
        // Commands
        Case("cancelar", IntentKind.Cancel),
        Case("Cancela la ruta", IntentKind.Cancel),
        Case("parar", IntentKind.Cancel),
        Case("para", IntentKind.Cancel),
        Case("stop", IntentKind.Cancel, lang = Language.EN),
        Case("cancel the navigation", IntentKind.Cancel, lang = Language.EN),
        Case("repite", IntentKind.Repeat),
        Case("say again", IntentKind.Repeat, lang = Language.EN),
        Case("mejor a pie", IntentKind.ChangeMode, mode = Walk),
        Case("en carro", IntentKind.ChangeMode, mode = Drive),
        Case("driving", IntentKind.ChangeMode, mode = Drive, lang = Language.EN),
        // Nothing usable
        Case("", IntentKind.Unknown, local = false),
        Case("por favor", IntentKind.Unknown, local = false),
        Case("¿?", IntentKind.Unknown, local = false)
    )

    @Test fun phraseTable() {
        assertTrue("at least 40 phrases", cases.size >= 40)
        val failures = cases.mapNotNull { c ->
            val r = LocalIntentParser.parse(c.phrase, c.lang)
            val accepted = r.confidence >= Interpretation.ACCEPT_CONFIDENCE
            val ok = r.kind == c.kind && r.placeQuery == c.place && r.mode == c.mode && accepted == c.local
            if (ok) null else "'${c.phrase}' -> $r (expected ${c.kind} '${c.place}' ${c.mode} local=${c.local})"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun cancelBeatsPlaceWhenOnlyCommandWords() {
        assertEquals(IntentKind.Navigate, LocalIntentParser.parse("para la plaza de Nariño", Language.ES).kind)
    }

    @Test fun placeIsTrimmedAndControlFree() {
        val long = "llévame a " + "x".repeat(200) + "\u0007"
        val r = LocalIntentParser.parse(long, Language.ES)
        assertTrue(r.placeQuery!!.length <= LocalIntentParser.MAX_PLACE_CHARS)
        assertTrue(r.placeQuery!!.none { it.isISOControl() })
    }

    @Test fun cleanPlaceQueryStripsRegion() {
        assertEquals("Universidad de Nariño", LocalIntentParser.cleanPlaceQuery("la Universidad de Nariño, Pasto, Colombia"))
        assertEquals("Pasto", LocalIntentParser.cleanPlaceQuery("Pasto"))
        assertEquals(null, LocalIntentParser.cleanPlaceQuery("  "))
    }
}
