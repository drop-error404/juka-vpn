package com.julogic.jukavpn.parsers

import android.net.Uri
import android.util.Base64
import com.julogic.jukavpn.models.Protocol
import com.julogic.jukavpn.models.Server

object ShadowsocksParser {
    
    /**
     * Parse ss:// URI format
     * Format 1 (SIP002): ss://base64(method:password)@host:port#name
     * Format 2 (Legacy): ss://base64(method:password@host:port)#name
     */
    fun parse(uri: String): Server? {
        if (!uri.startsWith("ss://")) return null
        
        return try {
            parseSIP002(uri) ?: parseLegacy(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun parseSIP002(uri: String): Server? {
        return try {
            val withoutScheme = uri.removePrefix("ss://")
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
            
            val atIndex = mainPart.lastIndexOf('@')
            if (atIndex < 0) return null
            
            val userInfo = mainPart.substring(0, atIndex)
            val hostPort = mainPart.substring(atIndex + 1)
            
            val decoded = try {
                String(Base64.decode(userInfo, Base64.URL_SAFE or Base64.NO_PADDING))
            } catch (e: Exception) {
                String(Base64.decode(userInfo, Base64.DEFAULT))
            }
            
            val colonIndex = decoded.indexOf(':')
            if (colonIndex < 0) return null
            
            val method = decoded.substring(0, colonIndex)
            val password = decoded.substring(colonIndex + 1)
            
            val hostPortParts = hostPort.split(':')
            if (hostPortParts.size != 2) return null
            
            val host = hostPortParts[0]
            val port = hostPortParts[1].toIntOrNull() ?: return null
            
            Server(
                name = name,
                address = host,
                port = port,
                protocol = Protocol.SHADOWSOCKS,
                countryCode = extractCountryCode(name),
                countryName = "",
                method = method,
                password = password
            )
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseLegacy(uri: String): Server? {
        return try {
            val withoutScheme = uri.removePrefix("ss://")
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
            
            val decoded = String(Base64.decode(base64Part, Base64.DEFAULT))
            val atIndex = decoded.lastIndexOf('@')
            if (atIndex < 0) return null
            
            val methodPassword = decoded.substring(0, atIndex)
            val hostPort = decoded.substring(atIndex + 1)
            
            val colonIndex = methodPassword.indexOf(':')
            if (colonIndex < 0) return null
            
            val method = methodPassword.substring(0, colonIndex)
            val password = methodPassword.substring(colonIndex + 1)
            
            val hostPortParts = hostPort.split(':')
            if (hostPortParts.size != 2) return null
            
            val host = hostPortParts[0]
            val port = hostPortParts[1].toIntOrNull() ?: return null
            
            Server(
                name = name,
                address = host,
                port = port,
                protocol = Protocol.SHADOWSOCKS,
                countryCode = extractCountryCode(name),
                countryName = "",
                method = method,
                password = password
            )
        } catch (e: Exception) {
            null
        }
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
        val fragment = Uri.encode(server.name)
        
        return "ss://$encoded@${server.address}:${server.port}#$fragment"
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
