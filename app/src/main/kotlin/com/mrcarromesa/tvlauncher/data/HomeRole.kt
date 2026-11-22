package com.mrcarromesa.tvlauncher.data

import android.app.role.RoleManager
import android.content.Context

/**
 * Deteção do papel HOME (contracts/app-catalog.md §5): só leitura via
 * `RoleManager.isRoleHeld(ROLE_HOME)`. Nenhum caminho escreve no RoleManager,
 * em Settings ou em pacotes.
 */
class HomeRole(context: Context) {
  private val roleManager = context.getSystemService(RoleManager::class.java)

  fun isHome(): Boolean = roleManager?.isRoleHeld(RoleManager.ROLE_HOME) ?: false
}
