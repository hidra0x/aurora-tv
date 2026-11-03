package ru.auroratv.launcher.core

import android.content.Context
import java.util.Calendar

data class CinemaSlot(
    val film: CinemaFilm,
    val startAt: Long,
    val endAt: Long
)

data class CinemaNow(
    val active: CinemaSlot?,
    val next: CinemaSlot,
    val joinMs: Long,
    val waitMs: Long
)

/**
 * Линейная сетка: два сеанса в день, 14:00 и 18:00 по местному времени.
 * 14:00 — сегодняшний «current», 18:00 — «next», завтра снова 14:00.
 */
object CinemaSchedule {

    private const val FIRST_HOUR = 14
    private const val SECOND_HOUR = 18

    fun now(context: Context, channel: CinemaChannel, nowMillis: Long = System.currentTimeMillis()): CinemaNow {
        val today = CinemaRepository.sessionFor(context, channel, 0)
            ?: error("Нет фильмов для канала ${channel.number}")
        val tomorrow = CinemaRepository.sessionFor(context, channel, 1)
            ?: today

        val today14 = slot(today.current, at(FIRST_HOUR, 0, 0))
        val today18 = slot(today.next, at(SECOND_HOUR, 0, 0))
        val tomorrow14 = slot(tomorrow.current, at(FIRST_HOUR, 0, 1))
        val slots = listOf(today14, today18, tomorrow14).sortedBy { it.startAt }

        val active = slots.firstOrNull { nowMillis >= it.startAt && nowMillis < it.endAt }
        val next = if (active != null) {
            slots.firstOrNull { it.startAt > active.startAt } ?: tomorrow14
        } else {
            slots.firstOrNull { it.startAt > nowMillis } ?: tomorrow14
        }
        return CinemaNow(
            active = active,
            next = next,
            joinMs = if (active != null) (nowMillis - active.startAt).coerceAtLeast(0) else 0,
            waitMs = (next.startAt - nowMillis).coerceAtLeast(0)
        )
    }

    fun at(hour: Int, minute: Int, dayOffset: Int): Long {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun slot(film: CinemaFilm, startAt: Long): CinemaSlot {
        val duration = if (film.durationSeconds > 0) film.durationSeconds else 90 * 60
        return CinemaSlot(film, startAt, startAt + duration * 1000L)
    }
}
