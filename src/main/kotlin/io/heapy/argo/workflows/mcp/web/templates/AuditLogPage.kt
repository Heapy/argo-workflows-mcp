package io.heapy.argo.workflows.mcp.web.templates

import kotlinx.html.DIV
import kotlinx.html.div
import kotlinx.html.h2

fun DIV.auditLogPage() {
    div(classes = "page-shell") {
        div(classes = "page-header") {
            h2 { +"Audit Log" }
        }
        div(classes = "surface-panel loading-state") {
            attributes["id"] = "audit-log"
            attributes["hx-get"] = "/api/audit"
            attributes["hx-trigger"] = "load, every 5s"
            attributes["hx-swap"] = "innerHTML"
            +"Loading..."
        }
    }
}
