package com.mrcarromesa.tvlauncher.data

import java.io.File
import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Envelope de cache JSON: `{ "schema": 1, "fetchedAt": "<ISO-8601>", "payload": {…} }`.
 *
 * Invariantes (data-model.md):
 * - `schema != 1` ⇒ o ficheiro é ignorado na leitura e **nunca apagado**.
 * - Escrita atómica: `*.tmp` + `renameTo`; um ficheiro truncado nunca é lido como válido.
 */
@Serializable
data class CacheEnvelope<T>(
  val schema: Int,
  val fetchedAt: String,
  val payload: T,
) {
  companion object {
    const val SCHEMA = 1
    val json: Json = Json { ignoreUnknownKeys = true }
  }
}

class JsonCache(private val dir: File) {

  /** Lê o envelope; `null` se não existir, for inválido ou tiver `schema != 1`. */
  fun <T> read(fileName: String, payloadSerializer: KSerializer<T>): CacheEnvelope<T>? {
    val file = File(dir, fileName)
    if (!file.exists()) return null
    return try {
      val envelope = CacheEnvelope.json.decodeFromString(
        CacheEnvelope.serializer(payloadSerializer),
        file.readText(),
      )
      if (envelope.schema == CacheEnvelope.SCHEMA) envelope else null
    } catch (e: Exception) {
      null
    }
  }

  /** Escrita atómica: escreve `*.tmp` e faz `renameTo` por cima do destino. */
  fun <T> write(fileName: String, envelope: CacheEnvelope<T>, payloadSerializer: KSerializer<T>): Boolean {
    return try {
      val tmp = File(dir, "$fileName.tmp")
      tmp.writeText(CacheEnvelope.json.encodeToString(CacheEnvelope.serializer(payloadSerializer), envelope))
      tmp.renameTo(File(dir, fileName))
    } catch (e: Exception) {
      false
    }
  }

  companion object {
    fun <T> envelope(fetchedAt: Instant, payload: T): CacheEnvelope<T> =
      CacheEnvelope(schema = CacheEnvelope.SCHEMA, fetchedAt = fetchedAt.toString(), payload = payload)
  }
}
