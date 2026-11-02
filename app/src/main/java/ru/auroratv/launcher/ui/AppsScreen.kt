package ru.auroratv.launcher.ui

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.auroratv.launcher.MainActivity
import ru.auroratv.launcher.R
import ru.auroratv.launcher.core.AppEntry
import ru.auroratv.launcher.core.Prefs

class AppsScreen(host: MainActivity, prefs: Prefs) : LauncherScreen(host, prefs) {

    private val adapter = AppTileAdapter(host, prefs, AppTileAdapter.Style.ICON, fillWidth = true)
    private val list = RecyclerView(host)
    private lateinit var countLabel: TextView
    private lateinit var emptyLabel: TextView

    // На узких/720p-телевизорах 5 колонок могут быть слишком мелкими.
    private val columns = if (host.resources.configuration.screenWidthDp >= 900) 5 else 4

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
            makeText("Приложения", 26f, color(R.color.text), light = true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        countLabel = makeText("", 11f, color(R.color.text_faint))
        header.addView(countLabel)
        root.addView(
            header,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
        )

        list.apply {
            layoutManager = GridLayoutManager(host, columns)
            adapter = this@AppsScreen.adapter
            setupDefaults()
            setPadding(0, dp(4), 0, dp(4))
            addItemDecoration(GridSpaceDecoration(columns, dp(10)))
        }
        root.addView(
            list,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        emptyLabel = makeText(
            "Приложения не найдены",
            13f,
            color(R.color.text_dim)
        ).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        root.addView(
            emptyLabel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    override fun onAppsLoaded(apps: List<AppEntry>) {
        val visible = if (prefs.showSystemApps) apps else apps.filterNot { it.system }
        adapter.submit(visible)
        countLabel.text = "Всего: ${visible.size}"
        emptyLabel.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onFavoritesChanged() {
        adapter.notifyDataSetChanged()
    }

    override fun requestInitialFocus() {
        if (adapter.itemCount > 0) list.focusFirstChild() else host.focusNavigation()
    }

    override fun hint(): String =
        "OK — открыть    OK (удерживать) — в избранное    MENU — настройки"
}
