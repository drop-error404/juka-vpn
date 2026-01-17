package com.julogic.jukavpn.tunnel

import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

/**
 * UDP Relay Manager for handling UDP traffic tunneling
 */
class UDPRelayManager {
    
    companion object {
        private const val TAG = "UDPRelayManager"
        private const val BUFFER_SIZE = 65535
        private const val SOCKET_TIMEOUT = 30000 // 30 seconds
        private const val DEFAULT_LOCAL_PORT = 10810
    }
    
    private var localSocket: DatagramSocket? = null
    private var isRunning = false
    private var relayJob: Job? = null
    private val activeSessions = ConcurrentHashMap<String, UDPSession>()
    
    data class UDPSession(
        val sourceAddress: InetAddress,
        val sourcePort: Int,
        val remoteSocket: DatagramSocket,
        val lastActivity: Long = System.currentTimeMillis()
    )
    
    interface RelayListener {
        fun onStarted(port: Int)
        fun onStopped()
        fun onError(message: String)
        fun onPacketRelayed(bytesIn: Long, bytesOut: Long)
    }
    
    private var listener: RelayListener? = null
    
    fun setRelayListener(listener: RelayListener) {
        this.listener = listener
    }
    
    /**
     * Start UDP relay on specified port
     */
    fun start(
        localPort: Int = DEFAULT_LOCAL_PORT,
        remoteHost: String,
        remotePort: Int
    ) {
        if (isRunning) {
            Log.w(TAG, "UDP relay already running")
            return
        }
        
        relayJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                localSocket = DatagramSocket(localPort)
                localSocket?.soTimeout = SOCKET_TIMEOUT
                isRunning = true
                
                withContext(Dispatchers.Main) {
                    listener?.onStarted(localPort)
                }
                
                Log.i(TAG, "UDP relay started on port $localPort -> $remoteHost:$remotePort")
                
                val remoteAddress = InetAddress.getByName(remoteHost)
                val buffer = ByteArray(BUFFER_SIZE)
                
                while (isRunning && isActive) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        localSocket?.receive(packet)
                        
                        // Create session key
                        val sessionKey = "${packet.address.hostAddress}:${packet.port}"
                        
                        // Get or create session
                        val session = activeSessions.getOrPut(sessionKey) {
                            UDPSession(
                                sourceAddress = packet.address,
                                sourcePort = packet.port,
                                remoteSocket = DatagramSocket()
                            )
                        }
                        
                        // Forward packet to remote
                        val forwardPacket = DatagramPacket(
                            packet.data,
                            packet.length,
                            remoteAddress,
                            remotePort
                        )
                        session.remoteSocket.send(forwardPacket)
                        
                        listener?.onPacketRelayed(packet.length.toLong(), 0)
                        
                        // Start receiving responses for this session
                        launch {
                            receiveResponses(session, sessionKey)
                        }
                        
                    } catch (e: SocketTimeoutException) {
                        // Cleanup stale sessions
                        cleanupStaleSessions()
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "UDP relay error", e)
                withContext(Dispatchers.Main) {
                    listener?.onError(e.message ?: "Unknown error")
                }
            } finally {
                cleanup()
            }
        }
    }
    
    private suspend fun receiveResponses(session: UDPSession, sessionKey: String) {
        withContext(Dispatchers.IO) {
            try {
                session.remoteSocket.soTimeout = SOCKET_TIMEOUT
                val buffer = ByteArray(BUFFER_SIZE)
                
                while (isRunning && isActive) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        session.remoteSocket.receive(packet)
                        
                        // Forward response back to client
                        val responsePacket = DatagramPacket(
                            packet.data,
                            packet.length,
                            session.sourceAddress,
                            session.sourcePort
                        )
                        localSocket?.send(responsePacket)
                        
                        listener?.onPacketRelayed(0, packet.length.toLong())
                        
                    } catch (e: SocketTimeoutException) {
                        // Session timeout
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error receiving response for session $sessionKey", e)
            } finally {
                activeSessions.remove(sessionKey)
                session.remoteSocket.close()
            }
        }
    }
    
    private fun cleanupStaleSessions() {
        val now = System.currentTimeMillis()
        val staleThreshold = SOCKET_TIMEOUT.toLong()
        
        activeSessions.entries.removeIf { entry ->
            val isStale = now - entry.value.lastActivity > staleThreshold
            if (isStale) {
                entry.value.remoteSocket.close()
            }
            isStale
        }
    }
    
    fun stop() {
        isRunning = false
        relayJob?.cancel()
        cleanup()
        listener?.onStopped()
    }
    
    private fun cleanup() {
        activeSessions.forEach { (_, session) ->
            try {
                session.remoteSocket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing session socket", e)
            }
        }
        activeSessions.clear()
        
        try {
            localSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing local socket", e)
        }
        localSocket = null
        isRunning = false
    }
    
    fun isRunning(): Boolean = isRunning
    
    fun getActiveSessionCount(): Int = activeSessions.size
}
