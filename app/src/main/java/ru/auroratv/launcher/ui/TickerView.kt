package ru.auroratv.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.roundToInt

/**
 * Бегущая строка инфоканала: тёмная полоса, метка AURORA TV и
 * непрерывно прокручивающийся текст.
 */
class TickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val barPaint = Paint().apply {
        color = Color.argb(228, 3, 9, 20)
    }
    private val edgePaint = Paint().apply {
        color = Color.argb(190, 139, 213, 255)
    }
    private val labelBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 18, 55, 96)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(139, 213, 255)
        textSize = sp(12f)
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        letterSpacing = 0.06f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(16f)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private var content = ""
    private var offset = 0f
    private var lastFrame = 0L
    private val speed = dp(105f)
    private val gap = dp(72f)
    private val labelWidth = dp(132f)
    private val edgePadding = dp(18f)
    private val labelRect = RectF()

    fun setText(value: String) {
        if (value == content) return
        content = value
        offset = 0f
        lastFrame = 0L
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return

        canvas.drawRect(0f, 0f, width, height, barPaint)
        canvas.drawRect(0f, 0f, width, dp(2f), edgePaint)

        labelRect.set(
            edgePadding,
            dp(8f),
            edgePadding + labelWidth,
            height - dp(8f)
        )
        canvas.drawRoundRect(labelRect, dp(8f), dp(8f), labelBackground)
        canvas.drawText(
            "AURORA TV",
            labelRect.centerX(),
            labelRect.centerY() + labelPaint.textSize / 3f,
            labelPaint
        )

        val textLeft = labelRect.right + dp(14f)
        val textWidth = textPaint.measureText(content)
        val block = textWidth + gap
        if (block <= 0f) return

        val now = SystemClock.uptimeMillis()
        if (lastFrame != 0L) {
            val delta = ((now - lastFrame).coerceAtMost(60L)) / 1000f
            offset -= speed * delta
            if (offset <= -block) offset += block
        }
        lastFrame = now

        canvas.save()
        canvas.clipRect(textLeft, 0f, width, height)
        val baseline = height / 2f + textPaint.textSize / 3f
        var x = textLeft + offset
        canvas.drawText(content, x, baseline, textPaint)
        x += block
        canvas.drawText(content, x, baseline, textPaint)
        canvas.restore()

        if (isShown) postInvalidateOnAnimation()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrame = 0L
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        lastFrame = 0L
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun dp(value: Int): Float = dp(value.toFloat())

    private fun sp(value: Float): Float =
        (value * resources.displayMetrics.scaledDensity).coerceAtLeast(value * 0.75f)
}
