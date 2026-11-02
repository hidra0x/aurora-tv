package ru.auroratv.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class AuroraBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val backgroundPaint = Paint()
    private val glowPaint = Paint()
    private val vignettePaint = Paint()

    private var backgroundShader: Shader? = null
    private var glowA: Shader? = null
    private var glowB: Shader? = null
    private var vignetteShader: Shader? = null

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0 || height <= 0) return

        backgroundShader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(0xFF07090C.toInt(), 0xFF0A1017.toInt(), 0xFF06080B.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )

        glowA = RadialGradient(
            width * 0.10f, -height * 0.25f, width * 0.85f,
            intArrayOf(0x338BD5FF, 0x0A8BD5FF, 0x008BD5FF),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )

        glowB = RadialGradient(
            width * 1.02f, height * 1.12f, width * 0.72f,
            intArrayOf(0x2AC4B5FD, 0x08C4B5FD, 0x00C4B5FD),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )

        vignetteShader = RadialGradient(
            width / 2f, height / 2f, max(width, height) * 0.72f,
            intArrayOf(0x00000000, 0x00000000, 0x52000000),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        backgroundShader?.let {
            backgroundPaint.shader = it
            canvas.drawRect(0f, 0f, width, height, backgroundPaint)
        }
        glowA?.let {
            glowPaint.shader = it
            canvas.drawRect(0f, 0f, width, height, glowPaint)
        }
        glowB?.let {
            glowPaint.shader = it
            canvas.drawRect(0f, 0f, width, height, glowPaint)
        }
        vignetteShader?.let {
            vignettePaint.shader = it
            canvas.drawRect(0f, 0f, width, height, vignettePaint)
        }
    }
}
