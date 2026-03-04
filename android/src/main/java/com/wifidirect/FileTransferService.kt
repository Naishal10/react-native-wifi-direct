package com.wifidirect

import kotlinx.coroutines.*
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class FileTransferService {

  companion object {
    const val FILE_TRANSFER_PORT = 8988
    const val BUFFER_SIZE = 8192
  }

  interface FileTransferListener {
    fun onTransferUpdate(
      transferId: String,
      progress: Double,
      status: String,
      fileName: String,
      error: String?
    )
  }

  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
  private val activeTransfers = ConcurrentHashMap<String, Job>()
  private var serverSocket: ServerSocket? = null
  var listener: FileTransferListener? = null

  fun sendFile(filePath: String, targetAddress: String): String {
    val transferId = UUID.randomUUID().toString()
    val file = File(filePath)
    val fileName = file.name

    val job = scope.launch {
      try {
        listener?.onTransferUpdate(transferId, 0.0, "started", fileName, null)

        val socket = Socket()
        socket.connect(InetSocketAddress(targetAddress, FILE_TRANSFER_PORT), 5000)

        val outputStream = socket.getOutputStream()
        val dataOut = DataOutputStream(outputStream)

        // Send file name and size as header
        dataOut.writeUTF(fileName)
        dataOut.writeLong(file.length())

        val inputStream = FileInputStream(file)
        val buffer = ByteArray(BUFFER_SIZE)
        val totalBytes = file.length()
        var bytesSent: Long = 0

        while (isActive) {
          val bytesRead = inputStream.read(buffer)
          if (bytesRead == -1) break
          outputStream.write(buffer, 0, bytesRead)
          bytesSent += bytesRead
          val progress = bytesSent.toDouble() / totalBytes.toDouble()
          listener?.onTransferUpdate(transferId, progress, "progress", fileName, null)
        }

        outputStream.flush()
        inputStream.close()
        socket.close()

        if (isActive) {
          listener?.onTransferUpdate(transferId, 1.0, "completed", fileName, null)
        }
      } catch (e: Exception) {
        if (isActive) {
          listener?.onTransferUpdate(transferId, 0.0, "failed", fileName, e.message)
        }
      } finally {
        activeTransfers.remove(transferId)
      }
    }

    activeTransfers[transferId] = job
    return transferId
  }

  fun startReceiving(saveDirectory: String) {
    scope.launch {
      try {
        serverSocket?.close()
        serverSocket = ServerSocket(FILE_TRANSFER_PORT)

        while (isActive) {
          val client = serverSocket?.accept() ?: break
          launch { handleIncomingFile(client, saveDirectory) }
        }
      } catch (_: Exception) {
        // Server socket closed
      }
    }
  }

  private suspend fun handleIncomingFile(client: Socket, saveDirectory: String) {
    val transferId = UUID.randomUUID().toString()
    try {
      val inputStream = client.getInputStream()
      val dataIn = DataInputStream(inputStream)

      val fileName = dataIn.readUTF()
      val fileSize = dataIn.readLong()

      listener?.onTransferUpdate(transferId, 0.0, "started", fileName, null)

      val dir = File(saveDirectory)
      if (!dir.exists()) dir.mkdirs()
      val file = File(dir, fileName)

      val outputStream = FileOutputStream(file)
      val buffer = ByteArray(BUFFER_SIZE)
      var bytesReceived: Long = 0

      while (bytesReceived < fileSize) {
        val bytesRead = inputStream.read(buffer, 0,
          minOf(BUFFER_SIZE.toLong(), fileSize - bytesReceived).toInt())
        if (bytesRead == -1) break
        outputStream.write(buffer, 0, bytesRead)
        bytesReceived += bytesRead
        val progress = bytesReceived.toDouble() / fileSize.toDouble()
        listener?.onTransferUpdate(transferId, progress, "progress", fileName, null)
      }

      outputStream.close()
      client.close()
      listener?.onTransferUpdate(transferId, 1.0, "completed", fileName, null)
    } catch (e: Exception) {
      listener?.onTransferUpdate(transferId, 0.0, "failed", "", e.message)
    }
  }

  fun cancelTransfer(transferId: String): Boolean {
    val job = activeTransfers[transferId] ?: return false
    job.cancel()
    activeTransfers.remove(transferId)
    return true
  }

  fun destroy() {
    scope.cancel()
    serverSocket?.close()
    activeTransfers.clear()
  }
}
