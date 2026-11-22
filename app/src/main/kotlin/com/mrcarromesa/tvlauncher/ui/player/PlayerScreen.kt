package com.mrcarromesa.tvlauncher.ui.player

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Ecrã de reprodução (contracts/jw-sources.md §6): Media3 `ExoPlayer` + `PlayerView`
 * com `videoUrl`, controlador visível, OK = play/pause, Voltar = sair e libertar.
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
    factory = { ctx: Context ->
      PlayerView(ctx).apply {
        useController = true
        setPlayer(player)
      }
    },
  )
}