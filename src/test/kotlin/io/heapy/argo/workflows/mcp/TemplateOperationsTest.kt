package io.heapy.argo.workflows.mcp

import io.heapy.argo.workflows.mcp.operations.OperationResult
import io.heapy.argo.workflows.mcp.operations.TemplateOperations
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TemplateOperationsTest {
    private val templateOps = TemplateOperations()

    @Test
    fun `listWorkflowTemplates returns mock data`() = runTest {
        val result = templateOps.listWorkflowTemplates(namespace = "default")

        assertTrue(result is OperationResult.Success)
        val success = result as OperationResult.Success
        assertTrue(success.message.contains("templates"))
    }

    @Test
    fun `getWorkflowTemplate returns template details`() = runTest {
        val result = templateOps.getWorkflowTemplate(namespace = "default", name = "build-template")

        assertTrue(result is OperationResult.Success)
        val success = result as OperationResult.Success
        assertEquals("build-template", success.data["name"])
        assertNotNull(success.data["entrypoint"])
    }

    @Test
    fun `listClusterWorkflowTemplates returns cluster-scoped templates`() = runTest {
        val result = templateOps.listClusterWorkflowTemplates()

        assertTrue(result is OperationResult.Success)
        val success = result as OperationResult.Success
        assertTrue(success.message.contains("cluster"))
    }
}