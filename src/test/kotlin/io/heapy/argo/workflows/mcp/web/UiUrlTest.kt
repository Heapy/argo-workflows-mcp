package io.heapy.argo.workflows.mcp.web

import io.ktor.server.engine.EngineConnectorBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UiUrlTest {
    @Test
    fun `ui url is built from ktor connector`() {
        val connector = EngineConnectorBuilder().apply {
            host = "localhost"
            port = 9090
        }

        assertEquals("http://localhost:9090/connections", connector.uiUrl())
    }
}
