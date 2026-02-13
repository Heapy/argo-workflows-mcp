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
    fieldSet {
        legend { +"Permissions" }

        label {
            checkBoxInput {
                name = "allow_destructive"
                checked = settings["allow_destructive"]?.toBoolean() ?: false
                attributes["hx-put"] = "/api/settings/allow_destructive"
                attributes["hx-trigger"] = "change"
                attributes["hx-vals"] = """js:{value: event.target.checked.toString()}"""
                attributes["hx-swap"] = "none"
            }
            +" Allow Destructive Operations (terminate workflows)"
        }

        label {
            checkBoxInput {
                name = "allow_mutations"
                checked = settings["allow_mutations"]?.toBoolean() ?: false
                attributes["hx-put"] = "/api/settings/allow_mutations"
                attributes["hx-trigger"] = "change"
                attributes["hx-vals"] = """js:{value: event.target.checked.toString()}"""
                attributes["hx-swap"] = "none"
            }
            +" Allow Mutations (retry, suspend/resume)"
        }

        label {
            checkBoxInput {
                name = "require_confirmation"
                checked = settings["require_confirmation"]?.toBoolean() ?: true
                attributes["hx-put"] = "/api/settings/require_confirmation"
                attributes["hx-trigger"] = "change"
                attributes["hx-vals"] = """js:{value: event.target.checked.toString()}"""
                attributes["hx-swap"] = "none"
            }
            +" Require Confirmation for Destructive Operations"
        }
    }

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
