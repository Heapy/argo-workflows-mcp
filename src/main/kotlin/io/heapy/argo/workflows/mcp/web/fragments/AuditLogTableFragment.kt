package io.heapy.argo.workflows.mcp.web.fragments

import io.heapy.argo.workflows.mcp.repository.AuditLogRecord
import kotlinx.html.FlowContent
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import java.time.format.DateTimeFormatter

private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

fun FlowContent.auditLogTableFragment(records: List<AuditLogRecord>, total: Long) {
    if (records.isEmpty()) {
        p { +"No audit records yet. Tool calls will appear here." }
        return
    }

    p { +"Total records: $total" }
    table {
        thead {
            tr {
                th { +"Time" }
                th { +"Tool" }
                th { +"Arguments" }
                th { +"Status" }
                th { +"Duration" }
            }
        }
        tbody {
            for (record in records) {
                tr {
                    td { +record.executedAt.format(formatter) }
                    td { +record.toolName }
                    td(classes = "truncate") {
                        +(record.arguments.take(MAX_ARG_DISPLAY_LENGTH) +
                            if (record.arguments.length > MAX_ARG_DISPLAY_LENGTH) "..." else "")
                    }
                    td {
                        span(classes = "badge badge-${record.status.lowercase()}") {
                            +record.status
                        }
                    }
                    td { +(record.durationMs?.let { "${it}ms" } ?: "n/a") }
                }
            }
        }
    }
}

private const val MAX_ARG_DISPLAY_LENGTH = 80
