package ru.auroratv.launcher.media

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import ru.auroratv.launcher.R
import ru.auroratv.launcher.core.ChannelControl
import ru.auroratv.launcher.core.MaintenanceScreen
import ru.auroratv.launcher.ui.TickerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * «Родной» экран профилактики Aurora TV: цветная телевизионная таблица,
 * периодический писк, живые часы и бегущая строка «Технические работы».
 *
 * Показывается поверх всех каналов — ТВ, «Совёнка» и кинозалов.
 */
class MaintenanceActivity : Activity() {

    private lateinit var bars: ColorBarsView
    private lateinit var clock: TextView
    private lateinit var status: TextView
    private lateinit var ticker: TickerView
    private var tone: ToneGenerator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var lastBackAt = 0L
    private var beepOn = true

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.currentTimeMillis()
            clock.text = SimpleDateFormat("HH:mm:ss", Locale("ru")).format(Date(now))
            val state = MaintenanceScreen.current(this@MaintenanceActivity)
            if (!state.active || (state.until in 1..now)) {
                running = false
                releaseTone()
                finish()
                return
            }
            status.text = buildString {
                append("ВСЕ КАНАЛЫ AURORA TV")
                if (state.reason.isNotBlank()) append("  •  ").append(state.reason)
                if (state.until > 0L) {
                    append("  •  ВОЗВРАТ В ЭФИР В ")
                    append(SimpleDateFormat("HH:mm", Locale("ru")).format(Date(state.until)))
                }
            }
            handler.postDelayed(this, 1000L)
        }
    }

    private val beep = object : Runnable {
        override fun run() {
            if (!running) return
            if (beepOn) playBeep()
            handler.postDelayed(this, 3000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!MaintenanceScreen.isActive(this)) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)

        bars = ColorBarsView(this)
        root.addView(bars, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(48), dp(36), dp(48), dp(96))
            isClickable = false
            isFocusable = false
        }
        root.addView(panel, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        panel.addView(
            label("AURORA TV  •  ТЕХНИЧЕСКИЕ РАБОТЫ", 18f, Color.WHITE, true).apply {
                setBackgroundColor(Color.argb(210, 0, 0, 0))
                setPadding(dp(22), dp(8), dp(22), dp(8))
                letterSpacing = 0.12f
            }
        )
        clock = label("", 96f, Color.WHITE, true).apply {
            typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD)
            setBackgroundColor(Color.argb(190, 0, 0, 0))
            setPadding(dp(28), dp(10), dp(28), dp(10))
            setPaddingRelative(dp(28), dp(10), dp(28), dp(10))
        }
        panel.addView(clock)
        status = label("", 20f, Color.rgb(230, 240, 255), true).apply {
            setBackgroundColor(Color.argb(210, 0, 0, 0))
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        panel.addView(status)
        panel.addView(
            label("СИГНАЛ ВРЕМЕННО ОТСУТСТВУЕТ  •  ПОЖАЛУЙСТА, ОЖИДАЙТЕ", 14f, Color.WHITE, false).apply {
                setBackgroundColor(Color.argb(190, 0, 0, 0))
                setPadding(dp(16), dp(6), dp(16), dp(6))
                letterSpacing = 0.08f
            }
        )

        ticker = TickerView(this).apply {
            setText(
                "ТЕХНИЧЕСКИЕ РАБОТЫ     •     ВСЕ КАНАЛЫ AURORA TV     •     " +
                    "СИГНАЛ ВОССТАНОВИТСЯ АВТОМАТИЧЕСКИ     •     AURORA TV"
            )
        }
        root.addView(
            ticker,
            FrameLayout.LayoutParams(MATCH_PARENT, dp(56), Gravity.BOTTOM)
        )

        running = true
        tick.run()
        handler.post(beep)
    }

    private fun playBeep() {
        try {
            if (tone == null) {
                tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            }
            // Короткий «пии» — две частоты, как в родной заставке «Совёнка».
            tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 140)
            handler.postDelayed({
                tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 140)
            }, 220L)
        } catch (_: Throwable) {
            beepOn = false
        }
    }

    private fun releaseTone() {
        try {
            tone?.release()
        } catch (_: Throwable) {
            // Ignore.
        }
        tone = null
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val now = System.currentTimeMillis()
            if (now - lastBackAt < 2500L || event.repeatCount > 0) {
                stopMaintenance()
                finish()
            } else {
                lastBackAt = now
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun stopMaintenance() {
        MaintenanceScreen.stop(this)
        ChannelControl.setAll(this, ChannelControl.STATE_LIVE)
        ChannelControl.setState(this, 0, ChannelControl.STATE_LIVE)
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        releaseTone()
        super.onDestroy()
    }

    private fun label(
        value: String,
        size: Float,
        color: Int,
        medium: Boolean
    ): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        typeface = android.graphics.Typeface.create(
            if (medium) "sans-serif-medium" else "sans-serif",
            android.graphics.Typeface.NORMAL
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

    /** Классическая цветная таблица: верхние полосы + нижняя шкала. */
    private class ColorBarsView(context: android.content.Context) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        private data class Band(val color: Int, val weight: Float)

        private val top = listOf(
            Band(Color.rgb(255, 255, 255), 1f),
            Band(Color.rgb(255, 255, 0), 1f),
            Band(Color.rgb(0, 255, 255), 1f),
            Band(Color.rgb(0, 255, 0), 1f),
            Band(Color.rgb(255, 0, 255), 1f),
            Band(Color.rgb(255, 0, 0), 1f),
            Band(Color.rgb(0, 0, 255), 1f)
        )

        private val middle = listOf(
            Band(Color.rgb(0, 0, 255), 1f),
            Band(Color.rgb(19, 19, 19), 1f),
            Band(Color.rgb(255, 0, 255), 1f),
            Band(Color.rgb(19, 19, 19), 1f),
            Band(Color.rgb(0, 255, 255), 1f),
            Band(Color.rgb(19, 19, 19), 1f),
            Band(Color.rgb(19, 19, 19), 1f)
        )

        private val lower = listOf(
            Band(Color.rgb(19, 19, 19), 1f),
            Band(Color.rgb(255, 255, 255), 1f),
            Band(Color.rgb(255, 0, 255), 1f),
            Band(Color.rgb(255, 255, 255), 1f),
            Band(Color.rgb(0, 255, 255), 1f),
            Band(Color.rgb(255, 255, 255), 1f),
            Band(Color.rgb(19, 19, 19), 1f)
        )

        private val bottom = listOf(
            Band(Color.rgb(0, 0, 180), 1f),
            Band(Color.rgb(0, 0, 255), 1f),
            Band(Color.rgb(0, 255, 255), 1f),
            Band(Color.rgb(190, 190, 190), 1f),
            Band(Color.rgb(255, 0, 255), 1f),
            Band(Color.rgb(255, 0, 0), 1f),
            Band(Color.rgb(180, 0, 0), 1f)
        )

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val h = height.toFloat()
            val topH = h * 0.66f
            val midH = h * 0.09f
            val lowH = h * 0.09f
            val bottomH = h - topH - midH - lowH

            drawBands(canvas, top, 0f, topH)
            drawBands(canvas, middle, topH, midH)
            drawBands(canvas, lower, topH + midH, lowH)
            drawBands(canvas, bottom, topH + midH + lowH, bottomH)
        }

        private fun drawBands(canvas: Canvas, bands: List<Band>, top: Float, bandHeight: Float) {
            val width = width.toFloat()
            val totalWeight = bands.sumOf { it.weight.toDouble() }.toFloat()
            var x = 0f
            bands.forEach { band ->
                val w = width * band.weight / totalWeight
                paint.color = band.color
                canvas.drawRect(x, top, x + w + 1f, top + bandHeight + 1f, paint)
                x += w
            }
        }
    }
}
