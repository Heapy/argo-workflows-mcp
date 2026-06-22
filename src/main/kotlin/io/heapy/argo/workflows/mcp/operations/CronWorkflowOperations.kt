package io.heapy.argo.workflows.mcp.operations

import io.heapy.argo.client.ArgoWorkflowsClient
import io.heapy.argo.client.CronWorkflowSummary
import io.heapy.argo.client.WorkflowSummary
import io.heapy.komok.tech.logging.Logger

class CronWorkflowOperations(
    private val defaultNamespace: String,
    private val allowMutations: Boolean,
    private val namespacePolicy: NamespacePolicy = NamespacePolicy(),
    private val argoClient: ArgoWorkflowsClient,
) {
    private companion object : Logger()

    private fun resolveNamespace(namespace: String?): String =
        namespace?.takeIf { it.isNotBlank() } ?: defaultNamespace

    private fun requireNamespaceAllowed(namespace: String): OperationResult.Error? =
        namespaceDeniedError(namespace, namespacePolicy)

    suspend fun listCronWorkflows(
        namespace: String? = null,
        suspended: Boolean? = null,
    ): OperationResult {
        val targetNamespace = resolveNamespace(namespace)
        requireNamespaceAllowed(targetNamespace)?.let { return it }
        log.info("Listing cron workflows: namespace=$targetNamespace, suspended=$suspended")

        return runCatching {
            val cronWorkflows = argoClient.listCronWorkflows(targetNamespace)
            val filtered = suspended?.let { desired ->
                cronWorkflows.filter { it.suspended == desired }
            } ?: cronWorkflows

            OperationResult.Success(
                message = if (filtered.isEmpty()) {
                    "No CronWorkflows found in namespace '$targetNamespace'"
                } else {
                    "Found ${filtered.size} CronWorkflow(s) in namespace '$targetNamespace'"
                },
                data = buildMap {
                    put("namespace", targetNamespace)
                    put("count", filtered.size.toString())
                    suspended?.let { put("suspended_filter", it.toString()) }
                    if (filtered.isNotEmpty()) {
                        put("cron_workflows", filtered.joinToString("\n") { it.toDisplayString() })
                    }
                },
            )
        }.getOrElse { error ->
            log.error("Failed to list CronWorkflows namespace={}", targetNamespace, error)
            error.toOperationError("list CronWorkflows")
        }
    }

    suspend fun getCronWorkflow(
        namespace: String,
        name: String,
    ): OperationResult {
        val targetNamespace = resolveNamespace(namespace)
        requireNamespaceAllowed(targetNamespace)?.let { return it }
        log.info("Getting cron workflow: namespace=$targetNamespace, name=$name")

        return runCatching {
            val detail = argoClient.getCronWorkflow(targetNamespace, name)
            val summary = detail.summary

            OperationResult.Success(
                message = "CronWorkflow '${summary.name}' retrieved from namespace '${summary.namespace}'",
                data = buildMap {
                    put("name", summary.name)
                    put("namespace", summary.namespace)
                    put(
                        "schedules",
                        summary.schedules
                            .joinToString(", ")
                            .ifBlank { "n/a" },
                    )
                    put("suspended", summary.suspended?.toString() ?: "n/a")
                    put("last_scheduled_time", formatInstant(summary.lastScheduledTime))
                    put(
                        "active_workflows",
                        summary
                            .activeWorkflows
                            .joinToString(", ")
                            .ifBlank { "none" },
                    )
                    summary.phase?.let { put("phase", it) }
                    summary.timezone?.let { put("timezone", it) }
                    detail.concurrencyPolicy?.let { put("concurrency_policy", it) }
                    detail.successfulJobsHistoryLimit?.let { put("successful_jobs_history_limit", it.toString()) }
                    detail.failedJobsHistoryLimit?.let { put("failed_jobs_history_limit", it.toString()) }
                    if (detail.labels.isNotEmpty()) {
                        put("labels", detail.labels.formatEntries())
                    }
                    if (detail.annotations.isNotEmpty()) {
                        put("annotations", detail.annotations.formatEntries())
                    }
                },
            )
        }.getOrElse { error ->
            log.error("Failed to get CronWorkflow namespace={}, name={}", targetNamespace, name, error)
            error.toOperationError("retrieve CronWorkflow")
        }
    }

    suspend fun getCronHistory(
        namespace: String,
        name: String,
        limit: Int = 10,
    ): OperationResult {
        val targetNamespace = resolveNamespace(namespace)
        requireNamespaceAllowed(targetNamespace)?.let { return it }
        log.info("Getting cron history: namespace=$targetNamespace, name=$name, limit=$limit")

        return runCatching {
            val history = argoClient.listWorkflows(
                namespace = targetNamespace,
                limit = limit.coerceAtLeast(1),
                labelSelector = "workflows.argoproj.io/cron-workflow=$name",
            )

            OperationResult.Success(
                message = if (history.isEmpty()) {
                    "No workflow history found for CronWorkflow '$name'"
                } else {
                    "Found ${history.size} workflow history item(s) for CronWorkflow '$name'"
                },
                data = buildMap {
                    put("namespace", targetNamespace)
                    put("cron_workflow", name)
                    put("count", history.size.toString())
                    if (history.isNotEmpty()) {
                        put("history", history.joinToString("\n") { it.toHistoryDisplayString() })
                    }
                },
            )
        }.getOrElse { error ->
            log.error("Failed to get CronWorkflow history namespace={}, name={}", targetNamespace, name, error)
            error.toOperationError("retrieve CronWorkflow history")
        }
    }

    suspend fun toggleCronSuspension(
        namespace: String,
        name: String,
        suspend: Boolean,
    ): OperationResult {
        val targetNamespace = resolveNamespace(namespace)
        val namespaceError = requireNamespaceAllowed(targetNamespace)
        log.info("Toggling cron suspension: namespace=$targetNamespace, name=$name, suspend=$suspend")

        return when {
            namespaceError != null -> namespaceError

            !allowMutations -> OperationResult.Error(
                message = "Mutation operations are not allowed by configuration",
                code = ERROR_CODE_PERMISSION_DENIED,
            )

            else -> runCatching {
                val cronWorkflow = if (suspend) {
                    argoClient.suspendCronWorkflow(targetNamespace, name)
                } else {
                    argoClient.resumeCronWorkflow(targetNamespace, name)
                }

                OperationResult.Success(
                    message = "CronWorkflow '${cronWorkflow.name}' ${if (suspend) "suspended" else "resumed"}",
                    data = mapOf(
                        "namespace" to cronWorkflow.namespace,
                        "cron_workflow" to cronWorkflow.name,
                        "suspended" to (cronWorkflow.suspended?.toString() ?: suspend.toString()),
                    ),
                )
            }.getOrElse { error ->
                log.error(
                    "Failed to toggle CronWorkflow suspension namespace={}, name={}",
                    targetNamespace,
                    name,
                    error,
                )
                error.toOperationError("toggle CronWorkflow suspension")
            }
        }
    }
}

private fun CronWorkflowSummary.toDisplayString(): String = buildString {
    append(name)
    append(" schedules=")
    append(schedules.joinToString(",").ifBlank { "n/a" })
    append(" suspended=")
    append(suspended?.toString() ?: "n/a")
    lastScheduledTime?.let { append(" lastScheduled=${formatInstant(it)}") }
    if (activeWorkflows.isNotEmpty()) {
        append(" active=")
        append(activeWorkflows.joinToString(","))
    }
}

private fun WorkflowSummary.toHistoryDisplayString(): String = buildString {
    append(name)
    append(" [")
    append(phase ?: "Unknown")
    append("]")
    append(" started=${formatInstant(startedAt)}")
    formatDuration(startedAt, finishedAt)?.let { append(" duration=$it") }
}
