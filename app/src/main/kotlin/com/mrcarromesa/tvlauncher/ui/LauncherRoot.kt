package com.mrcarromesa.tvlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrcarromesa.tvlauncher.ui.apps.AllAppsScreen
import com.mrcarromesa.tvlauncher.ui.home.HomeScreen
import com.mrcarromesa.tvlauncher.ui.home.HomeViewModel
import com.mrcarromesa.tvlauncher.ui.player.PlayerScreen
import com.mrcarromesa.tvlauncher.ui.reader.ReaderScreen

/**
 * Ecrãs da app (contracts/ui-behavior.md §1):
 * `Home` → `AllApps` (Voltar → Home) · `Home → Reader/Player` (Voltar → Home foco na entrada).
 * O atual vive no [HomeViewModel], sobrevive à rotação e à ida a outra app.
 */
sealed interface Screen {
  data object Home : Screen
  data object AllApps : Screen
  data class Reader(val pageUrl: String) : Screen
  data class Player(val videoUrl: String, val title: String, val posterUrl: String?) : Screen
}

@Composable
fun LauncherRoot(viewModel: HomeViewModel = viewModel()) {
  val screen by viewModel.screen.collectAsStateWithLifecycle()

  // Re-verifica o papel HOME, liberta a guarda de lançamento e retoma os refreshes
  // ao voltar a estar visível (volta de uma app / primeiro arranque).
  androidx.lifecycle.compose.LifecycleResumeEffect(viewModel, androidx.lifecycle.compose.LocalLifecycleOwner.current) {
    viewModel.onResume()
    onPauseOrDispose { }
  }

  // Voltar em Home não faz nada (é o HOME); nos outros ecrãs volta ao Home.
  BackHandler(enabled = screen !is Screen.Home) { viewModel.goHome() }

  when (val current = screen) {
    Screen.Home -> HomeScreen(viewModel = viewModel)
    Screen.AllApps -> AllAppsScreen(
      onLaunch = { viewModel.launchPackage(it, fromAllApps = true) },
      onBack = { viewModel.goHome() },
    )
    is Screen.Reader -> ReaderScreen(pageUrl = current.pageUrl, onClose = { viewModel.goHome() })
    is Screen.Player -> PlayerScreen(
      videoUrl = current.videoUrl,
      title = current.title,
      posterUrl = current.posterUrl,
      onClose = { viewModel.goHome() },
    )
  }
}
