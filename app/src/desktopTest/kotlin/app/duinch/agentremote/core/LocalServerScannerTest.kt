package app.duinch.agentremote.core

import app.duinch.agentremote.features.connect.data.LocalServerScanner
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertTrue

class LocalServerScannerTest {

    @Test
    fun testScanFindsRunningServer() = runBlocking {
        val server = try {
            val s = HttpServer.create(InetSocketAddress("127.0.0.1", 8765), 0)
            s.createContext("/api/status") { exchange ->
                val response = """{"online": true, "serverName": "TestHost", "port": 8765}"""
                val bytes = response.toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            s.start()
            s
        } catch (_: Exception) {
            null
        }

        try {
            val scanner = LocalServerScanner()
            val results = scanner.scan()
            println("Discovered servers: $results")
            assertTrue(results.isNotEmpty(), "Scanner should find local server on 8765")
        } finally {
            server?.stop(0)
        }
    }
}
