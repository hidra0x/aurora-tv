package ru.auroratv.launcher.media

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import android.widget.ScrollView
import android.widget.TextView
import ru.auroratv.launcher.R
import ru.auroratv.launcher.core.CinemaChannel
import ru.auroratv.launcher.core.CinemaFilm
import ru.auroratv.launcher.core.CinemaRepository
import ru.auroratv.launcher.core.CinemaSchedule
import ru.auroratv.launcher.ui.CinemaParticlesView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Общее расписание кинозалов: все каналы 20–32 на ближайший сеанс,
 * как в телевизионной афише. Синий фон, блики, анимация строк.
 */
class CinemaScheduleActivity : Activity() {

    private data class LineupRow(
        val channel: CinemaChannel,
        val film: CinemaFilm,
        val startAt: Long,
        val active: Boolean
    )

    private lateinit var channel: CinemaChannel
    private lateinit var background: ImageView
    private lateinit var statusLabel: TextView
    private val ambient by lazy { CinemaAmbient(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var activeStart: Long? = null
    private var ticker: Runnable? = null
    private var selectedRow: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val number = intent.getIntExtra(EXTRA_CHANNEL_NUMBER, -1)
        channel = CinemaRepository.channelFor(this, number) ?: run {
            finish()
            return
        }
        val now = try {
            CinemaSchedule.now(this, channel)
        } catch (_: Throwable) {
            finish()
            return
        }
        activeStart = now.active?.startAt

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        val root = FrameLayout(this)
        setContentView(root)

        background = ImageView(this).apply {
            setImageResource(R.drawable.cinema_bg)
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.95f
        }
        root.addView(
            background,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        background.animate().scaleX(1.04f).scaleY(1.04f).setDuration(14000L).start()

        root.addView(
            CinemaParticlesView(this),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(64), dp(30), dp(64), dp(18))
            background = GradientDrawable().apply {
                setColor(Color.argb(64, 2, 8, 20))
            }
        }
        root.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val accent = try {
            Color.parseColor(channel.color)
        } catch (_: Throwable) {
            Color.rgb(139, 213, 255)
        }

        content.addView(text("AURORA TV  •  КИНОЗАЛЫ 20–32", 14f, accent, true))
        content.addView(text("РАСПИСАНИЕ СЕАНСОВ", 36f, Color.WHITE, true))
        content.addView(
            text(
                "НАЧАЛО СЕАНСОВ В 14:00 И 18:00  •  ВЫБРАН КАНАЛ ${channel.number}",
                13f,
                Color.rgb(190, 210, 240),
                false
            )
        )
        statusLabel = text("", 13f, accent, true)
        content.addView(statusLabel)
        updateStatus()

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, dp(8), 0, dp(2))
        }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = false
        }
        scroll.addView(
            list,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        content.addView(
            scroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val lineup = CinemaRepository.channels(this).mapNotNull { item ->
            val schedule = try {
                CinemaSchedule.now(this, item)
            } catch (_: Throwable) {
                null
            } ?: return@mapNotNull null
            LineupRow(
                channel = item,
                film = schedule.active?.film ?: schedule.next.film,
                startAt = schedule.active?.startAt ?: schedule.next.startAt,
                active = schedule.active != null
            )
        }

        lineup.forEachIndexed { index, row ->
            val rowView = makeRow(row, row.channel.number == channel.number)
            list.addView(
                rowView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(92)
                ).apply { bottomMargin = dp(7) }
            )
            rowView.alpha = 0f
            rowView.translationX = -dp(26).toFloat()
            rowView.animate()
                .alpha(1f)
                .translationX(0f)
                .setStartDelay(index * 55L)
                .setDuration(320L)
                .start()
            if (row.channel.number == channel.number) selectedRow = rowView
        }

        val footer = text(
            "Каждый день новые сеансы     OK — открыть кинозал     BACK — назад",
            12f,
            Color.rgb(180, 200, 230),
            false
        )
        content.addView(footer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(30)
        ).apply { topMargin = dp(4) })

        scroll.postDelayed({
            val target = selectedRow ?: return@postDelayed
            scroll.scrollTo(0, (target.top - dp(4)).coerceAtLeast(0))
            target.requestFocus()
        }, 320L)
    }

    private fun makeRow(row: LineupRow, selected: Boolean): LinearLayout {
        val accent = try {
            Color.parseColor(row.channel.color)
        } catch (_: Throwable) {
            Color.rgb(139, 213, 255)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(if (row.active) Color.argb(216, 16, 48, 92) else Color.argb(178, 7, 22, 44))
                cornerRadius = dp(12).toFloat()
                setStroke(
                    dp(if (selected) 2 else 1),
                    if (selected) accent else Color.argb(55, 130, 170, 230)
                )
            }
            setOnClickListener { startPlayer(row.channel.number) }
        }

        val badge = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.argb(120, 20, 55, 100))
                cornerRadius = dp(9).toFloat()
                if (row.active) setStroke(dp(1), accent)
            }
        }
        badge.addView(
            text(row.channel.number.toString(), 20f, accent, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 0)
            }
        )
        badge.addView(
            text(row.channel.name.uppercase(), 7.5f, Color.rgb(180, 200, 230), true).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(0, 0, 0, 0)
            }
        )
        container.addView(badge, LinearLayout.LayoutParams(dp(96), dp(62)))

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(10), 0)
        }
        info.addView(
            text(row.film.title, 18f, Color.WHITE, true).apply {
                maxLines = 1
                setPadding(0, 0, 0, 0)
            }
        )
        val meta = "${row.channel.name}  •  ${row.film.year}  •  " +
            "${row.film.durationSeconds / 60} мин  •  ${row.film.rating}"
        info.addView(
            text(meta, 10.5f, Color.rgb(170, 195, 225), false).apply {
                maxLines = 1
                setPadding(0, dp(1), 0, 0)
            }
        )
        info.addView(
            text(row.film.description, 10.5f, Color.rgb(140, 165, 195), false).apply {
                maxLines = 1
                setPadding(0, dp(1), 0, 0)
            }
        )
        container.addView(
            info,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        val timeText = if (row.active) {
            "СЕЙЧАС"
        } else {
            SimpleDateFormat("HH:mm", Locale("ru")).format(Date(row.startAt))
        }
        val timeView = text(
            timeText,
            if (row.active) 10f else 11f,
            if (row.active) accent else Color.rgb(180, 200, 230),
            true
        ).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.argb(120, 20, 55, 100))
                cornerRadius = dp(8).toFloat()
            }
        }
        container.addView(timeView, LinearLayout.LayoutParams(dp(70), dp(36)))

        return container
    }

    private fun startPlayer(number: Int = channel.number) {
        val intent = Intent(this, CinemaPlayerActivity::class.java).apply {
            putExtra(CinemaPlayerActivity.EXTRA_CHANNEL_NUMBER, number)
        }
        startActivity(intent)
    }

    private fun updateStatus() {
        val now = try {
            CinemaSchedule.now(this, channel)
        } catch (_: Throwable) {
            return
        }
        statusLabel.text = if (now.active != null) {
            val left = (now.active.endAt - System.currentTimeMillis()).coerceAtLeast(0L)
            "Сейчас в эфире  •  до конца ${formatCountdown(left)}"
        } else {
            "До следующего сеанса: ${formatCountdown(now.waitMs)}  •  ${formatTime(now.next.startAt)}"
        }
        val currentStart = now.active?.startAt
        if (currentStart != activeStart) {
            activeStart = currentStart
            if (currentStart != null) {
                startPlayer(channel.number)
            } else {
                recreate()
            }
        }
    }

    private fun startTicker() {
        stopTicker()
        val runnable = object : Runnable {
            override fun run() {
                updateStatus()
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
        ambient.start()
        startTicker()
    }

    override fun onStop() {
        stopTicker()
        ambient.pause()
        super.onStop()
    }

    override fun onDestroy() {
        stopTicker()
        ambient.release()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun text(value: String, size: Float, color: Int, medium: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            setPadding(0, dp(3), 0, dp(3))
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
        const val EXTRA_CHANNEL_NUMBER = "cinema_schedule_channel"
    }
}
