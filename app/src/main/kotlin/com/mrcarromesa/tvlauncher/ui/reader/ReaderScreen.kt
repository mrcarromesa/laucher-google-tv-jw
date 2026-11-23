package com.mrcarromesa.tvlauncher.ui.reader

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.mrcarromesa.tvlauncher.R
import java.net.URI

/**
 * Ecrã de leitura (contracts/jw-sources.md §6): WebView com o `pageUrl` e um cursor
 * virtual sobreposto ([TvCursorWebView]): D-pad move o cursor, OK clica o elemento
 * nesse ponto, e nas bordas verticais o cursor rola a página. Sem alterar o HTML nem
 * depender das classes do site. Navegação limitada a `jw.org`/`wol.jw.org`;
 * sem `addJavascriptInterface`; JS só para dispensar o aviso de cookies. Voltar fecha.
 * Fullscreen de vídeo: clique no botão fullscreen do player abre no browser TV externo
 * (ex.: `com.internet.tvbrowser`) via intent — mais confiável no Android TV.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReaderScreen(
  pageUrl: String,
  onClose: () -> Unit,
) {
  BackHandler { onClose() }

  // Guarda o WebView para o destruir ao sair (para o vídeo/áudio parar e libertar memória).
  val webHolder = androidx.compose.runtime.remember { arrayOfNulls<TvCursorWebView>(1) }
  AndroidView(
    modifier = Modifier.fillMaxSize(),
    factory = { context ->
      FrameLayout(context).apply {
        val web = TvCursorWebView(context).apply {
          layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
          settings.javaScriptEnabled = true
          settings.domStorageEnabled = true
          isFocusable = true
          isFocusableInTouchMode = true
          onVideoFullscreen = { videoUrl ->
            // Abre o vídeo no browser TV externo (ex.: com.internet.tvbrowser)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              .setPackage("com.internet.tvbrowser") // Browser TV específico
            try {
              context.startActivity(intent)
            } catch (e: Exception) {
              // Fallback: tenta abrir com qualquer app que suporte VIEW
              intent.setPackage(null)
              context.startActivity(intent)
            }
          }
        }
        webHolder[0] = web
        web.setFullscreenContainer(this)
        web.webViewClient =
          object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
              // Allowlist jw.org / wol.jw.org (contracts/jw-sources.md §6).
              val host = runCatching { URI(request.url.toString()).host }.getOrNull()
              val allowed = host == "www.jw.org" || host == "wol.jw.org"
              if (allowed && request.isForMainFrame()) {
                // Só os cliques do cursor criam histórico (não redirects nem voltas).
                val from = view.url
                if (web.wasClickNavigation() && from != null && from != request.url.toString() && from != "about:blank") {
                  web.pushNavigation(from)
                }
              }
              return !allowed
            }

            override fun onPageFinished(view: WebView, url: String) {
              // Dispensa o aviso de cookies (conteúdo bloqueado até aceitar); re-aplica em cada página.
              view.evaluateJavascript(DISMISS_CONSENT_JS, null)
              // Garante foco no WebView após cada carregamento.
              view.post { (view as? TvCursorWebView)?.ensureWebViewFocus() }
            }
          }
        web.loadUrl(pageUrl)
        // D-pad: sem foco, o WebView nunca recebe as teclas da TV.
        web.post { web.requestFocus() }
        val back = ImageView(context).apply {
          setImageResource(R.drawable.tv_back)
          isFocusable = false
          contentDescription = "Voltar"
          setOnClickListener { if (!web.goBackInStack()) onClose() }
        }
        web.backButton = back
        val cursor = ImageView(context).apply {
          setImageResource(R.drawable.tv_cursor)
          isFocusable = false
        }
        web.cursor = cursor
        addView(web)
        val density = context.resources.displayMetrics.density
        addView(
          back,
          FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            setMargins((20 * density).toInt(), (20 * density).toInt(), 0, 0)
          },
        )
        addView(cursor, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // Cursor começa ao centro.
        cursor.post {
          cursor.x = (width - cursor.width) / 2f
          cursor.y = (height - cursor.height) / 2f
        }
      }
    },
    // Ao sair do ecrã, destrói o WebView para parar o vídeo/áudio e não deixar estado preso.
    onRelease = { webHolder[0]?.destroy() },
  )
}

/**
 * Aceita o aviso de cookies (botão "aceitar" por texto, pt/en) para o conteúdo ficar
 * visível logo à primeira. Não altera nada da página para além desse clique.
 */
private val DISMISS_CONSENT_JS =
  """
  (function() {
    function dismissConsent() {
      var btns = document.querySelectorAll('button');
      for (var i = 0; i < btns.length; i++) {
        var t = (btns[i].textContent || '').trim().toLowerCase();
        if (/^(aceitar|accept|concordo|agree)/.test(t)) { btns[i].click(); return; }
      }
    }
    dismissConsent();
    setTimeout(dismissConsent, 1500);
  })();
  """.trimIndent()
