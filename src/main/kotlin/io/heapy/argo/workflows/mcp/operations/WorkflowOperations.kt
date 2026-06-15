package io.heapy.argo.workflows.mcp.operations

import io.heapy.argo.client.ArgoWorkflowsClient
import io.heapy.argo.client.WorkflowLogs
import io.heapy.argo.client.WorkflowSummary
import io.heapy.komok.tech.logging.Logger

class WorkflowOperations(
    private val defaultNamespace: String,
    private val allowDestructive: Boolean,
    private val allowMutations: Boolean,
    private val requireConfirmation: Boolean,
    private val namespacePolicy: NamespacePolicy = NamespacePolicy(),
    private val argoClient: ArgoWorkflowsClient,
) {
    private companion object : Logger()

    private fun resolveNamespace(namespace: String?): String =
        namespace?.takeIf { it.isNotBlank() } ?: defaultNamespace

    private fun requireNamespaceAllowed(namespace: String): OperationResult.Error? =
        namespaceDeniedError(namespace, namespacePolicy)

    private fun namespaceAllowed(namespace: String): Boolean =
        namespaceDeniedError(namespace, namespacePolicy) == null

    suspend fun listWorkflows(
        namespace: String? = null,
        status: String? = null,
        limit: Int = 50,
    ): OperationResult {
        // An empty namespace makes Argo (GET /api/v1/workflows/) return workflows
        // from every namespace the connection can access.
        val targetNamespace = namespace?.trim().orEmpty()
        val allNamespaces = targetNamespace.isEmpty()

        if (!allNamespaces) {
            requireNamespaceAllowed(targetNamespace)?.let { return it }
        }
        val scopeLog = if (allNamespaces) "<all>" else targetNamespace
        log.info("Listing workflows: namespace=$scopeLog, status=$status, limit=$limit")

        return runCatching {
            val workflows = argoClient.listWorkflows(targetNamespace, limit)
            val statusFiltered = status?.let { desired ->
                val desiredLower = desired.lowercase()
                workflows.filter { it.phase?.lowercase() == desiredLower }
            } ?: workflows

            // When listing across all namespaces, drop workflows from namespaces
            // excluded by the allow/deny settings instead of failing the whole call.
            val filtered = if (allNamespaces) {
                statusFiltered.filter { namespaceAllowed(it.namespace) }
            } else {
                statusFiltered
            }

            val scope = if (allNamespaces) "all namespaces" else "namespace '$targetNamespace'"
            val message = if (filtered.isEmpty()) {
                "No workflows found in $scope"
            } else {
                "Found ${filtered.size} workflow(s) in $scope"
            }

            val data = mutableMapOf(
                "namespace" to if (allNamespaces) "*" else targetNamespace,
                "count" to filtered.size.toString(),
            )
            status?.let { data["status_filter"] = it }
            if (filtered.isNotEmpty()) {
                data["workflows"] = filtered.joinToString(separator = "\n") {
                    it.toDisplayString(includeNamespace = allNamespaces)
                }
            }

            OperationResult.Success(
                message = message,
                data = data,
            )
        }.getOrElse { error ->
            log.error("Failed to list workflows for namespace={}", scopeLog, error)
            error.toOperationError("list workflows")
        }
    }

    suspend fun getWorkflow(
        namespace: String,
        name: String,
    ): OperationResult {
        val targetNamespace = resolveNamespace(namespace)
        requireNamespaceAllowed(targetNamespace)?.let { return it }
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
        requireNamespaceAllowed(targetNamespace)?.let { return it }
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

    suspend fun terminateWorkflow(
        namespace: String,
        name: String,
        reason: String,
        dryRun: Boolean = true,
        confirmationToken: String? = null,
    ): OperationResult {
        val targetNamespace = resolveNamespace(namespace)
        requireNamespaceAllowed(targetNamespace)?.let { return it }
        log.info("Terminating workflow: namespace=$targetNamespace, name=$name, reason=$reason, dryRun=$dryRun")

        val expectedToken = confirmationTokenFor(targetNamespace, name)

        return when {
            !allowDestructive -> OperationResult.Error(
                message = "Destructive operations are not allowed by configuration",
                code = ERROR_CODE_PERMISSION_DENIED,
            )

            dryRun -> OperationResult.DryRun(
                preview = """
                    Would request workflow termination from Argo
                    - Namespace: $targetNamespace
                    - Workflow: $name
                    - Reason: $reason
                    - Impact: Argo will stop the workflow according to its terminate behavior
                """.trimIndent(),
                instructions = "Call again with dryRun=false and confirmationToken='$expectedToken'",
            )

            requireConfirmation && confirmationToken == null ->
                OperationResult.NeedsConfirmation(
                    preview = "Workflow '$name' in namespace '$targetNamespace' will be terminated by Argo",
                    token = expectedToken,
                )

            requireConfirmation && confirmationToken != expectedToken ->
                OperationResult.Error(
                    message = "Invalid confirmation token for workflow '$targetNamespace/$name'",
                    code = ERROR_CODE_PERMISSION_DENIED,
                )

            else -> runCatching {
                val workflow = argoClient.terminateWorkflow(targetNamespace, name)
                OperationResult.Success(
                    message = "Workflow '${workflow.name}' termination requested",
                    data = mapOf(
                        "namespace" to workflow.namespace,
                        "workflow" to workflow.name,
                        "status" to (workflow.phase ?: "Unknown"),
                        "reason" to reason,
                        "action" to "terminated",
                    ),
                )
            }.getOrElse { error ->
                log.error("Failed to terminate workflow namespace={}, name={}", targetNamespace, name, error)
                error.toOperationError("terminate workflow")
            }
        }
    }

    suspend fun retryWorkflow(
        namespace: String,
        name: String,
        restartSuccessful: Boolean = false,
    ): OperationResult {
        val targetNamespace = resolveNamespace(namespace)
        val namespaceError = requireNamespaceAllowed(targetNamespace)
        log.info("Retrying workflow: namespace=$targetNamespace, name=$name, restartSuccessful=$restartSuccessful")

        return when {
            namespaceError != null -> namespaceError

            !allowMutations -> OperationResult.Error(
                message = "Mutation operations are not allowed by configuration",
                code = ERROR_CODE_PERMISSION_DENIED,
            )

            else -> runCatching {
                val workflow = argoClient.retryWorkflow(targetNamespace, name, restartSuccessful)
                OperationResult.Success(
                    message = "Workflow '${workflow.name}' retry requested",
                    data = mapOf(
                        "namespace" to workflow.namespace,
                        "workflow" to workflow.name,
                        "status" to (workflow.phase ?: "Unknown"),
                        "restart_successful" to restartSuccessful.toString(),
                    ),
                )
            }.getOrElse { error ->
                log.error("Failed to retry workflow namespace={}, name={}", targetNamespace, name, error)
                error.toOperationError("retry workflow")
            }
        }
    }
}

private fun confirmationTokenFor(namespace: String, name: String): String =
    "terminate:$namespace:$name"

private const val DEFAULT_LOG_LINE_LIMIT = 200

private fun WorkflowSummary.toDisplayString(includeNamespace: Boolean = false): String = buildString {
    if (includeNamespace) {
        append(namespace)
        append("/")
    }
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

private data class LogFormatting(
    val rendered: String,
    val note: String?,
    val totalLines: Int,
    val matchedLines: Int,
    val returnedLines: Int,
)
