package io.heapy.argo.workflows.mcp.web.fragments

import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.checkBoxInput
import kotlinx.html.div
import kotlinx.html.fieldSet
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.legend
import kotlinx.html.textInput

fun FlowContent.settingsFormFragment(settings: Map<String, String>) {
    permissionsFieldSet(settings)
    namespaceFilteringFieldSet(settings)

    div {
        attributes["id"] = "settings-feedback"
        input(type = InputType.hidden) {
            attributes["hx-get"] = "/api/settings"
            attributes["hx-trigger"] = "settingsUpdated from:body"
            attributes["hx-target"] = "#settings-form"
            attributes["hx-swap"] = "innerHTML"
        }
    }
}

private fun FlowContent.permissionsFieldSet(settings: Map<String, String>) {
    fieldSet {
        legend { +"Permissions" }

        settingsCheckbox(
            name = "allow_destructive",
            checked = settings["allow_destructive"]?.toBoolean() ?: false,
            label = " Allow Destructive Operations (terminate workflows)",
        )

        settingsCheckbox(
            name = "allow_mutations",
            checked = settings["allow_mutations"]?.toBoolean() ?: false,
            label = " Allow Mutations (retry, suspend/resume)",
        )

        settingsCheckbox(
            name = "require_confirmation",
            checked = settings["require_confirmation"]?.toBoolean() ?: true,
            label = " Require Confirmation for Destructive Operations",
        )
    }
}

private fun FlowContent.settingsCheckbox(name: String, checked: Boolean, label: String) {
    label {
        checkBoxInput {
            this.name = name
            this.checked = checked
            attributes["hx-put"] = "/api/settings/$name"
            attributes["hx-trigger"] = "change"
            attributes["hx-vals"] = """js:{value: event.target.checked.toString()}"""
            attributes["hx-swap"] = "none"
        }
        +label
    }
}

private fun FlowContent.namespaceFilteringFieldSet(settings: Map<String, String>) {
    fieldSet {
        legend { +"Namespace Filtering" }

        label {
            +"Allowed Namespaces (comma-separated, * for all)"
            div {
                textInput(name = "namespaces_allow") {
                    value = settings["namespaces_allow"] ?: "*"
                    attributes["hx-put"] = "/api/settings/namespaces_allow"
                    attributes["hx-trigger"] = "change"
                    attributes["hx-vals"] = """js:{value: event.target.value}"""
                    attributes["hx-swap"] = "none"
                }
            }
        }

        label {
            +"Denied Namespaces (comma-separated)"
            div {
                textInput(name = "namespaces_deny") {
                    value = settings["namespaces_deny"] ?: ""
                    attributes["hx-put"] = "/api/settings/namespaces_deny"
                    attributes["hx-trigger"] = "change"
                    attributes["hx-vals"] = """js:{value: event.target.value}"""
                    attributes["hx-swap"] = "none"
                }
            }
        }
    }
}
