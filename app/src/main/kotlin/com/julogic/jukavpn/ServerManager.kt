package com.julogic.jukavpn

data class Server(
    val name: String,
    val address: String,
    val port: Int,
    val uuid: String,
    val alterId: Int,
    val security: String = "auto"
)

object ServerManager {
    val servers = listOf(
        Server("Server 1", "example.com", 443, "uuid-example-1", 0),
        Server("Server 2", "example.org", 443, "uuid-example-2", 0)
    )
}