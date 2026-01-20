package com.julogic.jukavpn.config

import com.julogic.jukavpn.models.Protocol
import com.julogic.jukavpn.models.Server
import org.json.JSONArray
import org.json.JSONObject

/**
 * V2Ray Configuration Generator
 * Generates complete V2Ray JSON configuration for various protocols
 * Supports: VMess, VLESS, Trojan, Shadowsocks
 * Transports: TCP, WebSocket, gRPC, KCP, HTTP/2, QUIC
 * Security: TLS, Reality, XTLS
 */
object V2RayConfigGenerator {
    
    // Configuration constants
    private const val SOCKS_PORT = 10808
    private const val HTTP_PORT = 10809
    private const val DEFAULT_MTU = 9000
    
    data class ConfigOptions(
        val dnsServers: List<String> = listOf("8.8.8.8", "8.8.4.4"),
        val enableMux: Boolean = false,
        val muxConcurrency: Int = 8,
        val logLevel: String = "warning",
        val enableSniffing: Boolean = true,
        val bypassLan: Boolean = true,
        val enableUdp: Boolean = true,
        val routeMode: RouteMode = RouteMode.GLOBAL
    )
    
    enum class RouteMode {
        GLOBAL,      // Proxy all traffic
        BYPASS_LAN,  // Bypass local network
        BYPASS_CN,   // Bypass China (for users in CN)
        CUSTOM       // Custom routing rules
    }
    
    /**
     * Generate complete V2Ray configuration
     */
    fun generate(
        server: Server, 
        options: ConfigOptions = ConfigOptions()
    ): String {
        val config = JSONObject()
        
        // Log settings
        config.put("log", createLogSettings(options.logLevel))
        
        // DNS settings
        config.put("dns", createDnsSettings(options.dnsServers))
        
        // Inbounds
        config.put("inbounds", createInbounds(options))
        
        // Outbounds
        config.put("outbounds", createOutbounds(server, options))
        
        // Routing
        config.put("routing", createRouting(options))
        
        // Policy (optional performance tuning)
        config.put("policy", createPolicy())
        
        // Stats (for traffic monitoring)
        config.put("stats", JSONObject())
        
        return config.toString(2)
    }
    
    /**
     * Generate minimal configuration (for testing)
     */
    fun generateMinimal(server: Server): String {
        val config = JSONObject()
        
        config.put("log", JSONObject().apply {
            put("loglevel", "warning")
        })
        
        config.put("inbounds", JSONArray().apply {
            put(createSocksInbound(true, true))
        })
        
        config.put("outbounds", JSONArray().apply {
            put(createMainOutbound(server, ConfigOptions()))
        })
        
        return config.toString(2)
    }
    
    // ==================== Log Settings ====================
    
    private fun createLogSettings(level: String): JSONObject {
        return JSONObject().apply {
            put("loglevel", level)
            put("access", "")  // Empty = disable access log
            put("error", "")   // Empty = disable error log to file
        }
    }
    
    // ==================== DNS Settings ====================
    
    private fun createDnsSettings(dnsServers: List<String>): JSONObject {
        return JSONObject().apply {
            put("servers", JSONArray().apply {
                // Primary DNS servers
                dnsServers.forEach { put(it) }
                
                // Localhost for local queries
                put(JSONObject().apply {
                    put("address", "localhost")
                    put("domains", JSONArray().apply {
                        put("domain:localhost")
                    })
                })
            })
            put("queryStrategy", "UseIP")
            put("disableCache", false)
            put("disableFallback", false)
            put("tag", "dns-out")
        }
    }
    
    // ==================== Inbounds ====================
    
    private fun createInbounds(options: ConfigOptions): JSONArray {
        return JSONArray().apply {
            put(createSocksInbound(options.enableSniffing, options.enableUdp))
            put(createHttpInbound())
        }
    }
    
    private fun createSocksInbound(sniffing: Boolean, udp: Boolean): JSONObject {
        return JSONObject().apply {
            put("tag", "socks-in")
            put("protocol", "socks")
            put("listen", "127.0.0.1")
            put("port", SOCKS_PORT)
            put("settings", JSONObject().apply {
                put("auth", "noauth")
                put("udp", udp)
                put("userLevel", 0)
            })
            
            if (sniffing) {
                put("sniffing", JSONObject().apply {
                    put("enabled", true)
                    put("destOverride", JSONArray().apply {
                        put("http")
                        put("tls")
                        put("quic")
                    })
                    put("metadataOnly", false)
                    put("routeOnly", false)
                })
            }
        }
    }
    
    private fun createHttpInbound(): JSONObject {
        return JSONObject().apply {
            put("tag", "http-in")
            put("protocol", "http")
            put("listen", "127.0.0.1")
            put("port", HTTP_PORT)
            put("settings", JSONObject().apply {
                put("allowTransparent", false)
                put("userLevel", 0)
            })
        }
    }
    
    // ==================== Outbounds ====================
    
    private fun createOutbounds(server: Server, options: ConfigOptions): JSONArray {
        return JSONArray().apply {
            put(createMainOutbound(server, options))
            put(createDirectOutbound())
            put(createBlockOutbound())
            put(createDnsOutbound())
        }
    }
    
    private fun createMainOutbound(server: Server, options: ConfigOptions): JSONObject {
        return JSONObject().apply {
            put("tag", "proxy")
            put("protocol", getProtocolName(server.protocol))
            put("settings", createOutboundSettings(server))
            put("streamSettings", createStreamSettings(server))
            
            // Mux settings (not for Trojan with XTLS/Reality)
            if (options.enableMux && shouldEnableMux(server)) {
                put("mux", JSONObject().apply {
                    put("enabled", true)
                    put("concurrency", options.muxConcurrency)
                    put("xudpConcurrency", 8)
                    put("xudpProxyUDP443", "reject")
                })
            } else {
                put("mux", JSONObject().apply {
                    put("enabled", false)
                })
            }
        }
    }
    
    private fun shouldEnableMux(server: Server): Boolean {
        // Mux is not recommended for:
        // - XTLS/Reality (breaks the protocol)
        // - Trojan (may cause issues)
        // - UDP-heavy usage
        val hasXtls = server.flow?.isNotEmpty() == true
        val hasReality = server.realityPublicKey?.isNotEmpty() == true
        
        return !hasXtls && !hasReality && server.protocol != Protocol.TROJAN
    }
    
    private fun getProtocolName(protocol: Protocol): String {
        return when (protocol) {
            Protocol.VMESS -> "vmess"
            Protocol.VLESS -> "vless"
            Protocol.TROJAN -> "trojan"
            Protocol.SHADOWSOCKS -> "shadowsocks"
            else -> throw IllegalArgumentException("Unsupported protocol: $protocol")
        }
    }
    
    private fun createOutboundSettings(server: Server): JSONObject {
        return when (server.protocol) {
            Protocol.VMESS -> createVmessSettings(server)
            Protocol.VLESS -> createVlessSettings(server)
            Protocol.TROJAN -> createTrojanSettings(server)
            Protocol.SHADOWSOCKS -> createShadowsocksSettings(server)
            else -> throw IllegalArgumentException("Unsupported protocol: ${server.protocol}")
        }
    }
    
    private fun createVmessSettings(server: Server): JSONObject {
        return JSONObject().apply {
            put("vnext", JSONArray().apply {
                put(JSONObject().apply {
                    put("address", server.address)
                    put("port", server.port)
                    put("users", JSONArray().apply {
                        put(JSONObject().apply {
                            put("id", server.uuid ?: "")
                            put("alterId", server.alterId ?: 0)
                            put("security", server.security ?: "auto")
                            put("level", 0)
                        })
                    })
                })
            })
        }
    }
    
    private fun createVlessSettings(server: Server): JSONObject {
        return JSONObject().apply {
            put("vnext", JSONArray().apply {
                put(JSONObject().apply {
                    put("address", server.address)
                    put("port", server.port)
                    put("users", JSONArray().apply {
                        put(JSONObject().apply {
                            put("id", server.uuid ?: "")
                            put("encryption", "none")
                            put("level", 0)
                            // Flow for XTLS
                            server.flow?.takeIf { it.isNotEmpty() }?.let { 
                                put("flow", it) 
                            }
                        })
                    })
                })
            })
        }
    }
    
    private fun createTrojanSettings(server: Server): JSONObject {
        return JSONObject().apply {
            put("servers", JSONArray().apply {
                put(JSONObject().apply {
                    put("address", server.address)
                    put("port", server.port)
                    put("password", server.password ?: "")
                    put("level", 0)
                })
            })
        }
    }
    
    private fun createShadowsocksSettings(server: Server): JSONObject {
        return JSONObject().apply {
            put("servers", JSONArray().apply {
                put(JSONObject().apply {
                    put("address", server.address)
                    put("port", server.port)
                    put("method", server.method ?: "aes-256-gcm")
                    put("password", server.password ?: "")
                    put("level", 0)
                })
            })
        }
    }
    
    // ==================== Stream Settings ====================
    
    private fun createStreamSettings(server: Server): JSONObject {
        return JSONObject().apply {
            val network = server.network ?: "tcp"
            put("network", network)
            
            // Security settings
            when {
                server.realityPublicKey?.isNotEmpty() == true -> {
                    put("security", "reality")
                    put("realitySettings", createRealitySettings(server))
                }
                server.tls -> {
                    put("security", "tls")
                    put("tlsSettings", createTlsSettings(server))
                }
                else -> {
                    put("security", "none")
                }
            }
            
            // Network-specific settings
            when (network) {
                "ws", "websocket" -> put("wsSettings", createWsSettings(server))
                "grpc" -> put("grpcSettings", createGrpcSettings(server))
                "tcp" -> {
                    if (server.headerType == "http") {
                        put("tcpSettings", createTcpHttpSettings(server))
                    }
                }
                "kcp", "mkcp" -> put("kcpSettings", createKcpSettings(server))
                "http", "h2" -> put("httpSettings", createHttpSettings(server))
                "quic" -> put("quicSettings", createQuicSettings(server))
            }
            
            // Socket options
            put("sockopt", JSONObject().apply {
                put("mark", 255)
                put("tcpFastOpen", true)
                put("tproxy", "off")
                put("domainStrategy", "AsIs")
            })
        }
    }
    
    private fun createTlsSettings(server: Server): JSONObject {
        return JSONObject().apply {
            // Server name indication
            val sni = server.sni?.takeIf { it.isNotEmpty() } 
                ?: server.host?.takeIf { it.isNotEmpty() }
                ?: server.address
            put("serverName", sni)
            
            // Allow insecure (should be false in production)
            put("allowInsecure", false)
            
            // Fingerprint
            server.fingerprint?.takeIf { it.isNotEmpty() }?.let {
                put("fingerprint", it)
            } ?: put("fingerprint", "chrome")
            
            // ALPN
            server.alpn?.takeIf { it.isNotEmpty() }?.let { alpn ->
                put("alpn", JSONArray().apply {
                    alpn.split(",").forEach { put(it.trim()) }
                })
            } ?: put("alpn", JSONArray().apply {
                put("h2")
                put("http/1.1")
            })
            
            // Disable system root (use embedded certs)
            put("disableSystemRoot", false)
            
            // Enable session resumption
            put("enableSessionResumption", true)
        }
    }
    
    private fun createRealitySettings(server: Server): JSONObject {
        return JSONObject().apply {
            put("show", false)
            
            // Fingerprint (required for Reality)
            put("fingerprint", server.fingerprint ?: "chrome")
            
            // Server name
            val serverName = server.sni?.takeIf { it.isNotEmpty() }
                ?: server.host?.takeIf { it.isNotEmpty() }
                ?: "www.google.com"
            put("serverName", serverName)
            
            // Public key (required)
            put("publicKey", server.realityPublicKey ?: "")
            
            // Short ID
            put("shortId", server.realityShortId ?: "")
            
            // Spider X (optional path)
            server.spiderX?.takeIf { it.isNotEmpty() }?.let {
                put("spiderX", it)
            }
        }
    }
    
    private fun createWsSettings(server: Server): JSONObject {
        return JSONObject().apply {
            put("path", server.path?.takeIf { it.isNotEmpty() } ?: "/")
            put("headers", JSONObject().apply {
                server.host?.takeIf { it.isNotEmpty() }?.let {
                    put("Host", it)
                }
            })
            // Early data settings for better performance
            put("maxEarlyData", 2048)
            put("earlyDataHeaderName", "Sec-WebSocket-Protocol")
        }
    }
    
    private fun createGrpcSettings(server: Server): JSONObject {
        return JSONObject().apply {
            put("serviceName", server.path ?: "")
            put("multiMode", server.grpcMode == "multi")
            put("idle_timeout", 60)
            put("health_check_timeout", 20)
            put("permit_without_stream", false)
            put("initial_windows_size", 0)
        }
    }
    
    private fun createTcpHttpSettings(server: Server): JSONObject {
        return JSONObject().apply {
            put("header", JSONObject().apply {
                put("type", "http")
                put("request", JSONObject().apply {
                    put("version", "1.1")
                    put("method", "GET")
                    put("path", JSONArray().apply { 
                        put(server.path?.takeIf { it.isNotEmpty() } ?: "/") 
                    })
                    put("headers", JSONObject().apply {
                        put("Host", JSONArray().apply { 
                            put(server.host?.takeIf { it.isNotEmpty() } ?: server.address) 
                        })
                        put("User-Agent", JSONArray().apply {
                            put("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        })
                        put("Accept-Encoding", JSONArray().apply {
                            put("gzip, deflate")
                        })
                        put("Connection", JSONArray().apply {
                            put("keep-alive")
                        })
                        put("Pragma", "no-cache")
                    })
                })
                put("response", JSONObject().apply {
                    put("version", "1.1")
                    put("status", "200")
                    put("reason", "OK")
                    put("headers", JSONObject().apply {
                        put("Content-Type", JSONArray().apply {
                            put("application/octet-stream")
                        })
                        put("Transfer-Encoding", JSONArray().apply {
                            put("chunked")
                        })
                        put("Connection", JSONArray().apply {
                            put("keep-alive")
                        })
                    })
                })
            })
        }
    }
    
    private fun createKcpSettings(server: Server): JSONObject {
        return JSONObject().apply {
            put("mtu", 1350)
            put("tti", 50)
            put("uplinkCapacity", 12)
            put("downlinkCapacity", 100)
            put("congestion", false)
            put("readBufferSize", 2)
            put("writeBufferSize", 2)
            put("header", JSONObject().apply {
                put("type", server.headerType?.takeIf { it.isNotEmpty() } ?: "none")
            })
            put("seed", server.kcpSeed ?: "")
        }
    }
    
    private fun createHttpSettings(server: Server): JSONObject {
        return JSONObject().apply {
            put("host", JSONArray().apply {
                val host = server.host?.takeIf { it.isNotEmpty() } ?: server.address
                host.split(",").forEach { put(it.trim()) }
            })
            put("path", server.path?.takeIf { it.isNotEmpty() } ?: "/")
            put("read_idle_timeout", 10)
            put("health_check_timeout", 15)
        }
    }
    
    private fun createQuicSettings(server: Server): JSONObject {
        return JSONObject().apply {
            put("security", server.quicSecurity ?: "none")
            put("key", server.quicKey ?: "")
            put("header", JSONObject().apply {
                put("type", server.headerType?.takeIf { it.isNotEmpty() } ?: "none")
            })
        }
    }
    
    // ==================== Direct & Block Outbounds ====================
    
    private fun createDirectOutbound(): JSONObject {
        return JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
            put("settings", JSONObject().apply {
                put("domainStrategy", "AsIs")
            })
        }
    }
    
    private fun createBlockOutbound(): JSONObject {
        return JSONObject().apply {
            put("tag", "block")
            put("protocol", "blackhole")
            put("settings", JSONObject().apply {
                put("response", JSONObject().apply {
                    put("type", "http")
                })
            })
        }
    }
    
    private fun createDnsOutbound(): JSONObject {
        return JSONObject().apply {
            put("tag", "dns-out")
            put("protocol", "dns")
        }
    }
    
    // ==================== Routing ====================
    
    private fun createRouting(options: ConfigOptions): JSONObject {
        return JSONObject().apply {
            put("domainStrategy", "IPIfNonMatch")
            put("domainMatcher", "hybrid")
            put("rules", createRoutingRules(options))
        }
    }
    
    private fun createRoutingRules(options: ConfigOptions): JSONArray {
        return JSONArray().apply {
            // DNS hijacking rule
            put(JSONObject().apply {
                put("type", "field")
                put("inboundTag", JSONArray().apply {
                    put("socks-in")
                    put("http-in")
                })
                put("port", 53)
                put("outboundTag", "dns-out")
            })
            
            // Block ads
            put(JSONObject().apply {
                put("type", "field")
                put("domain", JSONArray().apply {
                    put("geosite:category-ads-all")
                })
                put("outboundTag", "block")
            })
            
            // Block private trackers
            put(JSONObject().apply {
                put("type", "field")
                put("domain", JSONArray().apply {
                    put("geosite:google-ads")
                    put("geosite:facebook-ads")
                })
                put("outboundTag", "block")
            })
            
            if (options.bypassLan) {
                // Direct for private IPs
                put(JSONObject().apply {
                    put("type", "field")
                    put("ip", JSONArray().apply {
                        put("geoip:private")
                    })
                    put("outboundTag", "direct")
                })
                
                // Direct for local network
                put(JSONObject().apply {
                    put("type", "field")
                    put("ip", JSONArray().apply {
                        put("127.0.0.0/8")
                        put("10.0.0.0/8")
                        put("172.16.0.0/12")
                        put("192.168.0.0/16")
                        put("::1/128")
                        put("fc00::/7")
                        put("fe80::/10")
                    })
                    put("outboundTag", "direct")
                })
                
                // Direct for localhost domains
                put(JSONObject().apply {
                    put("type", "field")
                    put("domain", JSONArray().apply {
                        put("localhost")
                        put("domain:local")
                        put("domain:localhost")
                        put("domain:lan")
                    })
                    put("outboundTag", "direct")
                })
            }
            
            // Proxy everything else
            put(JSONObject().apply {
                put("type", "field")
                put("port", "0-65535")
                put("outboundTag", "proxy")
            })
        }
    }
    
    // ==================== Policy ====================
    
    private fun createPolicy(): JSONObject {
        return JSONObject().apply {
            put("levels", JSONObject().apply {
                put("0", JSONObject().apply {
                    put("handshake", 4)
                    put("connIdle", 300)
                    put("uplinkOnly", 1)
                    put("downlinkOnly", 1)
                    put("statsUserUplink", true)
                    put("statsUserDownlink", true)
                    put("bufferSize", 4)
                })
            })
            put("system", JSONObject().apply {
                put("statsInboundUplink", true)
                put("statsInboundDownlink", true)
                put("statsOutboundUplink", true)
                put("statsOutboundDownlink", true)
            })
        }
    }
    
    // ==================== Utility Functions ====================
    
    /**
     * Validate server configuration
     */
    fun validateServer(server: Server): ValidationResult {
        val errors = mutableListOf<String>()
        
        if (server.address.isBlank()) {
            errors.add("Server address is required")
        }
        
        if (server.port !in 1..65535) {
            errors.add("Invalid port number: ${server.port}")
        }
        
        when (server.protocol) {
            Protocol.VMESS, Protocol.VLESS -> {
                if (server.uuid.isNullOrBlank()) {
                    errors.add("UUID is required for ${server.protocol}")
                }
            }
            Protocol.TROJAN, Protocol.SHADOWSOCKS -> {
                if (server.password.isNullOrBlank()) {
                    errors.add("Password is required for ${server.protocol}")
                }
            }
            else -> errors.add("Unsupported protocol: ${server.protocol}")
        }
        
        if (server.protocol == Protocol.SHADOWSOCKS && server.method.isNullOrBlank()) {
            errors.add("Encryption method is required for Shadowsocks")
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }
    
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val errors: List<String>) : ValidationResult()
    }
    
    /**
     * Get configuration as pretty-printed JSON
     */
    fun generatePretty(server: Server, options: ConfigOptions = ConfigOptions()): String {
        return generate(server, options)
    }
    
    /**
     * Get configuration as compact JSON (for file size optimization)
     */
    fun generateCompact(server: Server, options: ConfigOptions = ConfigOptions()): String {
        val config = JSONObject(generate(server, options))
        return config.toString()
    }
}
