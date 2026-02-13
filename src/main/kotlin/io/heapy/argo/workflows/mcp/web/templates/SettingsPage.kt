package io.heapy.argo.workflows.mcp.web.templates

import kotlinx.html.DIV
import kotlinx.html.div
import kotlinx.html.h2

fun DIV.settingsPage() {
    h2 { +"Settings" }
    div {
        attributes["id"] = "settings-form"
        attributes["hx-get"] = "/api/settings"
        attributes["hx-trigger"] = "load"
        attributes["hx-swap"] = "innerHTML"
        +"Loading..."
    }
}
