package com.mrcarromesa.tvlauncher.data.weather

import com.mrcarromesa.tvlauncher.model.WeatherCondition

/**
 * Tabela WMO fixa (data-model.md): 0–3, 45/48, 51–57, 61–67, 71–77, 80–82,
 * 85/86, 95–99. Código desconhecido → condição "—" (não é falha).
 */
object WeatherCode {

  fun condition(code: Int): WeatherCondition = when (code) {
    0 -> WeatherCondition(code, "Céu limpo")
    1 -> WeatherCondition(code, "Maiormente limpo")
    2 -> WeatherCondition(code, "Parcialmente nublado")
    3 -> WeatherCondition(code, "Nublado")
    45, 48 -> WeatherCondition(code, "Nevoeiro")
    51, 53, 55 -> WeatherCondition(code, "Chuvisco")
    56, 57 -> WeatherCondition(code, "Chuvisco gelado")
    61, 63, 65 -> WeatherCondition(code, "Chuva")
    66, 67 -> WeatherCondition(code, "Chuva gelada")
    71, 73, 75 -> WeatherCondition(code, "Neve")
    77 -> WeatherCondition(code, "Grãos de neve")
    80, 81, 82 -> WeatherCondition(code, "Aguaceiros")
    85, 86 -> WeatherCondition(code, "Aguaceiros de neve")
    95 -> WeatherCondition(code, "Trovoada")
    96, 99 -> WeatherCondition(code, "Trovoada com granizo")
    else -> WeatherCondition(code, "—")
  }
}
