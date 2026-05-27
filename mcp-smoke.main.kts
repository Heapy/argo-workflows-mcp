#!/usr/bin/env kotlin

/**
 * Minimal MCP SSE smoke client for argo-workflows-mcp.
 *
 * Connects to the MCP SSE endpoint, performs the JSON-RPC handshake
 * (initialize -> notifications/initialized -> tools/list), then calls
 * add_connection and list_workflows. Use it when no native MCP client is
 * available. See LOCAL_ARGO_MCP_TESTING.md for the surrounding smoke plan.
 *
 * Usage:
 * - ./mcp-smoke.main.kts                 # defaults to http://localhost:8080/
 * - ./mcp-smoke.main.kts http://host:port/
 *
 * Requires the `kotlin` compiler on PATH (e.g. `sdk install kotlin`).
 */

@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

val base = (args.firstOrNull() ?: "http://localhost:8080/").let {
    if (it.endsWith("/")) it else "$it/"
}

val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

val messages = LinkedBlockingQueue<String>()
val endpointQueue = LinkedBlockingQueue<String>()

// Background SSE reader: splits the stream into events and routes the
// `endpoint` event (the POST URL with sessionId) separately from responses.
thread(isDaemon = true, name = "sse-reader") {
    val request = HttpRequest.newBuilder(URI.create(base))
        .header("Accept", "text/event-stream")
        .GET()
        .build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
    var event: String? = null
    val dataLines = mutableListOf<String>()
    response.body().bufferedReader().forEachLine { raw ->
        val line = raw.trimEnd('\n', '\r')
        when {
            line.isEmpty() -> {
                if (dataLines.isNotEmpty()) {
                    val data = dataLines.joinToString("\n")
                    if (event == "endpoint") endpointQueue.put(data) else messages.put(data)
                }
                event = null
                dataLines.clear()
            }
            line.startsWith("event:") -> event = line.substringAfter("event:").trim()
            line.startsWith("data:") -> dataLines.add(line.substringAfter("data:").trim())
        }
    }
}

val endpoint = endpointQueue.poll(5, TimeUnit.SECONDS)
    ?: error("Did not receive MCP endpoint event within 5s")
val postUrl = URI.create(base).resolve(endpoint)
println("MCP endpoint: $endpoint")

var nextId = 1

fun post(payload: JsonObject) {
    val request = HttpRequest.newBuilder(postUrl)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
        .build()
    client.send(request, HttpResponse.BodyHandlers.discarding())
}

fun waitForId(id: Int, timeoutSeconds: Long = 10): JsonObject {
    val deadline = System.currentTimeMillis() + timeoutSeconds * 1000
    while (true) {
        val remaining = deadline - System.currentTimeMillis()
        if (remaining <= 0) error("Timed out waiting for response id=$id")
        val raw = messages.poll(remaining, TimeUnit.MILLISECONDS)
            ?: error("Timed out waiting for response id=$id")
        val obj = json.parseToJsonElement(raw).jsonObject
        if ((obj["id"] as? JsonPrimitive)?.intOrNull == id) return obj
    }
}

fun request(method: String, params: JsonObject = buildJsonObject {}): JsonObject {
    val id = nextId++
    post(
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        },
    )
    return waitForId(id)
}

fun callTool(name: String, arguments: JsonObject = buildJsonObject {}): JsonObject =
    request(
        "tools/call",
        buildJsonObject {
            put("name", name)
            put("arguments", arguments)
        },
    )

fun pretty(obj: JsonObject) = println(json.encodeToString(JsonObject.serializer(), obj))

pretty(
    request(
        "initialize",
        buildJsonObject {
            put("protocolVersion", "2024-11-05")
            put("capabilities", buildJsonObject {})
            put(
                "clientInfo",
                buildJsonObject {
                    put("name", "local-argo-smoke")
                    put("version", "0.1")
                },
            )
        },
    ),
)

post(
    buildJsonObject {
        put("jsonrpc", "2.0")
        put("method", "notifications/initialized")
    },
)

val tools = request("tools/list")
val toolNames = tools["result"]?.jsonObject?.get("tools")?.jsonArray
    ?.mapNotNull { (it.jsonObject["name"] as? JsonPrimitive)?.content }
println("TOOLS: $toolNames")

pretty(
    callTool(
        "add_connection",
        buildJsonObject {
            put("name", "docker-desktop-local")
            put("base_url", "https://localhost:2746")
            put("default_namespace", "argo")
            put("auth_type", "none")
            put("insecure_skip_tls_verify", true)
            put("activate", true)
        },
    ),
)

pretty(
    callTool(
        "list_workflows",
        buildJsonObject {
            put("namespace", "argo")
            put("limit", 10)
        },
    ),
)
