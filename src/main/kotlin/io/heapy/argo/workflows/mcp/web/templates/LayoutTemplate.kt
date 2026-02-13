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
    head {
        meta { charset = "utf-8" }
        meta { name = "viewport"; this.content = "width=device-width, initial-scale=1" }
        title { +pageTitle }
        link(rel = "stylesheet", href = "https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.min.css")
        script(src = "https://unpkg.com/htmx.org@2.0.4") {}
        unsafe {
            +"""
            <style>
                .badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 0.8em; font-weight: bold; }
                .badge-success { background: #2ecc71; color: white; }
                .badge-error { background: #e74c3c; color: white; }
                .badge-blocked { background: #f39c12; color: white; }
                .badge-active { background: #3498db; color: white; }
                .truncate { max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
                nav ul li a[aria-current="page"] { font-weight: bold; }
                .alert { padding: 1em; border-radius: 4px; margin-bottom: 1em; }
                .alert-success { background: #d4edda; color: #155724; border: 1px solid #c3e6cb; }
                .alert-error { background: #f8d7da; color: #721c24; border: 1px solid #f5c6cb; }
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
                li { a(href = "/audit") { if (activePage == "audit") attributes["aria-current"] = "page"; +"Audit Log" } }
                li { a(href = "/connections") { if (activePage == "connections") attributes["aria-current"] = "page"; +"Connections" } }
                li { a(href = "/settings") { if (activePage == "settings") attributes["aria-current"] = "page"; +"Settings" } }
            }
        }
        main(classes = "container") {
            div { content() }
        }
    }
}
