package ru.auroratv.launcher.core

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Cartoon(
    val id: String,
    val title: String,
    val originalTitle: String,
    val year: Int,
    val description: String,
    val license: String,
    val file: String,
    val source: String,
    val durationSeconds: Double,
    val previewAtSeconds: Double,
    val silent: Boolean,
    val subtitle: String,
    val kind: String,
    val label: String,
    val promo: String
)

data class KidsProgram(
    val time: String,
    val title: String
)

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class KidsChannelConfig(
    val number: Int,
    val name: String,
    val slug: String,
    val logoRes: Int,
    val bannerRes: Int,
    val newYearLogoRes: Int,
    val newYearBannerRes: Int,
    val hasStartup: Boolean
)

/**
 * Семейство детских каналов «Совёнок»:
 * 15 — Совёнок, 16 — Совёнок Юниор, 17 — Мульт-Класс, 18 — Тин-Класс.
 * У каждого канала свой каталог и брендинг, общая медиатека и анонсы.
 */
object KidsRepository {

    const val DEFAULT_CHANNEL = 15
    const val OPEN_HOUR = 5
    const val CLOSE_HOUR = 22

    val channels: List<KidsChannelConfig> = listOf(
        KidsChannelConfig(
            15, "Совёнок", "sovyonok",
            ru.auroratv.launcher.R.drawable.kids_logo,
            ru.auroratv.launcher.R.drawable.kids_logo_banner,
            ru.auroratv.launcher.R.drawable.kids_logo_ny,
            ru.auroratv.launcher.R.drawable.kids_logo_ny_banner,
            true
        ),
        KidsChannelConfig(
            16, "Совёнок Юниор", "junior",
            ru.auroratv.launcher.R.drawable.kids_logo_junior,
            ru.auroratv.launcher.R.drawable.kids_logo_junior_banner,
            ru.auroratv.launcher.R.drawable.kids_logo_ny_junior,
            ru.auroratv.launcher.R.drawable.kids_logo_ny_junior_banner,
            false
        ),
        KidsChannelConfig(
            17, "Мульт-Класс", "class",
            ru.auroratv.launcher.R.drawable.kids_logo_class,
            ru.auroratv.launcher.R.drawable.kids_logo_class_banner,
            ru.auroratv.launcher.R.drawable.kids_logo_ny_class,
            ru.auroratv.launcher.R.drawable.kids_logo_ny_class_banner,
            false
        ),
        KidsChannelConfig(
            18, "Тин-Класс", "teen",
            ru.auroratv.launcher.R.drawable.kids_logo_teen,
            ru.auroratv.launcher.R.drawable.kids_logo_teen_banner,
            ru.auroratv.launcher.R.drawable.kids_logo_ny_teen,
            ru.auroratv.launcher.R.drawable.kids_logo_ny_teen_banner,
            false
        )
    )

    fun channelFor(number: Int): KidsChannelConfig? = channels.firstOrNull { it.number == number }

    fun isBroadcastTime(now: Calendar = Calendar.getInstance()): Boolean {
        val hour = now.get(Calendar.HOUR_OF_DAY)
        return hour in OPEN_HOUR until CLOSE_HOUR
    }

    fun cartoons(context: Context, channel: KidsChannelConfig): List<Cartoon> {
        val data = readCatalog(context, channel) ?: return emptyList()
        val array = data.optJSONArray("cartoons") ?: return emptyList()
        val result = ArrayList<Cartoon>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            result.add(
                Cartoon(
                    id = item.optString("id"),
                    title = item.optString("title"),
                    originalTitle = item.optString("originalTitle"),
                    year = item.optInt("year"),
                    description = item.optString("description"),
                    license = item.optString("license"),
                    file = item.optString("file"),
                    source = item.optString("source"),
                    durationSeconds = item.optDouble("durationSeconds", 0.0),
                    previewAtSeconds = item.optDouble("previewAtSeconds", 30.0),
                    silent = item.optBoolean("silent", false),
                    subtitle = item.optString("subtitle"),
                    kind = item.optString("kind", "cartoon"),
                    label = item.optString("label"),
                    promo = item.optString("promo", "promo_${item.optString("id")}.mp4")
                )
            )
        }
        return result.filter { it.id.isNotBlank() && (it.source.isNotBlank() || it.file.isNotBlank()) }
    }

    private fun mediaDirs(context: Context, channel: KidsChannelConfig): List<File> = listOfNotNull(
        context.getExternalFilesDir(null)?.let { File(it, "kids/${channel.slug}/media") },
        context.getExternalFilesDir(null)?.let { File(it, "kids/${channel.slug}") },
        context.getExternalFilesDir(null)?.let { File(it, "kids/media") },
        File("/sdcard/Movies/AuroraKids/${channel.slug}/media"),
        File("/sdcard/Movies/AuroraKids/media")
    )

    fun localVideo(context: Context, channel: KidsChannelConfig, cartoon: Cartoon): File? {
        if (cartoon.file.isBlank()) return null
        for (dir in mediaDirs(context, channel)) {
            val file = File(dir, cartoon.file)
            if (file.exists() && file.length() > 1024 * 1024) return file
        }
        return null
    }

    fun uri(context: Context, channel: KidsChannelConfig, cartoon: Cartoon): String {
        val local = localVideo(context, channel, cartoon)
        if (local != null) {
            return Uri.fromFile(local).toString()
        }
        if (cartoon.file.isNotBlank()) {
            return "asset:///kids/media/${cartoon.file}"
        }
        return cartoon.source
    }

    fun promoUri(context: Context, channel: KidsChannelConfig, cartoon: Cartoon): String {
        val name = cartoon.promo.ifBlank { "promo_${cartoon.id}.mp4" }
        val dirs = listOfNotNull(
            context.getExternalFilesDir(null)?.let { File(it, "kids/${channel.slug}/promos") },
            context.getExternalFilesDir(null)?.let { File(it, "kids/promos") },
            File("/sdcard/Movies/AuroraKids/${channel.slug}/promos"),
            File("/sdcard/Movies/AuroraKids/promos")
        )
        for (dir in dirs) {
            val file = File(dir, name)
            if (file.exists() && file.length() > 1024) return Uri.fromFile(file).toString()
        }
        return "asset:///kids/promos/$name"
    }

    fun subtitles(context: Context, channel: KidsChannelConfig, cartoon: Cartoon): List<SubtitleCue> {
        if (cartoon.subtitle.isBlank()) return emptyList()
        val text = try {
            context.assets.open("kids/${channel.slug}/subtitles/${cartoon.subtitle}")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        } catch (_: Throwable) {
            try {
                context.assets.open("kids/subtitles/${cartoon.subtitle}")
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
            } catch (_: Throwable) {
                return emptyList()
            }
        }
        val timePattern = Regex(
            "(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})"
        )
        val cues = ArrayList<SubtitleCue>()
        for (block in text.replace("\r\n", "\n").split(Regex("\\n\\s*\\n"))) {
            val lines = block.lines().filter { it.isNotBlank() }
            val timeLine = lines.firstOrNull { it.contains("-->") } ?: continue
            val match = timePattern.find(timeLine) ?: continue
            fun part(index: Int): Long {
                val hours = match.groupValues[index].toLong()
                val minutes = match.groupValues[index + 1].toLong()
                val seconds = match.groupValues[index + 2].toLong()
                val millis = match.groupValues[index + 3].toLong()
                return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis
            }
            val start = part(1)
            val end = part(5)
            val body = lines.dropWhile { it != timeLine }.drop(1).joinToString("\n").trim()
            if (body.isNotEmpty() && end > start) {
                cues.add(SubtitleCue(start, end, body))
            }
        }
        return cues.sortedBy { it.startMs }
    }

    fun schedule(context: Context, channel: KidsChannelConfig): List<KidsProgram> {
        val titles = cartoons(context, channel).map { it.title }
        val rows = ArrayList<KidsProgram>()
        val times = listOf("14:00", "14:30", "15:00", "15:30", "16:00", "16:30")
        val used = titles.take(6)
        used.forEachIndexed { index, title ->
            rows.add(KidsProgram(times.getOrElse(index) { "" }, title))
        }
        rows.add(KidsProgram("17:00", "далее по кругу до 22:00"))
        return rows
    }

    fun clockText(): String =
        SimpleDateFormat("HH:mm:ss", Locale("ru")).format(Date())

    fun dateText(): String =
        SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(Date())

    private fun readCatalog(context: Context, channel: KidsChannelConfig): JSONObject? {
        val local = context.getExternalFilesDir(null)
            ?.let { File(it, "kids/${channel.slug}/catalog.json") }
        if (local != null && local.exists()) {
            try {
                return JSONObject(local.readText(Charsets.UTF_8))
            } catch (_: Throwable) {
                // Fall through to the bundled catalog.
            }
        }
        return try {
            context.assets.open("kids/${channel.slug}/catalog.json")
                .bufferedReader(Charsets.UTF_8)
                .use { JSONObject(it.readText()) }
        } catch (_: Throwable) {
            null
        }
    }
}
