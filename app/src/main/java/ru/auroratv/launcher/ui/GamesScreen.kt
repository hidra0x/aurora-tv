package ru.auroratv.launcher.ui

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import ru.auroratv.launcher.MainActivity
import ru.auroratv.launcher.R
import ru.auroratv.launcher.core.AppEntry
import ru.auroratv.launcher.core.Prefs
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Игровой хаб Aurora: статус Sunshine-хоста, запуск клиента и
 * подготовка к встроенному стримингу.
 */
class GamesScreen(host: MainActivity, prefs: Prefs) : LauncherScreen(host, prefs) {

    private val hostIp = "192.168.0.11"
    private val hostMac = "28:D0:43:89:2D:F1"
    private val hostName = "DESKTOP-K7OC3PA"

    private lateinit var statusView: TextView
    private lateinit var statusDot: View
    private lateinit var moonlightTile: View
    private var checking = false

    init {
        val root = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(40), dp(4), dp(40), dp(8))
        }
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val header = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            makeText("Игры", 26f, color(R.color.text), light = true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(makeText("CLOUD GAMING", 11f, color(R.color.accent), medium = true))
        root.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))

        root.addView(
            makeText(
                "Стриминг с ПК • Sunshine + Moonlight",
                12f,
                color(R.color.text_dim)
            )
        )

        val status = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.argb(225, 10, 24, 42))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.argb(90, 120, 160, 220))
            }
        }
        val info = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        info.addView(makeText("Игровой ПК", 20f, Color.WHITE, medium = true))
        info.addView(
            makeText("$hostName • $hostIp:47989", 11f, Color.rgb(160, 185, 215))
        )
        status.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val indicator = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusDot = View(host).apply {
            background = dot(Color.rgb(255, 209, 102))
        }
        indicator.addView(statusDot, LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(8) })
        statusView = makeText("Проверяю…", 12f, Color.rgb(190, 210, 235), medium = true)
        indicator.addView(statusView)
        status.addView(indicator)

        val actions = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(
            makeButton("Проверить") { checkHost() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)).apply { marginEnd = dp(8) }
        )
        actions.addView(
            makeButton("Разбудить ПК") { wakePc() }
        )
        status.addView(
            actions,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        root.addView(
            status,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(92)).apply {
                topMargin = dp(14)
                bottomMargin = dp(14)
            }
        )

        val grid = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        root.addView(
            grid,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        fun row(): LinearLayout = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        fun addTile(parent: LinearLayout, tile: View) {
            parent.addView(
                tile,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(dp(5), dp(5), dp(5), dp(5))
                }
            )
        }

        val first = row()
        moonlightTile = tile(
            "Moonlight",
            "Тестовый клиент: открыть стрим",
            R.color.accent
        ) { launchPackage("com.limelight", "Moonlight") }
        addTile(first, moonlightTile)
        addTile(
            first,
            tile("Sunshine", "Веб-панель и паринг", R.color.accent) { showSunshine() }
        )
        addTile(
            first,
            tile("Библиотека", "Скоро: игры и обложки", R.color.text_dim) {
                host.toast("Библиотека игр появится после встраивания клиента")
            }
        )
        grid.addView(first, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val second = row()
        addTile(
            second,
            tile("Свой клиент", "Встраивание Moonlight core", R.color.text_dim) { showOwnClient() }
        )
        addTile(
            second,
            tile("Steam Link", "Если установлен на ТВ", R.color.text_dim) {
                launchPackage("com.valvesoftware.steamlink", "Steam Link")
            }
        )
        addTile(
            second,
            tile("Wake-on-LAN", "Включить ПК с пульта", R.color.text_dim) { wakePc() }
        )
        grid.addView(second, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(
            makeText(
                "Игровой хост: Sunshine • клиент: Moonlight • в планах — встроенный клиент Aurora",
                11f,
                color(R.color.text_faint)
            ),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(26))
        )
    }

    private fun tile(title: String, subtitle: String, accentRes: Int, onClick: () -> Unit): LinearLayout =
        LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.argb(220, 10, 24, 42))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.argb(85, 120, 160, 220))
            }
            addView(makeText(title, 18f, Color.WHITE, medium = true))
            addView(makeText(subtitle, 11f, Color.rgb(150, 175, 205)))
            setOnClickListener { onClick() }
        }

    private fun dot(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun checkHost() {
        if (checking) return
        checking = true
        statusView.text = "Проверяю…"
        statusDot.background = dot(Color.rgb(255, 209, 102))
        Thread {
            val online = try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(hostIp, 47989), 1600)
                }
                true
            } catch (_: Throwable) {
                false
            }
            host.runOnUiThread {
                checking = false
                statusView.text = if (online) "Онлайн • 47989" else "Офлайн"
                statusDot.background = dot(
                    if (online) Color.rgb(126, 226, 168) else Color.rgb(255, 107, 107)
                )
            }
        }.start()
    }

    private fun launchPackage(packageName: String, label: String) {
        val intent = host.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            host.toast("$label не установлен")
            return
        }
        try {
            host.startActivity(intent)
        } catch (_: Throwable) {
            host.toast("Не удалось открыть $label")
        }
    }

    private fun showSunshine() {
        AlertDialog.Builder(host, R.style.Theme_Aurora_Dialog)
            .setTitle("Sunshine")
            .setMessage(
                "Веб-панель хоста:\n\n" +
                    "https://$hostIp:47990\n\n" +
                    "Логин: aurora. Пароль задан при настройке Sunshine.\n\n" +
                    "Панель открывается с ПК: на телевизоре нет браузера. " +
                    "Там же — PIN-паринг и список игр."
            )
            .setPositiveButton("Понятно", null)
            .show()
    }

    private fun showOwnClient() {
        AlertDialog.Builder(host, R.style.Theme_Aurora_Dialog)
            .setTitle("Свой клиент стриминга")
            .setMessage(
                "Сейчас используется Moonlight как эталон: он уже стримит рабочий стол " +
                    "с RTX 4050 на ТВ.\n\n" +
                    "Следующий этап — встроить Moonlight core в Aurora: вкладка «Игры», " +
                    "паринг, видео, звук и геймпад без отдельного приложения."
            )
            .setPositiveButton("Понятно", null)
            .show()
    }

    private fun wakePc() {
        Thread {
            try {
                val mac = hostMac.split(":", "-").joinToString("") { it.padStart(2, '0') }
                val macBytes = mac.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val packet = ByteArray(6 + 16 * 6) { 0xFF.toByte() }
                for (round in 0 until 16) {
                    macBytes.copyInto(packet, 6 + round * 6)
                }
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    socket.send(
                        DatagramPacket(
                            packet,
                            packet.size,
                            InetAddress.getByName("255.255.255.255"),
                            9
                        )
                    )
                }
                host.runOnUiThread { host.toast("Wake-on-LAN отправлен") }
            } catch (_: Throwable) {
                host.runOnUiThread { host.toast("Не удалось отправить Wake-on-LAN") }
            }
        }.start()
    }

    override fun onAppsLoaded(apps: List<AppEntry>) {}

    override fun requestInitialFocus() {
        moonlightTile.post { moonlightTile.requestFocus() }
    }

    override fun onShown() {
        checkHost()
    }

    override fun hint(): String =
        "OK — открыть    ↑ ↓ ← → — выбор    MENU — настройки"
}
