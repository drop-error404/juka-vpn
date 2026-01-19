package com.julogic.jukavpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.julogic.jukavpn.config.V2RayConfigGenerator
import com.julogic.jukavpn.models.ConnectionState
import com.julogic.jukavpn.models.Protocol
import com.julogic.jukavpn.models.Server
import com.julogic.jukavpn.service.TrafficStats
import com.julogic.jukavpn.utils.NotificationHelper
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * VPN Service for V2Ray based connections
 * Handles VMess, VLESS, Trojan, and Shadowsocks protocols
 */
class V2rayVpnService : VpnService() {
    
    companion object {
        private const val TAG = "V2rayVpnService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "juka_vpn_channel"
        private const val CHANNEL_NAME = "JukaVPN Connection"
        
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val ACTION_RECONNECT = "RECONNECT"
        const val EXTRA_SERVER_ID = "server_id"
        
        // V2Ray ports
        private const val SOCKS_PORT = 10808
        private const val HTTP_PORT = 10809
        
        // VPN interface configuration
        private const val VPN_ADDRESS = "10.1.10.1"
        private const val VPN_ROUTE = "0.0.0.0"
        private const val VPN_DNS_1 = "8.8.8.8"
        private const val VPN_DNS_2 = "8.8.4.4"
        private const val VPN_MTU = 1500
        
        // For static access
        @Volatile
        var isRunning = false
            private set
        
        @Volatile
        var currentState: ConnectionState = ConnectionState.DISCONNECTED
            private set
    }
    
    // Native library interface for V2Ray
    private external fun startV2Ray(configPath: String): Int
    private external fun stopV2Ray(): Int
    private external fun getV2RayVersion(): String
    private external fun getUplink(): Long
    private external fun getDownlink(): Long
    private external fun isV2RayRunning(): Boolean
    private external fun protectFd(fd: Int): Boolean
    
    // Service state
    private var vpnInterface: ParcelFileDescriptor? = null
    private var tun2socksProcess: Process? = null
    private var currentServer: Server? = null
    private val isConnecting = AtomicBoolean(false)
    private val isDisconnecting = AtomicBoolean(false)
    
    // Traffic stats
    private var lastUplink: Long = 0
    private var lastDownlink: Long = 0
    private var connectedAt: Long = 0
    
    // Coroutines
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var statsJob: Job? = null
    
    // Wake lock
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Network monitoring
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    // Service binder
    private val binder = LocalBinder()
    
    inner class LocalBinder : Binder() {
        fun getService(): V2rayVpnService = this@V2rayVpnService
    }
    
    // Service callbacks
    interface ServiceCallback {
        fun onStateChanged(state: ConnectionState)
        fun onStatsUpdated(uplink: Long, downlink: Long, duration: Long)
        fun onError(message: String)
    }
    
    private var callback: ServiceCallback? = null
    
    fun setCallback(callback: ServiceCallback?) {
        this.callback = callback
    }
    
    // ==================== LIFECYCLE ====================
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        loadNativeLibrary()
        createNotificationChannel()
        setupNetworkMonitoring()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        
        when (intent?.action) {
            ACTION_START -> {
                val serverId = intent.getStringExtra(EXTRA_SERVER_ID)
                if (serverId != null) {
                    startVpn(serverId)
                } else {
                    Log.e(TAG, "No server ID provided")
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopVpn()
            }
            ACTION_RECONNECT -> {
                reconnect()
            }
            else -> {
                // Handle null action (service restart)
                currentServer?.let { 
                    startVpn(it.id) 
                } ?: stopSelf()
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onRevoke() {
        Log.w(TAG, "VPN permission revoked")
        stopVpn()
        super.onRevoke()
    }
    
    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        
        serviceScope.cancel()
        stopVpn()
        releaseWakeLock()
        unregisterNetworkCallback()
        
        super.onDestroy()
    }
    
    // ==================== VPN OPERATIONS ====================
    
    private fun startVpn(serverId: String) {
        if (isConnecting.getAndSet(true)) {
            Log.w(TAG, "Already connecting")
            return
        }
        
        serviceScope.launch {
            try {
                updateState(ConnectionState.CONNECTING)
                
                // Load server
                val serverManager = ServerManager.getInstance(this@V2rayVpnService)
                val server = serverManager.getServerById(serverId)
                
                if (server == null) {
                    throw Exception("Server not found: $serverId")
                }
                
                currentServer = server
                Log.d(TAG, "Starting VPN with server: ${server.name} (${server.protocol})")
                
                // Generate V2Ray config
                val configPath = generateConfig(server)
                Log.d(TAG, "Config generated at: $configPath")
                
                // Start foreground notification
                startForeground(NOTIFICATION_ID, createNotification(server, ConnectionState.CONNECTING))
                
                // Acquire wake lock
                acquireWakeLock()
                
                // Test server connectivity first
                if (!testServerConnectivity(server)) {
                    throw Exception("Cannot reach server: ${server.address}:${server.port}")
                }
                
                // Start V2Ray core
                val result = withContext(Dispatchers.IO) {
                    startV2Ray(configPath)
                }
                
                if (result != 0) {
                    throw Exception("V2Ray start failed with code: $result")
                }
                
                Log.d(TAG, "V2Ray core started successfully")
                
                // Create VPN interface
                createVpnInterface(server)
                
                // Start tun2socks
                startTun2Socks()
                
                // Update state
                isRunning = true
                connectedAt = System.currentTimeMillis()
                updateState(ConnectionState.CONNECTED)
                
                // Start traffic stats monitoring
                startStatsMonitoring()
                
                Log.d(TAG, "VPN connected successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "VPN start failed", e)
                callback?.onError(e.message ?: "Connection failed")
                updateState(ConnectionState.ERROR)
                stopVpn()
            } finally {
                isConnecting.set(false)
            }
        }
    }
    
    private fun stopVpn() {
        if (isDisconnecting.getAndSet(true)) {
            Log.w(TAG, "Already disconnecting")
            return
        }
        
        Log.d(TAG, "Stopping VPN")
        
        try {
            updateState(ConnectionState.DISCONNECTING)
            
            // Stop stats monitoring
            statsJob?.cancel()
            statsJob = null
            
            // Stop tun2socks
            stopTun2Socks()
            
            // Stop V2Ray core
            serviceScope.launch(Dispatchers.IO) {
                try {
                    stopV2Ray()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping V2Ray", e)
                }
            }
            
            // Close VPN interface
            vpnInterface?.close()
            vpnInterface = null
            
            // Release wake lock
            releaseWakeLock()
            
            isRunning = false
            updateState(ConnectionState.DISCONNECTED)
            
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            
            Log.d(TAG, "VPN stopped")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping VPN", e)
        } finally {
            isDisconnecting.set(false)
        }
    }
    
    private fun reconnect() {
        Log.d(TAG, "Reconnecting...")
        
        serviceScope.launch {
            stopVpn()
            delay(1000)
            currentServer?.let { startVpn(it.id) }
        }
    }
    
    // ==================== V2RAY CONFIGURATION ====================
    
    private suspend fun generateConfig(server: Server): String {
        return withContext(Dispatchers.IO) {
            // Get custom DNS settings from preferences
            val prefs = getSharedPreferences("juka_vpn_settings", Context.MODE_PRIVATE)
            val dnsServers = listOf(
                prefs.getString("dns_primary", VPN_DNS_1) ?: VPN_DNS_1,
                prefs.getString("dns_secondary", VPN_DNS_2) ?: VPN_DNS_2
            )
            
            val config = V2RayConfigGenerator.generate(server, dnsServers)
            
            // Write config to file
            val configFile = File(filesDir, "v2ray_config.json")
            FileOutputStream(configFile).use { it.write(config.toByteArray()) }
            
            configFile.absolutePath
        }
    }
    
    // ==================== VPN INTERFACE ====================
    
    private fun createVpnInterface(server: Server) {
        val builder = Builder()
            .setSession(server.name)
            .setMtu(VPN_MTU)
            .addAddress(VPN_ADDRESS, 32)
            .addRoute(VPN_ROUTE, 0)
            .addDnsServer(VPN_DNS_1)
            .addDnsServer(VPN_DNS_2)
        
        // Add blocking for this app to prevent loops
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {
                Log.w(TAG, "Could not exclude app from VPN", e)
            }
        }
        
        // Load app bypass settings
        loadAppBypassSettings(builder)
        
        vpnInterface = builder.establish()
        
        if (vpnInterface == null) {
            throw Exception("Failed to establish VPN interface")
        }
        
        Log.d(TAG, "VPN interface created with fd: ${vpnInterface?.fd}")
    }
    
    private fun loadAppBypassSettings(builder: Builder) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        
        try {
            val prefs = getSharedPreferences("juka_vpn_settings", Context.MODE_PRIVATE)
            val bypassApps = prefs.getStringSet("bypass_apps", emptySet()) ?: emptySet()
            val proxyMode = prefs.getString("proxy_mode", "all") ?: "all"
            
            when (proxyMode) {
                "bypass" -> {
                    // Only selected apps bypass VPN
                    for (packageName in bypassApps) {
                        try {
                            builder.addDisallowedApplication(packageName)
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not bypass app: $packageName", e)
                        }
                    }
                }
                "proxy" -> {
                    // Only selected apps use VPN
                    for (packageName in bypassApps) {
                        try {
                            builder.addAllowedApplication(packageName)
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not add allowed app: $packageName", e)
                        }
                    }
                }
                // "all" -> route all traffic through VPN (default)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading app bypass settings", e)
        }
    }
    
    // ==================== TUN2SOCKS ====================
    
    private fun startTun2Socks() {
        val fd = vpnInterface?.fd ?: throw Exception("VPN interface not available")
        
        try {
            val tun2socksPath = "${applicationInfo.nativeLibraryDir}/libtun2socks.so"
            
            val cmd = arrayOf(
                tun2socksPath,
                "--netif-ipaddr", "10.1.10.2",
                "--netif-netmask", "255.255.255.252",
                "--socks-server-addr", "127.0.0.1:$SOCKS_PORT",
                "--tunmtu", VPN_MTU.toString(),
                "--sock-path", "sock_path",
                "--enable-udprelay",
                "--loglevel", "warning"
            )
            
            val pb = ProcessBuilder(*cmd)
            pb.redirectErrorStream(true)
            pb.environment()["TUN_FD"] = fd.toString()
            
            tun2socksProcess = pb.start()
            
            Log.d(TAG, "tun2socks started with PID: ${tun2socksProcess?.toString()}")
            
            // Monitor process
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val exitCode = tun2socksProcess?.waitFor()
                    Log.w(TAG, "tun2socks exited with code: $exitCode")
                    
                    if (isRunning && exitCode != 0) {
                        withContext(Dispatchers.Main) {
                            callback?.onError("tun2socks crashed")
                            reconnect()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error monitoring tun2socks", e)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start tun2socks", e)
            throw e
        }
    }
    
    private fun stopTun2Socks() {
        try {
            tun2socksProcess?.let { process ->
                process.destroy()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    process.destroyForcibly()
                }
            }
            tun2socksProcess = null
            Log.d(TAG, "tun2socks stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping tun2socks", e)
        }
    }
    
    // ==================== TRAFFIC STATS ====================
    
    private fun startStatsMonitoring() {
        statsJob = serviceScope.launch {
            while (isActive && isRunning) {
                try {
                    val uplink = getUplink()
                    val downlink = getDownlink()
                    val duration = if (connectedAt > 0) {
                        (System.currentTimeMillis() - connectedAt) / 1000
                    } else 0L
                    
                    // Calculate speed
                    val uploadSpeed = uplink - lastUplink
                    val downloadSpeed = downlink - lastDownlink
                    lastUplink = uplink
                    lastDownlink = downlink
                    
                    // Update notification
                    currentServer?.let { server ->
                        val notification = createNotification(
                            server,
                            ConnectionState.CONNECTED,
                            uplink,
                            downlink,
                            uploadSpeed,
                            downloadSpeed
                        )
                        val nm = getSystemService(NotificationManager::class.java)
                        nm.notify(NOTIFICATION_ID, notification)
                    }
                    
                    // Notify callback
                    callback?.onStatsUpdated(uplink, downlink, duration)
                    
                    delay(1000)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating stats", e)
                }
            }
        }
    }
    
    // ==================== NOTIFICATIONS ====================
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "VPN connection status"
                setShowBadge(false)
            }
            
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(
        server: Server,
        state: ConnectionState,
        uplink: Long = 0,
        downlink: Long = 0,
        uploadSpeed: Long = 0,
        downloadSpeed: Long = 0
    ): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val disconnectIntent = Intent(this, V2rayVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this, 1, disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val title = when (state) {
            ConnectionState.CONNECTING -> "Connecting..."
            ConnectionState.CONNECTED -> "Connected to ${server.name}"
            ConnectionState.DISCONNECTING -> "Disconnecting..."
            else -> "Disconnected"
        }
        
        val content = when (state) {
            ConnectionState.CONNECTED -> {
                val up = formatBytes(uplink)
                val down = formatBytes(downlink)
                val upSpeed = formatBytes(uploadSpeed) + "/s"
                val downSpeed = formatBytes(downloadSpeed) + "/s"
                "↑ $up ($upSpeed) | ↓ $down ($downSpeed)"
            }
            ConnectionState.CONNECTING -> server.getDisplayAddress()
            else -> ""
        }
        
        val icon = when (state) {
            ConnectionState.CONNECTED -> R.drawable.ic_vpn_connected
            ConnectionState.CONNECTING -> R.drawable.ic_vpn_connecting
            ConnectionState.ERROR -> R.drawable.ic_vpn_error
            else -> R.drawable.ic_vpn_disconnected
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setOngoing(state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                R.drawable.ic_disconnect,
                "Disconnect",
                disconnectPendingIntent
            )
            .build()
    }
    
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> String.format("%.2f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> String.format("%.2f MB", bytes / 1_048_576.0)
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
    
    // ==================== NETWORK MONITORING ====================
    
    private fun setupNetworkMonitoring() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
            }
            
            override fun onLost(network: Network) {
                Log.w(TAG, "Network lost")
                if (isRunning) {
                    callback?.onError("Network connection lost")
                    // Could trigger reconnect here
                }
            }
            
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val hasValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                Log.d(TAG, "Network capabilities changed: internet=$hasInternet, validated=$hasValidated")
            }
        }
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager?.registerNetworkCallback(request, networkCallback!!)
    }
    
    private fun unregisterNetworkCallback() {
        networkCallback?.let { callback ->
            try {
                connectivityManager?.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering network callback", e)
            }
        }
        networkCallback = null
    }
    
    // ==================== UTILITY METHODS ====================
    
    private fun loadNativeLibrary() {
        try {
            System.loadLibrary("xray")
            Log.d(TAG, "V2Ray native library loaded. Version: ${getV2RayVersion()}")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load V2Ray native library", e)
        }
    }
    
    private suspend fun testServerConnectivity(server: Server): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(server.address, server.port), 5000)
                socket.close()
                true
            } catch (e: Exception) {
                Log.w(TAG, "Server connectivity test failed", e)
                false
            }
        }
    }
    
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JukaVPN::VpnWakeLock")
            wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 hours max
        }
    }
    
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }
    
    private fun updateState(state: ConnectionState) {
        currentState = state
        callback?.onStateChanged(state)
        
        // Broadcast state change
        val intent = Intent("com.julogic.jukavpn.VPN_STATE_CHANGED").apply {
            putExtra("state", state.name)
        }
        sendBroadcast(intent)
    }
    
    // ==================== PUBLIC API ====================
    
    fun getConnectionDuration(): Long {
        return if (connectedAt > 0 && isRunning) {
            (System.currentTimeMillis() - connectedAt) / 1000
        } else 0
    }
    
    fun getTotalUplink(): Long = lastUplink
    
    fun getTotalDownlink(): Long = lastDownlink
    
    fun getCurrentServerInfo(): Server? = currentServer
    
    fun getState(): ConnectionState = currentState
}
