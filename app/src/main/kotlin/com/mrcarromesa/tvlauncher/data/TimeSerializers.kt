package com.mrcarromesa.tvlauncher.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** `Instant` em epoch-millis (Long) — sem dependência kotlinx-datetime. */
object InstantMillisSerializer : KSerializer<Instant> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("InstantMillis", PrimitiveKind.LONG)
  override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeLong(value.toEpochMilli())
  override fun deserialize(decoder: Decoder): Instant = Instant.ofEpochMilli(decoder.decodeLong())
}

/** `LocalDateTime` em ISO-8601 (String). */
object LocalDateTimeIsoSerializer : KSerializer<LocalDateTime> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDateTimeIso", PrimitiveKind.STRING)
  override fun serialize(encoder: Encoder, value: LocalDateTime) = encoder.encodeString(value.toString())
  override fun deserialize(decoder: Decoder): LocalDateTime = LocalDateTime.parse(decoder.decodeString())
}

/** `LocalDate` em ISO-8601 (String). */
object LocalDateIsoSerializer : KSerializer<LocalDate> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDateIso", PrimitiveKind.STRING)
  override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
  override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}
