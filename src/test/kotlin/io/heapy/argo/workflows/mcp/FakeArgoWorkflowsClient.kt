package io.heapy.argo.workflows.mcp

import io.heapy.argo.client.ArgoWorkflowsClient
import io.heapy.argo.client.CronWorkflowDetail
import io.heapy.argo.client.CronWorkflowSummary
import io.heapy.argo.client.WorkflowDetail
import io.heapy.argo.client.WorkflowLogs
import io.heapy.argo.client.WorkflowSummary
import io.heapy.argo.client.WorkflowTemplateDetail
import io.heapy.argo.client.WorkflowTemplateSummary
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
    var terminateWorkflowResult: WorkflowSummary = workflowDetailResult.summary.copy(phase = "Failed")
    var retryWorkflowResult: WorkflowSummary = workflowDetailResult.summary.copy(name = "default-retry", phase = "Running")
    var listCronWorkflowsResult: List<CronWorkflowSummary> = emptyList()
    var cronWorkflowDetailResult: CronWorkflowDetail = CronWorkflowDetail(
        summary = CronWorkflowSummary(
            name = "daily-job",
            namespace = "default",
            schedules = listOf("0 0 * * *"),
            suspended = false,
            timezone = "UTC",
            lastScheduledTime = Instant.parse("2024-01-01T00:00:00Z"),
            activeWorkflows = emptyList(),
            phase = "Active",
        ),
        concurrencyPolicy = "Forbid",
        successfulJobsHistoryLimit = 3,
        failedJobsHistoryLimit = 1,
        labels = emptyMap(),
        annotations = emptyMap(),
        raw = JsonObject(emptyMap()),
    )
    var suspendCronWorkflowResult: CronWorkflowSummary = cronWorkflowDetailResult.summary.copy(suspended = true)
    var resumeCronWorkflowResult: CronWorkflowSummary = cronWorkflowDetailResult.summary.copy(suspended = false)
    var listWorkflowTemplatesResult: List<WorkflowTemplateSummary> = emptyList()
    var workflowTemplateDetailResult: WorkflowTemplateDetail = WorkflowTemplateDetail(
        summary = WorkflowTemplateSummary(
            name = "build-template",
            namespace = "default",
            entrypoint = "main",
            templateCount = 2,
            labels = emptyMap(),
        ),
        parameters = emptyMap(),
        annotations = emptyMap(),
        raw = JsonObject(emptyMap()),
    )
    var listClusterWorkflowTemplatesResult: List<WorkflowTemplateSummary> = emptyList()
    var clusterWorkflowTemplateDetailResult: WorkflowTemplateDetail = workflowTemplateDetailResult.copy(
        summary = workflowTemplateDetailResult.summary.copy(
            name = "global-build",
            namespace = null,
        ),
    )
    val calls: MutableList<String> = mutableListOf()

    override suspend fun listWorkflows(
        namespace: String,
        limit: Int,
        labelSelector: String?,
        fieldSelector: String?
    ): List<WorkflowSummary> {
        calls += "listWorkflows:$namespace:$limit:${labelSelector.orEmpty()}:${fieldSelector.orEmpty()}"
        return listWorkflowsResult
    }

    override suspend fun getWorkflow(namespace: String, name: String): WorkflowDetail {
        calls += "getWorkflow:$namespace:$name"
        return workflowDetailResult
    }

    override suspend fun getWorkflowLogs(
        namespace: String,
        workflowName: String,
        podName: String?,
        container: String
    ): WorkflowLogs {
        calls += "getWorkflowLogs:$namespace:$workflowName:${podName.orEmpty()}:$container"
        return workflowLogsResult
    }

    override suspend fun terminateWorkflow(namespace: String, name: String): WorkflowSummary {
        calls += "terminateWorkflow:$namespace:$name"
        return terminateWorkflowResult.copy(namespace = namespace, name = name)
    }

    override suspend fun retryWorkflow(
        namespace: String,
        name: String,
        restartSuccessful: Boolean
    ): WorkflowSummary {
        calls += "retryWorkflow:$namespace:$name:$restartSuccessful"
        return retryWorkflowResult.copy(namespace = namespace)
    }

    override suspend fun listCronWorkflows(
        namespace: String,
        labelSelector: String?
    ): List<CronWorkflowSummary> {
        calls += "listCronWorkflows:$namespace:${labelSelector.orEmpty()}"
        return listCronWorkflowsResult
    }

    override suspend fun getCronWorkflow(namespace: String, name: String): CronWorkflowDetail {
        calls += "getCronWorkflow:$namespace:$name"
        return cronWorkflowDetailResult.copy(
            summary = cronWorkflowDetailResult.summary.copy(namespace = namespace, name = name),
        )
    }

    override suspend fun suspendCronWorkflow(namespace: String, name: String): CronWorkflowSummary {
        calls += "suspendCronWorkflow:$namespace:$name"
        return suspendCronWorkflowResult.copy(namespace = namespace, name = name)
    }

    override suspend fun resumeCronWorkflow(namespace: String, name: String): CronWorkflowSummary {
        calls += "resumeCronWorkflow:$namespace:$name"
        return resumeCronWorkflowResult.copy(namespace = namespace, name = name)
    }

    override suspend fun listWorkflowTemplates(
        namespace: String,
        labelSelector: String?
    ): List<WorkflowTemplateSummary> {
        calls += "listWorkflowTemplates:$namespace:${labelSelector.orEmpty()}"
        return listWorkflowTemplatesResult
    }

    override suspend fun getWorkflowTemplate(namespace: String, name: String): WorkflowTemplateDetail {
        calls += "getWorkflowTemplate:$namespace:$name"
        return workflowTemplateDetailResult.copy(
            summary = workflowTemplateDetailResult.summary.copy(namespace = namespace, name = name),
        )
    }

    override suspend fun listClusterWorkflowTemplates(
        labelSelector: String?
    ): List<WorkflowTemplateSummary> {
        calls += "listClusterWorkflowTemplates:${labelSelector.orEmpty()}"
        return listClusterWorkflowTemplatesResult
    }

    override suspend fun getClusterWorkflowTemplate(name: String): WorkflowTemplateDetail {
        calls += "getClusterWorkflowTemplate:$name"
        return clusterWorkflowTemplateDetailResult.copy(
            summary = clusterWorkflowTemplateDetailResult.summary.copy(name = name, namespace = null),
        )
    }

    override fun close() = Unit
}
