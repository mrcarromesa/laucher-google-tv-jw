package com.mrcarromesa.tvlauncher.data.jw

import java.io.File
import java.net.URI
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Testes dos parsers JW.org com fixtures capturadas (contracts/jw-sources.md §8;
 * T045). Se a estrutura das fontes mudar, estes testes falham — aviso antecipado (R3).
 */
class JwSourcesTest {

  private val sources = JwSources(http = com.mrcarromesa.tvlauncher.data.Http())
  private val fixtureDir = "src/test/resources/fixtures"
  private fun fixture(name: String): String = File(fixtureDir, name).readText()

  // --- §1 Novidades (HTML) ---

  @Test
  fun `novidades primeiro bloco valido devolve titulo url e data`() {
    val result = sources.parseWhatsNew(fixture("whats-new.html"))
    assertTrue("esperado ParseResult.Ok, obtido $result", result is ParseResult.Ok)
    val entry = (result as ParseResult.Ok).value
    assertTrue(entry.title.isNotEmpty())
    assertTrue(entry.pageUrl.startsWith("https://www.jw.org/"))
    // O primeiro bloco compubDate é a notícia de 2026-11-20 (os blocos de topo não têm pubDate).
    assertTrue("url deve pertencer a jw.org: ${entry.pageUrl}", URI(entry.pageUrl).host == "www.jw.org")
  }

  @Test
  fun `novidades sem bloco valido da ParseFailure`() {
    // Bloco com classe synopsis+docId e pubDate mas sem h3>a → ignorado → ParseFailure.
    val html =
      "<div class=\"whatsNewItems\">" +
        "<div class=\"synopsis docId-1\"><p class=\"meta pubDate\">2026-11-20</p></div>" +
        "</div>"
    assertTrue(sources.parseWhatsNew(html) is ParseResult.ParseFailure)
    val broken = fixture("whats-new.html").replace("docId-", "docX-").replace("pubDate", "pubXDate")
    assertTrue(sources.parseWhatsNew(broken) is ParseResult.ParseFailure)
  }

  @Test
  fun `href relativo resolve contra jw org`() {
    val html =
      "<div class=\"synopsis docId-9\"><p class=\"meta pubDate\">2026-11-20</p>" +
        "<h3><a href=\"/en/news/x/\">Título</a></h3></div>"
    val result = sources.parseWhatsNew(html)
    assertTrue("esperado Ok, obtido $result", result is ParseResult.Ok)
    assertEquals("https://www.jw.org/en/news/x/", (result as ParseResult.Ok).value.pageUrl)
  }

  @Test
  fun `href de outro host nao e aceite`() {
    val html =
      "<div class=\"synopsis docId-9\"><p class=\"meta pubDate\">2026-11-20</p>" +
        "<h3><a href=\"https://evil.example/x\">Título</a></h3></div>"
    assertTrue(sources.parseWhatsNew(html) is ParseResult.ParseFailure)
  }

  // --- §2 Texto do dia (HTML) ---

  @Test
  fun `texto do dia escolhe bloco com data de hoje`() {
    val today = java.time.LocalDate.now().toString()
    val html =
      "<div class=\"tabContent\" data-date=\"2026-01-01T00:00:00.000Z\"><h2>Ontem</h2>" +
        "<p class=\"themeScrp\">Isa. 1:1</p><div class=\"bodyTxt\"><p class=\"sb\">comentário on</p></div></div>" +
        "<div class=\"tabContent\" data-date=\"${today}T00:00:00.000Z\"><h2>Hoje</h2>" +
        "<p class=\"themeScrp\">Isa. 1:2</p><div class=\"bodyTxt\"><p class=\"sb\">comentário ho</p></div></div>" +
        "<div class=\"tabContent\" data-date=\"2026-01-03T00:00:00.000Z\"><h2>Amanhã</h2>" +
        "<p class=\"themeScrp\">Isa. 1:3</p><div class=\"bodyTxt\"><p class=\"sb\">comentário am</p></div></div>"
    val result = sources.parseDailyText(html)
    assertTrue("esperado Ok, obtido $result", result is ParseResult.Ok)
    val entry = (result as ParseResult.Ok).value
    assertEquals("Hoje", entry.dateLabel)
    assertEquals("Isa. 1:2", entry.scripture)
    assertEquals("comentário ho", entry.comment)
    assertEquals(JwSources.WOL_DAILY_URL, entry.pageUrl)
  }

  @Test
  fun `texto do dia sem data de hoje usa bloco do meio`() {
    val html =
      "<div class=\"tabContent\" data-date=\"2026-01-01T00:00:00.000Z\"><h2>A</h2>" +
        "<p class=\"themeScrp\">Isa. 1:1</p><div class=\"bodyTxt\"><p class=\"sb\">c1</p></div></div>" +
        "<div class=\"tabContent\" data-date=\"2026-01-02T00:00:00.000Z\"><h2>B</h2>" +
        "<p class=\"themeScrp\">Isa. 1:2</p><div class=\"bodyTxt\"><p class=\"sb\">c2</p></div></div>" +
        "<div class=\"tabContent\" data-date=\"2026-01-03T00:00:00.000Z\"><h2>C</h2>" +
        "<p class=\"themeScrp\">Isa. 1:3</p><div class=\"bodyTxt\"><p class=\"sb\">c3</p></div></div>"
    val result = sources.parseDailyText(html)
    assertTrue("esperado Ok, obtido $result", result is ParseResult.Ok)
    assertEquals("B", (result as ParseResult.Ok).value.dateLabel)
  }

  @Test
  fun `texto do dia com campo vazio da ParseFailure`() {
    val html =
      "<div class=\"tabContent\" data-date=\"2026-01-01T00:00:00.000Z\"><h2>A</h2>" +
        "<p class=\"themeScrp\">Isa. 1:1</p><div class=\"bodyTxt\"><p class=\"sb\"></p></div></div>" +
        "<div class=\"tabContent\" data-date=\"2026-01-02T00:00:00.000Z\"><h2>B</h2>" +
        "<p class=\"themeScrp\"></p><div class=\"bodyTxt\"><p class=\"sb\">c2</p></div></div>" +
        "<div class=\"tabContent\" data-date=\"2026-01-03T00:00:00.000Z\"><h2>C</h2>" +
        "<p class=\"themeScrp\">Isa. 1:3</p><div class=\"bodyTxt\"><p class=\"sb\">c3</p></div></div>"
    // hoje não está nos data-date ⇒ meio (B) tem themeScrp vazio ⇒ ParseFailure
    assertTrue(sources.parseDailyText(html) is ParseResult.ParseFailure)
  }

  // --- §3/§4 Mediator (JSON) ---

  @Test
  fun `latest videos escolhe primeiro StudioMonthlyPrograms`() {
    val result = sources.parseLatestVideos(fixture("mediator-latest-videos.json"))
    assertTrue("esperado Ok, obtido $result", result is ParseResult.Ok)
    val entry = (result as ParseResult.Ok).value
    assertTrue("título deve conter 'JW Broadcasting', obtido: ${entry.title}", entry.title.contains("JW Broadcasting"))
    assertTrue(entry.videoUrl.endsWith(".mp4"))
    assertTrue("URL do vídeo deve ser de *.jw-cdn.org: ${entry.videoUrl}", URI(entry.videoUrl).host.endsWith(".jw-cdn.org"))
  }

  @Test
  fun `studio news escolhe primeiro boletim do corpo governante`() {
    val result = sources.parseStudioNews(fixture("mediator-news.json"))
    assertTrue("esperado Ok, obtido $result", result is ParseResult.Ok)
    val entry = (result as ParseResult.Ok).value
    assertTrue("título: ${entry.title}", entry.title.startsWith("Boletim do Corpo Governante"))
    assertTrue(entry.videoUrl.endsWith(".mp4"))
  }

  @Test
  fun `boletim ausente da ParseFailure`() {
    val json = """{"category":{"media":[{"title":"Outra coisa","firstPublished":"2026-11-01T00:00:00Z","files":[]}]}}"""
    assertTrue(sources.parseStudioNews(json) is ParseResult.ParseFailure)
  }

  // --- §5 Escolha do ficheiro de vídeo ---

  @Test
  fun `escolhe mp4 de maior altura entre 240p e 1080p`() {
    val files =
      kotlinx.serialization.json.Json.parseToJsonElement(
        """[
          {"label":"144p","mimetype":"video/mp4","progressiveDownloadURL":"https://a.jw-cdn.org/144.mp4"},
          {"label":"240p","mimetype":"video/mp4","progressiveDownloadURL":"https://a.jw-cdn.org/240.mp4"},
          {"label":"720p","mimetype":"video/mp4","progressiveDownloadURL":"https://a.jw-cdn.org/720.mp4"},
          {"label":"1080p","mimetype":"video/mp4","progressiveDownloadURL":"https://a.jw-cdn.org/1080.mp4"}
        ]""",
      ).jsonArray
    assertEquals("https://a.jw-cdn.org/1080.mp4", sources.chooseVideoUrl(files))
  }

  @Test
  fun `nunca escolhe 3gpp nem url fora de jw-cdn`() {
    val files =
      kotlinx.serialization.json.Json.parseToJsonElement(
        """[
          {"label":"240p","mimetype":"video/3gpp","progressiveDownloadURL":"https://a.jw-cdn.org/x.3gp"},
          {"label":"360p","mimetype":"video/mp4","progressiveDownloadURL":"https://evil.example/x.mp4"},
          {"label":"240p","mimetype":"video/mp4","progressiveDownloadURL":"https://a.jw-cdn.org/ok.mp4"}
        ]""",
      ).jsonArray
    assertEquals("https://a.jw-cdn.org/ok.mp4", sources.chooseVideoUrl(files))
  }

  @Test
  fun `sem mp4 valido escolha devolve null`() {
    val files =
      kotlinx.serialization.json.Json.parseToJsonElement(
        """[{"label":"240p","mimetype":"video/3gpp","progressiveDownloadURL":"https://a.jw-cdn.org/x.3gp"}]""",
      ).jsonArray
    assertTrue(sources.chooseVideoUrl(files) == null)
  }
}
