package app.duinch.agentremote.features.connect.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DiscoveredServer(
    val serverName: String,
    val host: String,
    val port: Int = 8765,
    val lanIp: String = "",
    val tunnelUrl: String = "",
    val targetAddress: String = "$host:$port"
)
