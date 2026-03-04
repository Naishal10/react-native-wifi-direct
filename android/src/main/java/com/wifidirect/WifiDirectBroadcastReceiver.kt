package com.wifidirect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build

class WifiDirectBroadcastReceiver(
  private val manager: WifiP2pManager,
  private val channel: WifiP2pManager.Channel,
  private val listener: WifiDirectEventListener
) : BroadcastReceiver() {

  interface WifiDirectEventListener {
    fun onP2pStateChanged(enabled: Boolean)
    fun onPeersChanged()
    fun onConnectionChanged(networkInfo: NetworkInfo?)
    fun onThisDeviceChanged(device: WifiP2pDevice?)
  }

  override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
      WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
        listener.onP2pStateChanged(state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
      }

      WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
        listener.onPeersChanged()
      }

      WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
        @Suppress("DEPRECATION")
        val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
        } else {
          intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
        }
        listener.onConnectionChanged(networkInfo)
      }

      WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
        @Suppress("DEPRECATION")
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE, WifiP2pDevice::class.java)
        } else {
          intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
        }
        listener.onThisDeviceChanged(device)
      }
    }
  }
}
