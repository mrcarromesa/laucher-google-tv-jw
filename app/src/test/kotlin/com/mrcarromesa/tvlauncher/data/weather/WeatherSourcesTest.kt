package com.mrcarromesa.tvlauncher.data.weather

import com.mrcarromesa.tvlauncher.data.Http
import com.mrcarromesa.tvlauncher.model.Place
import java.io.File
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes dos parsers de clima com fixtures capturadas das respostas reais
 * (contracts/weather-sources.md §1–2; T035). Se a estrutura das fontes mudar,
 * estes testes falham — é o aviso antecipado (R3).
 */
class WeatherSourcesTest {

  // Só os métodos de parsing são exercitados; o Http nunca é chamado.
  private val sources = WeatherSources(http = Http(), filesDir = File("."))
  private val fixtureDir = "src/test/resources/fixtures"
  private fun fixture(name: String): String = File(fixtureDir, name).readText()

  @Test
  fun `ipwhois sucesso devolve coordenadas e cidade`() {
    val place = sources.parsePlace(fixture("ipwhois.json"))
    assertNotNull(place)
    assertEquals("Ararangua", place!!.city)
    assertEquals(-28.9357532, place.latitude, 1e-9)
    assertEquals(-49.4953806, place.longitude, 1e-9)
    assertEquals("America/Sao_Paulo", place.timeZoneId)
  }

  @Test
  fun `ipwhois success nao verdadeiro falha`() {
    val json = """{"success":false,"latitude":-28.9,"longitude":-49.4,"city":"X"}"""
    assertNull(sources.parsePlace(json))
  }

  @Test
  fun `ipwhois coordenadas ausentes falha`() {
    val json = """{"success":true,"city":"X"}"""
    assertNull(sources.parsePlace(json))
  }

  @Test
  fun `open-meteo parsing corrente diario e horario`() {
    val place = Place("C", "CC", -28.9, -49.4, "America/Sao_Paulo")
    val now = LocalDateTime.of(2026, 11, 22, 3, 0)
    val snap = sources.parseForecast(fixture("open-meteo.json"), place, now)
    assertNotNull(snap)
    assertEquals(22.2, snap!!.currentTempC, 1e-9)
    assertEquals(95, snap.currentCode)
    assertEquals(23.8, snap.maxC, 1e-9)
    assertEquals(19.2, snap.minC, 1e-9)
  }

  @Test
  fun `abreviacoes horarias sao passadas ordenadas a partir de agora`() {
    val place = Place("C", "CC", -28.9, -49.4, "America/Sao_Paulo")
    val now = LocalDateTime.of(2026, 11, 22, 3, 0)
    val snap = sources.parseForecast(fixture("open-meteo.json"), place, now)
    val hourly = snap!!.hourly
    assertTrue(hourly.size >= 5)
    assertTrue(hourly.first().localTime.isAfter(now) || hourly.first().localTime == now)
    // ordenados
    hourly.zipWithNext().forEach { (a, b) -> assertTrue(a.localTime <= b.localTime) }
  }

  @Test
  fun `menos de 5 pontos futuros e ParseFailure`() {
    val place = Place("C", "CC", -28.9, -49.4, "America/Sao_Paulo")
    // A fixture cobre 2026-11-22..23 → filtrar a partir de um `now` demasiado tarde
    // deixa poucos pontos futuros.
    val lateNow = LocalDateTime.of(2026, 11, 23, 22, 0)
    assertNull(sources.parseForecast(fixture("open-meteo.json"), place, lateNow))
  }

  @Test
  fun `arrays horarios de comprimento diferente e ParseFailure`() {
    val place = Place("C", "CC", -28.9, -49.4, "America/Sao_Paulo")
    val now = LocalDateTime.of(2026, 11, 22, 3, 0)
    val json =
      """{"current":{"temperature_2m":20,"weather_code":0},"daily":{
         "temperature_2m_max":[23],"temperature_2m_min":[18]},
         "hourly":{"time":["2026-11-22T03:00"],"temperature_2m":[20,21],"weather_code":[0,1]}}"""
    assertNull(sources.parseForecast(json, place, now))
  }

  @Test
  fun `campo obrigatorio ausente e ParseFailure`() {
    val place = Place("C", "CC", -28.9, -49.4, "America/Sao_Paulo")
    val now = LocalDateTime.of(2026, 11, 22, 3, 0)
    val json =
      """{"current":{"temperature_2m":20},"daily":{
         "temperature_2m_max":[23],"temperature_2m_min":[18]},
         "hourly":{"time":["2026-11-22T03:00"],"temperature_2m":[20],"weather_code":[0]}}"""
    assertNull(sources.parseForecast(json, place, now))
  }

  @Test
  fun `codigo WMO desconhecido vira condicao traco e nao falha`() {
    assertEquals("—", WeatherCode.condition(999).labelPt)
    assertEquals("Céu limpo", WeatherCode.condition(0).labelPt)
    assertEquals("Trovoada", WeatherCode.condition(95).labelPt)
  }
}
