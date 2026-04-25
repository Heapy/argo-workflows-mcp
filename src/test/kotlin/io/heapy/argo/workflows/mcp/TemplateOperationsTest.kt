package io.heapy.argo.workflows.mcp

import io.heapy.argo.client.WorkflowTemplateSummary
import io.heapy.argo.workflows.mcp.operations.OperationResult
import io.heapy.argo.workflows.mcp.operations.TemplateOperations
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TemplateOperationsTest {
    private fun createOps(
        fakeClient: FakeArgoWorkflowsClient = FakeArgoWorkflowsClient(),
        namespacesAllow: String = "*",
        namespacesDeny: String = "",
    ) = TemplateOperations(
        defaultNamespace = "default",
        namespacesAllow = namespacesAllow,
        namespacesDeny = namespacesDeny,
        argoClient = fakeClient,
    )

    @Test
    fun `listWorkflowTemplates returns Argo data`() = runTest {
        val fakeClient = FakeArgoWorkflowsClient().apply {
            listWorkflowTemplatesResult = listOf(
                WorkflowTemplateSummary(
                    name = "build-template",
                    namespace = "default",
                    entrypoint = "main",
                    templateCount = 2,
                    labels = emptyMap(),
                ),
            )
        }
        val templateOps = createOps(fakeClient)

        val result = templateOps.listWorkflowTemplates(namespace = "default", labelSelector = "app=build")

        assertTrue(result is OperationResult.Success)
        val success = result as OperationResult.Success
        assertEquals("1", success.data["count"])
        assertTrue(success.data["templates"]?.contains("build-template") ?: false)
        assertEquals(listOf("listWorkflowTemplates:default:app=build"), fakeClient.calls)
    }

    @Test
    fun `getWorkflowTemplate returns template details`() = runTest {
        val templateOps = createOps()

        val result = templateOps.getWorkflowTemplate(namespace = "default", name = "build-template")

        assertTrue(result is OperationResult.Success)
        val success = result as OperationResult.Success
        assertEquals("build-template", success.data["name"])
        assertNotNull(success.data["entrypoint"])
    }

    @Test
    fun `listClusterWorkflowTemplates returns cluster-scoped templates`() = runTest {
        val fakeClient = FakeArgoWorkflowsClient().apply {
            listClusterWorkflowTemplatesResult = listOf(
                WorkflowTemplateSummary(
                    name = "global-build",
                    namespace = null,
                    entrypoint = "main",
                    templateCount = 1,
                    labels = emptyMap(),
                ),
            )
        }
        val templateOps = createOps(fakeClient)

        val result = templateOps.listClusterWorkflowTemplates()

        assertTrue(result is OperationResult.Success)
        val success = result as OperationResult.Success
        assertTrue(success.message.contains("ClusterWorkflowTemplate"))
        assertEquals(listOf("listClusterWorkflowTemplates:"), fakeClient.calls)
    }

    @Test
    fun `getWorkflowTemplate rejects denied namespace before calling Argo`() = runTest {
        val fakeClient = FakeArgoWorkflowsClient()
        val templateOps = createOps(
            fakeClient = fakeClient,
            namespacesAllow = "default",
            namespacesDeny = "blocked",
        )

        val result = templateOps.getWorkflowTemplate(namespace = "blocked", name = "build-template")

        assertTrue(result is OperationResult.Error)
        assertEquals("NAMESPACE_DENIED", (result as OperationResult.Error).code)
        assertTrue(fakeClient.calls.isEmpty())
    }
}
