package com.julogic.jukavpn.models

import java.io.Serializable

data class VpnProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val servers: List<Server> = emptyList(),
    val dnsServers: List<String> = listOf("8.8.8.8", "8.8.4.4"),
    val splitTunneling: Boolean = false,
    val allowedApps: List<String> = emptyList(),  // Package names
    val disallowedApps: List<String> = emptyList(),
    val bypassLan: Boolean = true,
    val ipv6Enabled: Boolean = false,
    val mtu: Int = 1500,
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = false
) : Serializable

data class ConnectionStats(
    val bytesIn: Long = 0,
    val bytesOut: Long = 0,
    val packetsIn: Long = 0,
    val packetsOut: Long = 0,
    val connectedAt: Long = 0,
    val serverLatency: Long = -1
) {
    fun getUploadSpeed(): String = formatBytes(bytesOut)
    fun getDownloadSpeed(): String = formatBytes(bytesIn)
    fun getConnectionDuration(): String {
        if (connectedAt == 0L) return "00:00:00"
        val duration = System.currentTimeMillis() - connectedAt
        val hours = duration / 3600000
        val minutes = (duration % 3600000) / 60000
        val seconds = (duration % 60000) / 1000
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
    
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
