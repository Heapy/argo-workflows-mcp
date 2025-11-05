package io.heapy.argo.client

import kotlinx.serialization.Serializable

/**
 * Configuration for connecting to the Argo Workflows API server.
 */
@Serializable
data class ArgoClientConfig(
    val baseUrl: String,
    val defaultNamespace: String,
    val auth: ArgoAuthConfig,
    val insecureSkipTlsVerify: Boolean,
    val requestTimeoutSeconds: Long,
    val tlsServerName: String?,
)

@Serializable
data class ArgoAuthConfig(
    val bearerToken: String?,
    val username: String?,
    val password: String?,
)
