package io.heapy.argo.workflows.mcp.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init(dbPath: String = "argo-workflows-mcp.db"): Database {
        val database = Database.connect(
            url = "jdbc:sqlite:$dbPath?journal_mode=WAL&foreign_keys=ON",
            driver = "org.sqlite.JDBC",
        )

        transaction(database) {
            SchemaUtils.create(ConnectionsTable, SettingsTable, AuditLogTable)
            seedDefaults()
        }

        return database
    }

    private fun seedDefaults() {
        val defaults = mapOf(
            "allow_destructive" to "false",
            "allow_mutations" to "false",
            "require_confirmation" to "true",
            "namespaces_allow" to "*",
            "namespaces_deny" to "",
        )

        for ((key, value) in defaults) {
            SettingsTable.insertIgnore {
                it[SettingsTable.key] = key
                it[SettingsTable.value] = value
            }
        }
    }
}
