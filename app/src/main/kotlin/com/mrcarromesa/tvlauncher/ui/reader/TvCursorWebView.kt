package com.mrcarromesa.tvlauncher.ui.reader

import android.content.Context
import android.os.Message
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

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

  /** Botão "voltar" (overlay da app); ativado quando o cursor clica em cima dele. */
  var backButton: View? = null

  /** Pilha manual de URLs — o histórico nativo do WebView não regista a navegação
   *  deste site (janelas novas encaminhadas), pelo que "voltar" segue esta pilha. */
  private val navStack = ArrayDeque<String>()

  /** Marcador de página anterior iniciada por clique do cursor. */
  private var clickNavPending = false

  /** Host reutilizado para as janelas novas (evita acumular WebViews → memória). */
  private var popupHost: WebView? = null

  /** Chamado quando o utilizador pede ecrã inteiro no vídeo do site: recebe o URL do
   *  vídeo para reproduzir no player nativo (os controlos em ecrã inteiro do site não
   *  respondem ao cursor). */
  var onVideoFullscreen: ((String) -> Unit)? = null

  /** Chamado pelo WebViewClient na navegação; true se foi iniciada por clique do cursor. */
  fun wasClickNavigation(): Boolean {
    if (clickNavPending) {
      clickNavPending = false
      return true
    }
    return false
  }

  /** O WebViewClient chama antes de cada navegação de frame principal aprovada. */
  fun pushNavigation(from: String?) {
    if (from != null && (navStack.isEmpty() || navStack.last() != from)) {
      if (navStack.size >= 50) navStack.removeFirst()
      navStack.addLast(from)
    }
  }

  /** Volta na pilha manual; true se navegou, false se não há página anterior. */
  fun goBackInStack(): Boolean {
    if (navStack.isEmpty()) return false
    loadUrl(navStack.removeLast())
    return true
  }

  init {
    // As janelas novas do site (window.open / target=_blank) chegam ao onCreateWindow;
    // sem isto são ignoradas e os itens de menu que as usam "não fazem nada".
    settings.setSupportMultipleWindows(true)
    // Permite o vídeo tocar a partir do clique sintético (o site só aceita gesto real
    // para o play; com isto a reprodução programática não é bloqueada).
    settings.mediaPlaybackRequiresUserGesture = false
    webChromeClient =
      object : WebChromeClient() {
        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
          Log.d("TvCursor", "${message.message()} [l:${message.lineNumber()}]")
          return true
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
          // Janela nova carrega no próprio WebView (TV: nada de popups).
          val popup =
            popupHost ?: WebView(view.context).also { popupHost = it }.apply {
              webViewClient =
                object : WebViewClient() {
                  override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                    this@TvCursorWebView.loadUrl(request.url.toString())
                    return true
                  }
                }
            }
          val transport = resultMsg.obj as WebView.WebViewTransport
          transport.webView = popup
          resultMsg.sendToTarget()
          return true
        }

        override fun onShowCustomView(customView: View, callback: WebChromeClient.CustomViewCallback) {
          // Em vez do ecrã inteiro do site (controlos não respondem ao cursor),
          // extrai o URL do vídeo e entrega-o ao player nativo; cancela aqui.
          val handler = onVideoFullscreen
          this@TvCursorWebView.evaluateJavascript(
            "(function(){var v=document.querySelector('video');" +
              "if(v){v.pause();}var s=v&&(v.currentSrc||v.src||(v.querySelector('source')||{}).src);" +
              "return s||'';})()",
          ) { url ->
            callback.onCustomViewHidden()
            val cleaned = url.trim().removeSurrounding("\"").ifBlank { null }
            if (cleaned != null) handler?.invoke(cleaned)
          }
          if (handler == null) callback.onCustomViewHidden()
        }
      }
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    when (event.keyCode) {
      KeyEvent.KEYCODE_BACK -> {
        // Volta na pilha manual; sem histórico, deixa o WebView/BackHandler fechar.
        if (event.action == KeyEvent.ACTION_DOWN && goBackInStack()) return true
        return super.dispatchKeyEvent(event)
      }
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
        if (event.action == KeyEvent.ACTION_UP) tapAtCursor()
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

  /** Clique sintético no elemento sob o cursor (via elementFromPoint); navegação
   *  fiável e o site abre menus. As janelas novas (target=_blank) são encaminhadas
   *  pelo onCreateWindow; o vídeo toca porque o gesto de reprodução está permitido. */
  private fun tapAtCursor() {
    val c = cursor ?: return
    val x = (c.x + c.width / 2).toInt()
    val y = (c.y + c.height / 2).toInt()
    // Botão voltar (overlay da app): se o cursor estiver em cima, ativa-o em vez da página.
    val b = backButton
    if (b != null && x in b.left..b.right && y in b.top..b.bottom) {
      b.performClick()
      return
    }
    // O viewport da página é em CSS px (ex.: 960) e o WebView em px de ecrã (1920):
    // converte-se pela escala antes do elementFromPoint.
    clickNavPending = true
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
