package com.mrcarromesa.tvlauncher.model

import com.mrcarromesa.tvlauncher.data.InstantMillisSerializer
import com.mrcarromesa.tvlauncher.data.LocalDateTimeIsoSerializer
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.serialization.Serializable

/** Localização dada por `ipwho.is` (contracts/weather-sources.md §1). */
@Serializable
data class Place(
  val city: String,
  val countryCode: String,
  val latitude: Double,
  val longitude: Double,
  val timeZoneId: String,
)

/**
 * Previsão atual de uma localização (data-model.md).
 * Invariantes:
 * - `hourly` está sempre filtrada para `localTime >= agora` e ordenada; mostram-se 5.
 * - `maxC >= minC`.
 */
@Serializable
data class WeatherSnapshot(
  val place: Place,
  val currentTempC: Double,
  val currentCode: Int,
  val maxC: Double,
  val minC: Double,
  val hourly: List<HourlyPoint>,
  @Serializable(with = InstantMillisSerializer::class)
  val fetchedAt: Instant,
) {
  init {
    require(hourly == hourly.sortedBy { it.localTime }) { "hourly must be sorted by localTime" }
    require(maxC >= minC) { "maxC must be >= minC" }
  }
}

@Serializable
data class HourlyPoint(
  @Serializable(with = LocalDateTimeIsoSerializer::class)
  val localTime: LocalDateTime,
  val tempC: Double,
  val code: Int,
)

/** Condição WMO traduzida (data-model.md); `labelPt` "—" com código desconhecido. */
data class WeatherCondition(val code: Int, val labelPt: String)
