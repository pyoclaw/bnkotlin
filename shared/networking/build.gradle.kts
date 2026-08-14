plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // JVM first, matching shared:sync; Android/iOS engines are added when those
    // targets land (PLAN.md). Ktor's HTTP client is multiplatform, so the
    // transport code itself lives in commonMain.
    jvm()

    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:sync"))
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
