package com.julogic.jukavpn.parsers

import android.net.Uri
import android.util.Base64
import com.julogic.jukavpn.models.Protocol
import com.julogic.jukavpn.models.Server
import org.json.JSONObject

object SSHConfigParser {
    
    /**
     * Parse SSH configuration from various formats
     */
    fun parse(config: String): Server? {
        return when {
            config.startsWith("ssh://") -> parseUri(config)
            config.startsWith("{") -> parseJson(config)
            config.contains("Host ") -> parseOpenSSHConfig(config)
            else -> parseSimple(config)
        }
    }
    
    /**
     * Parse ssh:// URI format
     * ssh://user:password@host:port#name
     */
    private fun parseUri(uri: String): Server? {
        return try {
            val parsed = Uri.parse(uri)
            val userInfo = parsed.userInfo ?: return null
            val host = parsed.host ?: return null
            val port = parsed.port.takeIf { it > 0 } ?: 22
            val fragment = parsed.fragment ?: "SSH Server"
            
            val userPassword = userInfo.split(':')
            val user = userPassword.getOrNull(0) ?: return null
            val password = userPassword.getOrNull(1)
            
            Server(
                name = Uri.decode(fragment),
                address = host,
                port = port,
                protocol = Protocol.SSH,
                countryCode = extractCountryCode(fragment),
                countryName = "",
                sshUser = user,
                sshPassword = password,
                sshPort = port
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Parse JSON format SSH config
     */
    private fun parseJson(json: String): Server? {
        return try {
            val obj = JSONObject(json)
            
            Server(
                name = obj.optString("name", "SSH Server"),
                address = obj.getString("host"),
                port = obj.optInt("port", 22),
                protocol = Protocol.SSH,
                countryCode = obj.optString("country", "UN"),
                countryName = obj.optString("countryName", ""),
                sshUser = obj.getString("user"),
                sshPassword = obj.optString("password"),
                sshPrivateKey = obj.optString("privateKey"),
                sshPort = obj.optInt("port", 22)
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Parse OpenSSH config format
     */
    private fun parseOpenSSHConfig(config: String): Server? {
        return try {
            val lines = config.lines()
            var name = "SSH Server"
            var host = ""
            var user = ""
            var port = 22
            var identityFile = ""
            
            for (line in lines) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("Host ") -> name = trimmed.removePrefix("Host ").trim()
                    trimmed.startsWith("HostName ") -> host = trimmed.removePrefix("HostName ").trim()
                    trimmed.startsWith("User ") -> user = trimmed.removePrefix("User ").trim()
                    trimmed.startsWith("Port ") -> port = trimmed.removePrefix("Port ").trim().toIntOrNull() ?: 22
                    trimmed.startsWith("IdentityFile ") -> identityFile = trimmed.removePrefix("IdentityFile ").trim()
                }
            }
            
            if (host.isEmpty()) return null
            
            Server(
                name = name,
                address = host,
                port = port,
                protocol = Protocol.SSH,
                countryCode = extractCountryCode(name),
                countryName = "",
                sshUser = user,
                sshPort = port
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Parse simple format: user@host:port
     */
    private fun parseSimple(config: String): Server? {
        return try {
            val atIndex = config.indexOf('@')
            if (atIndex < 0) return null
            
            val user = config.substring(0, atIndex)
            val hostPort = config.substring(atIndex + 1)
            
            val colonIndex = hostPort.lastIndexOf(':')
            val host: String
            val port: Int
            
            if (colonIndex > 0) {
                host = hostPort.substring(0, colonIndex)
                port = hostPort.substring(colonIndex + 1).toIntOrNull() ?: 22
            } else {
                host = hostPort
                port = 22
            }
            
            Server(
                name = "SSH - $host",
                address = host,
                port = port,
                protocol = Protocol.SSH,
                countryCode = "UN",
                countryName = "",
                sshUser = user,
                sshPort = port
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Convert Server to JSON format
     */
    fun toJson(server: Server): String {
        return JSONObject().apply {
            put("name", server.name)
            put("host", server.address)
            put("port", server.sshPort ?: server.port)
            put("user", server.sshUser)
            server.sshPassword?.let { put("password", it) }
            server.sshPrivateKey?.let { put("privateKey", it) }
            put("country", server.countryCode)
            put("countryName", server.countryName)
        }.toString(2)
    }
    
    /**
     * Convert Server to ssh:// URI
     */
    fun toUri(server: Server): String {
        val auth = if (server.sshPassword != null) {
            "${server.sshUser}:${server.sshPassword}"
        } else {
            server.sshUser ?: ""
        }
        val port = server.sshPort ?: 22
        val fragment = Uri.encode(server.name)
        
        return "ssh://$auth@${server.address}:$port#$fragment"
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
