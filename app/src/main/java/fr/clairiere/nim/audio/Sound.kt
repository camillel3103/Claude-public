package fr.clairiere.nim.audio

import androidx.compose.runtime.staticCompositionLocalOf

enum class Sfx { TAP, SELECT, DESELECT, REMOVE_WOOD, REMOVE_LEAF, REMOVE_STONE, REMOVE_SOFT, SPLIT, INVALID, TURN, WIN }

/** Abstraction du son, pour garder l'interface indépendante d'Android. */
interface SoundBoard {
    fun play(sfx: Sfx)
    fun setSfxVolume(volume: Float)
    fun setAmbienceVolume(volume: Float)
    fun setNight(night: Boolean)
}

object SilentSoundBoard : SoundBoard {
    override fun play(sfx: Sfx) {}
    override fun setSfxVolume(volume: Float) {}
    override fun setAmbienceVolume(volume: Float) {}
    override fun setNight(night: Boolean) {}
}

val LocalSound = staticCompositionLocalOf<SoundBoard> { SilentSoundBoard }
