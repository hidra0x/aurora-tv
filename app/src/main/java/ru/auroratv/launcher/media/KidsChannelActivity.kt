package ru.auroratv.launcher.media

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import ru.auroratv.launcher.R
import ru.auroratv.launcher.core.Cartoon
import ru.auroratv.launcher.core.ChannelCatalog
import ru.auroratv.launcher.core.ChannelControl
import ru.auroratv.launcher.core.GuideChannel
import ru.auroratv.launcher.core.KidsProgram
import ru.auroratv.launcher.core.KidsChannelConfig
import ru.auroratv.launcher.core.KidsRepository
import ru.auroratv.launcher.core.ManagedChannel
import ru.auroratv.launcher.core.NewYearEvent
import ru.auroratv.launcher.core.SubtitleCue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Детские каналы 15–18 «Совёнок»: сказки, наука и английский с
 * анонсами, логотип канала в углу и ночной режим с часами и таблицей.
 * Вещание 05:00–22:00, вне этого окна — «Спокойной ночи, дети».
 */
@androidx.annotation.OptIn(UnstableApi::class)
class KidsChannelActivity : Activity() {

    private enum class Mode {
        NIGHT,
        STARTUP,
        ANNOUNCE_LOGO,
        PREVIEW,
        TITLE,
        ANNOUNCE_LOGO2,
        PROMO,
        CARTOON,
        MAINTENANCE
    }

    private lateinit var channel: KidsChannelConfig
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var cornerLogo: ImageView
    private lateinit var centerLogo: ImageView
    private lateinit var subtitleView: TextView
    private lateinit var brandBar: LinearLayout
    private lateinit var announcementCard: LinearLayout
    private lateinit var announcementLabel: TextView
    private lateinit var announcementTitle: TextView
    private lateinit var announcementMeta: TextView
    private lateinit var announcementDescription: TextView
    private lateinit var nightView: FrameLayout
    private lateinit var nightClock: TextView
    private lateinit var nightDate: TextView
    private lateinit var nightSchedule: LinearLayout
    private lateinit var maintenanceView: FrameLayout
    private lateinit var maintenanceStatus: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var cartoons: List<Cartoon> = emptyList()
    private var currentIndex = 0
    private var mode = Mode.NIGHT
    private var nightMusic: MediaPlayer? = null
    private var nightTick: Runnable? = null
    private var maintenanceTick: Runnable? = null
    private var subtitleTick: Runnable? = null
    private var subtitleCues: List<SubtitleCue> = emptyList()
    private var stepToken = 0
    private var wasPlaying = false
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val number = intent.getIntExtra(EXTRA_CHANNEL, KidsRepository.DEFAULT_CHANNEL)
        val selected = KidsRepository.channelFor(number)
        if (selected == null) {
            finish()
            return
        }
        channel = selected
        cartoons = KidsRepository.cartoons(this, channel)
        if (cartoons.isEmpty()) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)

        playerView = PlayerView(this).apply {
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

        subtitleView = TextView(this).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(10), dp(20), dp(10))
            maxWidth = dp(1000)
            maxLines = 3
            background = GradientDrawable().apply {
                setColor(Color.argb(205, 0, 0, 0))
                cornerRadius = dp(12).toFloat()
            }
            visibility = View.GONE
            elevation = dp(10).toFloat()
        }
        root.addView(
            subtitleView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                setMargins(dp(80), 0, dp(80), dp(74))
            }
        )

        brandBar = buildBrandBar()
        root.addView(
            brandBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(58),
                Gravity.BOTTOM
            )
        )
        playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { updateBrandBar() }
        )

        cornerLogo = buildCornerLogo()
        root.addView(
            cornerLogo,
            FrameLayout.LayoutParams(dp(226), dp(59), Gravity.TOP or Gravity.START).apply {
                setMargins(dp(24), dp(18), 0, 0)
            }
        )

        centerLogo = buildCenterLogo()
        root.addView(
            centerLogo,
            FrameLayout.LayoutParams(dp(310), dp(310), Gravity.CENTER)
        )

        announcementCard = buildAnnouncementCard()
        root.addView(
            announcementCard,
            FrameLayout.LayoutParams(dp(760), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
                setMargins(dp(40), dp(40), dp(40), dp(40))
            }
        )

        nightView = buildNightView()
        root.addView(nightView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        maintenanceView = buildMaintenanceView()
        root.addView(
            maintenanceView,
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        playerView.player = exoPlayer
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onEnded()
            }

            override fun onPlayerError(error: PlaybackException) {
                onPlaybackError()
            }
        })
        exoPlayer.prepare()
        playerView.requestFocus()

        started = true
        if (KidsRepository.isBroadcastTime()) {
            startBroadcast()
        } else {
            showNight()
        }
        scheduleMaintenanceCheck()
    }

    private fun logoRes(): Int =
        if (NewYearEvent.isActive(this)) channel.newYearLogoRes else channel.logoRes

    private fun bannerRes(): Int =
        if (NewYearEvent.isActive(this)) channel.newYearBannerRes else channel.bannerRes

    private fun buildCornerLogo(): ImageView = ImageView(this).apply {
        setImageResource(bannerRes())
        scaleType = ImageView.ScaleType.FIT_CENTER
        alpha = 0f
        visibility = View.GONE
        elevation = dp(8).toFloat()
    }

    private fun buildCenterLogo(): ImageView = ImageView(this).apply {
        setImageResource(logoRes())
        scaleType = ImageView.ScaleType.FIT_CENTER
        alpha = 0f
        visibility = View.GONE
    }

    private fun buildBrandBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(18), 0)
            background = GradientDrawable().apply {
                setColor(Color.rgb(6, 18, 32))
            }
            visibility = View.GONE
        }
        bar.addView(
            label("${channel.name}  •  КАНАЛ ${channel.number}", 12f, Color.rgb(244, 227, 193), true).apply {
                gravity = Gravity.START
                letterSpacing = 0.04f
            }
        )
        bar.addView(
            label("СОВЁНОК", 11f, Color.rgb(255, 209, 102), true).apply {
                gravity = Gravity.END
                letterSpacing = 0.1f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        return bar
    }

    private fun updateBrandBar() {
        if (!::brandBar.isInitialized) return
        val playing = mode == Mode.PROMO || mode == Mode.CARTOON
        val controlsVisible = playerView.isControllerFullyVisible
        brandBar.visibility = if (playing && !controlsVisible) View.VISIBLE else View.GONE
    }

    private fun buildAnnouncementCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(34), dp(26), dp(34), dp(28))
            background = GradientDrawable().apply {
                setColor(Color.argb(232, 6, 18, 32))
                cornerRadius = dp(24).toFloat()
                setStroke(dp(2), Color.argb(190, 255, 209, 102))
            }
            visibility = View.GONE
            alpha = 0f
            elevation = dp(14).toFloat()
        }
        announcementLabel = label("АНОНС", 15f, Color.rgb(255, 209, 102), true).apply {
            letterSpacing = 0.16f
        }
        announcementTitle = label("", 38f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(2))
        }
        announcementMeta = label("", 17f, Color.rgb(139, 213, 255), false)
        announcementDescription = label("", 17f, Color.rgb(190, 208, 224), false).apply {
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(0, dp(12), 0, 0)
        }
        card.addView(announcementLabel)
        card.addView(announcementTitle)
        card.addView(announcementMeta)
        card.addView(announcementDescription)
        return card
    }

    private fun buildNightView(): FrameLayout {
        val view = FrameLayout(this).apply {
            visibility = View.GONE
        }
        view.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.cinema_bg)
                scaleType = ImageView.ScaleType.CENTER_CROP
                alpha = 0.72f
            },
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        )
        view.addView(
            View(this).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        Color.argb(225, 3, 8, 20),
                        Color.argb(245, 3, 8, 20),
                        Color.argb(225, 3, 8, 20)
                    )
                )
            },
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        )

        val banner = ImageView(this).apply {
            setImageResource(bannerRes())
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        view.addView(
            banner,
            FrameLayout.LayoutParams(dp(250), dp(65), Gravity.TOP or Gravity.START).apply {
                setMargins(dp(42), dp(30), 0, 0)
            }
        )

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(60), dp(10), dp(60), dp(10))
        }
        view.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        content.addView(label("СПОКОЙНОЙ НОЧИ,", 32f, Color.rgb(246, 231, 200), true).apply {
            letterSpacing = 0.08f
        })
        content.addView(label("ДЕТИ", 66f, Color.rgb(255, 209, 102), true).apply {
            setPadding(0, 0, 0, dp(12))
        })
        nightClock = label("", 74f, Color.WHITE, true).apply {
            typeface = Typeface.create("monospace", Typeface.BOLD)
        }
        content.addView(nightClock)
        nightDate = label("", 19f, Color.rgb(160, 184, 202), false)
        content.addView(nightDate)
        content.addView(
            label("${channel.name.uppercase(Locale("ru"))} ВЕРНЁТСЯ В 05:00", 20f, Color.rgb(139, 213, 255), true).apply {
                letterSpacing = 0.06f
                setPadding(0, dp(16), 0, dp(18))
            }
        )

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(18), dp(28), dp(18))
            background = GradientDrawable().apply {
                setColor(Color.argb(225, 7, 20, 36))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.argb(120, 139, 213, 255))
            }
        }
        card.addView(
            label("ЗАВТРА В ЭФИРЕ", 15f, Color.rgb(255, 209, 102), true).apply {
                letterSpacing = 0.12f
                setPadding(0, 0, 0, dp(8))
            }
        )
        nightSchedule = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        KidsRepository.schedule(this, channel).take(7).forEach { program ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3), 0, dp(3))
            }
            row.addView(
                TextView(this).apply {
                    text = program.time
                    textSize = 14f
                    setTextColor(Color.rgb(255, 209, 102))
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    gravity = Gravity.START
                    layoutParams = LinearLayout.LayoutParams(dp(64), LinearLayout.LayoutParams.WRAP_CONTENT)
                }
            )
            row.addView(
                TextView(this).apply {
                    text = program.title
                    textSize = 14f
                    setTextColor(Color.rgb(236, 243, 250))
                    typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                    gravity = Gravity.START
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
            nightSchedule.addView(row)
        }
        card.addView(nightSchedule)
        content.addView(card)

        content.addView(
            label("${channel.name}  •  КАНАЛ ${channel.number}", 14f, Color.rgb(130, 155, 190), false).apply {
                setPadding(0, dp(16), 0, 0)
                letterSpacing = 0.08f
            }
        )
        return view
    }

    private fun buildMaintenanceView(): FrameLayout {
        val view = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(4, 8, 16))
            visibility = View.GONE
        }
        view.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.cinema_bg)
                scaleType = ImageView.ScaleType.CENTER_CROP
                alpha = 0.45f
            },
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        )
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(80), dp(50), dp(80), dp(50))
        }
        view.addView(content, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        val logo = ImageView(this).apply {
            setImageResource(logoRes())
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0.96f
        }
        content.addView(
            logo,
            LinearLayout.LayoutParams(dp(300), dp(300)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(8)
            }
        )
        content.addView(label("ТЕХНИЧЕСКИЕ РАБОТЫ", 32f, Color.WHITE, true).apply {
            letterSpacing = 0.04f
        })
        content.addView(label("${channel.name} скоро вернётся", 22f, Color.rgb(255, 209, 102), true).apply {
            setPadding(0, dp(8), 0, dp(18))
        })
        maintenanceStatus = label("", 18f, Color.rgb(190, 208, 224), false).apply {
            gravity = Gravity.CENTER
            setLineSpacing(dp(5).toFloat(), 1f)
        }
        content.addView(maintenanceStatus)
        content.addView(label("${channel.name}  •  КАНАЛ ${channel.number}", 14f, Color.rgb(139, 213, 255), true).apply {
            letterSpacing = 0.12f
            setPadding(0, dp(18), 0, 0)
        })
        return view
    }

    // -----------------------------------------------------------------------
    // Broadcasting
    // -----------------------------------------------------------------------

    private fun startBroadcast() {
        val state = ChannelControl.stateFor(this, channel.number)
        if (!state.isLive) {
            showMaintenance(state)
            return
        }
        if (!KidsRepository.isBroadcastTime()) {
            showNight()
            return
        }
        stopNightMusic()
        nightView.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        setCorner(false)
        setCenter(false)
        setCard(false)
        if (channel.hasStartup && consumeStartup()) {
            playStartup()
        } else {
            startPromo()
        }
    }

    private fun playStartup() {
        mode = Mode.STARTUP
        updateBrandBar()
        val uri = RawResourceDataSource.buildRawResourceUri(R.raw.kids_startup)
        val current = player ?: return
        current.setMediaItem(MediaItem.fromUri(uri), 0L)
        current.prepare()
        current.playWhenReady = true
    }

    private fun startPromo() {
        val current = player ?: return
        if (cartoons.isEmpty()) return
        val cartoon = cartoons[currentIndex % cartoons.size]
        mode = Mode.PROMO
        updateBrandBar()
        stepToken++
        stopSubtitleTicker()
        setCard(false)
        setCorner(false)
        setCenter(false)
        current.setMediaItem(MediaItem.fromUri(KidsRepository.promoUri(this, channel, cartoon)), 0L)
        current.prepare()
        current.playWhenReady = true
    }

    private fun startPreview() {
        val current = player ?: return
        if (cartoons.isEmpty()) return
        val cartoon = cartoons[currentIndex % cartoons.size]
        val durationMs = (cartoon.durationSeconds * 1000).toLong().coerceAtLeast(30_000L)
        val previewMs = (cartoon.previewAtSeconds * 1000).toLong()
            .coerceAtLeast(10_000L)
            .coerceAtMost((durationMs - 15_000L).coerceAtLeast(10_000L))
        mode = Mode.PREVIEW
        setCenter(false)
        setCorner(false)
        current.setMediaItem(MediaItem.fromUri(KidsRepository.uri(this, channel, cartoon)), previewMs)
        current.prepare()
        current.playWhenReady = true
        nextStep(6000L) { showTitleCard() }
    }

    private fun showTitleCard() {
        if (mode != Mode.PREVIEW) return
        mode = Mode.TITLE
        stepToken++
        player?.pause()
        val cartoon = cartoons[currentIndex % cartoons.size]
        announcementLabel.text = cartoon.label.ifBlank { "АНОНС" }
        announcementTitle.text = cartoon.title
        announcementMeta.text = buildString {
            if (cartoon.year > 0) append(cartoon.year)
            val note = when {
                cartoon.subtitle.isNotBlank() -> "русские субтитры"
                cartoon.silent -> "немой мультфильм"
                else -> ""
            }
            if (note.isNotBlank()) {
                if (isNotEmpty()) append("  •  ")
                append(note)
            }
        }
        announcementDescription.text = cartoon.description
        setCorner(false)
        setCenter(false)
        setCard(true)
        nextStep(4200L) { showAnnounceLogoAgain() }
    }

    private fun showAnnounceLogoAgain() {
        mode = Mode.ANNOUNCE_LOGO2
        setCard(false)
        setCenter(true)
        nextStep(2000L) { startCartoon() }
    }

    private fun startCartoon() {
        val current = player ?: return
        if (cartoons.isEmpty()) return
        val cartoon = cartoons[currentIndex % cartoons.size]
        mode = Mode.CARTOON
        updateBrandBar()
        setCenter(false)
        setCard(false)
        setCorner(true)
        subtitleCues = KidsRepository.subtitles(this, channel, cartoon)
        current.setMediaItem(MediaItem.fromUri(KidsRepository.uri(this, channel, cartoon)), 0L)
        current.prepare()
        current.playWhenReady = true
        startSubtitleTicker()
    }

    private fun onEnded() {
        when (mode) {
            Mode.STARTUP -> startPromo()
            Mode.PROMO -> startCartoon()
            Mode.CARTOON -> {
                currentIndex = (currentIndex + 1) % cartoons.size.coerceAtLeast(1)
                startPromo()
            }
            Mode.PREVIEW -> showTitleCard()
            else -> Unit
        }
    }

    private fun onPlaybackError() {
        when (mode) {
            Mode.STARTUP -> startPromo()
            Mode.PROMO -> {
                handler.postDelayed({
                    if (mode == Mode.PROMO) startCartoon()
                }, 1200L)
            }
            Mode.PREVIEW, Mode.CARTOON -> {
                handler.postDelayed({
                    if (mode == Mode.PREVIEW || mode == Mode.CARTOON) {
                        currentIndex = (currentIndex + 1) % cartoons.size.coerceAtLeast(1)
                        startPromo()
                    }
                }, 2000L)
            }
            else -> Unit
        }
    }

    private fun consumeStartup(): Boolean {
        if (!channel.hasStartup) return false
        val prefs = getSharedPreferences("aurora_kids", MODE_PRIVATE)
        val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val key = "startup_day_${channel.number}"
        if (prefs.getString(key, "") == day) return false
        prefs.edit().putString(key, day).apply()
        return true
    }

    // -----------------------------------------------------------------------
    // Night and maintenance
    // -----------------------------------------------------------------------

    private fun showNight() {
        mode = Mode.NIGHT
        updateBrandBar()
        stepToken++
        stopSubtitleTicker()
        player?.pause()
        setCorner(false)
        setCenter(false)
        setCard(false)
        maintenanceView.visibility = View.GONE
        nightView.visibility = View.VISIBLE
        updateNightClock()
        startNightMusic()
        scheduleNightTick()
    }

    private fun scheduleNightTick() {
        nightTick?.let { handler.removeCallbacks(it) }
        val runnable = object : Runnable {
            override fun run() {
                if (!started || mode != Mode.NIGHT) return
                updateNightClock()
                if (KidsRepository.isBroadcastTime()) {
                    startBroadcast()
                } else {
                    handler.postDelayed(this, 1000L)
                }
            }
        }
        nightTick = runnable
        handler.post(runnable)
    }

    private fun updateNightClock() {
        nightClock.text = KidsRepository.clockText()
        nightDate.text = KidsRepository.dateText()
    }

    private fun startSubtitleTicker() {
        stopSubtitleTicker()
        if (subtitleCues.isEmpty()) return
        val runnable = object : Runnable {
            override fun run() {
                if (mode != Mode.CARTOON) {
                    subtitleView.visibility = View.GONE
                    return
                }
                val position = player?.currentPosition ?: 0L
                val cue = subtitleCues.firstOrNull { position in it.startMs..it.endMs }
                if (cue != null) {
                    subtitleView.text = cue.text
                    subtitleView.visibility = View.VISIBLE
                } else {
                    subtitleView.visibility = View.GONE
                }
                handler.postDelayed(this, 200L)
            }
        }
        subtitleTick = runnable
        handler.post(runnable)
    }

    private fun stopSubtitleTicker() {
        subtitleTick?.let { handler.removeCallbacks(it) }
        subtitleTick = null
        if (::subtitleView.isInitialized) subtitleView.visibility = View.GONE
    }

    private fun startNightMusic() {
        if (nightMusic?.isPlaying == true) return
        if (nightMusic == null) {
            nightMusic = try {
                MediaPlayer.create(this, R.raw.cinema_ambient)?.apply {
                    isLooping = true
                    setVolume(0.10f, 0.10f)
                }
            } catch (_: Throwable) {
                null
            }
        }
        try {
            nightMusic?.start()
        } catch (_: Throwable) {
            // Ignore audio focus errors.
        }
    }

    private fun stopNightMusic() {
        try {
            nightMusic?.pause()
        } catch (_: Throwable) {
            // Ignore.
        }
    }

    private fun scheduleMaintenanceCheck() {
        maintenanceTick?.let { handler.removeCallbacks(it) }
        val runnable = object : Runnable {
            override fun run() {
                if (!started) return
                val state = ChannelControl.stateFor(this@KidsChannelActivity, channel.number)
                if (!state.isLive) {
                    if (mode != Mode.MAINTENANCE) showMaintenance(state)
                } else if (mode == Mode.MAINTENANCE) {
                    maintenanceView.visibility = View.GONE
                    if (KidsRepository.isBroadcastTime()) startBroadcast() else showNight()
                } else {
                    val broadcast = KidsRepository.isBroadcastTime()
                    if (!broadcast && mode != Mode.NIGHT) {
                        showNight()
                    } else if (broadcast && mode == Mode.NIGHT) {
                        startBroadcast()
                    }
                }
                handler.postDelayed(this, 15_000L)
            }
        }
        maintenanceTick = runnable
        handler.postDelayed(runnable, 15_000L)
    }

    private fun showMaintenance(state: ManagedChannel) {
        mode = Mode.MAINTENANCE
        updateBrandBar()
        stepToken++
        stopSubtitleTicker()
        player?.pause()
        startNightMusic()
        setCorner(false)
        setCenter(false)
        setCard(false)
        nightView.visibility = View.GONE
        maintenanceView.visibility = View.VISIBLE
        maintenanceStatus.text = buildString {
            append("Канал временно недоступен.\n\n")
            if (state.reason.isNotBlank()) append(state.reason).append("\n\n")
            if (state.version.isNotBlank()) append("Версия: ").append(state.version).append("\n")
            if (state.until > 0L) {
                append("Возврат в эфир в ")
                append(SimpleDateFormat("HH:mm", Locale("ru")).format(Date(state.until)))
            } else {
                append("Идут технические работы. Канал скоро вернётся.")
            }
        }
    }

    // -----------------------------------------------------------------------
    // Overlay helpers
    // -----------------------------------------------------------------------

    private fun setCorner(visible: Boolean) {
        cornerLogo.animate().cancel()
        if (visible) {
            cornerLogo.visibility = View.VISIBLE
            cornerLogo.alpha = 0f
            cornerLogo.scaleX = 0.9f
            cornerLogo.scaleY = 0.9f
            cornerLogo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280L).start()
        } else if (cornerLogo.visibility == View.VISIBLE) {
            cornerLogo.animate().alpha(0f).setDuration(220L).withEndAction {
                cornerLogo.visibility = View.GONE
            }.start()
        }
    }

    private fun setCenter(visible: Boolean) {
        centerLogo.animate().cancel()
        if (visible) {
            centerLogo.visibility = View.VISIBLE
            centerLogo.alpha = 0f
            centerLogo.scaleX = 0.82f
            centerLogo.scaleY = 0.82f
            centerLogo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320L).start()
        } else if (centerLogo.visibility == View.VISIBLE) {
            centerLogo.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(240L).withEndAction {
                centerLogo.visibility = View.GONE
            }.start()
        }
    }

    private fun setCard(visible: Boolean) {
        announcementCard.animate().cancel()
        if (visible) {
            announcementCard.visibility = View.VISIBLE
            announcementCard.alpha = 0f
            announcementCard.translationY = dp(18).toFloat()
            announcementCard.animate().alpha(1f).translationY(0f).setDuration(280L).start()
        } else if (announcementCard.visibility == View.VISIBLE) {
            announcementCard.animate().alpha(0f).translationY(dp(12).toFloat()).setDuration(220L).withEndAction {
                announcementCard.visibility = View.GONE
            }.start()
        }
    }

    private fun nextStep(delay: Long, action: () -> Unit) {
        val token = ++stepToken
        handler.postDelayed({
            if (token == stepToken && started) action()
        }, delay)
    }

    // -----------------------------------------------------------------------
    // UI helpers
    // -----------------------------------------------------------------------

    private fun label(value: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(2))
            typeface = Typeface.create(
                if (bold) "sans-serif-medium" else "sans-serif-light",
                Typeface.NORMAL
            )
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

    override fun onStart() {
        super.onStart()
        if (mode == Mode.NIGHT || mode == Mode.MAINTENANCE) startNightMusic()
        else {
            if (wasPlaying) player?.play()
            if (mode == Mode.CARTOON) startSubtitleTicker()
        }
        startMaintenanceWatch()
    }

    private val maintenanceWatch = object : Runnable {
        override fun run() {
            if (ru.auroratv.launcher.core.MaintenanceScreen.isActive(this@KidsChannelActivity)) {
                ru.auroratv.launcher.core.MaintenanceScreen.show(this@KidsChannelActivity)
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
        stopSubtitleTicker()
        player?.pause()
        nightMusic?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        started = false
        stopSubtitleTicker()
        handler.removeCallbacksAndMessages(null)
        try {
            player?.release()
        } catch (_: Throwable) {
            // Ignore.
        }
        player = null
        try {
            nightMusic?.release()
        } catch (_: Throwable) {
            // Ignore.
        }
        nightMusic = null
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
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
                startActivityForResult(
                    Intent(this, ChannelGuideActivity::class.java)
                        .putExtra(ChannelGuideActivity.EXTRA_CURRENT, channel.number)
                        .putExtra(ChannelGuideActivity.EXTRA_FROM_PLAYER, true),
                    ChannelGuideActivity.REQUEST_GUIDE
                )
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ChannelGuideActivity.REQUEST_GUIDE && resultCode == RESULT_OK) {
            val number = data?.getIntExtra(ChannelGuideActivity.EXTRA_SELECTED, -1) ?: -1
            val target = ChannelCatalog.byNumber(this, number) ?: return
            switchTo(target)
        }
    }

    private fun switchRelative(delta: Int) {
        val target = ChannelCatalog.next(this, channel.number, delta) ?: return
        switchTo(target)
    }

    private fun switchTo(target: GuideChannel) {
        if (target.number == channel.number) return
        if (target.kind == "kids") {
            startActivity(
                Intent(this, KidsChannelActivity::class.java)
                    .putExtra(EXTRA_CHANNEL, target.number)
            )
            finish()
            return
        }
        if (ChannelCatalog.open(this, target)) finish()
    }

    companion object {
        const val EXTRA_CHANNEL = "kids_channel"
    }
}
