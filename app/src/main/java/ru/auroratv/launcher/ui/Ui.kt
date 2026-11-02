package ru.auroratv.launcher.ui

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import kotlin.math.roundToInt

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

fun View.applyFocusScale(enabled: Boolean, focusedScale: Float = 1.04f) {
    applyFocusScale({ enabled }, focusedScale)
}

fun View.applyFocusScale(enabledProvider: () -> Boolean, focusedScale: Float = 1.04f) {
    setOnFocusChangeListener { view, hasFocus ->
        view.animate().cancel()
        view.translationZ = if (hasFocus) 12f * resources.displayMetrics.density else 0f
        if (enabledProvider()) {
            view.animate()
                .scaleX(if (hasFocus) focusedScale else 1f)
                .scaleY(if (hasFocus) focusedScale else 1f)
                .setDuration(if (hasFocus) 150L else 110L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            view.scaleX = 1f
            view.scaleY = 1f
        }
    }
}

fun RecyclerView.setupDefaults() {
    setHasFixedSize(true)
    itemAnimator = null
    overScrollMode = View.OVER_SCROLL_NEVER
    clipToPadding = false
    clipChildren = false
}

fun RecyclerView.focusFirstChild(attempt: Int = 0) {
    val delay = when (attempt) {
        0 -> 0L
        1 -> 100L
        2 -> 250L
        else -> 500L
    }
    postDelayed({
        if (!isShown || adapter?.itemCount == 0) return@postDelayed
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.isFocusable && child.visibility == View.VISIBLE) {
                child.requestFocus()
                return@postDelayed
            }
        }
        if (attempt < 3) focusFirstChild(attempt + 1)
    }, delay)
}

fun RecyclerView.focusChildAt(position: Int, attempt: Int = 0) {
    val delay = when (attempt) {
        0 -> 0L
        1 -> 100L
        2 -> 250L
        else -> 500L
    }
    postDelayed({
        val count = adapter?.itemCount ?: 0
        if (!isShown || count == 0) return@postDelayed
        val target = position.coerceIn(0, count - 1)
        layoutManager?.scrollToPosition(target)
        val holder = findViewHolderForAdapterPosition(target)
        if (holder != null && holder.itemView.isFocusable) {
            holder.itemView.requestFocus()
        } else if (attempt < 3) {
            focusChildAt(target, attempt + 1)
        }
    }, delay)
}

class HorizontalSpaceDecoration(private val space: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        outRect.right = space
    }
}

class VerticalSpaceDecoration(private val space: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        outRect.bottom = space
    }
}

class GridSpaceDecoration(
    private val columns: Int,
    private val spacing: Int
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return
        val column = position % columns
        outRect.left = column * spacing / columns
        outRect.right = spacing - (column + 1) * spacing / columns
        if (position >= columns) outRect.top = spacing
    }
}

fun String.capitalizeRu(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru")) else it.toString() }

fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "$bytes Б" else String.format(Locale("ru"), "%.1f %s", value, units[unit])
}
