package ru.auroratv.launcher.ui

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.auroratv.launcher.MainActivity
import ru.auroratv.launcher.R
import ru.auroratv.launcher.core.AppEntry
import ru.auroratv.launcher.core.CinemaChannel
import ru.auroratv.launcher.core.CinemaRepository
import ru.auroratv.launcher.core.CinemaSchedule
import ru.auroratv.launcher.core.Prefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CinemaScreen(host: MainActivity, prefs: Prefs) : LauncherScreen(host, prefs) {

    private val adapter = CinemaAdapter(host) { channel -> host.showCinemaSchedule(channel.number) }
    private val list = RecyclerView(host)
    private lateinit var countLabel: TextView

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
            makeText("Кинозалы", 26f, color(R.color.text), light = true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        countLabel = makeText("20–32", 11f, color(R.color.text_faint))
        header.addView(countLabel)
        root.addView(
            header,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
        )

        root.addView(
            makeText(
                "Публичное достояние • каждый день новый сеанс",
                12f,
                color(R.color.text_dim)
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        list.apply {
            layoutManager = GridLayoutManager(host, 3)
            adapter = this@CinemaScreen.adapter
            setupDefaults()
            isFocusable = false
            setPadding(0, dp(8), 0, dp(8))
            addItemDecoration(GridSpaceDecoration(3, dp(10)))
        }
        root.addView(
            list,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
    }

    override fun onAppsLoaded(apps: List<AppEntry>) {}

    override fun requestInitialFocus() {
        list.postDelayed({
            val holder = list.findViewHolderForAdapterPosition(0)
            if (holder != null) holder.itemView.requestFocus() else host.focusNavigation()
        }, 220L)
    }

    override fun onShown() {
        adapter.submit(CinemaRepository.channels(host))
        list.postDelayed({
            val holder = list.findViewHolderForAdapterPosition(0)
            if (holder != null) holder.itemView.requestFocus()
        }, 220L)
    }

    override fun hint(): String =
        "OK — расписание сеансов    HOME — переключить ТВ/Aurora    MENU — настройки"

    private class CinemaAdapter(
        private val host: MainActivity,
        private val onClick: (CinemaChannel) -> Unit
    ) : RecyclerView.Adapter<CinemaAdapter.Holder>() {

        private var items: List<CinemaChannel> = emptyList()

        fun submit(newItems: List<CinemaChannel>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_cinema_channel, parent, false)
            view.applyFocusScale(true, 1.03f)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val channel = items[position]
            val schedule = try {
                CinemaSchedule.now(host, channel)
            } catch (_: Throwable) {
                null
            }
            val accent = try {
                Color.parseColor(channel.color)
            } catch (_: Throwable) {
                host.getColor(R.color.accent)
            }
            holder.number.text = channel.number.toString()
            holder.number.setTextColor(accent)
            holder.current.setTextColor(accent)
            holder.title.text = channel.name
            holder.description.text = channel.description
            val active = schedule?.active
            holder.current.text = when {
                active != null -> "Сейчас: ${active.film.title} (${active.film.year})"
                schedule != null -> "В ${time(schedule.next.startAt)}: " +
                    "${schedule.next.film.title} (${schedule.next.film.year})"
                else -> "Сеанс скоро"
            }
            holder.next.text = if (active != null) {
                "Далее в ${time(schedule.next.startAt)}: " +
                    "${schedule.next.film.title} (${schedule.next.film.year})"
            } else {
                ""
            }
            holder.itemView.contentDescription = "${channel.number}. ${channel.name}"
            holder.itemView.setOnClickListener { onClick(channel) }
        }

        private fun time(millis: Long): String =
            SimpleDateFormat("HH:mm", Locale("ru")).format(Date(millis))

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val number: TextView = view.findViewById(R.id.number)
            val title: TextView = view.findViewById(R.id.title)
            val description: TextView = view.findViewById(R.id.description)
            val current: TextView = view.findViewById(R.id.current)
            val next: TextView = view.findViewById(R.id.next)
        }
    }
}
