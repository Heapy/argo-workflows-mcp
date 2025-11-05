package io.heapy.argo.workflows.mcp.config

import io.heapy.argo.client.ArgoAuthConfig
import io.heapy.argo.client.ArgoClientConfig
import io.heapy.komok.tech.config.dotenv.dotenv
import kotlinx.serialization.Serializable

/**
 * Main server configuration
 */
@Serializable
data class ServerConfig(
    val server: ServerInfo,
    val kubernetes: KubernetesConfig,
    val argo: ArgoClientConfig,
    val permissions: PermissionsConfig,
    val logging: LoggingConfig,
) {
    companion object {
        fun fromEnvironment(
            environmentOverrides: Map<String, String> = emptyMap(),
        ): ServerConfig {
            val env = buildMap {
                putAll(System.getenv())
                putAll(dotenv().properties)
                putAll(environmentOverrides)
            }

            return ServerConfig(
                server = ServerInfo(
                    name = env["MCP_SERVER_NAME"] ?: "argo-workflows-mcp",
                    version = env["MCP_SERVER_VERSION"] ?: "0.1.0",
                ),
                kubernetes = KubernetesConfig(
                    configPath = env["KUBECONFIG"],
                    context = env["KUBE_CONTEXT"],
                ),
                argo = ArgoClientConfig(
                    baseUrl = env["ARGO_BASE_URL"]
                        ?: error("ARGO_BASE_URL is required"),
                    defaultNamespace = env["ARGO_NAMESPACE"]
                        ?: error("ARGO_NAMESPACE is required"),
                    auth = ArgoAuthConfig(
                        bearerToken = env["ARGO_TOKEN"],
                        username = env["ARGO_USERNAME"],
                        password = env["ARGO_PASSWORD"]
                    ),
                    insecureSkipTlsVerify = env["ARGO_INSECURE_SKIP_TLS_VERIFY"]?.toBoolean()
                        ?: false,
                    requestTimeoutSeconds = env["ARGO_REQUEST_TIMEOUT_SECONDS"]?.toLongOrNull()
                        ?: 30,
                ),
                permissions = PermissionsConfig(
                    allowDestructive = env["MCP_ALLOW_DESTRUCTIVE"]?.toBoolean() ?: false,
                    allowMutations = env["MCP_ALLOW_MUTATIONS"]?.toBoolean() ?: false,
                    requireConfirmation = env["MCP_REQUIRE_CONFIRMATION"]?.toBoolean() ?: true,
                    namespaces = NamespaceFilter(
                        allow = env["MCP_NAMESPACES_ALLOW"]?.split(",")?.map { it.trim() }
                            ?: listOf("*"),
                        deny = env["MCP_NAMESPACES_DENY"]?.split(",")?.map { it.trim() }
                            ?: emptyList(),
                    )
                ),
                logging = LoggingConfig(
                    audit = env["MCP_AUDIT_ENABLED"]?.toBoolean() ?: true,
                    auditFile = env["MCP_AUDIT_FILE"] ?: "./mcp-audit.log",
                    level = env["MCP_LOG_LEVEL"] ?: "info",
                )
            )
        }
    }
}

@Serializable
data class ServerInfo(
    val name: String,
    val version: String,
)

@Serializable
data class KubernetesConfig(
    val configPath: String?,
    val context: String?,
)

@Serializable
data class PermissionsConfig(
    val allowDestructive: Boolean,
    val allowMutations: Boolean,
    val requireConfirmation: Boolean,
    val namespaces: NamespaceFilter,
)

@Serializable
data class NamespaceFilter(
    val allow: List<String>,
    val deny: List<String>,
)

@Serializable
data class LoggingConfig(
    val audit: Boolean,
    val auditFile: String,
    val level: String,
)
