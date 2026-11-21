package ru.auroratv.launcher.core

import android.content.Context
import android.content.Intent

/**
 * Глобальный «родной» экран профилактики Aurora TV: цветная таблица,
 * писк, часы и бегущая строка. Состояние живёт в SharedPreferences, чтобы
 * сервер, лаунчер и уже открытые плееры видели одну и ту же картину.
 */
object MaintenanceScreen {

    private const val PREFS = "aurora_maintenance_screen"
    private const val KEY_ACTIVE = "active"
    private const val KEY_STARTED = "started_at"
    private const val KEY_UNTIL = "until"
    private const val KEY_REASON = "reason"

    data class State(
        val active: Boolean,
        val startedAt: Long,
        val until: Long,
        val reason: String
    )

    fun start(
        context: Context,
        until: Long,
        reason: String,
        showOverlay: Boolean = true
    ): State {
        val state = State(
            active = true,
            startedAt = System.currentTimeMillis(),
            until = until,
            reason = reason
        )
        save(context, state)
        if (showOverlay) show(context)
        return state
    }

    fun stop(context: Context) {
        save(context, State(false, 0L, 0L, ""))
    }

    fun current(context: Context): State {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return State(false, 0L, 0L, "")
        return State(
            active = true,
            startedAt = prefs.getLong(KEY_STARTED, 0L),
            until = prefs.getLong(KEY_UNTIL, 0L),
            reason = prefs.getString(KEY_REASON, "").orEmpty()
        )
    }

    /** Активен ли глобальный экран прямо сейчас (с учётом истечения срока). */
    fun isActive(context: Context): Boolean {
        val state = current(context)
        return state.active && (state.until <= 0L || state.until > System.currentTimeMillis())
    }

    /** Показать полноэкранный экран поверх любого плеера/лаунчера. */
    fun show(context: Context) {
        val intent = Intent(context, ru.auroratv.launcher.media.MaintenanceActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        try {
            context.startActivity(intent)
        } catch (_: Throwable) {
            // Если система не дала запустить activity, состояние остаётся и его
            // покажет следующий открытый экран.
        }
    }

    private fun save(context: Context, state: State) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACTIVE, state.active)
            .putLong(KEY_STARTED, state.startedAt)
            .putLong(KEY_UNTIL, state.until)
            .putString(KEY_REASON, state.reason)
            .apply()
    }
}
