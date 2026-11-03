package ru.auroratv.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

/**
 * Медленно падающие блики поверх синего фона — как в заставке кинозала.
 * Лёгкий по нагрузке: несколько десятков радиальных градиентов, 30 fps.
 */
class CinemaParticlesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private class Particle(
        val startX: Float,
        val startY: Float,
        val radius: Float,
        val speed: Float,
        val drift: Float,
        val phase: Float,
        val shader: RadialGradient
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particles = ArrayList<Particle>()
    private var builtWidth = 0
    private var builtHeight = 0
    private val startedAt = SystemClock.uptimeMillis()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        if (width != builtWidth || height != builtHeight) {
            builtWidth = width
            builtHeight = height
            buildParticles()
        }

        val t = (SystemClock.uptimeMillis() - startedAt) / 1000f
        val h = height.toFloat()
        for (particle in particles) {
            val span = h + particle.radius * 2f
            val y = (particle.startY + particle.speed * t) % span - particle.radius
            val x = particle.startX +
                sin((t * 0.25f + particle.phase).toDouble()).toFloat() * particle.drift

            canvas.save()
            canvas.translate(x, y)
            paint.shader = particle.shader
            paint.alpha = (150 + 105 *
                sin((t * 0.7f + particle.phase).toDouble()).toFloat())
                .toInt()
                .coerceIn(45, 255)
            canvas.drawCircle(0f, 0f, particle.radius, paint)
            canvas.restore()
        }
        paint.shader = null
        postInvalidateDelayed(33L)
    }

    private fun buildParticles() {
        particles.clear()
        val w = width.toFloat()
        val h = height.toFloat()
        val random = Random(20261103)
        repeat(46) {
            val radius = w * (0.0035f + random.nextFloat() * 0.011f)
            val base = when (random.nextInt(3)) {
                0 -> Color.rgb(139, 213, 255)
                1 -> Color.rgb(205, 235, 255)
                else -> Color.rgb(110, 170, 255)
            }
            val alpha = 60 + random.nextInt(90)
            val shader = RadialGradient(
                0f,
                0f,
                radius,
                intArrayOf(
                    Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base)),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            particles.add(
                Particle(
                    startX = random.nextFloat() * w,
                    startY = random.nextFloat() * h,
                    radius = radius,
                    speed = h * (0.012f + random.nextFloat() * 0.035f),
                    drift = w * (0.003f + random.nextFloat() * 0.01f),
                    phase = random.nextFloat() * 6.283f,
                    shader = shader
                )
            )
        }
    }
}
