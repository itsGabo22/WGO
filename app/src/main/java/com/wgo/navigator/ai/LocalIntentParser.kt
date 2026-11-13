package com.wgo.navigator.ai

import com.wgo.navigator.Language
import com.wgo.navigator.TravelMode
import com.wgo.navigator.core.IntentKind
import com.wgo.navigator.core.Interpretation
import java.text.Normalizer

/**
 * Rule-based ES/EN interpreter: pure Kotlin, no network, a few microseconds per utterance. It understands navigation
 * verbs ("llévame a", "take me to"), travel modes ("a pie", "en carro", "walking"), cancel/repeat commands and bare
 * place names (screen 01 already asks "where to?"). The place keeps the user's own spelling (accents included) but loses
 * politeness, articles and a trailing city name ("... en Pasto"), which makes free-form geocoding miss.
 *
 * Confidence: verb + concrete place 0.9; bare short place (<= 4 words) 0.75; anything vague ("donde venden ...",
 * "near the ...") or long without a verb 0.5-0.55, so a remote interpreter may be consulted.
 *
 * Long phrases (P10, docs/design "Long phrases, strict place choice"): the clause with the navigation verb is kept
 * (the cloud transcript is punctuated), politeness and filler go at both ends, the verb may stand anywhere in the
 * clause, the place stops at a clause-breaking word ("... la cooperativa es lo único que te pido"), and a request for
 * help ("ayúdame") is never a destination. A place still longer than [MAX_PLACE_WORDS] words goes to the remote
 * interpreter, whose phrase must be made of the utterance's own words ([groundedIn]).
 */
object LocalIntentParser {
    const val MAX_PLACE_CHARS = 80

    /** P10: a place longer than this is not trusted locally (the remote interpreter extracts the destination). */
    const val MAX_PLACE_WORDS = 8

    /** P10: a clause this short right after the verb clause is part of the place ("..., la cooperativa"). */
    private const val APPOSITION_WORDS = 3

    /** A known Pasto nickname ([PlaceAliases]) is as certain as a verb + concrete place. */
    const val ALIAS_CONFIDENCE = 0.9f

    private data class Tok(val orig: String, val norm: String)
    private class Phrase(text: String, val mode: TravelMode? = null) {
        val words = text.split(' ')
    }

    fun parse(transcript: String, @Suppress("UNUSED_PARAMETER") language: Language): Interpretation {
        var toks = stripPoliteness(tokenize(destinationClause(transcript)))
        if (toks.isEmpty()) return Interpretation.Unknown

        if (toks.first().norm in CANCEL_WORDS && toks.all { it.norm in CANCEL_WORDS || it.norm in CANCEL_FILLER }) {
            return Interpretation(IntentKind.Cancel, null, null, 0.95f)
        }
        if (toks.size <= 4 && REPEAT.any { startsWith(toks, it.words) }) {
            return Interpretation(IntentKind.Repeat, null, null, 0.9f)
        }

        // A verb that carries a mode ("walk to"), else a mode phrase anywhere ("a pie" must go before "llévame a" can
        // swallow its "a"), then the plain verb at the start.
        var verb = VERBS.firstOrNull { it.mode != null && startsWith(toks, it.words) }
        if (verb != null) toks = toks.drop(verb.words.size)
        var mode: TravelMode? = verb?.mode
        if (mode == null) for (phrase in MODES) {
            val at = indexOf(toks, phrase.words)
            if (at >= 0) {
                mode = phrase.mode
                toks = toks.take(at) + toks.drop(at + phrase.words.size)
                break
            }
        }
        // P10: a mode said in another clause ("a pie, llévame al parque") still counts.
        if (mode == null) {
            val all = tokenize(transcript)
            mode = MODES.firstOrNull { indexOf(all, it.words) >= 0 }?.mode
        }
        toks = stripPoliteness(toks)
        if (verb == null) {
            verb = leadingVerb(toks)
            if (verb != null) toks = toks.drop(verb.words.size)
        }
        if (verb == null) {
            // P10: "amigo, quiero ir a la cooperativa": a full navigation verb later in the clause.
            val found = innerVerb(toks)
            if (found != null) {
                verb = found.first
                toks = toks.drop(found.second + found.first.words.size)
            }
        }
        toks = stripPoliteness(toks.dropWhile { it.norm in CONNECTORS }).dropWhile { it.norm in CONNECTORS }
        toks = cutAtBreak(toks)

        val placeToks = dropQuantifiers(cleanPlace(toks))
        val heard = join(toks.dropWhile { it.norm in CONNECTORS }).ifEmpty { null }
        if (placeToks.isEmpty()) {
            return if (mode != null) Interpretation(IntentKind.ChangeMode, null, mode, 0.9f) else Interpretation.Unknown
        }
        // P10: "por favor ayúdame" asks for help, it names no place.
        if (placeToks.all { it.norm in NON_PLACE }) return Interpretation.Unknown
        // P10: a street address ("la calle 6A #36-41, barrio San Vicente") is searched as an address, never as a name.
        address(placeToks)?.let { (street, barrio) ->
            return Interpretation(
                IntentKind.Navigate, street, mode, ALIAS_CONFIDENCE, placeHint = barrio, placeHeard = heard, placeIsAddress = true
            )
        }
        // Local nicknames ("la UDENAR", "el terminal") resolve here, with no remote call.
        PlaceAliases.resolve(placeToks.map { it.norm })?.let {
            return Interpretation(IntentKind.Navigate, it, mode, ALIAS_CONFIDENCE, placeFromAlias = true, placeHeard = heard)
        }
        // "X de la Y" / "X cerca de Y": name X, landmark hint Y (docs/PLACE_SEARCH.md). A vague description
        // ("donde venden helados frente a la plaza") stays whole: the remote interpreter reads it.
        val vagueWhole = placeToks.any { it.norm in VAGUE_WORDS && it.norm !in RELATION_WORDS }
        val split = if (vagueWhole) PlaceHint.Split(join(placeToks), null) else PlaceHint.split(join(placeToks))
        val nameToks = dropQuantifiers(tokenize(split.name))
        // P10 category request: the place is only a kind of place ("droguería", "restaurantes"): real places of that
        // category near the hint, always as the pick list.
        if (nameToks.size == 1) {
            val category = com.wgo.navigator.places.PlaceCategories.categoryWord(nameToks[0].norm)
            if (category != null) return Interpretation(
                IntentKind.Navigate, nameToks[0].orig, mode, ALIAS_CONFIDENCE, placeHint = split.hint,
                placeCategory = nameToks[0].orig, placeVague = true, placeHeard = heard
            )
        }
        PlaceAliases.resolve(nameToks.map { it.norm })?.let {
            return Interpretation(
                IntentKind.Navigate, it, mode, ALIAS_CONFIDENCE, placeHint = split.hint, placeFromAlias = true, placeHeard = heard
            )
        }
        val vague = nameToks.any { it.norm in VAGUE_WORDS }
        val confidence = when {
            vague -> 0.5f
            placeToks.size > MAX_PLACE_WORDS -> 0.55f
            verb != null -> 0.9f
            placeToks.size <= 4 -> Interpretation.ACCEPT_CONFIDENCE // the whole phrase: long descriptions go remote
            else -> 0.55f
        }
        return Interpretation(
            IntentKind.Navigate, split.name, mode, confidence, placeHint = split.hint, placeVague = vague || vagueWhole,
            placeHeard = heard
        )
    }

    /**
     * P10: true when every content word of [phrase] (articles and connectors aside, accents and case ignored) is a word
     * of [utterance]: the remote interpreter may only copy the destination, never invent or complete it.
     */
    fun groundedIn(phrase: String, utterance: String): Boolean {
        val said = tokenize(utterance).map { it.norm }.toSet()
        val words = tokenize(phrase).map { it.norm }.filter { it !in ARTICLES && it !in CONNECTORS && it !in REGION_CONNECTORS }
        return words.isNotEmpty() && words.all { it in said }
    }

    /**
     * The clause that names the destination: the transcript split at its punctuation; the first clause with a
     * navigation verb (joined to the next one when nothing follows the verb), else the first one that is not only
     * politeness. One clause, or none with content: the whole transcript.
     */
    private fun destinationClause(transcript: String): String {
        val parts = transcript.split(CLAUSE).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size <= 1) return transcript
        val toks = parts.map { stripPoliteness(tokenize(it)) }
        val withVerb = toks.indexOfFirst { t -> leadingVerb(t) != null || innerVerb(t) != null }
        if (withVerb >= 0) {
            val t = toks[withVerb]
            val verb = leadingVerb(t) ?: innerVerb(t)?.first
            val at = if (leadingVerb(t) != null) 0 else innerVerb(t)!!.second
            val after = t.drop(at + (verb?.words?.size ?: 0)).filter { it.norm !in CONNECTORS }
            val next = toks.getOrNull(withVerb + 1)
            // The place continues in the next clause: nothing after the verb yet, or a short apposition that is not a new
            // clause ("quiero ir a mi U, la cooperativa"; but not "..., es lo único que te pido").
            val apposition = next != null && next.isNotEmpty() && next.size <= APPOSITION_WORDS &&
                next.first().norm !in BREAK_WORDS && leadingVerb(next) == null && innerVerb(next) == null &&
                !next.all { it.norm in NON_PLACE }
            return if ((after.isEmpty() || apposition) && withVerb + 1 < parts.size) parts[withVerb] + " " + parts[withVerb + 1] else parts[withVerb]
        }
        return parts.firstOrNull { stripPoliteness(tokenize(it)).isNotEmpty() } ?: transcript
    }

    /** A navigation verb of two words or more inside the clause (one-word verbs only count at its start). */
    private fun innerVerb(toks: List<Tok>): Pair<Phrase, Int>? {
        for (i in 1 until toks.size) {
            val v = VERBS.firstOrNull { it.words.size >= 2 && it.mode == null && startsWith(toks.drop(i), it.words) }
            if (v != null) return v to i
        }
        return null
    }

    /**
     * P10: a Colombian street address: a street word, then a number ("6A", "36"), then optionally "#" / "no" / "número"
     * and the house number ("36-41"), then optionally "barrio X". Returns ("Calle 6A #36-41", "San Vicente") or null.
     */
    private fun address(toks: List<Tok>): Pair<String, String?>? {
        if (toks.size < 2 || toks[0].norm !in STREET_WORDS || toks[1].norm.firstOrNull()?.isDigit() != true) return null
        val street = toks[0].orig.replaceFirstChar { it.uppercase() } + " " + toks[1].orig.uppercase()
        var rest = toks.drop(2)
        var house: String? = null
        val first = rest.firstOrNull()
        if (first != null && first.norm.startsWith("#") && first.norm.length > 1) {
            house = first.orig.removePrefix("#")
            rest = rest.drop(1)
        } else if (first != null && first.norm in HOUSE_MARKS && rest.size >= 2) {
            house = rest[1].orig.removePrefix("#")
            rest = rest.drop(2)
        } else if (first != null && first.norm.firstOrNull()?.isDigit() == true) {
            house = first.orig
            rest = rest.drop(1)
        }
        val b = rest.indexOfFirst { it.norm == "barrio" || it.norm == "neighborhood" }
        val barrio = if (b >= 0) join(rest.drop(b + 1)).ifEmpty { null } else null
        return (if (house != null) "$street #$house" else street) to barrio
    }

    /** P10: "alguna droguería", "el mejor restaurante", "un buen hotel": the quantifier is not part of the place. */
    private fun dropQuantifiers(toks: List<Tok>): List<Tok> {
        var t = toks
        var changed = true
        while (changed && t.size > 1) {
            changed = false
            for (q in QUANTIFIERS) if (startsWith(t, q.words) && t.size > q.words.size) {
                t = t.drop(q.words.size).dropWhile { it.norm in ARTICLES }
                changed = true
                break
            }
        }
        return t
    }

    /** The place stops before a word that opens another clause ("... cooperativa es lo único", "... porque ..."). */
    private fun cutAtBreak(toks: List<Tok>): List<Tok> {
        for (i in 1 until toks.size) {
            val w = toks[i].norm
            val next = toks.getOrNull(i + 1)?.norm
            if (w in BREAK_WORDS || (w == "por" && next == "favor") || (w == "y" && next in AND_FILLER)) return toks.take(i)
        }
        return toks
    }

    /** Cleans a place query from any source (also the remote interpreter): articles, trailing city, length, controls. */
    fun cleanPlaceQuery(text: String): String? = join(cleanPlace(tokenize(text))).takeIf { it.isNotEmpty() }

    // ---- helpers ----

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(COMBINING, "")

    private fun tokenize(text: String): List<Tok> = text
        .replace(CONTROL, " ")
        .replace(PUNCTUATION, " ")
        .split(WHITESPACE)
        .filter { it.isNotBlank() }
        .map { Tok(it, normalize(it)) }

    private fun join(toks: List<Tok>): String = toks.joinToString(" ") { it.orig }.take(MAX_PLACE_CHARS).trim()

    private fun leadingVerb(toks: List<Tok>): Phrase? = VERBS.firstOrNull { startsWith(toks, it.words) }

    private fun startsWith(toks: List<Tok>, words: List<String>): Boolean =
        toks.size >= words.size && words.indices.all { toks[it].norm == words[it] }

    private fun indexOf(toks: List<Tok>, words: List<String>): Int {
        for (i in 0..toks.size - words.size) if (words.indices.all { toks[i + it].norm == words[it] }) return i
        return -1
    }

    private fun stripPoliteness(input: List<Tok>): List<Tok> {
        var toks = input
        var changed = true
        while (changed && toks.isNotEmpty()) {
            changed = false
            for (p in POLITE) {
                if (startsWith(toks, p.words)) { toks = toks.drop(p.words.size); changed = true; break }
                if (toks.size >= p.words.size && startsWith(toks.takeLast(p.words.size), p.words)) {
                    toks = toks.dropLast(p.words.size); changed = true; break
                }
            }
        }
        return toks
    }

    private fun cleanPlace(input: List<Tok>): List<Tok> {
        var toks = input.dropWhile { it.norm in ARTICLES || it.norm in CONNECTORS }
        // Trailing region words: "... en Pasto", "... Pasto Nariño", "... in Pasto, Colombia".
        var changed = true
        while (changed && toks.size > 1) {
            changed = false
            val last = toks.last().norm
            val prev = toks[toks.size - 2].norm
            when {
                last == "colombia" -> { toks = toks.dropLast(1); changed = true }
                last == "narino" && prev == "pasto" -> { toks = toks.dropLast(1); changed = true }
                last == "pasto" -> { toks = toks.dropLast(1); changed = true }
                last in REGION_CONNECTORS -> { toks = toks.dropLast(1); changed = true }
            }
        }
        return toks.dropWhile { it.norm in ARTICLES }
    }

    private val CLAUSE = Regex("[.;!?¡¿,]")
    private val COMBINING = Regex("\\p{Mn}+")
    private val CONTROL = Regex("\\p{Cntrl}")
    private val PUNCTUATION = Regex("[¿?¡!.,;:\"“”«»()\\[\\]{}]")
    private val WHITESPACE = Regex("\\s+")

    // Longest phrases first so "quiero ir a" wins over "quiero".
    private val VERBS = listOf(
        Phrase("como hago para llegar a"), Phrase("how do i get to"), Phrase("i want to go to"), Phrase("i need to go to"),
        Phrase("como llego a"), Phrase("como llego al"), Phrase("como voy a"), Phrase("como voy al"), Phrase("como ir a"),
        Phrase("quiero ir a"), Phrase("quiero ir al"), Phrase("quisiera ir a"), Phrase("quisiera ir al"),
        Phrase("necesito llegar a"), Phrase("necesito llegar al"), Phrase("quiero llegar a"), Phrase("quiero llegar al"),
        Phrase("tengo que llegar a"), Phrase("tengo que llegar al"), Phrase("quisiera llegar a"), Phrase("i need to get to"),
        Phrase("necesito ir a"), Phrase("necesito ir al"), Phrase("tengo que ir a"), Phrase("tengo que ir al"),
        Phrase("me llevas a"), Phrase("me llevas al"), Phrase("me lleva a"), Phrase("me lleva al"),
        Phrase("lets go to"), Phrase("let s go to"), Phrase("take me to"), Phrase("bring me to"), Phrase("get me to"),
        Phrase("navigate to"), Phrase("directions to"), Phrase("route to"), Phrase("head to"),
        Phrase("walk to", TravelMode.Walk), Phrase("drive to", TravelMode.Drive), Phrase("drive me to", TravelMode.Drive),
        Phrase("llevame a"), Phrase("llevame al"), Phrase("llevame hasta"), Phrase("llevame hacia"), Phrase("llevame"),
        Phrase("lleveme a"), Phrase("lleveme al"), Phrase("llevenme a"), Phrase("guiame a"), Phrase("guiame al"),
        Phrase("dirigeme a"), Phrase("navega a"), Phrase("navega al"), Phrase("navegar a"), Phrase("navegar al"),
        Phrase("vamos a"), Phrase("vamos al"), Phrase("vamonos a"), Phrase("vamonos al"), Phrase("vamos para"),
        Phrase("ruta a"), Phrase("ruta al"), Phrase("ruta hacia"), Phrase("ruta hasta"),
        Phrase("ir a"), Phrase("ir al"), Phrase("ir hasta"), Phrase("quiero ir"), Phrase("quiero"), Phrase("go to")
    ).sortedByDescending { it.words.size }

    private val MODES = listOf(
        Phrase("a pie", TravelMode.Walk), Phrase("caminando", TravelMode.Walk), Phrase("andando", TravelMode.Walk),
        Phrase("caminar", TravelMode.Walk), Phrase("on foot", TravelMode.Walk), Phrase("walking", TravelMode.Walk),
        Phrase("by foot", TravelMode.Walk), Phrase("walk", TravelMode.Walk),
        Phrase("en carro", TravelMode.Drive), Phrase("en el carro", TravelMode.Drive), Phrase("en mi carro", TravelMode.Drive),
        Phrase("en moto", TravelMode.Drive), Phrase("en la moto", TravelMode.Drive), Phrase("en bus", TravelMode.Drive),
        Phrase("en buseta", TravelMode.Drive), Phrase("en taxi", TravelMode.Drive), Phrase("en auto", TravelMode.Drive),
        Phrase("en coche", TravelMode.Drive), Phrase("manejando", TravelMode.Drive), Phrase("conduciendo", TravelMode.Drive),
        Phrase("by car", TravelMode.Drive), Phrase("driving", TravelMode.Drive), Phrase("by bus", TravelMode.Drive),
        Phrase("drive", TravelMode.Drive)
    ).sortedByDescending { it.words.size }

    private val POLITE = listOf(
        Phrase("por favor"), Phrase("porfavor"), Phrase("porfa"), Phrase("please"), Phrase("gracias"), Phrase("thanks"),
        Phrase("thank you"), Phrase("hey"), Phrase("oye"), Phrase("ok"), Phrase("okay"), Phrase("hola"), Phrase("hello"),
        Phrase("hi"), Phrase("bueno"), Phrase("mejor"), Phrase("wgo"), Phrase("ahora"), Phrase("now"), Phrase("pues"),
        // P10 filler of long phrases
        Phrase("amigo"), Phrase("amiga"), Phrase("parce"), Phrase("hermano"), Phrase("hermana"), Phrase("mano"),
        Phrase("mira"), Phrase("y bueno"), Phrase("mas cosas"), Phrase("y mas cosas"), Phrase("muchas gracias"),
        Phrase("ya"), Phrase("rapido"), Phrase("es lo unico que te pido"), Phrase("eso es todo"), Phrase("buddy"),
        Phrase("man"), Phrase("dude"), Phrase("right now"), Phrase("thank you very much")
    ).sortedByDescending { it.words.size }

    /** P10: the street words of a Colombian address and the marks before its house number. */
    private val STREET_WORDS = setOf(
        "calle", "cl", "cll", "carrera", "cra", "kr", "cr", "avenida", "av", "transversal", "tv", "diagonal", "dg", "street", "avenue"
    )
    private val HOUSE_MARKS = setOf("#", "no", "numero", "nro", "n")

    /** P10: quantifiers and judgements before a kind of place. */
    private val QUANTIFIERS = listOf(
        Phrase("el mejor"), Phrase("la mejor"), Phrase("los mejores"), Phrase("las mejores"), Phrase("mejor"),
        Phrase("mejores"), Phrase("algun"), Phrase("alguna"), Phrase("alguno"), Phrase("algunos"), Phrase("algunas"),
        Phrase("cualquier"), Phrase("cualquiera"), Phrase("un buen"), Phrase("una buena"), Phrase("buen"), Phrase("buena"),
        Phrase("un"), Phrase("una"), Phrase("some"), Phrase("any"), Phrase("a good"), Phrase("the best"), Phrase("best"),
        Phrase("the nearest"), Phrase("nearest"), Phrase("good")
    ).sortedByDescending { it.words.size }

    /** P10: words that open another clause after the place. */
    private val BREAK_WORDS = setOf("es", "porque", "pues", "eso", "gracias", "please", "because", "thanks", "so")
    private val AND_FILLER = setOf("bueno", "mas", "ya", "nada")

    /** P10: a request for help or nothing at all, never a destination. */
    private val NON_PLACE = setOf("ayudame", "ayuda", "ayudenme", "auxilio", "help", "me", "no", "se", "nada", "nothing", "please")

    private val REPEAT = listOf(Phrase("repite"), Phrase("repitelo"), Phrase("repetir"), Phrase("otra vez"),
        Phrase("repeat"), Phrase("say again"), Phrase("say that again"))

    private val CANCEL_WORDS = setOf(
        "cancelar", "cancela", "cancelalo", "cancelala", "parar", "para", "detener", "detente", "deten", "alto", "stop",
        "cancel", "salir", "olvidalo", "quit", "exit", "terminar", "termina", "finalizar"
    )
    private val CANCEL_FILLER = setOf("la", "el", "ruta", "navegacion", "viaje", "todo", "ya", "the", "route", "navigation",
        "trip", "it", "everything", "now")

    private val CONNECTORS = setOf("a", "al", "hasta", "hacia", "para", "to", "towards", "toward", "for")
    private val ARTICLES = setOf("el", "la", "los", "las", "un", "una", "the", "a", "an")
    private val REGION_CONNECTORS = setOf("en", "de", "del", "in", "of")
    /** Vague words that are also landmark relations ("cerca de", "frente a"): a concrete name before them splits. */
    private val RELATION_WORDS = setOf("cerca", "frente", "junto", "near", "next", "opposite", "close")
    private val VAGUE_WORDS = setOf(
        "donde", "venden", "cerca", "frente", "junto", "esa", "ese", "aquel", "aquella", "alli", "alla", "lugar", "sitio",
        "algo", "famoso", "famosa", "queda", "llama", "where", "near", "nearby", "sell", "sells", "that", "which",
        "there", "place", "something", "famous", "called", "close", "opposite", "next"
    )
}
