package ru.auroratv.launcher.weather

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import ru.auroratv.launcher.core.Prefs
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

data class Weather(
    val tempC: Int,
    val code: Int,
    val city: String,
    val fetchedAt: Long
) {
    fun icon(): String = when (code) {
        0 -> "☀"
        1 -> "🌤"
        2 -> "⛅"
        3 -> "☁"
        45, 48 -> "🌫"
        51, 53, 55, 56, 57 -> "🌦"
        61, 63, 65, 66, 67 -> "🌧"
        71, 73, 75, 77 -> "❄"
        80, 81, 82 -> "🌧"
        85, 86 -> "❄"
        95, 96, 99 -> "⛈"
        else -> "·"
    }

    fun label(): String = when (code) {
        0 -> "Ясно"
        1 -> "Почти ясно"
        2 -> "Переменная облачность"
        3 -> "Пасмурно"
        45, 48 -> "Туман"
        51, 53, 55, 56, 57 -> "Морось"
        61, 63, 65, 66, 67 -> "Дождь"
        71, 73, 75, 77 -> "Снег"
        80, 81, 82 -> "Ливень"
        85, 86 -> "Снегопад"
        95, 96, 99 -> "Гроза"
        else -> ""
    }
}

data class City(
    val name: String,
    val lat: Double,
    val lon: Double
)

object WeatherRepository {

    private const val TAG = "AuroraWeather"
    private const val CACHE_MS = 30 * 60 * 1000L

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "aurora-weather").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    fun load(prefs: Prefs, force: Boolean = false, callback: (Weather?) -> Unit) {
        if (!prefs.weatherEnabled) {
            callback(null)
            return
        }

        val cached = prefs.readWeather()
        if (cached != null) callback(cached)

        val isFresh = cached != null && System.currentTimeMillis() - cached.fetchedAt < CACHE_MS
        if (!force && isFresh) return

        val city = City(prefs.cityName, prefs.cityLat.toDouble(), prefs.cityLon.toDouble())
        executor.execute {
            val fresh = fetch(city)
            if (fresh != null) {
                prefs.saveWeather(fresh)
                main.post { callback(fresh) }
            } else if (cached == null) {
                main.post { callback(null) }
            }
        }
    }

    private fun fetch(city: City): Weather? =
        fetchMetNo(city) ?: fetchOpenMeteo(city)

    /**
     * MET Norway is the primary source: it is reachable from Russia more reliably than
     * Open-Meteo and still requires no API key or device identifier.
     */
    private fun fetchMetNo(city: City): Weather? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(
                "https://api.met.no/weatherapi/locationforecast/2.0/compact" +
                    "?lat=${city.lat}&lon=${city.lon}"
            )
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                useCaches = false
                setRequestProperty(
                    "User-Agent",
                    "AuroraTV/1.0 (Android TV launcher; privacy-first weather)"
                )
            }
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val series = JSONObject(text)
                .getJSONObject("properties")
                .getJSONArray("timeseries")
            if (series.length() == 0) return null

            val data = series.getJSONObject(0).getJSONObject("data")
            val details = data.getJSONObject("instant").getJSONObject("details")
            val temp = details.getDouble("air_temperature").roundToInt()
            val summary = data.optJSONObject("next_1_hours")?.optJSONObject("summary")
                ?: data.optJSONObject("next_6_hours")?.optJSONObject("summary")
                ?: data.optJSONObject("next_12_hours")?.optJSONObject("summary")
            val symbol = summary?.optString("symbol_code").orEmpty()
            Weather(temp, mapMetNoSymbol(symbol), city.name, System.currentTimeMillis())
        } catch (error: Throwable) {
            Log.w(TAG, "MET Norway failed: ${error.javaClass.simpleName}: ${error.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun mapMetNoSymbol(symbol: String): Int {
        val value = symbol.lowercase(Locale.US)
        return when {
            value.contains("thunder") -> 95
            value.contains("heavyrain") -> 65
            value.contains("lightrain") -> 61
            value.contains("rain") -> 63
            value.contains("heavysnow") -> 75
            value.contains("lightsnow") -> 71
            value.contains("snow") -> 73
            value.contains("sleet") -> 66
            value.contains("fog") -> 45
            value.contains("partlycloudy") -> 2
            value.contains("cloudy") -> 3
            value.contains("fair") -> 1
            value.contains("clearsky") -> 0
            else -> -1
        }
    }

    private fun fetchOpenMeteo(city: City): Weather? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=${city.lat}&longitude=${city.lon}" +
                    "&current=temperature_2m,weather_code&timezone=auto"
            )
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                useCaches = false
                setRequestProperty("User-Agent", "AuroraTV/1.0 (Android TV launcher)")
            }
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val current = JSONObject(text).getJSONObject("current")
            val temp = current.getDouble("temperature_2m").roundToInt()
            val code = current.optInt("weather_code", -1)
            Weather(temp, code, city.name, System.currentTimeMillis())
        } catch (error: Throwable) {
            Log.w(TAG, "Open-Meteo failed: ${error.javaClass.simpleName}: ${error.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    fun geocode(query: String, callback: (City?) -> Unit) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            callback(null)
            return
        }
        executor.execute {
            var connection: HttpURLConnection? = null
            val city = try {
                val encoded = URLEncoder.encode(trimmed, "UTF-8")
                val url = URL(
                    "https://geocoding-api.open-meteo.com/v1/search" +
                        "?name=$encoded&count=1&language=ru&format=json"
                )
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 7000
                    readTimeout = 7000
                    requestMethod = "GET"
                    useCaches = false
                    setRequestProperty("User-Agent", "AuroraTV/1.0 (Android TV launcher)")
                }
                val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val results = JSONObject(text).optJSONArray("results")
                if (results == null || results.length() == 0) {
                    null
                } else {
                    val item = results.getJSONObject(0)
                    val name = item.optString("name")
                    val admin = item.optString("admin1")
                    val country = item.optString("country")
                    val display = listOf(name, admin, country)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(", ")
                    City(
                        display.ifBlank { trimmed },
                        item.getDouble("latitude"),
                        item.getDouble("longitude")
                    )
                }
            } catch (error: Throwable) {
                Log.w(TAG, "Geocoding failed: ${error.javaClass.simpleName}: ${error.message}")
                null
            } finally {
                connection?.disconnect()
            }
            main.post { callback(city) }
        }
    }
}
