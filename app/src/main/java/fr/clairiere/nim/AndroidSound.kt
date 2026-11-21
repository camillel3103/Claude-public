package fr.clairiere.nim

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import fr.clairiere.nim.audio.Sfx
import fr.clairiere.nim.audio.SoundBoard
import kotlin.random.Random

/**
 * Sons Android : SoundPool pour les effets courts, MediaPlayer en boucle pour l'ambiance,
 * avec des fondus doux à l'ouverture, à la mise en pause et lors du passage jour / nuit.
 */
class AndroidSound(private val context: Context) : SoundBoard {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids: Map<Sfx, Int> = mapOf(
        Sfx.TAP to R.raw.sfx_tap,
        Sfx.SELECT to R.raw.sfx_select,
        Sfx.DESELECT to R.raw.sfx_deselect,
        Sfx.REMOVE_WOOD to R.raw.sfx_remove_wood,
        Sfx.REMOVE_LEAF to R.raw.sfx_remove_leaf,
        Sfx.REMOVE_STONE to R.raw.sfx_remove_stone,
        Sfx.REMOVE_SOFT to R.raw.sfx_remove_soft,
        Sfx.SPLIT to R.raw.sfx_split,
        Sfx.INVALID to R.raw.sfx_invalid,
        Sfx.TURN to R.raw.sfx_turn,
        Sfx.WIN to R.raw.sfx_win,
    ).mapValues { (_, res) -> pool.load(context, res, 1) }

    private var sfxVolume = 0.8f
    private var ambienceVolume = 0.55f
    private var night = false
    private var resumed = false

    private var player: MediaPlayer? = null
    private var playerNight = false
    private var playerLevel = 0f
    private var fadeAnim: ValueAnimator? = null

    override fun play(sfx: Sfx) {
        val id = ids[sfx] ?: return
        if (sfxVolume <= 0.001f) return
        // Légère variation de hauteur : les sons répétés paraissent plus naturels
        val rate = when (sfx) {
            Sfx.WIN, Sfx.TURN -> 1f
            else -> Random.nextDouble(0.93, 1.07).toFloat()
        }
        val v = sfxVolume * sfxVolume
        pool.play(id, v, v, 1, 0, rate)
    }

    override fun setSfxVolume(volume: Float) {
        sfxVolume = volume.coerceIn(0f, 1f)
    }

    override fun setAmbienceVolume(volume: Float) {
        ambienceVolume = volume.coerceIn(0f, 1f)
        if (fadeAnim?.isRunning != true) applyLevel(target())
        if (resumed && player == null && ambienceVolume > 0f) startAmbience()
    }

    override fun setNight(night: Boolean) {
        this.night = night
        if (resumed && player != null && playerNight != night) {
            // Fondu enchaîné vers l'autre ambiance
            val old = player
            player = null
            fade(old, playerLevel, 0f, 1200) { old?.release() }
            startAmbience()
        }
    }

    fun resume() {
        resumed = true
        if (player == null) startAmbience() else {
            player?.start()
            fade(player, 0f, target(), 2000)
        }
    }

    fun pause() {
        resumed = false
        fadeAnim?.cancel()
        player?.let { runCatching { it.pause() } }
    }

    fun release() {
        fadeAnim?.cancel()
        player?.release()
        player = null
        pool.release()
    }

    private fun target() = ambienceVolume * ambienceVolume * 0.9f

    private fun startAmbience() {
        if (ambienceVolume <= 0f) return
        val res = if (night) R.raw.ambience_night else R.raw.ambience_day
        val mp = MediaPlayer.create(context, res) ?: return
        mp.isLooping = true
        mp.setVolume(0f, 0f)
        mp.start()
        player = mp
        playerNight = night
        playerLevel = 0f
        fade(mp, 0f, target(), 2500)
    }

    private fun applyLevel(level: Float) {
        playerLevel = level
        player?.let { runCatching { it.setVolume(level, level) } }
    }

    private fun fade(mp: MediaPlayer?, from: Float, to: Float, ms: Long, onEnd: (() -> Unit)? = null) {
        mp ?: return
        val isMain = mp === player
        if (isMain) fadeAnim?.cancel()
        val anim = ValueAnimator.ofFloat(from, to).apply {
            duration = ms
            addUpdateListener {
                val v = it.animatedValue as Float
                if (mp === player) playerLevel = v
                runCatching { mp.setVolume(v, v) }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd?.invoke()
                }
            })
        }
        if (isMain) fadeAnim = anim
        anim.start()
    }
}
