package com.julogic.jukavpn.models

import java.io.Serializable

enum class Protocol {
    VMESS,
    VLESS,
    TROJAN,
    SHADOWSOCKS,
    SSH,
    UDP
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

data class Server(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val address: String,
    val port: Int,
    val protocol: Protocol,
    val countryCode: String,  // ISO 3166-1 alpha-2 (ex: "BR", "US", "DE")
    val countryName: String,
    
    // V2Ray specific
    val uuid: String? = null,
    val alterId: Int? = null,
    val security: String? = null,
    val network: String? = null,  // tcp, ws, kcp, http, quic, grpc
    val headerType: String? = null,
    val host: String? = null,
    val path: String? = null,
    val tls: Boolean = false,
    val sni: String? = null,
    val fingerprint: String? = null,
    val alpn: String? = null,
    val flow: String? = null,  // VLESS flow
    
    // Shadowsocks specific
    val method: String? = null,  // encryption method
    val password: String? = null,
    
    // SSH specific
    val sshUser: String? = null,
    val sshPassword: String? = null,
    val sshPrivateKey: String? = null,
    val sshPort: Int? = 22,
    
    // UDP specific
    val udpPort: Int? = null,
    val obfs: String? = null,
    val obfsParam: String? = null,
    
    // Metadata
    val latency: Long = -1,
    val isFavorite: Boolean = false,
    val isPremium: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null
) : Serializable {
    
    fun getDisplayAddress(): String = "$address:$port"
    
    fun getProtocolDisplayName(): String = when(protocol) {
        Protocol.VMESS -> "VMess"
        Protocol.VLESS -> "VLESS"
        Protocol.TROJAN -> "Trojan"
        Protocol.SHADOWSOCKS -> "Shadowsocks"
        Protocol.SSH -> "SSH Tunnel"
        Protocol.UDP -> "UDP"
    }
    
    companion object {
        fun fromVmessUri(uri: String): Server? {
            return try {
                VmessParser.parse(uri)
            } catch (e: Exception) {
                null
            }
        }
        
        fun fromVlessUri(uri: String): Server? {
            return try {
                VlessParser.parse(uri)
            } catch (e: Exception) {
                null
            }
        }
        
        fun fromShadowsocksUri(uri: String): Server? {
            return try {
                ShadowsocksParser.parse(uri)
            } catch (e: Exception) {
                null
            }
        }
        
        fun fromTrojanUri(uri: String): Server? {
            return try {
                TrojanParser.parse(uri)
            } catch (e: Exception) {
                null
            }
        }
    }
}
