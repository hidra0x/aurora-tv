package ru.auroratv.launcher.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Collections
import java.util.Random

data class CinemaFilm(
    val id: String,
    val title: String,
    val year: Int,
    val description: String,
    val durationSeconds: Int,
    val url: String,
    val page: String,
    val genres: List<String>,
    val rating: String
)

data class CinemaChannel(
    val number: Int,
    val id: String,
    val name: String,
    val description: String,
    val filmIds: List<String>,
    val color: String
)

data class CinemaSession(
    val channel: CinemaChannel,
    val current: CinemaFilm,
    val next: CinemaFilm,
    val currentIndex: Int,
    val order: List<CinemaFilm>
)

object CinemaRepository {

    private data class Catalog(
        val channels: List<CinemaChannel>,
        val films: Map<String, CinemaFilm>
    )

    private var cached: Catalog? = null

    private fun catalog(context: Context): Catalog {
        cached?.let { return it }
        val channelsJson = context.assets.open("cinema/channels.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val filmsJson = context.assets.open("cinema/films.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

        val films = LinkedHashMap<String, CinemaFilm>()
        val filmArray = JSONArray(filmsJson)
        for (index in 0 until filmArray.length()) {
            val item = filmArray.getJSONObject(index)
            val genres = item.optJSONArray("genres")?.let { array ->
                (0 until array.length()).map { array.optString(it) }
            } ?: emptyList()
            val film = CinemaFilm(
                id = item.getString("id"),
                title = item.optString("title", item.getString("id")),
                year = item.optInt("year", 0),
                description = item.optString("description", ""),
                durationSeconds = item.optInt("duration", 0),
                url = item.getString("url"),
                page = item.optString("page", ""),
                genres = genres,
                rating = item.optString("rating", "12+")
            )
            films[film.id] = film
        }

        val channels = ArrayList<CinemaChannel>()
        val channelArray = JSONObject(channelsJson).getJSONArray("channels")
        for (index in 0 until channelArray.length()) {
            val item = channelArray.getJSONObject(index)
            val ids = item.optJSONArray("films")?.let { array ->
                (0 until array.length()).map { array.optString(it) }
                    .filter { films.containsKey(it) }
            } ?: emptyList()
            if (ids.isEmpty()) continue
            channels.add(
                CinemaChannel(
                    number = item.getInt("number"),
                    id = item.getString("id"),
                    name = item.optString("name", item.getString("id")),
                    description = item.optString("description", ""),
                    filmIds = ids,
                    color = item.optString("color", "#8BD5FF")
                )
            )
        }
        return Catalog(channels.sortedBy { it.number }, films).also { cached = it }
    }

    fun channels(context: Context): List<CinemaChannel> = catalog(context).channels

    fun channelFor(context: Context, number: Int): CinemaChannel? =
        catalog(context).channels.firstOrNull { it.number == number }

    fun film(context: Context, id: String): CinemaFilm? = catalog(context).films[id]

    fun sessionFor(context: Context, channel: CinemaChannel, dayOffset: Int = 0): CinemaSession? {
        val cat = catalog(context)
        val ids = channel.filmIds
        if (ids.isEmpty()) return null
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
        }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)

        // Новый порядок каждый месяц: даже при 5 фильмах нет одинакового
        // «поезда» из месяца в месяц.
        val shuffled = ids.toMutableList()
        val seed = channel.number * 1_000_003L + year * 100L + month
        Collections.shuffle(shuffled, Random(seed))

        // Каждый сеанс — отдельная позиция в каталоге, поэтому вечерний
        // фильм не повторяется на следующее утро.
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val slotIndex = Math.floorMod((year * 366 + dayOfYear - 1) * 2, shuffled.size)
        val currentIndex = Math.floorMod(slotIndex, shuffled.size)
        val order = shuffled.mapNotNull { cat.films[it] }
        if (order.isEmpty()) return null
        val current = order[currentIndex]
        val next = order[(currentIndex + 1) % order.size]
        return CinemaSession(channel, current, next, currentIndex, order)
    }

    fun nextFilm(context: Context, channel: CinemaChannel, afterIndex: Int): CinemaFilm? {
        val cat = catalog(context)
        if (channel.filmIds.isEmpty()) return null
        val index = (afterIndex + 1) % channel.filmIds.size
        return cat.films[channel.filmIds[index]]
    }

}
