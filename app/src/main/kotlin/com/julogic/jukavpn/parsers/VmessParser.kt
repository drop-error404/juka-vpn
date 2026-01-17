package com.julogic.jukavpn.parsers

import android.util.Base64
import com.julogic.jukavpn.models.Protocol
import com.julogic.jukavpn.models.Server
import org.json.JSONObject

object VmessParser {
    
    /**
     * Parse vmess:// URI format
     * vmess://base64encodedJson
     */
    fun parse(uri: String): Server? {
        if (!uri.startsWith("vmess://")) return null
        
        return try {
            val base64Part = uri.removePrefix("vmess://")
            val decoded = String(Base64.decode(base64Part, Base64.DEFAULT))
            val json = JSONObject(decoded)
            
            Server(
                name = json.optString("ps", "VMess Server"),
                address = json.getString("add"),
                port = json.optString("port", "443").toIntOrNull() ?: 443,
                protocol = Protocol.VMESS,
                countryCode = extractCountryCode(json.optString("ps", "")),
                countryName = "",
                uuid = json.getString("id"),
                alterId = json.optString("aid", "0").toIntOrNull() ?: 0,
                security = json.optString("scy", "auto"),
                network = json.optString("net", "tcp"),
                headerType = json.optString("type", "none"),
                host = json.optString("host", ""),
                path = json.optString("path", ""),
                tls = json.optString("tls", "") == "tls",
                sni = json.optString("sni", ""),
                fingerprint = json.optString("fp", ""),
                alpn = json.optString("alpn", "")
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
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
            put("id", server.uuid)
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
    
    private fun extractCountryCode(name: String): String {
        // Try to extract country code from server name
        val countryPatterns = listOf(
            Regex("\\[([A-Z]{2})\\]"),
            Regex("\\(([A-Z]{2})\\)"),
            Regex("^([A-Z]{2})[-_]"),
            Regex("[-_]([A-Z]{2})$")
        )
        
        for (pattern in countryPatterns) {
            pattern.find(name.uppercase())?.let {
                return it.groupValues[1]
            }
        }
        
        return "UN" // Unknown
    }
}
