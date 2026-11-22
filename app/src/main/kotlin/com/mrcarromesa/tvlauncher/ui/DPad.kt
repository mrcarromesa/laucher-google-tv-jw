package com.mrcarromesa.tvlauncher.ui

import android.view.KeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent

/**
 * Manuseamento D-pad, padrão JetStream (android/tv-samples ModifierUtils.kt):
 * consome ESQUERDA/DIREITA/CIMA/BAIXO/OK e chama os callbacks no ACTION_UP.
 * O foco não se move para outro elemento quando o evento é consumido.
 */
fun Modifier.handleDPadKeyEvents(
  onLeft: (() -> Unit)? = null,
  onRight: (() -> Unit)? = null,
  onUp: (() -> Unit)? = null,
  onDown: (() -> Unit)? = null,
  onEnter: (() -> Unit)? = null,
): Modifier = onKeyEvent {
  if (it.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
    when (it.nativeKeyEvent.keyCode) {
      KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> {
        onLeft?.invoke().also { return@onKeyEvent true }
      }
      KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> {
        onRight?.invoke().also { return@onKeyEvent true }
      }
      KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP -> {
        onUp?.invoke().also { return@onKeyEvent true }
      }
      KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN -> {
        onDown?.invoke().also { return@onKeyEvent true }
      }
      KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
        onEnter?.invoke().also { return@onKeyEvent true }
      }
    }
  }
  false
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