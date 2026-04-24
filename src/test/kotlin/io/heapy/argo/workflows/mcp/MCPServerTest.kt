package io.heapy.argo.workflows.mcp

import io.modelcontextprotocol.kotlin.sdk.server.ClientConnection
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageResult
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequest
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ElicitResult
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.ElicitationCompleteNotification
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsResult
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ServerNotification
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `add connection tool creates and activates connection`() = runTest {
        val repos = createTestRepositories()
        val mcpServer = ArgoWorkflowsMCPServer(
            connectionRepo = repos.connectionRepo,
            settingsRepo = repos.settingsRepo,
            auditLogRepo = repos.auditLogRepo,
            clientFactory = { FakeArgoWorkflowsClient() },
        )
        val server = mcpServer.createServer()
        val addConnection = server.tools["add_connection"]
        assertNotNull(addConnection)

        val result = addConnection?.handler?.invoke(
            UnusedClientConnection,
            CallToolRequest(
                CallToolRequestParams(
                    name = "add_connection",
                    arguments = buildJsonObject {
                        put("name", "local")
                        put("base_url", "http://localhost:2746")
                        put("default_namespace", "argo")
                        put("auth_type", "bearer")
                        put("bearer_token", "secret-token")
                        put("activate", true)
                    },
                ),
            ),
        )

        assertEquals(false, result?.isError)
        val activeConnection = repos.connectionRepo.findActive()
        assertEquals("local", activeConnection?.name)
        assertEquals("http://localhost:2746", activeConnection?.baseUrl)
        assertEquals("argo", activeConnection?.defaultNamespace)
        assertEquals("bearer", activeConnection?.authType)
        assertEquals("secret-token", activeConnection?.bearerToken)
    }

    @Test
    fun `add connection tool redacts sensitive audit arguments`() = runTest {
        val repos = createTestRepositories()
        val mcpServer = ArgoWorkflowsMCPServer(
            connectionRepo = repos.connectionRepo,
            settingsRepo = repos.settingsRepo,
            auditLogRepo = repos.auditLogRepo,
            clientFactory = { FakeArgoWorkflowsClient() },
        )
        val server = mcpServer.createServer()
        val addConnection = server.tools["add_connection"]
        assertNotNull(addConnection)

        addConnection?.handler?.invoke(
            UnusedClientConnection,
            CallToolRequest(
                CallToolRequestParams(
                    name = "add_connection",
                    arguments = buildJsonObject {
                        put("name", "secure")
                        put("base_url", "https://argo.example.com")
                        put("auth_type", "bearer")
                        put("bearer_token", "secret-token")
                    },
                ),
            ),
        )

        val auditRecord = repos.auditLogRepo.findAll().single()
        assertEquals("add_connection", auditRecord.toolName)
        assertTrue(auditRecord.arguments.contains("bearer_token=[REDACTED]"))
        assertFalse(auditRecord.arguments.contains("secret-token"))
    }

    private object UnusedClientConnection : ClientConnection {
        override val sessionId: String = "test"

        override suspend fun notification(
            notification: ServerNotification,
            relatedRequestId: RequestId?,
        ) = Unit

        override suspend fun ping(
            request: PingRequest,
            options: RequestOptions?,
        ): EmptyResult = unused()

        override suspend fun createMessage(
            request: CreateMessageRequest,
            options: RequestOptions?,
        ): CreateMessageResult = unused()

        override suspend fun listRoots(
            request: ListRootsRequest,
            options: RequestOptions?,
        ): ListRootsResult = unused()

        override suspend fun createElicitation(
            message: String,
            requestedSchema: ElicitRequestParams.RequestedSchema,
            options: RequestOptions?,
        ): ElicitResult = unused()

        override suspend fun createElicitation(
            message: String,
            elicitationId: String,
            url: String,
            options: RequestOptions?,
        ): ElicitResult = unused()

        override suspend fun createElicitation(
            request: ElicitRequest,
            options: RequestOptions?,
        ): ElicitResult = unused()

        override suspend fun sendLoggingMessage(notification: LoggingMessageNotification) = Unit

        override suspend fun sendResourceUpdated(notification: ResourceUpdatedNotification) = Unit

        override suspend fun sendResourceListChanged() = Unit

        override suspend fun sendToolListChanged() = Unit

        override suspend fun sendPromptListChanged() = Unit

        override suspend fun sendElicitationComplete(notification: ElicitationCompleteNotification) = Unit

        private fun unused(): Nothing = error("Test connection should not be used")
    }
}
