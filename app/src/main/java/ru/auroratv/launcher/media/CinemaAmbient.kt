package ru.auroratv.launcher.media

import android.animation.ValueAnimator
import android.content.Context
import android.media.MediaPlayer
import ru.auroratv.launcher.R

/**
 * Тихая фоновая музыка кинозала. Одна и та же для всех каналов:
 * плавно появляется, зациклена и не перебивает фильм.
 */
class CinemaAmbient(private val context: Context) {

    private var player: MediaPlayer? = null
    private var animator: ValueAnimator? = null
    private var level = 0f

    fun start(target: Float = DEFAULT_VOLUME) {
        val mediaPlayer = ensurePlayer() ?: return
        animator?.cancel()
        try {
            if (!mediaPlayer.isPlaying) mediaPlayer.start()
        } catch (_: Throwable) {
            return
        }
        animator = ValueAnimator.ofFloat(level, target).apply {
            duration = 1400L
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                level = value
                try {
                    mediaPlayer.setVolume(value, value)
                } catch (_: Throwable) {
                    // Плеер мог быть освобожден при выходе.
                }
            }
            start()
        }
    }

    fun pause() {
        animator?.cancel()
        animator = null
        try {
            player?.pause()
        } catch (_: Throwable) {
            // Музыка необязательна.
        }
        level = 0f
    }

    fun release() {
        animator?.cancel()
        animator = null
        try {
            player?.release()
        } catch (_: Throwable) {
            // Игнорируем.
        }
        player = null
        level = 0f
    }

    private fun ensurePlayer(): MediaPlayer? {
        player?.let { return it }
        player = try {
            MediaPlayer.create(context, R.raw.cinema_ambient)?.apply {
                isLooping = true
                setVolume(0f, 0f)
            }
        } catch (_: Throwable) {
            null
        }
        return player
    }

    companion object {
        const val DEFAULT_VOLUME = 0.55f
    }
}
