package io.heapy.argo.workflows.mcp.web.templates

import kotlinx.html.DIV
import kotlinx.html.div
import kotlinx.html.h2

fun DIV.settingsPage() {
    div(classes = "page-shell") {
        div(classes = "page-header") {
            h2 { +"Settings" }
        }
        div(classes = "surface-panel loading-state") {
            attributes["id"] = "settings-form"
            attributes["hx-get"] = "/api/settings"
            attributes["hx-trigger"] = "load"
            attributes["hx-swap"] = "innerHTML"
            +"Loading..."
        }
    }
}
