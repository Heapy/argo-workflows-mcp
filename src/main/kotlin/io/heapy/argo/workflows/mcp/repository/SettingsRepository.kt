package io.heapy.argo.workflows.mcp.repository

import io.heapy.argo.workflows.mcp.db.SettingsTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class SettingsRepository(private val database: Database) {
    fun get(key: String): String? = transaction(database) {
        SettingsTable.selectAll()
            .where { SettingsTable.key eq key }
            .singleOrNull()
            ?.get(SettingsTable.value)
    }

    fun set(key: String, value: String) {
        transaction(database) {
            val updated = SettingsTable.update({ SettingsTable.key eq key }) {
                it[SettingsTable.value] = value
            }
            if (updated == 0) {
                SettingsTable.insert {
                    it[SettingsTable.key] = key
                    it[SettingsTable.value] = value
                }
            }
        }
    }

    fun getAll(): Map<String, String> = transaction(database) {
        SettingsTable.selectAll()
            .associate { it[SettingsTable.key] to it[SettingsTable.value] }
    }

    fun getAllowDestructive(): Boolean = get("allow_destructive")?.toBoolean() ?: false
    fun getAllowMutations(): Boolean = get("allow_mutations")?.toBoolean() ?: false
    fun getRequireConfirmation(): Boolean = get("require_confirmation")?.toBoolean() ?: true
    fun getNamespacesAllow(): String = get("namespaces_allow") ?: "*"
    fun getNamespacesDeny(): String = get("namespaces_deny") ?: ""
}
