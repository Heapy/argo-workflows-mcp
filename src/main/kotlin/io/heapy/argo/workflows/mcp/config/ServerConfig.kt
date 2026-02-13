package io.heapy.argo.workflows.mcp.config

data class ServerConfig(
    val host: String = System.getenv("ARGO_MCP_HOST") ?: "0.0.0.0",
    val port: Int = System.getenv("ARGO_MCP_PORT")?.toIntOrNull() ?: DEFAULT_PORT,
    val dbPath: String = System.getenv("ARGO_MCP_DB_PATH") ?: "argo-workflows-mcp.db",
    val serverName: String = "argo-workflows-mcp",
    val serverVersion: String = "0.2.0",
)

private const val DEFAULT_PORT = 8080
