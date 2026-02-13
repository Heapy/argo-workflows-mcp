package io.heapy.argo.workflows.mcp.repository

import io.heapy.argo.workflows.mcp.db.AuditLogTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

data class AuditLogRecord(
    val id: Int = 0,
    val toolName: String,
    val arguments: String,
    val status: String,
    val resultSummary: String?,
    val durationMs: Long?,
    val executedAt: LocalDateTime,
)

class AuditLogRepository(private val database: Database) {
    fun add(record: AuditLogRecord) {
        transaction(database) {
            AuditLogTable.insert {
                it[toolName] = record.toolName
                it[arguments] = record.arguments
                it[status] = record.status
                it[resultSummary] = record.resultSummary
                it[durationMs] = record.durationMs
                it[executedAt] = record.executedAt
            }
        }
    }

    fun findAll(page: Int = 0, pageSize: Int = DEFAULT_PAGE_SIZE): List<AuditLogRecord> =
        transaction(database) {
            AuditLogTable.selectAll()
                .orderBy(AuditLogTable.executedAt, SortOrder.DESC)
                .limit(pageSize)
                .offset((page * pageSize).toLong())
                .map { row ->
                    AuditLogRecord(
                        id = row[AuditLogTable.id].value,
                        toolName = row[AuditLogTable.toolName],
                        arguments = row[AuditLogTable.arguments],
                        status = row[AuditLogTable.status],
                        resultSummary = row[AuditLogTable.resultSummary],
                        durationMs = row[AuditLogTable.durationMs],
                        executedAt = row[AuditLogTable.executedAt],
                    )
                }
        }

    fun count(): Long = transaction(database) {
        AuditLogTable.selectAll().count()
    }
}

private const val DEFAULT_PAGE_SIZE = 50
