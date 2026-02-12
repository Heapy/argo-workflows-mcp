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
    implementation(platform(ktorLibs.bom))
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(libs.komok.tech.logging)
    implementation(libs.komok.tech.config.dotenv)
    implementation(libs.logback.classic)
    implementation(libs.mcp.kotlin.sdk)

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
