package com.julogic.jukavpn.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.julogic.jukavpn.MainActivity
import com.julogic.jukavpn.R
import com.julogic.jukavpn.models.ConnectionState
import com.julogic.jukavpn.models.ConnectionStats
import com.julogic.jukavpn.models.Server

class NotificationHelper(private val context: Context) {
    
    companion object {
        const val CHANNEL_VPN = "vpn_channel"
        const val CHANNEL_STATUS = "status_channel"
        const val NOTIFICATION_ID_VPN = 1
        const val NOTIFICATION_ID_STATUS = 2
        
        const val ACTION_DISCONNECT = "com.julogic.jukavpn.DISCONNECT"
        const val ACTION_CONNECT = "com.julogic.jukavpn.CONNECT"
        const val ACTION_OPEN = "com.julogic.jukavpn.OPEN"
    }
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    init {
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // VPN Service Channel (required for foreground service)
            val vpnChannel = NotificationChannel(
                CHANNEL_VPN,
                "VPN Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows VPN connection status"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            // Status Channel
            val statusChannel = NotificationChannel(
                CHANNEL_STATUS,
                "Status Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Connection status updates"
            }
            
            notificationManager.createNotificationChannel(vpnChannel)
            notificationManager.createNotificationChannel(statusChannel)
        }
    }
    
    /**
     * Create foreground notification for VPN service
     */
    fun createVpnNotification(
        state: ConnectionState,
        server: Server?,
        stats: ConnectionStats? = null
    ): Notification {
        val (title, content, icon) = when (state) {
            ConnectionState.CONNECTING -> Triple(
                "Connecting...",
                server?.let { "Connecting to ${it.name}" } ?: "Establishing connection",
                R.drawable.ic_vpn_connecting
            )
            ConnectionState.CONNECTED -> Triple(
                "Connected",
                server?.let { "${it.name} • ${stats?.getConnectionDuration() ?: ""}" }
                    ?: "VPN is active",
                R.drawable.ic_vpn_connected
            )
            ConnectionState.DISCONNECTING -> Triple(
                "Disconnecting...",
                "Closing connection",
                R.drawable.ic_vpn_connecting
            )
            ConnectionState.DISCONNECTED -> Triple(
                "Disconnected",
                "Tap to connect",
                R.drawable.ic_vpn_disconnected
            )
            ConnectionState.ERROR -> Triple(
                "Connection Error",
                "Tap to retry",
                R.drawable.ic_vpn_error
            )
        }
        
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        // Open app intent
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            pendingIntentFlags
        )
        
        val builder = NotificationCompat.Builder(context, CHANNEL_VPN)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(openIntent)
            .setOngoing(state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        
        // Add action button based on state
        when (state) {
            ConnectionState.CONNECTED -> {
                val disconnectIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(ACTION_DISCONNECT),
                    pendingIntentFlags
                )
                builder.addAction(
                    R.drawable.ic_disconnect,
                    "Disconnect",
                    disconnectIntent
                )
                
                // Add traffic stats if available
                stats?.let {
                    builder.setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText("${it.getDownloadSpeed()} ↓  ${it.getUploadSpeed()} ↑\n${it.getConnectionDuration()}")
                    )
                }
            }
            ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                val connectIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(ACTION_CONNECT),
                    pendingIntentFlags
                )
                builder.addAction(
                    R.drawable.ic_connect,
                    "Connect",
                    connectIntent
                )
            }
            else -> {}
        }
        
        return builder.build()
    }
    
    /**
     * Update VPN notification
     */
    fun updateVpnNotification(
        state: ConnectionState,
        server: Server?,
        stats: ConnectionStats? = null
    ) {
        val notification = createVpnNotification(state, server, stats)
        notificationManager.notify(NOTIFICATION_ID_VPN, notification)
    }
    
    /**
     * Show connection established notification
     */
    fun showConnectedNotification(server: Server) {
        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_vpn_connected)
            .setContentTitle("VPN Connected")
            .setContentText("Connected to ${server.name}")
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_STATUS, notification)
    }
    
    /**
     * Show connection error notification
     */
    fun showErrorNotification(message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_vpn_error)
            .setContentTitle("Connection Failed")
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_STATUS, notification)
    }
    
    /**
     * Cancel all notifications
     */
    fun cancelAll() {
        notificationManager.cancelAll()
    }
    
    /**
     * Cancel specific notification
     */
    fun cancel(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }
}
