package ru.auroratv.launcher.core

import android.content.Context
import java.util.Calendar

/**
 * Новогодний эфир Aurora TV. Включает снег в главном меню, праздничные
 * логотипы детских каналов и новогоднее оформление кинозалов.
 * По умолчанию работает до 1 января 2027 года; можно выключить раньше
 * через настройки/сервер.
 */
object NewYearEvent {

    private const val PREFS = "aurora_new_year"
    private const val KEY_ENABLED = "enabled"

    fun untilMillis(): Long = Calendar.getInstance().apply {
        clear()
        set(2027, Calendar.JANUARY, 1, 0, 0, 0)
    }.timeInMillis

    fun isActive(context: Context): Boolean {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENABLED, null)
        if (value != null) return value == "1"
        return System.currentTimeMillis() < untilMillis()
    }

    fun setActive(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENABLED, if (active) "1" else "0")
            .apply()
    }

    fun cinemaName(id: String): String? = when (id) {
        "premiere" -> "Новогодняя премьера"
        "hit" -> "Зимний хит"
        "aurora" -> "Aurora Cinema • Новый год"
        "comedy" -> "Новогодняя комедия"
        "action" -> "Снежный боевик"
        "sci-fi" -> "Зимняя фантастика"
        "detective" -> "Новогодний детектив"
        "horror" -> "Снежная мистика"
        "family" -> "Семейный Новый год"
        "russian" -> "Наше новогоднее"
        "legends" -> "Легенды зимы"
        "night" -> "Ночной Новый год"
        "series" -> "Новогодние сериалы"
        else -> null
    }

    fun cinemaDescription(id: String): String? = when (id) {
        "premiere" -> "Праздничные премьеры и зимние истории."
        "hit" -> "Классика, которую хочется пересматривать зимой."
        "aurora" -> "Главный кинозал Aurora в новогоднем оформлении."
        "comedy" -> "Смешные истории для праздничного вечера."
        "action" -> "Погони и приключения в зимнем эфире."
        "sci-fi" -> "Космос, будущее и новогодние чудеса."
        "detective" -> "Тайны и расследования под снегом."
        "horror" -> "Атмосферные истории для зимних вечеров."
        "family" -> "Кино для всей семьи под ёлку."
        "russian" -> "Российские и советские фильмы в праздничном эфире."
        "legends" -> "Золотая классика мирового кино."
        "night" -> "Нуар и триллеры для длинных зимних ночей."
        "series" -> "Истории, которые можно смотреть запоем."
        else -> null
    }

    fun cinemaColor(id: String): String? = when (id) {
        "premiere" -> "#FF6B6B"
        "hit" -> "#FFD166"
        "aurora" -> "#8BD5FF"
        "comedy" -> "#FFB86B"
        "action" -> "#FF5C8A"
        "sci-fi" -> "#7C9CFF"
        "detective" -> "#C4B5FD"
        "horror" -> "#B388FF"
        "family" -> "#7EE2A8"
        "russian" -> "#F5C97A"
        "legends" -> "#E0E0E0"
        "night" -> "#6C7AFF"
        "series" -> "#8BD5FF"
        else -> null
    }
}
