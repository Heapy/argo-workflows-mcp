package io.heapy.argo.workflows.mcp.operations

import io.heapy.argo.client.ArgoWorkflowsClient
import io.heapy.argo.client.WorkflowLogs
import io.heapy.argo.client.WorkflowSummary
import io.heapy.komok.tech.logging.Logger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class WorkflowOperations(
    private val defaultNamespace: String,
    private val allowDestructive: Boolean,
    private val allowMutations: Boolean,
    private val requireConfirmation: Boolean,
    private val argoClient: ArgoWorkflowsClient,
) {
    private companion object : Logger()

    private fun resolveNamespace(namespace: String?): String =
        namespace?.takeIf { it.isNotBlank() } ?: defaultNamespace

    suspend fun listWorkflows(
        namespace: String? = null,
        status: String? = null,
        limit: Int = 50,
    ): OperationResult {
        val targetNamespace = resolveNamespace(namespace)
        log.info("Listing workflows: namespace=$targetNamespace, status=$status, limit=$limit")

        return runCatching {
            val workflows = argoClient.listWorkflows(targetNamespace, limit)
            val filtered = status?.let { desired ->
                val desiredLower = desired.lowercase()
                workflows.filter { it.phase?.lowercase() == desiredLower }
            } ?: workflows

            val message = if (filtered.isEmpty()) {
                "No workflows found in namespace '$targetNamespace'"
            } else {
                "Found ${filtered.size} workflow(s) in namespace '$targetNamespace'"
            }

            val data = mutableMapOf(
                "namespace" to targetNamespace,
                "count" to filtered.size.toString(),
            )
            status?.let { data["status_filter"] = it }
            if (filtered.isNotEmpty()) {
                data["workflows"] = filtered.joinToString(separator = "\n") { it.toDisplayString() }
            }

            OperationResult.Success(
                message = message,
                data = data,
            )
        }.getOrElse { error ->
            log.error("Failed to list workflows for namespace={}", targetNamespace, error)
            error.toOperationError("list workflows")
        }
    }

    suspend fun getWorkflow(
        namespace: String,
        name: String,
    ): OperationResult {
        val targetNamespace = resolveNamespace(namespace)
        log.info("Getting workflow: namespace=$targetNamespace, name=$name")

        return runCatching {
            val detail = argoClient.getWorkflow(targetNamespace, name)
            val summary = detail.summary
            val duration = formatDuration(summary.startedAt, summary.finishedAt)

            val data = mutableMapOf(
                "namespace" to summary.namespace,
                "status" to (summary.phase ?: "Unknown"),
                "progress" to (summary.progress ?: "n/a"),
                "started_at" to formatInstant(summary.startedAt),
                "finished_at" to formatInstant(summary.finishedAt),
                "duration" to (duration ?: "n/a"),
            )

            detail.message
                ?.takeIf { it.isNotBlank() }
                ?.let { data["message"] = it }
            if (detail.labels.isNotEmpty()) {
                data["labels"] = detail.labels.entries
                    .joinToString(", ") { (k, v) -> "$k=$v" }
            }
            if (detail.annotations.isNotEmpty()) {
                data["annotations"] = detail.annotations.entries
                    .joinToString(", ") { (k, v) -> "$k=$v" }
            }
            if (detail.parameters.isNotEmpty()) {
                data["parameters"] = detail.parameters.entries
                    .joinToString("\n") { (k, v) -> "$k = $v" }
            }
            if (detail.outputs.isNotEmpty()) {
                data["outputs"] = detail.outputs.entries
                    .joinToString("\n") { (k, v) -> "$k = $v" }
            }

            OperationResult.Success(
                message = "Workflow '${summary.name}' status: ${summary.phase ?: "Unknown"}",
                data = data,
            )
        }.getOrElse { error ->
            log.error(
                "Failed to get workflow details namespace={}, name={}",
                targetNamespace,
                name,
                error,
            )
            error.toOperationError("retrieve workflow details")
        }
    }

    @Suppress("LongParameterList")
    suspend fun getWorkflowLogs(
        namespace: String,
        workflowName: String,
        podName: String? = null,
        container: String = "main",
        search: String? = null,
        maxLines: Int = DEFAULT_LOG_LINE_LIMIT,
    ): OperationResult {
        val targetNamespace = resolveNamespace(namespace)
        log.info(
            "Getting logs: namespace={}, workflow={}, pod={}, container={}, search='{}', maxLines={}",
            targetNamespace,
            workflowName,
            podName,
            container,
            search,
            maxLines,
        )

        return runCatching {
            val logs = argoClient.getWorkflowLogs(
                namespace = targetNamespace,
                workflowName = workflowName,
                podName = podName,
                container = container,
            )
            val formatting = logs.formatForDisplay(search, maxLines)

            val data = mutableMapOf(
                "namespace" to targetNamespace,
                "workflow" to workflowName,
                "container" to container,
                "total_lines" to formatting.totalLines.toString(),
                "matching_lines" to formatting.matchedLines.toString(),
                "returned_lines" to formatting.returnedLines.toString(),
                "logs" to formatting.rendered,
            )
            podName?.let { data["pod"] = it }
            search?.let { data["search_term"] = it }
            if (maxLines > 0) {
                data["max_lines"] = maxLines.toString()
            }
            formatting.note?.let { data["note"] = it }

            OperationResult.Success(
                message = when {
                    formatting.returnedLines == 0 && search != null ->
                        "No log lines matching '$search' for '$workflowName'"

                    formatting.returnedLines == 0 ->
                        "No logs returned for '$workflowName'"

                    search != null ->
                        "Retrieved ${formatting.returnedLines} of ${formatting.matchedLines} matching log line(s) for '$workflowName'"

                    else ->
                        "Retrieved ${formatting.returnedLines} of ${formatting.totalLines} log line(s) for '$workflowName'"
                },
                data = data,
            )
        }.getOrElse { error ->
            log.error(
                "Failed to fetch workflow logs namespace={}, workflow={}",
                targetNamespace,
                workflowName,
                error,
            )
            error.toOperationError("fetch workflow logs")
        }
    }

    fun terminateWorkflow(
        namespace: String,
        name: String,
        reason: String,
        dryRun: Boolean = true,
        confirmationToken: String? = null,
    ): OperationResult {
        log.info("Terminating workflow: namespace=$namespace, name=$name, reason=$reason, dryRun=$dryRun")

        return when {
            !allowDestructive -> OperationResult.Error(
                message = "Destructive operations are not allowed by configuration",
                code = "PERMISSION_DENIED",
            )

            dryRun -> OperationResult.DryRun(
                preview = """
                    Mock: Would terminate workflow
                    - Namespace: $namespace
                    - Workflow: $name
                    - Reason: $reason
                    - Running pods: 3
                    - Impact: All pods will be stopped immediately
                """.trimIndent(),
                instructions = "Call again with dryRun=false and confirmationToken='mock-token-123'",
            )

            requireConfirmation && confirmationToken == null ->
                OperationResult.NeedsConfirmation(
                    preview = "Mock: Workflow $name will be terminated",
                    token = "mock-token-123",
                )

            else -> OperationResult.Success(
                message = "Mock: Workflow terminated successfully",
                data = mapOf(
                    "namespace" to namespace,
                    "workflow" to name,
                    "action" to "terminated",
                ),
            )
        }
    }

    fun retryWorkflow(
        namespace: String,
        name: String,
        restartSuccessful: Boolean = false,
    ): OperationResult {
        log.info("Retrying workflow: namespace=$namespace, name=$name, restartSuccessful=$restartSuccessful")

        if (!allowMutations) {
            return OperationResult.Error(
                message = "Mutation operations are not allowed by configuration",
                code = "PERMISSION_DENIED",
            )
        }

        return OperationResult.Success(
            message = "Mock: Workflow retry initiated",
            data = mapOf(
                "namespace" to namespace,
                "originalWorkflow" to name,
                "newWorkflow" to "$name-retry-1",
            ),
        )
    }
}

private const val ERROR_CODE_ARGO_API = "ARGO_API_ERROR"
private const val DEFAULT_LOG_LINE_LIMIT = 200

private fun WorkflowSummary.toDisplayString(): String = buildString {
    append(name)
    append(" [")
    append(phase ?: "Unknown")
    append("]")
    progress?.let { append(" progress=$it") }
    append(" started=${formatInstant(startedAt)}")
    formatDuration(startedAt, finishedAt)?.let { append(" duration=$it") }
}

@Suppress("CyclomaticComplexMethod")
private fun WorkflowLogs.formatForDisplay(
    search: String?,
    maxLines: Int,
): LogFormatting {
    if (entries.isEmpty()) {
        return LogFormatting(
            rendered = "",
            note = null,
            totalLines = 0,
            matchedLines = 0,
            returnedLines = 0,
        )
    }

    val sanitizedSearch = search?.takeIf { it.isNotBlank() }
    val filtered = sanitizedSearch?.let { query ->
        entries.filter { entry ->
            entry.content.contains(query, ignoreCase = true) ||
                (entry.podName?.contains(query, ignoreCase = true) == true)
        }
    } ?: entries

    val total = entries.size
    val matched = filtered.size
    val limit = maxLines.takeIf { it > 0 }
    val subset = if (limit != null && matched > limit) filtered.takeLast(limit) else filtered
    val truncated = limit != null && matched > limit

    val rendered = subset.joinToString("\n") { entry ->
        val podPrefix = entry.podName
            ?.let { "[$it] " }.orEmpty()
        "$podPrefix${entry.content}"
    }

    val note = when {
        sanitizedSearch != null && matched == 0 ->
            "No lines matched search '$sanitizedSearch'. Total lines: $total."

        sanitizedSearch != null && truncated ->
            "Showing last ${subset.size} of $matched matching lines (search='$sanitizedSearch'). Total lines: $total."

        sanitizedSearch != null ->
            "Search matched $matched line(s) out of $total."

        truncated ->
            "Showing last ${subset.size} of $matched lines. Total lines: $total."

        else -> null
    }

    return LogFormatting(
        rendered = rendered,
        note = note,
        totalLines = total,
        matchedLines = matched,
        returnedLines = subset.size,
    )
}

private fun formatDuration(startedAt: Instant?, finishedAt: Instant?): String? {
    val start = startedAt ?: return null
    val duration = (finishedAt ?: Clock.System.now()) - start
    return duration.takeIf { !it.isNegative() }?.formatParts()
}

private fun Duration.formatParts(): String =
    toComponents { days, hours, minutes, seconds, _ ->
        buildList {
            if (days > 0L) add("${days}d")
            if (hours > 0) add("${hours}h")
            if (minutes > 0) add("${minutes}m")
            if (seconds > 0 || isEmpty()) add("${seconds}s")
        }.joinToString(" ")
    }

private fun formatInstant(instant: Instant?): String =
    instant?.toString() ?: "n/a"

private fun Throwable.toOperationError(action: String): OperationResult.Error {
    val detail = message?.takeIf { it.isNotBlank() } ?: this::class.simpleName ?: "unknown error"
    return OperationResult.Error(
        message = "Failed to $action: $detail",
        code = ERROR_CODE_ARGO_API,
    )
}

private data class LogFormatting(
    val rendered: String,
    val note: String?,
    val totalLines: Int,
    val matchedLines: Int,
    val returnedLines: Int,
)
