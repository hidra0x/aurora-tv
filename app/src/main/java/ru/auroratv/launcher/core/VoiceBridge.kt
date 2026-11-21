package ru.auroratv.launcher.core

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import ru.auroratv.launcher.media.ChannelGuideActivity
import ru.auroratv.launcher.media.VoiceOverlayActivity
import java.util.Locale

/** Фазы отображения голосового помощника на экране ТВ. */
enum class VoicePhase {
    IDLE, LISTENING, THINKING, ANSWERING, RADIO, TIMER
}

data class VoiceState(
    val phase: VoicePhase = VoicePhase.IDLE,
    val heard: String = "",
    val answer: String = "",
    val candidates: List<GuideChannel> = emptyList(),
    val timerEndAt: Long = 0L,
    val timerTotalMs: Long = 0L,
    val connected: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val timerRemainingMs: Long
        get() = if (timerEndAt <= 0L) 0L else (timerEndAt - System.currentTimeMillis()).coerceAtLeast(0L)
}

/**
 * Ядро голосового управления Aurora TV.
 * Демон на Алисе шлёт сюда фазы и распознанный текст; ядро разбирает команды,
 * открывает каналы и рассылает состояние полноэкранному оверлею.
 */
object VoiceBridge {

    private const val TAG = "AuroraVoice"

    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var state = VoiceState()
    private val listeners = LinkedHashSet<(VoiceState) -> Unit>()
    private var pendingCandidates: List<GuideChannel> = emptyList()
    private var clearRunnable: Runnable? = null
    private var timerRunnable: Runnable? = null

    // Ритм эквалайзера. TV не видит сэмплы звука Алисы, поэтому полосы
    // пульсируют в такт по BPM; внешний демон может выравнивать удары через beat().
    private var rhythmBpm = 120
    private var beatEpoch = System.currentTimeMillis()
    private var beatIntensity = 1f

    val current: VoiceState
        @Synchronized get() = state

    val rhythm: Int
        @Synchronized get() = rhythmBpm

    val beatAt: Long
        @Synchronized get() = beatEpoch

    val beatLevel: Float
        @Synchronized get() = beatIntensity

    /** Синхронизировать эквалайзер с ударом, присланным с Алисы. */
    @Synchronized
    fun beat(context: Context, intensity: Float = 1f): VoiceState {
        attach(context)
        beatEpoch = System.currentTimeMillis()
        beatIntensity = intensity.coerceIn(0.2f, 2f)
        return state
    }

    /** Задать темп («ритм 128») без выхода из режима радио. */
    @Synchronized
    fun setRhythm(context: Context, bpm: Int): VoiceState {
        attach(context)
        rhythmBpm = bpm.coerceIn(40, 220)
        beatEpoch = System.currentTimeMillis()
        if (state.phase == VoicePhase.RADIO) {
            state = state.copy(answer = "Ритм $rhythmBpm BPM.", updatedAt = System.currentTimeMillis())
            publish(context)
        } else {
            state = answer(context, "Ритм $rhythmBpm BPM.")
        }
        return state
    }

    @Synchronized
    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    fun listen(listener: (VoiceState) -> Unit) {
        synchronized(this) { listeners.add(listener) }
        listener(current)
    }

    fun unlisten(listener: (VoiceState) -> Unit) {
        synchronized(this) { listeners.remove(listener) }
    }

    /** Отметить подключение демона — на экране появится мини-инструкция. */
    @Synchronized
    fun setConnected(context: Context, connected: Boolean): VoiceState {
        attach(context)
        val wasConnected = state.connected
        state = state.copy(connected = connected, updatedAt = System.currentTimeMillis())
        if (connected && !wasConnected && state.phase == VoicePhase.IDLE) {
            state = state.copy(
                answer = COMMAND_HELP,
                phase = VoicePhase.ANSWERING,
                updatedAt = System.currentTimeMillis()
            )
            scheduleClear(context, 15_000L)
        }
        publish(context)
        return state
    }

    /** Событие от демона Алисы: фаза и распознанный/синтезированный текст. */
    @Synchronized
    fun onEvent(
        context: Context,
        phase: String,
        text: String = "",
        answer: String = ""
    ): VoiceState {
        attach(context)
        val parsed = runCatching { VoicePhase.valueOf(phase.uppercase(Locale.ROOT)) }
            .getOrDefault(VoicePhase.IDLE)
        val heard = if (text.isNotBlank()) text else state.heard
        val reply = answer.ifBlank { if (parsed == VoicePhase.ANSWERING) state.answer else "" }
        state = state.copy(
            phase = parsed,
            heard = heard,
            answer = reply,
            updatedAt = System.currentTimeMillis()
        )
        if (parsed == VoicePhase.LISTENING || parsed == VoicePhase.THINKING) {
            scheduleClear(context, 12_000L)
        } else if (parsed == VoicePhase.ANSWERING && state.timerEndAt <= 0L) {
            scheduleClear(context, 9_000L)
        }
        publish(context)
        return state
    }

    /** Выполнить текстовую команду (из гида, веб-панели или от демона Алисы). */
    @Synchronized
    fun command(context: Context, raw: String): VoiceState {
        attach(context)
        clearPendingClear()
        val text = normalize(raw)
        if (text.isBlank()) return state
        android.util.Log.i(TAG, "command: $text")

        when {
            text.matches(Regex("(?:что умеешь|какие команды|помощь|инструкция|список команд)")) ->
                return answer(context, COMMAND_HELP)

            text.contains(Regex("(?:ритм|темп)\\s*\\d+")) -> {
                val bpm = Regex("\\d+").find(text)?.value?.toIntOrNull() ?: 120
                return setRhythm(context, bpm)
            }
            text.matches(Regex("(?:быстрее|ускорь|увеличь ритм)")) ->
                return setRhythm(context, rhythmBpm + 10)
            text.matches(Regex("(?:медленнее|замедли|уменьши ритм)")) ->
                return setRhythm(context, rhythmBpm - 10)

            text.contains(Regex("(?:выключи|останови|закрой|хватит).*радио")) ||
                text.matches(Regex("(?:стоп|хватит|выход)")) ->
                return answer(context, "Радио выключено.")

            text.contains("радио") || text.contains("музыку") ->
                return switchPhase(context, VoicePhase.RADIO,
                    "Радио включено. Звук идёт с Алисы на телевизор. Скажите «выключи радио», чтобы закончить.")

            text.contains(Regex("(?:открой|покажи).*(?:гид|программу|расписание)")) ||
                text.matches(Regex("(?:телегид|гид|программа передач)")) -> {
                    answer(context, "Открываю телегид.")
                    clearPendingClear()
                    scheduleClear(context, 2_000L)
                    val intent = Intent(context, ChannelGuideActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return current
                }

            text.contains(Regex("(?:отмени|сбрось|убери).*таймер")) -> {
                timerRunnable?.let { handler.removeCallbacks(it) }
                timerRunnable = null
                state = state.copy(timerEndAt = 0L, timerTotalMs = 0L)
                return answer(context, "Таймер отменён.")
            }

            parseTimerSeconds(text) != null -> {
                val seconds = parseTimerSeconds(text)!!
                timerRunnable?.let { handler.removeCallbacks(it) }
                val endAt = System.currentTimeMillis() + seconds * 1000L
                state = state.copy(
                    phase = VoicePhase.TIMER,
                    timerEndAt = endAt,
                    timerTotalMs = seconds * 1000L,
                    answer = "Таймер на ${formatDuration(seconds)} поставлен.",
                    updatedAt = System.currentTimeMillis()
                )
                val finish = Runnable {
                    state = state.copy(
                        phase = VoicePhase.ANSWERING,
                        timerEndAt = 0L,
                        timerTotalMs = 0L,
                        answer = "Таймер завершён.",
                        updatedAt = System.currentTimeMillis()
                    )
                    publish(context)
                    scheduleClear(context, 10_000L)
                }
                timerRunnable = finish
                handler.postDelayed(finish, seconds * 1000L)
                publish(context)
                return current
            }

            text.matches(Regex("(?:первый|второй|третий|четвёртый|пятый|[1-5])")) &&
                pendingCandidates.isNotEmpty() -> {
                val index = when (text) {
                    "первый", "1" -> 0
                    "второй", "2" -> 1
                    "третий", "3" -> 2
                    "четвёртый", "4" -> 3
                    else -> 4
                }
                val chosen = pendingCandidates.getOrNull(index)
                pendingCandidates = emptyList()
                return if (chosen != null) openChannel(context, chosen) else answer(context, "Такого варианта нет.")
            }
        }

        val resolution = resolveChannel(context, raw)
        return when {
            resolution.channel != null -> openChannel(context, resolution.channel)
            resolution.candidates.size == 1 -> openChannel(context, resolution.candidates.first())
            resolution.candidates.size > 1 -> {
                pendingCandidates = resolution.candidates
                val names = resolution.candidates.take(6)
                    .mapIndexed { i, c -> "${i + 1}) ${c.name}" }
                    .joinToString("; ")
                answer(context, "Несколько каналов подходит: $names. Уточните номер или название.")
                state = state.copy(candidates = resolution.candidates)
                publish(context)
                state
            }
            else -> answer(context, "Не поняла команду. Скажите «что умеешь» для списка.")
        }
    }

    @Synchronized
    fun selectCandidate(context: Context, index: Int): VoiceState {
        val chosen = pendingCandidates.getOrNull(index) ?: return current
        pendingCandidates = emptyList()
        return openChannel(context, chosen)
    }

    @Synchronized
    fun reset(context: Context): VoiceState {
        clearPendingClear()
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null
        pendingCandidates = emptyList()
        state = VoiceState(connected = state.connected)
        publish(context)
        return state
    }

    private fun answer(context: Context, text: String): VoiceState =
        switchPhase(context, VoicePhase.ANSWERING, text)

    private fun switchPhase(context: Context, phase: VoicePhase, text: String): VoiceState {
        state = state.copy(
            phase = phase,
            answer = text,
            heard = "",
            candidates = emptyList(),
            updatedAt = System.currentTimeMillis()
        )
        if (phase == VoicePhase.ANSWERING) scheduleClear(context, 9_000L)
        publish(context)
        return state
    }

    private fun openChannel(context: Context, channel: GuideChannel): VoiceState {
        state = state.copy(
            phase = VoicePhase.ANSWERING,
            answer = "Включаю канал ${channel.number}, ${channel.name}.",
            candidates = emptyList(),
            updatedAt = System.currentTimeMillis()
        )
        scheduleClear(context, 5_000L)
        publish(context)
        ChannelCatalog.open(context, channel)
        return state
    }

    private fun publish(context: Context) {
        val snapshot = state
        handler.post {
            listeners.toList().forEach { runCatching { it(snapshot) } }
            val phase = snapshot.phase
            if (phase != VoicePhase.IDLE) {
                val intent = Intent(context, VoiceOverlayActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                runCatching { context.startActivity(intent) }
            }
        }
    }

    private fun scheduleClear(context: Context, delayMs: Long) {
        clearPendingClear()
        val runnable = Runnable {
            if (state.phase != VoicePhase.RADIO && state.phase != VoicePhase.TIMER) {
                state = state.copy(
                    phase = VoicePhase.IDLE,
                    heard = "",
                    answer = "",
                    candidates = emptyList(),
                    updatedAt = System.currentTimeMillis()
                )
                publishIdle(context)
            }
        }
        clearRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun publishIdle(context: Context) {
        handler.post { listeners.toList().forEach { runCatching { it(state) } } }
    }

    private fun clearPendingClear() {
        clearRunnable?.let { handler.removeCallbacks(it) }
        clearRunnable = null
    }

    data class Resolution(val channel: GuideChannel?, val candidates: List<GuideChannel>)

    fun resolveChannel(context: Context, raw: String): Resolution {
        var query = normalize(raw)
        STOP_WORDS.forEach { query = query.replace(Regex("\\b$it\\b"), " ") }
        query = query.replace(Regex("\\s+"), " ").trim()

        // Только «5» / «канал 5» — иначе «Россия 1» мог бы включить канал 1.
        if (query.matches(Regex("\\d{1,2}"))) {
            val channel = ChannelCatalog.byNumber(context, query.toInt())
            if (channel != null) return Resolution(channel, emptyList())
        }
        if (query.isBlank()) return Resolution(null, emptyList())

        val all = ChannelCatalog.all(context)
        val named = all.map { it to normalize(it.name) }

        // Точное название («НТВ +1», «НТВ плюс один») — включаем сразу.
        named.firstOrNull { it.second == query }?.let {
            return Resolution(it.first, emptyList())
        }

        // «НТВ» без уточнения — показываем всё семейство: HD, +1, Право, Стиль…
        val family = named.filter { (_, name) ->
            name.startsWith("$query ") || familyOf(name) == query
        }.map { it.first }
        if (family.size > 1) return Resolution(null, family)
        if (family.size == 1) return Resolution(family.first(), emptyList())

        val tokens = query.split(' ').filter { it.length > 1 }
        if (tokens.isEmpty()) return Resolution(null, emptyList())

        val scored = all.mapNotNull { channel ->
            val name = normalize(channel.name)
            val score = when {
                name == query -> 120
                name.startsWith(query) -> 100
                name.contains(query) -> 80
                tokens.all { name.contains(it) } -> 70 + tokens.size
                tokens.any { name.contains(it) } -> 40 + tokens.count { name.contains(it) }
                else -> 0
            }
            if (score > 0) score to channel else null
        }.sortedByDescending { it.first }

        val best = scored.firstOrNull()?.first ?: 0
        val top = scored.filter { it.first >= best - 5 }.map { it.second }
        return if (top.size == 1) Resolution(top.first(), emptyList())
        else Resolution(null, top)
    }

    private fun normalize(raw: String): String {
        var text = raw.lowercase(Locale.ROOT).replace('ё', 'е')
        text = text.replace("°", "")
        text = text.replace(Regex("[.,!?;:«»\"()]+"), " ")
        NUMBER_WORDS.forEach { (word, digit) ->
            text = text.replace(Regex("\\b$word\\b"), digit)
        }
        text = text.replace(Regex("\\bплюс\\b"), "+")
        text = text.replace(Regex("\\+\\s*(\\d)"), "+\$1")
        text = text.replace(Regex("\\bканал\\b"), " ")
        text = text.replace(Regex("\\bтелеканал\\b"), " ")
        text = text.replace(Regex("^(алиса|окей алиса|привет алиса)\\s+"), "")
        return text.replace(Regex("\\s+"), " ").trim()
    }

    /** Корень названия: «НТВ +1» и «НТВ HD» → «нтв». */
    private fun familyOf(name: String): String =
        name.replace(Regex("\\s*\\+\\s*\\d+"), "")
            .replace(Regex("\\s*hd$"), "")
            .trim()

    private fun parseTimerSeconds(text: String): Long? {
        val match = Regex("(?:таймер|напомни|через).*?(\\d+)\\s*(минут|минуты|минуту|мин|часов|часа|час|ч)\\b")
            .find(text) ?: return null
        val amount = match.groupValues[1].toLongOrNull() ?: return null
        val multiplier = if (match.groupValues[2].startsWith("ч")) 3600L else 60L
        return amount * multiplier
    }

    private fun formatDuration(seconds: Long): String = when {
        seconds >= 3600 && seconds % 3600L == 0L -> "${seconds / 3600} ч"
        seconds >= 3600 -> "${seconds / 3600} ч ${(seconds % 3600) / 60} мин"
        else -> "${seconds / 60} мин"
    }

    private val STOP_WORDS = setOf(
        "включи", "включить", "переключи", "переключить", "покажи", "покажь", "открой",
        "на", "мне", "пожалуйста", "канал", "этот", "тот", "давай", "ну", "и", "а",
        "смотри", "смотреть", "хочу", "желаю", "быстро", "сейчас"
    )

    private val NUMBER_WORDS = mapOf(
        "ноль" to "0", "один" to "1", "одна" to "1", "два" to "2", "две" to "2",
        "три" to "3", "четыре" to "4", "пять" to "5", "шесть" to "6",
        "семь" to "7", "восемь" to "8", "девять" to "9", "десять" to "10"
    )

    const val COMMAND_HELP =
        "Команды: «включи радио», «включи канал 5», «включи НТВ +1», «открой телегид», " +
        "«поставь таймер на 10 минут», «отмени таймер», «ритм 128», «быстрее», «выключи радио», «что умеешь»."
}
