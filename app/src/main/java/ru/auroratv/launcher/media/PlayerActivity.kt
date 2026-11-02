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
import ru.auroratv.launcher.core.InfoRepository
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerActivity : Activity() {

    private var player: ExoPlayer? = null
    private var titleView: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var hideTitle: Runnable? = null
    private var infoChannel = false
    private var infoUris: ArrayList<String> = arrayListOf()
    private var infoTitles: ArrayList<String> = arrayListOf()
    private var tipView: TextView? = null
    private var hideTip: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uriStrings = intent.getStringArrayListExtra(EXTRA_URIS)
        if (uriStrings.isNullOrEmpty()) {
            finish()
            return
        }
        val titles = intent.getStringArrayListExtra(EXTRA_TITLES)
        val start = intent.getIntExtra(EXTRA_START, 0).coerceIn(0, uriStrings.lastIndex)
        infoChannel = intent.getBooleanExtra(EXTRA_INFO_CHANNEL, false)
        infoUris = ArrayList(uriStrings)
        infoTitles = ArrayList(titles ?: emptyList())

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
        if (infoChannel) setupInfoTips(root)

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
                title.text = if (infoChannel) {
                    infoTitles.getOrNull(index).orEmpty()
                } else {
                    titles?.getOrNull(index)
                        ?: mediaItem?.mediaMetadata?.title
                        ?: ""
                }
                title.alpha = 1f
                scheduleTitleHide()
                if (infoChannel) showTip()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (infoChannel && playbackState == Player.STATE_ENDED) {
                    restartInfoPlaylist()
                }
            }
        })
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        playerView.requestFocus()
        if (infoChannel) {
            handler.postDelayed({ showTip() }, 900L)
        }
    }

    private fun setupInfoTips(root: FrameLayout) {
        val tip = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(dp(22), dp(10), dp(22), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(220, 8, 12, 18))
                cornerRadius = dp(14).toFloat()
            }
            alpha = 0f
            maxWidth = dp(900)
        }
        tipView = tip
        root.addView(
            tip,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                setMargins(dp(40), 0, dp(40), dp(100))
            }
        )
    }

    private fun showTip() {
        val tip = tipView ?: return
        val tips = InfoRepository.tips()
        if (tips.isEmpty()) return
        tip.text = tips.random()
        hideTip?.let { handler.removeCallbacks(it) }
        tip.animate().cancel()
        tip.alpha = 0f
        tip.animate().alpha(1f).setDuration(220).start()
        val hide = Runnable {
            tip.animate().alpha(0f).setDuration(350).start()
        }
        hideTip = hide
        handler.postDelayed(hide, 4500L)
    }

    private fun restartInfoPlaylist() {
        val current = player ?: return
        if (infoUris.size <= 1) {
            current.seekTo(0)
            current.play()
            showTip()
            return
        }
        val order = infoUris.indices.shuffled()
        val newUris = ArrayList(order.map { infoUris[it] })
        val newTitles = ArrayList(order.map { infoTitles.getOrNull(it).orEmpty() })
        infoUris = newUris
        infoTitles = newTitles
        val items = newUris.mapIndexed { index, value ->
            MediaItem.Builder()
                .setUri(Uri.parse(value))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(newTitles.getOrNull(index).orEmpty())
                        .build()
                )
                .build()
        }
        current.setMediaItems(items, 0, 0L)
        current.prepare()
        current.playWhenReady = true
        showTip()
    }

    override fun onStop() {
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        hideTitle?.let { handler.removeCallbacks(it) }
        hideTip?.let { handler.removeCallbacks(it) }
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
        const val EXTRA_INFO_CHANNEL = "info_channel"
    }
}
