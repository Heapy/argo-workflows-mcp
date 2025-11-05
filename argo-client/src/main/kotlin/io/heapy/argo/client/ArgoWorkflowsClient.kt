package io.heapy.argo.client

import kotlinx.serialization.json.JsonObject
import java.io.Closeable
import kotlin.time.Instant

/**
 * Minimal Argo Workflows client used by the MCP server.
 */
interface ArgoWorkflowsClient : Closeable {
    /**
     * List workflows in the provided namespace.
     */
    suspend fun listWorkflows(
        namespace: String,
        limit: Int = 50,
        labelSelector: String? = null,
        fieldSelector: String? = null
    ): List<WorkflowSummary>

    /**
     * Retrieve detailed workflow information.
     */
    suspend fun getWorkflow(
        namespace: String,
        name: String
    ): WorkflowDetail

    /**
     * Fetch logs for workflow pods.
     */
    suspend fun getWorkflowLogs(
        namespace: String,
        workflowName: String,
        podName: String? = null,
        container: String = "main"
    ): WorkflowLogs
}

data class WorkflowSummary(
    val name: String,
    val namespace: String,
    val phase: String?,
    val progress: String?,
    val startedAt: Instant?,
    val finishedAt: Instant?
)

data class WorkflowDetail(
    val summary: WorkflowSummary,
    val message: String?,
    val labels: Map<String, String>,
    val annotations: Map<String, String>,
    val parameters: Map<String, String>,
    val outputs: Map<String, String>,
    val raw: JsonObject
)

data class WorkflowLogs(
    val entries: List<WorkflowLogEntry>
)

data class WorkflowLogEntry(
    val podName: String?,
    val content: String
)
