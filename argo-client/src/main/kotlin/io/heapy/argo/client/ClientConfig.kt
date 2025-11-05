package io.heapy.argo.client

import kotlinx.serialization.Serializable

/**
 * Configuration for connecting to the Argo Workflows API server.
 */
@Serializable
data class ArgoClientConfig(
    val baseUrl: String = "http://localhost:2746",
    val defaultNamespace: String = "default",
    val auth: ArgoAuthConfig = ArgoAuthConfig(),
    val insecureSkipTlsVerify: Boolean = false,
    val requestTimeoutSeconds: Long = 30
)

@Serializable
data class ArgoAuthConfig(
    val bearerToken: String? = null,
    val username: String? = null,
    val password: String? = null
)
