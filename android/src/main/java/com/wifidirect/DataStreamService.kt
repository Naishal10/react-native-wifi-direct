package com.wifidirect

import kotlinx.coroutines.*
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class DataStreamService {

  companion object {
    const val DATA_PORT = 8989
  }

  interface DataListener {
    fun onDataReceived(
      senderId: String,
      senderName: String,
      data: String,
      isBase64: Boolean
    )
  }

  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
  private var serverSocket: ServerSocket? = null
  private var clientSocket: Socket? = null
  var listener: DataListener? = null

  fun startListening() {
    scope.launch {
      try {
        serverSocket?.close()
        serverSocket = ServerSocket(DATA_PORT)

        while (isActive) {
          val client = serverSocket?.accept() ?: break
          launch { handleIncomingData(client) }
        }
      } catch (_: Exception) {
        // Server socket closed
      }
    }
  }

  private suspend fun handleIncomingData(client: Socket) {
    try {
      val dataIn = DataInputStream(client.getInputStream())
      while (currentCoroutineContext().isActive) {
        val senderId = dataIn.readUTF()
        val senderName = dataIn.readUTF()
        val isBase64 = dataIn.readBoolean()
        val data = dataIn.readUTF()
        listener?.onDataReceived(senderId, senderName, data, isBase64)
      }
    } catch (_: Exception) {
      // Connection closed
    } finally {
      client.close()
    }
  }

  fun sendData(data: String, targetAddress: String, isBase64: Boolean): Boolean {
    return try {
      val socket = Socket()
      socket.connect(InetSocketAddress(targetAddress, DATA_PORT), 5000)
      val dataOut = DataOutputStream(socket.getOutputStream())
      dataOut.writeUTF(targetAddress) // senderId
      dataOut.writeUTF("device") // senderName
      dataOut.writeBoolean(isBase64)
      dataOut.writeUTF(data)
      dataOut.flush()
      socket.close()
      true
    } catch (e: Exception) {
      false
    }
  }

  fun destroy() {
    scope.cancel()
    serverSocket?.close()
    clientSocket?.close()
  }
}
