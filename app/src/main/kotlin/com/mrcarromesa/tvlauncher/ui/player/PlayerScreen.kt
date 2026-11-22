package com.mrcarromesa.tvlauncher.ui.player

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
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
 * OK = play/pause, Voltar = sair e libertar. Usa `tv_player_view`/`tv_control_view`
 * porque o layout padrão do Media3 1.3 não tem botão de velocidade.
 * O foco é pedido ao PlayerView para o D-pad abrir o controlador (na TV não há toque).
 */
@Composable
fun PlayerScreen(
  videoUrl: String,
  title: String,
  posterUrl: String?,
  onClose: () -> Unit,
) {
  BackHandler { onClose() }

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
      // D-pad: sem foco, o PlayerView nunca recebe as teclas da TV e o controlador não abre.
      view.post { view.requestFocus() }
      // Quando o controlador se auto-oculta, o foco fica preso nos botões ocultos e o
      // OK deixa de funcionar; devolve-se o foco ao PlayerView para reabrir o controlador.
      view.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
        if (visibility != View.VISIBLE) view.requestFocus()
      })
      view
    },
  )
}
