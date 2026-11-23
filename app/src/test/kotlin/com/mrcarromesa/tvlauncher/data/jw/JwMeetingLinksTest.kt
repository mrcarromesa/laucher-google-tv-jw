package com.mrcarromesa.tvlauncher.data.jw

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JwMeetingLinksTest {

  @Test
  fun workbookUrl_isBimonthly() {
    assertEquals(
      "https://www.jw.org/pt/biblioteca/jw-apostila-do-mes/novembro-dezembro-2026-mwb/",
      JwMeetingLinks.workbookUrl(LocalDate.of(2026, 11, 23)),
    )
    assertEquals(
      "https://www.jw.org/pt/biblioteca/jw-apostila-do-mes/janeiro-fevereiro-2027-mwb/",
      JwMeetingLinks.workbookUrl(LocalDate.of(2027, 1, 5)),
    )
  }

  @Test
  fun studyEdition_isTwoMonthsEarlier() {
    assertEquals(
      "https://www.jw.org/pt/biblioteca/revistas/sentinela-estudo-setembro-2026/",
      JwMeetingLinks.studyEditionUrl(LocalDate.of(2026, 11, 23)),
    )
    assertEquals(
      "https://www.jw.org/pt/biblioteca/revistas/sentinela-estudo-novembro-2026/",
      JwMeetingLinks.studyEditionUrl(LocalDate.of(2027, 1, 5)),
    )
  }

  @Test
  fun parseWeek_handlesMonthAndYearCrossing() {
    assertEquals(
      LocalDate.of(2026, 11, 16) to LocalDate.of(2026, 11, 22),
      JwMeetingLinks.parseWeek("16-22\u00A0de novembro de 2026"),
    )
    assertEquals(
      LocalDate.of(2026, 11, 30) to LocalDate.of(2026, 12, 6),
      JwMeetingLinks.parseWeek("30 de novembro-6 de dezembro de 2026"),
    )
    assertEquals(
      LocalDate.of(2026, 12, 28) to LocalDate.of(2027, 1, 3),
      JwMeetingLinks.parseWeek("28 de dezembro de 2026-3 de janeiro de 2027"),
    )
    assertNull(JwMeetingLinks.parseWeek("lixo"))
  }

  @Test
  fun pickStudyArticle_matchesCurrentWeek() {
    val html =
      """
      <a href="/pt/biblioteca/revistas/sentinela-estudo-setembro-2026/Confie/">x</a>
      <p>Estudo para a semana de 2-8 de novembro de 2026.</p>
      <a href="/pt/biblioteca/revistas/sentinela-estudo-setembro-2026/Pregando/">x</a>
      <p>Estudo para a semana de 16-22 de novembro de 2026.</p>
      """
        .trimIndent()
    assertEquals(
      "https://www.jw.org/pt/biblioteca/revistas/sentinela-estudo-setembro-2026/Pregando/",
      JwMeetingLinks.pickStudyArticle(html, LocalDate.of(2026, 11, 18)),
    )
  }

  @Test
  fun pickWorkbookWeek_matchesCurrentWeekAndYearCrossing() {
    val html =
      """
      <a href="/pt/biblioteca/jw-apostila-do-mes/novembro-dezembro-2026-mwb/Programa%C3%A7%C3%A3o-da-Reuni%C3%A3o-Vida-e-Minist%C3%A9rio-para-2-8-de-novembro-de-2026/">x</a>
      <a href="/pt/biblioteca/jw-apostila-do-mes/novembro-dezembro-2026-mwb/Programa%C3%A7%C3%A3o-da-Reuni%C3%A3o-Vida-e-Minist%C3%A9rio-para-23-29-de-novembro-de-2026/">x</a>
      <a href="/pt/biblioteca/jw-apostila-do-mes/novembro-dezembro-2026-mwb/Programa%C3%A7%C3%A3o-da-Reuni%C3%A3o-Vida-e-Minist%C3%A9rio-para-28-de-dezembro-de-2026-3-de-janeiro-de-2027/">x</a>
      """
        .trimIndent()
    assertEquals(
      "https://www.jw.org/pt/biblioteca/jw-apostila-do-mes/novembro-dezembro-2026-mwb/Programa%C3%A7%C3%A3o-da-Reuni%C3%A3o-Vida-e-Minist%C3%A9rio-para-23-29-de-novembro-de-2026/",
      JwMeetingLinks.pickWorkbookWeek(html, LocalDate.of(2026, 11, 23)),
    )
    assertEquals(
      "https://www.jw.org/pt/biblioteca/jw-apostila-do-mes/novembro-dezembro-2026-mwb/Programa%C3%A7%C3%A3o-da-Reuni%C3%A3o-Vida-e-Minist%C3%A9rio-para-28-de-dezembro-de-2026-3-de-janeiro-de-2027/",
      JwMeetingLinks.pickWorkbookWeek(html, LocalDate.of(2026, 12, 30)),
    )
  }
}
