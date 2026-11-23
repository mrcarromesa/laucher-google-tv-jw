package com.mrcarromesa.tvlauncher.data.jw

import java.text.Normalizer
import java.time.LocalDate

/**
 * Ligações das publicações da reunião derivadas da data atual:
 * Apostila da Reunião Vida e Ministério (bimestral) e artigo de estudo de A Sentinela.
 */
object JwMeetingLinks {

  private val MONTHS =
    listOf(
      "janeiro", "fevereiro", "marco", "abril", "maio", "junho",
      "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
    )

  /**
   * Apostila da reunião da semana de [date].
   * shortcut: slug bimestral `<mes>-<mes>-<ano>-mwb` calculado da data; rever se o
   * jw.org mudar o esquema de URLs da apostila.
   */
  fun workbookUrl(date: LocalDate): String {
    val start = ((date.monthValue - 1) / 2) * 2 + 1
    return "${JwSources.JW_BASE_URL}/biblioteca/jw-apostila-do-mes/" +
      "${MONTHS[start - 1]}-${MONTHS[start]}-${date.year}-mwb/"
  }

  /**
   * URL da semana da apostila que contém [date]; `null` se não encontrar.
   * As subpáginas têm slug `…-para-<dia>-<dia>-de-<mes>-de-<ano>`.
   */
  fun pickWorkbookWeek(html: String, date: LocalDate): String? {
    val links =
      Regex("""href="(/pt/biblioteca/jw-apostila-do-mes/[^"/]+/[^"/]+/)"""")
        .findAll(html)
        .map { it.groupValues[1] }
    for (path in links) {
      val decoded = runCatching { java.net.URLDecoder.decode(path, "UTF-8") }.getOrNull() ?: continue
      val marker = decoded.indexOf("-para-")
      if (marker < 0) continue
      val rangeText = decoded.substring(marker + "-para-".length).trimEnd('/').replace("-de-", " de ")
      val range = parseWeek(rangeText) ?: continue
      if (date < range.first || date > range.second) continue
      return "https://www.jw.org$path"
    }
    return null
  }

  /**
   * Edição de estudo de A Sentinela que cobre [date]. O número é publicado cerca
   * de dois meses antes das semanas que cobre.
   * shortcut: offset fixo de 2 meses; aceitar pequenas variações editoriais.
   */
  fun studyEditionUrl(date: LocalDate): String {
    val edition = date.minusMonths(2)
    return "${JwSources.JW_BASE_URL}/biblioteca/revistas/sentinela-estudo-" +
      "${MONTHS[edition.monthValue - 1]}-${edition.year}/"
  }

  /** URL do artigo de estudo cuja semana contém [date]; `null` se não encontrar. */
  fun pickStudyArticle(html: String, date: LocalDate): String? {
    val hrefs =
      Regex("""href="(/pt/biblioteca/revistas/sentinela-estudo-[^"/]+/[^"/]+/)"""")
        .findAll(html)
        .map { it.range.first to it.groupValues[1] }
        .toList()
    for (week in Regex("""Estudo para a semana de ([^<]+?)\.""").findAll(html)) {
      val range = parseWeek(week.groupValues[1]) ?: continue
      if (date < range.first || date > range.second) continue
      val href = hrefs.lastOrNull { it.first < week.range.first }?.second ?: continue
      return "https://www.jw.org$href"
    }
    return null
  }

  /** Interpreta "16-22 de novembro de 2026" (e variantes que mudam de mês/ano). */
  fun parseWeek(raw: String): Pair<LocalDate, LocalDate>? {
    val s =
      raw
        .replace('\u00A0', ' ')
        .replace('\u202F', ' ')
        .let { it.replace(Regex("[\\u2010\\u2011\\u2013\\u2014]"), "-") }
        .replace(Regex("\\s+"), " ")
        .trim()
    val m =
      Regex(
          """(\d{1,2})(?:\s+de\s+([\p{L}]+))?(?:\s+de\s+(\d{4}))?\s*-\s*""" +
            """(\d{1,2})\s+de\s+([\p{L}]+)\s+de\s+(\d{4})""",
        )
        .matchEntire(s) ?: return null
    val (sd, sm, sy, ed, em, ey) = m.destructured
    val endMonth = monthNumber(em) ?: return null
    val endYear = ey.toIntOrNull() ?: return null
    val startMonth = if (sm.isNotEmpty()) monthNumber(sm) ?: return null else endMonth
    val startYear = if (sy.isNotEmpty()) sy.toIntOrNull() ?: return null else endYear
    return runCatching {
      LocalDate.of(startYear, startMonth, sd.toInt()) to LocalDate.of(endYear, endMonth, ed.toInt())
    }.getOrNull()
  }

  private fun monthNumber(name: String): Int? {
    val normalized =
      Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    return MONTHS.indexOf(normalized).takeIf { it >= 0 }?.plus(1)
  }
}
