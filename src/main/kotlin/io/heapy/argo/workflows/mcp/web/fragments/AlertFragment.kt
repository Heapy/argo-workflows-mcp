package io.heapy.argo.workflows.mcp.web.fragments

import kotlinx.html.FlowContent
import kotlinx.html.div

fun FlowContent.alertSuccess(message: String) {
    div(classes = "alert alert-success") { +message }
}

fun FlowContent.alertError(message: String) {
    div(classes = "alert alert-error") { +message }
}
