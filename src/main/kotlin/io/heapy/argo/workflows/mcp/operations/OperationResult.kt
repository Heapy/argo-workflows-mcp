package io.heapy.argo.workflows.mcp.operations

import kotlinx.serialization.Serializable

/**
 * Result wrapper for operation responses
 */
@Serializable
sealed class OperationResult {
    @Serializable
    data class Success(
        val message: String,
        val data: Map<String, String> = emptyMap()
    ) : OperationResult()

    @Serializable
    data class NeedsConfirmation(
        val preview: String,
        val token: String
    ) : OperationResult()

    @Serializable
    data class DryRun(
        val preview: String,
        val instructions: String
    ) : OperationResult()

    @Serializable
    data class Error(
        val message: String,
        val code: String = "OPERATION_FAILED"
    ) : OperationResult()

    @Serializable
    data class Cancelled(
        val reason: String
    ) : OperationResult()
}
