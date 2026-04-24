package io.heapy.argo.workflows.mcp

import io.heapy.argo.workflows.mcp.repository.ConnectionRecord
import io.heapy.argo.workflows.mcp.repository.ConnectionRepository
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.URI
import java.time.LocalDateTime

internal class ConnectionTools(
    private val connectionRepo: ConnectionRepository,
    private val audit: suspend (
        toolName: String,
        arguments: Map<String, JsonElement>?,
        block: suspend () -> CallToolResult,
    ) -> CallToolResult,
) {
    fun register(server: Server) {
        server.addTool(
            name = "add_connection",
            description = "Add a new Argo Workflows connection and optionally activate it",
            inputSchema = addConnectionInputSchema(),
            toolAnnotations = ToolAnnotations(
                readOnlyHint = false,
                destructiveHint = false,
                idempotentHint = false,
                openWorldHint = false,
            ),
        ) { request ->
            audit("add_connection", request.arguments) {
                createConnection(request.arguments)
            }
        }
    }

    @Suppress("ReturnCount")
    private fun createConnection(arguments: Map<String, JsonElement>?): CallToolResult {
        val name = arguments.requiredNonBlankString("name")
            ?: return errorResult("name is required")
        val baseUrl = arguments.requiredNonBlankString("base_url")
            ?: return errorResult("base_url is required")
        validateBaseUrl(baseUrl)?.let { return it }

        val defaultNamespace = arguments.stringOrNull("default_namespace")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_NAMESPACE
        val bearerToken = arguments.stringOrNull("bearer_token")?.trimToNull()
        val username = arguments.stringOrNull("username")?.trimToNull()
        val password = arguments.stringOrNull("password")?.trimToNull()
        val authType = resolveAuthType(arguments, bearerToken, username, password)
            ?: return errorResult("auth_type must be one of: none, bearer, basic")
        validateAuth(authType, bearerToken, username, password)?.let { return it }

        val requestTimeoutSeconds = arguments.intOrNull("request_timeout_seconds")
            ?: DEFAULT_REQUEST_TIMEOUT_SECONDS.toInt()
        if (requestTimeoutSeconds <= 0) {
            return errorResult("request_timeout_seconds must be greater than 0")
        }

        if (connectionRepo.findAll().any { it.name == name }) {
            return errorResult("connection name already exists: $name")
        }

        val id = connectionRepo.create(
            ConnectionRecord(
                id = 0,
                name = name,
                baseUrl = baseUrl,
                defaultNamespace = defaultNamespace,
                authType = authType,
                bearerToken = bearerToken.takeIf { authType == AUTH_TYPE_BEARER },
                username = username.takeIf { authType == AUTH_TYPE_BASIC },
                password = password.takeIf { authType == AUTH_TYPE_BASIC },
                insecureSkipTlsVerify = arguments.booleanOrNull("insecure_skip_tls_verify") ?: false,
                requestTimeoutSeconds = requestTimeoutSeconds.toLong(),
                tlsServerName = arguments.stringOrNull("tls_server_name")?.trimToNull(),
                isActive = false,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            ),
        )
        val activate = arguments.booleanOrNull("activate") ?: true
        if (activate) {
            connectionRepo.activate(id)
        }

        val activeMessage = if (activate) " and activated" else ""
        return CallToolResult(
            content = listOf(
                TextContent("Connection '$name' created with id $id$activeMessage."),
            ),
            isError = false,
        )
    }
}

@Suppress("LongMethod")
private fun addConnectionInputSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
        putJsonObject("name") {
            put("type", "string")
            put("description", "Unique connection name")
        }
        putJsonObject("base_url") {
            put("type", "string")
            put("description", "Argo Workflows API base URL, for example http://localhost:2746")
        }
        putJsonObject("default_namespace") {
            put("type", "string")
            put("description", "Default Kubernetes namespace")
            put("default", DEFAULT_NAMESPACE)
        }
        putJsonObject("auth_type") {
            put("type", "string")
            put("description", "Authentication type")
            putJsonArray("enum") {
                add(AUTH_TYPE_NONE)
                add(AUTH_TYPE_BEARER)
                add(AUTH_TYPE_BASIC)
            }
            put("default", AUTH_TYPE_NONE)
        }
        putJsonObject("bearer_token") {
            put("type", "string")
            put("description", "Bearer token when auth_type is bearer")
        }
        putJsonObject("username") {
            put("type", "string")
            put("description", "Username when auth_type is basic")
        }
        putJsonObject("password") {
            put("type", "string")
            put("description", "Password when auth_type is basic")
        }
        putJsonObject("insecure_skip_tls_verify") {
            put("type", "boolean")
            put("description", "Skip TLS certificate verification")
            put("default", false)
        }
        putJsonObject("request_timeout_seconds") {
            put("type", "integer")
            put("description", "HTTP request timeout in seconds")
            put("default", DEFAULT_REQUEST_TIMEOUT_SECONDS)
        }
        putJsonObject("tls_server_name") {
            put("type", "string")
            put("description", "Override TLS server name / SNI")
        }
        putJsonObject("activate") {
            put("type", "boolean")
            put("description", "Make this connection active immediately")
            put("default", true)
        }
    },
    required = listOf("name", "base_url"),
)

private fun errorResult(message: String): CallToolResult =
    CallToolResult(content = listOf(TextContent(message)), isError = true)

private fun validateBaseUrl(baseUrl: String): CallToolResult? {
    val uri = runCatching { URI(baseUrl) }.getOrNull()
        ?: return errorResult("base_url must be a valid URL")
    val scheme = uri.scheme?.lowercase()
    return if ((scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()) {
        null
    } else {
        errorResult("base_url must be an absolute http or https URL")
    }
}

private fun resolveAuthType(
    arguments: Map<String, JsonElement>?,
    bearerToken: String?,
    username: String?,
    password: String?,
): String? {
    val explicitAuthType = arguments.stringOrNull("auth_type")
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotEmpty() }
    val authType = explicitAuthType ?: when {
        bearerToken != null -> AUTH_TYPE_BEARER
        username != null || password != null -> AUTH_TYPE_BASIC
        else -> AUTH_TYPE_NONE
    }
    return authType.takeIf {
        it == AUTH_TYPE_NONE || it == AUTH_TYPE_BEARER || it == AUTH_TYPE_BASIC
    }
}

private fun validateAuth(
    authType: String,
    bearerToken: String?,
    username: String?,
    password: String?,
): CallToolResult? = when (authType) {
    AUTH_TYPE_NONE -> {
        if (bearerToken != null || username != null || password != null) {
            errorResult("auth_type none cannot include bearer_token, username, or password")
        } else {
            null
        }
    }

    AUTH_TYPE_BEARER -> {
        if (bearerToken == null) {
            errorResult("bearer_token is required when auth_type is bearer")
        } else {
            null
        }
    }

    AUTH_TYPE_BASIC -> {
        if (username == null || password == null) {
            errorResult("username and password are required when auth_type is basic")
        } else {
            null
        }
    }

    else -> errorResult("auth_type must be one of: none, bearer, basic")
}

private fun Map<String, JsonElement>?.stringOrNull(key: String): String? =
    this?.get(key)?.run { jsonPrimitive.contentOrNull }

private fun Map<String, JsonElement>?.requiredString(key: String): String? =
    this?.get(key)?.run { jsonPrimitive.content }

private fun Map<String, JsonElement>?.requiredNonBlankString(key: String): String? =
    requiredString(key)?.trimToNull()

private fun Map<String, JsonElement>?.intOrNull(key: String): Int? =
    this?.get(key)?.run { jsonPrimitive.intOrNull }

private fun Map<String, JsonElement>?.booleanOrNull(key: String): Boolean? =
    this?.get(key)?.run { jsonPrimitive.booleanOrNull }

private fun String.trimToNull(): String? = trim().takeIf { it.isNotEmpty() }

private const val DEFAULT_NAMESPACE = "default"
private const val DEFAULT_REQUEST_TIMEOUT_SECONDS = 30L
private const val AUTH_TYPE_NONE = "none"
private const val AUTH_TYPE_BEARER = "bearer"
private const val AUTH_TYPE_BASIC = "basic"
