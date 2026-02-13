package io.heapy.argo.workflows.mcp

import io.heapy.argo.client.WorkflowDetail
import io.heapy.argo.client.WorkflowLogEntry
import io.heapy.argo.client.WorkflowLogs
import io.heapy.argo.client.WorkflowSummary
import io.heapy.argo.workflows.mcp.operations.OperationResult
import io.heapy.argo.workflows.mcp.operations.WorkflowOperations
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class WorkflowOperationsTest {
    private fun createOps(
        fakeClient: FakeArgoWorkflowsClient = FakeArgoWorkflowsClient(),
        allowDestructive: Boolean = false,
        allowMutations: Boolean = false,
        requireConfirmation: Boolean = true,
    ) = WorkflowOperations(
        defaultNamespace = "default",
        allowDestructive = allowDestructive,
        allowMutations = allowMutations,
        requireConfirmation = requireConfirmation,
        argoClient = fakeClient,
    )

    @Test
    fun `listWorkflows filters by status`() = runTest {
        val fakeClient = FakeArgoWorkflowsClient().apply {
            listWorkflowsResult = listOf(
                WorkflowSummary(
                    name = "wf-running",
                    namespace = "default",
                    phase = "Running",
                    progress = "1/3",
                    startedAt = Instant.parse("2024-01-01T00:00:00Z"),
                    finishedAt = null,
                ),
                WorkflowSummary(
                    name = "wf-succeeded",
                    namespace = "default",
                    phase = "Succeeded",
                    progress = "3/3",
                    startedAt = Instant.parse("2024-01-01T01:00:00Z"),
                    finishedAt = Instant.parse("2024-01-01T01:05:00Z"),
                ),
            )
        }
        val ops = createOps(fakeClient)

        val result = ops.listWorkflows(namespace = "default", status = "Succeeded", limit = 10)

        assertTrue(result is OperationResult.Success)
        val success = result as OperationResult.Success
        assertEquals("default", success.data["namespace"])
        assertEquals("1", success.data["count"])
        assertTrue(success.data["workflows"]?.contains("wf-succeeded") ?: false)
        assertFalse(success.data["workflows"]?.contains("wf-running") ?: false)
    }

    @Test
    fun `getWorkflow formats detail data`() = runTest {
        val detail = WorkflowDetail(
            summary = WorkflowSummary(
                name = "test-workflow",
                namespace = "default",
                phase = "Running",
                progress = "2/5",
                startedAt = Instant.parse("2024-01-01T00:00:00Z"),
                finishedAt = null,
            ),
            message = "Workflow progressing",
            labels = mapOf("app" to "demo"),
            annotations = mapOf("owner" to "team-a"),
            parameters = mapOf("image" to "alpine:3.19"),
            outputs = mapOf("result" to "pending"),
            raw = JsonObject(emptyMap()),
        )
        val fakeClient = FakeArgoWorkflowsClient().apply {
            workflowDetailResult = detail
        }
        val ops = createOps(fakeClient)

        val result = ops.getWorkflow(namespace = "default", name = "test-workflow")

        assertTrue(result is OperationResult.Success)
        val success = result as OperationResult.Success
        assertEquals("default", success.data["namespace"])
        assertEquals("Running", success.data["status"])
        assertEquals("2/5", success.data["progress"])
        assertTrue(success.data["labels"]?.contains("app=demo") ?: false)
        assertTrue(success.data["parameters"]?.contains("image = alpine:3.19") ?: false)
        assertEquals("Workflow 'test-workflow' status: Running", success.message)
    }

    @Test
    fun `getWorkflowLogs aggregates entries`() = runTest {
        val logs = WorkflowLogs(
            entries = listOf(
                WorkflowLogEntry("pod-1", "line one"),
                WorkflowLogEntry("pod-1", "line two"),
                WorkflowLogEntry("pod-2", "line three"),
            ),
        )
        val fakeClient = FakeArgoWorkflowsClient().apply {
            workflowLogsResult = logs
        }
        val ops = createOps(fakeClient)

        val result = ops.getWorkflowLogs(namespace = "default", workflowName = "demo", podName = null)

        assertTrue(result is OperationResult.Success)
        val success = result as OperationResult.Success
        assertEquals("Retrieved 3 of 3 log line(s) for 'demo'", success.message)
        assertEquals("3", success.data["total_lines"])
        assertEquals("3", success.data["matching_lines"])
        assertEquals("3", success.data["returned_lines"])
        assertTrue(success.data["logs"]?.contains("[pod-1] line one") ?: false)
        assertTrue(success.data["logs"]?.contains("[pod-2] line three") ?: false)
    }

    @Test
    fun `getWorkflowLogs supports search filter`() = runTest {
        val logs = WorkflowLogs(
            entries = listOf(
                WorkflowLogEntry("pod-1", "campaign count: 10"),
                WorkflowLogEntry("pod-1", "progress update"),
                WorkflowLogEntry("pod-2", "campaign count: 12"),
                WorkflowLogEntry("pod-2", "error: failed to sync"),
            ),
        )
        val fakeClient = FakeArgoWorkflowsClient().apply {
            workflowLogsResult = logs
        }
        val ops = createOps(fakeClient)

        val result = ops.getWorkflowLogs(
            namespace = "default",
            workflowName = "demo",
            search = "campaign",
            maxLines = 1,
        )

        assertTrue(result is OperationResult.Success)
        val success = result as OperationResult.Success
        assertEquals("Retrieved 1 of 2 matching log line(s) for 'demo'", success.message)
        assertEquals("4", success.data["total_lines"])
        assertEquals("2", success.data["matching_lines"])
        assertEquals("1", success.data["returned_lines"])
        assertTrue(success.data["logs"]?.contains("campaign count: 12") ?: false)
        assertEquals("campaign", success.data["search_term"])
        assertTrue(success.data["note"]?.contains("Showing last 1 of 2 matching lines") ?: false)
    }

    @Test
    fun `terminateWorkflow requires confirmation when destructive operations disabled`() = runTest {
        val ops = createOps()

        val result = ops.terminateWorkflow(
            namespace = "default",
            name = "test-workflow",
            reason = "test",
            dryRun = false,
        )

        assertTrue(result is OperationResult.Error)
        val error = result as OperationResult.Error
        assertEquals("PERMISSION_DENIED", error.code)
    }

    @Test
    fun `terminateWorkflow shows dry run by default`() = runTest {
        val ops = createOps(allowDestructive = true)

        val result = ops.terminateWorkflow(
            namespace = "default",
            name = "test-workflow",
            reason = "test",
            dryRun = true,
        )

        assertTrue(result is OperationResult.DryRun)
        val dryRun = result as OperationResult.DryRun
        assertTrue(dryRun.preview.contains("Would terminate"))
    }
}
