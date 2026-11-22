package com.mrcarromesa.tvlauncher.data

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import com.mrcarromesa.tvlauncher.model.InstalledApp
import java.text.Collator
import java.util.Locale

/**
 * Enumera as apps abríveis (contracts/app-catalog.md §2):
 * união por `packageName` de LEANBACK_LAUNCHER + LAUNCHER, excluindo o próprio launcher,
 * ordenada por `label` (Collator `pt`, sem distinguir maiúsculas). Lista vazia é estado válido.
 *
 * `shortcut: algumas apps de TV (ex.: Netflix) declaram MAIN+LEANBACK_LAUNCHER sem
 * CATEGORY_DEFAULT; MATCH_DEFAULT_ONLY e getLaunchIntentForPackage exigem DEFAULT na
 * resolução implícita, o que as excluiria. Enumera sem MATCH_DEFAULT_ONLY e guarda um
 * intent explícito (className) para o lançamento; GET_PREFERRED não é usado. Ao migrar o
 * contrato app-catalog.md §2, documentar que a resolução implícita é apenas preferida.
 */
class AppCatalog(private val packageManager: PackageManager) {

  private val explicitLaunchers = mutableMapOf<String, Intent>()

  fun installedApps(): List<InstalledApp> =
    (query(Intent.CATEGORY_LEANBACK_LAUNCHER) + query(Intent.CATEGORY_LAUNCHER))
      .asSequence()
      .filterNot { it.activityInfo.packageName == SELF_PACKAGE }
      .groupBy { it.activityInfo.packageName }
      .map { it.value.first() }
      .mapNotNull { it.toInstalledApp() }
      .sortedWith(
        compareBy(collator) { app ->
          val label = app.label
          if (label.isNullOrEmpty()) "" else label.lowercase(Locale("pt"))
        },
      )
      .toList()

  /**
   * Intent de lançamento de uma app: preferido o implícito (leanback/launcher), sempre
   * que resolver; senão o explícito `packageName + activity` obtido na enumeração.
   */
  fun launchIntent(packageName: String): Intent? =
    packageManager.getLeanbackLaunchIntentForPackage(packageName)
      ?: packageManager.getLaunchIntentForPackage(packageName)
      ?: explicitLaunchers[packageName]

  private fun query(category: String): List<android.content.pm.ResolveInfo> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
    return packageManager.queryIntentActivities(intent, 0).orEmpty()
  }

  private fun android.content.pm.ResolveInfo.toInstalledApp(): InstalledApp? {
    val packageName = activityInfo.packageName
    val label = runCatching { loadLabel(packageManager).toString() }.getOrNull()
      ?: return null
    val icon = runCatching { loadIcon(packageManager) }.getOrNull() ?: genericIcon()
    // Lançamento explícito (como `am start -n pkg/.Activity`): não depende de DEFAULT.
    explicitLaunchers[packageName] =
      Intent().setClassName(packageName, activityInfo.name)
    return InstalledApp(packageName = packageName, label = label, icon = icon)
  }

  private fun genericIcon(): Drawable = ColorDrawable(0xFF616E79.toInt())

  companion object {
    const val SELF_PACKAGE = "com.mrcarromesa.tvlauncher"
    private val collator: Collator = Collator.getInstance(Locale("pt")).apply { strength = Collator.TERTIARY }
  }
}