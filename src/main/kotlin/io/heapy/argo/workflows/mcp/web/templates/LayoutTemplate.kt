package io.heapy.argo.workflows.mcp.web.templates

import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.lang
import kotlinx.html.li
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.script
import kotlinx.html.title
import kotlinx.html.ul
import kotlinx.html.unsafe

fun HTML.layout(pageTitle: String, activePage: String, content: kotlinx.html.DIV.() -> Unit) {
    lang = "en"
    attributes["data-theme"] = "light"
    head {
        meta { charset = "utf-8" }
        meta { name = "viewport"; this.content = "width=device-width, initial-scale=1" }
        title { +pageTitle }
        link(rel = "stylesheet", href = "https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.min.css")
        script(src = "https://unpkg.com/htmx.org@2.0.4") {}
        unsafe {
            +"""
            <style>
                :root {
                    --argo-bg: #f5f7fb;
                    --argo-surface: #ffffff;
                    --argo-surface-muted: #f8fafc;
                    --argo-border: #d9e0ea;
                    --argo-text: #172033;
                    --argo-muted: #667085;
                    --argo-accent: #2563eb;
                    --argo-accent-soft: #e8f0ff;
                    --pico-border-radius: 0.375rem;
                    --pico-font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                }

                body {
                    min-height: 100vh;
                    background: var(--argo-bg);
                    color: var(--argo-text);
                }

                body > nav {
                    margin: 0;
                    padding: 0.9rem clamp(1rem, 3vw, 2rem);
                    border-bottom: 1px solid var(--argo-border);
                    background: var(--argo-surface);
                    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.05);
                }

                body > nav h1 {
                    margin: 0;
                    color: var(--argo-text);
                    font-size: 1.2rem;
                    line-height: 1.2;
                    white-space: nowrap;
                }

                body > nav ul {
                    align-items: center;
                    gap: 0.25rem;
                }

                body > nav a {
                    display: inline-flex;
                    align-items: center;
                    min-height: 2.25rem;
                    padding: 0.45rem 0.7rem;
                    border-radius: 0.375rem;
                    color: #344054;
                    font-weight: 600;
                    text-decoration: none;
                }

                body > nav a:hover {
                    background: var(--argo-surface-muted);
                    color: var(--argo-text);
                }

                body > nav a[aria-current="page"] {
                    background: var(--argo-accent-soft);
                    color: var(--argo-accent);
                }

                main.container {
                    max-width: 1180px;
                    padding: 2rem 1.25rem 4rem;
                }

                .page-shell {
                    display: grid;
                    gap: 1rem;
                }

                .page-header {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    gap: 1rem;
                }

                .page-header h2,
                .connection-form h3 {
                    margin: 0;
                    color: var(--argo-text);
                    line-height: 1.2;
                }

                .page-header h2 {
                    font-size: 1.85rem;
                }

                .page-actions,
                .form-actions,
                .table-actions {
                    display: flex;
                    align-items: center;
                    gap: 0.65rem;
                    flex-wrap: wrap;
                }

                .primary-action,
                .form-actions button,
                .table-actions button {
                    width: auto;
                    margin: 0;
                }

                .primary-action {
                    padding: 0.7rem 1rem;
                    font-weight: 700;
                }

                .surface-panel {
                    border: 1px solid var(--argo-border);
                    border-radius: 0.5rem;
                    background: var(--argo-surface);
                    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
                }

                #connection-form:empty {
                    display: none;
                }

                #connections-list,
                #audit-log,
                #settings-form {
                    padding: 1.25rem;
                }

                #connections-list > *:last-child,
                #audit-log > *:last-child,
                #settings-form > *:last-child,
                .connection-form > *:last-child {
                    margin-bottom: 0;
                }

                .loading-state,
                .empty-state {
                    color: var(--argo-muted);
                }

                .empty-state {
                    display: grid;
                    min-height: 13rem;
                    place-items: center;
                    text-align: center;
                }

                .empty-state p {
                    margin: 0;
                    max-width: 34rem;
                }

                .connection-form {
                    display: grid;
                    gap: 1rem;
                    padding: 1.25rem;
                }

                .form-section {
                    display: grid;
                    grid-template-columns: repeat(3, minmax(0, 1fr));
                    gap: 1rem;
                    margin: 0;
                    padding: 1rem 0 0;
                    border: 0;
                    border-top: 1px solid var(--argo-border);
                }

                .connection-form h3 + .form-section {
                    padding-top: 0;
                    border-top: 0;
                }

                .form-section legend {
                    grid-column: 1 / -1;
                    margin: 0 0 -0.25rem;
                    color: #344054;
                    font-size: 0.95rem;
                    font-weight: 700;
                }

                .form-section label {
                    margin: 0;
                    color: #344054;
                    font-weight: 600;
                }

                .form-section input,
                .form-section select {
                    margin: 0.35rem 0 0;
                }

                .form-section label:has(input[type="checkbox"]) {
                    align-self: end;
                    min-height: 3.8rem;
                    display: flex;
                    align-items: center;
                    gap: 0.45rem;
                }

                .form-actions {
                    justify-content: flex-end;
                    padding-top: 1rem;
                    border-top: 1px solid var(--argo-border);
                }

                .table-wrap {
                    overflow-x: auto;
                }

                .table-wrap table {
                    margin: 0;
                }

                .table-actions button {
                    padding: 0.4rem 0.65rem;
                    font-size: 0.9rem;
                }

                .badge {
                    display: inline-flex;
                    align-items: center;
                    min-height: 1.5rem;
                    padding: 0.2rem 0.55rem;
                    border-radius: 999px;
                    font-size: 0.8rem;
                    font-weight: 700;
                    line-height: 1;
                }

                .badge-success { background: #dcfce7; color: #166534; }
                .badge-error { background: #fee2e2; color: #991b1b; }
                .badge-blocked { background: #fef3c7; color: #92400e; }
                .badge-active { background: #dbeafe; color: #1d4ed8; }
                .truncate { max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
                .alert { padding: 1em; border-radius: 0.375rem; margin-bottom: 1em; }
                .alert-success { background: #ecfdf3; color: #067647; border: 1px solid #abefc6; }
                .alert-error { background: #fef3f2; color: #b42318; border: 1px solid #fecdca; }

                @media (max-width: 760px) {
                    body > nav {
                        align-items: flex-start;
                        gap: 0.75rem;
                    }

                    body > nav ul:last-child {
                        width: 100%;
                        justify-content: flex-start;
                        overflow-x: auto;
                    }

                    main.container {
                        padding-top: 1.25rem;
                    }

                    .page-header {
                        align-items: stretch;
                        flex-direction: column;
                    }

                    .page-actions,
                    .primary-action {
                        width: 100%;
                    }

                    .form-section {
                        grid-template-columns: 1fr;
                    }

                    .form-actions {
                        justify-content: stretch;
                    }

                    .form-actions button {
                        flex: 1 1 10rem;
                    }
                }
            </style>
            """
        }
    }
    body {
        nav {
            ul {
                li { h1 { +"Argo MCP" } }
            }
            ul {
                li {
                    navLink("/audit", "Audit Log", activePage == "audit")
                }
                li {
                    navLink("/connections", "Connections", activePage == "connections")
                }
                li {
                    navLink("/settings", "Settings", activePage == "settings")
                }
            }
        }
        main(classes = "container") {
            div { content() }
        }
    }
}

private fun kotlinx.html.LI.navLink(href: String, text: String, active: Boolean) {
    a(href = href) {
        if (active) {
            attributes["aria-current"] = "page"
        }
        +text
    }
}
