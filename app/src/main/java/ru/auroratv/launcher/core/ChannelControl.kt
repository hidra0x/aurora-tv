package ru.auroratv.launcher.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class ManagedChannel(
    val number: Int,
    val name: String,
    val kind: String,
    val state: String,
    val reason: String,
    val until: Long,
    val version: String,
    val notes: String,
    val updatedAt: Long
) {
    val isLive: Boolean get() = state == "live"
}

/**
 * Состояние каналов и расписание профилактики. Хранится в SharedPreferences,
 * чтобы сервер и приложение видели одни и те же данные без перезапуска.
 */
object ChannelControl {

    const val STATE_LIVE = "live"
    const val STATE_MAINTENANCE = "maintenance"
    const val STATE_UPGRADING = "upgrading"

    private const val PREFS = "aurora_channel_control"
    private const val KEY_STATES = "states"
    private const val KEY_SCHEDULE = "schedule"

    fun channels(context: Context): List<ManagedChannel> {
        val states = readStates(context)
        val result = ArrayList<ManagedChannel>()
        result.add(
            managed(
                0,
                "Инфоканал",
                "info",
                states.optJSONObject("0")
            )
        )
        ChannelsRepository.bundled(context).forEachIndexed { index, channel ->
            val number = index + 1
            result.add(
                managed(
                    number,
                    channel.name,
                    "tv",
                    states.optJSONObject(number.toString())
                )
            )
        }
        CinemaRepository.channels(context).forEach { channel ->
            result.add(
                managed(
                    channel.number,
                    channel.name,
                    "cinema",
                    states.optJSONObject(channel.number.toString())
                )
            )
        }
        return result.sortedBy { it.number }
    }

    fun stateFor(context: Context, number: Int): ManagedChannel {
        val state = readStates(context).optJSONObject(number.toString())
        return managed(number, "", "channel", state)
    }

    fun isBlocked(context: Context, number: Int): Boolean =
        !stateFor(context, number).isLive

    @Synchronized
    fun setState(
        context: Context,
        number: Int,
        state: String,
        reason: String = "",
        until: Long = 0L,
        version: String = "",
        notes: String = ""
    ) {
        val states = readStates(context)
        if (state == STATE_LIVE) {
            states.remove(number.toString())
        } else {
            states.put(
                number.toString(),
                JSONObject().apply {
                    put("state", state)
                    put("reason", reason)
                    put("until", until)
                    put("version", version)
                    put("notes", notes)
                    put("updatedAt", System.currentTimeMillis())
                }
            )
        }
        writeStates(context, states)
    }

    @Synchronized
    fun setAll(
        context: Context,
        state: String,
        reason: String = "",
        until: Long = 0L
    ) {
        val numbers = channels(context)
            .filter { it.number != 0 }
            .map { it.number }
        val states = readStates(context)
        numbers.forEach { number ->
            if (state == STATE_LIVE) {
                states.remove(number.toString())
            } else {
                states.put(
                    number.toString(),
                    JSONObject().apply {
                        put("state", state)
                        put("reason", reason)
                        put("until", until)
                        put("version", "")
                        put("notes", "")
                        put("updatedAt", System.currentTimeMillis())
                    }
                )
            }
        }
        writeStates(context, states)
    }

    fun schedule(context: Context): JSONObject {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SCHEDULE, null)
        if (stored != null) {
            return try {
                JSONObject(stored)
            } catch (_: Throwable) {
                defaultSchedule()
            }
        }
        return defaultSchedule()
    }

    @Synchronized
    fun saveSchedule(context: Context, schedule: JSONObject) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SCHEDULE, schedule.toString())
            .apply()
    }

    fun defaultSchedule(): JSONObject {
        val channels = JSONArray()
        val weekdays = arrayOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY,
            Calendar.SUNDAY
        )
        for (number in 20..32) {
            val index = number - 20
            val dayIndex = index % 7
            val lateSlot = index >= 7
            channels.put(
                JSONObject().apply {
                    put("number", number)
                    put("weekday", weekdays[dayIndex])
                    put("start", if (lateSlot) "04:30" else "04:00")
                    put("end", if (lateSlot) "05:00" else "04:30")
                }
            )
        }
        return JSONObject().apply {
            put(
                "global",
                JSONObject().apply {
                    put("start", "03:00")
                    put("end", "04:00")
                    put("note", "Общая профилактика всех каналов")
                }
            )
            put("channels", channels)
            put("note", "Киноканалы обслуживаются по одному в 04:00 и 04:30")
        }
    }

    private fun managed(
        number: Int,
        name: String,
        kind: String,
        state: JSONObject?
    ): ManagedChannel {
        val now = System.currentTimeMillis()
        val until = state?.optLong("until", 0L) ?: 0L
        val expired = until > 0L && until <= now
        val actualState = if (expired) STATE_LIVE else state?.optString("state", STATE_LIVE) ?: STATE_LIVE
        return ManagedChannel(
            number = number,
            name = name,
            kind = kind,
            state = actualState,
            reason = if (expired) "" else state?.optString("reason", "").orEmpty(),
            until = if (expired) 0L else until,
            version = if (expired) "" else state?.optString("version", "").orEmpty(),
            notes = if (expired) "" else state?.optString("notes", "").orEmpty(),
            updatedAt = if (expired) 0L else state?.optLong("updatedAt", 0L) ?: 0L
        )
    }

    private fun readStates(context: Context): JSONObject {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STATES, null)
        return try {
            if (raw.isNullOrBlank()) JSONObject() else JSONObject(raw)
        } catch (_: Throwable) {
            JSONObject()
        }
    }

    private fun writeStates(context: Context, states: JSONObject) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATES, states.toString())
            .apply()
    }
}
