package com.mrcarromesa.tvlauncher.ui.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mrcarromesa.tvlauncher.R
import com.mrcarromesa.tvlauncher.data.AppCatalog
import com.mrcarromesa.tvlauncher.model.InstalledApp
import com.mrcarromesa.tvlauncher.ui.drawableToImageBitmap
import com.mrcarromesa.tvlauncher.ui.handleDPadKeyEvents
import com.mrcarromesa.tvlauncher.ui.theme.Ink
import com.mrcarromesa.tvlauncher.ui.theme.InkMuted
import com.mrcarromesa.tvlauncher.ui.theme.glassPanel
import com.mrcarromesa.tvlauncher.ui.theme.tvFocusRing

/**
 * Grelha de todas as apps:
 * - Mesmo background fotográfico da Home com gradiente vítreo
 * - 5 colunas com proporções corretas (sem cortes)
 * - Foco fluido com anel neon azul e cards com altura fixa uniforme
 */
@Composable
fun AllAppsScreen(
  onLaunch: (String) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val apps = remember { AppCatalog(context.packageManager).installedApps() }

  Box(modifier = modifier.fillMaxSize()) {
    // 1. Wallpaper fotográfico do lago
    Image(
      painter = painterResource(R.drawable.bg_launcher),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )

    // 2. Sobreposição translúcida escura para contraste ideal
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.50f)),
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 48.dp, vertical = 24.dp),
    ) {
      // Cabeçalho da tela Todos os Apps
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Box(
          modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.20f)),
          contentAlignment = Alignment.Center,
        ) {
          Image(
            painter = painterResource(R.drawable.ic_apps_grid),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
          )
        }

        Text(
          text = stringResource(R.string.dock_all_apps),
          color = Color.White,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.weight(1f))

        Text(
          text = "${apps.size} apps instalados",
          color = Color.White.copy(alpha = 0.65f),
          style = MaterialTheme.typography.bodyMedium,
        )
      }

      Spacer(Modifier.height(12.dp))

      if (apps.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = stringResource(R.string.all_apps_empty),
            color = InkMuted,
            style = MaterialTheme.typography.titleMedium,
          )
        }
      } else {
        val gridState = rememberLazyGridState()
        val firstRequester = remember { FocusRequester() }

        // Foco inicial na primeira app
        LaunchedEffect(Unit) {
          androidx.compose.runtime.withFrameNanos { }
          firstRequester.requestFocus()
        }

        LazyVerticalGrid(
          columns = GridCells.Fixed(5),
          state = gridState,
          contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp),
          modifier = Modifier.fillMaxSize(),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
            AppCard(
              app = app,
              onLaunch = onLaunch,
              requester = if (index == 0) firstRequester else null,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AppCard(
  app: InstalledApp,
  onLaunch: (String) -> Unit,
  requester: FocusRequester? = null,
) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  val cardShape = RoundedCornerShape(20.dp)

  Box(
    modifier =
      Modifier
        .fillMaxWidth()
        .height(136.dp)
        .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
        .focusable(interactionSource = interaction)
        .then(Modifier.tvFocusRing(focused, shape = cardShape, scaleFocused = 1.06f))
        .handleDPadKeyEvents(onEnter = { onLaunch(app.packageName) })
        .glassPanel(
          shape = cardShape,
          elevation = 4.dp,
          bgAlphaTop = 0.28f,
          bgAlphaBottom = 0.12f,
        )
        .padding(horizontal = 12.dp, vertical = 10.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Image(
        bitmap = drawableToImageBitmap(app.icon),
        contentDescription = app.label,
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(56.dp),
      )
      Spacer(Modifier.height(8.dp))
      Text(
        text = app.label,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
      )
    }
  }
}
