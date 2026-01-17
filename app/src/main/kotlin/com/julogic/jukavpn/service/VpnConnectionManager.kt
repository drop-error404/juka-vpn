package com.julogic.jukavpn.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.julogic.jukavpn.V2rayVpnService
import com.julogic.jukavpn.config.V2RayConfigGenerator
import com.julogic.jukavpn.data.ServerRepository
import com.julogic.jukavpn.models.ConnectionState
import com.julogic.jukavpn.models.ConnectionStats
import com.julogic.jukavpn.models.Protocol
import com.julogic.jukavpn.models.Server
import com.julogic.jukavpn.tunnel.SSHTunnelManager
import com.julogic.jukavpn.utils.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central manager for VPN connections
 * Handles different protocols and connection lifecycle
 */
class VpnConnectionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "VpnConnectionManager"
        
        // Singleton instance
        @Volatile
        private var instance: VpnConnectionManager? = null
        
        fun getInstance(context: Context): VpnConnectionManager {
            return instance ?: synchronized(this) {
                instance ?: VpnConnectionManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
    
    private val repository = ServerRepository(context)
    private val notificationHelper = NotificationHelper(context)
    private val sshTunnelManager = SSHTunnelManager()
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Connection state
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _currentServer = MutableStateFlow<Server?>(null)
    val currentServer: StateFlow<Server?> = _currentServer.asStateFlow()
    
    private val _connectionStats = MutableStateFlow(ConnectionStats())
    val connectionStats: StateFlow<ConnectionStats> = _connectionStats.asStateFlow()
    
    private var statsJob: Job? = null
    
    // Connection listener
    interface ConnectionListener {
        fun onStateChanged(state: ConnectionState)
        fun onServerChanged(server: Server?)
        fun onStatsUpdated(stats: ConnectionStats)
        fun onError(message: String)
    }
    
    private val listeners = mutableListOf<ConnectionListener>()
    
    fun addConnectionListener(listener: ConnectionListener) {
        listeners.add(listener)
    }
    
    fun removeConnectionListener(listener: ConnectionListener) {
        listeners.remove(listener)
    }
    
    private fun notifyStateChanged(state: ConnectionState) {
        _connectionState.value = state
        listeners.forEach { it.onStateChanged(state) }
    }
    
    private fun notifyError(message: String) {
        listeners.forEach { it.onError(message) }
    }
    
    // ==================== CONNECTION METHODS ====================
    
    /**
     * Check if VPN permission is granted
     */
    fun prepareVpn(): Intent? {
        return VpnService.prepare(context)
    }
    
    /**
     * Connect to a server
     */
    fun connect(server: Server) {
        if (_connectionState.value == ConnectionState.CONNECTING ||
            _connectionState.value == ConnectionState.CONNECTED) {
            Log.w(TAG, "Already connected or connecting")
            return
        }
        
        _currentServer.value = server
        notifyStateChanged(ConnectionState.CONNECTING)
        
        scope.launch {
            try {
                when (server.protocol) {
                    Protocol.VMESS, Protocol.VLESS, Protocol.TROJAN, Protocol.SHADOWSOCKS -> {
                        connectV2Ray(server)
                    }
                    Protocol.SSH -> {
                        connectSSH(server)
                    }
                    Protocol.UDP -> {
                        connectUDP(server)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                notifyStateChanged(ConnectionState.ERROR)
                notifyError(e.message ?: "Connection failed")
            }
        }
    }
    
    /**
     * Connect using V2Ray protocols
     */
    private suspend fun connectV2Ray(server: Server) {
        withContext(Dispatchers.IO) {
            // Generate V2Ray config
            val config = V2RayConfigGenerator.generate(server)
            Log.d(TAG, "Generated V2Ray config for ${server.protocol}")
            
            // Save config for service to use
            context.openFileOutput("v2ray_config.json", Context.MODE_PRIVATE).use {
                it.write(config.toByteArray())
            }
            
            // Start VPN service
            val intent = Intent(context, V2rayVpnService::class.java).apply {
                action = "START"
                putExtra("server_id", server.id)
            }
            context.startService(intent)
            
            // Update state
            withContext(Dispatchers.Main) {
                notifyStateChanged(ConnectionState.CONNECTED)
                startStatsUpdater()
                
                // Update last used
                repository.saveServer(server.copy(lastUsedAt = System.currentTimeMillis()))
            }
        }
    }
    
    /**
     * Connect using SSH tunnel
     */
    private suspend fun connectSSH(server: Server) {
        sshTunnelManager.setConnectionListener(object : SSHTunnelManager.ConnectionListener {
            override fun onConnecting() {
                notifyStateChanged(ConnectionState.CONNECTING)
            }
            
            override fun onConnected() {
                scope.launch {
                    notifyStateChanged(ConnectionState.CONNECTED)
                    startStatsUpdater()
                }
            }
            
            override fun onDisconnected() {
                scope.launch {
                    notifyStateChanged(ConnectionState.DISCONNECTED)
                    stopStatsUpdater()
                }
            }
            
            override fun onError(message: String) {
                scope.launch {
                    notifyStateChanged(ConnectionState.ERROR)
                    notifyError(message)
                }
            }
        })
        
        sshTunnelManager.connect(server)
    }
    
    /**
     * Connect using UDP relay
     */
    private suspend fun connectUDP(server: Server) {
        // UDP relay typically works alongside another protocol
        // Implementation depends on specific UDP tunnel requirements
        notifyStateChanged(ConnectionState.ERROR)
        notifyError("UDP standalone connection not implemented")
    }
    
    /**
     * Disconnect current connection
     */
    fun disconnect() {
        notifyStateChanged(ConnectionState.DISCONNECTING)
        
        scope.launch {
            try {
                // Stop SSH tunnel if active
                if (sshTunnelManager.isConnected()) {
                    sshTunnelManager.disconnect()
                }
                
                // Stop VPN service
                val intent = Intent(context, V2rayVpnService::class.java).apply {
                    action = "STOP"
                }
                context.startService(intent)
                
                stopStatsUpdater()
                _currentServer.value = null
                _connectionStats.value = ConnectionStats()
                notifyStateChanged(ConnectionState.DISCONNECTED)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting", e)
                notifyStateChanged(ConnectionState.ERROR)
            }
        }
    }
    
    /**
     * Reconnect to current or last server
     */
    fun reconnect() {
        val server = _currentServer.value ?: repository.getSelectedServer()
        
        if (server != null) {
            disconnect()
            scope.launch {
                delay(500) // Brief delay before reconnecting
                connect(server)
            }
        } else {
            notifyError("No server selected")
        }
    }
    
    /**
     * Quick connect to best available server
     */
    fun quickConnect() {
        scope.launch {
            // Try last used server first
            val lastServer = repository.getSelectedServer()
            if (lastServer != null) {
                connect(lastServer)
                return@launch
            }
            
            // Otherwise pick best by latency
            val servers = repository.getAllServers()
            val bestServer = servers
                .filter { it.latency > 0 }
                .minByOrNull { it.latency }
                ?: servers.firstOrNull()
            
            if (bestServer != null) {
                connect(bestServer)
            } else {
                notifyError("No servers available")
            }
        }
    }
    
    // ==================== STATS ====================
    
    private fun startStatsUpdater() {
        statsJob?.cancel()
        statsJob = scope.launch {
            val connectedAt = System.currentTimeMillis()
            _connectionStats.value = ConnectionStats(connectedAt = connectedAt)
            
            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                // TODO: Get actual stats from V2Ray/VPN service
                val currentStats = _connectionStats.value.copy(
                    // Stats would be updated from service
                )
                _connectionStats.value = currentStats
                listeners.forEach { it.onStatsUpdated(currentStats) }
                
                // Update notification
                notificationHelper.updateVpnNotification(
                    ConnectionState.CONNECTED,
                    _currentServer.value,
                    currentStats
                )
                
                delay(1000)
            }
        }
    }
    
    private fun stopStatsUpdater() {
        statsJob?.cancel()
        statsJob = null
    }
    
    // ==================== STATE QUERIES ====================
    
    fun isConnected(): Boolean = _connectionState.value == ConnectionState.CONNECTED
    
    fun isConnecting(): Boolean = _connectionState.value == ConnectionState.CONNECTING
    
    fun getCurrentState(): ConnectionState = _connectionState.value
    
    fun getCurrentServer(): Server? = _currentServer.value
    
    fun getStats(): ConnectionStats = _connectionStats.value
}
