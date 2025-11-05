package io.heapy.argo.workflows.mcp

import io.heapy.argo.client.ArgoWorkflowsClient
import io.heapy.argo.client.WorkflowDetail
import io.heapy.argo.client.WorkflowLogs
import io.heapy.argo.client.WorkflowSummary
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

class FakeArgoWorkflowsClient : ArgoWorkflowsClient {
    var listWorkflowsResult: List<WorkflowSummary> = emptyList()
    var workflowDetailResult: WorkflowDetail = WorkflowDetail(
        summary = WorkflowSummary(
            name = "default",
            namespace = "default",
            phase = "Succeeded",
            progress = "1/1",
            startedAt = Instant.parse("2024-01-01T00:00:00Z"),
            finishedAt = Instant.parse("2024-01-01T00:01:00Z")
        ),
        message = null,
        labels = emptyMap(),
        annotations = emptyMap(),
        parameters = emptyMap(),
        outputs = emptyMap(),
        raw = JsonObject(emptyMap())
    )
    var workflowLogsResult: WorkflowLogs = WorkflowLogs(emptyList())

    override suspend fun listWorkflows(
        namespace: String,
        limit: Int,
        labelSelector: String?,
        fieldSelector: String?
    ): List<WorkflowSummary> = listWorkflowsResult

    override suspend fun getWorkflow(namespace: String, name: String): WorkflowDetail = workflowDetailResult

    override suspend fun getWorkflowLogs(
        namespace: String,
        workflowName: String,
        podName: String?,
        container: String
    ): WorkflowLogs = workflowLogsResult

    override fun close() = Unit
}