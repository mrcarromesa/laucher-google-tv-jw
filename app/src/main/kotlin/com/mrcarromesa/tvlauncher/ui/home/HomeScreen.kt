package com.mrcarromesa.tvlauncher.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.mrcarromesa.tvlauncher.R
import com.mrcarromesa.tvlauncher.data.jw.JwState
import com.mrcarromesa.tvlauncher.data.weather.WeatherCode
import com.mrcarromesa.tvlauncher.data.weather.WeatherState
import com.mrcarromesa.tvlauncher.model.DockItem
import com.mrcarromesa.tvlauncher.model.WeatherSnapshot
import com.mrcarromesa.tvlauncher.ui.drawableToImageBitmap
import com.mrcarromesa.tvlauncher.ui.handleDPadKeyEvents
import com.mrcarromesa.tvlauncher.ui.theme.Ink
import com.mrcarromesa.tvlauncher.ui.theme.InkMuted
import com.mrcarromesa.tvlauncher.ui.theme.Missing
import com.mrcarromesa.tvlauncher.ui.theme.glassPanel
import com.mrcarromesa.tvlauncher.ui.theme.tvFocusRing
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, d MMM", Locale("pt", "PT"))

private const val DOCK_SIZE = 5
private const val JW_ENTRY_COUNT = 4

/**
 * Home (contracts/ui-behavior.md §7): Box raiz com margem segura (48/32 dp),
 * fundo desfocado UMA vez (R1), banner HOME, cabeçalho, clima+JW ao centro e dock.
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val weather by viewModel.weatherState.collectAsStateWithLifecycle()
  val jw by viewModel.jwState.collectAsStateWithLifecycle()
  val dialog by viewModel.dialog.collectAsStateWithLifecycle()
  val focusTarget by viewModel.focusTarget.collectAsStateWithLifecycle()

  val dockRequesters = remember { List(DOCK_SIZE) { FocusRequester() } }
  val jwRequesters = remember { List(JW_ENTRY_COUNT) { FocusRequester() } }

  // Restaura o foco ao (re)compor o Home (volta da grelha/leitura/player);
  // ao regressar de outra app o foco é restaurado pelo sistema (window focus).
  fun requestInitialFocus() {
    val target = focusTarget
    when (target) {
      is FocusTarget.Dock -> dockRequesters[target.index.coerceIn(0, DOCK_SIZE - 1)].requestFocus()
      is FocusTarget.Jw -> jwRequesters[target.index.coerceIn(0, JW_ENTRY_COUNT - 1)].requestFocus()
    }
  }
  LaunchedEffect(focusTarget) {
    // A primeira composição ainda não anexou os FocusRequester; esperar um frame.
    androidx.compose.runtime.withFrameNanos { }
    requestInitialFocus()
  }

  val background = painterResource(R.drawable.bg_launcher)

  Box(modifier = Modifier.fillMaxSize()) {
    Image(
      painter = background,
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize().blur(48.dp), // desfocada uma vez; nunca por frame (R1)
    )

    // Banner HOME: sobreposição no topo, não focável e não bloqueante
    // (FR-008 — "não interfere com o D-pad"). O fluxo abaixo desloca-se para
    // dar lugar ao banner quando visível (padding top condicional).
    if (!uiState.isHome) {
      Box(
        modifier =
          Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(horizontal = 96.dp)
            .glassPanel()
            .padding(horizontal = 16.dp, vertical = 8.dp),
      ) {
        Text(
          text =
            stringResource(R.string.home_banner) + "  " + stringResource(R.string.home_banner_note),
          color = InkMuted,
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }

    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Header(now = uiState.now, isOnline = uiState.isOnline, weather = weather)

      Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
        WeatherPanel(
          state = weather,
          modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 16.dp),
        )
        JwPanel(
          state = jw,
          dockFocusIndex = uiState.focusIndex,
          onJwFocus = viewModel::onJwFocused,
          jwRequesters = jwRequesters,
          dockRequesters = dockRequesters,
          onJwEntryClick = {
            when (it) {
              is JwEntryData.Text -> viewModel.openReader(it.pageUrl)
              is JwEntryData.Video -> viewModel.openPlayer(it.videoUrl, it.title, it.posterUrl)
            }
          },
          modifier = Modifier.weight(1f).fillMaxHeight(),
        )
      }

      DockRow(
        dock = uiState.dock,
        viewModel = viewModel,
        requesters = dockRequesters,
        jwRequesters = jwRequesters,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }

  dialog?.let { DialogCard(it, onClose = viewModel::dismissDialog) }

  // Voltar fecha o diálogo (contracts/ui-behavior.md §3); Voltar no Home sem
  // diálogo não faz nada (é o HOME).
  BackHandler(enabled = dialog != null) { viewModel.dismissDialog() }
}

// ---------------------------------------------------------------- cabeçalho (US2/US3)

@Composable
private fun Header(now: LocalDateTime, isOnline: Boolean, weather: WeatherState) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
    Text(now.format(TIME_FORMAT), color = Ink, style = MaterialTheme.typography.titleLarge)
    Text(now.format(DATE_FORMAT), color = InkMuted, style = MaterialTheme.typography.titleLarge)
    if (isOnline) {
      Text("● " + stringResource(R.string.header_wifi), color = Ink, style = MaterialTheme.typography.titleMedium)
    } else {
      Text("○ " + stringResource(R.string.header_no_connection), color = Missing, style = MaterialTheme.typography.titleMedium)
    }
    // FR-009: a temperatura do cabeçalho é exatamente a do painel; omitida se indisponível.
    val current = (weather as? WeatherState.Available)?.snapshot?.currentTempC
    if (current != null) {
      Text("${current.toInt()}°", color = Ink, style = MaterialTheme.typography.titleLarge)
    }
  }
}

// ---------------------------------------------------------------- painel clima (US3)

@Composable
private fun WeatherPanel(state: WeatherState, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.glassPanel().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    when (state) {
      WeatherState.Loading -> Column(modifier = Modifier.height(220.dp)) { Text("…", color = InkMuted) }
      is WeatherState.Unavailable -> {
        Text(stringResource(R.string.content_unavailable), color = InkMuted, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.weather_data_source), color = InkMuted, style = MaterialTheme.typography.bodyMedium)
      }
      is WeatherState.Available -> WeatherContent(state.snapshot)
    }
  }
}

@Composable
private fun WeatherContent(snapshot: WeatherSnapshot) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text("${snapshot.currentTempC.toInt()}°  ${WeatherCode.condition(snapshot.currentCode).labelPt}",
      color = Ink, style = MaterialTheme.typography.headlineSmall)
    Text("Máx. ${snapshot.maxC.toInt()}° · Mín. ${snapshot.minC.toInt()}°", color = InkMuted, style = MaterialTheme.typography.titleMedium)

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      snapshot.hourly.take(5).forEach { h ->
        Box(modifier = Modifier.width(56.dp).glassPanel().padding(6.dp)) {
          Column {
            Text(h.localTime.format(TIME_FORMAT), color = InkMuted, style = MaterialTheme.typography.labelLarge)
            Text("${h.tempC.toInt()}°", color = Ink, style = MaterialTheme.typography.titleMedium)
          }
        }
      }
    }

    val age = Duration.between(snapshot.fetchedAt, Instant.now())
    Text(
      text = if (age > STALE_AFTER) stringResource(R.string.weather_no_recent)
      else stringResource(R.string.weather_updated, idade(age)),
      color = InkMuted,
      style = MaterialTheme.typography.bodyMedium,
    )
    Text(stringResource(R.string.weather_data_source), color = InkMuted, style = MaterialTheme.typography.bodyMedium)
  }
}

private val STALE_AFTER = Duration.ofHours(24)

// ---------------------------------------------------------------- painel JW.org (US4)

private sealed interface JwEntryData {
  data class Text(val pageUrl: String) : JwEntryData
  data class Video(val title: String, val videoUrl: String, val posterUrl: String?) : JwEntryData
}

@Composable
private fun JwPanel(
  state: JwState,
  dockFocusIndex: Int,
  onJwFocus: (Int) -> Unit,
  jwRequesters: List<FocusRequester>,
  dockRequesters: List<FocusRequester>,
  onJwEntryClick: (JwEntryData) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.glassPanel().padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    when (state) {
      JwState.Loading -> Column(modifier = Modifier.height(220.dp)) { Text("…", color = InkMuted) }
      is JwState.Unavailable -> {
        Text(stringResource(R.string.content_unavailable), color = InkMuted)
        state.lastAttemptAt?.let {
          Spacer(Modifier.height(8.dp))
          Text("Última atualização: ${it.toString().take(10)}", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
        }
      }
      is JwState.Available -> {
        val s = state.snapshot
        JwEntry(
          label = stringResource(R.string.jw_whats_new),
          title = s.whatsNew?.title,
          imageUrl = s.whatsNew?.imageUrl,
          index = 0,
          focusRequester = jwRequesters[0],
          onFocus = { onJwFocus(0) },
          onClick = s.whatsNew?.let { { onJwEntryClick(JwEntryData.Text(it.pageUrl)) } },
        )
        JwEntry(
          label = stringResource(R.string.jw_daily_text),
          title = s.dailyText?.let { "${it.dateLabel} · ${it.scripture}" },
          imageUrl = null,
          index = 1,
          focusRequester = jwRequesters[1],
          onFocus = { onJwFocus(1) },
          onClick = s.dailyText?.let { { onJwEntryClick(JwEntryData.Text(it.pageUrl)) } },
        )
        JwEntry(
          label = stringResource(R.string.jw_broadcasting),
          title = s.broadcasting?.title,
          imageUrl = s.broadcasting?.posterUrl,
          index = 2,
          focusRequester = jwRequesters[2],
          onFocus = { onJwFocus(2) },
          onClick = s.broadcasting?.let { { onJwEntryClick(JwEntryData.Video(it.title, it.videoUrl, it.posterUrl)) } },
        )
        JwEntry(
          label = stringResource(R.string.jw_governing_body),
          title = s.governingBody?.title,
          imageUrl = s.governingBody?.posterUrl,
          index = 3,
          focusRequester = jwRequesters[3],
          onFocus = { onJwFocus(3) },
          onClick = s.governingBody?.let { { onJwEntryClick(JwEntryData.Video(it.title, it.videoUrl, it.posterUrl)) } },
          onLastDownToDock = { dockRequesters[dockFocusIndex.coerceIn(0, DOCK_SIZE - 1)].requestFocus() },
        )
        Text(
          text = stringResource(R.string.jw_updated, idade(Duration.between(s.fetchedAt, Instant.now()))),
          color = InkMuted,
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
  }
}

@Composable
private fun JwEntry(
  label: String,
  title: String?,
  imageUrl: String?,
  index: Int,
  focusRequester: FocusRequester,
  onFocus: () -> Unit,
  onClick: (() -> Unit)?,
  onLastDownToDock: () -> Unit = {},
) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .focusRequester(focusRequester)
        .focusable(interactionSource = interaction)
        .then(Modifier.tvFocusRing(focused))
        .handleDPadKeyEvents(
          onLeft = {}, // ← sem efeito no painel JW
          onRight = {}, // → sem efeito no painel JW
          onDown = { if (index == JW_ENTRY_COUNT - 1) onLastDownToDock() },
          onEnter = { onClick?.invoke() },
        )
        .onFocusChanged { state -> if (state.isFocused) onFocus() }
        .glassPanel()
        .padding(horizontal = 8.dp, vertical = 2.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    if (imageUrl != null) {
      AsyncImage(model = imageUrl, contentDescription = label, modifier = Modifier.width(48.dp).height(27.dp))
    }
    Column {
      Text(label, color = InkMuted, style = MaterialTheme.typography.labelLarge, maxLines = 1)
      Text(
        title ?: stringResource(R.string.content_unavailable),
        color = if (title == null) Missing else Ink,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

// ---------------------------------------------------------------- dock (US1)

@Composable
private fun DockRow(
  dock: List<DockItem>,
  viewModel: HomeViewModel,
  requesters: List<FocusRequester>,
  jwRequesters: List<FocusRequester>,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.height(132.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    dock.forEach { item ->
      DockCard(
        item = item,
        viewModel = viewModel,
        requester = requesters[item.order.coerceIn(0, DOCK_SIZE - 1)],
        jwRequesters = jwRequesters,
      )
      Spacer(Modifier.width(20.dp))
    }
  }
}

@Composable
private fun DockCard(
  item: DockItem,
  viewModel: HomeViewModel,
  requester: FocusRequester,
  jwRequesters: List<FocusRequester>,
) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  val installed = (item as? DockItem.App)?.installed ?: true

  Column(
    modifier =
      Modifier
        .width(168.dp)
        .height(120.dp)
        .focusRequester(requester)
        .focusable(interactionSource = interaction)
        .then(Modifier.tvFocusRing(focused))
        .handleDPadKeyEvents(
          onUp = {
            // ↑ no dock → última entrada JW focada (contracts/ui-behavior.md §2).
            val jw = viewModel.jwFocusForDockUp().coerceIn(0, jwRequesters.size - 1)
            jwRequesters[jw].requestFocus()
          },
          onEnter = { viewModel.onDockOk(item) },
        )
        .onFocusChanged { state -> if (state.isFocused) viewModel.onDockFocused(item.order) }
        .glassPanel(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    val icon = (item as? DockItem.App)?.icon
    if (icon != null) {
      Image(
        bitmap = drawableToImageBitmap(icon),
        contentDescription = item.contentDescription(),
        modifier = Modifier.width(64.dp).alpha(if (installed) 1f else 0.35f),
      )
    }
    Spacer(Modifier.height(6.dp))
    Text(item.label, color = Ink, style = MaterialTheme.typography.labelLarge)
    if (!installed) {
      Text(stringResource(R.string.dock_unavailable), color = Missing, style = MaterialTheme.typography.labelLarge)
    }
  }
}

private fun DockItem.contentDescription(): String =
  when (this) {
    is DockItem.App -> if (installed) label else "$label, indisponível"
    is DockItem.Hdmi -> label
    DockItem.AllApps -> label
  }

// ---------------------------------------------------------------- diálogo (US1)

@Composable
private fun DialogCard(dialog: HomeDialog, onClose: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier =
        Modifier
          .width(560.dp)
          .glassPanel()
          .padding(28.dp)
          .focusable(interactionSource = interaction)
          .then(Modifier.tvFocusRing(false))
          .handleDPadKeyEvents(onEnter = { onClose() }),
    ) {
      Column {
        Text(
          stringResource(R.string.dialog_app_missing_title, (dialog as HomeDialog.AppMissing).appName),
          color = Ink,
          style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(12.dp))
        Text(
          stringResource(R.string.dialog_app_missing_body),
          color = InkMuted,
          style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.dialog_ok), color = Ink, style = MaterialTheme.typography.labelLarge)
      }
    }
  }
}

private fun idade(age: Duration): String =
  if (age < Duration.ofHours(1)) {
    "${maxOf(age.toMinutes(), 1)} min"
  } else {
    "${age.toHours()} h"
  }