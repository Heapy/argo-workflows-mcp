package io.heapy.argo.workflows.mcp

import io.heapy.argo.workflows.mcp.operations.CronWorkflowOperations
import io.heapy.argo.workflows.mcp.operations.OperationResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CronWorkflowOperationsTest {
    private val cronOps = CronWorkflowOperations(serverConfig)

    @Test
    fun `listCronWorkflows returns mock data`() = runTest {
        val result = cronOps.listCronWorkflows(namespace = "default")

        assertTrue(result is OperationResult.Success)
        val success = result as OperationResult.Success
        assertTrue(success.message.contains("cron"))
    }

    @Test
    fun `getCronWorkflow returns schedule details`() = runTest {
        val result = cronOps.getCronWorkflow(namespace = "default", name = "daily-job")

        assertTrue(result is OperationResult.Success)
        val success = result as OperationResult.Success
        assertEquals("daily-job", success.data["name"])
        assertNotNull(success.data["schedule"])
    }

    @Test
    fun `toggleCronSuspension requires mutations enabled`() = runTest {
        val result = cronOps.toggleCronSuspension(
            namespace = "default",
            name = "daily-job",
            suspend = true
        )

        assertTrue(result is OperationResult.Error)
        val error = result as OperationResult.Error
        assertEquals("PERMISSION_DENIED", error.code)
    }
}
