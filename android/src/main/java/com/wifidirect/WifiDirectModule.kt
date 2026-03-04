package com.wifidirect

import android.content.Context
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.*
import android.os.Looper
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import org.json.JSONArray
import org.json.JSONObject

class WifiDirectModule(reactContext: ReactApplicationContext) :
  NativeWifiDirectSpec(reactContext),
  WifiDirectBroadcastReceiver.WifiDirectEventListener,
  FileTransferService.FileTransferListener,
  DataStreamService.DataListener {

  private var manager: WifiP2pManager? = null
  private var channel: WifiP2pManager.Channel? = null
  private var receiver: WifiDirectBroadcastReceiver? = null
  private var isP2pEnabled = false
  private var peers: MutableList<WifiP2pDevice> = mutableListOf()

  private val fileTransferService = FileTransferService()
  private val dataStreamService = DataStreamService()
  private var listenerCount = 0

  companion object {
    const val NAME = NativeWifiDirectSpec.NAME
  }

  // ---- Lifecycle ----

  override fun initialize(promise: Promise) {
    try {
      val context = reactApplicationContext
      manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
      if (manager == null) {
        promise.reject("WIFI_P2P_NOT_SUPPORTED", "WiFi Direct is not supported on this device")
        return
      }

      channel = manager!!.initialize(context, Looper.getMainLooper(), null)
      receiver = WifiDirectBroadcastReceiver(manager!!, channel!!, this)

      val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
      }
      context.registerReceiver(receiver, intentFilter)

      fileTransferService.listener = this
      dataStreamService.listener = this
      dataStreamService.startListening()

      promise.resolve(true)
    } catch (e: Exception) {
      promise.reject("INIT_FAILED", e.message)
    }
  }

  override fun dispose() {
    try {
      receiver?.let { reactApplicationContext.unregisterReceiver(it) }
    } catch (_: Exception) {}
    receiver = null
    fileTransferService.destroy()
    dataStreamService.destroy()
    channel?.close()
    channel = null
    manager = null
    peers.clear()
  }

  // ---- Discovery ----

  override fun startDiscovery(promise: Promise) {
    val mgr = manager
    val ch = channel
    if (mgr == null || ch == null) {
      promise.reject("NOT_INITIALIZED", "Call initialize() first")
      return
    }

    mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
      override fun onSuccess() {
        promise.resolve(true)
      }

      override fun onFailure(reason: Int) {
        promise.reject("DISCOVER_FAILED", "Discovery failed with reason: $reason")
      }
    })
  }

  override fun stopDiscovery(promise: Promise) {
    val mgr = manager
    val ch = channel
    if (mgr == null || ch == null) {
      promise.reject("NOT_INITIALIZED", "Call initialize() first")
      return
    }

    mgr.stopPeerDiscovery(ch, object : WifiP2pManager.ActionListener {
      override fun onSuccess() {
        promise.resolve(true)
      }

      override fun onFailure(reason: Int) {
        promise.reject("STOP_DISCOVER_FAILED", "Stop discovery failed with reason: $reason")
      }
    })
  }

  override fun getAvailablePeers(promise: Promise) {
    val mgr = manager
    val ch = channel
    if (mgr == null || ch == null) {
      promise.reject("NOT_INITIALIZED", "Call initialize() first")
      return
    }

    mgr.requestPeers(ch) { peerList ->
      peers.clear()
      peers.addAll(peerList.deviceList)
      promise.resolve(deviceListToJson(peers))
    }
  }

  // ---- Connection ----

  override fun connect(deviceAddress: String, promise: Promise) {
    val mgr = manager
    val ch = channel
    if (mgr == null || ch == null) {
      promise.reject("NOT_INITIALIZED", "Call initialize() first")
      return
    }

    val config = WifiP2pConfig().apply {
      this.deviceAddress = deviceAddress
    }

    mgr.connect(ch, config, object : WifiP2pManager.ActionListener {
      override fun onSuccess() {
        promise.resolve(true)
      }

      override fun onFailure(reason: Int) {
        promise.reject("CONNECT_FAILED", "Connection failed with reason: $reason")
      }
    })
  }

  override fun cancelConnect(promise: Promise) {
    val mgr = manager
    val ch = channel
    if (mgr == null || ch == null) {
      promise.reject("NOT_INITIALIZED", "Call initialize() first")
      return
    }

    mgr.cancelConnect(ch, object : WifiP2pManager.ActionListener {
      override fun onSuccess() {
        promise.resolve(true)
      }

      override fun onFailure(reason: Int) {
        promise.reject("CANCEL_CONNECT_FAILED", "Cancel connect failed with reason: $reason")
      }
    })
  }

  override fun disconnect(promise: Promise) {
    val mgr = manager
    val ch = channel
    if (mgr == null || ch == null) {
      promise.reject("NOT_INITIALIZED", "Call initialize() first")
      return
    }

    mgr.removeGroup(ch, object : WifiP2pManager.ActionListener {
      override fun onSuccess() {
        promise.resolve(true)
      }

      override fun onFailure(reason: Int) {
        promise.reject("DISCONNECT_FAILED", "Disconnect failed with reason: $reason")
      }
    })
  }

  override fun getConnectionInfo(promise: Promise) {
    val mgr = manager
    val ch = channel
    if (mgr == null || ch == null) {
      promise.reject("NOT_INITIALIZED", "Call initialize() first")
      return
    }

    mgr.requestConnectionInfo(ch) { info ->
      val json = JSONObject().apply {
        put("groupOwnerAddress", info?.groupOwnerAddress?.hostAddress ?: "")
        put("isGroupOwner", info?.isGroupOwner ?: false)
        put("groupFormed", info?.groupFormed ?: false)
      }
      promise.resolve(json.toString())
    }
  }

  // ---- Group Management ----

  override fun createGroup(promise: Promise) {
    val mgr = manager
    val ch = channel
    if (mgr == null || ch == null) {
      promise.reject("NOT_INITIALIZED", "Call initialize() first")
      return
    }

    mgr.createGroup(ch, object : WifiP2pManager.ActionListener {
      override fun onSuccess() {
        promise.resolve(true)
      }

      override fun onFailure(reason: Int) {
        promise.reject("CREATE_GROUP_FAILED", "Create group failed with reason: $reason")
      }
    })
  }

  override fun removeGroup(promise: Promise) {
    val mgr = manager
    val ch = channel
    if (mgr == null || ch == null) {
      promise.reject("NOT_INITIALIZED", "Call initialize() first")
      return
    }

    mgr.removeGroup(ch, object : WifiP2pManager.ActionListener {
      override fun onSuccess() {
        promise.resolve(true)
      }

      override fun onFailure(reason: Int) {
        promise.reject("REMOVE_GROUP_FAILED", "Remove group failed with reason: $reason")
      }
    })
  }

  override fun getGroupInfo(promise: Promise) {
    val mgr = manager
    val ch = channel
    if (mgr == null || ch == null) {
      promise.reject("NOT_INITIALIZED", "Call initialize() first")
      return
    }

    mgr.requestGroupInfo(ch) { group ->
      if (group == null) {
        promise.resolve("{}")
        return@requestGroupInfo
      }
      val clientsArray = JSONArray()
      group.clientList.forEach { device ->
        clientsArray.put(deviceToJson(device))
      }
      val json = JSONObject().apply {
        put("networkName", group.networkName)
        put("passphrase", group.passphrase)
        put("isGroupOwner", group.isGroupOwner)
        put("ownerAddress", group.owner?.deviceAddress ?: "")
        put("clients", clientsArray)
      }
      promise.resolve(json.toString())
    }
  }

  // ---- File Transfer ----

  override fun sendFile(filePath: String, targetAddress: String, promise: Promise) {
    try {
      val transferId = fileTransferService.sendFile(filePath, targetAddress)
      promise.resolve(transferId)
    } catch (e: Exception) {
      promise.reject("SEND_FILE_FAILED", e.message)
    }
  }

  override fun cancelFileTransfer(transferId: String, promise: Promise) {
    val result = fileTransferService.cancelTransfer(transferId)
    promise.resolve(result)
  }

  // ---- Data / Messaging ----

  override fun sendData(data: String, targetAddress: String, isBase64: Boolean, promise: Promise) {
    try {
      val result = dataStreamService.sendData(data, targetAddress, isBase64)
      promise.resolve(result)
    } catch (e: Exception) {
      promise.reject("SEND_DATA_FAILED", e.message)
    }
  }

  // ---- Event Listener Management ----

  override fun addListener(eventName: String) {
    listenerCount++
  }

  override fun removeListeners(count: Double) {
    listenerCount -= count.toInt()
    if (listenerCount < 0) listenerCount = 0
  }

  // ---- BroadcastReceiver Callbacks ----

  override fun onP2pStateChanged(enabled: Boolean) {
    isP2pEnabled = enabled
  }

  override fun onPeersChanged() {
    val mgr = manager ?: return
    val ch = channel ?: return

    mgr.requestPeers(ch) { peerList ->
      peers.clear()
      peers.addAll(peerList.deviceList)
      val params = Arguments.createMap().apply {
        putString("devices", deviceListToJson(peers))
      }
      sendEvent("onPeersUpdated", params)
    }
  }

  override fun onConnectionChanged(networkInfo: NetworkInfo?) {
    val mgr = manager ?: return
    val ch = channel ?: return

    if (networkInfo?.isConnected == true) {
      mgr.requestConnectionInfo(ch) { info ->
        val json = JSONObject().apply {
          put("groupOwnerAddress", info?.groupOwnerAddress?.hostAddress ?: "")
          put("isGroupOwner", info?.isGroupOwner ?: false)
          put("groupFormed", info?.groupFormed ?: false)
        }
        val params = Arguments.createMap().apply {
          putString("connectionInfo", json.toString())
        }
        sendEvent("onConnectionInfoUpdated", params)

        // Start file receiving on the group owner side
        if (info?.isGroupOwner == true) {
          val cacheDir = reactApplicationContext.cacheDir.absolutePath
          fileTransferService.startReceiving(cacheDir)
        }
      }
    } else {
      val json = JSONObject().apply {
        put("groupOwnerAddress", "")
        put("isGroupOwner", false)
        put("groupFormed", false)
      }
      val params = Arguments.createMap().apply {
        putString("connectionInfo", json.toString())
      }
      sendEvent("onConnectionInfoUpdated", params)
    }
  }

  override fun onThisDeviceChanged(device: WifiP2pDevice?) {
    if (device == null) return
    val params = Arguments.createMap().apply {
      putString("device", deviceToJson(device).toString())
    }
    sendEvent("onThisDeviceChanged", params)
  }

  // ---- FileTransferService Callbacks ----

  override fun onTransferUpdate(
    transferId: String,
    progress: Double,
    status: String,
    fileName: String,
    error: String?
  ) {
    val params = Arguments.createMap().apply {
      putString("transferId", transferId)
      putDouble("progress", progress)
      putString("status", status)
      putString("fileName", fileName)
      error?.let { putString("error", it) }
    }
    sendEvent("onFileTransferUpdate", params)
  }

  // ---- DataStreamService Callbacks ----

  override fun onDataReceived(
    senderId: String,
    senderName: String,
    data: String,
    isBase64: Boolean
  ) {
    val params = Arguments.createMap().apply {
      putString("senderId", senderId)
      putString("senderName", senderName)
      putString("data", data)
      putBoolean("isBase64", isBase64)
    }
    sendEvent("onDataReceived", params)
  }

  // ---- Helpers ----

  private fun sendEvent(eventName: String, params: WritableMap) {
    if (listenerCount > 0) {
      reactApplicationContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        .emit(eventName, params)
    }
  }

  private fun deviceToJson(device: WifiP2pDevice): JSONObject {
    return JSONObject().apply {
      put("deviceName", device.deviceName)
      put("deviceAddress", device.deviceAddress)
      put("isGroupOwner", device.isGroupOwner)
      put("status", device.status)
      put("primaryDeviceType", device.primaryDeviceType ?: "")
    }
  }

  private fun deviceListToJson(devices: List<WifiP2pDevice>): String {
    val array = JSONArray()
    devices.forEach { array.put(deviceToJson(it)) }
    return array.toString()
  }
}
