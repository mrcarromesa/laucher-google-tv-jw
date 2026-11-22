package com.mrcarromesa.tvlauncher.ui.reader

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * WebView de TV para o leitor: o comando não tem rato, então o D-pad move um cursor
 * virtual sobreposto e OK clica o elemento nesse ponto (via `elementFromPoint`).
 *
 * Consome todas as setas para o Chromium não as usar para mover o foco HTML — é isso
 * que impedia a navegação e fazia o seletor de idioma abrir o teclado. Não altera o
 * HTML da página nem depende das classes do site.
 */
class TvCursorWebView(context: Context) : WebView(context) {

  /** Imagem do cursor, sobreposta ao WebView; posição em px relativa à view. */
  var cursor: View? = null

  init {
    webChromeClient =
      object : WebChromeClient() {
        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
          Log.d("TvCursor", "${message.message()} [l:${message.lineNumber()}]")
          return true
        }
      }
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    when (event.keyCode) {
      KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP -> {
        if (event.action == KeyEvent.ACTION_DOWN) moveCursor(0, -STEP)
        return true
      }
      KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN -> {
        if (event.action == KeyEvent.ACTION_DOWN) moveCursor(0, STEP)
        return true
      }
      KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> {
        if (event.action == KeyEvent.ACTION_DOWN) moveCursor(-STEP, 0)
        return true
      }
      KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> {
        if (event.action == KeyEvent.ACTION_DOWN) moveCursor(STEP, 0)
        return true
      }
      KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
        if (event.action == KeyEvent.ACTION_UP) clickAtCursor()
        return true
      }
      else -> return super.dispatchKeyEvent(event)
    }
  }

  private fun moveCursor(dx: Int, dy: Int) {
    val c = cursor ?: return
    val cw = c.width
    val ch = c.height
    val nx = (c.x + dx).coerceIn(0f, (width - cw).toFloat())
    // Nas bordas verticais, em vez de sair do ecrã, o cursor "empurra" o scroll da página.
    val ny = when {
      dy > 0 && c.y + dy > height - ch -> {
        if (canScrollVertically(1)) scrollBy(0, dy)
        (height - ch).toFloat()
      }
      dy < 0 && c.y + dy < 0f -> {
        if (canScrollVertically(-1)) scrollBy(0, dy)
        0f
      }
      else -> c.y + dy
    }
    c.x = nx
    c.y = ny
  }

  private fun clickAtCursor() {
    val c = cursor ?: return
    val x = (c.x + c.width / 2).toInt()
    val y = (c.y + c.height / 2).toInt()
    // O viewport da página é em CSS px (ex.: 960) e o WebView em px de ecrã (1920):
    // converte-se pela escala antes do elementFromPoint.
    evaluateJavascript(
      "(function(){var innerW=window.innerWidth,innerH=window.innerHeight;" +
        "var sx=innerW/$width, sy=innerH/$height;" +
        "var e=document.elementFromPoint($x*sx,$y*sy);" +
        "if(!e)return;var a=e.closest?e.closest('a'):null;" +
        "if(a){a.removeAttribute('target');a.click();}else if(e.click){e.click();}})();",
      null,
    )
  }

  private companion object {
    /** Passo do cursor por tecla (px). O repeat do comando dá movimento contínuo. */
    const val STEP = 40
  }
}
