package com.jellycine.data.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

@Serializable
data class DiscoveredServer(
    @SerialName("Address") val address: String,
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("EndpointAddress") val endpointAddress: String? = null
)

object ServerDiscovery {

    private const val TAG = "JellyCineDiscovery"
    private const val PORT = 7359
    private const val TIMEOUT_MS = 1500
    private const val MAX_SERVERS = 15
    private const val BUFFER_SIZE = 1024
    private val MESSAGES = listOf("who is JellyfinServer?", "who is EmbyServer?")

    fun discoverServers(context: Context): Flow<DiscoveredServer> = flow {
        val appContext = context.applicationContext
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val multicastLock = wifiManager.createMulticastLock("JellyCineServerDiscovery")

        try {
            multicastLock.setReferenceCounted(true)
            multicastLock.acquire()

            val seen = mutableSetOf<String>()
            val broadcastAddress = InetAddress.getByName("255.255.255.255")

            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = TIMEOUT_MS

                repeat(2) { round ->
                    for (message in MESSAGES) {
                        val data = message.toByteArray(Charsets.UTF_8)
                        try {
                            socket.send(DatagramPacket(data, data.size, broadcastAddress, PORT))
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            continue
                        }
                    }
                    if (round == 0) Thread.sleep(100)
                }

                val buffer = ByteArray(BUFFER_SIZE)
                var received = 0
                while (received < MAX_SERVERS) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)

                        val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                        val server = try {
                            JellyCineJson.decodeFromString<DiscoveredServer>(text)
                        } catch (_: Exception) {
                            continue
                        }

                        val resolved = server.copy(endpointAddress = packet.address.hostAddress)
                        if (seen.add(resolved.id)) {
                            emit(resolved)
                        }
                        received++
                    } catch (_: SocketTimeoutException) {
                        break
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        break
                    }
                }
            }
            Log.d(TAG, "Discovery complete, found ${seen.size} server(s)")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Discovery failed: ${e.message}")
        } finally {
            if (multicastLock.isHeld) {
                multicastLock.release()
            }
        }
    }.flowOn(Dispatchers.IO)
}