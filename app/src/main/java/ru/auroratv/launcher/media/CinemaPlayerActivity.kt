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
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
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
import ru.auroratv.launcher.core.ChannelControl
import ru.auroratv.launcher.core.CinemaChannel
import ru.auroratv.launcher.core.CinemaFilm
import ru.auroratv.launcher.core.CinemaRepository
import ru.auroratv.launcher.core.CinemaSchedule
import ru.auroratv.launcher.core.CinemaSlot
import ru.auroratv.launcher.core.ManagedChannel
import ru.auroratv.launcher.ui.CinemaParticlesView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Линейный кинозал: фиксированные сеансы 14:00 и 18:00, анимированные
 * плашки, тихая музыка, реклама, «продолжение сеанса» и титры.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class CinemaPlayerActivity : Activity() {

    private enum class Phase { WAITING, FILM, AD, CONTINUE, CREDITS, ERROR, MAINTENANCE }

    private data class Placard(
        val eyebrow: String,
        val title: String,
        val meta: String,
        val description: String,
        val status: String,
        val accent: Int
    )

    private var player: ExoPlayer? = null
    private var channel: CinemaChannel? = null
    private var currentFilm: CinemaFilm? = null
    private var phase = Phase.WAITING
    private var adShown = false
    private var adAtMs = 0L
    private var adEndsAt = 0L
    private var pendingSeekMs = 0L

    private lateinit var overlay: FrameLayout
    private lateinit var adPlayerView: PlayerView
    private var adPlayer: ExoPlayer? = null
    private lateinit var adHud: LinearLayout
    private lateinit var adTimer: TextView
    private lateinit var adProgress: View
    private lateinit var card: LinearLayout
    private lateinit var cardBackground: GradientDrawable
    private lateinit var accentBar: View
    private lateinit var progressLine: View
    private lateinit var playerView: PlayerView
    private lateinit var channelLabel: TextView
    private lateinit var titleView: TextView
    private lateinit var metaView: TextView
    private lateinit var descriptionView: TextView
    private lateinit var statusView: TextView
    private lateinit var footerView: TextView

    private lateinit var showreel: FrameLayout
    private lateinit var showreelHeader: TextView
    private lateinit var showreelFooter: TextView
    private lateinit var lineupGrid: LinearLayout
    private lateinit var logoBox: LinearLayout
    private val showreelCards = mutableListOf<View>()
    private var showreelPage = 0
    private var showreelToken = 0

    private val handler = Handler(Looper.getMainLooper())
    private val ambient by lazy { CinemaAmbient(this) }
    private var waitingRunnable: Runnable? = null
    private var adRunnable: Runnable? = null
    private var maintenanceRunnable: Runnable? = null
    private var ticker: Runnable? = null
    private var waitingSlot: CinemaSlot? = null
    private var currentMaintenance: ManagedChannel? = null
    private var lastControlCheck = 0L
    private var placardToken = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val number = intent.getIntExtra(EXTRA_CHANNEL_NUMBER, -1)
        val selected = CinemaRepository.channelFor(this, number)
        if (selected == null) {
            finish()
            return
        }
        channel = selected

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)

        val bgImage = ImageView(this).apply {
            setImageResource(R.drawable.cinema_bg)
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.95f
        }
        root.addView(
            bgImage,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            CinemaParticlesView(this),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        playerView = PlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(Color.BLACK)
            keepScreenOn = true
            isFocusable = false
        }
        root.addView(playerView)

        adPlayerView = PlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(Color.BLACK)
            isFocusable = false
            visibility = View.GONE
        }
        root.addView(adPlayerView)

        overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(120, 0, 0, 0))
            visibility = View.VISIBLE
        }
        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        cardBackground = GradientDrawable().apply {
            setColor(Color.argb(236, 5, 18, 38))
            setStroke(dp(1), Color.argb(130, 139, 213, 255))
            cornerRadius = dp(22).toFloat()
        }
        card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(56), dp(40), dp(56), dp(34))
            background = cardBackground
        }
        overlay.addView(
            card,
            FrameLayout.LayoutParams(
                dp(1020),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        accentBar = View(this)
        card.addView(
            accentBar,
            LinearLayout.LayoutParams(dp(64), dp(3)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(16)
            }
        )

        channelLabel = label(14f, Color.rgb(139, 213, 255), true)
        titleView = label(44f, Color.WHITE, false).apply {
            maxLines = 3
            setLineSpacing(0f, 0.95f)
        }
        metaView = label(15f, Color.rgb(150, 175, 205), false)
        descriptionView = label(17f, Color.rgb(190, 205, 225), false).apply {
            maxWidth = dp(860)
            maxLines = 4
        }
        statusView = label(18f, Color.rgb(139, 213, 255), true)
        footerView = label(11f, Color.rgb(105, 125, 155), false).apply {
            text = "AURORA TV  •  КИНОЗАЛ"
        }

        card.addView(channelLabel)
        card.addView(titleView)
        card.addView(metaView)
        card.addView(descriptionView)
        card.addView(statusView)
        card.addView(footerView)

        progressLine = View(this).apply {
            pivotX = 0f
            visibility = View.INVISIBLE
        }
        card.addView(
            progressLine,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(2)
            ).apply { topMargin = dp(20) }
        )

        showreel = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(26, 0, 0, 0))
            visibility = View.GONE
        }
        root.addView(
            showreel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        val reelContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(64), dp(26), dp(64), dp(16))
        }
        showreel.addView(
            reelContent,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        showreelHeader = label(13f, Color.rgb(190, 210, 240), true).apply {
            text = "СКОРО НА ЭКРАНЕ"
            letterSpacing = 0.08f
        }
        reelContent.addView(
            showreelHeader,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        lineupGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        reelContent.addView(
            lineupGrid,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(8)
                bottomMargin = dp(6)
            }
        )
        showreelFooter = label(13f, accentColor(), true)
        reelContent.addView(
            showreelFooter,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(34)
            )
        )

        logoBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        showreel.addView(
            logoBox,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )
        logoBox.addView(
            label(13f, Color.rgb(190, 210, 240), true).apply {
                text = "ВАМ ОБЕСПЕЧИВАЕТ"
                letterSpacing = 0.12f
            }
        )
        logoBox.addView(
            label(64f, Color.WHITE, false).apply {
                text = "AURORA TV"
                letterSpacing = 0.12f
            }
        )
        logoBox.addView(
            View(this).apply {
                background = GradientDrawable().apply {
                    setColor(accentColor())
                    cornerRadius = dp(2).toFloat()
                }
            },
            LinearLayout.LayoutParams(dp(140), dp(2)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(10)
                bottomMargin = dp(12)
            }
        )
        logoBox.addView(
            label(15f, Color.rgb(180, 200, 230), false).apply {
                text = "КИНОЗАЛЫ 20–32  •  ПУБЛИЧНОЕ ДОСТОЯНИЕ"
            }
        )

        adHud = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(48), 0, dp(48), 0)
            background = GradientDrawable().apply {
                setColor(Color.argb(205, 3, 10, 24))
            }
            visibility = View.GONE
        }
        adHud.addView(
            label(13f, Color.rgb(255, 107, 107), true).apply {
                text = "РЕКЛАМА"
                letterSpacing = 0.1f
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        adHud.addView(
            View(this),
            LinearLayout.LayoutParams(0, 1, 1f)
        )
        adTimer = label(13f, Color.WHITE, true).apply {
            text = "До продолжения: 03:00"
        }
        adHud.addView(
            adTimer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            adHud,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(52),
                Gravity.BOTTOM
            )
        )
        adProgress = View(this).apply {
            pivotX = 0f
            background = GradientDrawable().apply {
                setColor(accentColor())
                cornerRadius = dp(2).toFloat()
            }
            visibility = View.GONE
        }
        root.addView(
            adProgress,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(2),
                Gravity.BOTTOM
            )
        )

        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        playerView.player = exoPlayer
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && pendingSeekMs > 0) {
                    exoPlayer.seekTo(pendingSeekMs)
                    pendingSeekMs = 0
                }
                if (phase == Phase.FILM && playbackState == Player.STATE_ENDED) {
                    startCredits()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                showUnavailable()
            }
        })
    }

    private fun prepareSchedule() {
        val ch = channel ?: return
        val managed = ChannelControl.stateFor(this, ch.number)
        if (!managed.isLive) {
            showMaintenance(managed)
            return
        }
        cancelMaintenancePolling()
        currentMaintenance = null
        val now = try {
            CinemaSchedule.now(this, ch)
        } catch (_: Throwable) {
            finish()
            return
        }
        val active = now.active
        if (active != null) {
            currentFilm = active.film
            adShown = now.joinMs >= adPositionMs(active.film) + AD_BREAK_MS
            val join = now.joinMs
            startFilm(
                active.film,
                join,
                if (join < 5000) "Сеанс начинается" else "Сеанс уже идет"
            )
        } else {
            startWaiting(now.next)
        }
    }

    private fun showMaintenance(state: ManagedChannel) {
        val accent = if (state.state == ChannelControl.STATE_UPGRADING) {
            Color.rgb(255, 209, 102)
        } else {
            Color.rgb(255, 107, 107)
        }
        val previous = currentMaintenance
        val sameState = phase == Phase.MAINTENANCE &&
            previous?.state == state.state &&
            previous?.until == state.until
        currentMaintenance = state
        if (!sameState) {
            phase = Phase.MAINTENANCE
            stopTicker()
            cancelWaitingCountdown()
            cancelAdCountdown()
            cancelShowreel()
            stopAdPlayback()
            player?.pause()
            playerView.visibility = View.INVISIBLE
            overlay.visibility = View.GONE
            ambient.start(0.4f)
            val meta = listOfNotNull(
                state.reason.takeIf { it.isNotBlank() },
                state.version.takeIf { it.isNotBlank() }
            ).joinToString("  •  ")
            val description = if (state.until > 0L) {
                "Канал вернется в эфир в ${formatTime(state.until)}. " +
                    "Следите за анонсами — сеанс начнется автоматически."
            } else {
                "Идут технические работы. Канал скоро вернется в эфир."
            }
            replacePlacard(
                Placard(
                    eyebrow = channelTitle(),
                    title = if (state.state == ChannelControl.STATE_UPGRADING) {
                        "ОБНОВЛЕНИЕ КАНАЛА"
                    } else {
                        "ПРОФИЛАКТИКА КАНАЛА"
                    },
                    meta = meta,
                    description = description,
                    status = "Ожидаем завершения",
                    accent = accent
                )
            )
        }
        statusView.setTextColor(accent)
        statusView.text = maintenanceStatus(state)
        startMaintenancePolling()
    }

    private fun maintenanceStatus(state: ManagedChannel): String =
        if (state.until > 0L) {
            "До возвращения: ${formatCountdown(state.until - System.currentTimeMillis())}"
        } else {
            "Идут технические работы"
        }

    private fun startMaintenancePolling() {
        cancelMaintenancePolling()
        val runnable = object : Runnable {
            override fun run() {
                val ch = channel ?: return
                if (phase != Phase.MAINTENANCE) return
                val state = ChannelControl.stateFor(this@CinemaPlayerActivity, ch.number)
                if (state.isLive) {
                    prepareSchedule()
                    return
                }
                if (state.state != currentMaintenance?.state || state.until != currentMaintenance?.until) {
                    showMaintenance(state)
                    return
                }
                currentMaintenance = state
                statusView.text = maintenanceStatus(state)
                handler.postDelayed(this, 1000L)
            }
        }
        maintenanceRunnable = runnable
        handler.postDelayed(runnable, 1000L)
    }

    private fun cancelMaintenancePolling() {
        maintenanceRunnable?.let { handler.removeCallbacks(it) }
        maintenanceRunnable = null
    }

    private fun startFilm(film: CinemaFilm, seekMs: Long, status: String) {
        val exo = player ?: return
        val ch = channel
        if (ch != null) {
            val control = ChannelControl.stateFor(this, ch.number)
            if (!control.isLive) {
                showMaintenance(control)
                return
            }
        }
        phase = Phase.FILM
        currentFilm = film
        pendingSeekMs = seekMs
        adAtMs = adPositionMs(film)
        cancelWaitingCountdown()
        cancelAdCountdown()
        cancelMaintenancePolling()
        cancelShowreel()
        stopAdPlayback()
        currentMaintenance = null
        playerView.visibility = View.VISIBLE
        ambient.pause()

        replacePlacard(
            Placard(
                eyebrow = channelTitle(),
                title = film.title,
                meta = meta(film),
                description = if (seekMs > 5000) {
                    "Вы подключились к сеансу. ${film.description}"
                } else {
                    film.description
                },
                status = status,
                accent = accentColor()
            )
        )

        exo.setMediaItem(MediaItem.fromUri(Uri.parse(film.url)))
        exo.prepare()
        exo.playWhenReady = true
        startTicker()

        handler.postDelayed({
            if (phase == Phase.FILM) hidePlacard()
        }, 3200L)
    }

    private fun startWaiting(slot: CinemaSlot) {
        val exo = player ?: return
        phase = Phase.WAITING
        waitingSlot = slot
        currentFilm = slot.film
        adShown = false
        showreelPage = 0
        cancelMaintenancePolling()
        currentMaintenance = null
        exo.pause()
        playerView.visibility = View.INVISIBLE
        ambient.start()
        cancelPlacards()
        cancelShowreel()
        stopAdPlayback()
        overlay.visibility = View.GONE
        startShowreel()
        startWaitingCountdown()
    }

    private fun startShowreel() {
        if (phase != Phase.WAITING) return
        val token = ++showreelToken
        showreel.visibility = View.VISIBLE
        logoBox.visibility = View.GONE
        lineupGrid.visibility = View.VISIBLE
        showreelHeader.visibility = View.VISIBLE
        showreelHeader.text = "СКОРО НА ЭКРАНЕ  •  ${channelTitle()}"
        val startAt = waitingSlot?.startAt
        showreelFooter.text = if (startAt != null) {
            "Сеанс начнется в ${formatTime(startAt)}"
        } else {
            "Сеанс начнется автоматически"
        }
        buildShowreelPage()
        showreelCards.forEachIndexed { index, view ->
            view.animate().cancel()
            view.alpha = 0f
            view.translationY = dp(26).toFloat()
            view.scaleX = 0.93f
            view.scaleY = 0.93f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(index * 90L)
                .setDuration(420L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        handler.postDelayed({
            if (token == showreelToken) hideShowreelCards(token)
        }, SHOWREEL_HOLD_MS)
    }

    private fun hideShowreelCards(token: Int) {
        if (token != showreelToken) return
        showreelCards.forEachIndexed { index, view ->
            view.animate().cancel()
            view.animate()
                .alpha(0f)
                .translationY(-dp(20).toFloat())
                .scaleX(1.03f)
                .scaleY(1.03f)
                .setStartDelay(index * 55L)
                .setDuration(300L)
                .setInterpolator(AccelerateInterpolator())
                .start()
        }
        handler.postDelayed({
            if (token != showreelToken) return@postDelayed
            lineupGrid.visibility = View.INVISIBLE
            showLogo(token)
        }, 850L)
    }

    private fun showLogo(token: Int) {
        if (token != showreelToken) return
        showreelHeader.visibility = View.INVISIBLE
        logoBox.visibility = View.VISIBLE
        logoBox.alpha = 0f
        logoBox.scaleX = 0.86f
        logoBox.scaleY = 0.86f
        logoBox.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(650L)
            .setInterpolator(DecelerateInterpolator())
            .start()
        handler.postDelayed({
            if (token != showreelToken) return@postDelayed
            logoBox.animate()
                .alpha(0f)
                .scaleX(1.08f)
                .scaleY(1.08f)
                .setDuration(450L)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    if (token == showreelToken) {
                        logoBox.visibility = View.GONE
                        startShowreel()
                    }
                }
                .start()
        }, LOGO_HOLD_MS)
    }

    private fun buildShowreelPage() {
        lineupGrid.removeAllViews()
        showreelCards.clear()
        val lineup = lineupForWaiting()
        if (lineup.isEmpty()) return
        val pageSize = 9
        val start = (showreelPage * pageSize) % lineup.size
        showreelPage++
        val page = (0 until minOf(pageSize, lineup.size)).map {
            lineup[(start + it) % lineup.size]
        }
        for (rowIndex in 0 until 3) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            lineupGrid.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
            for (column in 0 until 3) {
                val index = rowIndex * 3 + column
                if (index >= page.size) {
                    row.addView(
                        View(this),
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    )
                    continue
                }
                val (itemChannel, film) = page[index]
                val item = makeFilmCard(
                    itemChannel,
                    film,
                    itemChannel.number == channel?.number
                )
                row.addView(
                    item,
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                        setMargins(dp(6), dp(6), dp(6), dp(6))
                    }
                )
                showreelCards.add(item)
            }
        }
    }

    private fun lineupForWaiting(): List<Pair<CinemaChannel, CinemaFilm>> {
        val slot = waitingSlot ?: return emptyList()
        val channels = CinemaRepository.channels(this)
        val selectedIndex = channels.indexOfFirst { it.number == channel?.number }
        val ordered = if (selectedIndex >= 0) {
            channels.drop(selectedIndex) + channels.take(selectedIndex)
        } else {
            channels
        }
        val today14 = CinemaSchedule.at(14, 0, 0)
        val today18 = CinemaSchedule.at(18, 0, 0)
        val dayOffset = if (slot.startAt >= CinemaSchedule.at(14, 0, 1)) 1 else 0
        return ordered.mapNotNull { item ->
            val session = CinemaRepository.sessionFor(this, item, dayOffset)
                ?: return@mapNotNull null
            val film = when (slot.startAt) {
                today14 -> session.current
                today18 -> session.next
                else -> session.current
            }
            item to film
        }
    }

    private fun makeFilmCard(
        itemChannel: CinemaChannel,
        film: CinemaFilm,
        selected: Boolean
    ): LinearLayout {
        val accent = try {
            Color.parseColor(itemChannel.color)
        } catch (_: Throwable) {
            Color.rgb(139, 213, 255)
        }
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(226, 6, 18, 36))
                cornerRadius = dp(16).toFloat()
                setStroke(
                    dp(if (selected) 2 else 1),
                    if (selected) accent else Color.argb(80, 130, 170, 230)
                )
            }
        }
        val poster = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.argb(235, Color.red(accent), Color.green(accent), Color.blue(accent)),
                    Color.argb(235, 8, 24, 48)
                )
            ).apply {
                cornerRadius = dp(10).toFloat()
            }
        }
        poster.addView(
            label(32f, Color.WHITE, true).apply {
                text = itemChannel.number.toString()
                alpha = 0.88f
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        poster.addView(
            label(8f, Color.WHITE, true).apply {
                text = itemChannel.name.uppercase()
                alpha = 0.8f
                letterSpacing = 0.06f
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply { bottomMargin = dp(6) }
        )
        item.addView(
            poster,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64)
            )
        )
        item.addView(
            label(14f, Color.WHITE, true).apply {
                text = film.title
                gravity = Gravity.START
                maxLines = 2
                setLineSpacing(0f, 0.9f)
                setPadding(0, dp(6), 0, 0)
            }
        )
        item.addView(
            label(9.5f, Color.rgb(170, 195, 225), false).apply {
                text = "${film.year}  •  ${film.durationSeconds / 60} мин  •  ${film.rating}"
                gravity = Gravity.START
                setPadding(0, dp(3), 0, 0)
            }
        )
        if (selected) {
            item.addView(
                label(8f, accent, true).apply {
                    text = "ВАШ КАНАЛ"
                    gravity = Gravity.START
                    letterSpacing = 0.1f
                    setPadding(0, dp(3), 0, 0)
                }
            )
        }
        return item
    }

    private fun startWaitingCountdown() {
        cancelWaitingCountdown()
        val runnable = object : Runnable {
            override fun run() {
                val slot = waitingSlot
                if (phase != Phase.WAITING || slot == null) return
                val ch = channel
                if (ch != null) {
                    val control = ChannelControl.stateFor(this@CinemaPlayerActivity, ch.number)
                    if (!control.isLive) {
                        showMaintenance(control)
                        return
                    }
                }
                val left = slot.startAt - System.currentTimeMillis()
                if (left <= 0) {
                    startFilm(slot.film, 0, "Сеанс начинается")
                } else {
                    showreelFooter.text =
                        "До начала: ${formatCountdown(left)}  •  сеанс в ${formatTime(slot.startAt)}"
                    handler.postDelayed(this, 1000L)
                }
            }
        }
        waitingRunnable = runnable
        handler.postDelayed(runnable, 1000L)
    }

    private fun cancelWaitingCountdown() {
        waitingRunnable?.let { handler.removeCallbacks(it) }
        waitingRunnable = null
    }

    private fun startAdBreak() {
        if (phase != Phase.FILM || adShown) return
        phase = Phase.AD
        adShown = true
        adEndsAt = System.currentTimeMillis() + AD_BREAK_MS
        cancelShowreel()
        cancelPlacards()
        player?.pause()
        playerView.visibility = View.INVISIBLE
        overlay.visibility = View.GONE
        ambient.start()
        showAdUi()
        adProgress.scaleX = 1f
        adProgress.animate()
            .scaleX(0f)
            .setDuration(AD_BREAK_MS)
            .setInterpolator(LinearInterpolator())
            .start()
        startAdPlayback(restart = true)
        startAdCountdown()
    }

    private fun showAdUi() {
        adPlayerView.visibility = View.VISIBLE
        adHud.visibility = View.VISIBLE
        adProgress.visibility = View.VISIBLE
        adTimer.text = "До продолжения: ${formatCountdown(AD_BREAK_MS)}"
    }

    private fun adMediaItems(): List<MediaItem> = listOf(
        R.raw.ad_remote,
        R.raw.ad_channels,
        R.raw.ad_aurora
    ).map { MediaItem.fromUri(RawResourceDataSource.buildRawResourceUri(it)) }

    private fun startAdPlayback(restart: Boolean) {
        val ad = adPlayer ?: ExoPlayer.Builder(this).build().also {
            adPlayer = it
            adPlayerView.player = it
        }
        if (restart || ad.mediaItemCount == 0) {
            ad.setMediaItems(adMediaItems())
            ad.repeatMode = Player.REPEAT_MODE_ALL
            ad.prepare()
        }
        ad.play()
    }

    private fun stopAdPlayback() {
        adPlayer?.pause()
        if (::adPlayerView.isInitialized) adPlayerView.visibility = View.GONE
        if (::adHud.isInitialized) adHud.visibility = View.GONE
        if (::adProgress.isInitialized) {
            adProgress.animate().cancel()
            adProgress.visibility = View.GONE
        }
    }

    private fun startAdCountdown() {
        cancelAdCountdown()
        val runnable = object : Runnable {
            override fun run() {
                if (phase != Phase.AD) return
                val left = adEndsAt - System.currentTimeMillis()
                if (left <= 0) {
                    showContinue()
                } else {
                    adTimer.text = "До продолжения: ${formatCountdown(left)}"
                    handler.postDelayed(this, 1000L)
                }
            }
        }
        adRunnable = runnable
        handler.postDelayed(runnable, 1000L)
    }

    private fun cancelAdCountdown() {
        adRunnable?.let { handler.removeCallbacks(it) }
        adRunnable = null
    }

    private fun showContinue() {
        phase = Phase.CONTINUE
        cancelAdCountdown()
        cancelShowreel()
        stopAdPlayback()
        val film = currentFilm
        replacePlacard(
            Placard(
                eyebrow = "ПРОДОЛЖЕНИЕ СЕАНСА",
                title = "ПРОДОЛЖЕНИЕ ПРОСМОТРА",
                meta = film?.let { meta(it) }.orEmpty(),
                description = film?.description.orEmpty(),
                status = "Возвращаемся в кино",
                accent = accentColor()
            ),
            3000
        ) {
            phase = Phase.FILM
            ambient.pause()
            playerView.visibility = View.VISIBLE
            player?.play()
            startTicker()
        }
    }

    private fun startCredits() {
        if (phase == Phase.CREDITS) return
        phase = Phase.CREDITS
        cancelShowreel()
        stopAdPlayback()
        player?.pause()
        playerView.visibility = View.INVISIBLE
        ambient.start()
        val film = currentFilm
        replacePlacard(
            Placard(
                eyebrow = "ТИТРЫ",
                title = film?.title.orEmpty(),
                meta = film?.let { meta(it) }.orEmpty(),
                description = "",
                status = "Сеанс завершен",
                accent = accentColor()
            ),
            5000
        ) {
            val ch = channel
            val now = if (ch != null) {
                try {
                    CinemaSchedule.now(this, ch)
                } catch (_: Throwable) {
                    null
                }
            } else {
                null
            }
            val next = now?.next
            if (next == null) {
                prepareSchedule()
            } else {
                replacePlacard(
                    Placard(
                        eyebrow = "СЛЕДУЮЩИЙ СЕАНС",
                        title = next.film.title,
                        meta = "${formatTime(next.startAt)}  •  ${next.film.year}  •  ${next.film.rating}",
                        description = next.film.description,
                        status = "Не переключайтесь",
                        accent = accentColor()
                    ),
                    6000
                ) {
                    startWaiting(next)
                }
            }
        }
    }

    private fun showUnavailable() {
        if (phase == Phase.ERROR) return
        phase = Phase.ERROR
        cancelShowreel()
        stopAdPlayback()
        player?.pause()
        playerView.visibility = View.INVISIBLE
        ambient.start(0.45f)
        replacePlacard(
            Placard(
                eyebrow = channelTitle(),
                title = "СЕАНС ВРЕМЕННО НЕДОСТУПЕН",
                meta = currentFilm?.title.orEmpty(),
                description = "Источник Archive.org не ответил. Попробуйте позже.",
                status = "Вернемся к расписанию",
                accent = accentColor()
            ),
            8000
        ) {
            prepareSchedule()
        }
    }

    private fun startTicker() {
        stopTicker()
        val runnable = object : Runnable {
            override fun run() {
                val exo = player
                if (phase == Phase.FILM && exo != null) {
                    val now = System.currentTimeMillis()
                    if (now - lastControlCheck > 3000L) {
                        lastControlCheck = now
                        val ch = channel
                        if (ch != null) {
                            val state = ChannelControl.stateFor(this@CinemaPlayerActivity, ch.number)
                            if (!state.isLive) {
                                showMaintenance(state)
                                return
                            }
                        }
                    }
                    if (!adShown && exo.currentPosition >= adAtMs) {
                        startAdBreak()
                        return
                    }
                }
                handler.postDelayed(this, 1000L)
            }
        }
        ticker = runnable
        handler.postDelayed(runnable, 1000L)
    }

    private fun stopTicker() {
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
    }

    private fun showPlacard(placard: Placard, durationMs: Long = 0L, onDone: () -> Unit = {}) {
        val token = ++placardToken
        channelLabel.text = placard.eyebrow
        titleView.text = placard.title
        metaView.text = placard.meta
        descriptionView.text = placard.description
        statusView.text = placard.status
        statusView.setTextColor(placard.accent)
        accentBar.background = GradientDrawable().apply {
            setColor(placard.accent)
            cornerRadius = dp(2).toFloat()
        }
        cardBackground.setStroke(
            dp(1),
            Color.argb(
                150,
                Color.red(placard.accent),
                Color.green(placard.accent),
                Color.blue(placard.accent)
            )
        )
        progressLine.setBackgroundColor(placard.accent)

        overlay.visibility = View.VISIBLE
        card.visibility = View.VISIBLE
        card.animate().cancel()
        card.alpha = 0f
        card.translationY = dp(28).toFloat()
        card.scaleX = 0.94f
        card.scaleY = 0.94f
        card.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(420L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        if (durationMs > 0L) {
            progressLine.visibility = View.VISIBLE
            progressLine.animate().cancel()
            progressLine.scaleX = 1f
            progressLine.animate()
                .scaleX(0f)
                .setDuration(durationMs)
                .setInterpolator(LinearInterpolator())
                .start()
            handler.postDelayed({
                if (token == placardToken) hidePlacard(onDone)
            }, durationMs)
        } else {
            progressLine.visibility = View.INVISIBLE
        }
    }

    private fun hidePlacard(onDone: () -> Unit = {}) {
        placardToken++
        progressLine.animate().cancel()
        card.animate().cancel()
        card.animate()
            .alpha(0f)
            .translationY(-dp(20).toFloat())
            .scaleX(1.03f)
            .scaleY(1.03f)
            .setDuration(300L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                overlay.visibility = View.GONE
                onDone()
            }
            .start()
    }

    private fun replacePlacard(
        placard: Placard,
        durationMs: Long = 0L,
        onDone: () -> Unit = {}
    ) {
        if (overlay.visibility == View.VISIBLE && card.alpha > 0.05f) {
            hidePlacard {
                showPlacard(placard, durationMs, onDone)
            }
        } else {
            showPlacard(placard, durationMs, onDone)
        }
    }

    private fun cancelPlacards() {
        placardToken++
        if (!::overlay.isInitialized) return
        card.animate().cancel()
        progressLine.animate().cancel()
        overlay.visibility = View.GONE
    }

    private fun cancelShowreel() {
        showreelToken++
        if (!::showreel.isInitialized) return
        showreelCards.forEach { it.animate().cancel() }
        showreel.visibility = View.GONE
        logoBox.visibility = View.GONE
        lineupGrid.visibility = View.VISIBLE
        lineupGrid.removeAllViews()
        showreelCards.clear()
    }

    private fun channelTitle(): String {
        val ch = channel ?: return "AURORA TV"
        return "КИНОЗАЛ ${ch.number}  •  ${ch.name.uppercase()}"
    }

    private fun accentColor(): Int = try {
        Color.parseColor(channel?.color)
    } catch (_: Throwable) {
        Color.rgb(139, 213, 255)
    }

    private fun adPositionMs(film: CinemaFilm): Long {
        val duration = if (film.durationSeconds > 0) {
            film.durationSeconds * 1000L
        } else {
            90 * 60 * 1000L
        }
        val seed = (film.id.hashCode().toLong() and 0xFFFFFFFFL) +
            (channel?.number ?: 0) * 7919L
        val fraction = (seed % 1000L) / 1000f
        val start = (duration * 0.05f).toLong()
        val end = (duration * 0.70f).toLong()
        return start + ((end - start) * fraction).toLong()
    }

    private fun meta(film: CinemaFilm): String {
        val minutes = if (film.durationSeconds > 0) film.durationSeconds / 60 else 0
        return buildString {
            if (film.year > 0) append(film.year)
            if (minutes > 0) {
                if (isNotEmpty()) append("  •  ")
                append("$minutes мин")
            }
            if (isNotEmpty()) append("  •  ")
            append(film.rating)
            append("  •  публичное достояние")
        }
    }

    private fun formatTime(millis: Long): String =
        SimpleDateFormat("HH:mm", Locale("ru")).format(Date(millis))

    private fun formatCountdown(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d ч %02d мин".format(hours, minutes)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }

    override fun onStart() {
        super.onStart()
        when (phase) {
            Phase.FILM -> {
                player?.play()
                startTicker()
            }
            Phase.AD -> {
                val left = adEndsAt - System.currentTimeMillis()
                if (left > 0L) {
                    showAdUi()
                    adProgress.scaleX = (left.toFloat() / AD_BREAK_MS).coerceIn(0f, 1f)
                    adProgress.animate()
                        .scaleX(0f)
                        .setDuration(left)
                        .setInterpolator(LinearInterpolator())
                        .start()
                    startAdPlayback(restart = false)
                    startAdCountdown()
                } else {
                    showContinue()
                }
            }
            Phase.CONTINUE -> showContinue()
            else -> prepareSchedule()
        }
    }

    override fun onStop() {
        stopTicker()
        cancelWaitingCountdown()
        cancelAdCountdown()
        cancelMaintenancePolling()
        cancelPlacards()
        cancelShowreel()
        ambient.pause()
        player?.pause()
        adPlayer?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        stopTicker()
        cancelWaitingCountdown()
        cancelAdCountdown()
        cancelMaintenancePolling()
        cancelPlacards()
        cancelShowreel()
        ambient.release()
        player?.release()
        player = null
        adPlayer?.release()
        adPlayer = null
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                finish()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                val exo = player ?: return true
                if (phase == Phase.FILM) {
                    if (exo.isPlaying) exo.pause() else exo.play()
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun label(size: Float, color: Int, medium: Boolean): TextView =
        TextView(this).apply {
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(4))
            typeface = android.graphics.Typeface.create(
                if (medium) "sans-serif-medium" else "sans-serif-light",
                0
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

    companion object {
        const val EXTRA_CHANNEL_NUMBER = "cinema_channel_number"
        private const val SHOWREEL_HOLD_MS = 8000L
        private const val LOGO_HOLD_MS = 4000L
        private const val AD_BREAK_MS = 3 * 60 * 1000L
    }
}
