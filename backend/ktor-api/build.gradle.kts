plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

application {
    mainClass.set("com.brodogfyld.api.ApplicationKt")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared:domain"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.websockets)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logback.classic)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(kotlin("test"))
}
