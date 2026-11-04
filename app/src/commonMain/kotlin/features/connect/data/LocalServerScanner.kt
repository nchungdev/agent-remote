package app.duinch.agentremote.features.connect.data

import app.duinch.agentremote.features.connect.domain.model.DiscoveredServer
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LocalServerScanner(
    private val httpClient: HttpClient = HttpClient()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun scan(additionalHosts: List<String> = emptyList()): List<DiscoveredServer> = coroutineScope {
        val candidates = (listOf("127.0.0.1", "localhost", "10.0.2.2") + additionalHosts)
            .distinct()
            .filter { it.isNotBlank() }

        val tasks = candidates.map { host ->
            async { probeServer(host) }
        }

        tasks.awaitAll().filterNotNull().distinctBy { it.serverName + it.port }
    }

    private suspend fun probeServer(host: String, port: Int = 8765): DiscoveredServer? {
        val cleanHost = host.removePrefix("http://").removePrefix("https://").substringBefore(":")
        val url = "http://$cleanHost:$port/api/status"
        return withTimeoutOrNull(900L) {
            try {
                val response = httpClient.get(url)
                if (response.status != HttpStatusCode.OK) return@withTimeoutOrNull null
                val text = response.bodyAsText()
                val root = json.parseToJsonElement(text).jsonObject
                val isOnline = root["online"]?.jsonPrimitive?.booleanOrNull ?: false
                if (!isOnline) return@withTimeoutOrNull null

                val name = root["serverName"]?.jsonPrimitive?.content.orEmpty().ifBlank { "Agent Remote Host" }
                val lanIp = root["lanIp"]?.jsonPrimitive?.content.orEmpty()
                val tunnelUrl = root["tunnelUrl"]?.jsonPrimitive?.content.orEmpty()
                val actualPort = root["port"]?.jsonPrimitive?.intOrNull ?: port

                DiscoveredServer(
                    serverName = name,
                    host = cleanHost,
                    port = actualPort,
                    lanIp = lanIp,
                    tunnelUrl = tunnelUrl,
                    targetAddress = "$cleanHost:$actualPort"
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
