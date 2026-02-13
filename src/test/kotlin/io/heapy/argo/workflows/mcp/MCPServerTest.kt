package io.heapy.argo.workflows.mcp

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class MCPServerTest {
    @Test
    fun `server can be created with repositories`() {
        val repos = createTestRepositories()
        val mcpServer = ArgoWorkflowsMCPServer(
            connectionRepo = repos.connectionRepo,
            settingsRepo = repos.settingsRepo,
            auditLogRepo = repos.auditLogRepo,
            clientFactory = { FakeArgoWorkflowsClient() },
        )
        val server = mcpServer.createServer()
        assertNotNull(server)
    }
}
