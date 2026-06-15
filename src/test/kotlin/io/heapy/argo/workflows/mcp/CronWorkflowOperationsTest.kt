package io.heapy.argo.workflows.mcp

import io.heapy.argo.client.CronWorkflowSummary
import io.heapy.argo.client.WorkflowSummary
import io.heapy.argo.workflows.mcp.operations.CronWorkflowOperations
import io.heapy.argo.workflows.mcp.operations.NamespacePolicy
import io.heapy.argo.workflows.mcp.operations.OperationResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class CronWorkflowOperationsTest {
    private fun createOps(
        fakeClient: FakeArgoWorkflowsClient = FakeArgoWorkflowsClient(),
        allowMutations: Boolean = false,
        namespacesAllow: String = "*",
        namespacesDeny: String = "",
    ) = CronWorkflowOperations(
        defaultNamespace = "default",
        allowMutations = allowMutations,
        namespacePolicy = NamespacePolicy(
            allow = namespacesAllow,
            deny = namespacesDeny,
        ),
        argoClient = fakeClient,
    )

    @Test
    fun `listCronWorkflows returns Argo data`() = runTest {
        val fakeClient = FakeArgoWorkflowsClient().apply {
            listCronWorkflowsResult = listOf(
                CronWorkflowSummary(
                    name = "daily-job",
                    namespace = "default",
                    schedules = listOf("0 0 * * *"),
                    suspended = false,
                    timezone = "UTC",
                    lastScheduledTime = Instant.parse("2024-01-01T00:00:00Z"),
                    activeWorkflows = emptyList(),
                    phase = "Active",
                ),
            )
        }
        val cronOps = createOps(fakeClient)

        val result = cronOps.listCronWorkflows(namespace = "default")

        assertTrue(result is OperationResult.Success)
        val success = result as OperationResult.Success
        assertEquals("1", success.data["count"])
        assertTrue(success.data["cron_workflows"]?.contains("daily-job") ?: false)
        assertEquals(listOf("listCronWorkflows:default:"), fakeClient.calls)
    }

    @Test
    fun `getCronWorkflow returns schedule details`() = runTest {
        val cronOps = createOps()

        val result = cronOps.getCronWorkflow(namespace = "default", name = "daily-job")

        assertTrue(result is OperationResult.Success)
        val success = result as OperationResult.Success
        assertEquals("daily-job", success.data["name"])
        assertNotNull(success.data["schedules"])
    }

    @Test
    fun `toggleCronSuspension requires mutations enabled`() = runTest {
        val cronOps = createOps()

        val result = cronOps.toggleCronSuspension(
            namespace = "default",
            name = "daily-job",
            suspend = true,
        )

        assertTrue(result is OperationResult.Error)
        val error = result as OperationResult.Error
        assertEquals("PERMISSION_DENIED", error.code)
    }

    @Test
    fun `toggleCronSuspension calls Argo when mutations enabled`() = runTest {
        val fakeClient = FakeArgoWorkflowsClient()
        val cronOps = createOps(fakeClient, allowMutations = true)

        val result = cronOps.toggleCronSuspension(
            namespace = "default",
            name = "daily-job",
            suspend = true,
        )

        assertTrue(result is OperationResult.Success)
        val success = result as OperationResult.Success
        assertEquals("true", success.data["suspended"])
        assertEquals(listOf("suspendCronWorkflow:default:daily-job"), fakeClient.calls)
    }

    @Test
    fun `getCronHistory lists workflows with cron label selector`() = runTest {
        val fakeClient = FakeArgoWorkflowsClient().apply {
            listWorkflowsResult = listOf(
                WorkflowSummary(
                    name = "daily-job-abc",
                    namespace = "default",
                    phase = "Succeeded",
                    progress = "1/1",
                    startedAt = Instant.parse("2024-01-01T00:00:00Z"),
                    finishedAt = Instant.parse("2024-01-01T00:01:00Z"),
                ),
            )
        }
        val cronOps = createOps(fakeClient)

        val result = cronOps.getCronHistory(namespace = "default", name = "daily-job", limit = 5)

        assertTrue(result is OperationResult.Success)
        assertEquals("1", (result as OperationResult.Success).data["count"])
        assertEquals(
            listOf("listWorkflows:default:5:workflows.argoproj.io/cron-workflow=daily-job:"),
            fakeClient.calls,
        )
    }
}
