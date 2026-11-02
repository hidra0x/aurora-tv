package ru.auroratv.launcher.ui

import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.auroratv.launcher.MainActivity
import ru.auroratv.launcher.R
import ru.auroratv.launcher.core.AppEntry
import ru.auroratv.launcher.core.Prefs
import ru.auroratv.launcher.weather.Weather
import ru.auroratv.launcher.weather.WeatherRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HomeScreen(host: MainActivity, prefs: Prefs) : LauncherScreen(host, prefs) {

    private val favoritesAdapter = AppTileAdapter(host, prefs, AppTileAdapter.Style.BANNER)
    private val appsAdapter = AppTileAdapter(host, prefs, AppTileAdapter.Style.ICON)
    private val favoritesList = RecyclerView(host)
    private val appsList = RecyclerView(host)

    private lateinit var greeting: TextView
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var weatherBlock: LinearLayout
    private lateinit var weatherIcon: TextView
    private lateinit var weatherTemp: TextView
    private lateinit var weatherCity: TextView
    private lateinit var weatherLabel: TextView
    private lateinit var favoritesEmpty: TextView

    private var allApps: List<AppEntry> = emptyList()
    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            ticker.postDelayed(this, 30_000L)
        }
    }

    init {
        val root = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            setPadding(dp(40), dp(4), dp(40), dp(4))
        }
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val info = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val left = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        greeting = makeText("", 10f, host.getColor(R.color.text_dim), medium = true).apply {
            letterSpacing = 0.16f
        }
        clock = makeText("00:00", 50f, host.getColor(R.color.text), light = true)
        date = makeText("", 14f, host.getColor(R.color.text_dim))
        left.addView(greeting)
        left.addView(clock)
        left.addView(date)
        info.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        weatherBlock = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        val weatherRow = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        weatherIcon = makeText("", 24f, host.getColor(R.color.accent))
        weatherTemp = makeText("--°", 30f, host.getColor(R.color.text), light = true)
        weatherRow.addView(weatherIcon)
        weatherRow.addView(
            weatherTemp,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(6) }
        )
        weatherCity = makeText(prefs.cityName, 11f, host.getColor(R.color.text_dim)).apply {
            gravity = Gravity.END
            maxWidth = dp(240)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        weatherLabel = makeText("", 10f, host.getColor(R.color.text_faint)).apply {
            gravity = Gravity.END
        }
        weatherBlock.addView(
            weatherRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.END }
        )
        weatherBlock.addView(
            weatherCity,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.END }
        )
        weatherBlock.addView(
            weatherLabel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.END }
        )
        info.addView(weatherBlock)
        root.addView(info, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(116)))

        val favoritesHeader = sectionHeader(
            "Избранное",
            makeText("Удерживайте OK — закрепить", 10f, host.getColor(R.color.text_faint))
        )
        root.addView(
            favoritesHeader,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30))
        )

        favoritesList.apply {
            layoutManager = LinearLayoutManager(host, RecyclerView.HORIZONTAL, false)
            adapter = favoritesAdapter
            setupDefaults()
            setPadding(0, dp(6), 0, dp(6))
            addItemDecoration(HorizontalSpaceDecoration(dp(12)))
        }
        favoritesEmpty = makeText(
            "Пока пусто. Удерживайте OK на плитке приложения — и оно появится здесь.",
            12f,
            host.getColor(R.color.text_dim)
        ).apply {
            gravity = Gravity.CENTER
            background = host.getDrawable(R.drawable.bg_card)
            setPadding(dp(24), 0, dp(24), 0)
            visibility = View.GONE
        }
        val favoritesContainer = FrameLayout(host)
        favoritesContainer.addView(
            favoritesList,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        favoritesContainer.addView(
            favoritesEmpty,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            favoritesContainer,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(124))
        )

        val appsHeader = sectionHeader(
            "Приложения",
            makeButton("Все приложения") { host.showApps() }
        )
        root.addView(
            appsHeader,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34))
        )

        appsList.apply {
            layoutManager = LinearLayoutManager(host, RecyclerView.HORIZONTAL, false)
            adapter = appsAdapter
            setupDefaults()
            addItemDecoration(HorizontalSpaceDecoration(dp(8)))
        }
        root.addView(
            appsList,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(104))
        )

        updateClock()
    }

    override fun onAppsLoaded(apps: List<AppEntry>) {
        allApps = apps
        refreshFavorites()
        updateAppsRow()
    }

    override fun onFavoritesChanged() {
        refreshFavorites()
        updateAppsRow()
    }

    override fun requestInitialFocus() {
        when {
            favoritesAdapter.itemCount > 0 -> favoritesList.focusFirstChild()
            appsAdapter.itemCount > 0 -> appsList.focusFirstChild()
            else -> host.focusNavigation()
        }
    }

    override fun onShown() {
        updateClock()
        updateWeather(prefs.readWeather())
        WeatherRepository.load(prefs) { weather -> updateWeather(weather) }
        ticker.removeCallbacks(tick)
        ticker.post(tick)
    }

    override fun onHidden() {
        ticker.removeCallbacks(tick)
    }

    override fun hint(): String =
        "OK — открыть    OK (удерживать) — в избранное    MENU — настройки"

    private fun refreshFavorites() {
        val favorites = prefs.favorites.mapNotNull { pkg ->
            allApps.firstOrNull { it.packageName == pkg }
        }
        favoritesAdapter.submit(favorites)
        val empty = favorites.isEmpty()
        favoritesList.visibility = if (empty) View.GONE else View.VISIBLE
        favoritesEmpty.visibility = if (empty) View.VISIBLE else View.GONE
    }

    private fun updateAppsRow() {
        val visible = allApps.filter { prefs.showSystemApps || !it.system }
        val nonFavorites = visible.filterNot { prefs.isFavorite(it.packageName) }
        val favorites = visible.filter { prefs.isFavorite(it.packageName) }
        appsAdapter.submit((nonFavorites + favorites).distinctBy { it.key }.take(10))
    }

    private fun updateClock() {
        val now = Date()
        clock.text = SimpleDateFormat("HH:mm", Locale("ru")).format(now)
        date.text = SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(now).capitalizeRu()
        greeting.text = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "ДОБРОЕ УТРО"
            in 12..16 -> "ДОБРЫЙ ДЕНЬ"
            in 17..22 -> "ДОБРЫЙ ВЕЧЕР"
            else -> "ДОБРОЙ НОЧИ"
        }
    }

    private fun updateWeather(weather: Weather?) {
        if (!prefs.weatherEnabled) {
            weatherBlock.visibility = View.GONE
            return
        }
        weatherBlock.visibility = View.VISIBLE
        weatherCity.text = prefs.cityName
        if (weather == null) {
            weatherIcon.text = "·"
            weatherTemp.text = "--°"
            weatherLabel.text = "нет данных"
            return
        }
        weatherIcon.text = weather.icon()
        val sign = if (weather.tempC > 0) "+" else ""
        weatherTemp.text = "$sign${weather.tempC}°"
        weatherCity.text = weather.city
        weatherLabel.text = weather.label()
    }
}
