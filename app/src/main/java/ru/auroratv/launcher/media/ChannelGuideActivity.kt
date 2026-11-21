package ru.auroratv.launcher.media

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.auroratv.launcher.R
import ru.auroratv.launcher.core.ChannelCatalog
import ru.auroratv.launcher.core.GuideChannel
import ru.auroratv.launcher.ui.setupDefaults
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Полноэкранный телегид: каналы, «сейчас» и «далее», фильтры и случайный канал. */
class ChannelGuideActivity : Activity() {

    private var all: List<GuideChannel> = emptyList()
    private var filtered: List<GuideChannel> = emptyList()
    private var filter = "all"
    private var currentNumber = -1
    private var fromPlayer = false
    private lateinit var adapter: GuideAdapter
    private lateinit var list: RecyclerView
    private lateinit var clock: TextView
    private val filterViews = mutableMapOf<String, TextView>()
    private val handler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            clock.text = SimpleDateFormat("HH:mm", Locale("ru")).format(Date())
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentNumber = intent.getIntExtra(EXTRA_CURRENT, -1)
        fromPlayer = intent.getBooleanExtra(EXTRA_FROM_PLAYER, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(5, 12, 22))
            setPadding(dp(34), dp(22), dp(34), dp(16))
        }
        setContentView(root)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            text("ТЕЛЕГИД", 27f, Color.WHITE, true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        clock = text("", 14f, Color.rgb(139, 213, 255), true).apply {
            typeface = Typeface.create("monospace", Typeface.BOLD)
        }
        header.addView(clock)
        root.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)))
        root.addView(text("Сейчас и далее • OK — смотреть • CH+/- — листать", 11f, Color.rgb(150, 175, 205), false))

        val filters = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(10))
        }
        val allButton = filterButton("Все", "all")
        val tvButton = filterButton("ТВ 1–14", "tv")
        val kidsButton = filterButton("Дети 15–18", "kids")
        val cinemaButton = filterButton("Кино 20–43", "cinema")
        val random = filterButton("Случайный канал", "random")
        filters.addView(allButton)
        filters.addView(tvButton)
        filters.addView(kidsButton)
        filters.addView(cinemaButton)
        filters.addView(random)
        root.addView(filters, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)))

        list = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@ChannelGuideActivity, if (resources.configuration.screenWidthDp >= 900) 4 else 3)
            adapter = GuideAdapter()
            clipToPadding = false
            setPadding(0, dp(2), 0, dp(2))
            addItemDecoration(GridSpaceDecoration(if (resources.configuration.screenWidthDp >= 900) 4 else 3, dp(10)))
            isFocusable = false
            setupDefaults()
        }
        adapter = list.adapter as GuideAdapter
        root.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(
            text("AURORA TV • ГИД ПО КАНАЛАМ", 10f, Color.rgb(110, 135, 170), true).apply {
                letterSpacing = 0.14f
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            }
        )

        all = ChannelCatalog.all(this)
        applyFilter()
        handler.post(clockTick)

        list.postDelayed({
            val index = filtered.indexOfFirst { it.number == currentNumber }
            if (index >= 0) {
                list.scrollToPosition(index)
                list.postDelayed({
                    list.findViewHolderForAdapterPosition(index)?.itemView?.requestFocus()
                }, 100)
            } else {
                list.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
            }
        }, 150)
    }

    private fun filterButton(label: String, key: String): TextView =
        text(label, 12f, Color.rgb(220, 230, 242), true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)).apply {
                marginEnd = dp(8)
            }
            background = pill(key == filter)
            id = View.generateViewId()
            isFocusable = true
            isClickable = true
            filterViews[key] = this
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (::list.isInitialized) {
                        list.post { list.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
                    }
                    true
                } else {
                    false
                }
            }
            setOnClickListener {
                if (key == "random") {
                    openRandom()
                } else {
                    filter = key
                    applyFilter()
                    refreshFilters()
                }
            }
            setOnFocusChangeListener { view, focused ->
                view.animate().scaleX(if (focused) 1.04f else 1f).scaleY(if (focused) 1.04f else 1f).setDuration(120).start()
            }
        }

    private fun refreshFilters() {
        filterViews.forEach { (key, view) ->
            view.background = pill(key == filter)
        }
    }

    private fun applyFilter() {
        filtered = when (filter) {
            "tv" -> all.filter { it.kind == "tv" }
            "kids" -> all.filter { it.kind == "kids" }
            "cinema" -> all.filter { it.kind == "cinema" }
            else -> all
        }
        adapter.submit(filtered)
        list.postDelayed({
            list.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
        }, 80)
    }

    private fun openRandom() {
        val live = all.filter { it.state == "live" && it.number != currentNumber }
        val target = if (live.isEmpty()) all.randomOrNull() else live.randomOrNull()
        target?.let { openChannel(it) }
    }

    private fun openChannel(channel: GuideChannel) {
        if (fromPlayer) {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_SELECTED, channel.number))
            finish()
            return
        }
        if (ChannelCatalog.open(this, channel)) finish()
    }

    private inner class GuideAdapter : RecyclerView.Adapter<GuideAdapter.Holder>() {

        private var items: List<GuideChannel> = emptyList()

        fun submit(newItems: List<GuideChannel>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val card = LinearLayout(this@ChannelGuideActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = cardBackground(false, Color.rgb(46, 111, 106))
                isFocusable = true
                isClickable = true
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(132)
                )
            }
            val top = LinearLayout(this@ChannelGuideActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val number = text("", 22f, Color.rgb(255, 209, 102), true)
            val name = text("", 13f, Color.WHITE, true).apply {
                maxLines = 1
                setPadding(dp(8), 0, 0, 0)
            }
            top.addView(number)
            top.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val kind = text("", 9f, Color.rgb(139, 213, 255), true).apply { letterSpacing = 0.1f }
            top.addView(kind)
            card.addView(top)
            val now = text("", 12f, Color.rgb(215, 225, 235), false).apply {
                setPadding(0, dp(8), 0, 0)
                maxLines = 1
            }
            val next = text("", 11f, Color.rgb(140, 165, 195), false).apply {
                setPadding(0, dp(3), 0, 0)
                maxLines = 1
            }
            val state = text("", 9f, Color.rgb(126, 226, 168), true).apply {
                setPadding(0, dp(5), 0, 0)
                letterSpacing = 0.08f
            }
            card.addView(now)
            card.addView(next)
            card.addView(state)
            card.setOnFocusChangeListener { view, focused ->
                view.animate().scaleX(if (focused) 1.025f else 1f).scaleY(if (focused) 1.025f else 1f).setDuration(120).start()
                view.background = cardBackground(focused, Color.rgb(46, 111, 106))
            }
            return Holder(card, number, name, kind, now, next, state)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val channel = items[position]
            holder.number.text = channel.number.toString().padStart(2, '0')
            holder.name.text = channel.name
            holder.kind.text = when (channel.kind) {
                "info" -> "ИНФО"
                "kids" -> "ДЕТИ"
                "cinema" -> "КИНО"
                else -> "ТВ"
            }
            holder.now.text = "Сейчас: ${channel.now}"
            holder.next.text = "Далее: ${channel.next}"
            holder.state.text = when (channel.state) {
                "maintenance" -> "ПРОФИЛАКТИКА"
                "upgrading" -> "ОБНОВЛЕНИЕ"
                else -> "В ЭФИРЕ"
            }
            holder.state.setTextColor(
                when (channel.state) {
                    "maintenance" -> Color.rgb(255, 107, 107)
                    "upgrading" -> Color.rgb(255, 209, 102)
                    else -> Color.rgb(126, 226, 168)
                }
            )
            val accent = when (channel.kind) {
                "kids" -> Color.rgb(255, 209, 102)
                "cinema" -> Color.rgb(139, 213, 255)
                "info" -> Color.rgb(126, 226, 168)
                else -> Color.rgb(255, 107, 107)
            }
            holder.name.setTextColor(if (channel.state == "live") Color.WHITE else Color.rgb(200, 210, 225))
            holder.itemView.setOnClickListener { openChannel(channel) }
            holder.itemView.contentDescription = "${channel.number} ${channel.name}"
            holder.itemView.background = cardBackground(holder.itemView.isFocused, accent)
            if (position < spanCount()) {
                holder.itemView.nextFocusUpId =
                    (filterViews[filter] ?: filterViews["all"])?.id ?: View.NO_ID
            }
        }

        private fun spanCount(): Int =
            (list.layoutManager as? GridLayoutManager)?.spanCount ?: 4

        inner class Holder(
            view: View,
            val number: TextView,
            val name: TextView,
            val kind: TextView,
            val now: TextView,
            val next: TextView,
            val state: TextView
        ) : RecyclerView.ViewHolder(view)
    }

    private fun cardBackground(focused: Boolean, accent: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(if (focused) Color.rgb(16, 38, 62) else Color.rgb(9, 22, 38))
            cornerRadius = dp(14).toFloat()
            setStroke(
                dp(if (focused) 2 else 1),
                if (focused) accent else Color.argb(100, Color.red(accent), Color.green(accent), Color.blue(accent))
            )
        }

    private fun pill(active: Boolean): GradientDrawable =
        GradientDrawable().apply {
            setColor(if (active) Color.rgb(46, 111, 106) else Color.rgb(10, 24, 42))
            cornerRadius = dp(17).toFloat()
            setStroke(dp(1), if (active) Color.rgb(255, 209, 102) else Color.argb(120, 139, 213, 255))
        }

    private fun text(value: String, size: Float, color: Int, medium: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = Typeface.create(
                if (medium) "sans-serif-medium" else "sans-serif-light",
                Typeface.NORMAL
            )
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

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

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                finish()
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                moveFocus(1)
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                moveFocus(-1)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun moveFocus(delta: Int) {
        val current = list.findContainingItemView(list.focusedChild ?: return) ?: return
        val position = list.getChildAdapterPosition(current)
        if (position == RecyclerView.NO_POSITION) return
        val target = (position + delta).coerceIn(0, (filtered.size - 1).coerceAtLeast(0))
        list.scrollToPosition(target)
        list.postDelayed({
            list.findViewHolderForAdapterPosition(target)?.itemView?.requestFocus()
        }, 80L)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private class GridSpaceDecoration(
        private val spanCount: Int,
        private val spacing: Int
    ) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val position = parent.getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION) return
            val column = position % spanCount
            outRect.left = spacing - column * spacing / spanCount
            outRect.right = (column + 1) * spacing / spanCount
            outRect.top = if (position < spanCount) spacing else 0
            outRect.bottom = spacing
        }
    }

    companion object {
        const val EXTRA_CURRENT = "guide_current"
        const val EXTRA_FROM_PLAYER = "guide_from_player"
        const val EXTRA_SELECTED = "guide_selected"
        const val REQUEST_GUIDE = 4101
    }
}
