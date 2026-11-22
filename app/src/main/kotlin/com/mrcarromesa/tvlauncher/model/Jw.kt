package com.mrcarromesa.tvlauncher.model

import com.mrcarromesa.tvlauncher.data.InstantMillisSerializer
import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * Conteúdo do jw.org (data-model.md). Cada campo é independente: uma fonte pode
 * falhar e o snapshot novo funde-se campo a campo com o anterior.
 */
@Serializable
data class JwSnapshot(
  val dailyText: JwDailyText? = null,
  val broadcasting: JwVideoEntry? = null,
  val governingBody: JwVideoEntry? = null,
  @Serializable(with = InstantMillisSerializer::class)
  val fetchedAt: Instant,
)

@Serializable
data class JwDailyText(
  val dateLabel: String,
  val scripture: String,
  val comment: String,
  val pageUrl: String,
)

@Serializable
data class JwVideoEntry(
  val title: String,
  @Serializable(with = InstantMillisSerializer::class)
  val publishedAt: Instant? = null,
  val posterUrl: String? = null,
  val videoUrl: String,
  val durationSeconds: Int? = null,
)
