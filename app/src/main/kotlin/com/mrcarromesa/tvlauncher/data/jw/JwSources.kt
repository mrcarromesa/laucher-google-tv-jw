package com.mrcarromesa.tvlauncher.data.jw

import com.mrcarromesa.tvlauncher.data.FetchResult
import com.mrcarromesa.tvlauncher.data.Http
import com.mrcarromesa.tvlauncher.model.JwDailyText
import com.mrcarromesa.tvlauncher.model.JwTextEntry
import com.mrcarromesa.tvlauncher.model.JwVideoEntry
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Resultado de um parser: conteúdo válido ou `ParseFailure` (nunca se escreve cache). */
sealed interface ParseResult<out T> {
  data class Ok<T>(val value: T) : ParseResult<T>
  data object ParseFailure : ParseResult<Nothing>
}

/**
 * Quatro fontes do jw.org (contracts/jw-sources.md, R3):
 * novidades e texto do dia em HTML; Broadcasting e Boletim pelo mediator API.
 * Cada parser valida os campos mínimos; em falha devolve `ParseFailure`
 * (o repositório mantém o cache e a data anteriores).
 */
class JwSources(private val http: Http) {

  private val json = Json { ignoreUnknownKeys = true }

  // --- fontes (HTTP) ---

  suspend fun fetchWhatsNew(): ParseResult<JwTextEntry> =
    parseFrom(getText(WHATSNEW_URL)) { parseWhatsNew(it) }

  suspend fun fetchDailyText(): ParseResult<JwDailyText> =
    parseFrom(getText(WOL_DAILY_URL)) { parseDailyText(it) }

  suspend fun fetchBroadcasting(): ParseResult<JwVideoEntry> =
    parseFrom(getText(LATEST_VIDEOS_URL, "application/json")) { parseLatestVideos(it) }

  suspend fun fetchGoverningBody(): ParseResult<JwVideoEntry> =
    parseFrom(getText(STUDIO_NEWS_URL, "application/json")) { parseStudioNews(it) }

  // --- parser HTML: novidades (contracts/jw-sources.md §1) ---

  fun parseWhatsNew(body: String): ParseResult<JwTextEntry> {
    val blockPattern = Regex("""(?s)<div class="([^"]*synopsis[^"]*docId-\d+[^"]*)">(.*?)(?=<div class="[^"]*synopsis|\z)""")
    for (match in blockPattern.findAll(body)) {
      val block = match.groupValues[2]
      val pubDate = Regex("""(?s)<p class="meta pubDate">\s*(\d{4}-\d{2}-\d{2})\s*</p>""").find(block)?.groupValues?.get(1)
      val link = Regex("""(?s)<h3>\s*<a href="([^"]+)"[^>]*>(.*?)</a>\s*</h3>""").find(block)
      if (pubDate == null || link == null) continue
      val href = link.groupValues[1]
      val resolved = resolveJwOrg(href) ?: continue
      val title = stripTags(link.groupValues[2]).trim()
      val imageUrl = Regex("""data-img-size-md='([^']+)'""").find(block)?.groupValues?.get(1)
      if (title.isEmpty()) continue
      return ParseResult.Ok(
        JwTextEntry(
          title = title,
          pageUrl = resolved,
          imageUrl = imageUrl,
          publishedOn = LocalDate.parse(pubDate),
        ),
      )
    }
    return ParseResult.ParseFailure
  }

  // --- parser HTML: texto do dia (contracts/jw-sources.md §2) ---

  fun parseDailyText(body: String): ParseResult<JwDailyText> {
    val blocks =
      Regex("""(?s)<div class="tabContent" data-date="([^"]+)".*?</div>""")
        .findAll(body)
        .toList()
    if (blocks.isEmpty()) return ParseResult.ParseFailure

    val today = LocalDate.now().toString()
    val chosen = blocks.firstOrNull { it.groupValues[1].take(10) == today }
      ?: blocks.getOrNull(1) ?: return ParseResult.ParseFailure

    val dateLabel = stripTags(firstGroup(chosen.value, """(?s)<h2[^>]*>(.*?)</h2>""") ?: "").trim()
    val scripture = stripTags(firstGroup(chosen.value, """(?s)<p[^>]*class="themeScrp"[^>]*>(.*?)</p>""") ?: "").trim()
    val comment =
      stripTags(firstGroup(chosen.value, """(?s)<p[^>]*class="sb"[^>]*>(.*?)</p>""") ?: "").trim()
    if (dateLabel.isEmpty() || scripture.isEmpty() || comment.isEmpty()) return ParseResult.ParseFailure

    return ParseResult.Ok(
      JwDailyText(
        dateLabel = dateLabel,
        scripture = scripture,
        comment = comment,
        pageUrl = WOL_DAILY_URL,
      ),
    )
  }

  // --- parser JSON: mediator (contracts/jw-sources.md §3–5) ---

  fun parseLatestVideos(body: String): ParseResult<JwVideoEntry> =
    parseMediatorVideo(body) { title, primaryCategory ->
      primaryCategory == "StudioMonthlyPrograms"
    }

  fun parseStudioNews(body: String): ParseResult<JwVideoEntry> =
    parseMediatorVideo(body) { title, _ -> GOVERNING_BODY_TITLE.containsMatchIn(title) }

  private fun parseMediatorVideo(
    body: String,
    matches: (title: String, primaryCategory: String) -> Boolean,
  ): ParseResult<JwVideoEntry> {
    val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return ParseResult.ParseFailure
    val media = root["category"]?.jsonObject?.get("media")?.jsonArray ?: return ParseResult.ParseFailure
    val item =
      media
        .mapNotNull { it.jsonObject }
        .filter { item ->
          val title = item["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
          matches(title, item["primaryCategory"]?.jsonPrimitive?.contentOrNull.orEmpty())
        }
        .maxByOrNull { parseInstant(itemPublished(it)) ?: Instant.MIN }
    if (item == null) return ParseResult.ParseFailure

    val title = item["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val videoUrl = chooseVideoUrl(item["files"]?.jsonArray) ?: return ParseResult.ParseFailure
    if (title.isEmpty()) return ParseResult.ParseFailure

    val images = item["images"]?.jsonObject
    val posterUrl = images?.get("wss")?.jsonObject?.get("lg")?.jsonPrimitive?.contentOrNull
      ?: images?.get("lss")?.jsonObject?.get("lg")?.jsonPrimitive?.contentOrNull
    val duration = runCatching { item["duration"]?.jsonPrimitive?.contentOrNull?.toDouble()?.toInt() }.getOrNull()
    return ParseResult.Ok(
      JwVideoEntry(
        title = title,
        publishedAt = parseInstant(itemPublished(item)),
        posterUrl = posterUrl,
        videoUrl = videoUrl,
        durationSeconds = duration,
      ),
    )
  }

  private fun itemPublished(item: kotlinx.serialization.json.JsonObject): String? =
    item["firstPublished"]?.jsonPrimitive?.contentOrNull

  /**
   * Escolha do ficheiro (§5): `mimetype == "video/mp4"`, maior altura entre `240p`…`1080p`;
   * nunca `video/3gpp`, HLS ou URL fora de `*.jw-cdn.org`.
   */
  fun chooseVideoUrl(files: kotlinx.serialization.json.JsonArray?): String? {
    if (files == null) return null
    var best: Pair<Int, String>? = null
    for (file in files) {
      val f = file.jsonObject
      if (f["mimetype"]?.jsonPrimitive?.contentOrNull != "video/mp4") continue
      val url = f["progressiveDownloadURL"]?.jsonPrimitive?.contentOrNull ?: continue
      if (!isJwCdn(url)) continue
      val label = f["label"]?.jsonPrimitive?.contentOrNull ?: continue
      val height = label.let { l -> Regex("""^(\d{3,4})p$""").find(l)?.groupValues?.get(1)?.toIntOrNull() } ?: continue
      if (height < 240 || height > 1080) continue
      if (best == null || height > best.first) best = height to url
    }
    return best?.second
  }

  // --- helpers de texto ---

  private suspend fun getText(url: String, accept: String = "text/html"): String? =
    when (val r = http.get(url, timeoutMs = TIMEOUT_MS, accept = accept)) {
      is FetchResult.Ok -> r.body
      is FetchResult.Error -> null
    }

  private suspend fun <T> parseFrom(body: String?, parse: (String) -> ParseResult<T>): ParseResult<T> =
    if (body == null) ParseResult.ParseFailure else parse(body)

  private fun stripTags(html: String): String = Regex("""<[^>]*>""").replace(html, "")

  private fun firstGroup(html: String, pattern: String): String? =
    Regex(pattern).find(html)?.groupValues?.get(1)

  private fun resolveJwOrg(href: String): String? {
    val uri = runCatching { URI(href) }.getOrNull() ?: return null
    if (uri.isAbsolute) return if (uri.host == "www.jw.org") href else null
    return "https://www.jw.org" + href
  }

  private fun isJwCdn(url: String): Boolean =
    runCatching { URI(url).host?.endsWith(".jw-cdn.org") ?: false }.getOrDefault(false)

  private fun parseInstant(value: String?): Instant? =
    value?.let { runCatching { Instant.parse(it) }.getOrNull() }

  companion object {
    const val WHATSNEW_URL = "https://www.jw.org/pt/novidades/"
    const val WOL_DAILY_URL = "https://wol.jw.org/pt/wol/h/r5/lp-t"
    const val LATEST_VIDEOS_URL =
      "https://b.jw-cdn.org/apis/mediator/v1/categories/T/LatestVideos?detailed=1&clientType=www"
    const val STUDIO_NEWS_URL =
      "https://b.jw-cdn.org/apis/mediator/v1/categories/T/StudioNewsReports?detailed=1&clientType=www"
    const val TIMEOUT_MS = 8000L
    val GOVERNING_BODY_TITLE = Regex("^Boletim do Corpo Governante")
  }
}
