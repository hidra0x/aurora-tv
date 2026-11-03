package ru.auroratv.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import ru.auroratv.launcher.core.AppEntry
import ru.auroratv.launcher.core.AppRepository
import ru.auroratv.launcher.core.Channel
import ru.auroratv.launcher.core.ChannelControl
import ru.auroratv.launcher.core.ChannelsRepository
import ru.auroratv.launcher.core.CinemaChannel
import ru.auroratv.launcher.core.CinemaRepository
import ru.auroratv.launcher.core.InfoRepository
import ru.auroratv.launcher.core.ManagedChannel
import ru.auroratv.launcher.core.NetworkState
import ru.auroratv.launcher.core.Prefs
import ru.auroratv.launcher.media.CinemaPlayerActivity
import ru.auroratv.launcher.media.CinemaScheduleActivity
import ru.auroratv.launcher.media.PlayerActivity
import ru.auroratv.launcher.ui.AppsScreen
import ru.auroratv.launcher.ui.ChannelsScreen
import ru.auroratv.launcher.ui.CinemaScreen
import ru.auroratv.launcher.ui.GamesScreen
import ru.auroratv.launcher.ui.HomeScreen
import ru.auroratv.launcher.ui.LauncherScreen
import ru.auroratv.launcher.ui.MediaScreen
import ru.auroratv.launcher.ui.SettingsScreen
import ru.auroratv.launcher.ui.VideoScreen
import ru.auroratv.launcher.ui.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var content: FrameLayout
    private lateinit var nav: LinearLayout
    private lateinit var hint: TextView
    private lateinit var headerClock: TextView
    private lateinit var networkStatus: TextView
    private lateinit var tvButton: TextView
    private lateinit var channelOverlay: LinearLayout
    private lateinit var channelNumberView: TextView
    private lateinit var channelNameView: TextView

    private var channels: List<Channel> = emptyList()
    private val channelInput = StringBuilder()
    private var channelHideRunnable: Runnable? = null

    private val navItems = mutableListOf<TextView>()
    private val screens = arrayOfNulls<LauncherScreen>(8)
    private var current = 0
    private var apps: List<AppEntry> = emptyList()
    private var loading = false
    private var queuedReload = false
    private var lastLoad = 0L
    private var receiverRegistered = false
    private var initialFocusPending = true

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "aurora-apps").apply { isDaemon = true }
    }

    private val clockRunnable = object : Runnable {
        override fun run() {
            headerClock.text = SimpleDateFormat("HH:mm", Locale("ru")).format(Date())
            handler.postDelayed(this, 30_000L)
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REMOVED,
                Intent.ACTION_PACKAGE_CHANGED,
                Intent.ACTION_PACKAGE_REPLACED -> reloadApps()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_main)

        content = findViewById(R.id.content)
        nav = findViewById(R.id.nav)
        hint = findViewById(R.id.hint)
        headerClock = findViewById(R.id.headerClock)
        networkStatus = findViewById(R.id.networkStatus)
        tvButton = findViewById(R.id.tvButton)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        hideSystemUi()

        setupHeader()
        setupChannelOverlay()
        channels = ChannelsRepository.bundled(this)
        reloadRemoteChannels()
        buildScreens()
        showScreen(0, focus = false)
        content.post { navItems.firstOrNull()?.requestFocus() }
        reloadApps()
        updateNetwork()
        handler.post(clockRunnable)
    }

    private fun setupChannelOverlay() {
        val root = findViewById<ViewGroup>(android.R.id.content)
        channelOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(12), dp(24), dp(12))
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_dialog)
            elevation = dp(12).toFloat()
            isFocusable = false
            isClickable = false
            visibility = View.GONE
        }
        channelNumberView = TextView(this).apply {
            textSize = 52f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent))
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-light", 0)
        }
        channelNameView = TextView(this).apply {
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_dim))
            gravity = Gravity.CENTER
            maxLines = 1
        }
        channelOverlay.addView(channelNumberView)
        channelOverlay.addView(channelNameView)
        root.addView(
            channelOverlay,
            FrameLayout.LayoutParams(
                dp(260),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ).apply {
                setMargins(0, 0, dp(40), dp(44))
            }
        )
        channelOverlay.bringToFront()
    }

    private fun reloadRemoteChannels() {
        val url = prefs.channelsUrl
        if (url.isBlank()) return
        executor.execute {
            val remote = ChannelsRepository.fromUrl(url)
            if (remote != null) {
                runOnUiThread { channels = remote }
            }
        }
    }

    private fun digitForKey(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> 0
        KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> 1
        KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> 2
        KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> 3
        KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> 4
        KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> 5
        KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> 6
        KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> 7
        KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> 8
        KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> 9
        else -> null
    }

    private fun onChannelDigit(digit: Int) {
        if (channelInput.length >= 3) channelInput.clear()
        channelInput.append(digit)
        val number = channelInput.toString()
        val channel = channelFor(number)
        val cinema = cinemaChannelFor(number)
        channelNumberView.text = number
        channelNameView.text = when {
            number == "0" -> "Инфоканал"
            cinema != null -> cinema.name
            channel != null -> channel.name
            else -> "Нет канала"
        }
        channelOverlay.visibility = View.VISIBLE
        channelOverlay.bringToFront()
        scheduleChannelConfirm()
    }

    private fun scheduleChannelConfirm() {
        channelHideRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable { confirmChannelInput() }
        channelHideRunnable = runnable
        handler.postDelayed(runnable, 3000L)
    }

    private fun confirmChannelInput() {
        val number = channelInput.toString()
        if (number.isBlank()) {
            hideChannelInput()
            return
        }
        val cinema = cinemaChannelFor(number)
        val channel = channelFor(number)
        hideChannelInput()
        if (number == "0") {
            showInfoChannel()
        } else if (cinema != null) {
            showCinemaChannel(cinema.number)
        } else if (channel == null) {
            toast("Канал $number не найден")
        } else {
            playChannel(channel)
        }
    }

    private fun channelFor(number: String): Channel? {
        val index = number.toIntOrNull() ?: return null
        return channels.getOrNull(index - 1)
    }

    private fun cinemaChannelFor(number: String): CinemaChannel? =
        number.toIntOrNull()?.let { CinemaRepository.channelFor(this, it) }

    private fun hideChannelInput() {
        channelHideRunnable?.let { handler.removeCallbacks(it) }
        channelHideRunnable = null
        channelInput.clear()
        channelOverlay.visibility = View.GONE
    }

    fun showChannelMaintenance(number: Int, state: ManagedChannel) {
        val title = when {
            number == 0 -> "Инфоканал обновляется"
            state.state == ChannelControl.STATE_UPGRADING -> "Обновление канала $number"
            else -> "Профилактика канала $number"
        }
        val message = buildString {
            if (state.reason.isNotBlank()) append(state.reason).append("\n\n")
            if (state.version.isNotBlank()) append("Версия: ").append(state.version).append("\n")
            if (state.notes.isNotBlank()) append(state.notes).append("\n\n")
            if (state.until > 0L) {
                append("Канал вернется в эфир в ")
                append(SimpleDateFormat("HH:mm", Locale("ru")).format(Date(state.until)))
            } else {
                append("Идут технические работы. Канал скоро вернется в эфир.")
            }
        }
        AlertDialog.Builder(this, R.style.Theme_Aurora_Dialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Понятно", null)
            .show()
    }

    private fun playChannel(channel: Channel) {
        val number = channels.indexOf(channel) + 1
        if (number > 0) {
            val state = ChannelControl.stateFor(this, number)
            if (!state.isLive) {
                showChannelMaintenance(number, state)
                return
            }
        }
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putStringArrayListExtra(PlayerActivity.EXTRA_URIS, arrayListOf(channel.url))
            putStringArrayListExtra(PlayerActivity.EXTRA_TITLES, arrayListOf(channel.name))
            putExtra(PlayerActivity.EXTRA_START, 0)
        }
        try {
            startActivity(intent)
        } catch (_: Throwable) {
            toast("Не удалось открыть канал")
        }
    }

    fun showCinemaChannel(number: Int) {
        val intent = Intent(this, CinemaPlayerActivity::class.java).apply {
            putExtra(CinemaPlayerActivity.EXTRA_CHANNEL_NUMBER, number)
        }
        try {
            startActivity(intent)
        } catch (_: Throwable) {
            toast("Не удалось открыть кинозал")
        }
    }

    fun showCinemaSchedule(number: Int) {
        val intent = Intent(this, CinemaScheduleActivity::class.java).apply {
            putExtra(CinemaScheduleActivity.EXTRA_CHANNEL_NUMBER, number)
        }
        try {
            startActivity(intent)
        } catch (_: Throwable) {
            toast("Не удалось открыть расписание")
        }
    }

    fun showInfoChannel() {
        val state = ChannelControl.stateFor(this, 0)
        if (!state.isLive) {
            showChannelMaintenance(0, state)
            return
        }
        val episodes = InfoRepository.episodes(this).shuffled()
        if (episodes.isEmpty()) {
            toast("Инфоканал пуст")
            return
        }
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putStringArrayListExtra(
                PlayerActivity.EXTRA_URIS,
                ArrayList(episodes.map { it.uri })
            )
            putStringArrayListExtra(
                PlayerActivity.EXTRA_TITLES,
                ArrayList(episodes.map { it.title })
            )
            putExtra(PlayerActivity.EXTRA_START, 0)
            putExtra(PlayerActivity.EXTRA_INFO_CHANNEL, true)
        }
        try {
            startActivity(intent)
        } catch (_: Throwable) {
            toast("Не удалось открыть инфоканал")
        }
    }

    private fun setupHeader() {
        tvButton.setOnClickListener { openTv() }

        val labels = listOf(
            "Главная", "Приложения", "Медиа", "Видео",
            "Каналы", "Кино", "Игры", "Настройки"
        )
        labels.forEachIndexed { index, label ->
            val item = TextView(this).apply {
                text = label
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(16), 0, dp(16), 0)
                background = getDrawable(R.drawable.bg_nav_item)
                isFocusable = true
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(36)
                ).apply { marginEnd = dp(4) }
                setOnClickListener { showScreen(index) }
                setOnFocusChangeListener { _, focused -> updateNavItem(index, focused) }
            }
            nav.addView(item)
            navItems.add(item)
        }
        updateNav()
    }

    private fun buildScreens() {
        screens[0] = HomeScreen(this, prefs)
        screens[1] = AppsScreen(this, prefs)
        screens[2] = MediaScreen(this, prefs)
        screens[3] = VideoScreen(this, prefs)
        screens[4] = ChannelsScreen(this, prefs)
        screens[5] = CinemaScreen(this, prefs)
        screens[6] = GamesScreen(this, prefs)
        screens[7] = SettingsScreen(this, prefs)
        screens.forEach { screen ->
            screen?.visibility = View.GONE
            content.addView(screen)
        }
    }

    fun showScreen(index: Int) {
        showScreen(index, focus = true)
    }

    private fun showScreen(index: Int, focus: Boolean) {
        if (index !in screens.indices) return
        val target = screens[index] ?: return
        if (current != index) {
            screens[current]?.let {
                it.onHidden()
                it.visibility = View.GONE
            }
            current = index
        }
        target.visibility = View.VISIBLE
        updateNav()
        headerClock.visibility = if (index == 0) View.GONE else View.VISIBLE
        applyHint()
        target.onShown()
        if (focus) target.requestInitialFocus()
    }

    fun showHome() = showScreen(0)
    fun showApps() = showScreen(1)
    fun showMedia() = showScreen(2)
    fun showVideo() = showScreen(3)
    fun showChannels() = showScreen(4)
    fun showCinema() = showScreen(5)
    fun showGames() = showScreen(6)
    fun showSettings() = showScreen(7)

    fun focusNavigation() {
        navItems.firstOrNull()?.requestFocus()
    }

    private fun updateNav() {
        navItems.forEachIndexed { index, item ->
            updateNavItem(index, item.hasFocus())
        }
    }

    private fun updateNavItem(index: Int, focused: Boolean) {
        val color = when {
            focused -> Color.WHITE
            index == current -> ContextCompat.getColor(this, R.color.accent)
            else -> ContextCompat.getColor(this, R.color.text_dim)
        }
        navItems.getOrNull(index)?.setTextColor(color)
    }

    fun applyHint() {
        hint.visibility = if (prefs.hints) View.VISIBLE else View.GONE
        hint.text = screens[current]?.hint().orEmpty()
    }

    fun reloadApps() {
        if (loading) {
            queuedReload = true
            return
        }
        loading = true
        executor.execute {
            val loaded = try {
                AppRepository.load(applicationContext)
            } catch (_: Throwable) {
                emptyList()
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                loading = false
                apps = loaded
                lastLoad = System.currentTimeMillis()

                if (!prefs.didAutoPin) {
                    if (prefs.favorites.isEmpty()) {
                        AppRepository.detectTvApp(this, loaded)?.let {
                            prefs.favorites = listOf(it.packageName)
                        }
                    }
                    prefs.didAutoPin = true
                }

                screens.forEach { it?.onAppsLoaded(loaded) }
                if (initialFocusPending && current == 0) {
                    initialFocusPending = false
                    screens[0]?.requestInitialFocus()
                } else if (window.currentFocus == null) {
                    screens[current]?.requestInitialFocus()
                }
                if (queuedReload) {
                    queuedReload = false
                    reloadApps()
                }
            }
        }
    }

    fun launchApp(app: AppEntry) {
        if (!AppRepository.launch(this, app)) {
            toast("Не удалось открыть «${app.label}»")
        }
    }

    fun toggleFavorite(app: AppEntry) {
        val added = prefs.toggleFavorite(app.packageName)
        toast(if (added) "В избранном: ${app.label}" else "Убрано из избранного")
        notifyFavoritesChanged()
    }

    fun notifyFavoritesChanged() {
        screens.forEach { it?.onFavoritesChanged() }
    }

    fun refreshAppFilters() {
        screens.forEach { it?.onAppsLoaded(apps) }
    }

    fun openTv() {
        val selected = prefs.tvPackage.takeIf { it.isNotBlank() }
        val app = selected?.let { pkg -> apps.firstOrNull { it.packageName == pkg } }
            ?: AppRepository.detectTvApp(this, apps)
        if (app != null) {
            if (prefs.tvPackage.isBlank()) prefs.tvPackage = app.packageName
            launchApp(app)
        } else {
            chooseTvApp()
        }
    }

    fun chooseTvApp() {
        if (apps.isEmpty()) {
            toast("Список приложений ещё загружается")
            return
        }
        val options = ArrayList<String>(apps.size + 1)
        options.add("Авто (найти ТВ-приложение)")
        val candidates = if (prefs.showSystemApps) {
            apps
        } else {
            apps.filter { AppRepository.isTvCandidate(it) }
        }
        options.addAll(candidates.map { it.label })

        AlertDialog.Builder(this, R.style.Theme_Aurora_Dialog)
            .setTitle("Приложение для кнопки ТВ")
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) {
                    prefs.tvPackage = ""
                    val auto = AppRepository.detectTvApp(this, apps)
                    if (auto != null) {
                        prefs.tvPackage = auto.packageName
                        toast("Выбрано: ${auto.label}")
                    } else {
                        toast("ТВ-приложение не найдено")
                    }
                } else {
                    val app = candidates[which - 1]
                    prefs.tvPackage = app.packageName
                    toast("Выбрано: ${app.label}")
                }
                notifyFavoritesChanged()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun updateNetwork() {
        val online = NetworkState.isOnline(this)
        networkStatus.text = if (online) "● Онлайн" else "● Офлайн"
        networkStatus.setTextColor(
            ContextCompat.getColor(this, if (online) R.color.green else R.color.text_faint)
        )
    }

    override fun onStart() {
        super.onStart()
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            registerReceiver(packageReceiver, filter)
            receiverRegistered = true
        }
        updateNetwork()
        if (lastLoad > 0 && System.currentTimeMillis() - lastLoad > 60_000L) {
            reloadApps()
        }
    }

    override fun onStop() {
        hideChannelInput()
        if (receiverRegistered) {
            unregisterReceiver(packageReceiver)
            receiverRegistered = false
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        updateNetwork()
        if (window.currentFocus == null) {
            screens[current]?.requestInitialFocus()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.hasCategory(Intent.CATEGORY_HOME) == true ||
            intent?.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) == true
        ) {
            showHome()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val digit = digitForKey(keyCode)
        if (digit != null) {
            onChannelDigit(digit)
            return true
        }
        if (channelOverlay.visibility == View.VISIBLE) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    confirmChannelInput()
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    hideChannelInput()
                    return true
                }
            }
        }
        when (keyCode) {
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> {
                showSettings()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (screens[current]?.onBack() == true) return true
                if (current != 0) {
                    showHome()
                    return true
                }
                return true
            }
            KeyEvent.KEYCODE_HOME -> {
                showHome()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        screens.forEach { screen ->
            if (screen?.onActivityResult(requestCode, resultCode, data) == true) return
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        screens.forEach { screen ->
            if (screen?.onRequestPermissionsResult(requestCode, permissions, grantResults) == true) return
        }
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    override fun onDestroy() {
        handler.removeCallbacks(clockRunnable)
        channelHideRunnable?.let { handler.removeCallbacks(it) }
        executor.shutdownNow()
        super.onDestroy()
    }
}
