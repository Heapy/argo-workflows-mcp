package io.heapy.argo.workflows.mcp.db

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime

object AuditLogTable : IntIdTable("audit_log") {
    val toolName = varchar("tool_name", 255)
    val arguments = text("arguments")
    val status = varchar("status", 50)
    val resultSummary = text("result_summary").nullable()
    val durationMs = long("duration_ms").nullable()
    val executedAt = datetime("executed_at")
}
