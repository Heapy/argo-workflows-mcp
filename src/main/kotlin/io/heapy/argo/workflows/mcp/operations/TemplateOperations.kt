package io.heapy.argo.workflows.mcp.operations

import io.heapy.argo.client.ArgoWorkflowsClient
import io.heapy.argo.client.WorkflowTemplateSummary
import io.heapy.komok.tech.logging.Logger

/**
 * Template operations (WorkflowTemplate and ClusterWorkflowTemplate).
 */
class TemplateOperations(
    private val defaultNamespace: String,
    private val namespacePolicy: NamespacePolicy = NamespacePolicy(),
    private val argoClient: ArgoWorkflowsClient,
) {
    private companion object : Logger()

    private fun resolveNamespace(namespace: String?): String =
        namespace?.takeIf { it.isNotBlank() } ?: defaultNamespace

    private fun requireNamespaceAllowed(namespace: String): OperationResult.Error? =
        namespaceDeniedError(namespace, namespacePolicy)

    suspend fun listWorkflowTemplates(
        namespace: String? = null,
        labelSelector: String? = null,
    ): OperationResult {
        val targetNamespace = resolveNamespace(namespace)
        requireNamespaceAllowed(targetNamespace)?.let { return it }
        log.info("Listing workflow templates: namespace=$targetNamespace, labelSelector=$labelSelector")

        return runCatching {
            val templates = argoClient.listWorkflowTemplates(targetNamespace, labelSelector)

            OperationResult.Success(
                message = if (templates.isEmpty()) {
                    "No WorkflowTemplates found in namespace '$targetNamespace'"
                } else {
                    "Found ${templates.size} WorkflowTemplate(s) in namespace '$targetNamespace'"
                },
                data = buildMap {
                    put("namespace", targetNamespace)
                    put("count", templates.size.toString())
                    labelSelector?.let { put("label_selector", it) }
                    if (templates.isNotEmpty()) {
                        put("templates", templates.joinToString("\n") { it.toDisplayString() })
                    }
                },
            )
        }.getOrElse { error ->
            log.error("Failed to list WorkflowTemplates namespace={}", targetNamespace, error)
            error.toOperationError("list WorkflowTemplates")
        }
    }

    suspend fun getWorkflowTemplate(
        namespace: String,
        name: String,
    ): OperationResult {
        val targetNamespace = resolveNamespace(namespace)
        requireNamespaceAllowed(targetNamespace)?.let { return it }
        log.info("Getting workflow template: namespace=$targetNamespace, name=$name")

        return runCatching {
            val detail = argoClient.getWorkflowTemplate(targetNamespace, name)
            val summary = detail.summary

            OperationResult.Success(
                message = "WorkflowTemplate '${summary.name}' retrieved from namespace '$targetNamespace'",
                data = templateData(summary) + buildMap {
                    put("namespace", targetNamespace)
                    if (detail.parameters.isNotEmpty()) {
                        put("parameters", detail.parameters.formatEntries(separator = "\n", assignment = " = "))
                    }
                    if (detail.annotations.isNotEmpty()) {
                        put("annotations", detail.annotations.formatEntries())
                    }
                },
            )
        }.getOrElse { error ->
            log.error("Failed to get WorkflowTemplate namespace={}, name={}", targetNamespace, name, error)
            error.toOperationError("retrieve WorkflowTemplate")
        }
    }

    suspend fun listClusterWorkflowTemplates(
        labelSelector: String? = null,
    ): OperationResult {
        log.info("Listing cluster workflow templates: labelSelector=$labelSelector")

        return runCatching {
            val templates = argoClient.listClusterWorkflowTemplates(labelSelector)

            OperationResult.Success(
                message = if (templates.isEmpty()) {
                    "No ClusterWorkflowTemplates found"
                } else {
                    "Found ${templates.size} ClusterWorkflowTemplate(s)"
                },
                data = buildMap {
                    put("count", templates.size.toString())
                    labelSelector?.let { put("label_selector", it) }
                    if (templates.isNotEmpty()) {
                        put("templates", templates.joinToString("\n") { it.toDisplayString() })
                    }
                },
            )
        }.getOrElse { error ->
            log.error("Failed to list ClusterWorkflowTemplates", error)
            error.toOperationError("list ClusterWorkflowTemplates")
        }
    }

    suspend fun getClusterWorkflowTemplate(
        name: String,
    ): OperationResult {
        log.info("Getting cluster workflow template: name=$name")

        return runCatching {
            val detail = argoClient.getClusterWorkflowTemplate(name)
            val summary = detail.summary

            OperationResult.Success(
                message = "ClusterWorkflowTemplate '${summary.name}' retrieved",
                data = templateData(summary) + buildMap {
                    if (detail.parameters.isNotEmpty()) {
                        put("parameters", detail.parameters.formatEntries(separator = "\n", assignment = " = "))
                    }
                    if (detail.annotations.isNotEmpty()) {
                        put("annotations", detail.annotations.formatEntries())
                    }
                },
            )
        }.getOrElse { error ->
            log.error("Failed to get ClusterWorkflowTemplate name={}", name, error)
            error.toOperationError("retrieve ClusterWorkflowTemplate")
        }
    }
}

private fun WorkflowTemplateSummary.toDisplayString(): String = buildString {
    append(name)
    entrypoint?.let { append(" entrypoint=$it") }
    append(" templates=$templateCount")
}

private fun templateData(summary: WorkflowTemplateSummary): Map<String, String> = buildMap {
    put("name", summary.name)
    summary.namespace?.let { put("namespace", it) }
    put("entrypoint", summary.entrypoint ?: "n/a")
    put("template_count", summary.templateCount.toString())
    if (summary.labels.isNotEmpty()) {
        put("labels", summary.labels.formatEntries())
    }
}
