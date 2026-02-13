package io.heapy.argo.workflows.mcp.web.templates

import kotlinx.html.DIV
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h2

fun DIV.connectionsPage() {
    h2 { +"Connections" }
    button {
        attributes["hx-get"] = "/api/connections/new"
        attributes["hx-target"] = "#connection-form"
        attributes["hx-swap"] = "innerHTML"
        +"Add Connection"
    }
    div { attributes["id"] = "connection-form" }
    div {
        attributes["id"] = "connections-list"
        attributes["hx-get"] = "/api/connections"
        attributes["hx-trigger"] = "load"
        attributes["hx-swap"] = "innerHTML"
        +"Loading..."
    }
}
