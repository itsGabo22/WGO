package com.wgo.navigator.route

import com.wgo.navigator.Language
import com.wgo.navigator.core.Maneuver
import com.wgo.navigator.core.RouteStep
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.stringFor
import java.util.Locale

/** Turn instructions in the UI language, built from the maneuver and the street name (never from server text). */
object ManeuverText {
    fun instruction(step: RouteStep, language: Language): String {
        val base = when (step.maneuver) {
            Maneuver.ROUNDABOUT -> step.exit?.let {
                String.format(locale(language), stringFor(StringKey.MAN_ROUNDABOUT_EXIT, language), it)
            } ?: stringFor(StringKey.MAN_ROUNDABOUT, language)
            else -> stringFor(keyFor(step.maneuver), language)
        }
        val street = step.street.trim()
        return if (street.isEmpty() || step.maneuver == Maneuver.ARRIVE) base
        else String.format(locale(language), stringFor(StringKey.MAN_ONTO, language), base, street)
    }

    fun keyFor(maneuver: Maneuver): StringKey = when (maneuver) {
        Maneuver.DEPART -> StringKey.MAN_DEPART
        Maneuver.STRAIGHT -> StringKey.MAN_STRAIGHT
        Maneuver.SLIGHT_LEFT -> StringKey.MAN_SLIGHT_LEFT
        Maneuver.LEFT -> StringKey.MAN_LEFT
        Maneuver.SHARP_LEFT -> StringKey.MAN_SHARP_LEFT
        Maneuver.SLIGHT_RIGHT -> StringKey.MAN_SLIGHT_RIGHT
        Maneuver.RIGHT -> StringKey.MAN_RIGHT
        Maneuver.SHARP_RIGHT -> StringKey.MAN_SHARP_RIGHT
        Maneuver.U_TURN -> StringKey.MAN_UTURN
        Maneuver.ROUNDABOUT -> StringKey.MAN_ROUNDABOUT
        Maneuver.ARRIVE -> StringKey.MAN_ARRIVE
    }

    private fun locale(language: Language) = if (language == Language.ES) Locale("es", "CO") else Locale.US
}
