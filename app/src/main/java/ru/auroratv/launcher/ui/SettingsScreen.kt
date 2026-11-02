package ru.auroratv.launcher.ui

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.provider.Settings as AndroidSettings
import android.text.format.Formatter
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.auroratv.launcher.MainActivity
import ru.auroratv.launcher.R
import ru.auroratv.launcher.core.AppEntry
import ru.auroratv.launcher.core.Prefs
import ru.auroratv.launcher.weather.Cities
import ru.auroratv.launcher.weather.City
import ru.auroratv.launcher.weather.WeatherRepository
import java.util.Locale

class SettingsScreen(host: MainActivity, prefs: Prefs) : LauncherScreen(host, prefs) {

    private enum class Kind { HEADER, ACTION, TOGGLE, VALUE, INFO }

    private data class Row(
        val id: String,
        val kind: Kind,
        val title: String = "",
        val subtitle: String = "",
        val value: String = "",
        val info: String = "",
        val accent: Boolean = false
    )

    private val adapter = SettingsAdapter(host) { row -> onRow(row) }
    private val list = RecyclerView(host)
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
            makeText("Настройки", 26f, host.getColor(R.color.text), light = true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(makeText("Aurora TV", 11f, host.getColor(R.color.text_faint)))
        root.addView(
            header,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
        )

        list.apply {
            layoutManager = LinearLayoutManager(host)
            adapter = this@SettingsScreen.adapter
            setupDefaults()
            setPadding(0, dp(4), 0, dp(8))
            addItemDecoration(VerticalSpaceDecoration(dp(3)))
        }
        root.addView(
            list,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        refreshRows()
    }

    override fun onAppsLoaded(apps: List<AppEntry>) {
        this.apps = apps
        refreshRows()
    }

    override fun onFavoritesChanged() {
        refreshRows()
    }

    override fun requestInitialFocus() {
        if (adapter.itemCount > 0) list.focusFirstChild() else host.focusNavigation()
    }

    override fun onShown() {
        refreshRows()
    }

    override fun hint(): String =
        "OK — изменить    ↑ ↓ — выбор    BACK — на главный"

    private fun refreshRows() {
        val focusedId = focusedRowId()
        val version = try {
            host.packageManager.getPackageInfo(host.packageName, 0).versionName ?: "1.0"
        } catch (_: Throwable) {
            "1.0"
        }
        val tvValue = when {
            prefs.tvPackage.isBlank() -> "Авто"
            else -> apps.firstOrNull { it.packageName == prefs.tvPackage }?.label ?: "Не найдено"
        }

        val rows = listOf(
            Row("h_screen", Kind.HEADER, title = "Главный экран"),
            Row(
                "hints",
                Kind.TOGGLE,
                title = "Подсказки внизу",
                subtitle = "Строка с назначением кнопок",
                value = if (prefs.hints) "Вкл" else "Выкл",
                accent = prefs.hints
            ),
            Row(
                "animations",
                Kind.TOGGLE,
                title = "Анимации фокуса",
                subtitle = "Лёгкое увеличение выбранной плитки",
                value = if (prefs.animations) "Вкл" else "Выкл",
                accent = prefs.animations
            ),
            Row(
                "system_apps",
                Kind.TOGGLE,
                title = "Системные приложения",
                subtitle = "Показывать служебные приложения в списке",
                value = if (prefs.showSystemApps) "Вкл" else "Выкл",
                accent = prefs.showSystemApps
            ),
            Row(
                "reset_favorites",
                Kind.ACTION,
                title = "Сбросить избранное",
                subtitle = "Убрать все закреплённые приложения"
            ),

            Row("h_weather", Kind.HEADER, title = "Погода"),
            Row(
                "weather",
                Kind.TOGGLE,
                title = "Погода на главном экране",
                subtitle = "MET Norway / Open-Meteo, без аккаунта и идентификаторов",
                value = if (prefs.weatherEnabled) "Вкл" else "Выкл",
                accent = prefs.weatherEnabled
            ),
            Row(
                "city",
                Kind.VALUE,
                title = "Город",
                subtitle = "Используется только для прогноза",
                value = prefs.cityName
            ),
            Row(
                "weather_info",
                Kind.INFO,
                info = "Запрос погоды содержит только координаты города и уходит на api.met.no " +
                    "(MET Norway) или open-meteo.com, без идентификатора устройства. " +
                    "Погоду можно выключить — тогда сеть не используется."
            ),

            Row("h_tv", Kind.HEADER, title = "Телевизор"),
            Row(
                "tv",
                Kind.VALUE,
                title = "Кнопка ТВ",
                subtitle = "Какое приложение открывать для каналов и тюнера",
                value = tvValue
            ),

            Row("h_system", Kind.HEADER, title = "Система"),
            Row("wifi", Kind.ACTION, title = "Wi-Fi", subtitle = "Статус, включение и переподключение"),
            Row("system_settings", Kind.ACTION, title = "Все настройки ТВ", subtitle = "Язык, установка приложений, восстановление"),
            Row("bluetooth", Kind.ACTION, title = "Bluetooth", subtitle = "Пульты, колонки, наушники"),
            Row("datetime", Kind.ACTION, title = "Дата и время", subtitle = "Часовой пояс и формат"),
            Row("uninstall_app", Kind.ACTION, title = "Удалить приложение…", subtitle = "Выбрать из установленных вручную"),

            Row("h_shell", Kind.HEADER, title = "Оболочка"),
            Row(
                "default_home",
                Kind.ACTION,
                title = "Сделать домашним экраном",
                subtitle = "Выбор лончера или возврат штатного",
                value = "→"
            ),
            Row("reload", Kind.ACTION, title = "Обновить список приложений", subtitle = "Если вы установили новое приложение"),
            Row("restart", Kind.ACTION, title = "Перезапустить оболочку", subtitle = "Без перезагрузки телевизора"),
            Row("uninstall_self", Kind.ACTION, title = "Удалить Aurora TV", subtitle = "Вернуть штатный домашний экран"),

            Row("h_privacy", Kind.HEADER, title = "Приватность"),
            Row(
                "privacy_info",
                Kind.INFO,
                info = "Никакой аналитики, рекламы и трекеров. Оболочка не отправляет идентификаторы " +
                    "и не читает аккаунты. На Android 7–9 медиатека использует системное разрешение " +
                    "на чтение видео и музыки; на новых версиях доступ к файлам выдаётся только к выбранным папкам."
            ),
            Row("about", Kind.ACTION, title = "О приложении", value = version)
        )
        adapter.submit(rows)
        if (focusedId != null) {
            val index = rows.indexOfFirst { it.id == focusedId }
            if (index >= 0) list.focusChildAt(index)
        }
    }

    private fun focusedRowId(): String? {
        val focused = list.findFocus() ?: return null
        val child = list.findContainingItemView(focused) ?: return null
        val position = list.getChildAdapterPosition(child)
        if (position == RecyclerView.NO_POSITION) return null
        return adapter.idAt(position)
    }

    private fun onRow(row: Row) {
        when (row.id) {
            "hints" -> {
                prefs.hints = !prefs.hints
                host.applyHint()
                refreshRows()
            }
            "animations" -> {
                prefs.animations = !prefs.animations
                refreshRows()
            }
            "system_apps" -> {
                prefs.showSystemApps = !prefs.showSystemApps
                host.refreshAppFilters()
                refreshRows()
            }
            "reset_favorites" -> confirmResetFavorites()
            "weather" -> {
                prefs.weatherEnabled = !prefs.weatherEnabled
                if (!prefs.weatherEnabled) prefs.clearWeather()
                refreshRows()
            }
            "city" -> chooseCity()
            "tv" -> host.chooseTvApp()
            "wifi" -> showWifiDialog()
            "system_settings" -> openVendorSettings(AndroidSettings.ACTION_SETTINGS, VENDOR_MAIN)
            "bluetooth" -> openVendorSettings(activity = VENDOR_BLUETOOTH)
            "datetime" -> openVendorSettings(AndroidSettings.ACTION_DATE_SETTINGS, VENDOR_MAIN)
            "uninstall_app" -> showUninstallDialog()
            "default_home" -> showDefaultHomeDialog()
            "reload" -> {
                host.reloadApps()
                host.toast("Обновляю список приложений")
            }
            "restart" -> host.recreate()
            "uninstall_self" -> confirmUninstall(host.packageName, "Aurora TV")
            "about" -> showAbout()
        }
    }

    private fun confirmResetFavorites() {
        AlertDialog.Builder(host, R.style.Theme_Aurora_Dialog)
            .setTitle("Сбросить избранное?")
            .setMessage("Все закреплённые приложения будут убраны с главного экрана.")
            .setPositiveButton("Сбросить") { _, _ ->
                prefs.favorites = emptyList()
                prefs.didAutoPin = true
                host.notifyFavoritesChanged()
                refreshRows()
                host.toast("Избранное очищено")
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun chooseCity() {
        val names = Cities.all.map { it.name } + "Другой город…"
        AlertDialog.Builder(host, R.style.Theme_Aurora_Dialog)
            .setTitle("Город для погоды")
            .setItems(names.toTypedArray()) { _, which ->
                if (which == Cities.all.size) {
                    showCustomCityDialog()
                } else {
                    applyCity(Cities.all[which])
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showCustomCityDialog() {
        val input = EditText(host).apply {
            hint = "Название города"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
            setTextColor(host.getColor(R.color.text))
            setHintTextColor(host.getColor(R.color.text_faint))
        }
        val container = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        container.addView(
            input,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val dialog = AlertDialog.Builder(host, R.style.Theme_Aurora_Dialog)
            .setTitle("Другой город")
            .setView(container)
            .setPositiveButton("Найти", null)
            .setNegativeButton("Отмена", null)
            .create()

        dialog.setOnShowListener {
            input.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val query = input.text.toString().trim()
                if (query.isEmpty()) {
                    host.toast("Введите название города")
                    return@setOnClickListener
                }
                button.isEnabled = false
                button.text = "Ищу…"
                WeatherRepository.geocode(query) { city ->
                    host.runOnUiThread {
                        if (city == null) {
                            button.isEnabled = true
                            button.text = "Найти"
                            host.toast("Город не найден")
                        } else {
                            dialog.dismiss()
                            applyCity(city)
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun applyCity(city: City) {
        prefs.cityName = city.name
        prefs.cityLat = city.lat.toFloat()
        prefs.cityLon = city.lon.toFloat()
        prefs.clearWeather()
        refreshRows()
        host.toast("Город: ${city.name}")
    }

    private fun showWifiDialog() {
        // Показываем диалог после отпускания OK: иначе key-up может сразу
        // нажать «Открыть настройки» и увести ТВ в чужой экран.
        host.window.decorView.postDelayed({ showWifiDialogNow() }, 350L)
    }

    @Suppress("DEPRECATION")
    private fun showWifiDialogNow() {
        val wifi = host.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val info = try {
            wifi?.connectionInfo
        } catch (_: Throwable) {
            null
        }
        val ssid = info?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        val enabled = wifi?.isWifiEnabled == true
        val connected = enabled && ssid != null && (info?.networkId ?: -1) != -1
        val ip = info?.ipAddress?.takeIf { it != 0 }?.let { Formatter.formatIpAddress(it) }

        val manager = host.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val capabilities = manager?.let { it.getNetworkCapabilities(it.activeNetwork) }
        val ethernet = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        val online = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        val status = when {
            connected && ip != null -> "Wi-Fi: $ssid, IP $ip."
            connected -> "Wi-Fi: $ssid."
            enabled -> "Wi-Fi включён, но сеть не подключена."
            else -> "Wi-Fi выключен."
        }
        val extra = when {
            ethernet -> "Сейчас активен кабель. Кнопка ниже переключит ТВ на Wi-Fi — " +
                "только по вашему нажатию."
            online -> "Сеть работает."
            else -> "Если сохранённая сеть не подключается, откройте «Все настройки ТВ»."
        }

        val dialog = AlertDialog.Builder(host, R.style.Theme_Aurora_Dialog)
            .setTitle("Wi-Fi")
            .setMessage(
                "$status\n\n$extra\n\n" +
                    "Aurora не открывает экран «Режим сети» и не переключает сеть сама."
            )
            .setPositiveButton(if (enabled) "Переподключиться" else "Включить Wi-Fi") { _, _ ->
                reconnectWifi(wifi, enabled)
            }
            .setNeutralButton("Все настройки") { _, _ ->
                openVendorSettings(AndroidSettings.ACTION_SETTINGS, VENDOR_MAIN)
            }
            .setNegativeButton("Закрыть", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus()
        }
        dialog.show()
    }

    @Suppress("DEPRECATION")
    private fun reconnectWifi(wifi: WifiManager?, wasEnabled: Boolean) {
        if (wifi == null) {
            host.toast("Wi-Fi недоступен на этом телевизоре")
            return
        }
        if (!wasEnabled) {
            val turnedOn = try {
                wifi.isWifiEnabled || wifi.setWifiEnabled(true)
            } catch (_: Throwable) {
                false
            }
            if (!turnedOn) {
                host.toast("Не удалось включить Wi-Fi")
                return
            }
            host.toast("Включаю Wi-Fi…")
        }
        host.window.decorView.postDelayed({
            val reconnected = try {
                wifi.reconnect()
            } catch (_: Throwable) {
                false
            }
            host.toast(
                if (reconnected) "Переподключаю сохранённую сеть…"
                else "Не удалось переподключиться. Откройте «Все настройки ТВ»."
            )
        }, 2500L)
    }

    private fun openVendorSettings(action: String? = null, activity: String? = null) {
        val attempts = ArrayList<Intent>(2)
        if (activity != null) {
            val intent = if (action == null) Intent() else Intent(action)
            intent.component = ComponentName(VENDOR_PACKAGE, activity)
            attempts.add(intent)
        }
        if (action != null) attempts.add(Intent(action))

        // Небольшая пауза, чтобы отпускание OK не попало в только что открытый экран
        // и не переключило «Режим сети» на первом пункте.
        host.window.decorView.postDelayed({
            for (intent in attempts) {
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    host.startActivity(intent)
                    return@postDelayed
                } catch (_: Throwable) {
                    // Пробуем следующий вариант: на другой прошивке может не быть вендорского пакета.
                }
            }
            host.toast("На этом телевизоре нет такого раздела настроек")
        }, 400L)
    }

    private fun showUninstallDialog() {
        val candidates = apps
            .asSequence()
            .filter { !it.system }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale("ru")) }
            .toList()
        if (candidates.isEmpty()) {
            host.toast("Нет приложений, которые можно удалить")
            return
        }
        val labels = candidates.map { it.label }.toTypedArray()
        AlertDialog.Builder(host, R.style.Theme_Aurora_Dialog)
            .setTitle("Удалить приложение")
            .setItems(labels) { _, which ->
                val app = candidates[which]
                confirmUninstall(app.packageName, app.label)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun confirmUninstall(packageName: String, label: String) {
        val self = packageName == host.packageName
        val message = if (self) {
            "Aurora TV будет удалена, а домашним экраном снова станет штатный лаунчер. " +
                "Избранное, город и настройки Aurora тоже удалятся."
        } else {
            "Приложение «$label» будет удалено с телевизора вместе с его данными."
        }
        AlertDialog.Builder(host, R.style.Theme_Aurora_Dialog)
            .setTitle(if (self) "Удалить Aurora TV?" else "Удалить «$label»?")
            .setMessage(message)
            .setPositiveButton("Удалить") { _, _ ->
                try {
                    host.startActivity(
                        Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                    )
                } catch (_: Throwable) {
                    host.toast("Не удалось открыть системный установщик")
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showDefaultHomeDialog() {
        AlertDialog.Builder(host, R.style.Theme_Aurora_Dialog)
            .setTitle("Домашний экран")
            .setMessage(
                "Нажмите «Открыть выбор» и выберите Aurora TV или штатный Launcher.\n\n" +
                    "Если выбора нет, значит Aurora TV уже назначена домашней. " +
                    "Чтобы вернуть штатный экран, удалите Aurora TV — прогресс и настройки " +
                    "оболочки при этом удалятся, а приложения на телевизоре останутся.\n\n" +
                    "Сброс телевизора для этого не нужен."
            )
            .setPositiveButton("Открыть выбор") { _, _ ->
                try {
                    host.startActivity(
                        Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Throwable) {
                    host.toast("Не удалось открыть выбор лончера")
                }
            }
            .setNeutralButton("Удалить Aurora") { _, _ ->
                confirmUninstall(host.packageName, "Aurora TV")
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun showAbout() {
        val version = try {
            host.packageManager.getPackageInfo(host.packageName, 0).versionName ?: "1.0"
        } catch (_: Throwable) {
            "1.0"
        }
        AlertDialog.Builder(host, R.style.Theme_Aurora_Dialog)
            .setTitle("Aurora TV $version")
            .setMessage(
                "Лёгкая оболочка для Android TV.\n\n" +
                    "Без рекламы, аналитики и трекеров. Без Google Play Services. " +
                    "Погода — единственный сетевой запрос, и его можно отключить.\n\n" +
                    "Приложения и иконки читаются локально. Доступ к файлам — только к папкам, " +
                    "которые вы выбрали вручную.\n\n" +
                    "Данные погоды: MET Norway (api.met.no) и Open-Meteo.\n\n" +
                    "Сделано для Telefunken Android TV и совместимых устройств."
            )
            .setPositiveButton("Закрыть", null)
            .show()
    }

    private class SettingsAdapter(
        private val host: MainActivity,
        private val onRow: (Row) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var items: List<Row> = emptyList()

        fun submit(newItems: List<Row>) {
            items = newItems
            notifyDataSetChanged()
        }

        fun idAt(position: Int): String? = items.getOrNull(position)?.id

        override fun getItemCount(): Int = items.size

        override fun getItemViewType(position: Int): Int = items[position].kind.ordinal

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (Kind.values()[viewType]) {
                Kind.HEADER -> TextViewHolder(inflater.inflate(R.layout.item_setting_header, parent, false))
                Kind.INFO -> InfoHolder(inflater.inflate(R.layout.item_setting_info, parent, false))
                else -> RowHolder(inflater.inflate(R.layout.item_setting_row, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val row = items[position]
            when (holder) {
                is TextViewHolder -> {
                    holder.text.text = row.title.uppercase(Locale("ru"))
                }
                is InfoHolder -> {
                    holder.text.text = row.info
                }
                is RowHolder -> {
                    holder.title.text = row.title
                    holder.subtitle.text = row.subtitle
                    holder.subtitle.visibility = if (row.subtitle.isBlank()) View.GONE else View.VISIBLE
                    holder.value.text = row.value
                    holder.value.visibility = if (row.value.isBlank()) View.GONE else View.VISIBLE
                    holder.value.setTextColor(
                        host.getColor(if (row.accent) R.color.accent else R.color.text_dim)
                    )
                    holder.chevron.visibility = if (row.kind == Kind.TOGGLE) View.GONE else View.VISIBLE
                    holder.itemView.contentDescription = row.title
                    holder.itemView.setOnClickListener { onRow(row) }
                }
            }
        }

        class TextViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val text: TextView = view as TextView
        }

        class InfoHolder(view: View) : RecyclerView.ViewHolder(view) {
            val text: TextView = view as TextView
        }

        class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.title)
            val subtitle: TextView = view.findViewById(R.id.subtitle)
            val value: TextView = view.findViewById(R.id.value)
            val chevron: ImageView = view.findViewById(R.id.chevron)
        }
    }

    private companion object {
        const val VENDOR_PACKAGE = "com.cvte.tv.androidsetting"
        const val VENDOR_MAIN = "$VENDOR_PACKAGE.GlobalSettingActivity"
        const val VENDOR_BLUETOOTH = "$VENDOR_PACKAGE.bluetooth.activity.BluetoothActivity"
    }
}
