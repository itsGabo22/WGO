package com.wgo.navigator.voice

/** The engine row's rules (spec section 7, voice settings, P8). Pure, for unit tests. */
object VoiceSettingsRules {
    /**
     * The engine in use when the user chose none: the system's default (`tts_default_synth`) when it is installed,
     * else the first engine the package manager lists (the Galaxy Watch5 Pro reports no default setting; Samsung's
     * engine comes first and is the one the platform binds).
     */
    fun defaultEngine(systemSetting: String?, installed: List<String>): String? =
        systemSetting?.takeIf { it in installed } ?: installed.firstOrNull()

    /** The row's next engine after [current] (wraps around); null when nothing is installed. */
    fun nextEngine(current: String?, installed: List<String>): String? {
        if (installed.isEmpty()) return null
        val i = installed.indexOf(current)
        return installed[(i + 1).mod(installed.size)]
    }
}
