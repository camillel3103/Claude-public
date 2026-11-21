package fr.clairiere.nim.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import fr.clairiere.nim.audio.LocalSound
import fr.clairiere.nim.audio.SoundBoard
import fr.clairiere.nim.game.GameConfig
import fr.clairiere.nim.game.Variant

/** Réglages de l'application, conservés d'une session à l'autre. */
data class AppSettings(
    val ambience: Float = 0.55f,
    val effects: Float = 0.8f,
    val haptics: Boolean = true,
    val night: Boolean = false,
    val player1: String = "Joueur 1",
    val player2: String = "Joueur 2",
)

interface SettingsStore {
    fun load(): AppSettings
    fun save(settings: AppSettings)
}

sealed interface Screen {
    data object Home : Screen
    data object Variants : Screen
    data class Setup(val variant: Variant) : Screen
    data class Play(val config: GameConfig, val round: Int) : Screen
    data object Settings : Screen
}

/** Permet à la plateforme de fournir la gestion du bouton « retour ». */
typealias BackHandlerHook = @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit

@Composable
fun ClairiereApp(
    sound: SoundBoard,
    store: SettingsStore,
    backHandler: BackHandlerHook,
    initialScreen: Screen = Screen.Home,
    onNightChanged: (Boolean) -> Unit = {},
) {
    var settings by remember { mutableStateOf(store.load()) }
    var screen by remember { mutableStateOf(initialScreen) }
    val scores = remember { mutableStateMapOf<String, Int>() }
    var round by remember { mutableStateOf(0) }

    fun update(s: AppSettings) {
        settings = s
        store.save(s)
    }

    LaunchedEffect(settings.ambience, settings.effects, settings.night) {
        sound.setAmbienceVolume(settings.ambience)
        sound.setSfxVolume(settings.effects)
        sound.setNight(settings.night)
        onNightChanged(settings.night)
    }

    fun startGame(config: GameConfig) {
        round++
        screen = Screen.Play(config, round)
    }

    MaterialTheme {
        ClairiereTheme(night = settings.night) {
            androidx.compose.runtime.CompositionLocalProvider(LocalSound provides sound) {
                Box(Modifier.fillMaxSize()) {
                    ForestBackground()
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = {
                            (fadeIn(tween(500, delayMillis = 120)) + scaleIn(tween(500, delayMillis = 120), initialScale = 0.97f)) togetherWith
                                (fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 1.02f))
                        },
                        label = "screen",
                    ) { s ->
                        when (s) {
                            Screen.Home -> HomeScreen(
                                onPlay = { screen = Screen.Variants },
                                onSettings = { screen = Screen.Settings },
                            )
                            Screen.Variants -> VariantsScreen(
                                onBack = { screen = Screen.Home },
                                onPick = { screen = Screen.Setup(it) },
                            )
                            is Screen.Setup -> SetupScreen(
                                variant = s.variant,
                                settings = settings,
                                onBack = { screen = Screen.Variants },
                                onStart = { config ->
                                    update(settings.copy(player1 = config.players[0], player2 = config.players[1]))
                                    startGame(config)
                                },
                            )
                            is Screen.Play -> GameScreen(
                                config = s.config,
                                haptics = settings.haptics,
                                scores = scores,
                                backHandler = backHandler,
                                onWin = { name -> scores[name] = (scores[name] ?: 0) + 1 },
                                onRematch = { loser -> startGame(s.config.copy(firstPlayer = loser)) },
                                onOtherGame = { screen = Screen.Variants },
                                onQuit = { screen = Screen.Setup(s.config.variant) },
                            )
                            Screen.Settings -> SettingsScreen(
                                settings = settings,
                                onChange = ::update,
                                onBack = { screen = Screen.Home },
                            )
                        }
                    }
                    // Bouton « retour » du système pour les écrans simples (la partie gère le sien)
                    val current = screen
                    backHandler(current !is Screen.Home && current !is Screen.Play) {
                        screen = when (current) {
                            is Screen.Setup -> Screen.Variants
                            else -> Screen.Home
                        }
                    }
                }
            }
        }
    }
}
