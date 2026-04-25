package io.heapy.argo.workflows.mcp.repository

import io.heapy.argo.workflows.mcp.db.ConnectionsTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDateTime

data class ConnectionRecord(
    val id: Int,
    val name: String,
    val baseUrl: String,
    val defaultNamespace: String,
    val authType: String,
    val bearerToken: String?,
    val username: String?,
    val password: String?,
    val insecureSkipTlsVerify: Boolean,
    val requestTimeoutSeconds: Long,
    val tlsServerName: String?,
    val isActive: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

class ConnectionRepository(private val database: Database) {
    fun findAll(): List<ConnectionRecord> = transaction(database) {
        ConnectionsTable.selectAll()
            .map { it.toConnectionRecord() }
    }

    fun findById(id: Int): ConnectionRecord? = transaction(database) {
        ConnectionsTable.selectAll()
            .where { ConnectionsTable.id eq id }
            .singleOrNull()
            ?.toConnectionRecord()
    }

    fun findActive(): ConnectionRecord? = transaction(database) {
        ConnectionsTable.selectAll()
            .where { ConnectionsTable.isActive eq true }
            .singleOrNull()
            ?.toConnectionRecord()
    }

    fun create(record: ConnectionRecord): Int = transaction(database) {
        val now = LocalDateTime.now()
        ConnectionsTable.insert { statement ->
            statement[name] = record.name
            statement[baseUrl] = record.baseUrl
            statement[defaultNamespace] = record.defaultNamespace
            statement[authType] = record.authType
            statement[bearerToken] = record.bearerToken
            statement[username] = record.username
            statement[password] = record.password
            statement[insecureSkipTlsVerify] = record.insecureSkipTlsVerify
            statement[requestTimeoutSeconds] = record.requestTimeoutSeconds
            statement[tlsServerName] = record.tlsServerName
            statement[isActive] = record.isActive
            statement[createdAt] = now
            statement[updatedAt] = now
        }[ConnectionsTable.id].value
    }

    fun update(id: Int, record: ConnectionRecord): Boolean = transaction(database) {
        ConnectionsTable.update({ ConnectionsTable.id eq id }) { statement ->
            statement[name] = record.name
            statement[baseUrl] = record.baseUrl
            statement[defaultNamespace] = record.defaultNamespace
            statement[authType] = record.authType
            statement[bearerToken] = record.bearerToken
            statement[username] = record.username
            statement[password] = record.password
            statement[insecureSkipTlsVerify] = record.insecureSkipTlsVerify
            statement[requestTimeoutSeconds] = record.requestTimeoutSeconds
            statement[tlsServerName] = record.tlsServerName
            statement[updatedAt] = LocalDateTime.now()
        } > 0
    }

    fun delete(id: Int): Boolean = transaction(database) {
        ConnectionsTable.deleteWhere { ConnectionsTable.id eq id } > 0
    }

    fun activate(id: Int): Boolean = transaction(database) {
        val targetExists = ConnectionsTable.selectAll()
            .where { ConnectionsTable.id eq id }
            .singleOrNull() != null

        if (targetExists) {
            ConnectionsTable.update({ ConnectionsTable.isActive eq true }) {
                it[isActive] = false
            }
            ConnectionsTable.update({ ConnectionsTable.id eq id }) {
                it[isActive] = true
            } > 0
        } else {
            false
        }
    }

    private fun ResultRow.toConnectionRecord() = ConnectionRecord(
        id = this[ConnectionsTable.id].value,
        name = this[ConnectionsTable.name],
        baseUrl = this[ConnectionsTable.baseUrl],
        defaultNamespace = this[ConnectionsTable.defaultNamespace],
        authType = this[ConnectionsTable.authType],
        bearerToken = this[ConnectionsTable.bearerToken],
        username = this[ConnectionsTable.username],
        password = this[ConnectionsTable.password],
        insecureSkipTlsVerify = this[ConnectionsTable.insecureSkipTlsVerify],
        requestTimeoutSeconds = this[ConnectionsTable.requestTimeoutSeconds],
        tlsServerName = this[ConnectionsTable.tlsServerName],
        isActive = this[ConnectionsTable.isActive],
        createdAt = this[ConnectionsTable.createdAt],
        updatedAt = this[ConnectionsTable.updatedAt],
    )
}
