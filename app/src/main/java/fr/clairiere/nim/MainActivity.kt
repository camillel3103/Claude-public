package fr.clairiere.nim

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import fr.clairiere.nim.ui.ClairiereApp

class MainActivity : ComponentActivity() {

    private lateinit var sound: AndroidSound

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        volumeControlStream = AudioManager.STREAM_MUSIC
        sound = AndroidSound(this)
        val store = PrefsStore(this)
        store.load().let {
            sound.setSfxVolume(it.effects)
            sound.setAmbienceVolume(it.ambience)
            sound.setNight(it.night)
        }
        setContent {
            ClairiereApp(
                sound = sound,
                store = store,
                backHandler = { enabled, onBack -> BackHandler(enabled, onBack) },
                onNightChanged = { night ->
                    // Icônes de la barre d'état sombres le jour, claires la nuit
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !night
                        isAppearanceLightNavigationBars = !night
                    }
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        sound.resume()
    }

    override fun onPause() {
        sound.pause()
        super.onPause()
    }

    override fun onDestroy() {
        sound.release()
        super.onDestroy()
    }
}
