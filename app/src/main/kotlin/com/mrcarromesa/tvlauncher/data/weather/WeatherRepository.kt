package com.mrcarromesa.tvlauncher.data.weather

import com.mrcarromesa.tvlauncher.data.JsonCache
import com.mrcarromesa.tvlauncher.model.WeatherSnapshot
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Estado do clima para a UI (data-model.md "Estados e transições — Clima"). */
sealed interface WeatherState {
  data object Loading : WeatherState
  data class Available(val snapshot: WeatherSnapshot, val age: Duration) : WeatherState
  data class Unavailable(val lastAttemptAt: Instant?) : WeatherState
}

/**
 * Clima: cache `filesDir/weather.json` + política de atualização
 * (contracts/weather-sources.md §3, R7):
 * - ticker de 15 min (ecrã visível): se `age > 60 min`, atualizar;
 * - refresh em curso: não iniciar outro (uma corrotina por fonte);
 * - falha: manter cache; não escrever; sem mensagem de erro técnica;
 * - cache > 24 h: `Unavailable` com a data do último sucesso.
 */
class WeatherRepository(
  private val sources: WeatherSources,
  private val cache: JsonCache,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val refreshMutex = Mutex()

  private val _state = MutableStateFlow<WeatherState>(WeatherState.Loading)
  val state: StateFlow<WeatherState> = _state.asStateFlow()

  init {
    val env = cache.read(WeatherRepository.WEATHER_FILE, WeatherSnapshot.serializer())
    _state.value =
      if (env == null) WeatherState.Loading
      else WeatherState.Available(env.payload, Duration.between(env.payload.fetchedAt, Instant.now()))
  }

  /** Chamar a cada 15 min enquanto o ecrã está visível e ao voltar de uma app. */
  fun tick(now: Instant = Instant.now()) {
    when (val s = _state.value) {
      is WeatherState.Available ->
        if (Duration.between(s.snapshot.fetchedAt, now) > REFRESH_AFTER) refresh(now)
      is WeatherState.Unavailable -> refresh(now)
      // Sem cache: tentar obter no arranque (Loading → Available/Unavailable).
      WeatherState.Loading -> refresh(now)
    }
  }

  /** Refresh em segundo plano; nunca bloqueia a UI nem inicia outro em paralelo. */
  fun refresh(now: Instant = Instant.now()) {
    scope.launch {
      refreshMutex.withLock {
        val loc =
          try {
            sources.locate()
          } catch (e: Exception) {
            LocationResult.Unavailable
          }
        when (loc) {
          is LocationResult.Unavailable -> keepCacheOrUnavailable(now)
          is LocationResult.Located -> {
            val localNow = LocalDateTime.ofInstant(now, ZoneId.systemDefault())
            val forecast =
              try {
                sources.forecast(loc.place, localNow)
              } catch (e: Exception) {
                ForecastResult.ParseFailure
              }
            when (forecast) {
              is ForecastResult.Ok -> {
                cache.write(
                  WeatherRepository.WEATHER_FILE,
                  JsonCache.envelope(forecast.snapshot.fetchedAt, forecast.snapshot),
                  WeatherSnapshot.serializer(),
                )
                _state.value = WeatherState.Available(forecast.snapshot, Duration.ZERO)
              }
              is ForecastResult.ParseFailure -> keepCacheOrUnavailable(now)
            }
          }
        }
      }
    }
  }

  private fun keepCacheOrUnavailable(now: Instant) {
    val env = cache.read(WeatherRepository.WEATHER_FILE, WeatherSnapshot.serializer())
    _state.value =
      if (env != null) WeatherState.Available(env.payload, Duration.between(env.payload.fetchedAt, now))
      else WeatherState.Unavailable(now)
  }

  companion object {
    const val WEATHER_FILE = "weather.json"
    val REFRESH_AFTER: Duration = Duration.ofMinutes(60)
  }
}
