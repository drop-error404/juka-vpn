package com.julogic.jukavpn.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.julogic.jukavpn.MainActivity
import com.julogic.jukavpn.R
import com.julogic.jukavpn.models.ConnectionState
import com.julogic.jukavpn.models.ConnectionStats
import com.julogic.jukavpn.models.Server
import com.julogic.jukavpn.receiver.VpnActionReceiver

/**
 * Comprehensive notification helper for VPN service
 * Handles notification channels, persistent notifications, actions, and traffic stats
 */
class NotificationHelper(private val context: Context) {
    
    companion object {
        // Notification Channel IDs
        const val CHANNEL_VPN_SERVICE = "vpn_service"
        const val CHANNEL_VPN_STATUS = "vpn_status"
        const val CHANNEL_VPN_ALERTS = "vpn_alerts"
        const val CHANNEL_VPN_UPDATES = "vpn_updates"
        const val CHANNEL_VPN_TRAFFIC = "vpn_traffic"
        
        // Notification IDs
        const val NOTIFICATION_VPN_SERVICE = 1
        const val NOTIFICATION_VPN_CONNECTED = 2
        const val NOTIFICATION_VPN_DISCONNECTED = 3
        const val NOTIFICATION_VPN_ERROR = 4
        const val NOTIFICATION_SUBSCRIPTION_UPDATE = 5
        const val NOTIFICATION_LATENCY_TEST = 6
        const val NOTIFICATION_TRAFFIC_WARNING = 7
        
        // Action constants
        const val ACTION_CONNECT = "com.julogic.jukavpn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.julogic.jukavpn.ACTION_DISCONNECT"
        const val ACTION_PAUSE = "com.julogic.jukavpn.ACTION_PAUSE"
        const val ACTION_RESUME = "com.julogic.jukavpn.ACTION_RESUME"
        const val ACTION_SWITCH_SERVER = "com.julogic.jukavpn.ACTION_SWITCH_SERVER"
        const val ACTION_OPEN_APP = "com.julogic.jukavpn.ACTION_OPEN_APP"
        const val ACTION_DISMISS = "com.julogic.jukavpn.ACTION_DISMISS"
        const val ACTION_RECONNECT = "com.julogic.jukavpn.ACTION_RECONNECT"
        const val ACTION_RETRY = "com.julogic.jukavpn.ACTION_RETRY"
        
        // Singleton for static access
        @Volatile
        private var instance: NotificationHelper? = null
        
        fun getInstance(context: Context): NotificationHelper {
            return instance ?: synchronized(this) {
                instance ?: NotificationHelper(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    init {
        createNotificationChannels()
    }
    
    /**
     * Create all notification channels (call in Application.onCreate)
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = mutableListOf<NotificationChannel>()
            
            // VPN Service Channel - Required for foreground service (IMPORTANCE_LOW = no sound)
            channels.add(NotificationChannel(
                CHANNEL_VPN_SERVICE,
                context.getString(R.string.notification_channel_service),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_service_desc)
                setShowBadge(false)
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
            
            // VPN Status Channel - Connection status updates
            channels.add(NotificationChannel(
                CHANNEL_VPN_STATUS,
                context.getString(R.string.notification_channel_status),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_status_desc)
                setShowBadge(false)
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
            
            // VPN Alerts Channel - Important alerts (connection drops, errors)
            channels.add(NotificationChannel(
                CHANNEL_VPN_ALERTS,
                context.getString(R.string.notification_channel_alerts),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_alerts_desc)
                setShowBadge(true)
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
            
            // Updates Channel - Subscription and server updates
            channels.add(NotificationChannel(
                CHANNEL_VPN_UPDATES,
                context.getString(R.string.notification_channel_updates),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_updates_desc)
                setShowBadge(true)
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
            
            // Traffic Channel - Data usage notifications
            channels.add(NotificationChannel(
                CHANNEL_VPN_TRAFFIC,
                context.getString(R.string.notification_channel_traffic),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_traffic_desc)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
            
            notificationManager.createNotificationChannels(channels)
        }
    }
    
    // ==================== PENDING INTENTS ====================
    
    /**
     * Create main activity pending intent
     */
    private fun getMainActivityIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            getPendingIntentFlags()
        )
    }
    
    /**
     * Create action pending intent for broadcast receiver
     */
    private fun getActionIntent(action: String, requestCode: Int = 0): PendingIntent {
        val intent = Intent(context, VpnActionReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            getPendingIntentFlags()
        )
    }
    
    /**
     * Get pending intent flags based on SDK version
     */
    private fun getPendingIntentFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }
    
    // ==================== VPN SERVICE NOTIFICATIONS ====================
    
    /**
     * Create foreground notification for VPN service based on state
     */
    fun createVpnNotification(
        state: ConnectionState,
        server: Server?,
        stats: ConnectionStats? = null
    ): Notification {
        return when (state) {
            ConnectionState.CONNECTING -> buildConnectingNotification(server)
            ConnectionState.CONNECTED -> buildConnectedNotification(server, stats)
            ConnectionState.DISCONNECTING -> buildDisconnectingNotification()
            ConnectionState.DISCONNECTED -> buildDisconnectedNotification()
            ConnectionState.ERROR -> buildErrorNotification(null)
        }
    }
    
    /**
     * Build connecting notification
     */
    fun buildConnectingNotification(server: Server?): Notification {
        val serverName = server?.name ?: context.getString(R.string.unknown_server)
        val countryEmoji = server?.let { CountryUtils.getFlagEmoji(it.countryCode) } ?: "🌐"
        
        return NotificationCompat.Builder(context, CHANNEL_VPN_SERVICE)
            .setSmallIcon(R.drawable.ic_vpn_connecting)
            .setContentTitle(context.getString(R.string.notification_connecting))
            .setContentText("$countryEmoji $serverName")
            .setContentIntent(getMainActivityIntent())
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(0, 0, true)
            .addAction(
                R.drawable.ic_disconnect,
                context.getString(R.string.action_cancel),
                getActionIntent(ACTION_DISCONNECT, 1)
            )
            .build()
    }
    
    /**
     * Build connected notification with traffic stats
     */
    fun buildConnectedNotification(
        server: Server?,
        stats: ConnectionStats? = null
    ): Notification {
        val serverName = server?.name ?: context.getString(R.string.unknown_server)
        val countryEmoji = server?.let { CountryUtils.getFlagEmoji(it.countryCode) } ?: "🌐"
        val countryName = server?.let { CountryUtils.getCountryName(it.countryCode) } ?: ""
        
        val contentText = buildString {
            append("$countryEmoji $serverName")
            if (countryName.isNotEmpty() && countryName != "Unknown") {
                append(" • $countryName")
            }
        }
        
        val builder = NotificationCompat.Builder(context, CHANNEL_VPN_SERVICE)
            .setSmallIcon(R.drawable.ic_vpn_connected)
            .setContentTitle(context.getString(R.string.notification_connected))
            .setContentText(contentText)
            .setContentIntent(getMainActivityIntent())
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(true)
            .setWhen(stats?.connectTime ?: System.currentTimeMillis())
            .setUsesChronometer(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(context.getColor(R.color.vpn_connected))
            .addAction(
                R.drawable.ic_disconnect,
                context.getString(R.string.action_disconnect),
                getActionIntent(ACTION_DISCONNECT, 1)
            )
            .addAction(
                R.drawable.ic_sort,
                context.getString(R.string.action_switch_server),
                getActionIntent(ACTION_SWITCH_SERVER, 2)
            )
        
        // Add expanded view with traffic stats
        stats?.let {
            val bigTextStyle = NotificationCompat.BigTextStyle()
                .setBigContentTitle(context.getString(R.string.notification_connected))
                .bigText(buildString {
                    append("$countryEmoji $serverName\n")
                    append("⏱️ ${it.getConnectionDuration()}\n")
                    append("↓ ${it.getDownloadSpeed()}  ↑ ${it.getUploadSpeed()}\n")
                    append("📊 ${formatBytes(it.downloadBytes)} / ${formatBytes(it.uploadBytes)}")
                })
            builder.setStyle(bigTextStyle)
        }
        
        return builder.build()
    }
    
    /**
     * Build disconnecting notification
     */
    fun buildDisconnectingNotification(): Notification {
        return NotificationCompat.Builder(context, CHANNEL_VPN_SERVICE)
            .setSmallIcon(R.drawable.ic_vpn_connecting)
            .setContentTitle(context.getString(R.string.notification_disconnecting))
            .setContentText(context.getString(R.string.notification_disconnecting_desc))
            .setContentIntent(getMainActivityIntent())
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(0, 0, true)
            .build()
    }
    
    /**
     * Build disconnected notification
     */
    fun buildDisconnectedNotification(reason: String? = null): Notification {
        val contentText = reason ?: context.getString(R.string.notification_disconnected_desc)
        
        return NotificationCompat.Builder(context, CHANNEL_VPN_STATUS)
            .setSmallIcon(R.drawable.ic_vpn_disconnected)
            .setContentTitle(context.getString(R.string.notification_disconnected))
            .setContentText(contentText)
            .setContentIntent(getMainActivityIntent())
            .setOngoing(false)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                R.drawable.ic_connect,
                context.getString(R.string.action_reconnect),
                getActionIntent(ACTION_CONNECT, 1)
            )
            .build()
    }
    
    /**
     * Build paused notification
     */
    fun buildPausedNotification(): Notification {
        return NotificationCompat.Builder(context, CHANNEL_VPN_SERVICE)
            .setSmallIcon(R.drawable.ic_vpn_disconnected)
            .setContentTitle(context.getString(R.string.notification_paused))
            .setContentText(context.getString(R.string.notification_paused_desc))
            .setContentIntent(getMainActivityIntent())
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                R.drawable.ic_connect,
                context.getString(R.string.action_resume),
                getActionIntent(ACTION_RESUME, 1)
            )
            .addAction(
                R.drawable.ic_disconnect,
                context.getString(R.string.action_disconnect),
                getActionIntent(ACTION_DISCONNECT, 2)
            )
            .build()
    }
    
    /**
     * Build error notification
     */
    fun buildErrorNotification(errorMessage: String?): Notification {
        val message = errorMessage ?: context.getString(R.string.notification_error_desc)
        
        return NotificationCompat.Builder(context, CHANNEL_VPN_ALERTS)
            .setSmallIcon(R.drawable.ic_vpn_error)
            .setContentTitle(context.getString(R.string.notification_error))
            .setContentText(message)
            .setContentIntent(getMainActivityIntent())
            .setOngoing(false)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(context.getColor(R.color.vpn_error))
            .addAction(
                R.drawable.ic_connect,
                context.getString(R.string.action_retry),
                getActionIntent(ACTION_CONNECT, 1)
            )
            .build()
    }
    
    // ==================== UPDATE NOTIFICATION ====================
    
    /**
     * Update VPN notification with current state
     */
    fun updateVpnNotification(
        state: ConnectionState,
        server: Server?,
        stats: ConnectionStats? = null
    ) {
        val notification = createVpnNotification(state, server, stats)
        notificationManager.notify(NOTIFICATION_VPN_SERVICE, notification)
    }
    
    /**
     * Update connected notification with new traffic stats
     */
    fun updateConnectedNotification(server: Server?, stats: ConnectionStats) {
        val notification = buildConnectedNotification(server, stats)
        notificationManager.notify(NOTIFICATION_VPN_SERVICE, notification)
    }
    
    // ==================== ALERT NOTIFICATIONS ====================
    
    /**
     * Show connection failed alert
     */
    fun showConnectionFailedAlert(server: Server?, error: String) {
        val serverName = server?.name ?: context.getString(R.string.unknown_server)
        val countryEmoji = server?.let { CountryUtils.getFlagEmoji(it.countryCode) } ?: "🌐"
        
        val notification = NotificationCompat.Builder(context, CHANNEL_VPN_ALERTS)
            .setSmallIcon(R.drawable.ic_vpn_error)
            .setContentTitle(context.getString(R.string.notification_connection_failed))
            .setContentText("$countryEmoji $serverName")
            .setStyle(NotificationCompat.BigTextStyle()
                .setBigContentTitle(context.getString(R.string.notification_connection_failed))
                .bigText("$countryEmoji $serverName\n\n$error"))
            .setContentIntent(getMainActivityIntent())
            .setAutoCancel(true)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(context.getColor(R.color.vpn_error))
            .addAction(
                R.drawable.ic_connect,
                context.getString(R.string.action_retry),
                getActionIntent(ACTION_CONNECT, 1)
            )
            .addAction(
                R.drawable.ic_sort,
                context.getString(R.string.action_switch_server),
                getActionIntent(ACTION_SWITCH_SERVER, 2)
            )
            .build()
        
        notificationManager.notify(NOTIFICATION_VPN_ERROR, notification)
    }
    
    /**
     * Show connection dropped alert
     */
    fun showConnectionDroppedAlert(willReconnect: Boolean) {
        val contentText = if (willReconnect) {
            context.getString(R.string.notification_reconnecting)
        } else {
            context.getString(R.string.notification_connection_lost)
        }
        
        val builder = NotificationCompat.Builder(context, CHANNEL_VPN_ALERTS)
            .setSmallIcon(R.drawable.ic_vpn_error)
            .setContentTitle(context.getString(R.string.notification_connection_dropped))
            .setContentText(contentText)
            .setContentIntent(getMainActivityIntent())
            .setAutoCancel(true)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(context.getColor(R.color.vpn_warning))
        
        if (!willReconnect) {
            builder.addAction(
                R.drawable.ic_connect,
                context.getString(R.string.action_reconnect),
                getActionIntent(ACTION_CONNECT, 1)
            )
        }
        
        notificationManager.notify(NOTIFICATION_VPN_ERROR, builder.build())
    }
    
    /**
     * Show server unreachable alert
     */
    fun showServerUnreachableAlert(server: Server) {
        val countryEmoji = CountryUtils.getFlagEmoji(server.countryCode)
        
        val notification = NotificationCompat.Builder(context, CHANNEL_VPN_ALERTS)
            .setSmallIcon(R.drawable.ic_vpn_error)
            .setContentTitle(context.getString(R.string.notification_server_unreachable))
            .setContentText("$countryEmoji ${server.name}")
            .setContentIntent(getMainActivityIntent())
            .setAutoCancel(true)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                R.drawable.ic_sort,
                context.getString(R.string.action_switch_server),
                getActionIntent(ACTION_SWITCH_SERVER, 1)
            )
            .build()
        
        notificationManager.notify(NOTIFICATION_VPN_ERROR, notification)
    }
    
    // ==================== STATUS NOTIFICATIONS ====================
    
    /**
     * Show connection established notification
     */
    fun showConnectedNotification(server: Server) {
        val countryEmoji = CountryUtils.getFlagEmoji(server.countryCode)
        
        val notification = NotificationCompat.Builder(context, CHANNEL_VPN_STATUS)
            .setSmallIcon(R.drawable.ic_vpn_connected)
            .setContentTitle(context.getString(R.string.notification_connected))
            .setContentText("$countryEmoji ${server.name}")
            .setContentIntent(getMainActivityIntent())
            .setAutoCancel(true)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(context.getColor(R.color.vpn_connected))
            .build()
        
        notificationManager.notify(NOTIFICATION_VPN_CONNECTED, notification)
    }
    
    /**
     * Show connection error notification
     */
    fun showErrorNotification(message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_VPN_ALERTS)
            .setSmallIcon(R.drawable.ic_vpn_error)
            .setContentTitle(context.getString(R.string.notification_error))
            .setContentText(message)
            .setContentIntent(getMainActivityIntent())
            .setAutoCancel(true)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(context.getColor(R.color.vpn_error))
            .addAction(
                R.drawable.ic_connect,
                context.getString(R.string.action_retry),
                getActionIntent(ACTION_CONNECT, 1)
            )
            .build()
        
        notificationManager.notify(NOTIFICATION_VPN_ERROR, notification)
    }
    
    // ==================== SUBSCRIPTION NOTIFICATIONS ====================
    
    /**
     * Show subscription updated notification
     */
    fun showSubscriptionUpdatedNotification(
        subscriptionName: String,
        serverCount: Int,
        newServers: Int
    ) {
        val contentText = if (newServers > 0) {
            context.getString(R.string.notification_subscription_new_servers, serverCount, newServers)
        } else {
            context.getString(R.string.notification_subscription_servers, serverCount)
        }
        
        val notification = NotificationCompat.Builder(context, CHANNEL_VPN_UPDATES)
            .setSmallIcon(R.drawable.ic_vpn_connected)
            .setContentTitle(context.getString(R.string.notification_subscription_updated, subscriptionName))
            .setContentText(contentText)
            .setContentIntent(getMainActivityIntent())
            .setAutoCancel(true)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        notificationManager.notify(NOTIFICATION_SUBSCRIPTION_UPDATE, notification)
    }
    
    /**
     * Show subscription update failed notification
     */
    fun showSubscriptionUpdateFailedNotification(subscriptionName: String, error: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_VPN_UPDATES)
            .setSmallIcon(R.drawable.ic_vpn_error)
            .setContentTitle(context.getString(R.string.notification_subscription_failed, subscriptionName))
            .setContentText(error)
            .setContentIntent(getMainActivityIntent())
            .setAutoCancel(true)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        notificationManager.notify(NOTIFICATION_SUBSCRIPTION_UPDATE, notification)
    }
    
    // ==================== TRAFFIC NOTIFICATIONS ====================
    
    /**
     * Show traffic warning notification
     */
    fun showTrafficWarningNotification(
        totalBytes: Long,
        thresholdBytes: Long
    ) {
        val usedFormatted = formatBytes(totalBytes)
        val thresholdFormatted = formatBytes(thresholdBytes)
        
        val notification = NotificationCompat.Builder(context, CHANNEL_VPN_TRAFFIC)
            .setSmallIcon(R.drawable.ic_vpn_connected)
            .setContentTitle(context.getString(R.string.notification_traffic_warning))
            .setContentText(context.getString(R.string.notification_traffic_warning_desc, usedFormatted, thresholdFormatted))
            .setContentIntent(getMainActivityIntent())
            .setAutoCancel(true)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        notificationManager.notify(NOTIFICATION_TRAFFIC_WARNING, notification)
    }
    
    // ==================== LATENCY TEST NOTIFICATIONS ====================
    
    /**
     * Show latency test progress notification
     */
    fun showLatencyTestProgressNotification(current: Int, total: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_VPN_UPDATES)
            .setSmallIcon(R.drawable.ic_vpn_connecting)
            .setContentTitle(context.getString(R.string.notification_testing_latency))
            .setContentText(context.getString(R.string.notification_testing_progress, current, total))
            .setProgress(total, current, false)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        notificationManager.notify(NOTIFICATION_LATENCY_TEST, notification)
    }
    
    /**
     * Show latency test complete notification
     */
    fun showLatencyTestCompleteNotification(testedCount: Int, bestServer: Server?) {
        val contentText = if (bestServer != null) {
            val emoji = CountryUtils.getFlagEmoji(bestServer.countryCode)
            context.getString(
                R.string.notification_latency_complete_best,
                testedCount,
                "$emoji ${bestServer.name}",
                bestServer.latency
            )
        } else {
            context.getString(R.string.notification_latency_complete, testedCount)
        }
        
        val notification = NotificationCompat.Builder(context, CHANNEL_VPN_UPDATES)
            .setSmallIcon(R.drawable.ic_vpn_connected)
            .setContentTitle(context.getString(R.string.notification_latency_done))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(getMainActivityIntent())
            .setAutoCancel(true)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        notificationManager.notify(NOTIFICATION_LATENCY_TEST, notification)
    }
    
    // ==================== UTILITY METHODS ====================
    
    /**
     * Cancel specific notification
     */
    fun cancel(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }
    
    /**
     * Cancel all notifications
     */
    fun cancelAll() {
        notificationManager.cancelAll()
    }
    
    /**
     * Check if notifications are enabled
     */
    fun areNotificationsEnabled(): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    
    /**
     * Check if specific channel is enabled
     */
    fun isChannelEnabled(channelId: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel(channelId)
            return channel?.importance != NotificationManager.IMPORTANCE_NONE
        }
        return true
    }
    
    /**
     * Get notification channel settings intent
     */
    fun getChannelSettingsIntent(channelId: String): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, channelId)
            }
        } else {
            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra("app_package", context.packageName)
                putExtra("app_uid", context.applicationInfo.uid)
            }
        }
    }
    
    /**
     * Delete notification channel
     */
    fun deleteChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.deleteNotificationChannel(channelId)
        }
    }
    
    // ==================== FORMATTING HELPERS ====================
    
    /**
     * Format bytes to human readable string
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
    
    /**
     * Format speed to human readable string
     */
    fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
            bytesPerSecond < 1024 * 1024 -> String.format("%.1f KB/s", bytesPerSecond / 1024.0)
            else -> String.format("%.2f MB/s", bytesPerSecond / (1024.0 * 1024.0))
        }
    }
    
    /**
     * Format duration to HH:MM:SS
     */
    fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }
    
    /**
     * Format duration from milliseconds
     */
    fun formatDurationMs(milliseconds: Long): String {
        return formatDuration(milliseconds / 1000)
    }
}
