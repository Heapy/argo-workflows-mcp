package io.heapy.argo.workflows.mcp.web.templates

import kotlinx.html.DIV
import kotlinx.html.div
import kotlinx.html.h2

fun DIV.auditLogPage() {
    h2 { +"Audit Log" }
    div {
        attributes["hx-get"] = "/api/audit"
        attributes["hx-trigger"] = "load, every 5s"
        attributes["hx-swap"] = "innerHTML"
        +"Loading..."
    }
}
