package com.mrcarromesa.tvlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mrcarromesa.tvlauncher.ui.LauncherRoot
import com.mrcarromesa.tvlauncher.ui.theme.TVLauncherTheme

/** Um só MainActivity (contracts/ui-behavior.md §1). */
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      TVLauncherTheme {
        LauncherRoot()
      }
    }
  }
}
