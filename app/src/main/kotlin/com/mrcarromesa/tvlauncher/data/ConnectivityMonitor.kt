package com.mrcarromesa.tvlauncher.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado da ligação atual (contracts/ui-behavior.md §4, FR-011):
 * atualiza por callback (`NetworkCallback`), não por polling.
 * `NET_CAPABILITY_INTERNET` ⇒ ligado.
 */
class ConnectivityMonitor(context: Context) {

  private val connectivityManager =
    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

  private val _isOnline = MutableStateFlow(fromCapabilities())
  val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

  private val callback =
    object : ConnectivityManager.NetworkCallback() {
      override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
        _isOnline.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
      }

      override fun onLost(network: Network) {
        _isOnline.value = fromCapabilities()
      }
    }

  init {
    runCatching {
      connectivityManager.registerDefaultNetworkCallback(callback)
    }
  }

  private fun fromCapabilities(): Boolean {
    val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
    return caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
  }
}
