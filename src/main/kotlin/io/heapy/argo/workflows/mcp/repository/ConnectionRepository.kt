package io.heapy.argo.workflows.mcp.repository

import io.heapy.argo.workflows.mcp.db.ConnectionsTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
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
        ConnectionsTable.insert {
            it[name] = record.name
            it[baseUrl] = record.baseUrl
            it[defaultNamespace] = record.defaultNamespace
            it[authType] = record.authType
            it[bearerToken] = record.bearerToken
            it[username] = record.username
            it[password] = record.password
            it[insecureSkipTlsVerify] = record.insecureSkipTlsVerify
            it[requestTimeoutSeconds] = record.requestTimeoutSeconds
            it[tlsServerName] = record.tlsServerName
            it[isActive] = record.isActive
            it[createdAt] = now
            it[updatedAt] = now
        }[ConnectionsTable.id].value
    }

    fun update(id: Int, record: ConnectionRecord): Boolean = transaction(database) {
        ConnectionsTable.update({ ConnectionsTable.id eq id }) {
            it[name] = record.name
            it[baseUrl] = record.baseUrl
            it[defaultNamespace] = record.defaultNamespace
            it[authType] = record.authType
            it[bearerToken] = record.bearerToken
            it[username] = record.username
            it[password] = record.password
            it[insecureSkipTlsVerify] = record.insecureSkipTlsVerify
            it[requestTimeoutSeconds] = record.requestTimeoutSeconds
            it[tlsServerName] = record.tlsServerName
            it[updatedAt] = LocalDateTime.now()
        } > 0
    }

    fun delete(id: Int): Boolean = transaction(database) {
        ConnectionsTable.deleteWhere { ConnectionsTable.id eq id } > 0
    }

    fun activate(id: Int): Boolean = transaction(database) {
        // Deactivate all
        ConnectionsTable.update({ ConnectionsTable.isActive eq true }) {
            it[isActive] = false
        }
        // Activate the selected one
        ConnectionsTable.update({ ConnectionsTable.id eq id }) {
            it[isActive] = true
        } > 0
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
