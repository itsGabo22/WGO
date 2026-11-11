package com.wgo.navigator.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wgo.navigator.SettingsRepository
import com.wgo.navigator.ui.theme.MapStyleId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug builds only: persists a map style and / or the theme, for screenshots of every style at the same place. The
 * settings are read at launch, so restart the app afterwards (`am force-stop`, then `am start`).
 *
 * `adb shell am broadcast -a com.wgo.navigator.debug.MAP_STYLE -p com.wgo.navigator --es style NEON --ez dark true`
 */
class MapStyleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val style = intent.getStringExtra("style")?.let { name -> MapStyleId.entries.firstOrNull { it.name == name } }
        val dark = if (intent.hasExtra("dark")) intent.getBooleanExtra("dark", false) else null
        val repository = SettingsRepository(context.applicationContext)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                style?.let { repository.setMapStyle(it) }
                dark?.let { repository.setDarkMode(it) }
                Log.i("WGO_DEBUG", "map style=$style dark=$dark")
            } finally {
                pending.finish()
            }
        }
    }
}
