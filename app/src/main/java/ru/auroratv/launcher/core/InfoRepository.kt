package ru.auroratv.launcher.core

import android.content.Context
import ru.auroratv.launcher.R

data class InfoEpisode(
    val id: String,
    val title: String,
    val description: String,
    val durationSeconds: Int,
    val uri: String
)

/**
 * Инфоканал 0. Видео лежат в res/raw, поэтому канал работает без сети.
 * Ролики генерируются scripts/generate_info.py из info/episodes.json.
 */
object InfoRepository {

    fun episodes(context: Context): List<InfoEpisode> {
        val packageName = context.packageName
        fun uri(resource: Int): String = "android.resource://$packageName/$resource"
        return listOf(
            InfoEpisode(
                "trailer",
                "Aurora TV 1.4 — трейлер",
                "Экран, каналы и видео за 28 секунд",
                28,
                uri(R.raw.info_trailer)
            ),
            InfoEpisode(
                "channels",
                "Каналы и цифры",
                "Home, номер канала, OSD и переключение",
                43,
                uri(R.raw.info_channels)
            ),
            InfoEpisode(
                "launcher",
                "Туториал по лаунчеру",
                "Главный экран, приложения, медиа и настройки",
                42,
                uri(R.raw.info_launcher)
            ),
            InfoEpisode(
                "whatsnew",
                "Что нового в 1.4",
                "Каналы, Home, плейлист и Universal",
                38,
                uri(R.raw.info_whatsnew)
            )
        )
    }
}
