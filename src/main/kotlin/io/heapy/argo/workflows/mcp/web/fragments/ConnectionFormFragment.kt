package io.heapy.argo.workflows.mcp.web.fragments

import io.heapy.argo.workflows.mcp.repository.ConnectionRecord
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.button
import kotlinx.html.checkBoxInput
import kotlinx.html.div
import kotlinx.html.fieldSet
import kotlinx.html.form
import kotlinx.html.h3
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.legend
import kotlinx.html.option
import kotlinx.html.select
import kotlinx.html.textInput

private const val DEFAULT_REQUEST_TIMEOUT_SECONDS = 30L

fun FlowContent.connectionFormFragment(existing: ConnectionRecord? = null) {
    val isEdit = existing != null
    val action = if (isEdit) "/api/connections/${existing.id}" else "/api/connections"
    val method = if (isEdit) "put" else "post"

    form(classes = "connection-form surface-panel") {
        attributes["hx-$method"] = action
        attributes["hx-target"] = "#connections-list"
        attributes["hx-swap"] = "innerHTML"

        h3 { +(if (isEdit) "Edit Connection" else "New Connection") }

        fieldSet(classes = "form-section") {
            legend { +"Connection Details" }
            connectionDetailsFields(existing)
        }

        fieldSet(classes = "form-section") {
            legend { +"Authentication" }
            authFields(existing)
        }

        fieldSet(classes = "form-section") {
            legend { +"Advanced" }
            advancedFields(existing)
        }

        formButtons(isEdit)
    }
}

private fun FlowContent.connectionDetailsFields(existing: ConnectionRecord?) {
    label {
        +"Name"
        textInput(name = "name") {
            value = existing?.name.orEmpty()
            required = true
            placeholder = "my-argo-server"
        }
    }

    label {
        +"Base URL"
        textInput(name = "baseUrl") {
            value = existing?.baseUrl.orEmpty()
            required = true
            placeholder = "https://argo.example.com"
        }
    }

    label {
        +"Default Namespace"
        textInput(name = "defaultNamespace") {
            value = existing?.defaultNamespace ?: "default"
            required = true
        }
    }
}

private fun FlowContent.authFields(existing: ConnectionRecord?) {
    label {
        +"Auth Type"
        select {
            name = "authType"
            for (authType in listOf("none", "bearer", "basic")) {
                option {
                    value = authType
                    if (existing?.authType == authType) selected = true
                    +authType
                }
            }
        }
    }

    label {
        +"Bearer Token"
        input(type = InputType.password, name = "bearerToken") {
            value = existing?.bearerToken.orEmpty()
            placeholder = "Optional"
        }
    }

    label {
        +"Username"
        textInput(name = "username") {
            value = existing?.username.orEmpty()
            placeholder = "Optional"
        }
    }

    label {
        +"Password"
        input(type = InputType.password, name = "password") {
            value = existing?.password.orEmpty()
            placeholder = "Optional"
        }
    }
}

private fun FlowContent.advancedFields(existing: ConnectionRecord?) {
    label {
        +"Request Timeout (seconds)"
        input(type = InputType.number, name = "requestTimeoutSeconds") {
            value = (existing?.requestTimeoutSeconds ?: DEFAULT_REQUEST_TIMEOUT_SECONDS).toString()
        }
    }

    label {
        +"TLS Server Name"
        textInput(name = "tlsServerName") {
            value = existing?.tlsServerName.orEmpty()
            placeholder = "Optional"
        }
    }

    label {
        checkBoxInput(name = "insecureSkipTlsVerify") {
            checked = existing?.insecureSkipTlsVerify ?: false
        }
        +" Skip TLS Verification"
    }
}

private fun FlowContent.formButtons(isEdit: Boolean) {
    div(classes = "form-actions") {
        button { +(if (isEdit) "Update" else "Create") }
        button(classes = "secondary", type = ButtonType.button) {
            attributes["onclick"] = "document.getElementById('connection-form').innerHTML=''"
            +"Cancel"
        }
    }
}
