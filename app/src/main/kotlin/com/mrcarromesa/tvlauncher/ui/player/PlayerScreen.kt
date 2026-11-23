package com.mrcarromesa.tvlauncher.ui.player

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.mrcarromesa.tvlauncher.R

/**
 * Ecrã de reprodução (contracts/jw-sources.md §6): Media3 `ExoPlayer` + `PlayerView`
 * com `videoUrl`, controlador visível (play/pause, ±10 s, velocidade 0,25×–1,5×),
 * OK = play/pause, Voltar = sair do fullscreen primeiro, depois fechar player.
 * Usa `tv_player_view`/`tv_control_view`.
 * No Android TV, o PlayerView em fullscreen consome o BACK para sair do fullscreen.
 * Interceptamos BACK no OnKeyListener: se PlayerView está em fullscreen (preenche a tela),
 * consome o evento; senão propaga para fechar o player.
 */
@Composable
fun PlayerScreen(
  videoUrl: String,
  title: String,
  posterUrl: String?,
  onClose: () -> Unit,
) {
  var isFullscreen by remember { mutableStateOf(false) }

  val context = androidx.compose.ui.platform.LocalContext.current
  val player = remember {
    ExoPlayer.Builder(context).build().apply {
      setMediaItem(
        MediaItem.Builder()
          .setUri(videoUrl)
          .setMimeType(MimeTypes.VIDEO_MP4)
          .build(),
      )
      prepare()
      playWhenReady = true
    }
  }

  DisposableEffect(player) {
    onDispose {
      player.release()
    }
  }

  AndroidView(
    modifier = Modifier.fillMaxSize(),
    factory = { ctx: Context ->
      val view = LayoutInflater.from(ctx).inflate(R.layout.tv_player_view, null) as PlayerView
      view.setPlayer(player)
      view.post { view.requestFocus() }

      // Detecta fullscreen no Android TV: verifica se o PlayerView ocupa a tela inteira
      // (parent é o decorView content root, tipicamente FrameLayout).
      fun updateFullscreen(v: PlayerView) {
        val parent = v.parent as? ViewGroup
        val decorView = v.rootView
        // Fullscreen: PlayerView tem mesmo tamanho do decorView (tela cheia)
        val nowFullscreen = parent != null &&
          v.width == decorView.width &&
          v.height == decorView.height
        if (nowFullscreen != isFullscreen) {
          isFullscreen = nowFullscreen
          Log.d("PlayerScreen", "Fullscreen: $isFullscreen (view=${v.width}x${v.height}, decor=${decorView.width}x${decorView.height}, parent=${parent?.width}x${parent?.height})")
        }
      }

      view.addOnLayoutChangeListener { v: View, _, _, _, _, _, _, _, _ ->
        updateFullscreen(v as PlayerView)
      }

      // Intercepta BACK no PlayerView:
      // - Se isFullscreen=true: consome (return true) -> PlayerView sai do fullscreen
      // - Se isFullscreen=false: propaga (return false) -> LauncherRoot BackHandler fecha
      view.setOnKeyListener(View.OnKeyListener { _, keyCode, event ->
        if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
          // Atualiza estado antes de decidir
          updateFullscreen(view)
          Log.d("PlayerScreen", "BACK pressed: isFullscreen=$isFullscreen, hasFocus=${view.hasFocus()}")
          if (isFullscreen) {
            view.requestFocus()
            Log.d("PlayerScreen", "BACK consumed (fullscreen)")
            true // CONSOME: PlayerView processa e sai do fullscreen
          } else {
            Log.d("PlayerScreen", "BACK propagated (not fullscreen)")
            false // PROPAGA: LauncherRoot BackHandler chama onClose()
          }
        } else {
          false
        }
      })

      view.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
        Log.d("PlayerScreen", "Controller visibility: $visibility")
        if (visibility != View.VISIBLE) view.requestFocus()
      })
      view
    },
    update = { view ->
      val parent = view.parent as? ViewGroup
      val decorView = view.rootView
      isFullscreen = parent != null &&
        view.width == decorView.width &&
        view.height == decorView.height
    },
  )
}
