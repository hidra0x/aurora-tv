package ru.auroratv.launcher.core

import android.content.Context
import android.content.Intent
import ru.auroratv.launcher.media.BroadcastTakeoverActivity

/**
 * Художественный спецэфир Aurora TV. Это постановка, а не реальное
 * экстренное оповещение: в углу всегда видна пометка.
 */
object BroadcastEvent {

    private const val PREFS = "aurora_broadcast_event"
    private const val KEY_ACTIVE = "active"
    private const val KEY_STARTED = "started_at"
    private const val KEY_DURATION = "duration_ms"
    private const val KEY_HEADLINE = "headline"
    private const val KEY_MESSAGE = "message"
    private const val KEY_ANNOUNCER = "announcer"

    data class State(
        val startedAt: Long,
        val durationMs: Long,
        val headline: String,
        val message: String,
        val announcerText: String
    )

    fun start(
        context: Context,
        minutes: Int = 10,
        headline: String = "СВОБОДУ АРТЁМУ",
        message: String = "Я ЖИВ. Со мной всё в порядке. Не верьте слухам. Я на связи.",
        announcerText: String = "Внимание. Мы прервали эфир для сообщения Артёма. " +
            "Уберите телефоны, планшеты и часы. Связи не будет. Говорит Артём. Я жив."
    ): State {
        val duration = (minutes.coerceIn(1, 120).toLong()) * 60_000L
        val state = State(
            startedAt = System.currentTimeMillis(),
            durationMs = duration,
            headline = headline,
            message = message,
            announcerText = announcerText
        )
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACTIVE, true)
            .putLong(KEY_STARTED, state.startedAt)
            .putLong(KEY_DURATION, state.durationMs)
            .putString(KEY_HEADLINE, state.headline)
            .putString(KEY_MESSAGE, state.message)
            .putString(KEY_ANNOUNCER, state.announcerText)
            .apply()

        val intent = Intent(context, BroadcastTakeoverActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            context.startActivity(intent)
        } catch (_: Throwable) {
            // Activity may be blocked by the system; state remains for manual open.
        }
        return state
    }

    fun current(context: Context): State? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return null
        return State(
            startedAt = prefs.getLong(KEY_STARTED, System.currentTimeMillis()),
            durationMs = prefs.getLong(KEY_DURATION, 10 * 60_000L),
            headline = prefs.getString(KEY_HEADLINE, "СВОБОДУ АРТЁМУ").orEmpty(),
            message = prefs.getString(KEY_MESSAGE, "Я ЖИВ").orEmpty(),
            announcerText = prefs.getString(KEY_ANNOUNCER, "").orEmpty()
        )
    }

    fun stop(context: Context, restoreChannels: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        if (restoreChannels) {
            ChannelControl.setAll(context, ChannelControl.STATE_LIVE)
            ChannelControl.setState(context, 0, ChannelControl.STATE_LIVE)
        }
    }

    fun toTechnical(context: Context, until: Long) {
        val reason = "Технические работы после спецэфира"
        ChannelControl.setAll(
            context,
            ChannelControl.STATE_MAINTENANCE,
            reason,
            until
        )
        ChannelControl.setState(context, 0, ChannelControl.STATE_MAINTENANCE, reason, until)
    }
}
