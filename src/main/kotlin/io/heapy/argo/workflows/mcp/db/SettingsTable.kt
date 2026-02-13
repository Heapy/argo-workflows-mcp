package io.heapy.argo.workflows.mcp.db

import org.jetbrains.exposed.dao.id.IntIdTable

object SettingsTable : IntIdTable("settings") {
    val key = varchar("key", 255).uniqueIndex()
    val value = varchar("value", 1024)
}
