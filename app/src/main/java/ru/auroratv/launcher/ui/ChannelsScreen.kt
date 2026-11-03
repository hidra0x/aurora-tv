package ru.auroratv.launcher.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.auroratv.launcher.MainActivity
import ru.auroratv.launcher.R
import ru.auroratv.launcher.core.AppEntry
import ru.auroratv.launcher.core.Channel
import ru.auroratv.launcher.core.ChannelControl
import ru.auroratv.launcher.core.ChannelsRepository
import ru.auroratv.launcher.core.Prefs
import ru.auroratv.launcher.media.PlayerActivity

class ChannelsScreen(host: MainActivity, prefs: Prefs) : LauncherScreen(host, prefs) {

    private val adapter = ChannelAdapter(host, prefs) { channel, number -> play(channel, number) }
    private val list = RecyclerView(host)
    private lateinit var countLabel: TextView
    private lateinit var emptyLabel: TextView
    private lateinit var tunerButton: TextView
    private var loading = false

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
            makeText("Каналы", 26f, color(R.color.text), light = true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        countLabel = makeText("", 11f, color(R.color.text_faint))
        header.addView(countLabel)
        root.addView(
            header,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
        )

        val actions = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        tunerButton = action("Эфирное ТВ") { host.openTv() }
        actions.addView(tunerButton)
        actions.addView(action("Инфоканал 0") { host.showInfoChannel() })
        actions.addView(action("Кинозалы") { host.showCinema() })
        actions.addView(action("Плейлист") { showPlaylistDialog() })
        actions.addView(action("Обновить") { refreshChannels() })
        root.addView(
            actions,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        list.apply {
            layoutManager = GridLayoutManager(host, 3)
            adapter = this@ChannelsScreen.adapter
            setupDefaults()
            isFocusable = false
            setPadding(0, dp(4), 0, dp(4))
            addItemDecoration(GridSpaceDecoration(3, dp(8)))
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
            "Каналы не загрузились. Нажмите «Плейлист», чтобы добавить M3U-ссылку.",
            12f,
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

        refreshChannels()
    }

    override fun onAppsLoaded(apps: List<AppEntry>) {
        // Каналы не зависят от списка приложений.
    }

    override fun requestInitialFocus() {
        focusFirstTile()
    }

    override fun onShown() {
        if (adapter.itemCount == 0) refreshChannels()
        focusFirstTile()
    }

    private fun focusFirstTile() {
        list.postDelayed({
            if (adapter.itemCount == 0) {
                tunerButton.requestFocus()
                return@postDelayed
            }
            val holder = list.findViewHolderForAdapterPosition(0)
            if (holder != null) {
                holder.itemView.requestFocus()
            } else {
                list.scrollToPosition(0)
                list.postDelayed({
                    list.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                        ?: tunerButton.requestFocus()
                }, 180L)
            }
        }, 220L)
    }

    override fun hint(): String =
        "OK — смотреть    0 — инфоканал    MENU — настройки"

    private fun action(label: String, onClick: () -> Unit): TextView =
        makeButton(label, onClick).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(32)
            ).apply { marginEnd = dp(8) }
        }

    private fun refreshChannels() {
        val bundled = ChannelsRepository.bundled(host)
        submit(bundled)
        val playlist = prefs.channelsUrl
        if (playlist.isBlank()) return
        if (loading) return
        loading = true
        Thread {
            val remote = ChannelsRepository.fromUrl(playlist)
            host.runOnUiThread {
                loading = false
                if (remote != null) {
                    submit(remote)
                } else {
                    host.toast("Плейлист не загрузился, оставлены официальные каналы")
                }
            }
        }.apply {
            isDaemon = true
            name = "aurora-channels"
        }.start()
    }

    private fun submit(channels: List<Channel>) {
        adapter.submit(channels)
        countLabel.text = "Каналов: ${channels.size}"
        emptyLabel.visibility = if (channels.isEmpty()) View.VISIBLE else View.GONE
        list.visibility = if (channels.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun play(channel: Channel, number: Int) {
        val state = ChannelControl.stateFor(host, number)
        if (!state.isLive) {
            host.showChannelMaintenance(number, state)
            return
        }
        val intent = Intent(host, PlayerActivity::class.java).apply {
            putStringArrayListExtra(PlayerActivity.EXTRA_URIS, arrayListOf(channel.url))
            putStringArrayListExtra(PlayerActivity.EXTRA_TITLES, arrayListOf(channel.name))
            putExtra(PlayerActivity.EXTRA_START, 0)
        }
        try {
            host.startActivity(intent)
        } catch (_: Throwable) {
            host.toast("Не удалось открыть канал")
        }
    }

    private fun showPlaylistDialog() {
        val input = EditText(host).apply {
            hint = "https://example.com/channels.m3u"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
            setText(prefs.channelsUrl)
            setTextColor(color(R.color.text))
            setHintTextColor(color(R.color.text_faint))
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
        AlertDialog.Builder(host, R.style.Theme_Aurora_Dialog)
            .setTitle("M3U-плейлист")
            .setMessage("Ссылка на легальный плейлист провайдера. Официальные каналы останутся, если плейлист не загрузится.")
            .setView(container)
            .setPositiveButton("Сохранить") { _, _ ->
                prefs.channelsUrl = input.text.toString()
                refreshChannels()
            }
            .setNeutralButton("Сбросить") { _, _ ->
                prefs.channelsUrl = ""
                refreshChannels()
                host.toast("Плейлист сброшен")
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private class ChannelAdapter(
        private val host: MainActivity,
        private val prefs: Prefs,
        private val onClick: (Channel, Int) -> Unit
    ) : RecyclerView.Adapter<ChannelAdapter.Holder>() {

        private var items: List<Channel> = emptyList()

        fun submit(newItems: List<Channel>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_channel, parent, false)
            view.applyFocusScale({ prefs.animations }, 1.03f)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val channel = items[position]
            val number = position + 1
            val state = ChannelControl.stateFor(host, number)
            holder.title.text = channel.name
            holder.title.setTextColor(
                if (state.isLive) {
                    androidx.core.content.ContextCompat.getColor(host, R.color.text)
                } else if (state.state == ChannelControl.STATE_UPGRADING) {
                    Color.rgb(255, 209, 102)
                } else {
                    Color.rgb(255, 107, 107)
                }
            )
            holder.group.text = buildString {
                append("$number · ${channel.group.ifBlank { "Официальный поток" }}")
                if (!state.isLive) {
                    append("  ·  ")
                    append(
                        if (state.state == ChannelControl.STATE_UPGRADING) {
                            "ОБНОВЛЕНИЕ"
                        } else {
                            "ПРОФИЛАКТИКА"
                        }
                    )
                }
            }
            holder.itemView.contentDescription = channel.name
            holder.itemView.setOnClickListener { onClick(channel, number) }
        }

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.title)
            val group: TextView = view.findViewById(R.id.group)
        }
    }
}
