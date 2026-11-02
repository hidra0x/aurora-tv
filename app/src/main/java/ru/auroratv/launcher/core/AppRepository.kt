package ru.auroratv.launcher.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import java.text.Collator
import java.util.Locale

object AppRepository {

    private val tvPackages = setOf(
        "com.android.tv",
        "com.google.android.tv",
        "com.google.android.apps.tv.live",
        "com.tcl.tv",
        "com.tcl.tvinput",
        "com.tcl.live",
        "com.tcl.tvmanager",
        "com.mstar.tv.tvplayer.ui",
        "com.mediatek.tv",
        "com.mediatek.wwtv.tvcenter",
        "com.hisense.tv",
        "com.hisense.tvinput",
        "com.skyworth.tv",
        "com.skyworth.live",
        "com.haier.tv",
        "com.haier.livetv",
        "com.unionman.tv",
        "com.unionman.live",
        "com.ktc.tv",
        "com.ktc.live",
        "com.telefunken.tv",
        "com.telefunken.live",
        "com.tve.tv",
        "org.droidtv.tv",
        "com.sony.dtv.tv",
        "com.panasonic.tv",
        "com.lge.tv",
        "com.philips.tv",
        "com.sharp.tv",
        "com.toshiba.tv",
        "com.jvc.tv",
        "com.grundig.tv",
        "com.vestel.tv",
        "com.changhong.tv",
        "com.konka.tv",
        "com.amazon.tv.livetv",
        "com.amazon.firetv.livetv",
        "com.nvidia.livetv"
    )

    private val tvLabel = Regex(
        "телевизор|live\\s*tv|прямой\\s*эфир|digital\\s*tv|\\bdtv\\b|\\batv\\b|каналы",
        RegexOption.IGNORE_CASE
    )

    private val tvLabelBlocklist = listOf(
        "aptoide", "store", "market", "browser", "youtube", "smarttube",
        "ivi", "okko", "rutube", "vk", "zona", "megogo", "start", "24тв"
    )

    @Suppress("DEPRECATION")
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        )

        val selected = LinkedHashMap<String, ResolveInfo>()
        for (intent in intents) {
            val resolved = try {
                pm.queryIntentActivities(intent, 0)
            } catch (_: Throwable) {
                emptyList()
            }
            for (info in resolved) {
                val activity = info.activityInfo ?: continue
                val app = activity.applicationInfo ?: continue
                val pkg = app.packageName
                if (pkg == context.packageName) continue
                if (!selected.containsKey(pkg)) selected[pkg] = info
            }
        }

        val collator = Collator.getInstance(Locale("ru")).apply { strength = Collator.PRIMARY }
        return selected.values.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            val app = activity.applicationInfo ?: return@mapNotNull null
            val label = try {
                activity.loadLabel(pm).toString().trim()
            } catch (_: Throwable) {
                app.packageName
            }.ifBlank { app.packageName }
            AppEntry(
                packageName = app.packageName,
                label = label,
                component = ComponentName(activity.packageName, activity.name),
                hasBanner = app.banner != 0,
                system = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
        }.sortedWith(Comparator { a, b -> collator.compare(a.label, b.label) })
    }

    fun launch(context: Context, app: AppEntry): Boolean {
        val direct = Intent(Intent.ACTION_MAIN).apply {
            component = app.component
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        try {
            context.startActivity(direct)
            return true
        } catch (_: Throwable) {
            val fallback = try {
                context.packageManager.getLaunchIntentForPackage(app.packageName)
            } catch (_: Throwable) {
                null
            } ?: return false
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(fallback)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    fun isTvCandidate(app: AppEntry): Boolean =
        !app.system ||
            app.hasBanner ||
            app.packageName in tvPackages ||
            tvPackages.any { known -> app.packageName.startsWith("$known.") } ||
            tvLabel.containsMatchIn(app.label)

    fun detectTvApp(context: Context, apps: List<AppEntry>): AppEntry? {
        if (apps.isEmpty()) return null
        val homePackages = homePackages(context)
        val candidates = apps.filterNot {
            it.packageName in homePackages ||
                it.packageName.contains("launcher", ignoreCase = true) ||
                it.label.contains("launcher", ignoreCase = true)
        }

        candidates.firstOrNull { it.packageName in tvPackages }?.let { return it }
        candidates.firstOrNull { app ->
            tvPackages.any { known -> app.packageName.startsWith("$known.") }
        }?.let { return it }

        return candidates.firstOrNull { app ->
            val label = app.label
            tvLabel.containsMatchIn(label) &&
                tvLabelBlocklist.none { blocked -> label.contains(blocked, ignoreCase = true) }
        }
    }

    private fun homePackages(context: Context): Set<String> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        return try {
            pm.queryIntentActivities(intent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        } catch (_: Throwable) {
            emptySet()
        }
    }
}
