package ru.auroratv.launcher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.auroratv.launcher.MainActivity
import ru.auroratv.launcher.R
import ru.auroratv.launcher.core.AppEntry
import ru.auroratv.launcher.core.IconLoader
import ru.auroratv.launcher.core.Prefs

class AppTileAdapter(
    private val host: MainActivity,
    private val prefs: Prefs,
    private val style: Style,
    private val fillWidth: Boolean = false
) : RecyclerView.Adapter<AppTileAdapter.Holder>() {

    enum class Style { BANNER, ICON }

    private var items: List<AppEntry> = emptyList()

    init {
        setHasStableIds(true)
    }

    fun submit(newItems: List<AppEntry>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long = items[position].key.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val layout = if (style == Style.BANNER) {
            R.layout.item_app_banner
        } else {
            R.layout.item_app_icon
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        if (style == Style.ICON && fillWidth) {
            val params = view.layoutParams
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            view.layoutParams = params
        }
        if (style == Style.BANNER) {
            view.findViewById<ImageView>(R.id.bannerImage)?.apply {
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(v: View, outline: android.graphics.Outline) {
                        val radius = 18f * v.resources.displayMetrics.density
                        outline.setRoundRect(0, 0, v.width, v.height, radius)
                    }
                }
            }
        }
        view.applyFocusScale({ prefs.animations }, if (style == Style.BANNER) 1.045f else 1.055f)
        return Holder(view, style)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = items[position]
        holder.title.text = app.label
        holder.root.contentDescription = app.label
        holder.root.setOnClickListener { host.launchApp(app) }
        holder.root.setOnLongClickListener {
            host.toggleFavorite(app)
            true
        }
        holder.star?.visibility = if (prefs.isFavorite(app.packageName)) View.VISIBLE else View.GONE

        if (style == Style.BANNER) {
            bindBanner(holder, app)
        } else {
            holder.icon?.let { loadIcon(it, app, 52) }
        }
    }

    private fun bindBanner(holder: Holder, app: AppEntry) {
        val banner = holder.banner ?: return
        val icon = holder.icon ?: return
        banner.setImageDrawable(null)
        if (!app.hasBanner) {
            banner.visibility = View.GONE
            icon.visibility = View.VISIBLE
            loadIcon(icon, app, 48)
            return
        }

        banner.visibility = View.VISIBLE
        icon.visibility = View.GONE
        val width = host.dp(196)
        val height = host.dp(112)
        val key = "b:${app.packageName}:$width:$height"
        banner.tag = key
        IconLoader.load(host, app, banner = true, widthPx = width, heightPx = height) { bitmap ->
            if (banner.tag != key) return@load
            if (bitmap != null) {
                banner.setImageBitmap(bitmap)
            } else {
                banner.visibility = View.GONE
                icon.visibility = View.VISIBLE
                loadIcon(icon, app, 48)
            }
        }
    }

    private fun loadIcon(view: ImageView, app: AppEntry, sizeDp: Int) {
        val size = host.dp(sizeDp)
        val key = "i:${app.packageName}:$size"
        view.tag = key
        view.setImageDrawable(null)
        IconLoader.load(host, app, banner = false, widthPx = size, heightPx = size) { bitmap ->
            if (view.tag == key) view.setImageBitmap(bitmap)
        }
    }

    class Holder(val root: View, style: Style) : RecyclerView.ViewHolder(root) {
        val title: TextView = root.findViewById(R.id.title)
        val icon: ImageView? = root.findViewById(R.id.iconImage)
        val banner: ImageView? = if (style == Style.BANNER) root.findViewById(R.id.bannerImage) else null
        val star: ImageView? = root.findViewById(R.id.star)
    }
}
