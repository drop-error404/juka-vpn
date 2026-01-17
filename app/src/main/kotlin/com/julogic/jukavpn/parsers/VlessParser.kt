package com.julogic.jukavpn.parsers

import android.net.Uri
import com.julogic.jukavpn.models.Protocol
import com.julogic.jukavpn.models.Server

object VlessParser {
    
    /**
     * Parse vless:// URI format
     * vless://uuid@host:port?parameters#name
     */
    fun parse(uri: String): Server? {
        if (!uri.startsWith("vless://")) return null
        
        return try {
            val parsed = Uri.parse(uri)
            val userInfo = parsed.userInfo ?: return null
            val host = parsed.host ?: return null
            val port = parsed.port.takeIf { it > 0 } ?: 443
            val fragment = parsed.fragment ?: "VLESS Server"
            
            Server(
                name = Uri.decode(fragment),
                address = host,
                port = port,
                protocol = Protocol.VLESS,
                countryCode = extractCountryCode(fragment),
                countryName = "",
                uuid = userInfo,
                security = parsed.getQueryParameter("security") ?: "none",
                network = parsed.getQueryParameter("type") ?: "tcp",
                headerType = parsed.getQueryParameter("headerType"),
                host = parsed.getQueryParameter("host"),
                path = parsed.getQueryParameter("path"),
                tls = parsed.getQueryParameter("security") == "tls" || 
                      parsed.getQueryParameter("security") == "reality",
                sni = parsed.getQueryParameter("sni"),
                fingerprint = parsed.getQueryParameter("fp"),
                alpn = parsed.getQueryParameter("alpn"),
                flow = parsed.getQueryParameter("flow")
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Convert Server to vless:// URI
     */
    fun toUri(server: Server): String {
        val params = mutableListOf<String>()
        
        server.security?.let { params.add("security=$it") }
        server.network?.let { params.add("type=$it") }
        server.headerType?.let { params.add("headerType=$it") }
        server.host?.let { if (it.isNotEmpty()) params.add("host=$it") }
        server.path?.let { if (it.isNotEmpty()) params.add("path=${Uri.encode(it)}") }
        server.sni?.let { if (it.isNotEmpty()) params.add("sni=$it") }
        server.fingerprint?.let { if (it.isNotEmpty()) params.add("fp=$it") }
        server.alpn?.let { if (it.isNotEmpty()) params.add("alpn=$it") }
        server.flow?.let { if (it.isNotEmpty()) params.add("flow=$it") }
        
        val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        val fragment = Uri.encode(server.name)
        
        return "vless://${server.uuid}@${server.address}:${server.port}$queryString#$fragment"
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
