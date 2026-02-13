package io.heapy.argo.workflows.mcp.web.routes

import io.heapy.argo.client.ArgoWorkflowsHttpClient
import io.heapy.argo.workflows.mcp.repository.AuditLogRepository
import io.heapy.argo.workflows.mcp.repository.ConnectionRecord
import io.heapy.argo.workflows.mcp.repository.ConnectionRepository
import io.heapy.argo.workflows.mcp.repository.SettingsRepository
import io.heapy.argo.workflows.mcp.toArgoClientConfig
import io.heapy.argo.workflows.mcp.web.fragments.alertError
import io.heapy.argo.workflows.mcp.web.fragments.alertSuccess
import io.heapy.argo.workflows.mcp.web.fragments.auditLogTableFragment
import io.heapy.argo.workflows.mcp.web.fragments.connectionFormFragment
import io.heapy.argo.workflows.mcp.web.fragments.connectionListFragment
import io.heapy.argo.workflows.mcp.web.fragments.settingsFormFragment
import io.ktor.server.html.respondHtmlFragment
import io.ktor.server.request.receiveParameters
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import java.time.LocalDateTime

@Suppress("LongMethod")
fun Route.apiRoutes(
    connectionRepo: ConnectionRepository,
    settingsRepo: SettingsRepository,
    auditLogRepo: AuditLogRepository,
) {
    // Audit endpoints
    get("/api/audit") {
        val page = call.parameters["page"]?.toIntOrNull() ?: 0
        val records = auditLogRepo.findAll(page)
        val total = auditLogRepo.count()
        call.respondHtmlFragment {
            auditLogTableFragment(records, total)
        }
    }

    // Connection list
    get("/api/connections") {
        val connections = connectionRepo.findAll()
        call.respondHtmlFragment {
            connectionListFragment(connections)
        }
    }

    // New connection form
    get("/api/connections/new") {
        call.respondHtmlFragment {
            connectionFormFragment()
        }
    }

    // Edit connection form
    get("/api/connections/{id}/edit") {
        val id = call.parameters["id"]?.toIntOrNull()
        val conn = id?.let { connectionRepo.findById(it) }
        if (conn == null) {
            call.respondHtmlFragment { alertError("Connection not found") }
            return@get
        }
        call.respondHtmlFragment {
            connectionFormFragment(conn)
        }
    }

    // Create connection
    post("/api/connections") {
        val params = call.receiveParameters()
        val record = params.toConnectionRecord()
        connectionRepo.create(record)
        val connections = connectionRepo.findAll()
        call.respondHtmlFragment {
            connectionListFragment(connections)
        }
    }

    // Update connection
    put("/api/connections/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
        if (id == null) {
            call.respondHtmlFragment { alertError("Invalid connection ID") }
            return@put
        }
        val params = call.receiveParameters()
        val record = params.toConnectionRecord()
        connectionRepo.update(id, record)
        val connections = connectionRepo.findAll()
        call.respondHtmlFragment {
            connectionListFragment(connections)
        }
    }

    // Delete connection
    delete("/api/connections/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
        if (id != null) {
            connectionRepo.delete(id)
        }
        val connections = connectionRepo.findAll()
        call.respondHtmlFragment {
            connectionListFragment(connections)
        }
    }

    // Activate connection
    post("/api/connections/{id}/activate") {
        val id = call.parameters["id"]?.toIntOrNull()
        if (id != null) {
            connectionRepo.activate(id)
        }
        val connections = connectionRepo.findAll()
        call.respondHtmlFragment {
            connectionListFragment(connections)
        }
    }

    // Test connection
    post("/api/test-connection/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
        val conn = id?.let { connectionRepo.findById(it) }
        if (conn == null) {
            call.respondHtmlFragment { alertError("Connection not found") }
            return@post
        }

        try {
            val client = ArgoWorkflowsHttpClient.create(conn.toArgoClientConfig())
            client.use {
                it.listWorkflows(conn.defaultNamespace, limit = 1)
            }
            call.respondHtmlFragment {
                alertSuccess("Connection successful! Argo server is reachable.")
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            call.respondHtmlFragment {
                alertError("Connection failed: ${e.message}")
            }
        }
    }

    // Settings endpoints
    get("/api/settings") {
        val settings = settingsRepo.getAll()
        call.respondHtmlFragment {
            settingsFormFragment(settings)
        }
    }

    put("/api/settings/{key}") {
        val key = call.parameters["key"]
        val params = call.receiveParameters()
        val value = params["value"]
        if (key != null && value != null) {
            settingsRepo.set(key, value)
        }
        call.respondHtmlFragment {
            alertSuccess("Setting updated")
        }
    }
}

private fun io.ktor.http.Parameters.toConnectionRecord(): ConnectionRecord {
    val now = LocalDateTime.now()
    return ConnectionRecord(
        id = 0,
        name = get("name") ?: "",
        baseUrl = get("baseUrl") ?: "",
        defaultNamespace = get("defaultNamespace") ?: "default",
        authType = get("authType") ?: "none",
        bearerToken = get("bearerToken")?.takeIf { it.isNotBlank() },
        username = get("username")?.takeIf { it.isNotBlank() },
        password = get("password")?.takeIf { it.isNotBlank() },
        insecureSkipTlsVerify = get("insecureSkipTlsVerify") == "on",
        requestTimeoutSeconds = get("requestTimeoutSeconds")?.toLongOrNull() ?: 30L,
        tlsServerName = get("tlsServerName")?.takeIf { it.isNotBlank() },
        isActive = false,
        createdAt = now,
        updatedAt = now,
    )
}
