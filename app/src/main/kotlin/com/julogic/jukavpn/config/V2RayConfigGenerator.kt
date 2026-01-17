package com.julogic.jukavpn.config

import com.julogic.jukavpn.models.Protocol
import com.julogic.jukavpn.models.Server
import org.json.JSONArray
import org.json.JSONObject

object V2RayConfigGenerator {
    
    fun generate(server: Server, dnsServers: List<String> = listOf("8.8.8.8", "8.8.4.4")): String {
        val config = JSONObject()
        
        // Log settings
        config.put("log", JSONObject().apply {
            put("loglevel", "warning")
        })
        
        // DNS settings
        config.put("dns", JSONObject().apply {
            put("servers", JSONArray(dnsServers))
            put("queryStrategy", "UseIP")
        })
        
        // Inbounds
        config.put("inbounds", JSONArray().apply {
            put(createSocksInbound())
            put(createHttpInbound())
        })
        
        // Outbounds
        config.put("outbounds", JSONArray().apply {
            put(createMainOutbound(server))
            put(createDirectOutbound())
            put(createBlockOutbound())
        })
        
        // Routing
        config.put("routing", createRoutingRules())
        
        return config.toString(2)
    }
    
    private fun createSocksInbound(): JSONObject {
        return JSONObject().apply {
            put("tag", "socks-in")
            put("protocol", "socks")
            put("listen", "127.0.0.1")
            put("port", 10808)
            put("settings", JSONObject().apply {
                put("auth", "noauth")
                put("udp", true)
            })
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray().apply {
                    put("http")
                    put("tls")
                })
            })
        }
    }
    
    private fun createHttpInbound(): JSONObject {
        return JSONObject().apply {
            put("tag", "http-in")
            put("protocol", "http")
            put("listen", "127.0.0.1")
            put("port", 10809)
        }
    }
    
    private fun createMainOutbound(server: Server): JSONObject {
        return JSONObject().apply {
            put("tag", "proxy")
            put("protocol", getProtocolName(server.protocol))
            put("settings", createOutboundSettings(server))
            put("streamSettings", createStreamSettings(server))
            
            if (server.protocol == Protocol.VMESS || server.protocol == Protocol.VLESS) {
                put("mux", JSONObject().apply {
                    put("enabled", false)
                    put("concurrency", 8)
                })
            }
        }
    }
    
    private fun getProtocolName(protocol: Protocol): String {
        return when (protocol) {
            Protocol.VMESS -> "vmess"
            Protocol.VLESS -> "vless"
            Protocol.TROJAN -> "trojan"
            Protocol.SHADOWSOCKS -> "shadowsocks"
            else -> "vmess"
        }
    }
    
    private fun createOutboundSettings(server: Server): JSONObject {
        return when (server.protocol) {
            Protocol.VMESS -> createVmessSettings(server)
            Protocol.VLESS -> createVlessSettings(server)
            Protocol.TROJAN -> createTrojanSettings(server)
            Protocol.SHADOWSOCKS -> createShadowsocksSettings(server)
            else -> JSONObject()
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
                            put("id", server.uuid)
                            put("alterId", server.alterId ?: 0)
                            put("security", server.security ?: "auto")
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
                            put("id", server.uuid)
                            put("encryption", "none")
                            server.flow?.let { put("flow", it) }
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
                    put("password", server.password)
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
                    put("method", server.method)
                    put("password", server.password)
                })
            })
        }
    }
    
    private fun createStreamSettings(server: Server): JSONObject {
        return JSONObject().apply {
            put("network", server.network ?: "tcp")
            
            if (server.tls) {
                put("security", "tls")
                put("tlsSettings", JSONObject().apply {
                    server.sni?.let { if (it.isNotEmpty()) put("serverName", it) }
                    server.fingerprint?.let { if (it.isNotEmpty()) put("fingerprint", it) }
                    server.alpn?.let { 
                        if (it.isNotEmpty()) put("alpn", JSONArray(it.split(",")))
                    }
                    put("allowInsecure", false)
                })
            } else {
                put("security", "none")
            }
            
            when (server.network) {
                "ws" -> put("wsSettings", createWsSettings(server))
                "grpc" -> put("grpcSettings", createGrpcSettings(server))
                "tcp" -> if (server.headerType == "http") {
                    put("tcpSettings", createTcpHttpSettings(server))
                }
                "kcp" -> put("kcpSettings", createKcpSettings(server))
                "http", "h2" -> put("httpSettings", createHttpSettings(server))
            }
        }
    }
    
    private fun createWsSettings(server: Server): JSONObject {
        return JSONObject().apply {
            put("path", server.path ?: "/")
            put("headers", JSONObject().apply {
                server.host?.let { if (it.isNotEmpty()) put("Host", it) }
            })
        }
    }
    
    private fun createGrpcSettings(server: Server): JSONObject {
        return JSONObject().apply {
            put("serviceName", server.path ?: "")
            put("multiMode", false)
        }
    }
    
    private fun createTcpHttpSettings(server: Server): JSONObject {
        return JSONObject().apply {
            put("header", JSONObject().apply {
                put("type", "http")
                put("request", JSONObject().apply {
                    put("version", "1.1")
                    put("method", "GET")
                    put("path", JSONArray().apply { put(server.path ?: "/") })
                    put("headers", JSONObject().apply {
                        put("Host", JSONArray().apply { 
                            put(server.host ?: server.address) 
                        })
                        put("User-Agent", JSONArray().apply {
                            put("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        })
                        put("Accept-Encoding", JSONArray().apply {
                            put("gzip, deflate")
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
                put("type", server.headerType ?: "none")
            })
        }
    }
    
    private fun createHttpSettings(server: Server): JSONObject {
        return JSONObject().apply {
            put("host", JSONArray().apply {
                put(server.host ?: server.address)
            })
            put("path", server.path ?: "/")
        }
    }
    
    private fun createDirectOutbound(): JSONObject {
        return JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
            put("settings", JSONObject())
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
    
    private fun createRoutingRules(): JSONObject {
        return JSONObject().apply {
            put("domainStrategy", "IPIfNonMatch")
            put("rules", JSONArray().apply {
                // Block ads
                put(JSONObject().apply {
                    put("type", "field")
                    put("domain", JSONArray().apply {
                        put("geosite:category-ads-all")
                    })
                    put("outboundTag", "block")
                })
                
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
                    })
                    put("outboundTag", "direct")
                })
                
                // Proxy everything else
                put(JSONObject().apply {
                    put("type", "field")
                    put("port", "0-65535")
                    put("outboundTag", "proxy")
                })
            })
        }
    }
}
