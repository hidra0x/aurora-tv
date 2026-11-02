package ru.auroratv.launcher.media

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerActivity : Activity() {

    private var player: ExoPlayer? = null
    private var titleView: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var hideTitle: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uriStrings = intent.getStringArrayListExtra(EXTRA_URIS)
        if (uriStrings.isNullOrEmpty()) {
            finish()
            return
        }
        val titles = intent.getStringArrayListExtra(EXTRA_TITLES)
        val start = intent.getIntExtra(EXTRA_START, 0).coerceIn(0, uriStrings.lastIndex)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)

        val playerView = PlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            useController = true
            controllerShowTimeoutMs = 3500
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(Color.BLACK)
            keepScreenOn = true
            isFocusable = true
        }
        root.addView(playerView)

        val title = TextView(this).apply {
            text = titles?.getOrNull(start).orEmpty()
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(dp(22), dp(12), dp(22), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.argb(170, 0, 0, 0))
                cornerRadius = dp(14).toFloat()
            }
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        titleView = title
        root.addView(
            title,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).apply {
                setMargins(dp(28), dp(22), dp(28), 0)
            }
        )
        scheduleTitleHide()

        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        playerView.player = exoPlayer
        exoPlayer.setAudioAttributes(AudioAttributes.DEFAULT, true)
        exoPlayer.setHandleAudioBecomingNoisy(true)
        if (intent.getBooleanExtra(EXTRA_LOOP, false)) {
            exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
        }

        val items = uriStrings.mapIndexed { index, value ->
            MediaItem.Builder()
                .setUri(Uri.parse(value))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(titles?.getOrNull(index).orEmpty())
                        .build()
                )
                .build()
        }
        exoPlayer.setMediaItems(items, start, 0L)
        exoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = exoPlayer.currentMediaItemIndex
                title.text = titles?.getOrNull(index)
                    ?: mediaItem?.mediaMetadata?.title
                    ?: ""
                title.alpha = 1f
                scheduleTitleHide()
            }
        })
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        playerView.requestFocus()
    }

    override fun onStop() {
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        hideTitle?.let { handler.removeCallbacks(it) }
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                finish()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                val current = player ?: return true
                if (current.isPlaying) current.pause() else current.play()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                player?.play()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                player?.pause()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun scheduleTitleHide() {
        val title = titleView ?: return
        hideTitle?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            title.animate().alpha(0f).setDuration(450).start()
        }
        hideTitle = runnable
        handler.postDelayed(runnable, 4000)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    companion object {
        const val EXTRA_URIS = "uris"
        const val EXTRA_TITLES = "titles"
        const val EXTRA_START = "start"
        const val EXTRA_LOOP = "loop"
    }
}
