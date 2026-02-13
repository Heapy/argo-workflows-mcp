package io.heapy.argo.workflows.mcp

import io.heapy.argo.workflows.mcp.db.DatabaseFactory
import io.heapy.argo.workflows.mcp.repository.AuditLogRepository
import io.heapy.argo.workflows.mcp.repository.ConnectionRepository
import io.heapy.argo.workflows.mcp.repository.SettingsRepository
import org.jetbrains.exposed.sql.Database
import java.nio.file.Files

fun createTestDatabase(): Database {
    val tempFile = Files.createTempFile("argo-mcp-test-", ".db")
    tempFile.toFile().deleteOnExit()
    return DatabaseFactory.init(tempFile.toString())
}

fun createTestRepositories(database: Database = createTestDatabase()): TestRepositories {
    val connectionRepo = ConnectionRepository(database)
    val settingsRepo = SettingsRepository(database)
    val auditLogRepo = AuditLogRepository(database)
    return TestRepositories(connectionRepo, settingsRepo, auditLogRepo)
}

data class TestRepositories(
    val connectionRepo: ConnectionRepository,
    val settingsRepo: SettingsRepository,
    val auditLogRepo: AuditLogRepository,
)
