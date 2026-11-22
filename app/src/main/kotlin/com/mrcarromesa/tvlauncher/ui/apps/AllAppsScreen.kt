package com.mrcarromesa.tvlauncher.ui.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
 * Grelha de todas as apps (contracts/ui-behavior.md §2): 5 colunas, espaçamento
 * 16 dp, ícones 96 dp; nomes longos com `maxLines = 2` e ellipsis sem sobrepor o
 * ícone; `LazyVerticalGrid` faz scroll para manter o item focado visível;
 * estado "Sem apps" quando a lista é vazia.
 */
@Composable
fun AllAppsScreen(
  onLaunch: (String) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val apps = remember { AppCatalog(context.packageManager).installedApps() }

  Column(modifier = modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
    Text(stringResource(R.string.dock_all_apps), color = Ink, style = MaterialTheme.typography.titleLarge)

    if (apps.isEmpty()) {
      Text(
        text = stringResource(R.string.all_apps_empty),
        color = InkMuted,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 24.dp),
      )
    } else {
      val gridState = rememberLazyGridState()
      val firstRequester = remember { FocusRequester() }
      // Foco inicial: primeira app (contracts/ui-behavior.md §2).
      LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos { }
        firstRequester.requestFocus()
      }
      LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        state = gridState,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
      ) {
        itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
          AppCard(app = app, onLaunch = onLaunch, requester = if (index == 0) firstRequester else null)
        }
      }
    }
  }
}

@Composable
private fun AppCard(app: InstalledApp, onLaunch: (String) -> Unit, requester: FocusRequester? = null) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()

  Box(
    modifier =
      Modifier
        .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
        .focusable(interactionSource = interaction)
        .then(Modifier.tvFocusRing(focused))
        .handleDPadKeyEvents(onEnter = { onLaunch(app.packageName) })
        .glassPanel()
        .padding(12.dp),
  ) {
    Column {
      Image(
        bitmap = drawableToImageBitmap(app.icon),
        contentDescription = app.label,
        contentScale = ContentScale.Fit,
        modifier = Modifier.width(96.dp).height(96.dp),
      )
      Text(
        text = app.label,
        color = Ink,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}