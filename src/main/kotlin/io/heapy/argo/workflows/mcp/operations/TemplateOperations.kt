package io.heapy.argo.workflows.mcp.operations

import io.heapy.komok.tech.logging.Logger

/**
 * Template operations (WorkflowTemplate and ClusterWorkflowTemplate)
 */
class TemplateOperations {
    private companion object : Logger()

    /**
     * List WorkflowTemplates
     */
    fun listWorkflowTemplates(
        namespace: String? = null,
        labelSelector: String? = null
    ): OperationResult {
        log.info("Listing workflow templates: namespace=$namespace, labelSelector=$labelSelector")

        return OperationResult.Success(
            message = "Mock: Found 4 workflow templates",
            data = mapOf(
                "count" to "4",
                "templates" to "build-template, test-template, deploy-template, cleanup-template"
            )
        )
    }

    /**
     * Get WorkflowTemplate details
     */
    fun getWorkflowTemplate(
        namespace: String,
        name: String
    ): OperationResult {
        log.info("Getting workflow template: namespace=$namespace, name=$name")

        return OperationResult.Success(
            message = "Mock: WorkflowTemplate retrieved",
            data = mapOf(
                "name" to name,
                "namespace" to namespace,
                "entrypoint" to "main",
                "steps" to "step1, step2, step3"
            )
        )
    }

    /**
     * List ClusterWorkflowTemplates
     */
    fun listClusterWorkflowTemplates(
        labelSelector: String? = null
    ): OperationResult {
        log.info("Listing cluster workflow templates: labelSelector=$labelSelector")

        return OperationResult.Success(
            message = "Mock: Found 2 cluster workflow templates",
            data = mapOf(
                "count" to "2",
                "templates" to "global-build, global-deploy"
            )
        )
    }

    /**
     * Get ClusterWorkflowTemplate details
     */
    fun getClusterWorkflowTemplate(
        name: String
    ): OperationResult {
        log.info("Getting cluster workflow template: name=$name")

        return OperationResult.Success(
            message = "Mock: ClusterWorkflowTemplate retrieved",
            data = mapOf(
                "name" to name,
                "entrypoint" to "main",
                "steps" to "step1, step2"
            )
        )
    }
}
