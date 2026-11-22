package com.mrcarromesa.tvlauncher.ui.reader

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URI

/**
 * Ecrã de leitura (contracts/jw-sources.md §6): WebView com o `pageUrl`,
 * JavaScript ativo só para injetar CSS de leitura; navegação limitada a
 * `jw.org`/`wol.jw.org`; sem `addJavascriptInterface`. Voltar fecha.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReaderScreen(
  pageUrl: String,
  onClose: () -> Unit,
) {
  BackHandler { onClose() }

  AndroidView(
    factory = { context ->
      WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.javaScriptEnabled = true
        webViewClient =
          object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
              // Allowlist jw.org / wol.jw.org (contracts/jw-sources.md §6).
              val host = runCatching { URI(request.url.toString()).host }.getOrNull()
              return host?.let { !(it == "www.jw.org" || it == "wol.jw.org") } ?: true
            }
          }
        loadUrl(pageUrl)
        // Modo leitura: esconder navegação e maximizar o texto (injeção de CSS).
        evaluateJavascript(
          """
          (function() {
            var css = 'header,nav,footer,.header,.nav,.footer{display:none !important}' +
                      'body{margin:24px;font-size:26px;line-height:1.5}';
            var style = document.createElement('style');
            style.textContent = css;
            document.head.appendChild(style);
          })();
          """.trimIndent(),
          null,
        )
      }
    },
  )
}