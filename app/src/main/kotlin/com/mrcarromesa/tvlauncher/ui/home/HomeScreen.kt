package com.mrcarromesa.tvlauncher.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.mrcarromesa.tvlauncher.R
import com.mrcarromesa.tvlauncher.data.jw.JwState
import com.mrcarromesa.tvlauncher.data.weather.WeatherCode
import com.mrcarromesa.tvlauncher.data.weather.WeatherState
import com.mrcarromesa.tvlauncher.model.DockItem
import com.mrcarromesa.tvlauncher.model.HourlyPoint
import com.mrcarromesa.tvlauncher.model.JwSnapshot
import com.mrcarromesa.tvlauncher.model.WeatherSnapshot
import com.mrcarromesa.tvlauncher.ui.drawableToImageBitmap
import com.mrcarromesa.tvlauncher.ui.handleDPadKeyEvents
import com.mrcarromesa.tvlauncher.ui.theme.GlassShape
import com.mrcarromesa.tvlauncher.ui.theme.GlassShapeMedium
import com.mrcarromesa.tvlauncher.ui.theme.GlassShapeSmall
import com.mrcarromesa.tvlauncher.ui.theme.Ink
import com.mrcarromesa.tvlauncher.ui.theme.InkMuted
import com.mrcarromesa.tvlauncher.ui.theme.Missing
import com.mrcarromesa.tvlauncher.ui.theme.glassPanel
import com.mrcarromesa.tvlauncher.ui.theme.tvFocusRing
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

private fun formatDate(date: LocalDateTime): String {
  val raw = date.format(DateTimeFormatter.ofPattern("EEE, d 'de' MMM", Locale("pt", "BR")))
    .replace(".", "")
  val parts = raw.split(" ")
  return parts.joinToString(" ") { part ->
    if (part.length > 2) part.replaceFirstChar { it.titlecase(Locale("pt", "BR")) } else part
  }
}

private fun weatherIconForCode(code: Int, hour: Int): Int {
  val isDay = hour in 6..18
  return when (code) {
    0, 1 -> if (isDay) R.drawable.ic_weather_sun else R.drawable.ic_weather_moon
    2 -> if (isDay) R.drawable.ic_weather_sun_cloud else R.drawable.ic_weather_cloud
    3, 45, 48 -> R.drawable.ic_weather_cloud
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> R.drawable.ic_weather_rain
    95, 96, 99 -> R.drawable.ic_weather_thunder
    else -> if (isDay) R.drawable.ic_weather_sun_cloud else R.drawable.ic_weather_cloud
  }
}

private const val DOCK_SIZE = 5
private const val JW_ENTRY_COUNT = 4

/**
 * Home Screen fiel à referência visual references/model-v1.png.
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

  fun requestInitialFocus() {
    when (val target = focusTarget) {
      is FocusTarget.Dock -> dockRequesters[target.index.coerceIn(0, DOCK_SIZE - 1)].requestFocus()
      is FocusTarget.Jw -> jwRequesters[target.index.coerceIn(0, JW_ENTRY_COUNT - 1)].requestFocus()
    }
  }

  LaunchedEffect(focusTarget) {
    androidx.compose.runtime.withFrameNanos { }
    requestInitialFocus()
  }

  Box(modifier = Modifier.fillMaxSize()) {
    // Wallpaper nítido fotográfico
    Image(
      painter = painterResource(R.drawable.bg_launcher),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 48.dp, vertical = 20.dp),
    ) {
      // 1. Barra Superior com ações interativas
      TopBar(
        now = uiState.now,
        isOnline = uiState.isOnline,
        onOpenAllApps = viewModel::openAllApps,
        onOpenSettings = viewModel::openSettings,
        onOpenSearch = viewModel::openSearch,
        onHomeClick = {
          if (!uiState.isHome) viewModel.showSetAsHomeDialog()
          else dockRequesters[0].requestFocus()
        },
      )

      Spacer(Modifier.height(14.dp))

      // 2. Linha Central de Widgets (Altura de 234 dp, proporcional à tela)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(234.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        WeatherPanel(
          state = weather,
          currentHour = if (uiState.now != LocalDateTime.MIN) uiState.now.hour else 12,
          modifier = Modifier
            .weight(0.33f)
            .fillMaxHeight(),
        )

        JwPanel(
          state = jw,
          dockFocusIndex = uiState.focusIndex,
          onJwFocus = viewModel::onJwFocused,
          jwRequesters = jwRequesters,
          dockRequesters = dockRequesters,
          onJwEntryClick = { entry ->
            when (entry) {
              is JwEntryData.Text -> viewModel.openReader(entry.pageUrl)
              is JwEntryData.Video -> viewModel.openPlayer(entry.videoUrl, entry.title, entry.posterUrl)
            }
          },
          modifier = Modifier
            .weight(0.67f)
            .fillMaxHeight(),
        )
      }

      // Espaçamento evidente entre os cards centrais e o dock de apps
      Spacer(Modifier.height(20.dp))

      // 3. Dock Inferior de Apps + Indicadores de Paginação
      BottomDockSection(
        dock = uiState.dock,
        viewModel = viewModel,
        dockRequesters = dockRequesters,
        jwRequesters = jwRequesters,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }

  dialog?.let { DialogCard(it, onClose = viewModel::dismissDialog) }

  BackHandler(enabled = dialog != null) { viewModel.dismissDialog() }
}

// ---------------------------------------------------------------- Barra Superior

@Composable
private fun TopBar(
  now: LocalDateTime,
  isOnline: Boolean,
  onOpenAllApps: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenSearch: () -> Unit,
  onHomeClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Lado esquerdo: Botão Início + Cápsula de Navegação
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Box(
        modifier = Modifier
          .size(42.dp)
          .clip(CircleShape)
          .background(Color.White)
          .clickable { onHomeClick() },
        contentAlignment = Alignment.Center,
      ) {
        Image(
          painter = painterResource(R.drawable.ic_home),
          contentDescription = "Início",
          modifier = Modifier.size(20.dp),
        )
      }

      Row(
        modifier = Modifier
          .height(42.dp)
          .glassPanel(
            shape = RoundedCornerShape(21.dp),
            elevation = 6.dp,
            bgAlphaTop = 0.25f,
            bgAlphaBottom = 0.12f,
          )
          .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Aba Início ativa
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.28f))
            .clickable { onHomeClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
          Text(
            text = "Início",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
        }

        // Aba Apps: Abre Todos os Apps
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { onOpenAllApps() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
          Text(
            text = "Apps",
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.titleMedium,
          )
        }

        // Aba Biblioteca
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
          Text(
            text = "Biblioteca",
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.titleMedium,
          )
        }

        Box(
          modifier = Modifier
            .width(1.dp)
            .height(18.dp)
            .background(Color.White.copy(alpha = 0.35f)),
        )

        // Busca
        Box(
          modifier = Modifier
            .clip(CircleShape)
            .clickable { onOpenSearch() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
          Image(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = "Pesquisar",
            modifier = Modifier.size(18.dp),
          )
        }

        // Definições
        Box(
          modifier = Modifier
            .clip(CircleShape)
            .clickable { onOpenSettings() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
          Image(
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = "Definições",
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }

    // Lado direito: Hora, Wi-Fi e Data
    Column(horizontalAlignment = Alignment.End) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = if (now != LocalDateTime.MIN) now.format(TIME_FORMAT) else "--:--",
          color = Color.White,
          style = MaterialTheme.typography.headlineLarge,
          fontWeight = FontWeight.Bold,
        )
        Image(
          painter = painterResource(R.drawable.ic_wifi),
          contentDescription = if (isOnline) "Wi-Fi ligado" else "Sem ligação",
          modifier = Modifier
            .size(24.dp)
            .alpha(if (isOnline) 1f else 0.35f),
        )
      }
      Text(
        text = if (now != LocalDateTime.MIN) formatDate(now) else "---, -- de ---",
        color = Color.White.copy(alpha = 0.85f),
        style = MaterialTheme.typography.labelLarge,
      )
    }
  }
}

// ---------------------------------------------------------------- Painel Clima

@Composable
private fun WeatherPanel(state: WeatherState, currentHour: Int, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .glassPanel(shape = GlassShape)
      .padding(horizontal = 18.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    when (state) {
      WeatherState.Loading -> {
        Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text("Carregando clima…", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
        }
      }
      is WeatherState.Unavailable -> {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
          Text(stringResource(R.string.content_unavailable), color = InkMuted, style = MaterialTheme.typography.titleMedium)
          Spacer(Modifier.height(4.dp))
          Text(stringResource(R.string.weather_data_source), color = InkMuted, style = MaterialTheme.typography.bodyMedium)
        }
      }
      is WeatherState.Available -> WeatherContent(state.snapshot, currentHour)
    }
  }
}

@Composable
private fun WeatherContent(snapshot: WeatherSnapshot, currentHour: Int) {
  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    // Topo do clima: Ícone grande dinâmico + Temperatura grande
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val iconRes = weatherIconForCode(snapshot.currentCode, currentHour)
      Image(
        painter = painterResource(iconRes),
        contentDescription = WeatherCode.condition(snapshot.currentCode).labelPt,
        modifier = Modifier.size(68.dp),
      )
      Text(
        text = "${snapshot.currentTempC.toInt()}°C",
        color = Color.White,
        fontSize = 44.sp,
        fontWeight = FontWeight.Bold,
      )
    }

    // Localização destacada
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.align(Alignment.CenterHorizontally),
    ) {
      Image(
        painter = painterResource(R.drawable.ic_location_pin),
        contentDescription = null,
        modifier = Modifier.size(16.dp),
      )
      val locationText = if (snapshot.place.city.isNotBlank()) {
        "${snapshot.place.city} - ${snapshot.place.countryCode}"
      } else {
        "São Paulo - SP"
      }
      Text(
        text = locationText,
        color = Color.White,
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium,
      )
    }

    // Divisor fino
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(1.dp)
        .background(Color.White.copy(alpha = 0.20f)),
    )

    // Máxima e Mínima
    Text(
      text = "Máx. ${snapshot.maxC.toInt()}° • Mín. ${snapshot.minC.toInt()}°",
      color = Color.White.copy(alpha = 0.9f),
      fontSize = 15.sp,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.align(Alignment.CenterHorizontally),
    )

    // Previsão horária bem distribuída
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .glassPanel(
          shape = GlassShapeSmall,
          elevation = 3.dp,
          bgAlphaTop = 0.16f,
          bgAlphaBottom = 0.06f,
          borderAlphaTop = 0.25f,
          borderAlphaBottom = 0.10f,
        )
        .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val hourlyList = snapshot.hourly.take(5)
        hourlyList.forEach { h ->
          HourlyColumn(h)
        }
      }
    }
  }
}

@Composable
private fun HourlyColumn(h: HourlyPoint) {
  val iconRes = weatherIconForCode(h.code, h.localTime.hour)

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(
      text = h.localTime.format(DateTimeFormatter.ofPattern("HH'h'")),
      color = Color.White.copy(alpha = 0.85f),
      fontSize = 12.sp,
      fontWeight = FontWeight.Normal,
    )
    Image(
      painter = painterResource(iconRes),
      contentDescription = null,
      modifier = Modifier.size(18.dp),
    )
    Text(
      text = "${h.tempC.toInt()}°",
      color = Color.White,
      fontSize = 14.sp,
      fontWeight = FontWeight.SemiBold,
    )
  }
}

// ---------------------------------------------------------------- Painel JW.org

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
    modifier = modifier
      .glassPanel(shape = GlassShape)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    when (state) {
      JwState.Loading -> {
        Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text("Carregando novidades…", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
        }
      }
      is JwState.Unavailable -> {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
          Text(stringResource(R.string.content_unavailable), color = InkMuted)
          state.lastAttemptAt?.let {
            Spacer(Modifier.height(6.dp))
            Text("Última atualização: ${it.toString().take(10)}", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
          }
        }
      }
      is JwState.Available -> {
        JwContent(
          snapshot = state.snapshot,
          dockFocusIndex = dockFocusIndex,
          onJwFocus = onJwFocus,
          jwRequesters = jwRequesters,
          dockRequesters = dockRequesters,
          onJwEntryClick = onJwEntryClick,
        )
      }
    }
  }
}

@Composable
private fun JwContent(
  snapshot: JwSnapshot,
  dockFocusIndex: Int,
  onJwFocus: (Int) -> Unit,
  jwRequesters: List<FocusRequester>,
  dockRequesters: List<FocusRequester>,
  onJwEntryClick: (JwEntryData) -> Unit,
) {
  val headerInteraction = remember { MutableInteractionSource() }
  val headerFocused by headerInteraction.collectIsFocusedAsState()

  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // Cabeçalho interno do widget: Logo JW.ORG + Título + Seta direita
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .focusRequester(jwRequesters[0])
        .focusable(interactionSource = headerInteraction)
        .then(Modifier.tvFocusRing(headerFocused, shape = RoundedCornerShape(10.dp)))
        .handleDPadKeyEvents(
          onDown = { jwRequesters[1].requestFocus() },
          onEnter = {
            snapshot.whatsNew?.pageUrl?.let { onJwEntryClick(JwEntryData.Text(it)) }
          },
        )
        .onFocusChanged { if (it.isFocused) onJwFocus(0) }
        .padding(horizontal = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(30.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(Color(0xFF0C4A8D)),
        contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text("JW", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp)
          Text(".ORG", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold, lineHeight = 8.sp)
        }
      }

      Spacer(Modifier.width(10.dp))

      Text(
        text = "Novidades no jw.org",
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )

      Spacer(Modifier.weight(1f))

      Image(
        painter = painterResource(R.drawable.ic_chevron_right),
        contentDescription = "Ver mais novidades",
        modifier = Modifier.size(20.dp),
      )
    }

    // Área de Conteúdo
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // 1. Card "Texto do dia" limpo
      DailyTextBanner(
        snapshot = snapshot,
        focusRequester = jwRequesters[1],
        onFocus = { onJwFocus(1) },
        onRight = { jwRequesters[2].requestFocus() },
        onUp = { jwRequesters[0].requestFocus() },
        onDown = { dockRequesters[dockFocusIndex.coerceIn(0, DOCK_SIZE - 1)].requestFocus() },
        onClick = {
          val url = snapshot.dailyText?.pageUrl ?: snapshot.whatsNew?.pageUrl
          if (url != null) onJwEntryClick(JwEntryData.Text(url))
        },
        modifier = Modifier
          .weight(0.53f)
          .fillMaxHeight(),
      )

      // 2. Coluna direita com Broadcasting e Boletim
      Column(
        modifier = Modifier
          .weight(0.47f)
          .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        BroadcastingCard(
          snapshot = snapshot,
          focusRequester = jwRequesters[2],
          onFocus = { onJwFocus(2) },
          onLeft = { jwRequesters[1].requestFocus() },
          onUp = { jwRequesters[0].requestFocus() },
          onDown = { jwRequesters[3].requestFocus() },
          onClick = {
            snapshot.broadcasting?.let {
              onJwEntryClick(JwEntryData.Video(it.title, it.videoUrl, it.posterUrl))
            }
          },
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        )

        GoverningBodyCard(
          snapshot = snapshot,
          focusRequester = jwRequesters[3],
          onFocus = { onJwFocus(3) },
          onLeft = { jwRequesters[1].requestFocus() },
          onUp = { jwRequesters[2].requestFocus() },
          onDown = { dockRequesters[dockFocusIndex.coerceIn(0, DOCK_SIZE - 1)].requestFocus() },
          onClick = {
            snapshot.governingBody?.let {
              onJwEntryClick(JwEntryData.Video(it.title, it.videoUrl, it.posterUrl))
            }
          },
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        )
      }
    }
  }
}

@Composable
private fun DailyTextBanner(
  snapshot: JwSnapshot,
  focusRequester: FocusRequester,
  onFocus: () -> Unit,
  onRight: () -> Unit,
  onUp: () -> Unit,
  onDown: () -> Unit,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()

  Box(
    modifier = modifier
      .focusRequester(focusRequester)
      .focusable(interactionSource = interaction)
      .then(Modifier.tvFocusRing(focused, shape = GlassShapeMedium))
      .handleDPadKeyEvents(
        onRight = onRight,
        onUp = onUp,
        onDown = onDown,
        onEnter = onClick,
      )
      .onFocusChanged { if (it.isFocused) onFocus() }
      .clip(GlassShapeMedium),
  ) {
    Image(
      painter = painterResource(R.drawable.jw_daily_text_clean),
      contentDescription = "Texto do dia",
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color(0x35000000), Color(0x95000000)),
          ),
        ),
    )

    Box(
      modifier = Modifier
        .padding(8.dp)
        .align(Alignment.TopStart)
        .clip(RoundedCornerShape(8.dp))
        .background(Color.White.copy(alpha = 0.92f))
        .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
      Text(
        text = "Texto do dia",
        color = Color(0xFF132238),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
      )
    }

    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      val scriptureText = snapshot.dailyText?.scripture ?: "“Confie no Senhor de todo o seu coração”"
      val formattedScripture = if (scriptureText.startsWith("“")) scriptureText else "“$scriptureText”"

      Text(
        text = formattedScripture,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        lineHeight = 16.sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(3.dp))
      val refText = snapshot.dailyText?.dateLabel ?: "— Provérbios 3:5"
      Text(
        text = if (refText.startsWith("—")) refText else "— $refText",
        color = Color.White.copy(alpha = 0.90f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
private fun BroadcastingCard(
  snapshot: JwSnapshot,
  focusRequester: FocusRequester,
  onFocus: () -> Unit,
  onLeft: () -> Unit,
  onUp: () -> Unit,
  onDown: () -> Unit,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()

  Row(
    modifier = modifier
      .focusRequester(focusRequester)
      .focusable(interactionSource = interaction)
      .then(Modifier.tvFocusRing(focused, shape = GlassShapeSmall))
      .handleDPadKeyEvents(
        onLeft = onLeft,
        onUp = onUp,
        onDown = onDown,
        onEnter = onClick,
      )
      .onFocusChanged { if (it.isFocused) onFocus() }
      .glassPanel(
        shape = GlassShapeSmall,
        elevation = 3.dp,
        bgAlphaTop = 0.18f,
        bgAlphaBottom = 0.08f,
      )
      .padding(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .width(84.dp)
        .fillMaxHeight()
        .clip(RoundedCornerShape(8.dp)),
    ) {
      if (snapshot.broadcasting?.posterUrl != null) {
        AsyncImage(
          model = snapshot.broadcasting.posterUrl,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        Image(
          painter = painterResource(R.drawable.jw_broadcasting_thumb),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      }

      Image(
        painter = painterResource(R.drawable.ic_play_circle),
        contentDescription = "Reproduzir",
        modifier = Modifier
          .size(20.dp)
          .align(Alignment.BottomEnd)
          .padding(end = 3.dp, bottom = 3.dp),
      )
    }

    Spacer(Modifier.width(10.dp))

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        text = "Último Broadcasting",
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
      )
      Spacer(Modifier.height(2.dp))
      Text(
        text = snapshot.broadcasting?.title ?: "Uma mensagem de esperança",
        color = Color.White.copy(alpha = 0.82f),
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun GoverningBodyCard(
  snapshot: JwSnapshot,
  focusRequester: FocusRequester,
  onFocus: () -> Unit,
  onLeft: () -> Unit,
  onUp: () -> Unit,
  onDown: () -> Unit,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()

  Row(
    modifier = modifier
      .focusRequester(focusRequester)
      .focusable(interactionSource = interaction)
      .then(Modifier.tvFocusRing(focused, shape = GlassShapeSmall))
      .handleDPadKeyEvents(
        onLeft = onLeft,
        onUp = onUp,
        onDown = onDown,
        onEnter = onClick,
      )
      .onFocusChanged { if (it.isFocused) onFocus() }
      .glassPanel(
        shape = GlassShapeSmall,
        elevation = 3.dp,
        bgAlphaTop = 0.18f,
        bgAlphaBottom = 0.08f,
      )
      .padding(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .width(84.dp)
        .fillMaxHeight()
        .clip(RoundedCornerShape(8.dp)),
    ) {
      if (snapshot.governingBody?.posterUrl != null) {
        AsyncImage(
          model = snapshot.governingBody.posterUrl,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        Image(
          painter = painterResource(R.drawable.jw_governing_body_thumb),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      }
    }

    Spacer(Modifier.width(10.dp))

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        text = "Último Boletim do\nCorpo Governante",
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        lineHeight = 16.sp,
      )
      Spacer(Modifier.height(2.dp))
      Text(
        text = snapshot.governingBody?.title ?: "Fortalecidos na fé",
        color = Color.White.copy(alpha = 0.82f),
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    Image(
      painter = painterResource(R.drawable.ic_chevron_right),
      contentDescription = null,
      modifier = Modifier.size(18.dp),
      colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.7f)),
    )
  }
}

// ---------------------------------------------------------------- Dock Inferior

@Composable
private fun BottomDockSection(
  dock: List<DockItem>,
  viewModel: HomeViewModel,
  dockRequesters: List<FocusRequester>,
  jwRequesters: List<FocusRequester>,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    // Linha de cards dos apps generosa em altura (126 dp)
    Row(
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      dock.forEach { item ->
        DockCard(
          item = item,
          viewModel = viewModel,
          requester = dockRequesters[item.order.coerceIn(0, DOCK_SIZE - 1)],
          jwRequesters = jwRequesters,
        )
      }

      // Botão circular com seta '>'
      Box(
        modifier = Modifier
          .size(46.dp)
          .glassPanel(
            shape = CircleShape,
            elevation = 6.dp,
            bgAlphaTop = 0.35f,
            bgAlphaBottom = 0.15f,
          )
          .clickable { viewModel.openAllApps() },
        contentAlignment = Alignment.Center,
      ) {
        Image(
          painter = painterResource(R.drawable.ic_chevron_right),
          contentDescription = "Mais apps",
          modifier = Modifier.size(22.dp),
        )
      }
    }

    // Indicadores de Paginação
    Row(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .width(28.dp)
          .height(4.dp)
          .clip(RoundedCornerShape(2.dp))
          .background(Color.White),
      )
      repeat(3) {
        Box(
          modifier = Modifier
            .width(6.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.40f)),
        )
      }
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

  val cardShape = RoundedCornerShape(22.dp)

  Box(
    modifier = Modifier
      .width(180.dp)
      .height(126.dp)
      .focusRequester(requester)
      .focusable(interactionSource = interaction)
      .then(Modifier.tvFocusRing(focused, shape = cardShape, scaleFocused = 1.06f))
      .handleDPadKeyEvents(
        onUp = {
          val jw = viewModel.jwFocusForDockUp().coerceIn(1, JW_ENTRY_COUNT - 1)
          jwRequesters[jw].requestFocus()
        },
        onEnter = { viewModel.onDockOk(item) },
      )
      .onFocusChanged { if (it.isFocused) viewModel.onDockFocused(item.order) }
      .clip(cardShape),
  ) {
    when (item) {
      is DockItem.App -> {
        if (item.packageName == "com.netflix.ninja") {
          // Netflix: Fundo preto com o "N" oficial vermelho + NETFLIX
          Column(
            modifier = Modifier
              .fillMaxSize()
              .background(Color.Black)
              .alpha(if (installed) 1f else 0.35f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Image(
              painter = painterResource(R.drawable.ic_netflix_n),
              contentDescription = "Netflix",
              modifier = Modifier.size(width = 28.dp, height = 48.dp),
            )
            Spacer(Modifier.height(5.dp))
            Text(
              text = "NETFLIX",
              color = Color.White,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 2.sp,
            )
          }
        } else if (item.packageName == "com.google.android.youtube.tv") {
          // YouTube: Fundo branco puro com logo oficial vermelho
          Row(
            modifier = Modifier
              .fillMaxSize()
              .background(Color.White)
              .alpha(if (installed) 1f else 0.35f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
          ) {
            Box(
              modifier = Modifier
                .size(width = 38.dp, height = 26.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFFF0000)),
              contentAlignment = Alignment.Center,
            ) {
              Image(
                painter = painterResource(R.drawable.ic_play_triangle),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
              )
            }
            Spacer(Modifier.width(8.dp))
            Text(
              text = "YouTube",
              color = Color(0xFF282828),
              fontSize = 20.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = (-0.5).sp,
            )
          }
        } else {
          // App genérica instalada
          Column(
            modifier = Modifier
              .fillMaxSize()
              .glassPanel(shape = cardShape),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            val icon = item.icon
            if (icon != null) {
              Image(
                bitmap = drawableToImageBitmap(icon),
                contentDescription = item.contentDescription(),
                modifier = Modifier
                  .size(52.dp)
                  .alpha(if (installed) 1f else 0.35f),
              )
            }
            Spacer(Modifier.height(6.dp))
            Text(item.label, color = Ink, style = MaterialTheme.typography.titleMedium)
          }
        }
      }

      // HDMI 1 e HDMI 2: Fundo azul com ícone e texto perfeitamente legíveis
      is DockItem.Hdmi -> {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .glassPanel(
              shape = cardShape,
              elevation = 6.dp,
              bgAlphaTop = 0.35f,
              bgAlphaBottom = 0.18f,
            ),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Image(
            painter = painterResource(R.drawable.ic_hdmi),
            contentDescription = item.label,
            modifier = Modifier.size(width = 44.dp, height = 24.dp),
          )
          Spacer(Modifier.height(8.dp))
          Text(
            text = item.label,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }

      // Todos os Apps: Apenas o ícone de 9 pontos centralizado (sem texto)
      DockItem.AllApps -> {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .glassPanel(
              shape = cardShape,
              elevation = 6.dp,
              bgAlphaTop = 0.38f,
              bgAlphaBottom = 0.20f,
            ),
          contentAlignment = Alignment.Center,
        ) {
          Image(
            painter = painterResource(R.drawable.ic_apps_grid),
            contentDescription = item.label,
            modifier = Modifier.size(36.dp),
          )
        }
      }
    }

    if (!installed) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.BottomCenter,
      ) {
        Text(
          stringResource(R.string.dock_unavailable),
          color = Missing,
          style = MaterialTheme.typography.labelLarge,
          modifier = Modifier.padding(bottom = 6.dp),
        )
      }
    }
  }
}

private fun DockItem.contentDescription(): String =
  when (this) {
    is DockItem.App -> if (installed) label else "$label, indisponível"
    is DockItem.Hdmi -> label
    DockItem.AllApps -> label
  }

// ---------------------------------------------------------------- Diálogo Modal

@Composable
private fun DialogCard(dialog: HomeDialog, onClose: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black.copy(alpha = 0.55f)),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier =
        Modifier
          .width(580.dp)
          .glassPanel(shape = GlassShape, elevation = 16.dp)
          .padding(28.dp)
          .focusable(interactionSource = interaction)
          .then(Modifier.tvFocusRing(false))
          .handleDPadKeyEvents(onEnter = { onClose() }),
    ) {
      Column {
        val title = when (dialog) {
          is HomeDialog.AppMissing -> stringResource(R.string.dialog_app_missing_title, dialog.appName)
          HomeDialog.SetAsHome -> stringResource(R.string.home_banner)
        }
        val body = when (dialog) {
          is HomeDialog.AppMissing -> stringResource(R.string.dialog_app_missing_body)
          HomeDialog.SetAsHome -> stringResource(R.string.home_banner_note)
        }

        Text(
          text = title,
          color = Ink,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
          text = body,
          color = InkMuted,
          style = MaterialTheme.typography.bodyMedium,
          lineHeight = 28.sp,
        )
        Spacer(Modifier.height(24.dp))
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.22f))
            .clickable { onClose() }
            .padding(horizontal = 28.dp, vertical = 10.dp),
        ) {
          Text(
            text = stringResource(R.string.dialog_ok),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }
  }
}
