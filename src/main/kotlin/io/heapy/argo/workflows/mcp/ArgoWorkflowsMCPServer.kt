package io.heapy.argo.workflows.mcp

import io.heapy.argo.client.ArgoAuthConfig
import io.heapy.argo.client.ArgoClientConfig
import io.heapy.argo.client.ArgoWorkflowsClient
import io.heapy.argo.client.ArgoWorkflowsHttpClient
import io.heapy.argo.workflows.mcp.operations.CronWorkflowOperations
import io.heapy.argo.workflows.mcp.operations.OperationResult
import io.heapy.argo.workflows.mcp.operations.TemplateOperations
import io.heapy.argo.workflows.mcp.operations.WorkflowOperations
import io.heapy.argo.workflows.mcp.repository.AuditLogRecord
import io.heapy.argo.workflows.mcp.repository.AuditLogRepository
import io.heapy.argo.workflows.mcp.repository.ConnectionRecord
import io.heapy.argo.workflows.mcp.repository.ConnectionRepository
import io.heapy.argo.workflows.mcp.repository.SettingsRepository
import io.heapy.komok.tech.logging.Logger
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.Closeable
import java.time.LocalDateTime

@Suppress("TooManyFunctions", "LabeledExpression")
class ArgoWorkflowsMCPServer(
    private val connectionRepo: ConnectionRepository,
    private val settingsRepo: SettingsRepository,
    private val auditLogRepo: AuditLogRepository,
    private val clientFactory: (ConnectionRecord) -> ArgoWorkflowsClient = { conn ->
        ArgoWorkflowsHttpClient.create(conn.toArgoClientConfig())
    },
    private val serverName: String = "argo-workflows-mcp",
    private val serverVersion: String = "0.2.0",
) : Closeable {
    private companion object : Logger()

    private var currentClient: ArgoWorkflowsClient? = null
    private var currentConnectionId: Int? = null

    private fun getClientAndConfig(): Pair<ArgoWorkflowsClient, ConnectionRecord>? {
        val activeConn = connectionRepo.findActive() ?: return null
        if (currentConnectionId != activeConn.id) {
            currentClient?.close()
            currentClient = clientFactory(activeConn)
            currentConnectionId = activeConn.id
        }
        return currentClient!! to activeConn
    }

    private fun noConnectionResult(): CallToolResult = CallToolResult(
        content = listOf(
            TextContent(
                "No active Argo connection configured. " +
                    "Please add and activate a connection via the web UI."
            )
        ),
        isError = true,
    )

    fun createServer(): Server {
        val server = Server(
            serverInfo = Implementation(
                name = serverName,
                version = serverVersion,
            ),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true),
                ),
            ),
        )

        registerTools(server)

        log.info("MCP Server created: $serverName v$serverVersion")
        return server
    }

    private fun registerTools(server: Server) {
        registerListWorkflows(server)
        registerGetWorkflow(server)
        registerGetWorkflowLogs(server)
        registerTerminateWorkflow(server)
        registerRetryWorkflow(server)
        registerListCronWorkflows(server)
        registerGetCronWorkflow(server)
        registerGetCronHistory(server)
        registerToggleCronSuspension(server)
        registerListWorkflowTemplates(server)
        registerGetWorkflowTemplate(server)
        registerListClusterWorkflowTemplates(server)
        registerGetClusterWorkflowTemplate(server)
    }

    override fun close() {
        currentClient?.close()
    }

    private fun createWorkflowOps(
        client: ArgoWorkflowsClient,
        conn: ConnectionRecord,
    ): WorkflowOperations = WorkflowOperations(
        defaultNamespace = conn.defaultNamespace,
        allowDestructive = settingsRepo.getAllowDestructive(),
        allowMutations = settingsRepo.getAllowMutations(),
        requireConfirmation = settingsRepo.getRequireConfirmation(),
        argoClient = client,
    )

    private fun createCronOps(): CronWorkflowOperations = CronWorkflowOperations(
        allowMutations = settingsRepo.getAllowMutations(),
    )

    private suspend fun withAudit(
        toolName: String,
        arguments: Map<String, JsonElement>?,
        block: suspend () -> CallToolResult,
    ): CallToolResult {
        val startTime = System.currentTimeMillis()
        val argsJson = arguments?.entries?.joinToString(", ") { "${it.key}=${it.value}" } ?: ""

        return try {
            val result = block()
            val duration = System.currentTimeMillis() - startTime
            val status = if (result.isError == true) "ERROR" else "SUCCESS"
            val summary = result.content
                .filterIsInstance<TextContent>()
                .joinToString("\n") { it.text }
                .take(MAX_SUMMARY_LENGTH)

            auditLogRepo.add(
                AuditLogRecord(
                    toolName = toolName,
                    arguments = argsJson,
                    status = status,
                    resultSummary = summary,
                    durationMs = duration,
                    executedAt = LocalDateTime.now(),
                ),
            )
            result
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            auditLogRepo.add(
                AuditLogRecord(
                    toolName = toolName,
                    arguments = argsJson,
                    status = "ERROR",
                    resultSummary = e.message?.take(MAX_SUMMARY_LENGTH),
                    durationMs = duration,
                    executedAt = LocalDateTime.now(),
                ),
            )
            throw e
        }
    }

    // Workflow tools
    private fun registerListWorkflows(server: Server) {
        server.addTool(
            name = "list_workflows",
            description = "List workflows in specified namespace(s) with optional status filtering",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("namespace") {
                        put("type", "string")
                        put("description", "Kubernetes namespace (optional, defaults to all namespaces)")
                    }
                    putJsonObject("status") {
                        put("type", "string")
                        put("description", "Filter by status: Running, Succeeded, Failed, Pending")
                        putJsonArray("enum") {
                            add("Running")
                            add("Succeeded")
                            add("Failed")
                            add("Pending")
                        }
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "Maximum number of workflows to return")
                        put("default", DEFAULT_WORKFLOW_LIMIT)
                    }
                },
            ),
        ) { request ->
            withAudit("list_workflows", request.arguments) {
                val (client, conn) = getClientAndConfig() ?: return@withAudit noConnectionResult()
                val ops = createWorkflowOps(client, conn)
                val namespace = request.arguments.stringOrNull("namespace")
                val status = request.arguments.stringOrNull("status")
                val limit = request.arguments.intOrNull("limit") ?: DEFAULT_WORKFLOW_LIMIT
                convertToToolResult(ops.listWorkflows(namespace, status, limit))
            }
        }
    }

    private fun registerGetWorkflow(server: Server) {
        server.addTool(
            name = "get_workflow",
            description = "Get detailed information about a specific workflow",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("namespace") {
                        put("type", "string")
                        put("description", "Kubernetes namespace")
                    }
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Workflow name")
                    }
                },
                required = listOf("namespace", "name"),
            ),
        ) { request ->
            withAudit("get_workflow", request.arguments) {
                val (client, conn) = getClientAndConfig() ?: return@withAudit noConnectionResult()
                val ops = createWorkflowOps(client, conn)
                val namespace = request.arguments.requiredString("namespace")
                    ?: return@withAudit errorResult("namespace is required")
                val name = request.arguments.requiredString("name")
                    ?: return@withAudit errorResult("name is required")
                convertToToolResult(ops.getWorkflow(namespace, name))
            }
        }
    }

    private fun registerGetWorkflowLogs(server: Server) {
        server.addTool(
            name = "get_workflow_logs",
            description = "Get logs from a workflow's pods",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("namespace") {
                        put("type", "string")
                        put("description", "Kubernetes namespace")
                    }
                    putJsonObject("workflow_name") {
                        put("type", "string")
                        put("description", "Workflow name")
                    }
                    putJsonObject("pod_name") {
                        put("type", "string")
                        put("description", "Specific pod name (optional)")
                    }
                    putJsonObject("container") {
                        put("type", "string")
                        put("description", "Container name")
                        put("default", "main")
                    }
                    putJsonObject("search") {
                        put("type", "string")
                        put("description", "Return only log lines containing this case-insensitive substring")
                    }
                    putJsonObject("max_lines") {
                        put("type", "integer")
                        put("description", "Maximum number of lines to return (0 for all lines)")
                        put("default", DEFAULT_LOG_MAX_LINES)
                    }
                },
                required = listOf("namespace", "workflow_name"),
            ),
        ) { request ->
            withAudit("get_workflow_logs", request.arguments) {
                val (client, conn) = getClientAndConfig() ?: return@withAudit noConnectionResult()
                val ops = createWorkflowOps(client, conn)
                val namespace = request.arguments.requiredString("namespace")
                    ?: return@withAudit errorResult("namespace is required")
                val workflowName = request.arguments.requiredString("workflow_name")
                    ?: return@withAudit errorResult("workflow_name is required")
                val podName = request.arguments.stringOrNull("pod_name")
                val container = request.arguments.stringOrNull("container") ?: "main"
                val search = request.arguments.stringOrNull("search")
                val maxLines = request.arguments.intOrNull("max_lines") ?: DEFAULT_LOG_MAX_LINES
                convertToToolResult(
                    ops.getWorkflowLogs(
                        namespace = namespace,
                        workflowName = workflowName,
                        podName = podName,
                        container = container,
                        search = search,
                        maxLines = maxLines,
                    ),
                )
            }
        }
    }

    private fun registerTerminateWorkflow(server: Server) {
        server.addTool(
            name = "terminate_workflow",
            description = "Terminate a running workflow (DESTRUCTIVE - requires confirmation)",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("namespace") {
                        put("type", "string")
                        put("description", "Kubernetes namespace")
                    }
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Workflow name")
                    }
                    putJsonObject("reason") {
                        put("type", "string")
                        put("description", "Reason for termination (for audit)")
                    }
                    putJsonObject("dry_run") {
                        put("type", "boolean")
                        put("description", "Preview mode - shows what would happen")
                        put("default", true)
                    }
                    putJsonObject("confirmation_token") {
                        put("type", "string")
                        put("description", "Token from dry-run preview (required for execution)")
                    }
                },
                required = listOf("namespace", "name", "reason"),
            ),
        ) { request ->
            withAudit("terminate_workflow", request.arguments) {
                val (client, conn) = getClientAndConfig() ?: return@withAudit noConnectionResult()
                val ops = createWorkflowOps(client, conn)
                val namespace = request.arguments.requiredString("namespace")
                    ?: return@withAudit errorResult("namespace is required")
                val name = request.arguments.requiredString("name")
                    ?: return@withAudit errorResult("name is required")
                val reason = request.arguments.requiredString("reason")
                    ?: return@withAudit errorResult("reason is required")
                val dryRun = request.arguments.booleanOrNull("dry_run") ?: true
                val confirmationToken = request.arguments.stringOrNull("confirmation_token")
                convertToToolResult(
                    ops.terminateWorkflow(
                        namespace = namespace,
                        name = name,
                        reason = reason,
                        dryRun = dryRun,
                        confirmationToken = confirmationToken,
                    ),
                )
            }
        }
    }

    private fun registerRetryWorkflow(server: Server) {
        server.addTool(
            name = "retry_workflow",
            description = "Retry a failed workflow",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("namespace") {
                        put("type", "string")
                        put("description", "Kubernetes namespace")
                    }
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Workflow name")
                    }
                    putJsonObject("restart_successful") {
                        put("type", "boolean")
                        put("description", "Also restart successful steps")
                        put("default", false)
                    }
                },
                required = listOf("namespace", "name"),
            ),
        ) { request ->
            withAudit("retry_workflow", request.arguments) {
                val (client, conn) = getClientAndConfig() ?: return@withAudit noConnectionResult()
                val ops = createWorkflowOps(client, conn)
                val namespace = request.arguments.requiredString("namespace")
                    ?: return@withAudit errorResult("namespace is required")
                val name = request.arguments.requiredString("name")
                    ?: return@withAudit errorResult("name is required")
                val restartSuccessful = request.arguments.booleanOrNull("restart_successful") ?: false
                convertToToolResult(ops.retryWorkflow(namespace, name, restartSuccessful))
            }
        }
    }

    // CronWorkflow tools
    private fun registerListCronWorkflows(server: Server) {
        server.addTool(
            name = "list_cron_workflows",
            description = "List CronWorkflows in namespace(s)",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("namespace") {
                        put("type", "string")
                        put("description", "Kubernetes namespace (optional)")
                    }
                    putJsonObject("suspended") {
                        put("type", "boolean")
                        put("description", "Filter by suspended state")
                    }
                },
            ),
        ) { request ->
            withAudit("list_cron_workflows", request.arguments) {
                val cronOps = createCronOps()
                val namespace = request.arguments.stringOrNull("namespace")
                val suspended = request.arguments.booleanOrNull("suspended")
                convertToToolResult(cronOps.listCronWorkflows(namespace, suspended))
            }
        }
    }

    private fun registerGetCronWorkflow(server: Server) {
        server.addTool(
            name = "get_cron_workflow",
            description = "Get CronWorkflow details including schedule and last execution",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("namespace") {
                        put("type", "string")
                        put("description", "Kubernetes namespace")
                    }
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "CronWorkflow name")
                    }
                },
                required = listOf("namespace", "name"),
            ),
        ) { request ->
            withAudit("get_cron_workflow", request.arguments) {
                val cronOps = createCronOps()
                val namespace = request.arguments.requiredString("namespace")
                    ?: return@withAudit errorResult("namespace is required")
                val name = request.arguments.requiredString("name")
                    ?: return@withAudit errorResult("name is required")
                convertToToolResult(cronOps.getCronWorkflow(namespace, name))
            }
        }
    }

    private fun registerGetCronHistory(server: Server) {
        server.addTool(
            name = "get_cron_history",
            description = "Get execution history of a CronWorkflow",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("namespace") {
                        put("type", "string")
                        put("description", "Kubernetes namespace")
                    }
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "CronWorkflow name")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "Number of recent executions")
                        put("default", DEFAULT_CRON_HISTORY_LIMIT)
                    }
                },
                required = listOf("namespace", "name"),
            ),
        ) { request ->
            withAudit("get_cron_history", request.arguments) {
                val cronOps = createCronOps()
                val namespace = request.arguments.requiredString("namespace")
                    ?: return@withAudit errorResult("namespace is required")
                val name = request.arguments.requiredString("name")
                    ?: return@withAudit errorResult("name is required")
                val limit = request.arguments.intOrNull("limit") ?: DEFAULT_CRON_HISTORY_LIMIT
                convertToToolResult(cronOps.getCronHistory(namespace, name, limit))
            }
        }
    }

    private fun registerToggleCronSuspension(server: Server) {
        server.addTool(
            name = "toggle_cron_suspension",
            description = "Suspend or resume a CronWorkflow",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("namespace") {
                        put("type", "string")
                        put("description", "Kubernetes namespace")
                    }
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "CronWorkflow name")
                    }
                    putJsonObject("suspend") {
                        put("type", "boolean")
                        put("description", "true to suspend, false to resume")
                    }
                },
                required = listOf("namespace", "name", "suspend"),
            ),
        ) { request ->
            withAudit("toggle_cron_suspension", request.arguments) {
                val cronOps = createCronOps()
                val namespace = request.arguments.requiredString("namespace")
                    ?: return@withAudit errorResult("namespace is required")
                val name = request.arguments.requiredString("name")
                    ?: return@withAudit errorResult("name is required")
                val suspend = request.arguments.booleanOrNull("suspend")
                    ?: return@withAudit errorResult("suspend is required")
                convertToToolResult(cronOps.toggleCronSuspension(namespace, name, suspend))
            }
        }
    }

    // Template tools
    private fun registerListWorkflowTemplates(server: Server) {
        server.addTool(
            name = "list_workflow_templates",
            description = "List WorkflowTemplates in namespace",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("namespace") {
                        put("type", "string")
                        put("description", "Kubernetes namespace")
                    }
                    putJsonObject("label_selector") {
                        put("type", "string")
                        put("description", "Label selector (e.g., 'app=myapp')")
                    }
                },
            ),
        ) { request ->
            withAudit("list_workflow_templates", request.arguments) {
                val templateOps = TemplateOperations()
                val namespace = request.arguments.stringOrNull("namespace")
                val labelSelector = request.arguments.stringOrNull("label_selector")
                convertToToolResult(templateOps.listWorkflowTemplates(namespace, labelSelector))
            }
        }
    }

    private fun registerGetWorkflowTemplate(server: Server) {
        server.addTool(
            name = "get_workflow_template",
            description = "Get WorkflowTemplate details",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("namespace") {
                        put("type", "string")
                        put("description", "Kubernetes namespace")
                    }
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Template name")
                    }
                },
                required = listOf("namespace", "name"),
            ),
        ) { request ->
            withAudit("get_workflow_template", request.arguments) {
                val templateOps = TemplateOperations()
                val namespace = request.arguments.requiredString("namespace")
                    ?: return@withAudit errorResult("namespace is required")
                val name = request.arguments.requiredString("name")
                    ?: return@withAudit errorResult("name is required")
                convertToToolResult(templateOps.getWorkflowTemplate(namespace, name))
            }
        }
    }

    private fun registerListClusterWorkflowTemplates(server: Server) {
        server.addTool(
            name = "list_cluster_workflow_templates",
            description = "List ClusterWorkflowTemplates (cluster-scoped)",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("label_selector") {
                        put("type", "string")
                        put("description", "Label selector")
                    }
                },
            ),
        ) { request ->
            withAudit("list_cluster_workflow_templates", request.arguments) {
                val templateOps = TemplateOperations()
                val labelSelector = request.arguments.stringOrNull("label_selector")
                convertToToolResult(templateOps.listClusterWorkflowTemplates(labelSelector))
            }
        }
    }

    private fun registerGetClusterWorkflowTemplate(server: Server) {
        server.addTool(
            name = "get_cluster_workflow_template",
            description = "Get ClusterWorkflowTemplate details",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Template name")
                    }
                },
                required = listOf("name"),
            ),
        ) { request ->
            withAudit("get_cluster_workflow_template", request.arguments) {
                val templateOps = TemplateOperations()
                val name = request.arguments.requiredString("name")
                    ?: return@withAudit errorResult("name is required")
                convertToToolResult(templateOps.getClusterWorkflowTemplate(name))
            }
        }
    }

    private fun convertToToolResult(result: OperationResult): CallToolResult {
        return when (result) {
            is OperationResult.Success -> {
                val text = buildString {
                    appendLine(result.message)
                    if (result.data.isNotEmpty()) {
                        appendLine()
                        result.data.forEach { (key, value) ->
                            appendLine("$key: $value")
                        }
                    }
                }
                CallToolResult(content = listOf(TextContent(text)), isError = false)
            }

            is OperationResult.DryRun -> {
                val text = buildString {
                    appendLine("DRY RUN MODE")
                    appendLine("=".repeat(SEPARATOR_LENGTH))
                    appendLine(result.preview)
                    appendLine()
                    appendLine(result.instructions)
                }
                CallToolResult(content = listOf(TextContent(text)), isError = false)
            }

            is OperationResult.NeedsConfirmation -> {
                val text = buildString {
                    appendLine("CONFIRMATION REQUIRED")
                    appendLine("=".repeat(SEPARATOR_LENGTH))
                    appendLine(result.preview)
                    appendLine()
                    appendLine("Token: ${result.token}")
                }
                CallToolResult(content = listOf(TextContent(text)), isError = false)
            }

            is OperationResult.Error -> {
                CallToolResult(
                    content = listOf(TextContent("ERROR [${result.code}]: ${result.message}")),
                    isError = true,
                )
            }

            is OperationResult.Cancelled -> {
                CallToolResult(
                    content = listOf(TextContent("CANCELLED: ${result.reason}")),
                    isError = false,
                )
            }
        }
    }
}

private const val SEPARATOR_LENGTH = 50
private const val DEFAULT_WORKFLOW_LIMIT = 50
private const val DEFAULT_LOG_MAX_LINES = 200
private const val DEFAULT_CRON_HISTORY_LIMIT = 10
private const val MAX_SUMMARY_LENGTH = 500

private fun errorResult(message: String): CallToolResult =
    CallToolResult(content = listOf(TextContent(message)))

private fun Map<String, JsonElement>?.stringOrNull(key: String): String? =
    this?.get(key)?.run { jsonPrimitive.contentOrNull }

private fun Map<String, JsonElement>?.requiredString(key: String): String? =
    this?.get(key)?.run { jsonPrimitive.content }

private fun Map<String, JsonElement>?.intOrNull(key: String): Int? =
    this?.get(key)?.run { jsonPrimitive.intOrNull }

private fun Map<String, JsonElement>?.booleanOrNull(key: String): Boolean? =
    this?.get(key)?.run { jsonPrimitive.booleanOrNull }

fun ConnectionRecord.toArgoClientConfig() = ArgoClientConfig(
    baseUrl = baseUrl,
    defaultNamespace = defaultNamespace,
    auth = ArgoAuthConfig(
        bearerToken = bearerToken,
        username = username,
        password = password,
    ),
    insecureSkipTlsVerify = insecureSkipTlsVerify,
    requestTimeoutSeconds = requestTimeoutSeconds,
    tlsServerName = tlsServerName,
)
