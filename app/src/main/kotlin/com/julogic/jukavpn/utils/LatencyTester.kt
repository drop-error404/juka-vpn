package com.julogic.jukavpn.utils

import android.util.Log
import com.julogic.jukavpn.data.ServerRepository
import com.julogic.jukavpn.models.Server
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Tests server latency using TCP connection time
 */
class LatencyTester(private val repository: ServerRepository) {
    
    companion object {
        private const val TAG = "LatencyTester"
        private const val TIMEOUT_MS = 5000
        private const val TEST_ATTEMPTS = 3
    }
    
    interface TestListener {
        fun onTestStarted(server: Server)
        fun onTestCompleted(server: Server, latency: Long)
        fun onTestFailed(server: Server, error: String)
        fun onAllTestsCompleted(results: Map<String, Long>)
    }
    
    private var listener: TestListener? = null
    
    fun setTestListener(listener: TestListener) {
        this.listener = listener
    }
    
    /**
     * Test latency for a single server
     */
    suspend fun testServer(server: Server): Long {
        return withContext(Dispatchers.IO) {
            listener?.onTestStarted(server)
            
            try {
                val latencies = mutableListOf<Long>()
                
                repeat(TEST_ATTEMPTS) {
                    val latency = measureTcpLatency(server.address, server.port)
                    if (latency >= 0) {
                        latencies.add(latency)
                    }
                    delay(100) // Small delay between attempts
                }
                
                val averageLatency = if (latencies.isNotEmpty()) {
                    latencies.sorted().let { sorted ->
                        // Use median for more accurate result
                        sorted[sorted.size / 2]
                    }
                } else {
                    -1L
                }
                
                if (averageLatency >= 0) {
                    repository.updateServerLatency(server.id, averageLatency)
                    listener?.onTestCompleted(server, averageLatency)
                } else {
                    listener?.onTestFailed(server, "Timeout")
                }
                
                averageLatency
            } catch (e: Exception) {
                Log.e(TAG, "Error testing server ${server.name}", e)
                listener?.onTestFailed(server, e.message ?: "Unknown error")
                -1L
            }
        }
    }
    
    /**
     * Test latency for all servers
     */
    suspend fun testAllServers(concurrency: Int = 5): Map<String, Long> {
        return withContext(Dispatchers.IO) {
            val servers = repository.getAllServers()
            val results = mutableMapOf<String, Long>()
            
            servers.chunked(concurrency).forEach { chunk ->
                val deferreds = chunk.map { server ->
                    async {
                        val latency = testServer(server)
                        server.id to latency
                    }
                }
                
                deferreds.awaitAll().forEach { (id, latency) ->
                    results[id] = latency
                }
            }
            
            listener?.onAllTestsCompleted(results)
            results
        }
    }
    
    /**
     * Test latency for specific servers
     */
    suspend fun testServers(serverIds: List<String>, concurrency: Int = 5): Map<String, Long> {
        return withContext(Dispatchers.IO) {
            val servers = serverIds.mapNotNull { repository.getServerById(it) }
            val results = mutableMapOf<String, Long>()
            
            servers.chunked(concurrency).forEach { chunk ->
                val deferreds = chunk.map { server ->
                    async {
                        val latency = testServer(server)
                        server.id to latency
                    }
                }
                
                deferreds.awaitAll().forEach { (id, latency) ->
                    results[id] = latency
                }
            }
            
            listener?.onAllTestsCompleted(results)
            results
        }
    }
    
    /**
     * Measure TCP connection latency
     */
    private fun measureTcpLatency(host: String, port: Int): Long {
        val socket = Socket()
        
        return try {
            val startTime = System.currentTimeMillis()
            socket.connect(InetSocketAddress(host, port), TIMEOUT_MS)
            val endTime = System.currentTimeMillis()
            
            endTime - startTime
        } catch (e: Exception) {
            -1L
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    /**
     * Get the best server based on latency
     */
    suspend fun getBestServer(): Server? {
        return withContext(Dispatchers.IO) {
            val servers = repository.getAllServers()
            
            if (servers.isEmpty()) return@withContext null
            
            // First try servers with known latency
            val withLatency = servers.filter { it.latency > 0 }
            if (withLatency.isNotEmpty()) {
                return@withContext withLatency.minByOrNull { it.latency }
            }
            
            // If no latency data, test all servers
            testAllServers()
            
            repository.getAllServers()
                .filter { it.latency > 0 }
                .minByOrNull { it.latency }
        }
    }
    
    /**
     * Format latency for display
     */
    fun formatLatency(latency: Long): String {
        return when {
            latency < 0 -> "—"
            latency < 100 -> "$latency ms ●"  // Green dot
            latency < 300 -> "$latency ms ●"  // Yellow dot
            else -> "$latency ms ●"           // Red dot
        }
    }
    
    /**
     * Get latency quality indicator
     */
    fun getLatencyQuality(latency: Long): LatencyQuality {
        return when {
            latency < 0 -> LatencyQuality.UNKNOWN
            latency < 100 -> LatencyQuality.EXCELLENT
            latency < 200 -> LatencyQuality.GOOD
            latency < 400 -> LatencyQuality.FAIR
            else -> LatencyQuality.POOR
        }
    }
    
    enum class LatencyQuality {
        EXCELLENT, GOOD, FAIR, POOR, UNKNOWN
    }
}
