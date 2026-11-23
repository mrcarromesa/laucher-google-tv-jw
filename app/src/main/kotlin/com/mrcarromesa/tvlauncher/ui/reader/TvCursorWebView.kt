package com.mrcarromesa.tvlauncher.ui.reader

import android.content.Context
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * WebView de TV para o leitor: o comando não tem rato, então o D-pad move um cursor
 * virtual sobreposto e OK clica o elemento nesse ponto (via `elementFromPoint`).
 *
 * Consome todas as setas para o Chromium não as usar para mover o foco HTML — é isso
 * que impedia a navegação e fazia o seletor de idioma abrir o teclado. Não altera o
 * HTML da página nem depende das classes do site.
 *
 * Fullscreen de vídeo via CSS (não nativo): injeta estilo para o <video> ocupar a tela.
 * O botão Voltar sai do fullscreen CSS primeiro, antes de navegar para trás.
 * Não usa onShowCustomView nem player nativo — evita problemas de foco/BACK no TV.
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

  /** Chamado quando o usuário clica no botão fullscreen do player.
   *  Abre o vídeo no browser TV externo (mais confiável no Android TV). */
  var onVideoFullscreen: ((String) -> Unit)? = null

  /** Handler para hover: mostra controles do vídeo após cursor parado sobre ele. */
  private val hoverHandler = android.os.Handler(Looper.getMainLooper())
  private var hoverRunnable: Runnable? = null
  private var lastHoverX = -1
  private var lastHoverY = -1

  /** Define o container raiz (mantido para compat). */
  fun setFullscreenContainer(container: FrameLayout) {
    Log.d("TvCursor", "setFullscreenContainer called (external browser fullscreen)")
  }

  /** Indica se está em modo fullscreen (sempre false — usamos browser externo). */
  fun isInFullscreen(): Boolean = false

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
    settings.setSupportMultipleWindows(true)
    settings.mediaPlaybackRequiresUserGesture = false
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true

    webChromeClient =
      object : WebChromeClient() {
        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
          Log.d("TvCursor", "${message.message()} [l:${message.lineNumber()}]")
          return true
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
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

        // Não implementa onShowCustomView/onHideCustomView — usamos CSS fullscreen
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
        if (event.action == KeyEvent.ACTION_UP) {
          Log.d("TvCursor", "DPAD_CENTER/ENTER pressed -> tapAtCursor")
          tapAtCursor()
        }
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

    // Hover detection: se cursor parou sobre vídeo, agenda mostra controles
    checkVideoHover(nx, ny)
  }

  /** Verifica se cursor está sobre <video> e agenda exibição de controles do player web. */
  private fun checkVideoHover(cursorX: Float, cursorY: Float) {
    // Cancela hover anterior se posição mudou significativamente
    if (abs(cursorX - lastHoverX) > 5 || abs(cursorY - lastHoverY) > 5) {
      hoverRunnable?.let { hoverHandler.removeCallbacks(it) }
      lastHoverX = cursorX.toInt()
      lastHoverY = cursorY.toInt()

      // Agenda verificação de hover sobre vídeo
      hoverRunnable = Runnable {
        checkAndShowVideoControls(cursorX.toInt(), cursorY.toInt())
      }
      hoverHandler.postDelayed(hoverRunnable!!, 800) // 0.8s parado
    }
  }

  /** Mostra controles do player web via mousemove simulado.
   *  Em fullscreen: não faz nada (cursor escondido, D-pad no player).
   *  Fora fullscreen: mostra controles via mousemove, esconde após 3s se cursor sair. */
  private fun checkAndShowVideoControls(x: Int, y: Int) {
    val inFullscreen = isInFullscreen()
    if (inFullscreen) return // Cursor escondido, D-pad controla player
    
    val js = """
      (function(){
        var innerW=window.innerWidth,innerH=window.innerHeight;
        var sx=innerW/$width, sy=innerH/$height;
        var e=document.elementFromPoint($x*sx,$y*sy);
        if(!e)return false;
        var vid=e.closest('video');
        if(vid){
          // Simula mousemove para acordar controles do player web (jw.org, etc.)
          vid.dispatchEvent(new MouseEvent('mousemove', {bubbles: true, clientX: $x*sx, clientY: $y*sy}));
          // Se pausado, garante que controles ficam visíveis
          if(vid.paused) vid.dispatchEvent(new MouseEvent('mousemove', {bubbles: true}));
          return true;
        }
        return false;
      })()
    """.trimIndent()
    evaluateJavascript(js) { result ->
      Log.d("TvCursor", "Hover video controls: $result (fullscreen=$inFullscreen)")
    }
  }

  /** Clique sintético no elemento sob o cursor (via elementFromPoint). */
  private fun tapAtCursor() {
    val c = cursor ?: return
    val x = (c.x + c.width / 2).toInt()
    val y = (c.y + c.height / 2).toInt()
    Log.d("TvCursor", "tapAtCursor: cursor=($x,$y), WebView size=${width}x${height}, visibility=${visibility}, hasFocus=${hasFocus()}")
    val b = backButton
    if (b != null && x in b.left..b.right && y in b.top..b.bottom) {
      Log.d("TvCursor", "tapAtCursor: backButton hit")
      b.performClick()
      return
    }
    clickNavPending = true

    // Detecta elemento sob o cursor: fullscreen-btn, video (play/pause), link, ou click genérico
    // Se botão fullscreen: extrai URL do vídeo e chama callback para abrir no browser TV externo
    val js = """
      (function(){
        var innerW=window.innerWidth,innerH=window.innerHeight;
        var sx=innerW/$width, sy=innerH/$height;
        var e=document.elementFromPoint($x*sx,$y*sy);
        if(!e)return 'none';
        // 1. Botão de fullscreen do player (jw.org + outros) -> extrai URL do vídeo
        var btn = e.closest('[data-fullscreen], .fullscreen-button, .vjs-fullscreen-control, .jw-fullscreen-button, [aria-label*="fullscreen" i], [title*="fullscreen" i], .plyr__control--overlaid, .mejs__fullscreen-button, .jw-icon-fullscreen, .jw-button-fullscreen, .jw-icon-inline-fullscreen, button[title*="Tela cheia" i], button[aria-label*="Tela cheia" i]');
        if(btn){
          var v=document.querySelector('video');
          var url=v&&(v.currentSrc||v.src||(v.querySelector('source')||{}).src);
          return 'fullscreen-btn:'+(url||'');
        }
        // 2. Link -> navegação
        var a = e.closest ? e.closest('a') : null;
        if(a){ a.removeAttribute('target'); a.click(); return 'link'; }
        // 3. Video -> play/pause
        var vid = e.closest('video');
        if(vid){
          if(vid.paused) vid.play(); else vid.pause();
          vid.dispatchEvent(new MouseEvent('mousemove', {bubbles: true}));
          return 'video-playpause';
        }
        // 4. Click genérico
        if(e.click){ e.click(); return 'click'; }
        return 'none';
      })()
    """.trimIndent()
    evaluateJavascript(js) { result ->
      val cleaned = result.trim().removeSurrounding("\"")
      Log.d("TvCursor", "tapAtCursor result: $cleaned")
      when (cleaned) {
        "video-playpause" -> { /* play/pause já feito no JS */ }
        else -> {
          if (cleaned.startsWith("fullscreen-btn:")) {
            val url = cleaned.substringAfter("fullscreen-btn:").ifBlank { null }
            if (url != null) {
              Log.d("TvCursor", "Fullscreen button clicked, video URL: $url")
              onVideoFullscreen?.invoke(url)
            } else {
              Log.w("TvCursor", "Fullscreen button clicked but no video URL found")
            }
          }
          /* navegação normal */
        }
      }
    }
  }

  /** Força foco no WebView — útil após sair do fullscreen. */
  fun ensureWebViewFocus() {
    Log.d("TvCursor", "ensureWebViewFocus called")
    requestFocus()
    isFocusable = true
    isFocusableInTouchMode = true
  }

  private companion object {
    /** Passo do cursor por tecla (px). Reduzido para 10 para melhor precisão. */
    const val STEP = 10
  }
}
