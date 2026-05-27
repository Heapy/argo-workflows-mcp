package io.heapy.argo.workflows.mcp

import io.heapy.argo.workflows.mcp.repository.ConnectionRecord
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
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

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

    @Test
    fun `active connection edits recreate cached client`() = runTest {
        val repos = createTestRepositories()
        val connectionId = repos.connectionRepo.create(
            testConnectionRecord(baseUrl = "https://argo-old.example.com"),
        )
        val createdFor = mutableListOf<ConnectionRecord>()
        val mcpServer = ArgoWorkflowsMCPServer(
            connectionRepo = repos.connectionRepo,
            settingsRepo = repos.settingsRepo,
            auditLogRepo = repos.auditLogRepo,
            clientFactory = { connection ->
                createdFor += connection
                FakeArgoWorkflowsClient()
            },
        )
        val server = mcpServer.createServer()
        val listWorkflows = server.tools["list_workflows"]
        assertNotNull(listWorkflows)

        val firstResult = listWorkflows?.handler?.invoke(
            UnusedClientConnection,
            CallToolRequest(
                CallToolRequestParams(
                    name = "list_workflows",
                    arguments = buildJsonObject {},
                ),
            ),
        )

        assertEquals(false, firstResult?.isError)
        assertEquals(listOf("https://argo-old.example.com"), createdFor.map { it.baseUrl })
        assertEquals(listOf("initial-token"), createdFor.map { it.bearerToken })

        val activeConnection = requireNotNull(repos.connectionRepo.findById(connectionId))
        repos.connectionRepo.update(
            connectionId,
            activeConnection.copy(
                baseUrl = "https://argo-new.example.com",
                bearerToken = "updated-token",
            ),
        )

        val secondResult = listWorkflows?.handler?.invoke(
            UnusedClientConnection,
            CallToolRequest(
                CallToolRequestParams(
                    name = "list_workflows",
                    arguments = buildJsonObject {},
                ),
            ),
        )

        assertEquals(false, secondResult?.isError)
        assertEquals(
            listOf("https://argo-old.example.com", "https://argo-new.example.com"),
            createdFor.map { it.baseUrl },
        )
        assertEquals(listOf("initial-token", "updated-token"), createdFor.map { it.bearerToken })
    }

    @Test
    fun `cron and template tools require active Argo connection`() = runTest {
        val repos = createTestRepositories()
        val mcpServer = ArgoWorkflowsMCPServer(
            connectionRepo = repos.connectionRepo,
            settingsRepo = repos.settingsRepo,
            auditLogRepo = repos.auditLogRepo,
            clientFactory = { FakeArgoWorkflowsClient() },
        )
        val server = mcpServer.createServer()

        val cronResult = server.tools["list_cron_workflows"]?.handler?.invoke(
            UnusedClientConnection,
            CallToolRequest(
                CallToolRequestParams(
                    name = "list_cron_workflows",
                    arguments = buildJsonObject {},
                ),
            ),
        )
        val templateResult = server.tools["list_workflow_templates"]?.handler?.invoke(
            UnusedClientConnection,
            CallToolRequest(
                CallToolRequestParams(
                    name = "list_workflow_templates",
                    arguments = buildJsonObject {},
                ),
            ),
        )

        assertEquals(true, cronResult?.isError)
        assertEquals(true, templateResult?.isError)
        assertTrue(
            cronResult?.content?.filterIsInstance<TextContent>()?.single()
                ?.text?.contains("No active Argo") == true,
        )
        assertTrue(
            templateResult?.content?.filterIsInstance<TextContent>()?.single()
                ?.text?.contains("No active Argo") == true,
        )
    }

    private fun testConnectionRecord(baseUrl: String): ConnectionRecord {
        val now = LocalDateTime.now()
        return ConnectionRecord(
            id = 0,
            name = "active",
            baseUrl = baseUrl,
            defaultNamespace = "default",
            authType = "bearer",
            bearerToken = "initial-token",
            username = null,
            password = null,
            insecureSkipTlsVerify = false,
            requestTimeoutSeconds = 30,
            tlsServerName = null,
            isActive = true,
            createdAt = now,
            updatedAt = now,
        )
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
