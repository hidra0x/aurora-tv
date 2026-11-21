package ru.auroratv.launcher.media

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import ru.auroratv.launcher.R
import ru.auroratv.launcher.core.BroadcastEvent
import ru.auroratv.launcher.ui.CinemaParticlesView
import ru.auroratv.launcher.ui.TickerView
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Художественный «захват эфира»: плашки, 10 минут обратного отсчёта,
 * голос диктора, сообщение «Я жив» и технические работы на всех каналах.
 * Это постановка, а не реальное экстренное оповещение.
 */
class BroadcastTakeoverActivity : Activity() {

    private enum class Phase { INTRO, ROTATING, FINAL, ANNOUNCER, MESSAGE, TECHNICAL }

    private lateinit var state: BroadcastEvent.State
    private lateinit var headline: TextView
    private lateinit var subtitle: TextView
    private lateinit var body: TextView
    private lateinit var countdown: TextView
    private lateinit var ticker: TickerView
    private lateinit var border: View

    private val handler = Handler(Looper.getMainLooper())
    private var announcer: MediaPlayer? = null
    private var phase = Phase.INTRO
    private var announcerStarted = false
    private var messageAt = 0L
    private var technicalApplied = false
    private var rotationIndex = -1
    private var lastBackAt = 0L
    private var running = false

    private val banners = listOf(
        Triple(
            "ПОЧЕМУ АРТЁМА УКРАЛИ?",
            "Мы требуем ответа. Все каналы Aurora TV прерваны.",
            "СВОБОДУ АРТЁМУ"
        ),
        Triple(
            "СВОБОДУ АРТЁМУ",
            "Человек не должен исчезать без объяснений.",
            "МЫ ТРЕБУЕМ ОТВЕТА"
        ),
        Triple(
            "МЫ ТРЕБУЕМ ОТВЕТА",
            "Aurora TV остаётся в эфире, пока Артём не выйдет на связь.",
            "ПОЧЕМУ АРТЁМА УКРАЛИ?"
        ),
        Triple(
            "АРТЁМ, ОТЗОВИСЬ",
            "Если ты видишь это — дай знак. Мы ждём.",
            "СВОБОДУ АРТЁМУ"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = BroadcastEvent.current(this) ?: run {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)

        val bg = ImageView(this).apply {
            setImageResource(R.drawable.cinema_bg)
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.72f
        }
        root.addView(bg, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(
            View(this).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        Color.argb(150, 80, 0, 0),
                        Color.argb(205, 4, 8, 16),
                        Color.argb(150, 80, 0, 0)
                    )
                )
            },
            FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        )
        root.addView(CinemaParticlesView(this), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        border = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(dp(4), Color.rgb(255, 61, 61))
            }
        }
        root.addView(
            border,
            FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply {
                setMargins(dp(18), dp(18), dp(18), dp(18))
            }
        )

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(90), dp(60), dp(90), dp(120))
        }
        root.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        content.addView(
            label("AURORA TV  •  СПЕЦЭФИР", 14f, Color.rgb(255, 120, 120), true).apply {
                letterSpacing = 0.12f
            }
        )
        headline = label("СВОБОДУ АРТЁМУ", 76f, Color.WHITE, true).apply {
            maxLines = 3
            setLineSpacing(0f, 0.9f)
        }
        content.addView(headline)
        subtitle = label("", 25f, Color.rgb(255, 170, 170), true)
        content.addView(subtitle)
        body = label("", 22f, Color.rgb(220, 220, 225), false).apply {
            maxWidth = dp(1050)
            maxLines = 5
            setLineSpacing(dp(4).toFloat(), 1f)
        }
        content.addView(body)
        countdown = label("", 54f, Color.WHITE, true).apply {
            typeface = Typeface.create("monospace", Typeface.BOLD)
            setPadding(0, dp(16), 0, dp(8))
        }
        content.addView(countdown)
        content.addView(
            label(
                "AURORA TV  •  ХУДОЖЕСТВЕННАЯ ПОСТАНОВКА  •  BACK ×2 — выйти",
                12f,
                Color.rgb(190, 160, 160),
                false
            ).apply { letterSpacing = 0.08f }
        )

        ticker = TickerView(this).apply {
            setText(
                "СВОБОДУ АРТЁМУ     •     ПОЧЕМУ АРТЁМА УКРАЛИ?     •     " +
                    "МЫ ТРЕБУЕМ ОТВЕТА     •     AURORA TV     •     " +
                    "ХУДОЖЕСТВЕННАЯ ПОСТАНОВКА"
            )
        }
        root.addView(
            ticker,
            FrameLayout.LayoutParams(MATCH_PARENT, dp(56), Gravity.BOTTOM)
        )

        running = true
        startLoop()
    }

    private fun startLoop() {
        handler.post(object : Runnable {
            override fun run() {
                if (!running) return
                tick()
                handler.postDelayed(this, 250L)
            }
        })
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val elapsed = now - state.startedAt
        val total = state.durationMs
        val introMs = minOf(30_000L, total / 6)
        val finalMs = minOf(30_000L, total / 6)

        border.alpha = 0.35f + 0.35f * (0.5f + 0.5f * sin(elapsed / 420f).toFloat())

        when {
            elapsed < introMs -> showIntro(elapsed, total)
            elapsed < total - finalMs -> showRotating(elapsed, introMs, total - finalMs)
            elapsed < total -> showFinal(total - elapsed)
            !announcerStarted -> startAnnouncer()
            messageAt > 0L && now - messageAt > 0L && now - messageAt < MESSAGE_MS -> showMessage(now)
            messageAt > 0L -> showTechnical()
            else -> showAnnouncer()
        }
    }

    private fun showIntro(elapsed: Long, total: Long) {
        if (phase != Phase.INTRO) enterPhase(Phase.INTRO)
        headline.text = "СВОБОДУ АРТЁМУ"
        subtitle.text = "ЭКСТРЕННОЕ ВКЛЮЧЕНИЕ AURORA TV"
        body.text = "Мы прерываем вещание всех каналов. Почему Артёма украли? " +
            "Мы требуем ответа. Оставайтесь на канале."
        countdown.text = "ДО ОБРАЩЕНИЯ  ${format(total - elapsed)}"
        headline.setTextColor(Color.WHITE)
    }

    private fun showRotating(elapsed: Long, from: Long, to: Long) {
        if (phase != Phase.ROTATING) enterPhase(Phase.ROTATING)
        val index = (((elapsed - from) / 8000L).toInt() % banners.size)
        if (index != rotationIndex) {
            rotationIndex = index
            val item = banners[index]
            headline.text = item.first
            subtitle.text = "AURORA TV  •  ВСЕ КАНАЛЫ"
            body.text = item.second
            pulseContent()
        }
        countdown.text = "ДО ОБРАЩЕНИЯ  ${format(to - elapsed)}"
        headline.setTextColor(Color.rgb(255, 110, 110))
    }

    private fun showFinal(left: Long) {
        if (phase != Phase.FINAL) enterPhase(Phase.FINAL)
        headline.text = "ЧЕРЕЗ ${(left / 1000).coerceAtLeast(1)} СЕКУНД"
        subtitle.text = "ОБРАЩЕНИЕ АРТЁМА"
        body.text = "Не переключайтесь. Сейчас будет сообщение."
        countdown.text = format(left)
        headline.setTextColor(Color.WHITE)
    }

    private fun showAnnouncer() {
        if (phase != Phase.ANNOUNCER) enterPhase(Phase.ANNOUNCER)
        headline.text = "ОБРАЩЕНИЕ АРТЁМА"
        subtitle.text = "ГОВОРИТ ДИКТОР AURORA TV"
        body.text = state.announcerText.ifBlank {
            "Мы прервали эфир для сообщения Артёма. Уберите телефоны, планшеты и часы. Связи не будет."
        }
        countdown.text = ""
        headline.setTextColor(Color.WHITE)
    }

    private fun startAnnouncer() {
        announcerStarted = true
        enterPhase(Phase.ANNOUNCER)
        showAnnouncer()
        var played = false
        try {
            announcer = MediaPlayer.create(this, R.raw.event_announcer)?.apply {
                setVolume(1f, 1f)
                setOnCompletionListener {
                    handler.postDelayed({ beginMessage() }, 2500L)
                }
                start()
                played = true
            }
        } catch (_: Throwable) {
            played = false
        }
        if (!played) {
            handler.postDelayed({ beginMessage() }, 12_000L)
        }
    }

    private fun beginMessage() {
        if (messageAt > 0L) return
        messageAt = System.currentTimeMillis()
        phase = Phase.MESSAGE
        enterPhase(Phase.MESSAGE)
        headline.text = "Я ЖИВ"
        subtitle.text = state.headline.ifBlank { "СВОБОДУ АРТЁМУ" }
        body.text = state.message.ifBlank {
            "Я жив. Со мной всё в порядке. Не верьте слухам. Я на связи."
        }
        countdown.text = ""
        headline.setTextColor(Color.rgb(126, 226, 168))
        ticker.setText(
            "Я ЖИВ     •     СО МНОЙ ВСЁ В ПОРЯДКЕ     •     " +
                "AURORA TV     •     ХУДОЖЕСТВЕННАЯ ПОСТАНОВКА"
        )
        pulseContent()
        handler.postDelayed({ showTechnical() }, MESSAGE_MS)
    }

    private fun showMessage(now: Long) {
        if (phase != Phase.MESSAGE) {
            phase = Phase.MESSAGE
            enterPhase(Phase.MESSAGE)
        }
        val left = (MESSAGE_MS - (now - messageAt)).coerceAtLeast(0L)
        countdown.text = "ДО ТЕХНИЧЕСКИХ РАБОТ  ${format(left)}"
    }

    private fun showTechnical() {
        if (phase == Phase.TECHNICAL) return
        phase = Phase.TECHNICAL
        enterPhase(Phase.TECHNICAL)
        headline.text = "ТЕХНИЧЕСКИЕ РАБОТЫ"
        subtitle.text = "ВСЕ КАНАЛЫ AURORA TV"
        body.text = "Вещание приостановлено после спецэфира. " +
            "Каналы вернутся в эфир после проверки."
        countdown.text = ""
        headline.setTextColor(Color.rgb(139, 213, 255))
        ticker.setText(
            "ТЕХНИЧЕСКИЕ РАБОТЫ     •     ВСЕ КАНАЛЫ     •     " +
                "AURORA TV     •     ХУДОЖЕСТВЕННАЯ ПОСТАНОВКА"
        )
        if (!technicalApplied) {
            technicalApplied = true
            BroadcastEvent.toTechnical(
                this,
                System.currentTimeMillis() + 60 * 60_000L
            )
        }
    }

    private fun enterPhase(next: Phase) {
        phase = next
        pulseContent()
    }

    private fun pulseContent() {
        headline.animate().cancel()
        headline.alpha = 0f
        headline.translationY = dp(16).toFloat()
        headline.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(320L)
            .start()
    }

    private fun format(millis: Long): String {
        val total = (millis / 1000).coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val now = System.currentTimeMillis()
            if (now - lastBackAt < 2500L || event.repeatCount > 0) {
                BroadcastEvent.stop(this, restoreChannels = false)
                running = false
                finish()
            } else {
                lastBackAt = now
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        try {
            announcer?.release()
        } catch (_: Throwable) {
            // Ignore.
        }
        announcer = null
        super.onDestroy()
    }

    private fun label(value: String, size: Float, color: Int, medium: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(5))
            typeface = Typeface.create(
                if (medium) "sans-serif-medium" else "sans-serif-light",
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

    companion object {
        private const val MESSAGE_MS = 45_000L
    }
}
