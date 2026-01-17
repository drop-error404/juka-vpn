package com.julogic.jukavpn.parsers

import android.net.Uri
import com.julogic.jukavpn.models.Protocol
import com.julogic.jukavpn.models.Server

object TrojanParser {
    
    /**
     * Parse trojan:// URI format
     * trojan://password@host:port?parameters#name
     */
    fun parse(uri: String): Server? {
        if (!uri.startsWith("trojan://")) return null
        
        return try {
            val parsed = Uri.parse(uri)
            val password = parsed.userInfo ?: return null
            val host = parsed.host ?: return null
            val port = parsed.port.takeIf { it > 0 } ?: 443
            val fragment = parsed.fragment ?: "Trojan Server"
            
            Server(
                name = Uri.decode(fragment),
                address = host,
                port = port,
                protocol = Protocol.TROJAN,
                countryCode = extractCountryCode(fragment),
                countryName = "",
                password = password,
                security = parsed.getQueryParameter("security") ?: "tls",
                network = parsed.getQueryParameter("type") ?: "tcp",
                host = parsed.getQueryParameter("host"),
                path = parsed.getQueryParameter("path"),
                tls = true,
                sni = parsed.getQueryParameter("sni") ?: host,
                fingerprint = parsed.getQueryParameter("fp"),
                alpn = parsed.getQueryParameter("alpn")
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Convert Server to trojan:// URI
     */
    fun toUri(server: Server): String {
        val params = mutableListOf<String>()
        
        server.security?.let { params.add("security=$it") }
        server.network?.let { if (it != "tcp") params.add("type=$it") }
        server.host?.let { if (it.isNotEmpty()) params.add("host=$it") }
        server.path?.let { if (it.isNotEmpty()) params.add("path=${Uri.encode(it)}") }
        server.sni?.let { if (it.isNotEmpty() && it != server.address) params.add("sni=$it") }
        server.fingerprint?.let { if (it.isNotEmpty()) params.add("fp=$it") }
        server.alpn?.let { if (it.isNotEmpty()) params.add("alpn=$it") }
        
        val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        val fragment = Uri.encode(server.name)
        
        return "trojan://${server.password}@${server.address}:${server.port}$queryString#$fragment"
    }
    
    private fun extractCountryCode(name: String): String {
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
        
        return "UN"
    }
}
