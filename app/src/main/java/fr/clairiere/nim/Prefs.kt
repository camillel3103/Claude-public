package fr.clairiere.nim

import android.content.Context
import fr.clairiere.nim.ui.AppSettings
import fr.clairiere.nim.ui.SettingsStore

/** Sauvegarde des réglages dans les SharedPreferences. */
class PrefsStore(context: Context) : SettingsStore {
    private val prefs = context.getSharedPreferences("clairiere", Context.MODE_PRIVATE)

    override fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            ambience = prefs.getFloat("ambience", d.ambience),
            effects = prefs.getFloat("effects", d.effects),
            haptics = prefs.getBoolean("haptics", d.haptics),
            night = prefs.getBoolean("night", d.night),
            player1 = prefs.getString("player1", d.player1) ?: d.player1,
            player2 = prefs.getString("player2", d.player2) ?: d.player2,
        )
    }

    override fun save(settings: AppSettings) {
        prefs.edit()
            .putFloat("ambience", settings.ambience)
            .putFloat("effects", settings.effects)
            .putBoolean("haptics", settings.haptics)
            .putBoolean("night", settings.night)
            .putString("player1", settings.player1)
            .putString("player2", settings.player2)
            .apply()
    }
}
