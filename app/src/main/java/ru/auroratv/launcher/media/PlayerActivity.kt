package ru.auroratv.launcher.media

import android.app.Activity
import android.content.Intent
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
import ru.auroratv.launcher.core.ChannelCatalog
import ru.auroratv.launcher.core.ChannelsRepository
import ru.auroratv.launcher.core.InfoRepository
import ru.auroratv.launcher.ui.TickerView
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
    private var tickerView: TickerView? = null
    private var channelNumber = -1
    private var currentChannelName = ""
    private var wasPlaying = false
    private lateinit var channelOsd: TextView
    private val channelInput = StringBuilder()
    private var osdRunnable: Runnable? = null

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
        channelNumber = intent.getIntExtra(EXTRA_CHANNEL, if (infoChannel) 0 else -1)
        currentChannelName = titles?.getOrNull(start).orEmpty()

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
        if (infoChannel) title.visibility = View.GONE
        scheduleTitleHide()
        if (infoChannel) setupInfoTips(root)

        channelOsd = TextView(this).apply {
            textSize = 36f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(30), dp(18), dp(30), dp(18))
            background = GradientDrawable().apply {
                setColor(Color.argb(225, 0, 0, 0))
                cornerRadius = dp(18).toFloat()
            }
            visibility = View.GONE
        }
        root.addView(
            channelOsd,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

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
                } else if (currentChannelName.isNotBlank()) {
                    currentChannelName
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
        val ticker = TickerView(this).apply {
            setText(InfoRepository.tickerText())
        }
        tickerView = ticker
        root.addView(
            ticker,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(56),
                Gravity.BOTTOM
            )
        )
    }

    private fun showTip() {
        tickerView?.setText(InfoRepository.tickerText())
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

    override fun onStart() {
        super.onStart()
        if (wasPlaying) player?.play()
        startMaintenanceWatch()
    }

    private val maintenanceWatch = object : Runnable {
        override fun run() {
            if (ru.auroratv.launcher.core.MaintenanceScreen.isActive(this@PlayerActivity)) {
                ru.auroratv.launcher.core.MaintenanceScreen.show(this@PlayerActivity)
                return
            }
            handler.postDelayed(this, 1500L)
        }
    }

    private fun startMaintenanceWatch() {
        handler.removeCallbacks(maintenanceWatch)
        handler.postDelayed(maintenanceWatch, 1000L)
    }

    override fun onStop() {
        wasPlaying = player?.isPlaying == true
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacks(maintenanceWatch)
        hideTitle?.let { handler.removeCallbacks(it) }
        osdRunnable?.let { handler.removeCallbacks(it) }
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        digitFor(keyCode)?.let {
            handleDigit(it)
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                finish()
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                switchRelative(1)
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                switchRelative(-1)
                return true
            }
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_GUIDE -> {
                openGuide()
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

    private fun digitFor(keyCode: Int): Int? = when (keyCode) {
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> keyCode - KeyEvent.KEYCODE_0
        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> keyCode - KeyEvent.KEYCODE_NUMPAD_0
        else -> null
    }

    private fun handleDigit(digit: Int) {
        if (channelInput.length >= 2) channelInput.clear()
        channelInput.append(digit)
        val number = channelInput.toString().toIntOrNull() ?: return
        val target = ChannelCatalog.byNumber(this, number)
        showChannelOsd("$number  ${target?.name ?: "канал не найден"}")
        osdRunnable?.let { handler.removeCallbacks(it) }
        val switch = Runnable {
            channelInput.clear()
            target?.let { switchTo(it) }
        }
        osdRunnable = switch
        handler.postDelayed(switch, 2500L)
    }

    private fun switchRelative(delta: Int) {
        val target = ChannelCatalog.next(this, channelNumber, delta) ?: return
        switchTo(target)
    }

    private fun switchTo(target: ru.auroratv.launcher.core.GuideChannel) {
        if (target.kind == "tv") {
            if (target.state != "live") {
                showChannelOsd("${target.number}  профилактика")
                return
            }
            val tv = ChannelsRepository.bundled(this).getOrNull(target.number - 1) ?: return
            val current = player ?: return
            infoChannel = false
            tickerView?.visibility = View.GONE
            titleView?.visibility = View.VISIBLE
            channelNumber = target.number
            currentChannelName = tv.name
            titleView?.text = tv.name
            titleView?.alpha = 1f
            current.setMediaItem(MediaItem.fromUri(Uri.parse(tv.url)))
            current.prepare()
            current.playWhenReady = true
            showChannelOsd("${target.number}  ${tv.name}")
        } else if (ChannelCatalog.open(this, target)) {
            finish()
        }
    }

    private fun openGuide() {
        startActivityForResult(
            Intent(this, ChannelGuideActivity::class.java)
                .putExtra(ChannelGuideActivity.EXTRA_CURRENT, channelNumber)
                .putExtra(ChannelGuideActivity.EXTRA_FROM_PLAYER, true),
            ChannelGuideActivity.REQUEST_GUIDE
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ChannelGuideActivity.REQUEST_GUIDE && resultCode == RESULT_OK) {
            val number = data?.getIntExtra(ChannelGuideActivity.EXTRA_SELECTED, -1) ?: -1
            val target = ChannelCatalog.byNumber(this, number) ?: return
            switchTo(target)
        }
    }

    private fun showChannelOsd(text: String) {
        if (!::channelOsd.isInitialized) return
        channelOsd.text = text
        channelOsd.visibility = View.VISIBLE
        channelOsd.alpha = 0f
        channelOsd.animate().alpha(1f).setDuration(140L).start()
        osdRunnable?.let { handler.removeCallbacks(it) }
        val hide = Runnable {
            channelOsd.animate().alpha(0f).setDuration(250L).withEndAction {
                channelOsd.visibility = View.GONE
            }.start()
        }
        osdRunnable = hide
        handler.postDelayed(hide, 2800L)
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
        const val EXTRA_CHANNEL = "channel_number"
    }
}
