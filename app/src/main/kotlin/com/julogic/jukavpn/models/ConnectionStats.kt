package com.julogic.jukavpn.models

/**
 * Data class to hold VPN connection statistics
 */
data class ConnectionStats(
    val connectedAt: Long = 0,
    val totalUplink: Long = 0,
    val totalDownlink: Long = 0,
    val uploadSpeed: Long = 0,
    val downloadSpeed: Long = 0,
    val currentLatency: Long = 0
) {
    /**
     * Get connection duration in seconds
     */
    fun getDurationSeconds(): Long {
        return if (connectedAt > 0) {
            (System.currentTimeMillis() - connectedAt) / 1000
        } else 0
    }
    
    /**
     * Get formatted duration string (HH:MM:SS)
     */
    fun getFormattedDuration(): String {
        val seconds = getDurationSeconds()
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }
    
    /**
     * Get formatted total upload
     */
    fun getFormattedUplink(): String = formatBytes(totalUplink)
    
    /**
     * Get formatted total download
     */
    fun getFormattedDownlink(): String = formatBytes(totalDownlink)
    
    /**
     * Get formatted upload speed
     */
    fun getFormattedUploadSpeed(): String = "${formatBytes(uploadSpeed)}/s"
    
    /**
     * Get formatted download speed
     */
    fun getFormattedDownloadSpeed(): String = "${formatBytes(downloadSpeed)}/s"
    
    /**
     * Get formatted latency
     */
    fun getFormattedLatency(): String {
        return if (currentLatency > 0) "${currentLatency}ms" else "—"
    }
    
    companion object {
        fun formatBytes(bytes: Long): String {
            return when {
                bytes >= 1_073_741_824 -> String.format("%.2f GB", bytes / 1_073_741_824.0)
                bytes >= 1_048_576 -> String.format("%.2f MB", bytes / 1_048_576.0)
                bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
                else -> "$bytes B"
            }
        }
        
        fun formatSpeed(bytesPerSecond: Long): String {
            return when {
                bytesPerSecond >= 1_073_741_824 -> String.format("%.2f GB/s", bytesPerSecond / 1_073_741_824.0)
                bytesPerSecond >= 1_048_576 -> String.format("%.2f MB/s", bytesPerSecond / 1_048_576.0)
                bytesPerSecond >= 1024 -> String.format("%.2f KB/s", bytesPerSecond / 1024.0)
                else -> "$bytesPerSecond B/s"
            }
        }
    }
}
