package ru.auroratv.launcher.ui

import android.content.Intent
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import ru.auroratv.launcher.MainActivity
import ru.auroratv.launcher.R
import ru.auroratv.launcher.core.AppEntry
import ru.auroratv.launcher.core.Prefs

abstract class LauncherScreen(
    protected val host: MainActivity,
    protected val prefs: Prefs
) : FrameLayout(host) {

    init {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        isFocusable = false
        clipChildren = false
        clipToPadding = false
    }

    abstract fun onAppsLoaded(apps: List<AppEntry>)

    open fun onFavoritesChanged() {}

    abstract fun requestInitialFocus()

    open fun onShown() {}

    open fun onHidden() {}

    open fun onBack(): Boolean = false

    open fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean = false

    open fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean = false

    open fun hint(): String = "OK — открыть    ← → — выбор    MENU — настройки"

    protected fun dp(value: Int): Int = host.dp(value)

    protected fun color(id: Int): Int = ContextCompat.getColor(host, id)

    protected fun makeText(
        value: String,
        sizeSp: Float,
        color: Int,
        medium: Boolean = false,
        light: Boolean = false
    ): TextView = TextView(host).apply {
        text = value
        textSize = sizeSp
        setTextColor(color)
        typeface = Typeface.create(
            if (light) "sans-serif-light" else if (medium) "sans-serif-medium" else "sans-serif",
            Typeface.NORMAL
        )
    }

    protected fun makeButton(label: String, onClick: () -> Unit): TextView = makeText(
        label,
        12f,
        color(R.color.accent),
        medium = true
    ).apply {
        gravity = Gravity.CENTER
        isFocusable = true
        isClickable = true
        setPadding(dp(16), 0, dp(16), 0)
        background = host.getDrawable(R.drawable.bg_nav_item)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            dp(32)
        )
        setOnClickListener { onClick() }
    }

    protected fun sectionHeader(title: String, trailing: View? = null): LinearLayout {
        val row = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(
            makeText(title, 16f, color(R.color.text), medium = true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        trailing?.let {
            row.addView(it)
        }
        return row
    }
}
