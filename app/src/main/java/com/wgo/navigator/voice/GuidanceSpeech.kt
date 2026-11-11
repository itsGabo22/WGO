package com.wgo.navigator.voice

import com.wgo.navigator.Language
import com.wgo.navigator.VoiceMode
import com.wgo.navigator.core.Maneuver
import com.wgo.navigator.core.RouteStep
import com.wgo.navigator.route.ManeuverText
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.stringFor
import java.util.Locale
import kotlin.math.roundToInt

/** One phrase to speak. [urgent] phrases flush the queue (spec section 8: the 50 m turn and the arrival). */
data class Phrase(val text: String, val urgent: Boolean)

/** Builds the spoken phrases of spec section 8 (ES / EN, numbers as words). Pure. */
object GuidancePhrases {
    /** "En doscientos metros, gira a la derecha por Carrera 35". */
    fun approaching(step: RouteStep, distanceM: Double, language: Language): Phrase {
        val action = when {
            step.maneuver == Maneuver.ROUNDABOUT && step.exit != null -> roundabout(step.exit, language)
            else -> ManeuverText.instruction(step, language)
        }
        val text = String.format(locale(language), stringFor(StringKey.SAY_IN, language), distance(distanceM, language), lowerFirst(action))
        return Phrase(text, urgent = false)
    }

    /** "Gira a la derecha" (no street: the turn is now). */
    fun atTurn(step: RouteStep, language: Language): Phrase {
        val text = if (step.maneuver == Maneuver.ROUNDABOUT && step.exit != null) roundabout(step.exit, language)
        else stringFor(ManeuverText.keyFor(step.maneuver), language)
        return Phrase(text, urgent = true)
    }

    fun reroute(language: Language) = Phrase(stringFor(StringKey.SAY_REROUTE, language), urgent = false)
    fun arrived(language: Language) = Phrase(stringFor(StringKey.SAY_ARRIVED, language), urgent = true)
    fun gpsLost(language: Language) = Phrase(stringFor(StringKey.SAY_GPS_LOST, language), urgent = false)
    fun lostRoute(language: Language) = Phrase(stringFor(StringKey.SAY_LOST_ROUTE, language), urgent = false)
    fun sample(language: Language) = Phrase(stringFor(StringKey.SAY_SAMPLE, language), urgent = true)

    /** Meters rounded to 10 under 1 km ("doscientos metros"), whole kilometres above ("un kilómetro", "dos kilómetros"). */
    fun distance(meters: Double, language: Language): String {
        if (meters < 995) {
            val m = (meters / 10).roundToInt().coerceAtLeast(1) * 10
            return String.format(locale(language), stringFor(StringKey.SAY_METERS, language), NumberWords.of(m, language))
        }
        val km = (meters / 1000).roundToInt().coerceAtLeast(1)
        return if (km == 1) stringFor(StringKey.SAY_ONE_KILOMETER, language)
        else String.format(locale(language), stringFor(StringKey.SAY_KILOMETERS, language), NumberWords.of(km, language))
    }

    private fun roundabout(exit: Int, language: Language) =
        String.format(locale(language), stringFor(StringKey.SAY_ROUNDABOUT_EXIT, language), NumberWords.of(exit, language))

    private fun lowerFirst(s: String) = if (s.isEmpty()) s else s.substring(0, 1).lowercase(Locale.ROOT) + s.substring(1)

    private fun locale(language: Language) = if (language == Language.ES) Locale("es", "CO") else Locale.US
}

/** Whether guidance is spoken now (spec section 8): OFF never, AUTO only with a headset, ALWAYS on any output. */
object GuidancePolicy {
    fun shouldSpeak(mode: VoiceMode, headsetConnected: Boolean): Boolean = when (mode) {
        VoiceMode.Off -> false
        VoiceMode.Auto -> headsetConnected
        VoiceMode.Always -> true
    }
}

/**
 * The once-only rules of spec section 8: each maneuver's 200 m and 50 m phrases once, GPS lost once until fixes
 * return, the lost-route phrase once per loss, a reroute once per detection. Pure; one instance per trip.
 */
class GuidanceDedupe {
    private val spoken = HashSet<String>()
    private var gpsLostSpoken = false
    private var lostRouteSpoken = false

    fun approaching(step: RouteStep): Boolean = spoken.add("200:${step.location.lat},${step.location.lon}")
    fun atTurn(step: RouteStep): Boolean = spoken.add("50:${step.location.lat},${step.location.lon}")

    fun gpsLost(): Boolean = !gpsLostSpoken.also { gpsLostSpoken = true }
    fun gpsRecovered() {
        gpsLostSpoken = false
    }

    fun lostRoute(): Boolean = !lostRouteSpoken.also { lostRouteSpoken = true }
    fun routeBack() {
        lostRouteSpoken = false
    }

    /** A new route after a reroute: the old maneuvers' phrases may be spoken again for the new steps. */
    fun newRoute() {
        spoken.clear()
    }
}
