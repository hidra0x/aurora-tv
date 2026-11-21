package ru.auroratv.launcher.core

import android.content.Context
import android.content.Intent
import android.widget.Toast
import ru.auroratv.launcher.media.CinemaPlayerActivity
import ru.auroratv.launcher.media.KidsChannelActivity
import ru.auroratv.launcher.media.PlayerActivity

data class GuideChannel(
    val number: Int,
    val name: String,
    val kind: String,
    val now: String,
    val next: String,
    val state: String
)

/**
 * Единый список каналов Aurora для телегида и переключения без выхода
 * в лаунчер: инфоканал, ТВ, детские и кинозалы.
 */
object ChannelCatalog {

    fun all(context: Context): List<GuideChannel> {
        val result = ArrayList<GuideChannel>()

        result.add(
            GuideChannel(
                0,
                "Инфоканал",
                "info",
                "Инфоканал Aurora TV",
                "Подсказки, анонсы и видео",
                stateOf(context, 0)
            )
        )

        ChannelsRepository.bundled(context).forEachIndexed { index, channel ->
            val number = index + 1
            result.add(
                GuideChannel(
                    number,
                    channel.name,
                    "tv",
                    "Прямой эфир",
                    "По программе канала",
                    stateOf(context, number)
                )
            )
        }

        KidsRepository.channels.forEach { config ->
            val titles = KidsRepository.cartoons(context, config).map { it.title }
            result.add(
                GuideChannel(
                    config.number,
                    config.name,
                    "kids",
                    titles.getOrNull(0) ?: "Детский эфир",
                    titles.getOrNull(1) ?: "Сказки и английский",
                    stateOf(context, config.number)
                )
            )
        }

        CinemaRepository.channels(context).forEach { channel ->
            val session = CinemaRepository.sessionFor(context, channel)
            result.add(
                GuideChannel(
                    channel.number,
                    channel.name,
                    "cinema",
                    session?.current?.title ?: "Афиша кинозала",
                    session?.next?.title ?: "Далее по программе",
                    stateOf(context, channel.number)
                )
            )
        }

        return result.sortedBy { it.number }
    }

    fun byNumber(context: Context, number: Int): GuideChannel? =
        all(context).firstOrNull { it.number == number }

    fun next(context: Context, number: Int, delta: Int): GuideChannel? {
        val channels = all(context)
        if (channels.isEmpty()) return null
        val index = channels.indexOfFirst { it.number == number }
        val start = if (index >= 0) index else 0
        return channels[Math.floorMod(start + delta, channels.size)]
    }

    fun open(context: Context, channel: GuideChannel): Boolean {
        val state = ChannelControl.stateFor(context, channel.number)
        if (!state.isLive && channel.kind != "kids" && channel.kind != "cinema") {
            Toast.makeText(context, "Канал на профилактике", Toast.LENGTH_SHORT).show()
            return false
        }
        val intent = when (channel.kind) {
            "info" -> {
                val episodes = InfoRepository.episodes(context).shuffled()
                Intent(context, PlayerActivity::class.java).apply {
                    putStringArrayListExtra(
                        PlayerActivity.EXTRA_URIS,
                        ArrayList(episodes.map { it.uri })
                    )
                    putStringArrayListExtra(
                        PlayerActivity.EXTRA_TITLES,
                        ArrayList(episodes.map { it.title })
                    )
                    putExtra(PlayerActivity.EXTRA_START, 0)
                    putExtra(PlayerActivity.EXTRA_INFO_CHANNEL, true)
                    putExtra(PlayerActivity.EXTRA_CHANNEL, 0)
                }
            }
            "kids" -> Intent(context, KidsChannelActivity::class.java).apply {
                putExtra(KidsChannelActivity.EXTRA_CHANNEL, channel.number)
            }
            "cinema" -> Intent(context, CinemaPlayerActivity::class.java).apply {
                putExtra(CinemaPlayerActivity.EXTRA_CHANNEL_NUMBER, channel.number)
            }
            else -> {
                val tv = ChannelsRepository.bundled(context).getOrNull(channel.number - 1)
                if (tv == null) {
                    Toast.makeText(context, "Канал не найден", Toast.LENGTH_SHORT).show()
                    return false
                }
                Intent(context, PlayerActivity::class.java).apply {
                    putStringArrayListExtra(
                        PlayerActivity.EXTRA_URIS,
                        arrayListOf(tv.url)
                    )
                    putStringArrayListExtra(
                        PlayerActivity.EXTRA_TITLES,
                        arrayListOf(tv.name)
                    )
                    putExtra(PlayerActivity.EXTRA_START, 0)
                    putExtra(PlayerActivity.EXTRA_CHANNEL, channel.number)
                }
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: Throwable) {
            Toast.makeText(context, "Не удалось открыть канал", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun stateOf(context: Context, number: Int): String {
        val state = ChannelControl.stateFor(context, number)
        return if (state.isLive) {
            "live"
        } else if (state.state == ChannelControl.STATE_UPGRADING) {
            "upgrading"
        } else {
            "maintenance"
        }
    }
}
