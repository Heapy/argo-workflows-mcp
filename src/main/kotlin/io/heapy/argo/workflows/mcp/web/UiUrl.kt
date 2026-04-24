package io.heapy.argo.workflows.mcp.web

import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.appendPathSegments
import io.ktor.server.engine.ConnectorType
import io.ktor.server.engine.EngineConnectorConfig

fun EngineConnectorConfig.uiUrl(): String? {
    if (type != ConnectorType.HTTP && type != ConnectorType.HTTPS) {
        return null
    }

    return URLBuilder(
        protocol = if (type == ConnectorType.HTTPS) URLProtocol.HTTPS else URLProtocol.HTTP,
        host = host,
        port = port,
    ).apply {
        appendPathSegments("connections")
    }.buildString()
}
