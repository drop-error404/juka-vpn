package com.julogic.jukavpn.service

import android.net.TrafficStats
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors network traffic statistics
 */
class TrafficStatsMonitor {
    
    companion object {
        private const val TAG = "TrafficStatsMonitor"
        private const val UPDATE_INTERVAL_MS = 1000L
    }
    
    data class TrafficData(
        val totalRx: Long = 0,
        val totalTx: Long = 0,
        val rxSpeed: Long = 0,  // bytes per second
        val txSpeed: Long = 0,  // bytes per second
        val sessionRx: Long = 0,
        val sessionTx: Long = 0
    ) {
        fun getRxSpeedFormatted(): String = formatSpeed(rxSpeed)
        fun getTxSpeedFormatted(): String = formatSpeed(txSpeed)
        fun getSessionRxFormatted(): String = formatBytes(sessionRx)
        fun getSessionTxFormatted(): String = formatBytes(sessionTx)
        
        private fun formatSpeed(bytesPerSecond: Long): String {
            return when {
                bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
                bytesPerSecond < 1024 * 1024 -> "${bytesPerSecond / 1024} KB/s"
                else -> String.format("%.2f MB/s", bytesPerSecond / (1024.0 * 1024.0))
            }
        }
        
        private fun formatBytes(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
                else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            }
        }
    }
    
    private val _trafficData = MutableStateFlow(TrafficData())
    val trafficData: StateFlow<TrafficData> = _trafficData.asStateFlow()
    
    private var monitorJob: Job? = null
    private var scope: CoroutineScope? = null
    
    private var initialRx: Long = 0
    private var initialTx: Long = 0
    private var lastRx: Long = 0
    private var lastTx: Long = 0
    private var lastTime: Long = 0
    
    /**
     * Start monitoring traffic for a specific UID (app)
     */
    fun startMonitoring(uid: Int = android.os.Process.myUid()) {
        stopMonitoring()
        
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        
        // Record initial values
        initialRx = TrafficStats.getUidRxBytes(uid)
        initialTx = TrafficStats.getUidTxBytes(uid)
        lastRx = initialRx
        lastTx = initialTx
        lastTime = System.currentTimeMillis()
        
        monitorJob = scope?.launch {
            while (isActive) {
                try {
                    val currentRx = TrafficStats.getUidRxBytes(uid)
                    val currentTx = TrafficStats.getUidTxBytes(uid)
                    val currentTime = System.currentTimeMillis()
                    
                    val timeDelta = (currentTime - lastTime) / 1000.0
                    
                    val rxSpeed = if (timeDelta > 0) {
                        ((currentRx - lastRx) / timeDelta).toLong()
                    } else 0L
                    
                    val txSpeed = if (timeDelta > 0) {
                        ((currentTx - lastTx) / timeDelta).toLong()
                    } else 0L
                    
                    _trafficData.value = TrafficData(
                        totalRx = currentRx,
                        totalTx = currentTx,
                        rxSpeed = rxSpeed,
                        txSpeed = txSpeed,
                        sessionRx = currentRx - initialRx,
                        sessionTx = currentTx - initialTx
                    )
                    
                    lastRx = currentRx
                    lastTx = currentTx
                    lastTime = currentTime
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading traffic stats", e)
                }
                
                delay(UPDATE_INTERVAL_MS)
            }
        }
    }
    
    /**
     * Start monitoring all mobile traffic
     */
    fun startMonitoringMobile() {
        stopMonitoring()
        
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        
        initialRx = TrafficStats.getMobileRxBytes()
        initialTx = TrafficStats.getMobileTxBytes()
        lastRx = initialRx
        lastTx = initialTx
        lastTime = System.currentTimeMillis()
        
        monitorJob = scope?.launch {
            while (isActive) {
                try {
                    val currentRx = TrafficStats.getMobileRxBytes()
                    val currentTx = TrafficStats.getMobileTxBytes()
                    val currentTime = System.currentTimeMillis()
                    
                    val timeDelta = (currentTime - lastTime) / 1000.0
                    
                    val rxSpeed = if (timeDelta > 0) {
                        ((currentRx - lastRx) / timeDelta).toLong()
                    } else 0L
                    
                    val txSpeed = if (timeDelta > 0) {
                        ((currentTx - lastTx) / timeDelta).toLong()
                    } else 0L
                    
                    _trafficData.value = TrafficData(
                        totalRx = currentRx,
                        totalTx = currentTx,
                        rxSpeed = rxSpeed,
                        txSpeed = txSpeed,
                        sessionRx = currentRx - initialRx,
                        sessionTx = currentTx - initialTx
                    )
                    
                    lastRx = currentRx
                    lastTx = currentTx
                    lastTime = currentTime
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading mobile traffic stats", e)
                }
                
                delay(UPDATE_INTERVAL_MS)
            }
        }
    }
    
    /**
     * Start monitoring total device traffic
     */
    fun startMonitoringTotal() {
        stopMonitoring()
        
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        
        initialRx = TrafficStats.getTotalRxBytes()
        initialTx = TrafficStats.getTotalTxBytes()
        lastRx = initialRx
        lastTx = initialTx
        lastTime = System.currentTimeMillis()
        
        monitorJob = scope?.launch {
            while (isActive) {
                try {
                    val currentRx = TrafficStats.getTotalRxBytes()
                    val currentTx = TrafficStats.getTotalTxBytes()
                    val currentTime = System.currentTimeMillis()
                    
                    val timeDelta = (currentTime - lastTime) / 1000.0
                    
                    val rxSpeed = if (timeDelta > 0) {
                        ((currentRx - lastRx) / timeDelta).toLong()
                    } else 0L
                    
                    val txSpeed = if (timeDelta > 0) {
                        ((currentTx - lastTx) / timeDelta).toLong()
                    } else 0L
                    
                    _trafficData.value = TrafficData(
                        totalRx = currentRx,
                        totalTx = currentTx,
                        rxSpeed = rxSpeed,
                        txSpeed = txSpeed,
                        sessionRx = currentRx - initialRx,
                        sessionTx = currentTx - initialTx
                    )
                    
                    lastRx = currentRx
                    lastTx = currentTx
                    lastTime = currentTime
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading total traffic stats", e)
                }
                
                delay(UPDATE_INTERVAL_MS)
            }
        }
    }
    
    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        scope?.cancel()
        scope = null
    }
    
    /**
     * Reset session stats
     */
    fun resetSession() {
        initialRx = lastRx
        initialTx = lastTx
        _trafficData.value = _trafficData.value.copy(
            sessionRx = 0,
            sessionTx = 0
        )
    }
    
    /**
     * Get current session traffic
     */
    fun getSessionTraffic(): Pair<Long, Long> {
        val data = _trafficData.value
        return data.sessionRx to data.sessionTx
    }
}
