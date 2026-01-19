package com.julogic.jukavpn.parsers

import android.net.Uri
import android.util.Base64
import com.julogic.jukavpn.models.Protocol
import com.julogic.jukavpn.models.Server
import org.json.JSONObject

/**
 * Parser completo para protocolo VMess
 * Suporta formato padrão vmess://base64(json)
 * Compatível com v2rayN, Clash e outros clientes
 */
object VmessParser {
    
    private val SUPPORTED_NETWORKS = setOf("tcp", "ws", "kcp", "http", "quic", "grpc", "h2")
    private val SUPPORTED_SECURITY = setOf("auto", "aes-128-gcm", "chacha20-poly1305", "none", "zero")
    private val SUPPORTED_HEADER_TYPES = setOf("none", "http", "srtp", "utp", "wechat-video", "dtls", "wireguard")
    
    /**
     * Parse vmess:// URI format
     * vmess://base64encodedJson
     */
    fun parse(uri: String): Server? {
        if (!uri.lowercase().startsWith("vmess://")) return null
        
        return try {
            val base64Part = uri.substring(8) // Remove "vmess://"
            val decoded = decodeBase64(base64Part)
            parseJsonConfig(decoded)
        } catch (e: Exception) {
            e.printStackTrace()
            null
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
        
        return try {
            // Tenta URL_SAFE primeiro
            String(Base64.decode(cleaned, Base64.URL_SAFE or Base64.NO_PADDING))
        } catch (e: Exception) {
            try {
                // Fallback para DEFAULT
                String(Base64.decode(cleaned, Base64.DEFAULT))
            } catch (e2: Exception) {
                // Tenta adicionar padding
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
     * Parse JSON config do VMess
     */
    private fun parseJsonConfig(json: String): Server {
        val obj = JSONObject(json)
        
        // Campos obrigatórios
        val address = obj.getString("add").trim()
        val uuid = obj.getString("id").trim()
        
        // Validação do UUID
        require(uuid.isNotEmpty()) { "UUID cannot be empty" }
        require(address.isNotEmpty()) { "Address cannot be empty" }
        
        // Campos opcionais com valores padrão
        val port = parsePort(obj.optString("port", "443"))
        val name = obj.optString("ps", "").ifEmpty { "VMess $address" }
        val alterId = obj.optString("aid", "0").toIntOrNull() ?: 0
        val security = obj.optString("scy", "auto").ifEmpty { "auto" }
        val network = obj.optString("net", "tcp").ifEmpty { "tcp" }.lowercase()
        val headerType = obj.optString("type", "none").ifEmpty { "none" }
        val host = obj.optString("host", "")
        val path = obj.optString("path", "")
        val tlsStr = obj.optString("tls", "")
        val sni = obj.optString("sni", "")
        val fingerprint = obj.optString("fp", "")
        val alpn = obj.optString("alpn", "")
        
        // Determina TLS
        val tls = tlsStr.equals("tls", ignoreCase = true) || 
                  tlsStr.equals("xtls", ignoreCase = true)
        
        // Extrai SNI do host se não especificado
        val effectiveSni = when {
            sni.isNotEmpty() -> sni
            host.isNotEmpty() && !host.contains(",") -> host
            else -> ""
        }
        
        return Server(
            name = Uri.decode(name),
            address = address,
            port = port,
            protocol = Protocol.VMESS,
            countryCode = extractCountryCode(name),
            countryName = "",
            uuid = uuid,
            alterId = alterId,
            security = normalizeSecurityMethod(security),
            network = normalizeNetwork(network),
            headerType = normalizeHeaderType(headerType),
            host = host,
            path = normalizePath(path, network),
            tls = tls,
            sni = effectiveSni,
            fingerprint = fingerprint.ifEmpty { null },
            alpn = alpn.ifEmpty { null }
        )
    }
    
    /**
     * Parse porta com validação
     */
    private fun parsePort(portStr: String): Int {
        val port = portStr.trim().toIntOrNull() ?: 443
        require(port in 1..65535) { "Invalid port: $port" }
        return port
    }
    
    /**
     * Normaliza método de segurança
     */
    private fun normalizeSecurityMethod(security: String): String {
        val normalized = security.lowercase().trim()
        return if (normalized in SUPPORTED_SECURITY) normalized else "auto"
    }
    
    /**
     * Normaliza tipo de rede
     */
    private fun normalizeNetwork(network: String): String {
        val normalized = network.lowercase().trim()
        return when (normalized) {
            "websocket" -> "ws"
            "http2", "h2" -> "h2"
            "grpc" -> "grpc"
            else -> if (normalized in SUPPORTED_NETWORKS) normalized else "tcp"
        }
    }
    
    /**
     * Normaliza header type
     */
    private fun normalizeHeaderType(headerType: String): String {
        val normalized = headerType.lowercase().trim()
        return if (normalized in SUPPORTED_HEADER_TYPES) normalized else "none"
    }
    
    /**
     * Normaliza path baseado no tipo de rede
     */
    private fun normalizePath(path: String, network: String): String {
        return when (network) {
            "ws", "h2" -> if (path.isEmpty()) "/" else path
            "grpc" -> path // serviceName para gRPC
            else -> path
        }
    }
    
    /**
     * Convert Server to vmess:// URI
     */
    fun toUri(server: Server): String {
        val json = JSONObject().apply {
            put("v", "2")
            put("ps", server.name)
            put("add", server.address)
            put("port", server.port.toString())
            put("id", server.uuid ?: "")
            put("aid", server.alterId?.toString() ?: "0")
            put("scy", server.security ?: "auto")
            put("net", server.network ?: "tcp")
            put("type", server.headerType ?: "none")
            put("host", server.host ?: "")
            put("path", server.path ?: "")
            put("tls", if (server.tls) "tls" else "")
            put("sni", server.sni ?: "")
            put("fp", server.fingerprint ?: "")
            put("alpn", server.alpn ?: "")
        }
        
        val encoded = Base64.encodeToString(json.toString().toByteArray(), Base64.NO_WRAP)
        return "vmess://$encoded"
    }
    
    /**
     * Valida se o servidor VMess está configurado corretamente
     */
    fun validate(server: Server): Boolean {
        return server.protocol == Protocol.VMESS &&
               !server.uuid.isNullOrEmpty() &&
               server.address.isNotEmpty() &&
               server.port in 1..65535
    }
    
    /**
     * Gera configuração JSON para V2Ray core
     */
    fun toV2RayConfig(server: Server): JSONObject {
        return JSONObject().apply {
            put("vnext", org.json.JSONArray().put(JSONObject().apply {
                put("address", server.address)
                put("port", server.port)
                put("users", org.json.JSONArray().put(JSONObject().apply {
                    put("id", server.uuid)
                    put("alterId", server.alterId ?: 0)
                    put("security", server.security ?: "auto")
                    put("level", 0)
                }))
            }))
        }
    }
    
    private fun extractCountryCode(name: String): String {
        val countryPatterns = listOf(
            Regex("\\[([A-Z]{2})\\]"),
            Regex("\\(([A-Z]{2})\\)"),
            Regex("^([A-Z]{2})[-_\\s]"),
            Regex("[-_\\s]([A-Z]{2})$"),
            Regex("🇺🇸|USA|United States" to "US"),
            Regex("🇬🇧|UK|United Kingdom" to "GB"),
            Regex("🇩🇪|Germany|Deutschland" to "DE"),
            Regex("🇫🇷|France" to "FR"),
            Regex("🇯🇵|Japan" to "JP"),
            Regex("🇸🇬|Singapore" to "SG"),
            Regex("🇭🇰|Hong Kong" to "HK"),
            Regex("🇰🇷|Korea" to "KR"),
            Regex("🇳🇱|Netherlands" to "NL"),
            Regex("🇨🇦|Canada" to "CA"),
            Regex("🇦🇺|Australia" to "AU"),
            Regex("🇧🇷|Brazil" to "BR"),
            Regex("🇷🇺|Russia" to "RU"),
            Regex("🇮🇳|India" to "IN"),
            Regex("🇹🇼|Taiwan" to "TW")
        )
        
        val upperName = name.uppercase()
        
        // Tenta padrões simples primeiro
        for (pattern in countryPatterns.take(4)) {
            pattern.find(upperName)?.let {
                return it.groupValues[1]
            }
        }
        
        // Tenta emojis e nomes de países
        val countryMap = mapOf(
            "🇺🇸" to "US", "USA" to "US", "UNITED STATES" to "US",
            "🇬🇧" to "GB", "UK" to "GB", "UNITED KINGDOM" to "GB",
            "🇩🇪" to "DE", "GERMANY" to "DE", "DEUTSCHLAND" to "DE",
            "🇫🇷" to "FR", "FRANCE" to "FR",
            "🇯🇵" to "JP", "JAPAN" to "JP",
            "🇸🇬" to "SG", "SINGAPORE" to "SG",
            "🇭🇰" to "HK", "HONG KONG" to "HK", "HONGKONG" to "HK",
            "🇰🇷" to "KR", "KOREA" to "KR",
            "🇳🇱" to "NL", "NETHERLANDS" to "NL",
            "🇨🇦" to "CA", "CANADA" to "CA",
            "🇦🇺" to "AU", "AUSTRALIA" to "AU",
            "🇧🇷" to "BR", "BRAZIL" to "BR",
            "🇷🇺" to "RU", "RUSSIA" to "RU",
            "🇮🇳" to "IN", "INDIA" to "IN",
            "🇹🇼" to "TW", "TAIWAN" to "TW",
            "🇮🇹" to "IT", "ITALY" to "IT",
            "🇪🇸" to "ES", "SPAIN" to "ES",
            "🇵🇱" to "PL", "POLAND" to "PL",
            "🇹🇷" to "TR", "TURKEY" to "TR",
            "🇮🇩" to "ID", "INDONESIA" to "ID",
            "🇹🇭" to "TH", "THAILAND" to "TH",
            "🇻🇳" to "VN", "VIETNAM" to "VN",
            "🇵🇭" to "PH", "PHILIPPINES" to "PH",
            "🇲🇾" to "MY", "MALAYSIA" to "MY"
        )
        
        for ((key, code) in countryMap) {
            if (name.contains(key) || upperName.contains(key)) {
                return code
            }
        }
        
        return "UN"
    }
}
