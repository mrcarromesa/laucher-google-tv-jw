package com.mrcarromesa.tvlauncher.ui.reader

import android.annotation.SuppressLint
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
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReaderScreen(
  pageUrl: String,
  onClose: () -> Unit,
) {
  BackHandler { onClose() }

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
          webViewClient =
            object : WebViewClient() {
              override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // Allowlist jw.org / wol.jw.org (contracts/jw-sources.md §6).
                val host = runCatching { URI(request.url.toString()).host }.getOrNull()
                return host?.let { !(it == "www.jw.org" || it == "wol.jw.org") } ?: true
              }

              override fun onPageFinished(view: WebView, url: String) {
                // Dispensa o aviso de cookies (conteúdo bloqueado até aceitar); re-aplica em cada página.
                view.evaluateJavascript(DISMISS_CONSENT_JS, null)
              }
            }
          loadUrl(pageUrl)
          // D-pad: sem foco, o WebView nunca recebe as teclas da TV.
          post { requestFocus() }
        }
        val cursor = ImageView(context).apply {
          setImageResource(R.drawable.tv_cursor)
          isFocusable = false
        }
        web.cursor = cursor
        addView(web)
        addView(cursor, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // Cursor começa ao centro.
        cursor.post {
          cursor.x = (width - cursor.width) / 2f
          cursor.y = (height - cursor.height) / 2f
        }
      }
    },
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
