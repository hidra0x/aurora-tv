package ru.auroratv.launcher.core

import android.content.Context
import ru.auroratv.launcher.R
import java.net.HttpURLConnection
import java.net.URL

data class Channel(
    val name: String,
    val url: String,
    val group: String = ""
)

object ChannelsRepository {

    fun bundled(context: Context): List<Channel> = try {
        context.resources.openRawResource(R.raw.channels)
            .bufferedReader(Charsets.UTF_8)
            .use { parse(it.readText()) }
    } catch (_: Throwable) {
        emptyList()
    }

    fun fromUrl(url: String): List<Channel>? {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (_: Throwable) {
            return null
        }
        return try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "AuroraTV/1.4 (Android TV launcher)")
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parse(text).ifEmpty { null }
        } catch (_: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }

    fun parse(text: String): List<Channel> {
        val channels = ArrayList<Channel>()
        var pendingName: String? = null
        var pendingGroup = ""
        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("\uFEFF")
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                pendingName = line.substringAfterLast(',', "").trim().ifBlank { null }
                pendingGroup = Regex("group-title=\"([^\"]*)\"")
                    .find(line)
                    ?.groupValues
                    ?.getOrNull(1)
                    .orEmpty()
            } else if (!line.startsWith("#")) {
                val name = pendingName
                if (name != null) {
                    channels.add(Channel(name, line, pendingGroup))
                    pendingName = null
                }
            }
        }
        return channels
    }
}
