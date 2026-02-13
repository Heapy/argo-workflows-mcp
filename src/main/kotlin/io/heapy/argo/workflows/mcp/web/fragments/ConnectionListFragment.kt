package io.heapy.argo.workflows.mcp.web.fragments

import io.heapy.argo.workflows.mcp.repository.ConnectionRecord
import kotlinx.html.FlowContent
import kotlinx.html.button
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr

fun FlowContent.connectionListFragment(connections: List<ConnectionRecord>) {
    if (connections.isEmpty()) {
        p { +"No connections configured. Add one above." }
        return
    }

    table {
        thead {
            tr {
                th { +"Name" }
                th { +"URL" }
                th { +"Namespace" }
                th { +"Auth" }
                th { +"Status" }
                th { +"Actions" }
            }
        }
        tbody {
            for (conn in connections) {
                tr {
                    td { +conn.name }
                    td { +conn.baseUrl }
                    td { +conn.defaultNamespace }
                    td { +conn.authType }
                    td {
                        if (conn.isActive) {
                            span(classes = "badge badge-active") { +"Active" }
                        }
                    }
                    td {
                        button {
                            attributes["hx-get"] = "/api/connections/${conn.id}/edit"
                            attributes["hx-target"] = "#connection-form"
                            attributes["hx-swap"] = "innerHTML"
                            +"Edit"
                        }
                        if (!conn.isActive) {
                            button {
                                attributes["hx-post"] = "/api/connections/${conn.id}/activate"
                                attributes["hx-target"] = "#connections-list"
                                attributes["hx-swap"] = "innerHTML"
                                +"Activate"
                            }
                        }
                        button {
                            attributes["hx-post"] = "/api/test-connection/${conn.id}"
                            attributes["hx-target"] = "#connection-form"
                            attributes["hx-swap"] = "innerHTML"
                            +"Test"
                        }
                        button(classes = "secondary") {
                            attributes["hx-delete"] = "/api/connections/${conn.id}"
                            attributes["hx-target"] = "#connections-list"
                            attributes["hx-swap"] = "innerHTML"
                            attributes["hx-confirm"] = "Delete connection '${conn.name}'?"
                            +"Delete"
                        }
                    }
                }
            }
        }
    }
}
