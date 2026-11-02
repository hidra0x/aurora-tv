package ru.auroratv.launcher.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

object IconLoader {

    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val executor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "aurora-icons").apply { isDaemon = true }
    }

    private val main = Handler(Looper.getMainLooper())

    fun load(
        context: Context,
        app: AppEntry,
        banner: Boolean,
        widthPx: Int,
        heightPx: Int,
        callback: (Bitmap?) -> Unit
    ) {
        val key = "${app.packageName}:${if (banner) "b" else "i"}:${widthPx}x${heightPx}"
        cache.get(key)?.let {
            callback(it)
            return
        }
        executor.execute {
            val bitmap = try {
                render(context.applicationContext, app, banner, widthPx, heightPx)
            } catch (_: Throwable) {
                null
            }
            if (bitmap != null) cache.put(key, bitmap)
            main.post { callback(bitmap) }
        }
    }

    private fun render(
        context: Context,
        app: AppEntry,
        banner: Boolean,
        width: Int,
        height: Int
    ): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val pm = context.packageManager
        val info = try {
            pm.getApplicationInfo(app.packageName, 0)
        } catch (_: Throwable) {
            return null
        }
        val loaded: Drawable? = try {
            if (banner && app.hasBanner) info.loadBanner(pm) else info.loadIcon(pm)
        } catch (_: Throwable) {
            null
        }
        val drawable: Drawable = loaded ?: return null

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (banner) {
            canvas.drawColor(Color.parseColor("#0E141B"))
        } else {
            canvas.drawColor(Color.TRANSPARENT)
        }
        drawDrawable(canvas, drawable, width, height, fill = banner)
        return bitmap
    }

    private fun drawDrawable(
        canvas: Canvas,
        drawable: Drawable,
        width: Int,
        height: Int,
        fill: Boolean
    ) {
        val intrinsicWidth = drawable.intrinsicWidth.takeIf { it > 0 } ?: width
        val intrinsicHeight = drawable.intrinsicHeight.takeIf { it > 0 } ?: height
        val scale = if (fill) {
            max(width.toFloat() / intrinsicWidth, height.toFloat() / intrinsicHeight)
        } else {
            min(width.toFloat() / intrinsicWidth, height.toFloat() / intrinsicHeight)
        }
        canvas.save()
        canvas.translate(
            (width - intrinsicWidth * scale) / 2f,
            (height - intrinsicHeight * scale) / 2f
        )
        canvas.scale(scale, scale)
        drawable.setBounds(0, 0, intrinsicWidth, intrinsicHeight)
        drawable.draw(canvas)
        canvas.restore()
    }
}
