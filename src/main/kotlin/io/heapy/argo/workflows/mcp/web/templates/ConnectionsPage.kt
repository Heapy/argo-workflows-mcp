package io.heapy.argo.workflows.mcp.web.templates

import kotlinx.html.DIV
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h2

fun DIV.connectionsPage() {
    div(classes = "page-shell") {
        div(classes = "page-header") {
            h2 { +"Connections" }
            div(classes = "page-actions") {
                button(classes = "primary-action") {
                    attributes["hx-get"] = "/api/connections/new"
                    attributes["hx-target"] = "#connection-form"
                    attributes["hx-swap"] = "innerHTML"
                    +"Add Connection"
                }
            }
        }
        div { attributes["id"] = "connection-form" }
        div(classes = "surface-panel loading-state") {
            attributes["id"] = "connections-list"
            attributes["hx-get"] = "/api/connections"
            attributes["hx-trigger"] = "load"
            attributes["hx-swap"] = "innerHTML"
            +"Loading..."
        }
    }
}
