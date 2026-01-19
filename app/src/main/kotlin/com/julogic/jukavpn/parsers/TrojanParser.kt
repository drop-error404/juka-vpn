package com.julogic.jukavpn.parsers

import android.net.Uri
import com.julogic.jukavpn.models.Protocol
import com.julogic.jukavpn.models.Server
import org.json.JSONObject

/**
 * Parser completo para protocolo Trojan
 * Suporta Trojan-GFW, Trojan-Go e variantes
 * Formato: trojan://password@host:port?parameters#name
 */
object TrojanParser {
    
    private val SUPPORTED_NETWORKS = setOf("tcp", "ws", "grpc", "h2")
    private val SUPPORTED_SECURITY = setOf("tls", "xtls", "reality", "none")
    
    /**
     * Parse trojan:// URI format
     * trojan://password@host:port?parameters#name
     */
    fun parse(uri: String): Server? {
        if (!uri.lowercase().startsWith("trojan://")) return null
        
        return try {
            val parsed = Uri.parse(uri)
            parseUri(parsed, uri)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback para parsing manual
            parseManual(uri)
        }
    }
    
    /**
     * Parse usando Android Uri
     */
    private fun parseUri(parsed: Uri, originalUri: String): Server? {
        val password = parsed.userInfo ?: return null
        val host = parsed.host ?: return null
        val port = parsed.port.takeIf { it > 0 } ?: 443
        val fragment = parsed.fragment ?: "Trojan Server"
        
        // Valida password
        if (password.isEmpty()) return null
        
        // Extrai parâmetros
        val security = parsed.getQueryParameter("security") ?: "tls"
        val network = parsed.getQueryParameter("type") ?: 
                      parsed.getQueryParameter("net") ?: "tcp"
        
        // TLS parameters
        val sni = parsed.getQueryParameter("sni") ?: 
                  parsed.getQueryParameter("peer") ?: ""
        val fingerprint = parsed.getQueryParameter("fp") ?: 
                          parsed.getQueryParameter("fingerprint") ?: ""
        val alpn = parsed.getQueryParameter("alpn") ?: ""
        val allowInsecure = parsed.getQueryParameter("allowInsecure") == "1" ||
                            parsed.getQueryParameter("skipCertVerify") == "1"
        
        // Transport parameters
        val host_param = parsed.getQueryParameter("host") ?: ""
        val path = parsed.getQueryParameter("path") ?: ""
        val serviceName = parsed.getQueryParameter("serviceName") ?: ""
        
        // Header type para TCP
        val headerType = parsed.getQueryParameter("headerType") ?: "none"
        
        // Flow (Trojan-Go específico)
        val flow = parsed.getQueryParameter("flow")
        
        // Trojan-Go WebSocket específico
        val wsHost = parsed.getQueryParameter("wsHost") ?: host_param
        val wsPath = parsed.getQueryParameter("wsPath") ?: path
        
        // Plugin (para shadowsocks plugin compat)
        val plugin = parsed.getQueryParameter("plugin")
        val pluginOpts = parsed.getQueryParameter("pluginOpts")
        
        // Determina TLS (Trojan sempre usa TLS por padrão)
        val tls = security != "none"
        
        // SNI efetivo
        val effectiveSni = when {
            sni.isNotEmpty() -> sni
            host_param.isNotEmpty() && !host_param.contains(",") -> host_param
            else -> host
        }
        
        // Path efetivo baseado no network
        val effectivePath = when (network.lowercase()) {
            "ws" -> wsPath.ifEmpty { path }.ifEmpty { "/" }
            "grpc" -> serviceName.ifEmpty { path }
            else -> path
        }
        
        // Host efetivo
        val effectiveHost = when (network.lowercase()) {
            "ws" -> wsHost.ifEmpty { host_param }
            else -> host_param
        }
        
        return Server(
            name = Uri.decode(fragment),
            address = host,
            port = port,
            protocol = Protocol.TROJAN,
            countryCode = extractCountryCode(fragment),
            countryName = "",
            password = password,
            security = normalizeSecurityType(security),
            network = normalizeNetwork(network),
            headerType = if (headerType != "none") headerType else null,
            host = effectiveHost.ifEmpty { null },
            path = effectivePath.ifEmpty { null },
            tls = tls,
            sni = effectiveSni.ifEmpty { null },
            fingerprint = fingerprint.ifEmpty { null },
            alpn = alpn.ifEmpty { null },
            flow = flow?.ifEmpty { null }
        )
    }
    
    /**
     * Parsing manual como fallback
     */
    private fun parseManual(uri: String): Server? {
        return try {
            val withoutScheme = uri.substring(9) // Remove "trojan://"
            
            // Separa fragment (#name)
            val fragmentIndex = withoutScheme.lastIndexOf('#')
            val name = if (fragmentIndex > 0) {
                Uri.decode(withoutScheme.substring(fragmentIndex + 1))
            } else {
                "Trojan Server"
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
            
            // Separa password@host:port
            val atIndex = hostPart.lastIndexOf('@')
            if (atIndex < 0) return null
            
            val password = Uri.decode(hostPart.substring(0, atIndex))
            val hostPortPart = hostPart.substring(atIndex + 1)
            
            // Parse host:port (suporta IPv6)
            val (host, port) = parseHostPort(hostPortPart)
            
            val security = queryParams["security"] ?: "tls"
            val tls = security != "none"
            
            Server(
                name = name,
                address = host,
                port = port,
                protocol = Protocol.TROJAN,
                countryCode = extractCountryCode(name),
                countryName = "",
                password = password,
                security = normalizeSecurityType(security),
                network = normalizeNetwork(queryParams["type"] ?: "tcp"),
                host = queryParams["host"],
                path = queryParams["path"] ?: queryParams["serviceName"],
                tls = tls,
                sni = queryParams["sni"] ?: host,
                fingerprint = queryParams["fp"],
                alpn = queryParams["alpn"]
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Parse query string para Map
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
     * Parse host:port com suporte a IPv6
     */
    private fun parseHostPort(hostPort: String): Pair<String, Int> {
        return if (hostPort.startsWith("[")) {
            // IPv6: [::1]:443
            val closeBracket = hostPort.indexOf(']')
            val host = hostPort.substring(1, closeBracket)
            val port = if (closeBracket + 2 < hostPort.length) {
                hostPort.substring(closeBracket + 2).toIntOrNull() ?: 443
            } else {
                443
            }
            host to port
        } else {
            // IPv4 ou hostname
            val lastColon = hostPort.lastIndexOf(':')
            if (lastColon > 0) {
                val host = hostPort.substring(0, lastColon)
                val port = hostPort.substring(lastColon + 1).toIntOrNull() ?: 443
                host to port
            } else {
                hostPort to 443
            }
        }
    }
    
    /**
     * Normaliza tipo de segurança
     */
    private fun normalizeSecurityType(security: String): String {
        val normalized = security.lowercase().trim()
        return if (normalized in SUPPORTED_SECURITY) normalized else "tls"
    }
    
    /**
     * Normaliza tipo de rede
     */
    private fun normalizeNetwork(network: String): String {
        val normalized = network.lowercase().trim()
        return when (normalized) {
            "websocket" -> "ws"
            "http2" -> "h2"
            else -> if (normalized in SUPPORTED_NETWORKS) normalized else "tcp"
        }
    }
    
    /**
     * Convert Server to trojan:// URI
     */
    fun toUri(server: Server): String {
        val params = mutableListOf<String>()
        
        // Security
        server.security?.let { 
            if (it != "tls") params.add("security=$it") 
        }
        
        // Network type
        server.network?.let { 
            if (it != "tcp") params.add("type=$it") 
        }
        
        // Host
        server.host?.let { 
            if (it.isNotEmpty()) params.add("host=${Uri.encode(it)}") 
        }
        
        // Path
        when (server.network) {
            "grpc" -> server.path?.let { 
                if (it.isNotEmpty()) params.add("serviceName=${Uri.encode(it)}") 
            }
            "ws" -> server.path?.let { 
                if (it.isNotEmpty()) params.add("path=${Uri.encode(it)}") 
            }
            else -> server.path?.let { 
                if (it.isNotEmpty()) params.add("path=${Uri.encode(it)}") 
            }
        }
        
        // TLS parameters
        server.sni?.let { 
            if (it.isNotEmpty() && it != server.address) params.add("sni=$it") 
        }
        server.fingerprint?.let { 
            if (it.isNotEmpty()) params.add("fp=$it") 
        }
        server.alpn?.let { 
            if (it.isNotEmpty()) params.add("alpn=${Uri.encode(it)}") 
        }
        
        // Flow
        server.flow?.let { 
            if (it.isNotEmpty()) params.add("flow=$it") 
        }
        
        val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        val fragment = Uri.encode(server.name)
        val encodedPassword = Uri.encode(server.password)
        
        return "trojan://$encodedPassword@${server.address}:${server.port}$queryString#$fragment"
    }
    
    /**
     * Valida servidor Trojan
     */
    fun validate(server: Server): Boolean {
        return server.protocol == Protocol.TROJAN &&
               !server.password.isNullOrEmpty() &&
               server.address.isNotEmpty() &&
               server.port in 1..65535
    }
    
    /**
     * Verifica se é Trojan-Go (com WebSocket)
     */
    fun isTrojanGo(server: Server): Boolean {
        return server.network == "ws" || server.network == "grpc"
    }
    
    /**
     * Gera configuração para V2Ray/Xray core (Trojan outbound)
     */
    fun toV2RayConfig(server: Server): JSONObject {
        return JSONObject().apply {
            put("servers", org.json.JSONArray().put(JSONObject().apply {
                put("address", server.address)
                put("port", server.port)
                put("password", server.password)
                put("level", 0)
            }))
        }
    }
    
    /**
     * Gera configuração streamSettings para Trojan
     */
    fun toStreamSettings(server: Server): JSONObject {
        return JSONObject().apply {
            put("network", server.network ?: "tcp")
            
            // TLS Settings
            if (server.tls) {
                put("security", server.security ?: "tls")
                put("tlsSettings", JSONObject().apply {
                    server.sni?.let { put("serverName", it) }
                    server.fingerprint?.let { put("fingerprint", it) }
                    server.alpn?.let { 
                        put("alpn", it.split(",").map { a -> a.trim() })
                    }
                    put("allowInsecure", false)
                })
            }
            
            // WebSocket Settings
            if (server.network == "ws") {
                put("wsSettings", JSONObject().apply {
                    server.path?.let { put("path", it) }
                    server.host?.let { 
                        put("headers", JSONObject().put("Host", it))
                    }
                })
            }
            
            // gRPC Settings
            if (server.network == "grpc") {
                put("grpcSettings", JSONObject().apply {
                    server.path?.let { put("serviceName", it) }
                    put("multiMode", false)
                })
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
        
        // Country emoji/name mapping
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
            "🇹🇼" to "TW", "TAIWAN" to "TW"
        )
        
        for ((key, code) in countryMap) {
            if (name.contains(key) || upperName.contains(key)) {
                return code
            }
        }
        
        return "UN"
    }
}
