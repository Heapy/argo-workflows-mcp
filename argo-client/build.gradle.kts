plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(ktorLibs.client.java)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.client.contentNegotiation)
}
