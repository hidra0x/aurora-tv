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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import ru.auroratv.launcher.core.AppEntry
import ru.auroratv.launcher.core.AppRepository
import ru.auroratv.launcher.core.NetworkState
import ru.auroratv.launcher.core.Prefs
import ru.auroratv.launcher.ui.AppsScreen
import ru.auroratv.launcher.ui.HomeScreen
import ru.auroratv.launcher.ui.LauncherScreen
import ru.auroratv.launcher.ui.MediaScreen
import ru.auroratv.launcher.ui.SettingsScreen
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

    private val navItems = mutableListOf<TextView>()
    private val screens = arrayOfNulls<LauncherScreen>(4)
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
        buildScreens()
        showScreen(0, focus = false)
        content.post { navItems.firstOrNull()?.requestFocus() }
        reloadApps()
        updateNetwork()
        handler.post(clockRunnable)
    }

    private fun setupHeader() {
        tvButton.setOnClickListener { openTv() }

        val labels = listOf("Главная", "Приложения", "Медиа", "Настройки")
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
        screens[3] = SettingsScreen(this, prefs)
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
    fun showSettings() = showScreen(3)

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
        executor.shutdownNow()
        super.onDestroy()
    }
}
