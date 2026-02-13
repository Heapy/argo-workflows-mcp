plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":argo-client"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.html)
    implementation(platform(ktorLibs.bom))
    implementation(ktorLibs.server.cio)
    implementation(ktorLibs.server.htmlBuilder)
    implementation(ktorLibs.server.sse)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(libs.komok.tech.logging)
    implementation(libs.logback.classic)
    implementation(libs.mcp.kotlin.sdk)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.sqlite.jdbc)

    testImplementation(libs.kotlinx.coroutines.test)

    detektPlugins(libs.detekt.rules.ktlint.wrapper)
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter("6.0.2")
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = "io.heapy.argo.workflows.mcp.AppKt"
}
