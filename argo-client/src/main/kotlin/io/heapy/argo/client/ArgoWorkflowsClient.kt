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

    suspend fun terminateWorkflow(
        namespace: String,
        name: String
    ): WorkflowSummary

    suspend fun retryWorkflow(
        namespace: String,
        name: String,
        restartSuccessful: Boolean = false
    ): WorkflowSummary

    suspend fun listCronWorkflows(
        namespace: String,
        labelSelector: String? = null
    ): List<CronWorkflowSummary>

    suspend fun getCronWorkflow(
        namespace: String,
        name: String
    ): CronWorkflowDetail

    suspend fun suspendCronWorkflow(
        namespace: String,
        name: String
    ): CronWorkflowSummary

    suspend fun resumeCronWorkflow(
        namespace: String,
        name: String
    ): CronWorkflowSummary

    suspend fun listWorkflowTemplates(
        namespace: String,
        labelSelector: String? = null
    ): List<WorkflowTemplateSummary>

    suspend fun getWorkflowTemplate(
        namespace: String,
        name: String
    ): WorkflowTemplateDetail

    suspend fun listClusterWorkflowTemplates(
        labelSelector: String? = null
    ): List<WorkflowTemplateSummary>

    suspend fun getClusterWorkflowTemplate(
        name: String
    ): WorkflowTemplateDetail
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

data class CronWorkflowSummary(
    val name: String,
    val namespace: String,
    val schedules: List<String>,
    val suspended: Boolean?,
    val timezone: String?,
    val lastScheduledTime: Instant?,
    val activeWorkflows: List<String>,
    val phase: String?
)

data class CronWorkflowDetail(
    val summary: CronWorkflowSummary,
    val concurrencyPolicy: String?,
    val successfulJobsHistoryLimit: Int?,
    val failedJobsHistoryLimit: Int?,
    val labels: Map<String, String>,
    val annotations: Map<String, String>,
    val raw: JsonObject
)

data class WorkflowTemplateSummary(
    val name: String,
    val namespace: String?,
    val entrypoint: String?,
    val templateCount: Int,
    val labels: Map<String, String>
)

data class WorkflowTemplateDetail(
    val summary: WorkflowTemplateSummary,
    val parameters: Map<String, String>,
    val annotations: Map<String, String>,
    val raw: JsonObject
)
