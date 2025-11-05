plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(ktorLibs.client.cio)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.client.contentNegotiation)
    implementation(libs.kotlinx.datetime)
}
