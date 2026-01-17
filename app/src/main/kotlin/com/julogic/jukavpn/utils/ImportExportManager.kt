package com.julogic.jukavpn.utils

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.julogic.jukavpn.data.ServerRepository
import com.julogic.jukavpn.models.Protocol
import com.julogic.jukavpn.models.Server
import com.julogic.jukavpn.parsers.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class ImportExportManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ImportExportManager"
        private const val EXPORT_VERSION = 1
        private const val MAGIC_HEADER = "JUKAVPN"
    }
    
    private val repository = ServerRepository(context)
    
    // ==================== IMPORT ====================
    
    /**
     * Import servers from URI (clipboard or file content)
     * Supports: vmess://, vless://, ss://, trojan://, ssh://
     * Also supports subscription URLs and base64 encoded lists
     */
    fun importFromUri(uri: String): ImportResult {
        val trimmed = uri.trim()
        
        return when {
            // Single protocol links
            trimmed.startsWith("vmess://") -> importSingleUri(trimmed, VmessParser::parse)
            trimmed.startsWith("vless://") -> importSingleUri(trimmed, VlessParser::parse)
            trimmed.startsWith("ss://") -> importSingleUri(trimmed, ShadowsocksParser::parse)
            trimmed.startsWith("trojan://") -> importSingleUri(trimmed, TrojanParser::parse)
            trimmed.startsWith("ssh://") -> importSingleUri(trimmed, SSHConfigParser::parse)
            
            // Subscription URL
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> {
                ImportResult.SubscriptionUrl(trimmed)
            }
            
            // Base64 encoded content (subscription response)
            isBase64(trimmed) -> importFromBase64(trimmed)
            
            // Multi-line content
            trimmed.contains("\n") -> importMultipleUris(trimmed)
            
            // JSON content
            trimmed.startsWith("{") || trimmed.startsWith("[") -> importFromJson(trimmed)
            
            else -> ImportResult.Error("Formato não reconhecido")
        }
    }
    
    private fun importSingleUri(uri: String, parser: (String) -> Server?): ImportResult {
        return try {
            val server = parser(uri)
            if (server != null) {
                repository.saveServer(server)
                ImportResult.Success(listOf(server))
            } else {
                ImportResult.Error("Falha ao analisar URI")
            }
        } catch (e: Exception) {
            ImportResult.Error(e.message ?: "Erro desconhecido")
        }
    }
    
    private fun importMultipleUris(content: String): ImportResult {
        val lines = content.lines().filter { it.isNotBlank() }
        val servers = mutableListOf<Server>()
        val errors = mutableListOf<String>()
        
        for (line in lines) {
            val result = importFromUri(line.trim())
            when (result) {
                is ImportResult.Success -> servers.addAll(result.servers)
                is ImportResult.Error -> errors.add(result.message)
                else -> {}
            }
        }
        
        return if (servers.isNotEmpty()) {
            ImportResult.Success(servers, errors)
        } else if (errors.isNotEmpty()) {
            ImportResult.Error(errors.joinToString("\n"))
        } else {
            ImportResult.Error("Nenhum servidor encontrado")
        }
    }
    
    private fun importFromBase64(encoded: String): ImportResult {
        return try {
            val decoded = String(Base64.decode(encoded, Base64.DEFAULT))
            importMultipleUris(decoded)
        } catch (e: Exception) {
            ImportResult.Error("Falha ao decodificar Base64: ${e.message}")
        }
    }
    
    private fun importFromJson(json: String): ImportResult {
        return try {
            val servers = mutableListOf<Server>()
            
            when {
                json.startsWith("[") -> {
                    val array = JSONArray(json)
                    for (i in 0 until array.length()) {
                        parseServerFromJson(array.getJSONObject(i))?.let { servers.add(it) }
                    }
                }
                json.startsWith("{") -> {
                    val obj = JSONObject(json)
                    // Check if it's a Juka VPN export
                    if (obj.has("servers")) {
                        val array = obj.getJSONArray("servers")
                        for (i in 0 until array.length()) {
                            parseServerFromJson(array.getJSONObject(i))?.let { servers.add(it) }
                        }
                    } else {
                        parseServerFromJson(obj)?.let { servers.add(it) }
                    }
                }
            }
            
            servers.forEach { repository.saveServer(it) }
            ImportResult.Success(servers)
        } catch (e: Exception) {
            ImportResult.Error("Erro ao analisar JSON: ${e.message}")
        }
    }
    
    /**
     * Import from file
     */
    fun importFromFile(uri: Uri): ImportResult {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val content = BufferedReader(InputStreamReader(inputStream)).readText()
                
                // Check for compressed Juka VPN format
                if (content.startsWith(MAGIC_HEADER)) {
                    importFromJukaFormat(content)
                } else {
                    importFromUri(content)
                }
            } ?: ImportResult.Error("Não foi possível abrir o arquivo")
        } catch (e: Exception) {
            Log.e(TAG, "Error importing from file", e)
            ImportResult.Error("Erro ao ler arquivo: ${e.message}")
        }
    }
    
    private fun importFromJukaFormat(content: String): ImportResult {
        return try {
            val base64Data = content.removePrefix(MAGIC_HEADER)
            val compressed = Base64.decode(base64Data, Base64.DEFAULT)
            val decompressed = GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader().readText()
            importFromJson(decompressed)
        } catch (e: Exception) {
            ImportResult.Error("Erro ao ler formato Juka: ${e.message}")
        }
    }
    
    // ==================== EXPORT ====================
    
    /**
     * Export all servers to JSON format
     */
    fun exportAllServers(): String {
        val servers = repository.getAllServers()
        return exportServers(servers)
    }
    
    /**
     * Export specific servers to JSON format
     */
    fun exportServers(servers: List<Server>): String {
        val json = JSONObject().apply {
            put("version", EXPORT_VERSION)
            put("app", "Juka VPN")
            put("exportedAt", System.currentTimeMillis())
            put("servers", JSONArray().apply {
                servers.forEach { server ->
                    put(serverToJson(server))
                }
            })
        }
        return json.toString(2)
    }
    
    /**
     * Export servers to compressed Juka format
     */
    fun exportToJukaFormat(servers: List<Server>): String {
        val json = exportServers(servers)
        val outputStream = ByteArrayOutputStream()
        GZIPOutputStream(outputStream).bufferedWriter().use { it.write(json) }
        val compressed = outputStream.toByteArray()
        return MAGIC_HEADER + Base64.encodeToString(compressed, Base64.NO_WRAP)
    }
    
    /**
     * Export server to shareable URI
     */
    fun exportServerToUri(server: Server): String {
        return when (server.protocol) {
            Protocol.VMESS -> VmessParser.toUri(server)
            Protocol.VLESS -> VlessParser.toUri(server)
            Protocol.SHADOWSOCKS -> ShadowsocksParser.toUri(server)
            Protocol.TROJAN -> TrojanParser.toUri(server)
            Protocol.SSH -> SSHConfigParser.toUri(server)
            Protocol.UDP -> "" // UDP doesn't have a standard URI format
        }
    }
    
    /**
     * Export all servers to shareable URIs
     */
    fun exportAllToUris(): String {
        return repository.getAllServers()
            .mapNotNull { server ->
                val uri = exportServerToUri(server)
                if (uri.isNotEmpty()) uri else null
            }
            .joinToString("\n")
    }
    
    /**
     * Save export to file
     */
    fun saveToFile(uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray())
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to file", e)
            false
        }
    }
    
    // ==================== HELPERS ====================
    
    private fun isBase64(str: String): Boolean {
        return try {
            val decoded = Base64.decode(str, Base64.DEFAULT)
            val reEncoded = Base64.encodeToString(decoded, Base64.NO_WRAP)
            str.replace("\\s".toRegex(), "") == reEncoded.replace("\\s".toRegex(), "")
        } catch (e: Exception) {
            false
        }
    }
    
    private fun serverToJson(server: Server): JSONObject {
        return JSONObject().apply {
            put("name", server.name)
            put("address", server.address)
            put("port", server.port)
            put("protocol", server.protocol.name)
            put("countryCode", server.countryCode)
            server.uuid?.let { put("uuid", it) }
            server.alterId?.let { put("alterId", it) }
            server.security?.let { put("security", it) }
            server.network?.let { put("network", it) }
            server.host?.let { put("host", it) }
            server.path?.let { put("path", it) }
            put("tls", server.tls)
            server.sni?.let { put("sni", it) }
            server.method?.let { put("method", it) }
            server.password?.let { put("password", it) }
            server.sshUser?.let { put("sshUser", it) }
            server.sshPort?.let { put("sshPort", it) }
        }
    }
    
    private fun parseServerFromJson(json: JSONObject): Server? {
        return try {
            Server(
                name = json.getString("name"),
                address = json.getString("address"),
                port = json.getInt("port"),
                protocol = enumValueOf(json.optString("protocol", "VMESS")),
                countryCode = json.optString("countryCode", "UN"),
                countryName = json.optString("countryName", ""),
                uuid = json.optString("uuid").takeIf { it.isNotEmpty() },
                alterId = json.optInt("alterId").takeIf { it > 0 },
                security = json.optString("security").takeIf { it.isNotEmpty() },
                network = json.optString("network").takeIf { it.isNotEmpty() },
                host = json.optString("host").takeIf { it.isNotEmpty() },
                path = json.optString("path").takeIf { it.isNotEmpty() },
                tls = json.optBoolean("tls", false),
                sni = json.optString("sni").takeIf { it.isNotEmpty() },
                method = json.optString("method").takeIf { it.isNotEmpty() },
                password = json.optString("password").takeIf { it.isNotEmpty() },
                sshUser = json.optString("sshUser").takeIf { it.isNotEmpty() },
                sshPort = json.optInt("sshPort", 22)
            )
        } catch (e: Exception) {
            null
        }
    }
    
    // ==================== RESULT CLASSES ====================
    
    sealed class ImportResult {
        data class Success(
            val servers: List<Server>,
            val warnings: List<String> = emptyList()
        ) : ImportResult()
        
        data class Error(val message: String) : ImportResult()
        
        data class SubscriptionUrl(val url: String) : ImportResult()
    }
}
