package com.julogic.jukavpn

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.julogic.jukavpn.models.Protocol
import com.julogic.jukavpn.models.Server
import com.julogic.jukavpn.parsers.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Base64

/**
 * Central manager for server list operations
 * Handles server storage, parsing, and selection
 */
class ServerManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ServerManager"
        private const val PREFS_NAME = "juka_vpn_servers"
        private const val KEY_SERVERS = "servers"
        private const val KEY_SELECTED_SERVER_ID = "selected_server_id"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_SUBSCRIPTIONS = "subscriptions"
        
        @Volatile
        private var instance: ServerManager? = null
        
        fun getInstance(context: Context): ServerManager {
            return instance ?: synchronized(this) {
                instance ?: ServerManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Server list state
    private val _servers = MutableStateFlow<List<Server>>(emptyList())
    val servers: StateFlow<List<Server>> = _servers.asStateFlow()
    
    private val _selectedServer = MutableStateFlow<Server?>(null)
    val selectedServer: StateFlow<Server?> = _selectedServer.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Subscriptions
    private val _subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions.asStateFlow()
    
    init {
        loadServers()
        loadSubscriptions()
    }
    
    // ==================== SERVER MANAGEMENT ====================
    
    /**
     * Load servers from persistent storage
     */
    private fun loadServers() {
        try {
            val json = prefs.getString(KEY_SERVERS, null)
            if (json != null) {
                val type = object : TypeToken<List<Server>>() {}.type
                val serverList: List<Server> = gson.fromJson(json, type)
                _servers.value = serverList
                
                // Load selected server
                val selectedId = prefs.getString(KEY_SELECTED_SERVER_ID, null)
                _selectedServer.value = serverList.find { it.id == selectedId }
            }
            Log.d(TAG, "Loaded ${_servers.value.size} servers")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading servers", e)
            _servers.value = emptyList()
        }
    }
    
    /**
     * Save servers to persistent storage
     */
    private fun saveServers() {
        try {
            val json = gson.toJson(_servers.value)
            prefs.edit().putString(KEY_SERVERS, json).apply()
            Log.d(TAG, "Saved ${_servers.value.size} servers")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving servers", e)
        }
    }
    
    /**
     * Add a new server
     */
    fun addServer(server: Server): Boolean {
        // Check for duplicates
        val exists = _servers.value.any { 
            it.address == server.address && it.port == server.port && it.uuid == server.uuid 
        }
        
        if (exists) {
            Log.w(TAG, "Server already exists: ${server.name}")
            return false
        }
        
        _servers.value = _servers.value + server
        saveServers()
        Log.d(TAG, "Added server: ${server.name}")
        return true
    }
    
    /**
     * Add multiple servers
     */
    fun addServers(newServers: List<Server>): Int {
        var added = 0
        val currentList = _servers.value.toMutableList()
        
        for (server in newServers) {
            val exists = currentList.any { 
                it.address == server.address && it.port == server.port && it.uuid == server.uuid 
            }
            if (!exists) {
                currentList.add(server)
                added++
            }
        }
        
        _servers.value = currentList
        saveServers()
        Log.d(TAG, "Added $added servers")
        return added
    }
    
    /**
     * Update an existing server
     */
    fun updateServer(server: Server) {
        _servers.value = _servers.value.map {
            if (it.id == server.id) server else it
        }
        saveServers()
        
        // Update selected if needed
        if (_selectedServer.value?.id == server.id) {
            _selectedServer.value = server
        }
    }
    
    /**
     * Remove a server by ID
     */
    fun removeServer(serverId: String) {
        _servers.value = _servers.value.filter { it.id != serverId }
        saveServers()
        
        if (_selectedServer.value?.id == serverId) {
            _selectedServer.value = null
            prefs.edit().remove(KEY_SELECTED_SERVER_ID).apply()
        }
    }
    
    /**
     * Clear all servers
     */
    fun clearAllServers() {
        _servers.value = emptyList()
        _selectedServer.value = null
        prefs.edit()
            .remove(KEY_SERVERS)
            .remove(KEY_SELECTED_SERVER_ID)
            .apply()
    }
    
    /**
     * Select a server for connection
     */
    fun selectServer(server: Server) {
        _selectedServer.value = server
        prefs.edit().putString(KEY_SELECTED_SERVER_ID, server.id).apply()
        
        // Update last used
        updateServer(server.copy(lastUsedAt = System.currentTimeMillis()))
    }
    
    /**
     * Get server by ID
     */
    fun getServerById(id: String): Server? {
        return _servers.value.find { it.id == id }
    }
    
    /**
     * Toggle favorite status
     */
    fun toggleFavorite(serverId: String) {
        _servers.value = _servers.value.map {
            if (it.id == serverId) it.copy(isFavorite = !it.isFavorite) else it
        }
        saveServers()
    }
    
    /**
     * Get all favorite servers
     */
    fun getFavorites(): List<Server> = _servers.value.filter { it.isFavorite }
    
    /**
     * Get servers by protocol
     */
    fun getServersByProtocol(protocol: Protocol): List<Server> = 
        _servers.value.filter { it.protocol == protocol }
    
    /**
     * Get servers by country
     */
    fun getServersByCountry(countryCode: String): List<Server> = 
        _servers.value.filter { it.countryCode.equals(countryCode, ignoreCase = true) }
    
    /**
     * Update server latency
     */
    fun updateLatency(serverId: String, latency: Long) {
        _servers.value = _servers.value.map {
            if (it.id == serverId) it.copy(latency = latency) else it
        }
        saveServers()
    }
    
    // ==================== URI PARSING ====================
    
    /**
     * Parse and add server from URI string
     */
    fun parseAndAddServer(uri: String): Server? {
        val server = parseUri(uri)
        if (server != null) {
            addServer(server)
        }
        return server
    }
    
    /**
     * Parse URI to Server without adding
     */
    fun parseUri(uri: String): Server? {
        return try {
            when {
                uri.startsWith("vmess://") -> VmessParser.parse(uri)
                uri.startsWith("vless://") -> VlessParser.parse(uri)
                uri.startsWith("ss://") -> ShadowsocksParser.parse(uri)
                uri.startsWith("trojan://") -> TrojanParser.parse(uri)
                uri.startsWith("ssh://") -> SSHConfigParser.parse(uri)
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing URI: $uri", e)
            null
        }
    }
    
    /**
     * Parse multiple URIs from text (separated by newlines)
     */
    fun parseMultipleUris(text: String): List<Server> {
        val servers = mutableListOf<Server>()
        val lines = text.split("\n", "\r\n").filter { it.isNotBlank() }
        
        for (line in lines) {
            val trimmed = line.trim()
            val server = parseUri(trimmed)
            if (server != null) {
                servers.add(server)
            }
        }
        
        return servers
    }
    
    /**
     * Parse Base64 encoded subscription content
     */
    fun parseBase64Content(base64Content: String): List<Server> {
        return try {
            val decoded = String(Base64.getDecoder().decode(base64Content.trim()))
            parseMultipleUris(decoded)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding base64 content", e)
            // Try as plain text
            parseMultipleUris(base64Content)
        }
    }
    
    // ==================== SUBSCRIPTIONS ====================
    
    data class Subscription(
        val id: String = java.util.UUID.randomUUID().toString(),
        val name: String,
        val url: String,
        val lastUpdated: Long = 0,
        val serverCount: Int = 0,
        val autoUpdate: Boolean = true
    )
    
    private fun loadSubscriptions() {
        try {
            val json = prefs.getString(KEY_SUBSCRIPTIONS, null)
            if (json != null) {
                val type = object : TypeToken<List<Subscription>>() {}.type
                _subscriptions.value = gson.fromJson(json, type)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading subscriptions", e)
        }
    }
    
    private fun saveSubscriptions() {
        try {
            val json = gson.toJson(_subscriptions.value)
            prefs.edit().putString(KEY_SUBSCRIPTIONS, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving subscriptions", e)
        }
    }
    
    /**
     * Add a subscription
     */
    fun addSubscription(name: String, url: String): Subscription {
        val subscription = Subscription(name = name, url = url)
        _subscriptions.value = _subscriptions.value + subscription
        saveSubscriptions()
        return subscription
    }
    
    /**
     * Remove a subscription
     */
    fun removeSubscription(subscriptionId: String) {
        _subscriptions.value = _subscriptions.value.filter { it.id != subscriptionId }
        saveSubscriptions()
    }
    
    /**
     * Update subscription with new server count and timestamp
     */
    fun updateSubscription(subscription: Subscription) {
        _subscriptions.value = _subscriptions.value.map {
            if (it.id == subscription.id) subscription else it
        }
        saveSubscriptions()
    }
    
    /**
     * Refresh a subscription (fetch from URL)
     */
    suspend fun refreshSubscription(subscriptionId: String): Result<Int> {
        val subscription = _subscriptions.value.find { it.id == subscriptionId }
            ?: return Result.failure(Exception("Subscription not found"))
        
        return withContext(Dispatchers.IO) {
            try {
                _isLoading.value = true
                
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                val request = okhttp3.Request.Builder()
                    .url(subscription.url)
                    .addHeader("User-Agent", "JukaVPN/1.0")
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                
                val content = response.body?.string() ?: ""
                val servers = parseBase64Content(content)
                
                if (servers.isEmpty()) {
                    return@withContext Result.failure(Exception("No valid servers found"))
                }
                
                val added = addServers(servers)
                
                updateSubscription(subscription.copy(
                    lastUpdated = System.currentTimeMillis(),
                    serverCount = servers.size
                ))
                
                Result.success(added)
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing subscription", e)
                Result.failure(e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Refresh all subscriptions
     */
    suspend fun refreshAllSubscriptions(): Map<String, Result<Int>> {
        val results = mutableMapOf<String, Result<Int>>()
        
        for (subscription in _subscriptions.value) {
            if (subscription.autoUpdate) {
                results[subscription.id] = refreshSubscription(subscription.id)
            }
        }
        
        return results
    }
    
    // ==================== IMPORT/EXPORT ====================
    
    /**
     * Export all servers as JSON
     */
    fun exportServersAsJson(): String {
        return gson.toJson(_servers.value)
    }
    
    /**
     * Export servers as URI list
     */
    fun exportServersAsUris(): String {
        return _servers.value.mapNotNull { serverToUri(it) }.joinToString("\n")
    }
    
    /**
     * Convert server back to URI format
     */
    fun serverToUri(server: Server): String? {
        return try {
            when (server.protocol) {
                Protocol.VMESS -> VmessParser.toUri(server)
                Protocol.VLESS -> VlessParser.toUri(server)
                Protocol.SHADOWSOCKS -> ShadowsocksParser.toUri(server)
                Protocol.TROJAN -> TrojanParser.toUri(server)
                Protocol.SSH -> SSHConfigParser.toUri(server)
                Protocol.UDP -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting server to URI", e)
            null
        }
    }
    
    /**
     * Import servers from JSON
     */
    fun importFromJson(json: String): Int {
        return try {
            val type = object : TypeToken<List<Server>>() {}.type
            val servers: List<Server> = gson.fromJson(json, type)
            addServers(servers)
        } catch (e: Exception) {
            Log.e(TAG, "Error importing JSON", e)
            0
        }
    }
    
    // ==================== SORTING & FILTERING ====================
    
    enum class SortBy {
        NAME, LATENCY, COUNTRY, PROTOCOL, LAST_USED, CREATED
    }
    
    /**
     * Get servers sorted by criteria
     */
    fun getServersSorted(sortBy: SortBy, ascending: Boolean = true): List<Server> {
        val sorted = when (sortBy) {
            SortBy.NAME -> _servers.value.sortedBy { it.name.lowercase() }
            SortBy.LATENCY -> _servers.value.sortedBy { if (it.latency <= 0) Long.MAX_VALUE else it.latency }
            SortBy.COUNTRY -> _servers.value.sortedBy { it.countryName.lowercase() }
            SortBy.PROTOCOL -> _servers.value.sortedBy { it.protocol.name }
            SortBy.LAST_USED -> _servers.value.sortedByDescending { it.lastUsedAt ?: 0 }
            SortBy.CREATED -> _servers.value.sortedByDescending { it.createdAt }
        }
        return if (ascending) sorted else sorted.reversed()
    }
    
    /**
     * Search servers by query
     */
    fun searchServers(query: String): List<Server> {
        if (query.isBlank()) return _servers.value
        
        val lowerQuery = query.lowercase()
        return _servers.value.filter {
            it.name.lowercase().contains(lowerQuery) ||
            it.address.lowercase().contains(lowerQuery) ||
            it.countryName.lowercase().contains(lowerQuery) ||
            it.countryCode.lowercase().contains(lowerQuery) ||
            it.protocol.name.lowercase().contains(lowerQuery)
        }
    }
    
    /**
     * Get best server by latency
     */
    fun getBestServer(): Server? {
        return _servers.value
            .filter { it.latency > 0 }
            .minByOrNull { it.latency }
            ?: _servers.value.firstOrNull()
    }
    
    /**
     * Get unique countries from server list
     */
    fun getAvailableCountries(): List<Pair<String, String>> {
        return _servers.value
            .map { Pair(it.countryCode, it.countryName) }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }
    
    /**
     * Get unique protocols from server list
     */
    fun getAvailableProtocols(): List<Protocol> {
        return _servers.value
            .map { it.protocol }
            .distinct()
            .sortedBy { it.name }
    }
    
    /**
     * Get server count by country
     */
    fun getServerCountByCountry(): Map<String, Int> {
        return _servers.value.groupBy { it.countryCode }.mapValues { it.value.size }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        scope.cancel()
    }
}
