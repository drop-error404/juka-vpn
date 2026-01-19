package com.julogic.jukavpn.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.julogic.jukavpn.models.Protocol
import com.julogic.jukavpn.models.Server
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Comprehensive latency tester for VPN servers
 * Supports TCP, HTTP, HTTPS, and real connection tests
 */
class LatencyTester(private val context: Context) {
    
    companion object {
        private const val TAG = "LatencyTester"
        private const val DEFAULT_TIMEOUT_MS = 5000
        private const val HTTP_TIMEOUT_MS = 8000
        private const val TEST_ATTEMPTS = 3
        private const val MAX_CONCURRENT_TESTS = 10
        
        // Test endpoints for real connectivity check
        private val TEST_URLS = listOf(
            "https://www.google.com/generate_204",
            "https://cp.cloudflare.com/generate_204",
            "https://www.gstatic.com/generate_204"
        )
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // State
    private val _isTestingAll = MutableStateFlow(false)
    val isTestingAll: StateFlow<Boolean> = _isTestingAll.asStateFlow()
    
    private val _testProgress = MutableStateFlow(0f)
    val testProgress: StateFlow<Float> = _testProgress.asStateFlow()
    
    private val _testResults = MutableStateFlow<Map<String, LatencyResult>>(emptyMap())
    val testResults: StateFlow<Map<String, LatencyResult>> = _testResults.asStateFlow()
    
    private val currentTests = ConcurrentHashMap<String, Job>()
    private val completedCount = AtomicInteger(0)
    private var totalCount = 0
    
    // Listener
    interface TestListener {
        fun onTestStarted(server: Server)
        fun onTestCompleted(server: Server, result: LatencyResult)
        fun onTestFailed(server: Server, error: String)
        fun onAllTestsStarted(count: Int)
        fun onAllTestsCompleted(results: Map<String, LatencyResult>)
        fun onProgressUpdated(progress: Float)
    }
    
    private var listener: TestListener? = null
    
    fun setTestListener(listener: TestListener?) {
        this.listener = listener
    }
    
    // ==================== SINGLE SERVER TEST ====================
    
    /**
     * Test latency for a single server
     */
    suspend fun testServer(server: Server): LatencyResult {
        return withContext(Dispatchers.IO) {
            listener?.onTestStarted(server)
            
            try {
                val result = when (server.protocol) {
                    Protocol.VMESS, Protocol.VLESS, Protocol.TROJAN -> {
                        testTcpWithTls(server)
                    }
                    Protocol.SHADOWSOCKS -> {
                        testTcp(server.address, server.port)
                    }
                    Protocol.SSH -> {
                        testTcp(server.address, server.sshPort ?: 22)
                    }
                    Protocol.UDP -> {
                        testUdp(server.address, server.udpPort ?: server.port)
                    }
                }
                
                listener?.onTestCompleted(server, result)
                result
                
            } catch (e: Exception) {
                Log.e(TAG, "Error testing server ${server.name}", e)
                val result = LatencyResult(
                    latency = -1,
                    status = LatencyStatus.ERROR,
                    error = e.message
                )
                listener?.onTestFailed(server, e.message ?: "Unknown error")
                result
            }
        }
    }
    
    /**
     * Test TCP connection with TLS (for V2Ray protocols)
     */
    private suspend fun testTcpWithTls(server: Server): LatencyResult {
        val latencies = mutableListOf<Long>()
        var lastError: String? = null
        
        repeat(TEST_ATTEMPTS) { attempt ->
            try {
                val latency = if (server.tls) {
                    measureTlsLatency(
                        host = server.address,
                        port = server.port,
                        sni = server.sni ?: server.host ?: server.address
                    )
                } else {
                    measureTcpLatency(server.address, server.port)
                }
                
                if (latency >= 0) {
                    latencies.add(latency)
                }
            } catch (e: Exception) {
                lastError = e.message
                Log.d(TAG, "Test attempt ${attempt + 1} failed: ${e.message}")
            }
            
            if (attempt < TEST_ATTEMPTS - 1) {
                delay(100)
            }
        }
        
        return if (latencies.isNotEmpty()) {
            // Use median for more accurate result
            val median = latencies.sorted()[latencies.size / 2]
            LatencyResult(
                latency = median,
                status = getStatusFromLatency(median),
                attempts = latencies.size,
                min = latencies.minOrNull() ?: median,
                max = latencies.maxOrNull() ?: median,
                avg = latencies.average().toLong()
            )
        } else {
            LatencyResult(
                latency = -1,
                status = LatencyStatus.TIMEOUT,
                error = lastError ?: "Connection timeout"
            )
        }
    }
    
    /**
     * Test basic TCP connection
     */
    private suspend fun testTcp(host: String, port: Int): LatencyResult {
        val latencies = mutableListOf<Long>()
        
        repeat(TEST_ATTEMPTS) { attempt ->
            val latency = measureTcpLatency(host, port)
            if (latency >= 0) {
                latencies.add(latency)
            }
            if (attempt < TEST_ATTEMPTS - 1) {
                delay(100)
            }
        }
        
        return if (latencies.isNotEmpty()) {
            val median = latencies.sorted()[latencies.size / 2]
            LatencyResult(
                latency = median,
                status = getStatusFromLatency(median),
                attempts = latencies.size
            )
        } else {
            LatencyResult(latency = -1, status = LatencyStatus.TIMEOUT)
        }
    }
    
    /**
     * Test UDP connectivity (basic reachability)
     */
    private suspend fun testUdp(host: String, port: Int): LatencyResult {
        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                
                DatagramSocket().use { socket ->
                    socket.soTimeout = DEFAULT_TIMEOUT_MS
                    val address = InetSocketAddress(host, port)
                    
                    // Send a small packet
                    val sendData = ByteArray(1) { 0x00 }
                    val sendPacket = DatagramPacket(sendData, sendData.size, address)
                    socket.send(sendPacket)
                    
                    // For UDP we can't really measure RTT without a response
                    // So we just measure time to resolve and send
                    val latency = System.currentTimeMillis() - startTime
                    
                    LatencyResult(
                        latency = latency,
                        status = LatencyStatus.REACHABLE,
                        note = "UDP send time only"
                    )
                }
            } catch (e: Exception) {
                LatencyResult(
                    latency = -1,
                    status = LatencyStatus.UNREACHABLE,
                    error = e.message
                )
            }
        }
    }
    
    /**
     * Measure TCP connection latency
     */
    private fun measureTcpLatency(host: String, port: Int, timeout: Int = DEFAULT_TIMEOUT_MS): Long {
        val socket = Socket()
        
        return try {
            val startTime = System.nanoTime()
            socket.connect(InetSocketAddress(host, port), timeout)
            val endTime = System.nanoTime()
            
            (endTime - startTime) / 1_000_000 // Convert to milliseconds
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
     * Measure TLS connection latency
     */
    private fun measureTlsLatency(host: String, port: Int, sni: String, timeout: Int = DEFAULT_TIMEOUT_MS): Long {
        var socket: Socket? = null
        var sslSocket: SSLSocket? = null
        
        return try {
            val startTime = System.nanoTime()
            
            // Create TCP socket first
            socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeout)
            socket.soTimeout = timeout
            
            // Wrap with TLS
            val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            sslSocket = sslFactory.createSocket(socket, sni, port, true) as SSLSocket
            
            // Set SNI
            val sslParams = sslSocket.sslParameters
            sslParams.serverNames = listOf(javax.net.ssl.SNIHostName(sni))
            sslSocket.sslParameters = sslParams
            
            // Initiate handshake
            sslSocket.startHandshake()
            
            val endTime = System.nanoTime()
            (endTime - startTime) / 1_000_000
            
        } catch (e: Exception) {
            Log.d(TAG, "TLS connection failed: ${e.message}")
            -1L
        } finally {
            try {
                sslSocket?.close()
                socket?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    // ==================== BATCH TESTING ====================
    
    /**
     * Test all servers
     */
    suspend fun testAllServers(
        servers: List<Server>,
        concurrency: Int = MAX_CONCURRENT_TESTS,
        onProgress: ((Server, LatencyResult) -> Unit)? = null
    ): Map<String, LatencyResult> {
        if (servers.isEmpty()) return emptyMap()
        
        _isTestingAll.value = true
        _testProgress.value = 0f
        completedCount.set(0)
        totalCount = servers.size
        
        listener?.onAllTestsStarted(servers.size)
        
        val results = ConcurrentHashMap<String, LatencyResult>()
        
        return withContext(Dispatchers.IO) {
            try {
                // Process in chunks for concurrency control
                servers.chunked(concurrency).forEach { chunk ->
                    val jobs = chunk.map { server ->
                        async {
                            val result = testServer(server)
                            results[server.id] = result
                            
                            val completed = completedCount.incrementAndGet()
                            _testProgress.value = completed.toFloat() / totalCount
                            listener?.onProgressUpdated(_testProgress.value)
                            
                            onProgress?.invoke(server, result)
                            
                            server.id to result
                        }
                    }
                    jobs.awaitAll()
                }
                
                val finalResults = results.toMap()
                _testResults.value = finalResults
                listener?.onAllTestsCompleted(finalResults)
                
                finalResults
                
            } finally {
                _isTestingAll.value = false
                _testProgress.value = 0f
            }
        }
    }
    
    /**
     * Test specific servers by IDs
     */
    suspend fun testServers(
        servers: List<Server>,
        concurrency: Int = MAX_CONCURRENT_TESTS
    ): Map<String, LatencyResult> {
        return testAllServers(servers, concurrency)
    }
    
    /**
     * Cancel all ongoing tests
     */
    fun cancelAllTests() {
        currentTests.values.forEach { it.cancel() }
        currentTests.clear()
        _isTestingAll.value = false
        _testProgress.value = 0f
    }
    
    // ==================== CONNECTIVITY TEST ====================
    
    /**
     * Test real connectivity through VPN
     */
    suspend fun testConnectivity(): ConnectivityResult {
        return withContext(Dispatchers.IO) {
            for (url in TEST_URLS) {
                try {
                    val startTime = System.nanoTime()
                    
                    val connection = URL(url).openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = HTTP_TIMEOUT_MS
                    connection.readTimeout = HTTP_TIMEOUT_MS
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", "curl/7.74.0")
                    
                    val responseCode = connection.responseCode
                    val endTime = System.nanoTime()
                    
                    connection.disconnect()
                    
                    if (responseCode in 200..299 || responseCode == 204) {
                        return@withContext ConnectivityResult(
                            success = true,
                            latency = (endTime - startTime) / 1_000_000,
                            url = url
                        )
                    }
                    
                } catch (e: Exception) {
                    Log.d(TAG, "Connectivity test to $url failed: ${e.message}")
                }
            }
            
            ConnectivityResult(success = false, latency = -1, url = null)
        }
    }
    
    /**
     * Check network availability
     */
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    
    // ==================== UTILITIES ====================
    
    /**
     * Get latency status from value
     */
    private fun getStatusFromLatency(latency: Long): LatencyStatus {
        return when {
            latency < 0 -> LatencyStatus.TIMEOUT
            latency < 80 -> LatencyStatus.EXCELLENT
            latency < 150 -> LatencyStatus.GOOD
            latency < 300 -> LatencyStatus.FAIR
            latency < 500 -> LatencyStatus.SLOW
            else -> LatencyStatus.VERY_SLOW
        }
    }
    
    /**
     * Get best server from list based on latency
     */
    suspend fun getBestServer(servers: List<Server>): Server? {
        if (servers.isEmpty()) return null
        
        // First try servers with known good latency
        val withLatency = servers.filter { it.latency > 0 && it.latency < 1000 }
        if (withLatency.isNotEmpty()) {
            return withLatency.minByOrNull { it.latency }
        }
        
        // Test all servers
        val results = testAllServers(servers)
        
        return servers
            .filter { results[it.id]?.latency ?: -1 > 0 }
            .minByOrNull { results[it.id]?.latency ?: Long.MAX_VALUE }
    }
    
    /**
     * Format latency for display
     */
    fun formatLatency(latency: Long): String {
        return when {
            latency < 0 -> "—"
            latency < 1000 -> "${latency}ms"
            else -> ">${latency / 1000}s"
        }
    }
    
    /**
     * Get color for latency (returns color resource name)
     */
    fun getLatencyColor(latency: Long): String {
        return when {
            latency < 0 -> "gray"
            latency < 80 -> "green"
            latency < 150 -> "light_green"
            latency < 300 -> "yellow"
            latency < 500 -> "orange"
            else -> "red"
        }
    }
    
    /**
     * Get quality indicator
     */
    fun getQualityIndicator(latency: Long): String {
        return when {
            latency < 0 -> "●"  // Gray
            latency < 80 -> "●"  // Green - Excellent
            latency < 150 -> "●"  // Light green - Good
            latency < 300 -> "●"  // Yellow - Fair
            latency < 500 -> "●"  // Orange - Slow
            else -> "●"  // Red - Very slow
        }
    }
    
    fun cleanup() {
        cancelAllTests()
        scope.cancel()
    }
    
    // ==================== DATA CLASSES ====================
    
    data class LatencyResult(
        val latency: Long,
        val status: LatencyStatus,
        val attempts: Int = 0,
        val min: Long = latency,
        val max: Long = latency,
        val avg: Long = latency,
        val error: String? = null,
        val note: String? = null
    )
    
    enum class LatencyStatus {
        EXCELLENT,   // < 80ms
        GOOD,        // 80-150ms
        FAIR,        // 150-300ms
        SLOW,        // 300-500ms
        VERY_SLOW,   // > 500ms
        TIMEOUT,     // Connection timeout
        UNREACHABLE, // Host unreachable
        REACHABLE,   // UDP only - can send
        ERROR        // Other error
    }
    
    data class ConnectivityResult(
        val success: Boolean,
        val latency: Long,
        val url: String?
    )
}
