package ru.auroratv.launcher.ui

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import ru.auroratv.launcher.MainActivity
import ru.auroratv.launcher.R
import ru.auroratv.launcher.core.AppEntry
import ru.auroratv.launcher.core.Prefs
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Видео-хаб: YouTube через SmartTube и RuTube через штатное приложение.
 * Никакого VPN и обхода сети: приложение только запускает клиенты и
 * показывает, отвечает ли YouTube у текущего провайдера.
 */
class VideoScreen(host: MainActivity, prefs: Prefs) : LauncherScreen(host, prefs) {

    private lateinit var youtubeCard: LinearLayout
    private lateinit var rutubeCard: LinearLayout
    private lateinit var status: TextView
    private var apps: List<AppEntry> = emptyList()

    init {
        val root = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            setPadding(dp(40), dp(4), dp(40), dp(8))
        }
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val header = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            makeText("Видео", 26f, color(R.color.text), light = true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(makeText("YouTube + RuTube", 11f, color(R.color.text_faint)))
        root.addView(
            header,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
        )

        val cards = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        youtubeCard = makeCard(
            "YouTube",
            "SmartTube: без рекламы, без VPN. Показывает то, что отдаёт сеть провайдера.",
            R.color.accent
        ) { openYoutube() }
        rutubeCard = makeCard(
            "RuTube",
            "Официальное приложение RuTube. Реклама и ограничения — на стороне сервиса.",
            R.color.text
        ) { openRutube() }
        cards.addView(
            youtubeCard,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(104)
            ).apply { bottomMargin = dp(10) }
        )
        cards.addView(
            rutubeCard,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(104)
            )
        )
        root.addView(
            cards,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        status = makeText("", 12f, color(R.color.text_faint)).apply {
            setPadding(0, dp(16), 0, dp(8))
            maxLines = 2
        }
        root.addView(
            status,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            makeButton("Проверить YouTube") { checkYoutube() },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(32)
            )
        )
    }

    override fun onAppsLoaded(apps: List<AppEntry>) {
        this.apps = apps
        updateCards()
    }

    private fun updateCards() {
        val youtube = findYoutube()
        val rutube = findRutube()
        youtubeCard.alpha = if (youtube != null) 1f else 0.45f
        rutubeCard.alpha = if (rutube != null) 1f else 0.45f
    }

    override fun requestInitialFocus() {
        youtubeCard.requestFocus()
    }

    override fun hint(): String = "OK — открыть    MENU — настройки"

    private fun openYoutube() {
        val app = findYoutube()
        if (app == null) {
            host.toast("SmartTube не установлен")
            return
        }
        host.launchApp(app)
    }

    private fun openRutube() {
        val app = findRutube()
        if (app == null) {
            host.toast("RuTube не установлен")
            return
        }
        host.launchApp(app)
    }

    private fun findYoutube(): AppEntry? {
        val preferred = listOf(
            "org.smarttube.stable",
            "com.teamsmart.videomanager.tv",
            "com.liskovsoft.smarttubetv.beta",
            "com.google.android.youtube.tv",
            "com.google.android.youtube"
        )
        for (packageName in preferred) {
            apps.firstOrNull { it.packageName == packageName }?.let { return it }
        }
        return null
    }

    private fun findRutube(): AppEntry? = apps.firstOrNull {
        it.packageName == "ru.rutube.app"
    }

    private fun checkYoutube() {
        status.setTextColor(color(R.color.text_dim))
        status.text = "Проверяю YouTube…"
        val finished = AtomicBoolean(false)
        val timeout = Runnable {
            if (finished.compareAndSet(false, true)) {
                status.setTextColor(color(R.color.warning))
                status.text = "YouTube не отвечает. Это сеть/провайдер: без VPN или прокси видео не загрузятся."
            }
        }
        host.window.decorView.postDelayed(timeout, 7000L)

        Thread {
            val result = try {
                val connection = URL("https://www.youtube.com/generate_204")
                    .openConnection() as HttpURLConnection
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "AuroraTV/1.3 (Android TV launcher)")
                val code = connection.responseCode
                connection.disconnect()
                code in 200..399
            } catch (_: Throwable) {
                false
            }
            host.runOnUiThread {
                if (finished.compareAndSet(false, true)) {
                    host.window.decorView.removeCallbacks(timeout)
                    if (result) {
                        status.setTextColor(color(R.color.green))
                        status.text = "YouTube отвечает. Если SmartTube всё равно крутит — обновите SmartTube."
                    } else {
                        status.setTextColor(color(R.color.warning))
                        status.text = "YouTube не отвечает. Это сеть/провайдер: без VPN или прокси видео не загрузятся."
                    }
                }
            }
        }.apply {
            isDaemon = true
            name = "aurora-video-check"
        }.start()
    }

    private fun makeCard(
        title: String,
        subtitle: String,
        accent: Int,
        onClick: () -> Unit
    ): LinearLayout = LinearLayout(host).apply {
        orientation = LinearLayout.VERTICAL
        isFocusable = true
        isClickable = true
        gravity = Gravity.CENTER_VERTICAL
        background = host.getDrawable(R.drawable.bg_row)
        setPadding(dp(24), dp(12), dp(24), dp(12))
        setOnClickListener { onClick() }
        applyFocusScale({ prefs.animations }, 1.02f)
        addView(makeText(title, 22f, color(accent), light = true))
        addView(
            makeText(subtitle, 12f, color(R.color.text_dim)).apply {
                setPadding(0, dp(4), 0, 0)
                maxLines = 2
            }
        )
    }

}
