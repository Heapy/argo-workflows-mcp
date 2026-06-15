package io.heapy.argo.workflows.mcp.operations

data class NamespacePolicy(
    val allow: String = "*",
    val deny: String = "",
)
