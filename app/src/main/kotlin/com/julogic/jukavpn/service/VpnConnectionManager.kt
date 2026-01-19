package com.julogic.jukavpn.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Build
import android.util.Log
import com.julogic.jukavpn.ServerManager
import com.julogic.jukavpn.V2rayVpnService
import com.julogic.jukavpn.config.V2RayConfigGenerator
import com.julogic.jukavpn.models.ConnectionState
import com.julogic.jukavpn.models.ConnectionStats
import com.julogic.jukavpn.models.Protocol
import com.julogic.jukavpn.models.Server
import com.julogic.jukavpn.parsers.*
import com.julogic.jukavpn.tunnel.SSHTunnelManager
import com.julogic.jukavpn.tunnel.UDPRelayManager
import com.julogic.jukavpn.utils.LatencyTester
import com.julogic.jukavpn.utils.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Central manager for VPN connections
 * Integrates all parsers and protocols (VMess, VLESS, Trojan, Shadowsocks, SSH, UDP)
 * Handles connection lifecycle, stats, reconnection and settings
 */
class VpnConnectionManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "VpnConnectionManager"
        private const val PREFS_NAME = "vpn_connection_prefs"
        private const val PREF_LAST_SERVER_ID = "last_server_id"
        private const val PREF_AUTO_CONNECT = "auto_connect"
        private const val PREF_RECONNECT_ENABLED = "reconnect_enabled"
        private const val PREF_KILL_SWITCH = "kill_switch"
        private const val PREF_DNS_PRIMARY = "dns_primary"
        private const val PREF_DNS_SECONDARY = "dns_secondary"
        private const val PREF_BYPASS_LAN = "bypass_lan"
        private const val PREF_IPV6_ENABLED = "ipv6_enabled"
        
        private const val CONFIG_FILE = "v2ray_config.json"
        private const val RECONNECT_DELAY = 2000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val STATS_UPDATE_INTERVAL = 1000L
        
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
    
    // Dependencies
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val serverManager = ServerManager.getInstance(context)
    private val notificationHelper = NotificationHelper(context)
    private val sshTunnelManager = SSHTunnelManager()
    private val udpRelayManager = UDPRelayManager()
    private lateinit var latencyTester: LatencyTester
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Connection state
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _currentServer = MutableStateFlow<Server?>(null)
    val currentServer: StateFlow<Server?> = _currentServer.asStateFlow()
    
    private val _connectionStats = MutableStateFlow(ConnectionStats())
    val connectionStats: StateFlow<ConnectionStats> = _connectionStats.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // Internal state
    private var statsJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private val isReconnecting = AtomicBoolean(false)
    private var connectionStartTime = 0L
    
    // Settings cache
    private var autoConnectEnabled = false
    private var reconnectEnabled = true
    private var killSwitchEnabled = false
    private var bypassLanEnabled = true
    private var ipv6Enabled = false
    private var primaryDns = "8.8.8.8"
    private var secondaryDns = "8.8.4.4"
    
    init {
        loadSettings()
    }
    
    // ==================== SETTINGS ====================
    
    private fun loadSettings() {
        autoConnectEnabled = prefs.getBoolean(PREF_AUTO_CONNECT, false)
        reconnectEnabled = prefs.getBoolean(PREF_RECONNECT_ENABLED, true)
        killSwitchEnabled = prefs.getBoolean(PREF_KILL_SWITCH, false)
        bypassLanEnabled = prefs.getBoolean(PREF_BYPASS_LAN, true)
        ipv6Enabled = prefs.getBoolean(PREF_IPV6_ENABLED, false)
        primaryDns = prefs.getString(PREF_DNS_PRIMARY, "8.8.8.8") ?: "8.8.8.8"
        secondaryDns = prefs.getString(PREF_DNS_SECONDARY, "8.8.4.4") ?: "8.8.4.4"
    }
    
    fun saveSettings(
        autoConnect: Boolean? = null,
        reconnect: Boolean? = null,
        killSwitch: Boolean? = null,
        bypassLan: Boolean? = null,
        ipv6: Boolean? = null,
        dnsPrimary: String? = null,
        dnsSecondary: String? = null
    ) {
        prefs.edit().apply {
            autoConnect?.let { 
                putBoolean(PREF_AUTO_CONNECT, it)
                autoConnectEnabled = it
            }
            reconnect?.let { 
                putBoolean(PREF_RECONNECT_ENABLED, it)
                reconnectEnabled = it
            }
            killSwitch?.let { 
                putBoolean(PREF_KILL_SWITCH, it)
                killSwitchEnabled = it
            }
            bypassLan?.let { 
                putBoolean(PREF_BYPASS_LAN, it)
                bypassLanEnabled = it
            }
            ipv6?.let { 
                putBoolean(PREF_IPV6_ENABLED, it)
                ipv6Enabled = it
            }
            dnsPrimary?.let { 
                putString(PREF_DNS_PRIMARY, it)
                primaryDns = it
            }
            dnsSecondary?.let { 
                putString(PREF_DNS_SECONDARY, it)
                secondaryDns = it
            }
            apply()
        }
    }
    
    fun getSettings(): ConnectionSettings {
        return ConnectionSettings(
            autoConnect = autoConnectEnabled,
            reconnect = reconnectEnabled,
            killSwitch = killSwitchEnabled,
            bypassLan = bypassLanEnabled,
            ipv6 = ipv6Enabled,
            primaryDns = primaryDns,
            secondaryDns = secondaryDns
        )
    }
    
    // ==================== CONNECTION LISTENERS ====================
    
    interface ConnectionListener {
        fun onStateChanged(state: ConnectionState)
        fun onServerChanged(server: Server?)
        fun onStatsUpdated(stats: ConnectionStats)
        fun onError(message: String)
    }
    
    private val listeners = mutableListOf<ConnectionListener>()
    
    fun addConnectionListener(listener: ConnectionListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }
    
    fun removeConnectionListener(listener: ConnectionListener) {
        listeners.remove(listener)
    }
    
    private fun notifyStateChanged(state: ConnectionState) {
        _connectionState.value = state
        scope.launch(Dispatchers.Main) {
            listeners.forEach { it.onStateChanged(state) }
        }
    }
    
    private fun notifyServerChanged(server: Server?) {
        _currentServer.value = server
        scope.launch(Dispatchers.Main) {
            listeners.forEach { it.onServerChanged(server) }
        }
    }
    
    private fun notifyStatsUpdated(stats: ConnectionStats) {
        _connectionStats.value = stats
        scope.launch(Dispatchers.Main) {
            listeners.forEach { it.onStatsUpdated(stats) }
        }
    }
    
    private fun notifyError(message: String) {
        _errorMessage.value = message
        scope.launch(Dispatchers.Main) {
            listeners.forEach { it.onError(message) }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    // ==================== VPN PERMISSION ====================
    
    /**
     * Check if VPN permission is granted
     * Returns Intent if permission needed, null if already granted
     */
    fun prepareVpn(): Intent? {
        return VpnService.prepare(context)
    }
    
    /**
     * Check if VPN is configured (has permission)
     */
    fun isVpnConfigured(): Boolean {
        return VpnService.prepare(context) == null
    }
    
    // ==================== URI PARSING ====================
    
    /**
     * Parse URI and create server config
     */
    fun parseUri(uri: String): Server? {
        val trimmed = uri.trim()
        return when {
            trimmed.startsWith("vmess://", ignoreCase = true) -> VmessParser.parse(trimmed)
            trimmed.startsWith("vless://", ignoreCase = true) -> VlessParser.parse(trimmed)
            trimmed.startsWith("trojan://", ignoreCase = true) -> TrojanParser.parse(trimmed)
            trimmed.startsWith("ss://", ignoreCase = true) -> ShadowsocksParser.parse(trimmed)
            trimmed.startsWith("ssh://", ignoreCase = true) -> SSHConfigParser.parse(trimmed)
            else -> null
        }
    }
    
    /**
     * Parse multiple URIs from text
     */
    fun parseMultipleUris(text: String): List<Server> {
        val lines = text.split("\n", "\r\n", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        
        return lines.mapNotNull { parseUri(it) }
    }
    
    /**
     * Convert server to URI
     */
    fun serverToUri(server: Server): String? {
        return when (server.protocol) {
            Protocol.VMESS -> VmessParser.toUri(server)
            Protocol.VLESS -> VlessParser.toUri(server)
            Protocol.TROJAN -> TrojanParser.toUri(server)
            Protocol.SHADOWSOCKS -> ShadowsocksParser.toUri(server)
            Protocol.SSH -> SSHConfigParser.toUri(server)
            Protocol.UDP -> null
        }
    }
    
    /**
     * Validate server configuration
     */
    fun validateServer(server: Server): Boolean {
        return when (server.protocol) {
            Protocol.VMESS -> VmessParser.validate(server)
            Protocol.VLESS -> VlessParser.validate(server)
            Protocol.TROJAN -> TrojanParser.validate(server)
            Protocol.SHADOWSOCKS -> ShadowsocksParser.validate(server)
            Protocol.SSH -> SSHConfigParser.validate(server)
            Protocol.UDP -> server.address.isNotEmpty() && server.port in 1..65535
        }
    }
    
    // ==================== CONNECTION METHODS ====================
    
    /**
     * Connect to a server
     */
    fun connect(server: Server) {
        if (_connectionState.value == ConnectionState.CONNECTING) {
            Log.w(TAG, "Already connecting")
            return
        }
        
        if (_connectionState.value == ConnectionState.CONNECTED) {
            Log.w(TAG, "Already connected, disconnecting first")
            disconnect()
            scope.launch {
                delay(500)
                startConnection(server)
            }
            return
        }
        
        startConnection(server)
    }
    
    private fun startConnection(server: Server) {
        // Validate server
        if (!validateServer(server)) {
            notifyError("Invalid server configuration")
            notifyStateChanged(ConnectionState.ERROR)
            return
        }
        
        notifyServerChanged(server)
        notifyStateChanged(ConnectionState.CONNECTING)
        reconnectAttempts = 0
        
        scope.launch {
            try {
                when (server.protocol) {
                    Protocol.VMESS -> connectV2Ray(server, "vmess")
                    Protocol.VLESS -> connectV2Ray(server, "vless")
                    Protocol.TROJAN -> connectV2Ray(server, "trojan")
                    Protocol.SHADOWSOCKS -> connectShadowsocks(server)
                    Protocol.SSH -> connectSSH(server)
                    Protocol.UDP -> connectUDP(server)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                handleConnectionError(e.message ?: "Connection failed")
            }
        }
    }
    
    /**
     * Connect using V2Ray protocols (VMess, VLESS, Trojan)
     */
    private suspend fun connectV2Ray(server: Server, protocol: String) {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Connecting with $protocol to ${server.address}:${server.port}")
            
            // Generate V2Ray config with custom DNS
            val config = V2RayConfigGenerator.generate(
                server = server,
                primaryDns = primaryDns,
                secondaryDns = secondaryDns,
                bypassLan = bypassLanEnabled,
                enableIpv6 = ipv6Enabled
            )
            
            // Save config file
            val configFile = File(context.filesDir, CONFIG_FILE)
            configFile.writeText(config)
            Log.d(TAG, "Config saved to ${configFile.absolutePath}")
            
            // Save last server ID
            prefs.edit().putString(PREF_LAST_SERVER_ID, server.id).apply()
            
            // Start VPN service
            val intent = Intent(context, V2rayVpnService::class.java).apply {
                action = V2rayVpnService.ACTION_START
                putExtra(V2rayVpnService.EXTRA_SERVER_ID, server.id)
                putExtra(V2rayVpnService.EXTRA_CONFIG_PATH, configFile.absolutePath)
                putExtra(V2rayVpnService.EXTRA_DNS_PRIMARY, primaryDns)
                putExtra(V2rayVpnService.EXTRA_DNS_SECONDARY, secondaryDns)
                putExtra(V2rayVpnService.EXTRA_BYPASS_LAN, bypassLanEnabled)
                putExtra(V2rayVpnService.EXTRA_KILL_SWITCH, killSwitchEnabled)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            
            // Wait for connection confirmation
            withContext(Dispatchers.Main) {
                connectionStartTime = System.currentTimeMillis()
                notifyStateChanged(ConnectionState.CONNECTED)
                startStatsUpdater()
                
                // Update server last used
                serverManager.updateServer(server.copy(lastUsedAt = System.currentTimeMillis()))
            }
        }
    }
    
    /**
     * Connect using Shadowsocks (can use V2Ray or native ss)
     */
    private suspend fun connectShadowsocks(server: Server) {
        // Use V2Ray for Shadowsocks as well
        connectV2Ray(server, "shadowsocks")
    }
    
    /**
     * Connect using SSH tunnel
     */
    private suspend fun connectSSH(server: Server) {
        Log.d(TAG, "Connecting SSH to ${server.address}:${server.sshPort}")
        
        sshTunnelManager.setConnectionListener(object : SSHTunnelManager.ConnectionListener {
            override fun onConnecting() {
                scope.launch {
                    notifyStateChanged(ConnectionState.CONNECTING)
                }
            }
            
            override fun onConnected() {
                scope.launch {
                    connectionStartTime = System.currentTimeMillis()
                    notifyStateChanged(ConnectionState.CONNECTED)
                    startStatsUpdater()
                    prefs.edit().putString(PREF_LAST_SERVER_ID, server.id).apply()
                    serverManager.updateServer(server.copy(lastUsedAt = System.currentTimeMillis()))
                }
            }
            
            override fun onDisconnected() {
                scope.launch {
                    handleDisconnection()
                }
            }
            
            override fun onError(message: String) {
                scope.launch {
                    handleConnectionError(message)
                }
            }
            
            override fun onTrafficUpdate(download: Long, upload: Long) {
                scope.launch {
                    val currentStats = _connectionStats.value
                    notifyStatsUpdated(currentStats.copy(
                        bytesDownloaded = download,
                        bytesUploaded = upload
                    ))
                }
            }
        })
        
        withContext(Dispatchers.IO) {
            sshTunnelManager.connect(
                server = server,
                localPort = 1080,
                dnsPort = 5353
            )
        }
    }
    
    /**
     * Connect using UDP relay
     */
    private suspend fun connectUDP(server: Server) {
        Log.d(TAG, "Connecting UDP to ${server.address}:${server.udpPort}")
        
        udpRelayManager.setConnectionListener(object : UDPRelayManager.ConnectionListener {
            override fun onConnected() {
                scope.launch {
                    connectionStartTime = System.currentTimeMillis()
                    notifyStateChanged(ConnectionState.CONNECTED)
                    startStatsUpdater()
                }
            }
            
            override fun onDisconnected() {
                scope.launch {
                    handleDisconnection()
                }
            }
            
            override fun onError(message: String) {
                scope.launch {
                    handleConnectionError(message)
                }
            }
        })
        
        withContext(Dispatchers.IO) {
            udpRelayManager.start(
                serverAddress = server.address,
                serverPort = server.udpPort ?: server.port,
                obfs = server.obfs,
                obfsParam = server.obfsParam
            )
        }
    }
    
    /**
     * Handle connection errors
     */
    private fun handleConnectionError(message: String) {
        Log.e(TAG, "Connection error: $message")
        notifyError(message)
        
        if (reconnectEnabled && reconnectAttempts < MAX_RECONNECT_ATTEMPTS && !isReconnecting.get()) {
            scheduleReconnect()
        } else {
            notifyStateChanged(ConnectionState.ERROR)
            stopStatsUpdater()
        }
    }
    
    /**
     * Handle disconnection (possibly unexpected)
     */
    private fun handleDisconnection() {
        if (_connectionState.value == ConnectionState.DISCONNECTING) {
            // Normal disconnection
            notifyStateChanged(ConnectionState.DISCONNECTED)
            stopStatsUpdater()
            return
        }
        
        // Unexpected disconnection
        Log.w(TAG, "Unexpected disconnection")
        
        if (reconnectEnabled && !isReconnecting.get()) {
            scheduleReconnect()
        } else {
            notifyStateChanged(ConnectionState.DISCONNECTED)
            stopStatsUpdater()
        }
    }
    
    /**
     * Schedule reconnection attempt
     */
    private fun scheduleReconnect() {
        if (isReconnecting.getAndSet(true)) return
        
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            reconnectAttempts++
            val delay = RECONNECT_DELAY * reconnectAttempts
            
            Log.d(TAG, "Scheduling reconnect attempt $reconnectAttempts in ${delay}ms")
            notifyStateChanged(ConnectionState.CONNECTING)
            
            delay(delay)
            
            val server = _currentServer.value
            if (server != null) {
                isReconnecting.set(false)
                startConnection(server)
            } else {
                isReconnecting.set(false)
                notifyStateChanged(ConnectionState.DISCONNECTED)
            }
        }
    }
    
    /**
     * Disconnect current connection
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting...")
        
        reconnectJob?.cancel()
        isReconnecting.set(false)
        reconnectAttempts = 0
        
        notifyStateChanged(ConnectionState.DISCONNECTING)
        
        scope.launch {
            try {
                // Stop SSH tunnel if active
                if (sshTunnelManager.isConnected()) {
                    sshTunnelManager.disconnect()
                }
                
                // Stop UDP relay if active
                if (udpRelayManager.isRunning()) {
                    udpRelayManager.stop()
                }
                
                // Stop V2Ray VPN service
                val intent = Intent(context, V2rayVpnService::class.java).apply {
                    action = V2rayVpnService.ACTION_STOP
                }
                context.startService(intent)
                
                stopStatsUpdater()
                notifyStateChanged(ConnectionState.DISCONNECTED)
                
                // Clear stats
                _connectionStats.value = ConnectionStats()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during disconnect", e)
                notifyStateChanged(ConnectionState.DISCONNECTED)
            }
        }
    }
    
    /**
     * Toggle connection state
     */
    fun toggleConnection() {
        when (_connectionState.value) {
            ConnectionState.CONNECTED, ConnectionState.CONNECTING -> disconnect()
            ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                val server = _currentServer.value ?: getLastServer()
                if (server != null) {
                    connect(server)
                } else {
                    notifyError("No server selected")
                }
            }
            ConnectionState.DISCONNECTING -> {
                // Wait for disconnection
            }
        }
    }
    
    /**
     * Reconnect to current server
     */
    fun reconnect() {
        val server = _currentServer.value ?: getLastServer()
        
        if (server != null) {
            disconnect()
            scope.launch {
                delay(500)
                connect(server)
            }
        } else {
            notifyError("No server to reconnect")
        }
    }
    
    /**
     * Quick connect to best available server
     */
    fun quickConnect() {
        scope.launch {
            // Try last used server first
            val lastServer = getLastServer()
            if (lastServer != null) {
                connect(lastServer)
                return@launch
            }
            
            // Otherwise pick best by latency
            val servers = serverManager.servers.value
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
    
    /**
     * Get last used server
     */
    fun getLastServer(): Server? {
        val lastServerId = prefs.getString(PREF_LAST_SERVER_ID, null) ?: return null
        return serverManager.getServerById(lastServerId)
    }
    
    /**
     * Auto connect on app start if enabled
     */
    fun autoConnectIfEnabled() {
        if (autoConnectEnabled && _connectionState.value == ConnectionState.DISCONNECTED) {
            quickConnect()
        }
    }
    
    // ==================== STATS ====================
    
    private fun startStatsUpdater() {
        statsJob?.cancel()
        statsJob = scope.launch {
            _connectionStats.value = ConnectionStats(
                connectedAt = connectionStartTime,
                bytesDownloaded = 0,
                bytesUploaded = 0
            )
            
            var lastDownload = 0L
            var lastUpload = 0L
            var lastUpdateTime = System.currentTimeMillis()
            
            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                try {
                    // Get stats from traffic stats
                    val currentStats = TrafficStats.getStats()
                    val currentTime = System.currentTimeMillis()
                    val timeDiff = (currentTime - lastUpdateTime) / 1000.0
                    
                    val downloadDiff = currentStats.rxBytes - lastDownload
                    val uploadDiff = currentStats.txBytes - lastUpload
                    
                    val downloadSpeed = if (timeDiff > 0) (downloadDiff / timeDiff).toLong() else 0L
                    val uploadSpeed = if (timeDiff > 0) (uploadDiff / timeDiff).toLong() else 0L
                    
                    val updatedStats = ConnectionStats(
                        connectedAt = connectionStartTime,
                        bytesDownloaded = currentStats.rxBytes,
                        bytesUploaded = currentStats.txBytes,
                        downloadSpeed = downloadSpeed,
                        uploadSpeed = uploadSpeed
                    )
                    
                    notifyStatsUpdated(updatedStats)
                    
                    // Update notification
                    notificationHelper.updateVpnNotification(
                        state = ConnectionState.CONNECTED,
                        server = _currentServer.value,
                        stats = updatedStats
                    )
                    
                    lastDownload = currentStats.rxBytes
                    lastUpload = currentStats.txBytes
                    lastUpdateTime = currentTime
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating stats", e)
                }
                
                delay(STATS_UPDATE_INTERVAL)
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
    
    fun isDisconnected(): Boolean = _connectionState.value == ConnectionState.DISCONNECTED
    
    fun getCurrentState(): ConnectionState = _connectionState.value
    
    fun getCurrentServer(): Server? = _currentServer.value
    
    fun getStats(): ConnectionStats = _connectionStats.value
    
    fun getConnectionDuration(): Long {
        return if (connectionStartTime > 0 && isConnected()) {
            System.currentTimeMillis() - connectionStartTime
        } else {
            0L
        }
    }
    
    // ==================== NETWORK INFO ====================
    
    /**
     * Get current network interface info
     */
    fun getNetworkInfo(): NetworkInfo {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
            val vpnInterface = interfaces.find { it.name.startsWith("tun") || it.name.startsWith("ppp") }
            
            NetworkInfo(
                isVpnActive = vpnInterface != null,
                vpnInterface = vpnInterface?.name,
                addresses = vpnInterface?.inetAddresses?.toList()?.map { it.hostAddress ?: "" } ?: emptyList()
            )
        } catch (e: Exception) {
            NetworkInfo(false, null, emptyList())
        }
    }
    
    // ==================== CLEANUP ====================
    
    fun cleanup() {
        disconnect()
        scope.cancel()
    }
    
    // ==================== DATA CLASSES ====================
    
    data class ConnectionSettings(
        val autoConnect: Boolean,
        val reconnect: Boolean,
        val killSwitch: Boolean,
        val bypassLan: Boolean,
        val ipv6: Boolean,
        val primaryDns: String,
        val secondaryDns: String
    )
    
    data class NetworkInfo(
        val isVpnActive: Boolean,
        val vpnInterface: String?,
        val addresses: List<String>
    )
}
