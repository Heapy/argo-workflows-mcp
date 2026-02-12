package io.heapy.argo.workflows.mcp

import io.heapy.argo.client.ArgoWorkflowsClient
import io.heapy.argo.client.ArgoWorkflowsHttpClient
import io.heapy.argo.workflows.mcp.config.ServerConfig
import io.heapy.argo.workflows.mcp.operations.CronWorkflowOperations
import io.heapy.argo.workflows.mcp.operations.OperationResult
import io.heapy.argo.workflows.mcp.operations.TemplateOperations
import io.heapy.argo.workflows.mcp.operations.WorkflowOperations
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

/**
 * Main MCP Server for Argo Workflows
 */
@Suppress("TooManyFunctions", "LabeledExpression")
class ArgoWorkflowsMCPServer(
    private val config: ServerConfig,
    private val workflowsClient: ArgoWorkflowsClient = ArgoWorkflowsHttpClient.create(config.argo)
) : Closeable {
    private companion object : Logger()

    // Operation handlers
    private val workflowOps = WorkflowOperations(config, workflowsClient)
    private val cronOps = CronWorkflowOperations(config)
    private val templateOps = TemplateOperations()

    /**
     * Create and configure the MCP server
     */
    fun createServer(): Server {
        val server = Server(
            serverInfo = Implementation(
                name = config.server.name,
                version = config.server.version
            ),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true)
                )
            )
        )

        registerTools(server)

        log.info("MCP Server created: ${config.server.name} v${config.server.version}")
        return server
    }

    /**
     * Register all available tools
     */
    private fun registerTools(server: Server) {
        // Workflow operations
        registerListWorkflows(server)
        registerGetWorkflow(server)
        registerGetWorkflowLogs(server)
        registerTerminateWorkflow(server)
        registerRetryWorkflow(server)

        // CronWorkflow operations
        registerListCronWorkflows(server)
        registerGetCronWorkflow(server)
        registerGetCronHistory(server)
        registerToggleCronSuspension(server)

        // Template operations
        registerListWorkflowTemplates(server)
        registerGetWorkflowTemplate(server)
        registerListClusterWorkflowTemplates(server)
        registerGetClusterWorkflowTemplate(server)
    }

    override fun close() {
        workflowsClient.close()
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
                }
            )
        ) { request ->
            val namespace = request.arguments.stringOrNull("namespace")
            val status = request.arguments.stringOrNull("status")
            val limit = request.arguments.intOrNull("limit") ?: DEFAULT_WORKFLOW_LIMIT

            val result = workflowOps.listWorkflows(namespace, status, limit)
            convertToToolResult(result)
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
                required = listOf("namespace", "name")
            )
        ) { request ->
            val namespace = request.arguments.requiredString("namespace")
                ?: return@addTool errorResult("namespace is required")
            val name = request.arguments.requiredString("name")
                ?: return@addTool errorResult("name is required")

            val result = workflowOps.getWorkflow(namespace, name)
            convertToToolResult(result)
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
                required = listOf("namespace", "workflow_name")
            )
        ) { request ->
            val namespace = request.arguments.requiredString("namespace")
                ?: return@addTool errorResult("namespace is required")
            val workflowName = request.arguments.requiredString("workflow_name")
                ?: return@addTool errorResult("workflow_name is required")
            val podName = request.arguments.stringOrNull("pod_name")
            val container = request.arguments.stringOrNull("container") ?: "main"
            val search = request.arguments.stringOrNull("search")
            val maxLines = request.arguments.intOrNull("max_lines") ?: DEFAULT_LOG_MAX_LINES

            val result = workflowOps.getWorkflowLogs(
                namespace = namespace,
                workflowName = workflowName,
                podName = podName,
                container = container,
                search = search,
                maxLines = maxLines,
            )
            convertToToolResult(result)
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
                required = listOf("namespace", "name", "reason")
            )
        ) { request ->
            val namespace = request.arguments.requiredString("namespace")
                ?: return@addTool errorResult("namespace is required")
            val name = request.arguments.requiredString("name")
                ?: return@addTool errorResult("name is required")
            val reason = request.arguments.requiredString("reason")
                ?: return@addTool errorResult("reason is required")
            val dryRun = request.arguments.booleanOrNull("dry_run") ?: true
            val confirmationToken = request.arguments.stringOrNull("confirmation_token")

            val result = workflowOps.terminateWorkflow(
                namespace = namespace,
                name = name,
                reason = reason,
                dryRun = dryRun,
                confirmationToken = confirmationToken,
            )
            convertToToolResult(result)
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
                required = listOf("namespace", "name")
            )
        ) { request ->
            val namespace = request.arguments.requiredString("namespace")
                ?: return@addTool errorResult("namespace is required")
            val name = request.arguments.requiredString("name")
                ?: return@addTool errorResult("name is required")
            val restartSuccessful = request.arguments.booleanOrNull("restart_successful") ?: false

            val result = workflowOps.retryWorkflow(namespace, name, restartSuccessful)
            convertToToolResult(result)
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
                }
            )
        ) { request ->
            val namespace = request.arguments.stringOrNull("namespace")
            val suspended = request.arguments.booleanOrNull("suspended")

            val result = cronOps.listCronWorkflows(namespace, suspended)
            convertToToolResult(result)
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
                required = listOf("namespace", "name")
            )
        ) { request ->
            val namespace = request.arguments.requiredString("namespace")
                ?: return@addTool errorResult("namespace is required")
            val name = request.arguments.requiredString("name")
                ?: return@addTool errorResult("name is required")

            val result = cronOps.getCronWorkflow(namespace, name)
            convertToToolResult(result)
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
                required = listOf("namespace", "name")
            )
        ) { request ->
            val namespace = request.arguments.requiredString("namespace")
                ?: return@addTool errorResult("namespace is required")
            val name = request.arguments.requiredString("name")
                ?: return@addTool errorResult("name is required")
            val limit = request.arguments.intOrNull("limit") ?: DEFAULT_CRON_HISTORY_LIMIT

            val result = cronOps.getCronHistory(namespace, name, limit)
            convertToToolResult(result)
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
                required = listOf("namespace", "name", "suspend")
            )
        ) { request ->
            val namespace = request.arguments.requiredString("namespace")
                ?: return@addTool errorResult("namespace is required")
            val name = request.arguments.requiredString("name")
                ?: return@addTool errorResult("name is required")
            val suspend = request.arguments.booleanOrNull("suspend")
                ?: return@addTool errorResult("suspend is required")

            val result = cronOps.toggleCronSuspension(namespace, name, suspend)
            convertToToolResult(result)
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
                }
            )
        ) { request ->
            val namespace = request.arguments.stringOrNull("namespace")
            val labelSelector = request.arguments.stringOrNull("label_selector")

            val result = templateOps.listWorkflowTemplates(namespace, labelSelector)
            convertToToolResult(result)
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
                required = listOf("namespace", "name")
            )
        ) { request ->
            val namespace = request.arguments.requiredString("namespace")
                ?: return@addTool errorResult("namespace is required")
            val name = request.arguments.requiredString("name")
                ?: return@addTool errorResult("name is required")

            val result = templateOps.getWorkflowTemplate(namespace, name)
            convertToToolResult(result)
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
                }
            )
        ) { request ->
            val labelSelector = request.arguments.stringOrNull("label_selector")

            val result = templateOps.listClusterWorkflowTemplates(labelSelector)
            convertToToolResult(result)
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
                required = listOf("name")
            )
        ) { request ->
            val name = request.arguments.requiredString("name")
                ?: return@addTool errorResult("name is required")

            val result = templateOps.getClusterWorkflowTemplate(name)
            convertToToolResult(result)
        }
    }

    /**
     * Convert OperationResult to MCP CallToolResult
     */
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
                    isError = true
                )
            }

            is OperationResult.Cancelled -> {
                CallToolResult(
                    content = listOf(TextContent("CANCELLED: ${result.reason}")),
                    isError = false
                )
            }
        }
    }
}

private const val SEPARATOR_LENGTH = 50
private const val DEFAULT_WORKFLOW_LIMIT = 50
private const val DEFAULT_LOG_MAX_LINES = 200
private const val DEFAULT_CRON_HISTORY_LIMIT = 10

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
