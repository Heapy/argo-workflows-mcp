package io.heapy.argo.workflows.mcp

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class MCPServerTest {
    @Test
    fun `server can be created with default config`() {
        val mcpServer = ArgoWorkflowsMCPServer(serverConfig, FakeArgoWorkflowsClient())
        val server = mcpServer.createServer()

        assertNotNull(server)
    }
}