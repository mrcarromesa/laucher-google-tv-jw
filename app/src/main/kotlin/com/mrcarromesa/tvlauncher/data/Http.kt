package com.mrcarromesa.tvlauncher.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Resultado de um GET; um erro de rede nunca chega como exceção ao chamador. */
sealed interface FetchResult {
  data class Ok(val body: String) : FetchResult
  data class Error(val reason: Reason) : FetchResult

  enum class Reason {
    NETWORK, // sem ligação, timeout, TLS, DNS…
    HTTP, // resposta com status ≠ 2xx
  }
}

class Http {

  private val client = OkHttpClient.Builder()
    .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    .build()

  /** GET com User-Agent fixo e timeout total por chamada ([timeoutMs]). */
  suspend fun get(
    url: String,
    timeoutMs: Long,
    accept: String = "application/json",
  ): FetchResult = withContext(Dispatchers.IO) {
    val request = Request.Builder()
      .url(url)
      .header("User-Agent", USER_AGENT)
      .header("Accept", accept)
      .get()
      .build()
    val call = client.newCall(request)
    call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
    try {
      call.execute().use { response ->
        if (response.isSuccessful) {
          FetchResult.Ok(response.body?.string().orEmpty())
        } else {
          FetchResult.Error(FetchResult.Reason.HTTP)
        }
      }
    } catch (e: IOException) {
      FetchResult.Error(FetchResult.Reason.NETWORK)
    }
  }

  companion object {
    const val USER_AGENT = "TvLauncher/1.0 (Android TV)"
    const val CONNECT_TIMEOUT_MS = 3000L
  }
}
