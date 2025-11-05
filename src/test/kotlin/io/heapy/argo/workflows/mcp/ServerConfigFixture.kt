package io.heapy.argo.workflows.mcp

import io.heapy.argo.client.ArgoAuthConfig
import io.heapy.argo.client.ArgoClientConfig
import io.heapy.argo.workflows.mcp.config.KubernetesConfig
import io.heapy.argo.workflows.mcp.config.LoggingConfig
import io.heapy.argo.workflows.mcp.config.NamespaceFilter
import io.heapy.argo.workflows.mcp.config.PermissionsConfig
import io.heapy.argo.workflows.mcp.config.ServerConfig
import io.heapy.argo.workflows.mcp.config.ServerInfo

val serverConfig = ServerConfig(
    server = ServerInfo(
        name = "argo-workflows-mcp",
        version = "0.1.0",
    ),
    kubernetes = KubernetesConfig(
        configPath = null,
        context = null,
    ),
    argo = ArgoClientConfig(
        baseUrl = "http://localhost:2746",
        defaultNamespace = "default",
        auth = ArgoAuthConfig(
            bearerToken = null,
            username = null,
            password = null,
        ),
        insecureSkipTlsVerify = false,
        requestTimeoutSeconds = 30,
    ),
    permissions = PermissionsConfig(
        allowDestructive = false,
        allowMutations = false,
        requireConfirmation = true,
        namespaces = NamespaceFilter(
            allow = listOf("*"),
            deny = emptyList(),
        ),
    ),
    logging = LoggingConfig(
        audit = true,
        auditFile = "./mcp-audit.log",
        level = "info",
    ),
)