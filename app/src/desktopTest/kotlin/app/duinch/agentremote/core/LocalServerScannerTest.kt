package app.duinch.agentremote.core

import app.duinch.agentremote.features.connect.data.LocalServerScanner
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class LocalServerScannerTest {

    @Test
    fun testScanFindsRunningServer() = runBlocking {
        val scanner = LocalServerScanner()
        val results = scanner.scan()
        // Since local server is running on 8765, results should contain at least one server
        println("Discovered servers: $results")
        assertTrue(results.isNotEmpty(), "Scanner should find local server on 8765")
    }
}
