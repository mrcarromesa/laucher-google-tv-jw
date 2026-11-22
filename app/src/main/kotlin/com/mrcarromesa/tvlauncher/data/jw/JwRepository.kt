package com.mrcarromesa.tvlauncher.data.jw

import com.mrcarromesa.tvlauncher.data.JsonCache
import com.mrcarromesa.tvlauncher.model.JwSnapshot
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Estado do painel JW.org para a UI (data-model.md "Estados e transições — JW.org"). */
sealed interface JwState {
  data object Loading : JwState
  data class Available(val snapshot: JwSnapshot, val age: Duration) : JwState
  data class Unavailable(val lastAttemptAt: Instant?) : JwState
}

/**
 * Conteúdo do jw.org: cache `filesDir/jw.json` + política de atualização
 * (contracts/jw-sources.md §7, R7):
 * - snapshot novo funde-se campo a campo com o anterior: uma fonte falhada mantém
 *   o valor antigo desse campo;
 * - atualizar no arranque/volta ao ecrã e a cada 6 h enquanto visível; o regresso ao
 *   ecrã faz refresh completo (novidades, texto do dia, vídeos); à meia-noite local
 *   um refresh completo renova o texto do dia;
 * - falha de rede/HTTP≠200/parsing ⇒ `ParseFailure`, sem escrever cache nem
 *   `fetchedAt`;
 * - cache > 24 h ⇒ painel `Unavailable` com a data do último sucesso.
 */
class JwRepository(
  private val sources: JwSources,
  private val cache: JsonCache,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val refreshMutex = Mutex()

  private val _state = MutableStateFlow<JwState>(JwState.Loading)
  val state: StateFlow<JwState> = _state.asStateFlow()

  init {
    val env = cache.read(JwRepository.JW_FILE, JwSnapshot.serializer())
    _state.value =
      if (env == null) JwState.Loading
      else JwState.Available(env.payload, Duration.between(env.payload.fetchedAt, Instant.now()))
  }

  /** Chamar no arranque/volta ao ecrã e a cada 6 h enquanto visível. */
  fun tick(now: Instant = Instant.now()) {
    when (val s = _state.value) {
      is JwState.Available ->
        if (Duration.between(s.snapshot.fetchedAt, now) > REFRESH_AFTER) refresh(now)
      is JwState.Unavailable -> refresh(now)
      // Sem cache: tentar obter no arranque (Loading → Available/Unavailable).
      JwState.Loading -> refresh(now)
    }
  }

  fun refresh(now: Instant = Instant.now()) {
    scope.launch {
      refreshMutex.withLock {
        val previous = _state.value
        val previousSnapshot = (previous as? JwState.Available)?.snapshot ?: readCache()

        val dailyText = runCatching { sources.fetchDailyText() }.getOrDefault(ParseResult.ParseFailure)
        val broadcasting = runCatching { sources.fetchBroadcasting() }.getOrDefault(ParseResult.ParseFailure)
        val governingBody = runCatching { sources.fetchGoverningBody() }.getOrDefault(ParseResult.ParseFailure)

        // Falha total (nomeadamente sem rede): mantém o último snapshot como está.
        val anySuccess =
          dailyText is ParseResult.Ok || broadcasting is ParseResult.Ok || governingBody is ParseResult.Ok
        if (!anySuccess) {
          if (previousSnapshot != null) {
            _state.value = JwState.Available(previousSnapshot, Duration.between(previousSnapshot.fetchedAt, now))
          } else {
            _state.value = JwState.Unavailable(now)
          }
          return@withLock
        }

        // Fusão campo a campo: uma fonte falhada mantém o valor antigo desse campo.
        val merged =
          JwSnapshot(
            dailyText = (dailyText as? ParseResult.Ok)?.value ?: previousSnapshot?.dailyText,
            broadcasting = (broadcasting as? ParseResult.Ok)?.value ?: previousSnapshot?.broadcasting,
            governingBody = (governingBody as? ParseResult.Ok)?.value ?: previousSnapshot?.governingBody,
            fetchedAt = now,
          )
        cache.write(JwRepository.JW_FILE, JsonCache.envelope(now, merged), JwSnapshot.serializer())
        _state.value = JwState.Available(merged, Duration.ZERO)
      }
    }
  }

  private fun readCache(): JwSnapshot? =
    cache.read(JwRepository.JW_FILE, JwSnapshot.serializer())?.payload

  companion object {
    const val JW_FILE = "jw.json"
    val REFRESH_AFTER: Duration = Duration.ofHours(6)
  }
}
