package io.heapy.argo.workflows.mcp.operations

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

internal const val ERROR_CODE_ARGO_API = "ARGO_API_ERROR"
internal const val ERROR_CODE_NAMESPACE_DENIED = "NAMESPACE_DENIED"
internal const val ERROR_CODE_PERMISSION_DENIED = "PERMISSION_DENIED"

internal fun String.toNamespaceSet(): Set<String> =
    split(',')
        .map { it.trim() }
        .filterTo(mutableSetOf()) { it.isNotEmpty() }

internal fun namespaceDeniedError(
    namespace: String,
    namespacesAllow: String,
    namespacesDeny: String,
): OperationResult.Error? {
    val allowedNamespaces = namespacesAllow.toNamespaceSet()
    val deniedNamespaces = namespacesDeny.toNamespaceSet()
    val isAllowed = "*" in allowedNamespaces || namespace in allowedNamespaces
    val isDenied = namespace in deniedNamespaces
    return if (isAllowed && !isDenied) {
        null
    } else {
        OperationResult.Error(
            message = "Namespace '$namespace' is not allowed by configuration",
            code = ERROR_CODE_NAMESPACE_DENIED,
        )
    }
}

internal fun Throwable.toOperationError(action: String): OperationResult.Error {
    val detail = message?.takeIf { it.isNotBlank() } ?: this::class.simpleName ?: "unknown error"
    return OperationResult.Error(
        message = "Failed to $action: $detail",
        code = ERROR_CODE_ARGO_API,
    )
}

internal fun formatDuration(startedAt: Instant?, finishedAt: Instant?): String? {
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

internal fun formatInstant(instant: Instant?): String =
    instant?.toString() ?: "n/a"
