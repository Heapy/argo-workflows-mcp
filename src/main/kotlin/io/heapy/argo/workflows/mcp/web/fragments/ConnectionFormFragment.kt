package io.heapy.argo.workflows.mcp.web.fragments

import io.heapy.argo.workflows.mcp.repository.ConnectionRecord
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

fun FlowContent.connectionFormFragment(existing: ConnectionRecord? = null) {
    val isEdit = existing != null
    val action = if (isEdit) "/api/connections/${existing?.id}" else "/api/connections"
    val method = if (isEdit) "put" else "post"

    form {
        attributes["hx-$method"] = action
        attributes["hx-target"] = "#connections-list"
        attributes["hx-swap"] = "innerHTML"

        h3 { +(if (isEdit) "Edit Connection" else "New Connection") }

        fieldSet {
            legend { +"Connection Details" }

            label {
                +"Name"
                textInput(name = "name") {
                    value = existing?.name ?: ""
                    required = true
                    placeholder = "my-argo-server"
                }
            }

            label {
                +"Base URL"
                textInput(name = "baseUrl") {
                    value = existing?.baseUrl ?: ""
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
                    value = existing?.bearerToken ?: ""
                    placeholder = "Optional"
                }
            }

            label {
                +"Username"
                textInput(name = "username") {
                    value = existing?.username ?: ""
                    placeholder = "Optional"
                }
            }

            label {
                +"Password"
                input(type = InputType.password, name = "password") {
                    value = existing?.password ?: ""
                    placeholder = "Optional"
                }
            }

            label {
                +"Request Timeout (seconds)"
                input(type = InputType.number, name = "requestTimeoutSeconds") {
                    value = (existing?.requestTimeoutSeconds ?: 30).toString()
                }
            }

            label {
                +"TLS Server Name"
                textInput(name = "tlsServerName") {
                    value = existing?.tlsServerName ?: ""
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

        div {
            button { +(if (isEdit) "Update" else "Create") }
            button(type = kotlinx.html.ButtonType.button) {
                attributes["onclick"] = "document.getElementById('connection-form').innerHTML=''"
                +"Cancel"
            }
        }
    }
}
