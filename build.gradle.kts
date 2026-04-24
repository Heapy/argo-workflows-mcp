plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    application
}

repositories {
    mavenCentral()
}

configurations.all {
    resolutionStrategy {
        failOnVersionConflict()
        // Pre-resolve known transitive conflicts to the project's pinned
        // versions so failOnVersionConflict catches only new/unexpected ones.
        val kotlinVersion = libs.versions.kotlin.get()
        val serializationVersion = libs.versions.kotlinx.serialization.get()
        force(
            "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion",
            "org.jetbrains.kotlin:kotlin-stdlib-common:$kotlinVersion",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion",
            "org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion",
            "org.jetbrains:annotations:23.0.0",
            "org.jetbrains.kotlinx:kotlinx-serialization-core:$serializationVersion",
            "org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion",
            "org.jetbrains.kotlinx:kotlinx-serialization-json-io:$serializationVersion",
            "org.jetbrains.kotlinx:kotlinx-io-core:0.9.0",
            "org.slf4j:slf4j-api:2.0.17",
        )
    }
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
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
    )
}
