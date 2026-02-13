package io.heapy.argo.workflows.mcp

import io.heapy.argo.workflows.mcp.config.ServerConfig
import io.heapy.argo.workflows.mcp.db.DatabaseFactory
import io.heapy.argo.workflows.mcp.repository.AuditLogRepository
import io.heapy.argo.workflows.mcp.repository.ConnectionRepository
import io.heapy.argo.workflows.mcp.repository.SettingsRepository
import io.heapy.argo.workflows.mcp.web.routes.apiRoutes
import io.heapy.argo.workflows.mcp.web.routes.uiRoutes
import io.heapy.komok.tech.logging.logger
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.application.install
import io.modelcontextprotocol.kotlin.sdk.server.mcp

fun main() {
    val log = logger {}
    val config = ServerConfig()

    log.info("Starting Argo Workflows MCP Server...")
    log.info("DB path: {}", config.dbPath)

    val database = DatabaseFactory.init(config.dbPath)
    val connectionRepo = ConnectionRepository(database)
    val settingsRepo = SettingsRepository(database)
    val auditLogRepo = AuditLogRepository(database)

    val mcpServer = ArgoWorkflowsMCPServer(
        connectionRepo = connectionRepo,
        settingsRepo = settingsRepo,
        auditLogRepo = auditLogRepo,
        serverName = config.serverName,
        serverVersion = config.serverVersion,
    )

    val server = mcpServer.createServer()

    log.info("Starting server on {}:{}", config.host, config.port)

    embeddedServer(CIO, host = config.host, port = config.port) {
        install(SSE)
        mcp {
            server
        }
        routing {
            uiRoutes()
            apiRoutes(connectionRepo, settingsRepo, auditLogRepo)
        }
    }.start(wait = true)
}
