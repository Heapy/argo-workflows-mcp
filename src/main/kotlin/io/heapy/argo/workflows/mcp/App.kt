package io.heapy.argo.workflows.mcp

import io.heapy.argo.workflows.mcp.config.ServerConfig
import io.heapy.komok.tech.logging.logger
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.ktor.utils.io.streams.asInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlin.system.exitProcess

/**
 * Main entry point for Argo Workflows MCP Server
 */
fun main() = runBlocking {
    val log = logger {}

    try {
        log.info("Starting Argo Workflows MCP Server...")

        // Load configuration from environment variables
        val config = ServerConfig.fromEnvironment()

        log.info("Configuration loaded: ${config.server.name} v${config.server.version}")
        log.info(
            "Permissions: destructive={}, mutations={}",
            config.permissions.allowDestructive,
            config.permissions.allowMutations,
        )
        log.info("Effective configuration: ${config.maskSensitiveForLogging()}")

        // Create MCP server
        val mcpServer = ArgoWorkflowsMCPServer(config)
        try {
            val server = mcpServer.createServer()

            // Use STDIO transport (standard for MCP servers)
            val transport = StdioServerTransport(
                System.`in`.asInput(),
                System.out.asSink().buffered()
            )

            log.info("Server starting with STDIO transport...")
            log.info("Server is ready to accept MCP connections")

            // Connect and run server
            val session = server.createSession(transport)
            val done = Job()
            session.onClose {
                done.complete()
            }
            done.join()
        } finally {
            mcpServer.close()
        }

        log.info("Server stopped")
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        log.error("Fatal error during server startup", e)
        exitProcess(1)
    }
}

private const val MIN_TOKEN_VISIBLE_LENGTH = 4

private fun ServerConfig.maskSensitiveForLogging(): ServerConfig = copy(
    argo = argo.copy(
        auth = argo.auth.copy(
            bearerToken = argo.auth.bearerToken
                .maskToken(),
            password = argo.auth.password
                .maskToken(),
        )
    )
)

private fun String?.maskToken(): String? = this?.let { token ->
    when {
        token.isEmpty() -> ""
        token.length <= MIN_TOKEN_VISIBLE_LENGTH -> "*".repeat(token.length)
        else -> buildString {
            append(token.take(2))
            append("*".repeat(token.length - MIN_TOKEN_VISIBLE_LENGTH))
            append(token.takeLast(2))
        }
    }
}
