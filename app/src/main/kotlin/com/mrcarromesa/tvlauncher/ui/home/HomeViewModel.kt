package com.mrcarromesa.tvlauncher.ui.home

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrcarromesa.tvlauncher.data.AppCatalog
import com.mrcarromesa.tvlauncher.data.ConnectivityMonitor
import com.mrcarromesa.tvlauncher.data.HomeRole
import com.mrcarromesa.tvlauncher.data.Http
import com.mrcarromesa.tvlauncher.data.JsonCache
import com.mrcarromesa.tvlauncher.data.jw.JwRepository
import com.mrcarromesa.tvlauncher.data.jw.JwSources
import com.mrcarromesa.tvlauncher.data.weather.WeatherRepository
import com.mrcarromesa.tvlauncher.data.weather.WeatherSources
import com.mrcarromesa.tvlauncher.model.DockItem
import com.mrcarromesa.tvlauncher.model.InstalledApp
import com.mrcarromesa.tvlauncher.ui.Screen
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted

/** Estado do ecrã Home para a UI (data-model.md `HomeUiState`). */
data class HomeUiState(
  val now: LocalDateTime = LocalDateTime.MIN,
  val isOnline: Boolean = false,
  val isHome: Boolean = false,
  val dock: List<DockItem> = emptyList(),
  val focusIndex: Int = 0,
)

/** Diálogo modal do Home (contracts/ui-behavior.md §3). */
sealed interface HomeDialog {
  data class AppMissing(val appName: String) : HomeDialog
  data object SetAsHome : HomeDialog
}

/** Alvo de restauração do foco ao (re)compor o Home. */
sealed interface FocusTarget {
  data class Dock(val index: Int) : FocusTarget
  data class Jw(val index: Int) : FocusTarget
}

/**
 * Estado do ecrã e do Home. O ecrã atual vive aqui (contracts/ui-behavior.md §1):
 * sobrevive a rotação e à ida a outra app.
 *
 * US1: dock/foco/isHome · US2: now/isOnline · US3: weather · US4: jw.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

  private val appContext = application
  private val http = Http()
  private val filesDir = appContext.filesDir

  private val appCatalog = AppCatalog(appContext.packageManager)
  private val homeRole = HomeRole(appContext)
  private val connectivity = ConnectivityMonitor(appContext)

  private val weatherRepository =
    WeatherRepository(
      sources = WeatherSources(http, filesDir),
      cache = JsonCache(filesDir),
    )
  private val jwRepository =
    JwRepository(
      sources = JwSources(http),
      cache = JsonCache(filesDir),
    )

  // --- Ecrã (contracts/ui-behavior.md §1) ---

  private val _screen = MutableStateFlow<Screen>(Screen.Home)
  val screen: StateFlow<Screen> = _screen.asStateFlow()

  // --- HomeUiState ---

  private val _now = MutableStateFlow(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES))
  private val _isHome = MutableStateFlow(homeRole.isHome())
  private val _dock = MutableStateFlow(buildDock())
  private val _focusIndex = MutableStateFlow(0)
  private val _dialog = MutableStateFlow<HomeDialog?>(null)
  private val _launching = MutableStateFlow(false)
  private val _jwFocusIndex = MutableStateFlow(0)
  private val _focusTarget = MutableStateFlow<FocusTarget>(FocusTarget.Dock(0))

  val dialog: StateFlow<HomeDialog?> = _dialog.asStateFlow()
  val jwFocusIndex: StateFlow<Int> = _jwFocusIndex.asStateFlow()

  /** Onde o foco deve repousar quando o Home é (re)composto (contracts/ui-behavior.md §1). */
  val focusTarget: StateFlow<FocusTarget> = _focusTarget.asStateFlow()

  val uiState: StateFlow<HomeUiState> =
    combine(_now, connectivity.isOnline, _isHome, _dock, _focusIndex) { now, online, isHome, dock, focus ->
      HomeUiState(now = now, isOnline = online, isHome = isHome, dock = dock, focusIndex = focus)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

  val weatherState = weatherRepository.state
  val jwState = jwRepository.state

  init {
    // `now` atualiza-se ao minuto (FR-010); a cada 15 min verifica clima/JW (R7).
    viewModelScope.launch {
      var minutes = 0L
      while (true) {
        minutes++
        _now.value = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
        if (minutes % 15L == 0L) {
          val nowOk = Instant.now()
          weatherRepository.tick(nowOk)
          jwRepository.tick(nowOk)
        }
        kotlinx.coroutines.delay(60_000L)
      }
    }
  }

  /** Chamado pela activity ao voltar a estar visível (volta de app / primeiro arranque). */
  fun onResume() {
    _launching.value = false
    _isHome.value = homeRole.isHome()
    weatherRepository.tick()
    jwRepository.tick()
  }

  // --- Foco (contracts/ui-behavior.md §2) ---

  fun onDockFocused(index: Int) {
    _focusIndex.value = index
  }

  fun goHome() {
    val previous = _screen.value
    _screen.value = Screen.Home
    when (previous) {
      is Screen.AllApps -> {
        // Voltar da grelha repõe o foco em "Todos os Apps" (índice 4) (contracts/ui-behavior.md §2).
        _focusIndex.value = 4
        _focusTarget.value = FocusTarget.Dock(4)
      }
      is Screen.Reader, is Screen.Player -> {
        // Voltar de um conteúdo repõe o foco na entrada JW de origem (contracts/ui-behavior.md §1).
        _focusTarget.value = FocusTarget.Jw(_jwFocusIndex.value)
      }
      else -> _focusTarget.value = FocusTarget.Dock(_focusIndex.value)
    }
  }

  /** Entrada JW focada (↑ no dock → última; ↓ regressa ao dock no índice que estava focado). */
  fun onJwFocused(index: Int) {
    _jwFocusIndex.value = index
  }

  /** ↑ no dock → última entrada JW focada (contracts/ui-behavior.md §2). */
  fun jwFocusForDockUp(): Int = _jwFocusIndex.value

  /** Voltar em Home não faz nada (é o HOME). */
  fun onBack() = goHome()

  fun openAllApps() {
    _screen.value = Screen.AllApps
  }

  fun openSettings() {
    try {
      val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      appContext.startActivity(intent)
    } catch (_: Exception) {}
  }

  fun openSearch() {
    try {
      val intent = Intent(Intent.ACTION_ASSIST).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      appContext.startActivity(intent)
    } catch (_: Exception) {
      try {
        val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(searchIntent)
      } catch (_: Exception) {}
    }
  }

  fun showSetAsHomeDialog() {
    _dialog.value = HomeDialog.SetAsHome
  }

  // --- Ações do dock (contracts/app-catalog.md §3–§4) ---

  fun onDockOk(item: DockItem) {
    when (item) {
      is DockItem.App ->
        if (item.installed) launchPackage(item.packageName)
        else _dialog.value = HomeDialog.AppMissing(item.label)
      is DockItem.Hdmi -> switchHdmi(item.index)
      DockItem.AllApps -> _screen.value = Screen.AllApps
    }
  }

  /**
   * Troca de entrada HDMI via passthrough do TvInput.
   * Resolve dinamicamente via TvInputManager ou usa o identificador verificado na TV:
   * HDMI 1 = HW4 (hdmi_port=1), HDMI 2 = HW5 (hdmi_port=2).
   */
  fun switchHdmi(index: Int) {
    if (_launching.value) return
    _launching.value = true
    val inputId = resolveHdmiInputId(index)
    val uri = TvContract.buildChannelUriForPassthroughInput(inputId)
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
      appContext.startActivity(intent)
    } catch (e: Exception) {
      _launching.value = false
    }
  }

  private fun resolveHdmiInputId(index: Int): String {
    try {
      val manager = appContext.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
      val hdmiInputs = manager?.tvInputList
        ?.filter { it.type == TvInputInfo.TYPE_HDMI || it.id.contains("hdmi", ignoreCase = true) }
        ?.sortedBy { it.id }
      if (!hdmiInputs.isNullOrEmpty()) {
        val matchByLabel = hdmiInputs.firstOrNull {
          val label = it.loadLabel(appContext)?.toString() ?: ""
          val custom = it.loadCustomLabel(appContext)?.toString() ?: ""
          label.contains(index.toString()) || custom.contains(index.toString())
        }
        if (matchByLabel != null) {
          return matchByLabel.id
        }
        val pos = index - 1
        if (pos in hdmiInputs.indices) {
          return hdmiInputs[pos].id
        }
      }
    } catch (_: Exception) {
      // Falha ao consultar TvInputManager, usa fallback
    }
    val fallbackHardwareId = if (index == 1) HDMI_HW1 else HDMI_HW2
    return "com.mediatek.tvinput/.hdmi.HDMIInputService/$fallbackHardwareId"
  }

  fun dismissDialog() {
    _dialog.value = null
  }

  /** Guarda `launching`: duas pressões rápidas em OK não lançam duas instâncias. */
  fun launchPackage(packageName: String, fromAllApps: Boolean = false) {
    if (_launching.value) return
    _launching.value = true
    val intent =
      appCatalog.launchIntent(packageName)
        ?: appContext.packageManager.getLeanbackLaunchIntentForPackage(packageName)
        ?: appContext.packageManager.getLaunchIntentForPackage(packageName)
    if (intent == null) {
      // App da grelha indisponível no momento do OK: diálogo idêntico; a grelha mantém-se.
      _dialog.value = HomeDialog.AppMissing(packageName)
      _launching.value = false
      return
    }
    try {
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      appContext.startActivity(intent)
      if (!fromAllApps) _screen.value = Screen.Home
    } catch (e: ActivityNotFoundException) {
      _dialog.value = HomeDialog.AppMissing(packageName)
      _launching.value = false
    }
  }

  fun openReader(pageUrl: String) {
    _screen.value = Screen.Reader(pageUrl)
  }

  fun openPlayer(videoUrl: String, title: String, posterUrl: String?) {
    _screen.value = Screen.Player(videoUrl, title, posterUrl)
  }

  // --- construções ---

  private fun buildDock(): List<DockItem> {
    val installed = appCatalog.installedApps()
    fun installedAppOrNull(pkg: String): InstalledApp? = installed.firstOrNull { it.packageName == pkg }
    val netflix = installedAppOrNull(DOCK_NETFLIX_PKG)
    val youtube = installedAppOrNull(DOCK_YOUTUBE_PKG)
    return listOf(
      DockItem.App(
        packageName = DOCK_NETFLIX_PKG,
        installed = netflix != null,
        icon = netflix?.icon,
        order = 0,
        label = netflix?.label ?: appContext.getString(com.mrcarromesa.tvlauncher.R.string.dock_netflix),
      ),
      DockItem.App(
        packageName = DOCK_YOUTUBE_PKG,
        installed = youtube != null,
        icon = youtube?.icon,
        order = 1,
        label = youtube?.label ?: appContext.getString(com.mrcarromesa.tvlauncher.R.string.dock_youtube),
      ),
      DockItem.Hdmi(1),
      DockItem.Hdmi(2),
      DockItem.AllApps,
    )
  }

  companion object {
    const val DOCK_NETFLIX_PKG = "com.netflix.ninja"
    const val DOCK_YOUTUBE_PKG = "com.google.android.youtube.tv"
    // Portas HDMI na TV (dumpsys tv_input):
    // HDMI 1 = HW4 (hdmi_port=1), HDMI 2 = HW5 (hdmi_port=2), HDMI 3 = HW6 (hdmi_port=3).
    const val HDMI_HW1 = "HW4"
    const val HDMI_HW2 = "HW5"
  }
}
