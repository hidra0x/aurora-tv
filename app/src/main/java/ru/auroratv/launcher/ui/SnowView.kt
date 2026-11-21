package ru.auroratv.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

/** Мягкий снег для главного экрана новогоднего эфира. */
class SnowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class Flake(
        var x: Float,
        var y: Float,
        val radius: Float,
        val speed: Float,
        val drift: Float,
        val phase: Float,
        val alpha: Int
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val random = Random(System.currentTimeMillis())
    private var flakes: List<Flake> = emptyList()
    private var lastNanos = 0L

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0 || height <= 0) return
        flakes = List(90) {
            Flake(
                x = random.nextFloat() * width,
                y = random.nextFloat() * height,
                radius = 1.2f + random.nextFloat() * 3.8f,
                speed = 22f + random.nextFloat() * 55f,
                drift = 8f + random.nextFloat() * 22f,
                phase = random.nextFloat() * 6.28f,
                alpha = 95 + random.nextInt(120)
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0.016f else ((now - lastNanos) / 1_000_000_000f).coerceAtMost(0.05f)
        lastNanos = now
        val time = now / 1_000_000_000f
        for (flake in flakes) {
            flake.y += flake.speed * dt
            flake.x += sin(time * 0.7f + flake.phase) * flake.drift * dt
            if (flake.y - flake.radius > height) {
                flake.y = -flake.radius * 2
                flake.x = random.nextFloat() * width
            }
            if (flake.x < -flake.radius) flake.x = width + flake.radius
            if (flake.x > width + flake.radius) flake.x = -flake.radius
            paint.alpha = flake.alpha
            canvas.drawCircle(flake.x, flake.y, flake.radius, paint)
            if (flake.radius > 3.4f) {
                paint.alpha = (flake.alpha * 0.65f).toInt()
                paint.strokeWidth = 1f
                paint.style = Paint.Style.STROKE
                canvas.drawCircle(flake.x, flake.y, flake.radius + 2.2f, paint)
                paint.style = Paint.Style.FILL
            }
        }
        postInvalidateOnAnimation()
    }
}
