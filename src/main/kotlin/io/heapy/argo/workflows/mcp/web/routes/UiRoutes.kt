package io.heapy.argo.workflows.mcp.web.routes

import io.heapy.argo.workflows.mcp.web.templates.auditLogPage
import io.heapy.argo.workflows.mcp.web.templates.connectionsPage
import io.heapy.argo.workflows.mcp.web.templates.layout
import io.heapy.argo.workflows.mcp.web.templates.settingsPage
import io.ktor.server.html.respondHtml
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.uiRoutes() {
    get("/") {
        call.respondRedirect("/audit")
    }

    get("/audit") {
        call.respondHtml {
            layout("Audit Log - Argo MCP", "audit") {
                auditLogPage()
            }
        }
    }

    get("/connections") {
        call.respondHtml {
            layout("Connections - Argo MCP", "connections") {
                connectionsPage()
            }
        }
    }

    get("/settings") {
        call.respondHtml {
            layout("Settings - Argo MCP", "settings") {
                settingsPage()
            }
        }
    }
}
