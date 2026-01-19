package com.julogic.jukavpn.parsers

import android.net.Uri
import com.julogic.jukavpn.models.Protocol
import com.julogic.jukavpn.models.Server
import org.json.JSONObject

/**
 * Parser completo para protocolo VLESS
 * Suporta VLESS com TLS, XTLS, Reality e múltiplos transports
 * Formato: vless://uuid@host:port?parameters#name
 */
object VlessParser {
    
    private val SUPPORTED_SECURITY = setOf("none", "tls", "xtls", "reality")
    private val SUPPORTED_NETWORKS = setOf("tcp", "ws", "kcp", "http", "quic", "grpc", "h2")
    private val SUPPORTED_FLOWS = setOf(
        "xtls-rprx-vision",
        "xtls-rprx-vision-udp443",
        "xtls-rprx-direct",
        "xtls-rprx-origin"
    )
    
    /**
     * Parse vless:// URI format
     * vless://uuid@host:port?parameters#name
     */
    fun parse(uri: String): Server? {
        if (!uri.lowercase().startsWith("vless://")) return null
        
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
        val uuid = parsed.userInfo ?: return null
        val host = parsed.host ?: return null
        val port = parsed.port.takeIf { it > 0 } ?: 443
        val fragment = parsed.fragment ?: "VLESS Server"
        
        // Valida UUID
        if (uuid.isEmpty() || uuid.length < 32) return null
        
        // Extrai parâmetros
        val security = parsed.getQueryParameter("security") ?: "none"
        val network = parsed.getQueryParameter("type") ?: 
                     parsed.getQueryParameter("net") ?: "tcp"
        val encryption = parsed.getQueryParameter("encryption") ?: "none"
        val flow = parsed.getQueryParameter("flow")?.takeIf { it.isNotEmpty() }
        
        // TLS/Reality parameters
        val sni = parsed.getQueryParameter("sni") ?: 
                  parsed.getQueryParameter("peer") ?: ""
        val fingerprint = parsed.getQueryParameter("fp") ?: 
                          parsed.getQueryParameter("fingerprint") ?: ""
        val alpn = parsed.getQueryParameter("alpn")?.replace(",", "%2C") ?: ""
        val allowInsecure = parsed.getQueryParameter("allowInsecure") == "1"
        
        // Reality specific
        val publicKey = parsed.getQueryParameter("pbk") ?: 
                        parsed.getQueryParameter("publicKey")
        val shortId = parsed.getQueryParameter("sid") ?: 
                      parsed.getQueryParameter("shortId")
        val spiderX = parsed.getQueryParameter("spx")
        
        // Transport parameters
        val headerType = parsed.getQueryParameter("headerType") ?: 
                         parsed.getQueryParameter("type")?.takeIf { security != "reality" }
        val host_param = parsed.getQueryParameter("host") ?: ""
        val path = parsed.getQueryParameter("path") ?: 
                   parsed.getQueryParameter("serviceName") ?: ""
        
        // gRPC specific
        val serviceName = parsed.getQueryParameter("serviceName") ?: ""
        val mode = parsed.getQueryParameter("mode") // gun, multi, etc.
        
        // Determina se TLS está ativo
        val tls = security in setOf("tls", "xtls", "reality")
        
        // Efective SNI
        val effectiveSni = when {
            sni.isNotEmpty() -> sni
            host_param.isNotEmpty() && !host_param.contains(",") -> host_param
            else -> host
        }
        
        // Build host string com parâmetros extras para Reality
        val hostValue = buildString {
            append(host_param)
            if (publicKey != null) {
                if (isNotEmpty()) append("|")
                append("pbk=$publicKey")
            }
            if (shortId != null) {
                if (isNotEmpty()) append("|")
                append("sid=$shortId")
            }
        }.ifEmpty { host_param }
        
        return Server(
            name = Uri.decode(fragment),
            address = host,
            port = port,
            protocol = Protocol.VLESS,
            countryCode = extractCountryCode(fragment),
            countryName = "",
            uuid = uuid,
            security = normalizeSecurityType(security),
            network = normalizeNetwork(network),
            headerType = headerType,
            host = hostValue.ifEmpty { null },
            path = if (network == "grpc") serviceName else path,
            tls = tls,
            sni = effectiveSni.ifEmpty { null },
            fingerprint = fingerprint.ifEmpty { null },
            alpn = alpn.ifEmpty { null },
            flow = normalizeFlow(flow)
        )
    }
    
    /**
     * Parsing manual como fallback
     */
    private fun parseManual(uri: String): Server? {
        return try {
            val withoutScheme = uri.substring(8) // Remove "vless://"
            
            // Separa fragment (#name)
            val fragmentIndex = withoutScheme.lastIndexOf('#')
            val name = if (fragmentIndex > 0) {
                Uri.decode(withoutScheme.substring(fragmentIndex + 1))
            } else {
                "VLESS Server"
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
            
            // Separa uuid@host:port
            val atIndex = hostPart.indexOf('@')
            if (atIndex < 0) return null
            
            val uuid = hostPart.substring(0, atIndex)
            val hostPortPart = hostPart.substring(atIndex + 1)
            
            // Parse host:port (suporta IPv6)
            val (host, port) = parseHostPort(hostPortPart)
            
            val security = queryParams["security"] ?: "none"
            val tls = security in setOf("tls", "xtls", "reality")
            
            Server(
                name = name,
                address = host,
                port = port,
                protocol = Protocol.VLESS,
                countryCode = extractCountryCode(name),
                countryName = "",
                uuid = uuid,
                security = normalizeSecurityType(security),
                network = normalizeNetwork(queryParams["type"] ?: "tcp"),
                headerType = queryParams["headerType"],
                host = queryParams["host"],
                path = queryParams["path"] ?: queryParams["serviceName"],
                tls = tls,
                sni = queryParams["sni"],
                fingerprint = queryParams["fp"],
                alpn = queryParams["alpn"],
                flow = normalizeFlow(queryParams["flow"])
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
            val parts = hostPort.split(":")
            val host = parts[0]
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 443
            host to port
        }
    }
    
    /**
     * Normaliza tipo de segurança
     */
    private fun normalizeSecurityType(security: String): String {
        val normalized = security.lowercase().trim()
        return if (normalized in SUPPORTED_SECURITY) normalized else "none"
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
     * Normaliza flow
     */
    private fun normalizeFlow(flow: String?): String? {
        if (flow.isNullOrEmpty()) return null
        val normalized = flow.lowercase().trim()
        return if (normalized in SUPPORTED_FLOWS) flow else null
    }
    
    /**
     * Convert Server to vless:// URI
     */
    fun toUri(server: Server): String {
        val params = mutableListOf<String>()
        
        // Security sempre primeiro
        server.security?.let { params.add("security=$it") }
        
        // Encryption para VLESS é sempre none
        params.add("encryption=none")
        
        // Network type
        server.network?.let { 
            if (it != "tcp") params.add("type=$it")
        }
        
        // Flow (para XTLS/Vision)
        server.flow?.let { 
            if (it.isNotEmpty()) params.add("flow=$it") 
        }
        
        // Header type
        server.headerType?.let { 
            if (it.isNotEmpty() && it != "none") params.add("headerType=$it") 
        }
        
        // Host
        server.host?.let { host ->
            val cleanHost = host.split("|").first()
            if (cleanHost.isNotEmpty()) params.add("host=${Uri.encode(cleanHost)}")
        }
        
        // Path ou serviceName
        when (server.network) {
            "grpc" -> server.path?.let { 
                if (it.isNotEmpty()) params.add("serviceName=${Uri.encode(it)}") 
            }
            else -> server.path?.let { 
                if (it.isNotEmpty()) params.add("path=${Uri.encode(it)}") 
            }
        }
        
        // TLS parameters
        server.sni?.let { if (it.isNotEmpty()) params.add("sni=$it") }
        server.fingerprint?.let { if (it.isNotEmpty()) params.add("fp=$it") }
        server.alpn?.let { if (it.isNotEmpty()) params.add("alpn=${Uri.encode(it)}") }
        
        // Reality parameters from host string
        server.host?.let { host ->
            val parts = host.split("|")
            parts.forEach { part ->
                when {
                    part.startsWith("pbk=") -> params.add(part)
                    part.startsWith("sid=") -> params.add(part)
                }
            }
        }
        
        val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        val fragment = Uri.encode(server.name)
        
        return "vless://${server.uuid}@${server.address}:${server.port}$queryString#$fragment"
    }
    
    /**
     * Valida servidor VLESS
     */
    fun validate(server: Server): Boolean {
        return server.protocol == Protocol.VLESS &&
               !server.uuid.isNullOrEmpty() &&
               server.uuid.length >= 32 &&
               server.address.isNotEmpty() &&
               server.port in 1..65535
    }
    
    /**
     * Verifica se é Reality
     */
    fun isReality(server: Server): Boolean {
        return server.security == "reality"
    }
    
    /**
     * Verifica se usa XTLS
     */
    fun usesXtls(server: Server): Boolean {
        return server.security == "xtls" || !server.flow.isNullOrEmpty()
    }
    
    /**
     * Gera configuração para V2Ray core
     */
    fun toV2RayConfig(server: Server): JSONObject {
        return JSONObject().apply {
            put("vnext", org.json.JSONArray().put(JSONObject().apply {
                put("address", server.address)
                put("port", server.port)
                put("users", org.json.JSONArray().put(JSONObject().apply {
                    put("id", server.uuid)
                    put("encryption", "none")
                    put("level", 0)
                    server.flow?.let { put("flow", it) }
                }))
            }))
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
            "🇺🇸" to "US", "USA" to "US", "UNITED STATES" to "US", "AMERICA" to "US",
            "🇬🇧" to "GB", "UK" to "GB", "UNITED KINGDOM" to "GB", "ENGLAND" to "GB",
            "🇩🇪" to "DE", "GERMANY" to "DE", "DEUTSCHLAND" to "DE",
            "🇫🇷" to "FR", "FRANCE" to "FR",
            "🇯🇵" to "JP", "JAPAN" to "JP",
            "🇸🇬" to "SG", "SINGAPORE" to "SG",
            "🇭🇰" to "HK", "HONG KONG" to "HK", "HONGKONG" to "HK",
            "🇰🇷" to "KR", "KOREA" to "KR", "SOUTH KOREA" to "KR",
            "🇳🇱" to "NL", "NETHERLANDS" to "NL", "HOLLAND" to "NL",
            "🇨🇦" to "CA", "CANADA" to "CA",
            "🇦🇺" to "AU", "AUSTRALIA" to "AU",
            "🇧🇷" to "BR", "BRAZIL" to "BR", "BRASIL" to "BR",
            "🇷🇺" to "RU", "RUSSIA" to "RU",
            "🇮🇳" to "IN", "INDIA" to "IN",
            "🇹🇼" to "TW", "TAIWAN" to "TW",
            "🇮🇹" to "IT", "ITALY" to "IT",
            "🇪🇸" to "ES", "SPAIN" to "ES",
            "🇵🇱" to "PL", "POLAND" to "PL",
            "🇹🇷" to "TR", "TURKEY" to "TR", "TÜRKIYE" to "TR",
            "🇮🇩" to "ID", "INDONESIA" to "ID",
            "🇹🇭" to "TH", "THAILAND" to "TH",
            "🇻🇳" to "VN", "VIETNAM" to "VN",
            "🇵🇭" to "PH", "PHILIPPINES" to "PH",
            "🇲🇾" to "MY", "MALAYSIA" to "MY",
            "🇦🇪" to "AE", "UAE" to "AE", "DUBAI" to "AE",
            "🇫🇮" to "FI", "FINLAND" to "FI",
            "🇸🇪" to "SE", "SWEDEN" to "SE",
            "🇳🇴" to "NO", "NORWAY" to "NO",
            "🇩🇰" to "DK", "DENMARK" to "DK",
            "🇨🇭" to "CH", "SWITZERLAND" to "CH",
            "🇦🇹" to "AT", "AUSTRIA" to "AT",
            "🇧🇪" to "BE", "BELGIUM" to "BE",
            "🇮🇪" to "IE", "IRELAND" to "IE",
            "🇵🇹" to "PT", "PORTUGAL" to "PT",
            "🇬🇷" to "GR", "GREECE" to "GR",
            "🇨🇿" to "CZ", "CZECH" to "CZ",
            "🇷🇴" to "RO", "ROMANIA" to "RO",
            "🇺🇦" to "UA", "UKRAINE" to "UA",
            "🇿🇦" to "ZA", "SOUTH AFRICA" to "ZA",
            "🇲🇽" to "MX", "MEXICO" to "MX",
            "🇦🇷" to "AR", "ARGENTINA" to "AR",
            "🇨🇱" to "CL", "CHILE" to "CL",
            "🇨🇴" to "CO", "COLOMBIA" to "CO",
            "🇵🇪" to "PE", "PERU" to "PE"
        )
        
        for ((key, code) in countryMap) {
            if (name.contains(key) || upperName.contains(key)) {
                return code
            }
        }
        
        return "UN"
    }
}
