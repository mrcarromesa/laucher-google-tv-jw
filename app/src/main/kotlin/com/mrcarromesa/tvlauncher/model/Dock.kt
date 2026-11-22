package com.mrcarromesa.tvlauncher.model

import android.graphics.drawable.Drawable

/**
 * Item do dock. Ordem 0…4 fixa (data-model.md):
 * Netflix, YouTube, HDMI 1, HDMI 2, Todos os Apps.
 *
 * Invariantes:
 * - A ordem e o número de itens são constantes; um item nunca desaparece por a app
 *   não estar instalada (FR-002, FR-004).
 * - `installed == false` ⇒ apresentado esbatido; OK mostra a mensagem de
 *   indisponibilidade; nunca lança outra app.
 * - `Hdmi` nunca está "indisponível": OK abre sempre o diálogo de ajuda (FR-005).
 */
sealed interface DockItem {
  val order: Int
  val label: String

  data class App(
    val packageName: String,
    val installed: Boolean,
    val icon: Drawable?,
    override val order: Int,
    override val label: String,
  ) : DockItem

  data class Hdmi(val index: Int) : DockItem {
    override val order: Int = 1 + index // HDMI 1 → 2, HDMI 2 → 3
    override val label: String = "HDMI $index"
  }

  data object AllApps : DockItem {
    override val order: Int = 4
    override val label: String = "Todos os Apps"
  }
}

/**
 * App abrível (data-model.md). Lista ordenada por `label`; só entram apps com
 * atividade de lançamento resolvível; exclui o próprio launcher.
 */
data class InstalledApp(
  val packageName: String,
  val label: String,
  val icon: android.graphics.drawable.Drawable, // falha → ícone genérico (já resolvido)
)
