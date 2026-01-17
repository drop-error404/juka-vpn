package com.julogic.jukavpn.data

import android.content.Context
import android.content.SharedPreferences
import com.julogic.jukavpn.models.Server
import com.julogic.jukavpn.models.VpnProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ServerRepository(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "juka_vpn_prefs"
        private const val KEY_SERVERS = "servers"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_SELECTED_SERVER = "selected_server"
        private const val KEY_ACTIVE_PROFILE = "active_profile"
    }
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    // ==================== SERVER METHODS ====================
    
    fun getAllServers(): List<Server> {
        val json = prefs.getString(KEY_SERVERS, "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                parseServerFromJson(array.getJSONObject(i))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun saveServer(server: Server) {
        val servers = getAllServers().toMutableList()
        val existingIndex = servers.indexOfFirst { it.id == server.id }
        
        if (existingIndex >= 0) {
            servers[existingIndex] = server
        } else {
            servers.add(server)
        }
        
        saveAllServers(servers)
    }
    
    fun deleteServer(serverId: String) {
        val servers = getAllServers().filter { it.id != serverId }
        saveAllServers(servers)
    }
    
    fun getServerById(id: String): Server? {
        return getAllServers().find { it.id == id }
    }
    
    fun getFavoriteServers(): List<Server> {
        return getAllServers().filter { it.isFavorite }
    }
    
    fun toggleFavorite(serverId: String) {
        getServerById(serverId)?.let { server ->
            saveServer(server.copy(isFavorite = !server.isFavorite))
        }
    }
    
    fun updateServerLatency(serverId: String, latency: Long) {
        getServerById(serverId)?.let { server ->
            saveServer(server.copy(latency = latency))
        }
    }
    
    private fun saveAllServers(servers: List<Server>) {
        val array = JSONArray()
        servers.forEach { server ->
            array.put(serverToJson(server))
        }
        prefs.edit().putString(KEY_SERVERS, array.toString()).apply()
    }
    
    // ==================== SELECTED SERVER ====================
    
    fun getSelectedServerId(): String? {
        return prefs.getString(KEY_SELECTED_SERVER, null)
    }
    
    fun setSelectedServer(serverId: String) {
        prefs.edit().putString(KEY_SELECTED_SERVER, serverId).apply()
    }
    
    fun getSelectedServer(): Server? {
        return getSelectedServerId()?.let { getServerById(it) }
    }
    
    // ==================== PROFILE METHODS ====================
    
    fun getAllProfiles(): List<VpnProfile> {
        val json = prefs.getString(KEY_PROFILES, "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                parseProfileFromJson(array.getJSONObject(i))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun saveProfile(profile: VpnProfile) {
        val profiles = getAllProfiles().toMutableList()
        val existingIndex = profiles.indexOfFirst { it.id == profile.id }
        
        if (existingIndex >= 0) {
            profiles[existingIndex] = profile
        } else {
            profiles.add(profile)
        }
        
        saveAllProfiles(profiles)
    }
    
    fun deleteProfile(profileId: String) {
        val profiles = getAllProfiles().filter { it.id != profileId }
        saveAllProfiles(profiles)
    }
    
    fun getActiveProfile(): VpnProfile? {
        val activeId = prefs.getString(KEY_ACTIVE_PROFILE, null)
        return activeId?.let { id ->
            getAllProfiles().find { it.id == id }
        }
    }
    
    fun setActiveProfile(profileId: String) {
        prefs.edit().putString(KEY_ACTIVE_PROFILE, profileId).apply()
    }
    
    private fun saveAllProfiles(profiles: List<VpnProfile>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(profileToJson(profile))
        }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }
    
    // ==================== JSON SERIALIZATION ====================
    
    private fun serverToJson(server: Server): JSONObject {
        return JSONObject().apply {
            put("id", server.id)
            put("name", server.name)
            put("address", server.address)
            put("port", server.port)
            put("protocol", server.protocol.name)
            put("countryCode", server.countryCode)
            put("countryName", server.countryName)
            put("uuid", server.uuid)
            put("alterId", server.alterId)
            put("security", server.security)
            put("network", server.network)
            put("headerType", server.headerType)
            put("host", server.host)
            put("path", server.path)
            put("tls", server.tls)
            put("sni", server.sni)
            put("fingerprint", server.fingerprint)
            put("alpn", server.alpn)
            put("flow", server.flow)
            put("method", server.method)
            put("password", server.password)
            put("sshUser", server.sshUser)
            put("sshPassword", server.sshPassword)
            put("sshPrivateKey", server.sshPrivateKey)
            put("sshPort", server.sshPort)
            put("udpPort", server.udpPort)
            put("obfs", server.obfs)
            put("obfsParam", server.obfsParam)
            put("latency", server.latency)
            put("isFavorite", server.isFavorite)
            put("isPremium", server.isPremium)
            put("createdAt", server.createdAt)
            put("lastUsedAt", server.lastUsedAt)
        }
    }
    
    private fun parseServerFromJson(json: JSONObject): Server? {
        return try {
            Server(
                id = json.getString("id"),
                name = json.getString("name"),
                address = json.getString("address"),
                port = json.getInt("port"),
                protocol = enumValueOf(json.getString("protocol")),
                countryCode = json.optString("countryCode", "UN"),
                countryName = json.optString("countryName", ""),
                uuid = json.optString("uuid").takeIf { it.isNotEmpty() },
                alterId = json.optInt("alterId").takeIf { it > 0 },
                security = json.optString("security").takeIf { it.isNotEmpty() },
                network = json.optString("network").takeIf { it.isNotEmpty() },
                headerType = json.optString("headerType").takeIf { it.isNotEmpty() },
                host = json.optString("host").takeIf { it.isNotEmpty() },
                path = json.optString("path").takeIf { it.isNotEmpty() },
                tls = json.optBoolean("tls", false),
                sni = json.optString("sni").takeIf { it.isNotEmpty() },
                fingerprint = json.optString("fingerprint").takeIf { it.isNotEmpty() },
                alpn = json.optString("alpn").takeIf { it.isNotEmpty() },
                flow = json.optString("flow").takeIf { it.isNotEmpty() },
                method = json.optString("method").takeIf { it.isNotEmpty() },
                password = json.optString("password").takeIf { it.isNotEmpty() },
                sshUser = json.optString("sshUser").takeIf { it.isNotEmpty() },
                sshPassword = json.optString("sshPassword").takeIf { it.isNotEmpty() },
                sshPrivateKey = json.optString("sshPrivateKey").takeIf { it.isNotEmpty() },
                sshPort = json.optInt("sshPort", 22),
                udpPort = json.optInt("udpPort").takeIf { it > 0 },
                obfs = json.optString("obfs").takeIf { it.isNotEmpty() },
                obfsParam = json.optString("obfsParam").takeIf { it.isNotEmpty() },
                latency = json.optLong("latency", -1),
                isFavorite = json.optBoolean("isFavorite", false),
                isPremium = json.optBoolean("isPremium", false),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                lastUsedAt = json.optLong("lastUsedAt").takeIf { it > 0 }
            )
        } catch (e: Exception) {
            null
        }
    }
    
    private fun profileToJson(profile: VpnProfile): JSONObject {
        return JSONObject().apply {
            put("id", profile.id)
            put("name", profile.name)
            put("dnsServers", JSONArray(profile.dnsServers))
            put("splitTunneling", profile.splitTunneling)
            put("allowedApps", JSONArray(profile.allowedApps))
            put("disallowedApps", JSONArray(profile.disallowedApps))
            put("bypassLan", profile.bypassLan)
            put("ipv6Enabled", profile.ipv6Enabled)
            put("mtu", profile.mtu)
            put("createdAt", profile.createdAt)
            put("isActive", profile.isActive)
        }
    }
    
    private fun parseProfileFromJson(json: JSONObject): VpnProfile? {
        return try {
            VpnProfile(
                id = json.getString("id"),
                name = json.getString("name"),
                dnsServers = json.optJSONArray("dnsServers")?.let { array ->
                    (0 until array.length()).map { array.getString(it) }
                } ?: listOf("8.8.8.8", "8.8.4.4"),
                splitTunneling = json.optBoolean("splitTunneling", false),
                allowedApps = json.optJSONArray("allowedApps")?.let { array ->
                    (0 until array.length()).map { array.getString(it) }
                } ?: emptyList(),
                disallowedApps = json.optJSONArray("disallowedApps")?.let { array ->
                    (0 until array.length()).map { array.getString(it) }
                } ?: emptyList(),
                bypassLan = json.optBoolean("bypassLan", true),
                ipv6Enabled = json.optBoolean("ipv6Enabled", false),
                mtu = json.optInt("mtu", 1500),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                isActive = json.optBoolean("isActive", false)
            )
        } catch (e: Exception) {
            null
        }
    }
}
