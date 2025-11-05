package io.heapy.argo.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.X509TrustManager

/**
 * HTTP implementation of [ArgoWorkflowsClient] using Ktor.
 */
class ArgoWorkflowsHttpClient private constructor(
    private val config: ArgoClientConfig,
    private val httpClient: HttpClient,
    private val json: Json
) : ArgoWorkflowsClient {

    override suspend fun listWorkflows(
        namespace: String,
        limit: Int,
        labelSelector: String?,
        fieldSelector: String?
    ): List<WorkflowSummary> {
        val response = httpClient.get("/api/v1/workflows/${namespace.encodeURLPath()}") {
            parameter("listOptions.limit", limit)
            labelSelector?.let { parameter("listOptions.labelSelector", it) }
            fieldSelector?.let { parameter("listOptions.fieldSelector", it) }
        }

        val payload = response.expectJson()
        val items = payload["items"]?.jsonArray ?: JsonArray(emptyList())

        return items.mapNotNull { entry ->
            entry.toWorkflowSummary(config.defaultNamespace)
        }
    }

    override suspend fun getWorkflow(namespace: String, name: String): WorkflowDetail {
        val response = httpClient.get("/api/v1/workflows/${namespace.encodeURLPath()}/${name.encodeURLPath()}")
        val payload = response.expectJson()
        val summary = payload.toWorkflowSummary(config.defaultNamespace)
            ?: throw IOException("Workflow metadata missing name for $namespace/$name")

        val status = payload["status"]?.jsonObject
        val message = status?.get("message")?.jsonPrimitive?.contentOrNull

        val metadata = payload["metadata"]?.jsonObject
        val labels = metadata?.objectToStringMap("labels") ?: emptyMap()
        val annotations = metadata?.objectToStringMap("annotations") ?: emptyMap()
        val parameters = payload.extractParameters("spec", "arguments")
        val outputs = payload.extractParameters("status", "outputs")

        return WorkflowDetail(
            summary = summary,
            message = message,
            labels = labels,
            annotations = annotations,
            parameters = parameters,
            outputs = outputs,
            raw = payload
        )
    }

    override suspend fun getWorkflowLogs(
        namespace: String,
        workflowName: String,
        podName: String?,
        container: String
    ): WorkflowLogs {
        val response = httpClient.get("/api/v1/workflows/${namespace.encodeURLPath()}/${workflowName.encodeURLPath()}/log") {
            podName?.let { parameter("podName", it) }
            parameter("logOptions.container", container)
        }

        val bodyText = response.expectText()
        val entries = bodyText.lineSequence()
            .mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                runCatching {
                    json.parseToJsonElement(line)
                }.fold(
                    onSuccess = { element -> element.toLogEntry() },
                    onFailure = { null }
                )
            }
            .toList()

        return WorkflowLogs(entries)
    }

    override fun close() {
        httpClient.close()
    }

    companion object {
        fun create(config: ArgoClientConfig): ArgoWorkflowsHttpClient {
            val json = Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }

            val httpClient = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(json)
                }
                install(HttpTimeout) {
                    val timeoutMillis = config.requestTimeoutSeconds.coerceAtLeast(1) * 1000
                    requestTimeoutMillis = timeoutMillis
                    connectTimeoutMillis = timeoutMillis
                    socketTimeoutMillis = timeoutMillis
                }
                defaultRequest {
                    url(config.baseUrl)
                    accept(ContentType.Application.Json)
                    header(HttpHeaders.Accept, ContentType.Application.Json)

                    when {
                        config.auth.bearerToken != null -> {
                            header(HttpHeaders.Authorization, "Bearer ${config.auth.bearerToken}")
                        }

                        config.auth.username != null && config.auth.password != null -> {
                            val creds = "${config.auth.username}:${config.auth.password}"
                            val encoded = Base64.getEncoder().encodeToString(creds.toByteArray())
                            header(HttpHeaders.Authorization, "Basic $encoded")
                        }
                    }
                }
                engine {
                    https {
                        if (config.insecureSkipTlsVerify) {
                            trustManager = trustAllCertificatesManager()
                        }
                        if (config.tlsServerName != null) {
                            serverName = config.tlsServerName
                        }
                    }
                }
            }

            return ArgoWorkflowsHttpClient(config, httpClient, json)
        }

        private fun trustAllCertificatesManager(): X509TrustManager =
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
    }
}

private suspend fun HttpResponse.expectJson(): JsonObject {
    if (!status.isSuccess()) {
        throw IOException("Argo API call failed with status $status: ${bodyAsText()}")
    }
    val element: JsonElement = body()
    return element.jsonObject
}

private suspend fun HttpResponse.expectText(): String {
    if (!status.isSuccess()) {
        throw IOException("Argo API call failed with status $status: ${bodyAsText()}")
    }
    return bodyAsText()
}

private fun JsonElement.toWorkflowSummary(
    defaultNamespace: String
): WorkflowSummary? {
    val obj = this as? JsonObject ?: return null
    val metadata = obj["metadata"]?.jsonObject ?: return null
    val status = obj["status"]?.jsonObject

    val name = metadata["name"]?.jsonPrimitive?.contentOrNull ?: return null
    val namespace = metadata["namespace"]?.jsonPrimitive?.contentOrNull ?: defaultNamespace
    val phase = status?.get("phase")?.jsonPrimitive?.contentOrNull
    val progress = status?.get("progress")?.jsonPrimitive?.contentOrNull
    val startedAt = status?.get("startedAt")?.jsonPrimitive?.contentOrNull?.parseInstant()
    val finishedAt = status?.get("finishedAt")?.jsonPrimitive?.contentOrNull?.parseInstant()

    return WorkflowSummary(
        name = name,
        namespace = namespace,
        phase = phase,
        progress = progress,
        startedAt = startedAt,
        finishedAt = finishedAt
    )
}

private fun JsonElement.toLogEntry(): WorkflowLogEntry? {
    val obj = this as? JsonObject ?: return null
    val result = obj["result"]?.jsonObject ?: return null
    val content = result["content"]?.jsonPrimitive?.contentOrNull ?: return null
    val podName = result["podName"]?.jsonPrimitive?.contentOrNull
    return WorkflowLogEntry(
        podName = podName,
        content = content
    )
}

private fun JsonObject.extractParameters(
    parentKey: String,
    argumentsKey: String
): Map<String, String> {
    val parent = this[parentKey] as? JsonObject ?: return emptyMap()
    val arguments = parent[argumentsKey] as? JsonObject ?: return emptyMap()
    val parameters = arguments["parameters"] as? JsonArray ?: return emptyMap()

    return parameters.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val value = obj["value"]?.jsonPrimitive?.contentOrNull
            ?: obj["default"]?.jsonPrimitive?.contentOrNull
            ?: obj["valueFrom"]?.jsonObject?.toString()
            ?: ""
        name to value
    }.toMap()
}

private fun JsonObject.objectToStringMap(key: String): Map<String, String> {
    val obj = this[key] as? JsonObject ?: return emptyMap()
    return obj.entries.associate { (k, v) -> k to v.toJsonPrimitiveString() }
}

private fun JsonElement.toJsonPrimitiveString(): String {
    return when (this) {
        is JsonPrimitive -> when {
            isString -> content
            booleanOrNull != null -> booleanOrNull.toString()
            longOrNull != null -> longOrNull.toString()
            doubleOrNull != null -> doubleOrNull.toString()
            else -> content
        }

        JsonNull -> "null"
        else -> toString()
    }
}

private fun String.parseInstant(): kotlin.time.Instant? =
    runCatching { kotlin.time.Instant.parse(this) }.getOrNull()

private fun io.ktor.http.HttpStatusCode.isSuccess(): Boolean = value in 200..299
