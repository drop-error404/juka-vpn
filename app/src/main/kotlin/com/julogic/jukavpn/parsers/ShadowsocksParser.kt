package com.julogic.jukavpn.parsers

import android.net.Uri
import android.util.Base64
import com.julogic.jukavpn.models.Protocol
import com.julogic.jukavpn.models.Server
import org.json.JSONObject

/**
 * Parser completo para protocolo Shadowsocks
 * Suporta SIP002, Legacy, SIP008 (JSON) e formatos de plugins
 * Formatos:
 * - SIP002: ss://base64(method:password)@host:port#name
 * - Legacy: ss://base64(method:password@host:port)#name
 * - SIP008: JSON format para subscription
 */
object ShadowsocksParser {
    
    private val SUPPORTED_METHODS = setOf(
        // AEAD ciphers (recomendados)
        "aes-128-gcm", "aes-256-gcm", "chacha20-ietf-poly1305",
        "xchacha20-ietf-poly1305",
        // AEAD 2022 ciphers
        "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm",
        "2022-blake3-chacha20-poly1305",
        // Stream ciphers (deprecated mas ainda usados)
        "aes-128-cfb", "aes-192-cfb", "aes-256-cfb",
        "aes-128-ctr", "aes-192-ctr", "aes-256-ctr",
        "camellia-128-cfb", "camellia-192-cfb", "camellia-256-cfb",
        "bf-cfb", "cast5-cfb", "des-cfb", "idea-cfb", "rc2-cfb",
        "seed-cfb", "salsa20", "chacha20", "chacha20-ietf",
        "rc4-md5",
        // None/Plain
        "none", "plain"
    )
    
    private val SUPPORTED_PLUGINS = setOf(
        "obfs-local", "simple-obfs", "v2ray-plugin", "xray-plugin",
        "kcptun", "cloak", "gost-plugin", "gun", "qtun"
    )
    
    /**
     * Parse ss:// URI format
     * Detecta automaticamente o formato e faz o parse
     */
    fun parse(uri: String): Server? {
        if (!uri.lowercase().startsWith("ss://")) return null
        
        return try {
            // Tenta SIP002 primeiro (mais comum)
            parseSIP002(uri) 
                ?: parseLegacy(uri)
                ?: parseAlternative(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Parse formato SIP002 (padrão atual)
     * ss://base64(method:password)@host:port?plugin=...#name
     */
    private fun parseSIP002(uri: String): Server? {
        return try {
            val withoutScheme = uri.substring(5) // Remove "ss://"
            
            // Separa fragment (#name)
            val fragmentIndex = withoutScheme.lastIndexOf('#')
            val name = if (fragmentIndex > 0) {
                Uri.decode(withoutScheme.substring(fragmentIndex + 1))
            } else {
                "Shadowsocks Server"
            }
            
            val mainPart = if (fragmentIndex > 0) {
                withoutScheme.substring(0, fragmentIndex)
            } else {
                withoutScheme
            }
            
            // Separa query string
            val queryIndex = mainPart.indexOf('?')
            val queryParams = if (queryIndex > 0) {
                parseQueryString(mainPart.substring(queryIndex + 1))
            } else {
                emptyMap()
            }
            
            val hostPart = if (queryIndex > 0) {
                mainPart.substring(0, queryIndex)
            } else {
                mainPart
            }
            
            // Separa userinfo@host:port
            val atIndex = hostPart.lastIndexOf('@')
            if (atIndex < 0) return null
            
            val userInfo = hostPart.substring(0, atIndex)
            val hostPortPart = hostPart.substring(atIndex + 1)
            
            // Decodifica userinfo (method:password)
            val decoded = decodeBase64(userInfo)
            
            val colonIndex = decoded.indexOf(':')
            if (colonIndex < 0) return null
            
            val method = decoded.substring(0, colonIndex).lowercase().trim()
            val password = decoded.substring(colonIndex + 1)
            
            // Parse host:port
            val (host, port) = parseHostPort(hostPortPart)
            
            // Plugin info
            val plugin = queryParams["plugin"]
            val pluginOpts = if (plugin != null) {
                extractPluginOpts(plugin)
            } else {
                null
            }
            
            // Extra params for obfs
            val obfs = queryParams["obfs"] ?: pluginOpts?.get("obfs")
            val obfsHost = queryParams["obfs-host"] ?: pluginOpts?.get("obfs-host")
            
            Server(
                name = name,
                address = host,
                port = port,
                protocol = Protocol.SHADOWSOCKS,
                countryCode = extractCountryCode(name),
                countryName = "",
                method = normalizeMethod(method),
                password = password,
                obfs = obfs,
                obfsParam = obfsHost
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Parse formato Legacy
     * ss://base64(method:password@host:port)#name
     */
    private fun parseLegacy(uri: String): Server? {
        return try {
            val withoutScheme = uri.substring(5) // Remove "ss://"
            
            // Separa fragment
            val fragmentIndex = withoutScheme.lastIndexOf('#')
            val name = if (fragmentIndex > 0) {
                Uri.decode(withoutScheme.substring(fragmentIndex + 1))
            } else {
                "Shadowsocks Server"
            }
            
            val base64Part = if (fragmentIndex > 0) {
                withoutScheme.substring(0, fragmentIndex)
            } else {
                withoutScheme
            }
            
            // Decodifica tudo
            val decoded = decodeBase64(base64Part)
            
            // Formato: method:password@host:port
            val atIndex = decoded.lastIndexOf('@')
            if (atIndex < 0) return null
            
            val methodPassword = decoded.substring(0, atIndex)
            val hostPort = decoded.substring(atIndex + 1)
            
            val colonIndex = methodPassword.indexOf(':')
            if (colonIndex < 0) return null
            
            val method = methodPassword.substring(0, colonIndex).lowercase().trim()
            val password = methodPassword.substring(colonIndex + 1)
            
            val (host, port) = parseHostPort(hostPort)
            
            Server(
                name = name,
                address = host,
                port = port,
                protocol = Protocol.SHADOWSOCKS,
                countryCode = extractCountryCode(name),
                countryName = "",
                method = normalizeMethod(method),
                password = password
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Parse formato alternativo usado por alguns clients
     * ss://method:password@host:port#name (sem base64)
     */
    private fun parseAlternative(uri: String): Server? {
        return try {
            val parsed = Uri.parse(uri)
            val userInfo = parsed.userInfo ?: return null
            val host = parsed.host ?: return null
            val port = parsed.port.takeIf { it > 0 } ?: 8388
            val name = Uri.decode(parsed.fragment ?: "Shadowsocks Server")
            
            val colonIndex = userInfo.indexOf(':')
            if (colonIndex < 0) return null
            
            val method = userInfo.substring(0, colonIndex).lowercase()
            val password = Uri.decode(userInfo.substring(colonIndex + 1))
            
            Server(
                name = name,
                address = host,
                port = port,
                protocol = Protocol.SHADOWSOCKS,
                countryCode = extractCountryCode(name),
                countryName = "",
                method = normalizeMethod(method),
                password = password
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Parse SIP008 JSON format (usado em subscriptions)
     */
    fun parseJson(json: String): Server? {
        return try {
            val obj = JSONObject(json)
            
            val server = obj.getString("server")
            val port = obj.getInt("server_port")
            val method = obj.getString("method").lowercase()
            val password = obj.getString("password")
            val name = obj.optString("remarks", "Shadowsocks Server")
            
            // Plugin support
            val plugin = obj.optString("plugin", null)
            val pluginOpts = obj.optString("plugin_opts", null)
            
            Server(
                name = name,
                address = server,
                port = port,
                protocol = Protocol.SHADOWSOCKS,
                countryCode = extractCountryCode(name),
                countryName = "",
                method = normalizeMethod(method),
                password = password,
                obfs = parsePluginObfs(plugin, pluginOpts),
                obfsParam = parsePluginObfsHost(pluginOpts)
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Parse múltiplos servidores de SIP008 JSON array
     */
    fun parseJsonArray(json: String): List<Server> {
        return try {
            val array = org.json.JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                parseJson(array.getJSONObject(i).toString())
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Decodifica Base64 com suporte a múltiplos formatos
     */
    private fun decodeBase64(encoded: String): String {
        val cleaned = encoded.trim()
            .replace("\n", "")
            .replace("\r", "")
            .replace(" ", "")
            .replace("-", "+")
            .replace("_", "/")
        
        return try {
            String(Base64.decode(cleaned, Base64.URL_SAFE or Base64.NO_PADDING))
        } catch (e: Exception) {
            try {
                String(Base64.decode(cleaned, Base64.DEFAULT))
            } catch (e2: Exception) {
                // Adiciona padding
                val padded = when (cleaned.length % 4) {
                    2 -> "$cleaned=="
                    3 -> "$cleaned="
                    else -> cleaned
                }
                String(Base64.decode(padded, Base64.DEFAULT))
            }
        }
    }
    
    /**
     * Parse host:port com suporte a IPv6
     */
    private fun parseHostPort(hostPort: String): Pair<String, Int> {
        return if (hostPort.startsWith("[")) {
            // IPv6
            val closeBracket = hostPort.indexOf(']')
            val host = hostPort.substring(1, closeBracket)
            val port = if (closeBracket + 2 < hostPort.length) {
                hostPort.substring(closeBracket + 2).toIntOrNull() ?: 8388
            } else {
                8388
            }
            host to port
        } else {
            val lastColon = hostPort.lastIndexOf(':')
            if (lastColon > 0) {
                val host = hostPort.substring(0, lastColon)
                val port = hostPort.substring(lastColon + 1).toIntOrNull() ?: 8388
                host to port
            } else {
                hostPort to 8388
            }
        }
    }
    
    /**
     * Parse query string
     */
    private fun parseQueryString(query: String): Map<String, String> {
        return query.split("&")
            .mapNotNull { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) {
                    parts[0] to Uri.decode(parts[1])
                } else null
            }
            .toMap()
    }
    
    /**
     * Extrai opções do plugin
     */
    private fun extractPluginOpts(plugin: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        
        // Formato: plugin-name;opt1=val1;opt2=val2
        val parts = plugin.split(";")
        if (parts.size > 1) {
            parts.drop(1).forEach { opt ->
                val kv = opt.split("=", limit = 2)
                if (kv.size == 2) {
                    result[kv[0]] = kv[1]
                }
            }
        }
        
        return result
    }
    
    /**
     * Extrai tipo de obfuscation do plugin
     */
    private fun parsePluginObfs(plugin: String?, pluginOpts: String?): String? {
        if (plugin.isNullOrEmpty()) return null
        
        return when {
            plugin.contains("obfs") -> {
                pluginOpts?.split(";")?.find { it.startsWith("obfs=") }
                    ?.substringAfter("=")
            }
            plugin.contains("v2ray") -> "websocket"
            else -> null
        }
    }
    
    /**
     * Extrai host de obfuscation
     */
    private fun parsePluginObfsHost(pluginOpts: String?): String? {
        return pluginOpts?.split(";")?.find { 
            it.startsWith("obfs-host=") || it.startsWith("host=")
        }?.substringAfter("=")
    }
    
    /**
     * Normaliza método de encriptação
     */
    private fun normalizeMethod(method: String): String {
        val normalized = method.lowercase().trim()
        return if (normalized in SUPPORTED_METHODS) normalized else "aes-256-gcm"
    }
    
    /**
     * Convert Server to ss:// URI (SIP002 format)
     */
    fun toUri(server: Server): String {
        val userInfo = "${server.method}:${server.password}"
        val encoded = Base64.encodeToString(
            userInfo.toByteArray(), 
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        
        val params = mutableListOf<String>()
        
        // Plugin
        if (!server.obfs.isNullOrEmpty()) {
            val pluginStr = buildString {
                append("obfs-local")
                append(";obfs=${server.obfs}")
                server.obfsParam?.let { append(";obfs-host=$it") }
            }
            params.add("plugin=${Uri.encode(pluginStr)}")
        }
        
        val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        val fragment = Uri.encode(server.name)
        
        return "ss://$encoded@${server.address}:${server.port}$queryString#$fragment"
    }
    
    /**
     * Convert Server to SIP008 JSON
     */
    fun toJson(server: Server): JSONObject {
        return JSONObject().apply {
            put("server", server.address)
            put("server_port", server.port)
            put("method", server.method)
            put("password", server.password)
            put("remarks", server.name)
            
            if (!server.obfs.isNullOrEmpty()) {
                put("plugin", "obfs-local")
                put("plugin_opts", buildString {
                    append("obfs=${server.obfs}")
                    server.obfsParam?.let { append(";obfs-host=$it") }
                })
            }
        }
    }
    
    /**
     * Valida servidor Shadowsocks
     */
    fun validate(server: Server): Boolean {
        return server.protocol == Protocol.SHADOWSOCKS &&
               !server.method.isNullOrEmpty() &&
               !server.password.isNullOrEmpty() &&
               server.address.isNotEmpty() &&
               server.port in 1..65535
    }
    
    /**
     * Verifica se usa AEAD cipher
     */
    fun isAead(server: Server): Boolean {
        val method = server.method?.lowercase() ?: return false
        return method.contains("gcm") || 
               method.contains("poly1305") || 
               method.startsWith("2022-")
    }
    
    /**
     * Gera configuração para libss/shadowsocks-rust
     */
    fun toShadowsocksConfig(server: Server): JSONObject {
        return JSONObject().apply {
            put("server", server.address)
            put("server_port", server.port)
            put("local_port", 1080)
            put("local_address", "127.0.0.1")
            put("password", server.password)
            put("method", server.method)
            put("timeout", 300)
            put("fast_open", false)
            
            if (!server.obfs.isNullOrEmpty()) {
                put("plugin", "obfs-local")
                put("plugin_opts", "obfs=${server.obfs}${server.obfsParam?.let { ";obfs-host=$it" } ?: ""}")
            }
        }
    }
    
    private fun extractCountryCode(name: String): String {
        val countryPatterns = listOf(
            Regex("\\[([A-Z]{2})\\]"),
            Regex("\\(([A-Z]{2})\\)"),
            Regex("^([A-Z]{2})[-_\\s]"),
            Regex("[-_\\s]([A-Z]{2})$")
        )
        
        val upperName = name.uppercase()
        
        for (pattern in countryPatterns) {
            pattern.find(upperName)?.let {
                return it.groupValues[1]
            }
        }
        
        // Country mapping
        val countryMap = mapOf(
            "🇺🇸" to "US", "USA" to "US", "UNITED STATES" to "US",
            "🇬🇧" to "GB", "UK" to "GB", "UNITED KINGDOM" to "GB",
            "🇩🇪" to "DE", "GERMANY" to "DE",
            "🇫🇷" to "FR", "FRANCE" to "FR",
            "🇯🇵" to "JP", "JAPAN" to "JP",
            "🇸🇬" to "SG", "SINGAPORE" to "SG",
            "🇭🇰" to "HK", "HONG KONG" to "HK",
            "🇰🇷" to "KR", "KOREA" to "KR",
            "🇳🇱" to "NL", "NETHERLANDS" to "NL",
            "🇨🇦" to "CA", "CANADA" to "CA",
            "🇦🇺" to "AU", "AUSTRALIA" to "AU",
            "🇧🇷" to "BR", "BRAZIL" to "BR",
            "🇷🇺" to "RU", "RUSSIA" to "RU",
            "🇮🇳" to "IN", "INDIA" to "IN",
            "🇹🇼" to "TW", "TAIWAN" to "TW",
            "🇨🇳" to "CN", "CHINA" to "CN"
        )
        
        for ((key, code) in countryMap) {
            if (name.contains(key) || upperName.contains(key)) {
                return code
            }
        }
        
        return "UN"
    }
}
