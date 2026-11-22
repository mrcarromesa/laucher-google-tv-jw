package com.mrcarromesa.tvlauncher.data.weather

import com.mrcarromesa.tvlauncher.data.FetchResult
import com.mrcarromesa.tvlauncher.data.Http
import com.mrcarromesa.tvlauncher.model.HourlyPoint
import com.mrcarromesa.tvlauncher.model.Place
import com.mrcarromesa.tvlauncher.model.WeatherSnapshot
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Resultado da localização: local conhecido ou indisponível (sem erro técnico). */
sealed interface LocationResult {
  data class Located(val place: Place) : LocationResult
  data object Unavailable : LocationResult
}

/** Resultado da previsão. `ParseFailure` nunca escreve cache nem altera `fetchedAt`. */
sealed interface ForecastResult {
  data class Ok(val snapshot: WeatherSnapshot) : ForecastResult
  data object ParseFailure : ForecastResult
}

/**
 * Fontes de clima (contracts/weather-sources.md):
 * - localização: `https://ipwho.is/` (timeout 5 s), cache `place.json` (sem envelope,
 *   renovada após 30 dias);
 * - previsão: Open-Meteo (timeout 8 s), parsing com as regras do §2.
 */
class WeatherSources(
  private val http: Http,
  private val filesDir: File,
) {
  private val json = Json { ignoreUnknownKeys = true }

  /**
   * Localização a usar. Devolve o cache `place.json` se existir e tiver ≤ 30 dias;
   * senão pede `ipwho.is` (sucesso ⇒ grava), e em falha de rede usa o cache se existir.
   */
  suspend fun locate(): LocationResult {
    val cached = readPlace()
    if (cached != null && !cached.isStale) {
      return LocationResult.Located(cached.place)
    }
    return when (val result = http.get(IPWHOIS_URL, timeoutMs = IPWHOIS_TIMEOUT_MS)) {
      is FetchResult.Ok -> {
        val place = parsePlace(result.body)
        if (place != null) {
          writePlace(place)
          LocationResult.Located(place)
        } else {
          cached?.let { LocationResult.Located(it.place) } ?: LocationResult.Unavailable
        }
      }
      is FetchResult.Error ->
        cached?.let { LocationResult.Located(it.place) } ?: LocationResult.Unavailable
    }
  }

  /** Previsão para as coordenadas dadas, filtrada a partir de [now]. */
  suspend fun forecast(place: Place, now: LocalDateTime): ForecastResult {
    val url =
      OPEN_METEO_URL +
        "?latitude=${place.latitude}&longitude=${place.longitude}" +
        "&current=temperature_2m,weather_code" +
        "&daily=temperature_2m_max,temperature_2m_min" +
        "&hourly=temperature_2m,weather_code" +
        "&timezone=auto&forecast_days=2"
    return when (val result = http.get(url, timeoutMs = OPEN_METEO_TIMEOUT_MS)) {
      is FetchResult.Ok -> parseForecast(result.body, place, now)?.let { ForecastResult.Ok(it) }
        ?: ForecastResult.ParseFailure
      is FetchResult.Error -> ForecastResult.ParseFailure
    }
  }

  // --- parsing (testável com fixtures; regras de contracts/weather-sources.md §1–2) ---

  fun parsePlace(body: String): Place? {
    val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
    val success = root["success"]?.jsonPrimitive?.contentOrNull == "true"
    val city = root["city"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
    val lat = root["latitude"]?.jsonPrimitive?.doubleOrNull
    val lon = root["longitude"]?.jsonPrimitive?.doubleOrNull
    val timezoneId = root["timezone"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
    if (!success || lat == null || lon == null || city.isEmpty() || timezoneId.isNullOrEmpty()) return null
    return Place(
      city = city,
      countryCode = root["country_code"]?.jsonPrimitive?.contentOrNull.orEmpty(),
      latitude = lat,
      longitude = lon,
      timeZoneId = timezoneId,
    )
  }

  fun parseForecast(body: String, place: Place, now: LocalDateTime): WeatherSnapshot? {
    val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
    val current = root["current"]?.jsonObject
    val temperatureNow = current?.get("temperature_2m")?.jsonPrimitive?.doubleOrNull
    val codeNow = current?.get("weather_code")?.jsonPrimitive?.intOrNull
    val daily = root["daily"]?.jsonObject
    val maxC = daily?.get("temperature_2m_max")?.jsonArray?.firstOrNull()?.jsonPrimitive?.doubleOrNull
    val minC = daily?.get("temperature_2m_min")?.jsonArray?.firstOrNull()?.jsonPrimitive?.doubleOrNull
    val hourly = root["hourly"]?.jsonObject
    val hourlyTimes = hourly?.get("time")?.jsonArray
    val hourlyTemps = hourly?.get("temperature_2m")?.jsonArray
    val hourlyCodes = hourly?.get("weather_code")?.jsonArray

    // "hourly tem de ter os três arrays com o mesmo comprimento; senão ParseFailure"
    if (hourlyTimes == null || hourlyTemps == null || hourlyCodes == null ||
      !(hourlyTimes.size == hourlyTemps.size && hourlyTemps.size == hourlyCodes.size)
    ) return null
    if (temperatureNow == null || codeNow == null || maxC == null || minC == null || maxC < minC) return null

    val points = mutableListOf<HourlyPoint>()
    for (i in hourlyTimes.indices) {
      val local = runCatching {
        LocalDateTime.parse(hourlyTimes[i].jsonPrimitive.content)
      }.getOrNull() ?: return null
      val temp = hourlyTemps[i].jsonPrimitive.doubleOrNull ?: return null
      val code = hourlyCodes[i].jsonPrimitive.intOrNull ?: return null
      points += HourlyPoint(localTime = local, tempC = temp, code = code)
    }

    // "Pontos com localTime >= agora (fuso do dispositivo), ordenados, primeiros 5"
    val future = points.filter { !it.localTime.isBefore(now) }.sortedBy { it.localTime }
    if (future.size < 5) return null

    return WeatherSnapshot(
      place = place,
      currentTempC = temperatureNow,
      currentCode = codeNow,
      maxC = maxC,
      minC = minC,
      hourly = future.take(5),
      fetchedAt = Instant.now(),
    )
  }

  // --- place.json (sem envelope; renovado após 30 dias) ---

  private fun readPlace(): CachedPlace? =
    runCatching {
      val file = File(filesDir, PLACE_FILE)
      if (!file.exists()) {
        null
      } else {
        val place = json.decodeFromString(Place.serializer(), file.readText())
        CachedPlace(place, Instant.ofEpochMilli(file.lastModified()).atZone(java.time.ZoneId.systemDefault()).toLocalDate())
      }
    }.getOrNull()

  private fun writePlace(place: Place) {
    runCatching {
      val tmp = File(filesDir, "$PLACE_FILE.tmp")
      tmp.writeText(json.encodeToString(Place.serializer(), place))
      tmp.renameTo(File(filesDir, PLACE_FILE))
    }
  }

  private data class CachedPlace(val place: Place, val savedOn: LocalDate) {
    val isStale: Boolean
      get() = java.time.temporal.ChronoUnit.DAYS.between(savedOn, LocalDate.now()) > PLACE_MAX_AGE_DAYS
  }

  companion object {
    const val IPWHOIS_URL = "https://ipwho.is/"
    const val OPEN_METEO_URL = "https://api.open-meteo.com/v1/forecast"
    const val IPWHOIS_TIMEOUT_MS = 5000L
    const val OPEN_METEO_TIMEOUT_MS = 8000L
    const val PLACE_FILE = "place.json"
    const val PLACE_MAX_AGE_DAYS = 30L
  }
}
