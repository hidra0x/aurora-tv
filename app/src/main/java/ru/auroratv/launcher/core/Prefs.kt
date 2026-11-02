package ru.auroratv.launcher.core

import android.content.Context
import ru.auroratv.launcher.weather.Cities
import ru.auroratv.launcher.weather.City
import ru.auroratv.launcher.weather.Weather
import java.util.TimeZone

class Prefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("aurora_prefs", Context.MODE_PRIVATE)
    private val defaultCity: City by lazy { Cities.forTimeZone(TimeZone.getDefault().id) }

    var favorites: List<String>
        get() = sp.getString(KEY_FAVORITES, "").orEmpty()
            .split('|')
            .filter { it.isNotBlank() }
        set(value) {
            sp.edit().putString(KEY_FAVORITES, value.distinct().joinToString("|")).apply()
        }

    fun isFavorite(packageName: String): Boolean = favorites.contains(packageName)

    fun toggleFavorite(packageName: String): Boolean {
        val current = favorites.toMutableList()
        val added = if (current.contains(packageName)) {
            current.remove(packageName)
            false
        } else {
            current.add(packageName)
            true
        }
        favorites = current
        return added
    }

    var weatherEnabled: Boolean
        get() = sp.getBoolean(KEY_WEATHER_ENABLED, true)
        set(value) {
            sp.edit().putBoolean(KEY_WEATHER_ENABLED, value).apply()
        }

    var cityName: String
        get() = sp.getString(KEY_CITY_NAME, defaultCity.name) ?: defaultCity.name
        set(value) {
            sp.edit().putString(KEY_CITY_NAME, value).apply()
        }

    var cityLat: Float
        get() = sp.getFloat(KEY_CITY_LAT, defaultCity.lat.toFloat())
        set(value) {
            sp.edit().putFloat(KEY_CITY_LAT, value).apply()
        }

    var cityLon: Float
        get() = sp.getFloat(KEY_CITY_LON, defaultCity.lon.toFloat())
        set(value) {
            sp.edit().putFloat(KEY_CITY_LON, value).apply()
        }

    var animations: Boolean
        get() = sp.getBoolean(KEY_ANIMATIONS, true)
        set(value) {
            sp.edit().putBoolean(KEY_ANIMATIONS, value).apply()
        }

    var hints: Boolean
        get() = sp.getBoolean(KEY_HINTS, true)
        set(value) {
            sp.edit().putBoolean(KEY_HINTS, value).apply()
        }

    var showSystemApps: Boolean
        get() = sp.getBoolean(KEY_SHOW_SYSTEM_APPS, false)
        set(value) {
            sp.edit().putBoolean(KEY_SHOW_SYSTEM_APPS, value).apply()
        }

    var didAutoPin: Boolean
        get() = sp.getBoolean(KEY_DID_AUTOPIN, false)
        set(value) {
            sp.edit().putBoolean(KEY_DID_AUTOPIN, value).apply()
        }

    var tvPackage: String
        get() = sp.getString(KEY_TV_PACKAGE, "").orEmpty()
        set(value) {
            sp.edit().putString(KEY_TV_PACKAGE, value).apply()
        }

    var weatherTemp: Int
        get() = sp.getInt(KEY_WEATHER_TEMP, Int.MIN_VALUE)
        set(value) {
            sp.edit().putInt(KEY_WEATHER_TEMP, value).apply()
        }

    var weatherCode: Int
        get() = sp.getInt(KEY_WEATHER_CODE, -1)
        set(value) {
            sp.edit().putInt(KEY_WEATHER_CODE, value).apply()
        }

    var weatherTime: Long
        get() = sp.getLong(KEY_WEATHER_TIME, 0L)
        set(value) {
            sp.edit().putLong(KEY_WEATHER_TIME, value).apply()
        }

    var weatherCity: String
        get() = sp.getString(KEY_WEATHER_CITY, "").orEmpty()
        set(value) {
            sp.edit().putString(KEY_WEATHER_CITY, value).apply()
        }

    fun readWeather(): Weather? {
        if (weatherCode < 0 || weatherTemp == Int.MIN_VALUE) return null
        if (weatherCity != cityName) return null
        return Weather(weatherTemp, weatherCode, weatherCity, weatherTime)
    }

    fun saveWeather(weather: Weather) {
        weatherTemp = weather.tempC
        weatherCode = weather.code
        weatherCity = weather.city
        weatherTime = weather.fetchedAt
    }

    fun clearWeather() {
        sp.edit()
            .remove(KEY_WEATHER_TEMP)
            .remove(KEY_WEATHER_CODE)
            .remove(KEY_WEATHER_TIME)
            .remove(KEY_WEATHER_CITY)
            .apply()
    }

    var mediaFolders: List<String>
        get() = sp.getString(KEY_MEDIA_FOLDERS, "").orEmpty()
            .split('\n')
            .filter { it.isNotBlank() }
        set(value) {
            sp.edit().putString(KEY_MEDIA_FOLDERS, value.distinct().joinToString("\n")).apply()
        }

    fun addMediaFolder(uri: String) {
        mediaFolders = mediaFolders + uri
    }

    fun removeMediaFolder(uri: String) {
        mediaFolders = mediaFolders.filterNot { it == uri }
    }

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_WEATHER_ENABLED = "weather_enabled"
        const val KEY_CITY_NAME = "city_name"
        const val KEY_CITY_LAT = "city_lat"
        const val KEY_CITY_LON = "city_lon"
        const val KEY_ANIMATIONS = "animations"
        const val KEY_HINTS = "hints"
        const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"
        const val KEY_DID_AUTOPIN = "did_autopin"
        const val KEY_TV_PACKAGE = "tv_package"
        const val KEY_WEATHER_TEMP = "weather_temp"
        const val KEY_WEATHER_CODE = "weather_code"
        const val KEY_WEATHER_TIME = "weather_time"
        const val KEY_WEATHER_CITY = "weather_city"
        const val KEY_MEDIA_FOLDERS = "media_folders"
    }
}
