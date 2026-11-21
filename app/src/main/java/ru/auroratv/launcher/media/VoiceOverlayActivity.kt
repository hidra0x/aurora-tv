package ru.auroratv.launcher.media

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import ru.auroratv.launcher.core.VoiceBridge
import ru.auroratv.launcher.core.VoicePhase
import ru.auroratv.launcher.core.VoiceState
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/**
 * Полноэкранный экран голосового помощника. Демон на Алисе присылает фазы
 * «слушает / думает / ответ», а также команды; здесь всё это отображается
 * поверх эфира. В режиме радио рисуется эквалайзер на весь экран.
 */
class VoiceOverlayActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var equalizer: ValueAnimator? = null
    private var pulse: ValueAnimator? = null
    private var dots: ValueAnimator? = null
    private var ready = false
    private var bars = ArrayList<View>()
    private var phase = VoicePhase.IDLE

    private lateinit var root: FrameLayout
    private lateinit var glow: View
    private lateinit var card: LinearLayout
    private lateinit var badge: TextView
    private lateinit var title: TextView
    private lateinit var heard: TextView
    private lateinit var answer: TextView
    private lateinit var hint: TextView
    private lateinit var equalizerBox: LinearLayout
    private lateinit var timerBox: LinearLayout
    private lateinit var timerValue: TextView
    private lateinit var timerProgress: View
    private lateinit var candidates: TextView
    private lateinit var pulseDot: View

    private val listener: (VoiceState) -> Unit = { render(it) }

    private val tick = object : Runnable {
        override fun run() {
            if (!isFinishing && phase == VoicePhase.TIMER) {
                renderTimer(VoiceBridge.current)
                handler.postDelayed(this, 500L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VoiceBridge.attach(this)
        buildUi()
        ready = true
        render(VoiceBridge.current)
    }

    override fun onStart() {
        super.onStart()
        VoiceBridge.listen(listener)
    }

    override fun onStop() {
        VoiceBridge.unlisten(listener)
        handler.removeCallbacks(tick)
        super.onStop()
    }

    override fun onDestroy() {
        equalizer?.cancel()
        pulse?.cancel()
        dots?.cancel()
        super.onDestroy()
    }

    override fun onBackPressed() {
        VoiceBridge.reset(this)
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 &&
            VoiceBridge.current.candidates.isNotEmpty()
        ) {
            val index = keyCode - KeyEvent.KEYCODE_0 - 1
            VoiceBridge.selectCandidate(this, index)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun buildUi() {
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(238, 5, 10, 20))
        }

        // Мягкое свечение позади карточки.
        glow = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.argb(90, 60, 150, 230), Color.argb(0, 10, 20, 40))
            ).apply { shape = GradientDrawable.OVAL }
            layoutParams = FrameLayout.LayoutParams(dp(1100), dp(1100)).apply {
                gravity = Gravity.CENTER
            }
        }
        root.addView(glow)

        card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(dp(1500), FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
        }

        badge = TextView(this).apply {
            text = "АЛИСА"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(139, 213, 255))
            gravity = Gravity.CENTER
        }
        card.addView(badge)

        title = TextView(this).apply {
            text = "Внимание. Алиса слушает"
            textSize = 46f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(18))
        }
        card.addView(title)

        pulseDot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(139, 213, 255))
            }
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                topMargin = dp(4)
                bottomMargin = dp(20)
            }
        }
        card.addView(pulseDot)

        heard = TextView(this).apply {
            textSize = 34f
            setTextColor(Color.rgb(210, 226, 245))
            gravity = Gravity.CENTER
            setPadding(dp(40), dp(6), dp(40), dp(6))
        }
        card.addView(heard)

        answer = TextView(this).apply {
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(48), dp(12), dp(48), dp(12))
            setLineSpacing(6f, 1.12f)
        }
        card.addView(answer)

        candidates = TextView(this).apply {
            textSize = 26f
            setTextColor(Color.rgb(245, 201, 122))
            gravity = Gravity.CENTER
            setPadding(dp(48), dp(8), dp(48), dp(8))
            visibility = View.GONE
        }
        card.addView(candidates)

        equalizerBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER or Gravity.BOTTOM
            setPadding(0, dp(24), 0, dp(24))
            visibility = View.GONE
        }
        repeat(44) { index ->
            val bar = View(this).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.BOTTOM_TOP,
                    intArrayOf(
                        Color.rgb(70, 140, 235),
                        Color.rgb(139, 213, 255),
                        Color.rgb(196, 181, 253)
                    )
                ).apply { cornerRadius = (dp(5) / 2).toFloat() }
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(24)).apply {
                    setMargins(dp(4), 0, dp(4), 0)
                }
            }
            bar.tag = index
            bars.add(bar)
            equalizerBox.addView(bar)
        }
        card.addView(equalizerBox)

        timerBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
        }
        timerValue = TextView(this).apply {
            text = "10:00"
            textSize = 96f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        timerBox.addView(timerValue)
        timerProgress = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.rgb(139, 213, 255))
                cornerRadius = dp(4).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(dp(640), dp(8)).apply {
                topMargin = dp(18)
            }
        }
        timerBox.addView(timerProgress)
        card.addView(timerBox)

        hint = TextView(this).apply {
            text = "Скажите: «включи радио», «включи НТВ +1», «поставь таймер на 10 минут», «что умеешь»"
            textSize = 22f
            setTextColor(Color.rgb(147, 160, 180))
            gravity = Gravity.CENTER
            setPadding(dp(60), dp(28), dp(60), 0)
        }
        card.addView(hint)

        root.addView(card)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(root)
    }

    private fun render(state: VoiceState) {
        if (!ready || isFinishing) return
        phase = state.phase
        if (phase == VoicePhase.IDLE) {
            finish()
            overridePendingTransition(0, 0)
            return
        }

        title.text = when (phase) {
            VoicePhase.LISTENING -> "Внимание. Алиса слушает"
            VoicePhase.THINKING -> "Алиса думает"
            VoicePhase.ANSWERING -> "Алиса отвечает"
            VoicePhase.RADIO -> "Радио"
            VoicePhase.TIMER -> "Таймер"
            VoicePhase.IDLE -> ""
        }
        badge.text = if (phase == VoicePhase.RADIO) "AURORA TV · RADIO" else "АЛИСА"

        heard.text = state.heard
        heard.visibility = if (state.heard.isNotBlank() &&
            (phase == VoicePhase.LISTENING || phase == VoicePhase.THINKING)
        ) View.VISIBLE else View.GONE

        answer.text = state.answer
        answer.visibility = if (state.answer.isNotBlank() &&
            phase != VoicePhase.RADIO && phase != VoicePhase.TIMER
        ) View.VISIBLE else View.GONE

        if (state.candidates.isNotEmpty() && phase == VoicePhase.ANSWERING) {
            candidates.text = state.candidates.take(6)
                .mapIndexed { i, c -> "${i + 1}) ${c.name}" }
                .joinToString("   ")
            candidates.visibility = View.VISIBLE
            hint.text = "Нажмите цифру 1–5 на пульте или скажите «первый», «второй»…"
        } else {
            candidates.visibility = View.GONE
            hint.text = if (phase == VoicePhase.RADIO) {
                "Ритм ${VoiceBridge.rhythm} BPM · скажите «быстрее», «медленнее» или «ритм 128»"
            } else {
                "Команды: «включи радио», «включи канал 5», «включи НТВ +1», " +
                    "«открой телегид», «поставь таймер на 10 минут», «что умеешь»"
            }
        }
        hint.visibility = if (state.connected ||
            phase == VoicePhase.ANSWERING || phase == VoicePhase.RADIO ||
            phase == VoicePhase.TIMER
        ) View.VISIBLE else View.GONE

        equalizerBox.visibility = if (phase == VoicePhase.RADIO) View.VISIBLE else View.GONE
        timerBox.visibility = if (phase == VoicePhase.TIMER) View.VISIBLE else View.GONE
        pulseDot.visibility = if (phase == VoicePhase.LISTENING || phase == VoicePhase.THINKING)
            View.VISIBLE else View.GONE

        root.setBackgroundColor(
            if (phase == VoicePhase.RADIO) Color.argb(250, 3, 7, 16)
            else Color.argb(238, 5, 10, 20)
        )
        card.setPadding(0, 0, 0, if (phase == VoicePhase.RADIO) dp(20) else 0)

        when (phase) {
            VoicePhase.LISTENING -> startPulse(1f)
            VoicePhase.THINKING -> startPulse(0.4f)
            VoicePhase.RADIO -> startEqualizer()
            VoicePhase.TIMER -> {
                stopEqualizer()
                stopPulse()
                renderTimer(state)
                handler.removeCallbacks(tick)
                handler.postDelayed(tick, 500L)
            }
            else -> {
                stopEqualizer()
                stopPulse()
            }
        }
    }

    private fun renderTimer(state: VoiceState) {
        if (!::timerValue.isInitialized) return
        val remaining = state.timerRemainingMs
        val totalSeconds = remaining / 1000L
        timerValue.text = String.format(
            "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L
        )
        val full = if (state.timerTotalMs > 0L) state.timerTotalMs else 10 * 60_000L
        val width = (dp(640) * (remaining.toFloat() / full)).toInt().coerceAtLeast(2)
        timerProgress.layoutParams.width = width
        timerProgress.requestLayout()
    }

    private fun startPulse(speed: Float) {
        stopPulse()
        pulse?.cancel()
        pulse = ValueAnimator.ofFloat(0.4f, 1f).apply {
            duration = (900L / speed).toLong()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val scale = anim.animatedValue as Float
                pulseDot.scaleX = scale * 2.4f
                pulseDot.scaleY = scale * 2.4f
                pulseDot.alpha = 0.45f + scale * 0.55f
            }
            start()
        }
    }

    private fun stopPulse() {
        pulse?.cancel()
        pulse = null
        dots?.cancel()
        dots = null
        if (::pulseDot.isInitialized) {
            pulseDot.scaleX = 1f
            pulseDot.scaleY = 1f
            pulseDot.alpha = 1f
        }
    }

    private fun startEqualizer() {
        stopPulse()
        if (equalizer?.isRunning == true) return
        equalizer?.cancel()
        // Кадры гонит ValueAnimator, а фазы бита считаем от реального времени:
        // так внешний /api/voice/beat может ровно выровнять удар с музыкой.
        equalizer = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animateEqualizer() }
            start()
        }
    }

    private fun animateEqualizer() {
        if (bars.isEmpty()) return
        val periodMs = 60_000f / VoiceBridge.rhythm.coerceAtLeast(40)
        val elapsed = System.currentTimeMillis() - VoiceBridge.beatAt
        val position = elapsed.toFloat() / periodMs
        val beatIndex = floor(position).toInt()
        val withinBeat = position - beatIndex
        val bar = Math.floorMod(beatIndex, 4)
        val accent = BEAT_ACCENTS[bar]
        val intensity = VoiceBridge.beatLevel
        // Резкий удар и плавное затухание до следующего.
        val envelope = exp(-3.6f * withinBeat)

        bars.forEach { view ->
            val i = view.tag as Int
            val band = i.toFloat() / bars.size
            val bandAccent = when {
                band < 0.22f -> if (bar == 0) 1f else 0.45f       // бас на первую долю
                band < 0.62f -> if (bar == 1 || bar == 3) 0.95f else 0.5f
                else -> if (bar % 2 == 1) 0.9f else 0.55f          // верха на «раз-два»
            }
            val seed = ((i * 37 + beatIndex * 17) % 13).toFloat() / 13f
            val target = (0.35f + 0.65f * seed) * accent * bandAccent * intensity
            val shimmer = 0.05f * (1f + sin(withinBeat * 6.28f + i * 0.5f))
            val level = (0.1f + target * envelope + shimmer).coerceIn(0.05f, 1f)
            view.layoutParams.height = dp(20) + (dp(230) * level).toInt()
            view.layoutParams.width = if (level > 0.72f) dp(22) else dp(18)
        }
        glow.alpha = (0.2f + 0.7f * envelope * accent).coerceIn(0.15f, 1f)
        equalizerBox.requestLayout()
    }

    private fun stopEqualizer() {
        equalizer?.cancel()
        equalizer = null
        if (::glow.isInitialized) glow.alpha = 1f
    }

    companion object {
        // 4/4: сильная доля, потом три более лёгких — «в ритм», а не хаос.
        private val BEAT_ACCENTS = floatArrayOf(1f, 0.62f, 0.82f, 0.7f)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
