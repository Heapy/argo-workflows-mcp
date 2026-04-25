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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
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

    override suspend fun terminateWorkflow(namespace: String, name: String): WorkflowSummary {
        val response = httpClient.put(
            "/api/v1/workflows/${namespace.encodeURLPath()}/${name.encodeURLPath()}/terminate"
        ) {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("namespace", namespace)
                    put("name", name)
                }
            )
        }

        return response.expectJson().toWorkflowSummary(config.defaultNamespace)
            ?: throw IOException("Workflow metadata missing name for terminated workflow $namespace/$name")
    }

    override suspend fun retryWorkflow(
        namespace: String,
        name: String,
        restartSuccessful: Boolean
    ): WorkflowSummary {
        val response = httpClient.put(
            "/api/v1/workflows/${namespace.encodeURLPath()}/${name.encodeURLPath()}/retry"
        ) {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("namespace", namespace)
                    put("name", name)
                    put("restartSuccessful", restartSuccessful)
                }
            )
        }

        return response.expectJson().toWorkflowSummary(config.defaultNamespace)
            ?: throw IOException("Workflow metadata missing name for retried workflow $namespace/$name")
    }

    override suspend fun listCronWorkflows(
        namespace: String,
        labelSelector: String?
    ): List<CronWorkflowSummary> {
        val response = httpClient.get("/api/v1/cron-workflows/${namespace.encodeURLPath()}") {
            labelSelector?.let { parameter("listOptions.labelSelector", it) }
        }

        val payload = response.expectJson()
        val items = payload["items"]?.jsonArray ?: JsonArray(emptyList())
        return items.mapNotNull { entry ->
            entry.toCronWorkflowSummary(config.defaultNamespace)
        }
    }

    override suspend fun getCronWorkflow(namespace: String, name: String): CronWorkflowDetail {
        val response = httpClient.get("/api/v1/cron-workflows/${namespace.encodeURLPath()}/${name.encodeURLPath()}")
        val payload = response.expectJson()
        val summary = payload.toCronWorkflowSummary(config.defaultNamespace)
            ?: throw IOException("CronWorkflow metadata missing name for $namespace/$name")
        val spec = payload["spec"]?.jsonObject
        val metadata = payload["metadata"]?.jsonObject

        return CronWorkflowDetail(
            summary = summary,
            concurrencyPolicy = spec?.get("concurrencyPolicy")?.jsonPrimitive?.contentOrNull,
            successfulJobsHistoryLimit = spec?.get("successfulJobsHistoryLimit")?.jsonPrimitive?.longOrNull?.toInt(),
            failedJobsHistoryLimit = spec?.get("failedJobsHistoryLimit")?.jsonPrimitive?.longOrNull?.toInt(),
            labels = metadata?.objectToStringMap("labels") ?: emptyMap(),
            annotations = metadata?.objectToStringMap("annotations") ?: emptyMap(),
            raw = payload
        )
    }

    override suspend fun suspendCronWorkflow(namespace: String, name: String): CronWorkflowSummary {
        val response = httpClient.put(
            "/api/v1/cron-workflows/${namespace.encodeURLPath()}/${name.encodeURLPath()}/suspend"
        ) {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("namespace", namespace)
                    put("name", name)
                }
            )
        }

        return response.expectJson().toCronWorkflowSummary(config.defaultNamespace)
            ?: throw IOException("CronWorkflow metadata missing name for suspended CronWorkflow $namespace/$name")
    }

    override suspend fun resumeCronWorkflow(namespace: String, name: String): CronWorkflowSummary {
        val response = httpClient.put(
            "/api/v1/cron-workflows/${namespace.encodeURLPath()}/${name.encodeURLPath()}/resume"
        ) {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("namespace", namespace)
                    put("name", name)
                }
            )
        }

        return response.expectJson().toCronWorkflowSummary(config.defaultNamespace)
            ?: throw IOException("CronWorkflow metadata missing name for resumed CronWorkflow $namespace/$name")
    }

    override suspend fun listWorkflowTemplates(
        namespace: String,
        labelSelector: String?
    ): List<WorkflowTemplateSummary> {
        val response = httpClient.get("/api/v1/workflow-templates/${namespace.encodeURLPath()}") {
            labelSelector?.let { parameter("listOptions.labelSelector", it) }
        }

        val payload = response.expectJson()
        val items = payload["items"]?.jsonArray ?: JsonArray(emptyList())
        return items.mapNotNull { entry ->
            entry.toWorkflowTemplateSummary(config.defaultNamespace)
        }
    }

    override suspend fun getWorkflowTemplate(namespace: String, name: String): WorkflowTemplateDetail {
        val response = httpClient.get(
            "/api/v1/workflow-templates/${namespace.encodeURLPath()}/${name.encodeURLPath()}"
        )
        return response.expectJson().toWorkflowTemplateDetail(namespace)
    }

    override suspend fun listClusterWorkflowTemplates(labelSelector: String?): List<WorkflowTemplateSummary> {
        val response = httpClient.get("/api/v1/cluster-workflow-templates") {
            labelSelector?.let { parameter("listOptions.labelSelector", it) }
        }

        val payload = response.expectJson()
        val items = payload["items"]?.jsonArray ?: JsonArray(emptyList())
        return items.mapNotNull { entry ->
            entry.toWorkflowTemplateSummary(null)
        }
    }

    override suspend fun getClusterWorkflowTemplate(name: String): WorkflowTemplateDetail {
        val response = httpClient.get("/api/v1/cluster-workflow-templates/${name.encodeURLPath()}")
        return response.expectJson().toWorkflowTemplateDetail(null)
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

private fun JsonElement.toCronWorkflowSummary(
    defaultNamespace: String
): CronWorkflowSummary? {
    val obj = this as? JsonObject ?: return null
    val metadata = obj["metadata"]?.jsonObject ?: return null
    val spec = obj["spec"]?.jsonObject
    val status = obj["status"]?.jsonObject

    val name = metadata["name"]?.jsonPrimitive?.contentOrNull ?: return null
    val namespace = metadata["namespace"]?.jsonPrimitive?.contentOrNull ?: defaultNamespace
    val schedules = spec.extractSchedules()
    val suspended = spec?.get("suspend")?.jsonPrimitive?.booleanOrNull
    val timezone = spec?.get("timezone")?.jsonPrimitive?.contentOrNull
    val lastScheduledTime = status?.get("lastScheduledTime")?.jsonPrimitive?.contentOrNull?.parseInstant()
    val activeWorkflows = status?.get("active")?.jsonArray
        ?.mapNotNull { active ->
            val activeObj = active as? JsonObject ?: return@mapNotNull null
            activeObj["name"]?.jsonPrimitive?.contentOrNull
        }
        .orEmpty()
    val phase = status?.get("phase")?.jsonPrimitive?.contentOrNull

    return CronWorkflowSummary(
        name = name,
        namespace = namespace,
        schedules = schedules,
        suspended = suspended,
        timezone = timezone,
        lastScheduledTime = lastScheduledTime,
        activeWorkflows = activeWorkflows,
        phase = phase
    )
}

private fun JsonObject?.extractSchedules(): List<String> {
    if (this == null) return emptyList()
    val schedules = this["schedules"] as? JsonArray
    if (schedules != null) {
        return schedules.mapNotNull { it.jsonPrimitive.contentOrNull }
    }

    return this["schedule"]?.jsonPrimitive?.contentOrNull
        ?.let { listOf(it) }
        .orEmpty()
}

private fun JsonElement.toWorkflowTemplateSummary(
    fallbackNamespace: String?
): WorkflowTemplateSummary? {
    val obj = this as? JsonObject ?: return null
    val metadata = obj["metadata"]?.jsonObject ?: return null
    val spec = obj["spec"]?.jsonObject

    val name = metadata["name"]?.jsonPrimitive?.contentOrNull ?: return null
    val namespace = metadata["namespace"]?.jsonPrimitive?.contentOrNull ?: fallbackNamespace
    val entrypoint = spec?.get("entrypoint")?.jsonPrimitive?.contentOrNull
    val templateCount = (spec?.get("templates") as? JsonArray)?.size ?: 0
    val labels = metadata.objectToStringMap("labels")

    return WorkflowTemplateSummary(
        name = name,
        namespace = namespace,
        entrypoint = entrypoint,
        templateCount = templateCount,
        labels = labels
    )
}

private fun JsonObject.toWorkflowTemplateDetail(
    fallbackNamespace: String?
): WorkflowTemplateDetail {
    val summary = toWorkflowTemplateSummary(fallbackNamespace)
        ?: throw IOException("WorkflowTemplate metadata missing name")
    val metadata = this["metadata"]?.jsonObject

    return WorkflowTemplateDetail(
        summary = summary,
        parameters = extractParameters("spec", "arguments"),
        annotations = metadata?.objectToStringMap("annotations") ?: emptyMap(),
        raw = this
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
