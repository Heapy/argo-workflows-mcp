package io.heapy.argo.workflows.mcp

import io.heapy.argo.workflows.mcp.repository.AuditLogRecord
import io.heapy.argo.workflows.mcp.repository.ConnectionRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class RepositoryTest {
    @Test
    fun `connection CRUD operations work`() {
        val repos = createTestRepositories()
        val now = LocalDateTime.now()

        val record = ConnectionRecord(
            id = 0,
            name = "test-conn",
            baseUrl = "https://argo.example.com",
            defaultNamespace = "default",
            authType = "bearer",
            bearerToken = "token123",
            username = null,
            password = null,
            insecureSkipTlsVerify = false,
            requestTimeoutSeconds = 30,
            tlsServerName = null,
            isActive = false,
            createdAt = now,
            updatedAt = now,
        )

        val id = repos.connectionRepo.create(record)
        assertTrue(id > 0)

        val found = repos.connectionRepo.findById(id)
        assertNotNull(found)
        assertEquals("test-conn", found?.name)
        assertEquals("https://argo.example.com", found?.baseUrl)

        repos.connectionRepo.update(id, record.copy(baseUrl = "https://argo2.example.com"))
        val updated = repos.connectionRepo.findById(id)
        assertEquals("https://argo2.example.com", updated?.baseUrl)

        repos.connectionRepo.delete(id)
        assertNull(repos.connectionRepo.findById(id))
    }

    @Test
    fun `connection activate deactivates others`() {
        val repos = createTestRepositories()
        val now = LocalDateTime.now()

        val base = ConnectionRecord(
            id = 0,
            name = "",
            baseUrl = "https://argo.example.com",
            defaultNamespace = "default",
            authType = "none",
            bearerToken = null,
            username = null,
            password = null,
            insecureSkipTlsVerify = false,
            requestTimeoutSeconds = 30,
            tlsServerName = null,
            isActive = false,
            createdAt = now,
            updatedAt = now,
        )

        val id1 = repos.connectionRepo.create(base.copy(name = "conn-1"))
        val id2 = repos.connectionRepo.create(base.copy(name = "conn-2"))

        repos.connectionRepo.activate(id1)
        assertEquals(id1, repos.connectionRepo.findActive()?.id)

        repos.connectionRepo.activate(id2)
        assertEquals(id2, repos.connectionRepo.findActive()?.id)
        assertEquals(false, repos.connectionRepo.findById(id1)?.isActive)
    }

    @Test
    fun `settings get and set work`() {
        val repos = createTestRepositories()

        // Default values seeded
        assertEquals("false", repos.settingsRepo.get("allow_destructive"))
        assertEquals("false", repos.settingsRepo.get("allow_mutations"))
        assertEquals("true", repos.settingsRepo.get("require_confirmation"))

        repos.settingsRepo.set("allow_destructive", "true")
        assertEquals("true", repos.settingsRepo.get("allow_destructive"))
        assertTrue(repos.settingsRepo.getAllowDestructive())
    }

    @Test
    fun `audit log records can be added and retrieved`() {
        val repos = createTestRepositories()

        repos.auditLogRepo.add(
            AuditLogRecord(
                toolName = "list_workflows",
                arguments = "namespace=default",
                status = "SUCCESS",
                resultSummary = "Found 5 workflows",
                durationMs = 42,
                executedAt = LocalDateTime.now(),
            ),
        )

        assertEquals(1L, repos.auditLogRepo.count())
        val records = repos.auditLogRepo.findAll()
        assertEquals(1, records.size)
        assertEquals("list_workflows", records[0].toolName)
        assertEquals("SUCCESS", records[0].status)
    }
}
