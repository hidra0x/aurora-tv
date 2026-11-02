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

    fun tips(): List<String> = listOf(
        "Home переключает штатное ТВ и Aurora.",
        "0 — инфоканал. 1–14 — каналы.",
        "Набери номер канала и подожди 3 секунды.",
        "OK запускает канал сразу.",
        "BACK отменяет ввод номера.",
        "Свой M3U: раздел «Каналы» → «Плейлист».",
        "Видео: SmartTube и RuTube — в разделе «Видео».",
        "Android 5.0+ — один APK для разных ТВ."
    )

    fun episodes(context: Context): List<InfoEpisode> {
        val packageName = context.packageName
        fun uri(resource: Int): String = "android.resource://$packageName/$resource"
        return listOf(
            InfoEpisode(
                "trailer",
                "Aurora TV 1.4 — трейлер",
                "Экран, каналы и видео вживую",
                43,
                uri(R.raw.info_trailer)
            ),
            InfoEpisode(
                "channels",
                "Каналы и цифры",
                "Home, номер канала, OSD и переключение",
                49,
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
                "remote",
                "Пульт и навигация",
                "OK, BACK, MENU и Home",
                31,
                uri(R.raw.info_remote)
            ),
            InfoEpisode(
                "playlist",
                "Свой M3U-плейлист",
                "Как добавить каналы провайдера",
                31,
                uri(R.raw.info_playlist)
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
