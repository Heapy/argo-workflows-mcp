package io.heapy.argo.workflows.mcp.db

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime

object ConnectionsTable : IntIdTable("connections") {
    val name = varchar("name", 255).uniqueIndex()
    val baseUrl = varchar("base_url", 512)
    val defaultNamespace = varchar("default_namespace", 255)
    val authType = varchar("auth_type", 50).default("none")
    val bearerToken = varchar("bearer_token", 1024).nullable()
    val username = varchar("username", 255).nullable()
    val password = varchar("password", 512).nullable()
    val insecureSkipTlsVerify = bool("insecure_skip_tls_verify").default(false)
    val requestTimeoutSeconds = long("request_timeout_seconds").default(30L)
    val tlsServerName = varchar("tls_server_name", 255).nullable()
    val isActive = bool("is_active").default(false)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}
