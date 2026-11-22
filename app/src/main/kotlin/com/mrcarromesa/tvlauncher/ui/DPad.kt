package com.mrcarromesa.tvlauncher.ui

import android.view.KeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent

/**
 * Manuseamento D-pad, padrão JetStream (android/tv-samples ModifierUtils.kt):
 * consome ESQUERDA/DIREITA/CIMA/BAIXO/OK e chama os callbacks no ACTION_UP.
 *
 * A tecla é consumida também em ACTION_DOWN (e repetições). Se a pressão
 * passasse, o focus manager do Compose move o foco pelo traversal padrão antes
 * do nosso ACTION_UP — são esses dois movimentos por tecla que fazem a seleção
 * saltar. Ao consumir a pressão, o foco só se move uma vez, para o destino
 * explícito (requestFocus) do callback, no ACTION_UP.
 */
fun Modifier.handleDPadKeyEvents(
  onLeft: (() -> Unit)? = null,
  onRight: (() -> Unit)? = null,
  onUp: (() -> Unit)? = null,
  onDown: (() -> Unit)? = null,
  onEnter: (() -> Unit)? = null,
): Modifier = onKeyEvent {
  val handler = when (it.nativeKeyEvent.keyCode) {
    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> onLeft
    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> onRight
    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP -> onUp
    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN -> onDown
    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> onEnter
    else -> null
  }
  if (handler == null) return@onKeyEvent false
  // Consome a tecla em qualquer ação; o callback só invoca no ACTION_UP.
  if (it.nativeKeyEvent.action == KeyEvent.ACTION_UP) handler.invoke()
  true
}

/** Igual a [handleDPadKeyEvents] mas em preview (antes do foco se mover). */
fun Modifier.handleDPadPreviewKeyEvents(
  onLeft: (() -> Unit)? = null,
  onRight: (() -> Unit)? = null,
  onEnter: (() -> Unit)? = null,
): Modifier = onPreviewKeyEvent {
  fun onActionUp(block: () -> Unit) {
    if (it.nativeKeyEvent.action == KeyEvent.ACTION_UP) block()
  }
  when (it.nativeKeyEvent.keyCode) {
    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> {
      onLeft?.apply { onActionUp(::invoke); return@onPreviewKeyEvent true }
    }
    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> {
      onRight?.apply { onActionUp(::invoke); return@onPreviewKeyEvent true }
    }
    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
      onEnter?.apply { onActionUp(::invoke); return@onPreviewKeyEvent true }
    }
  }
  false
}