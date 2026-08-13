plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // Milestone 1 compiles the JVM target only. Android, iOS and Web/Wasm
    // targets are added in later slices once the matching SDKs/toolchains are
    // available in development and CI. All domain code lives in commonMain so
    // that adding those targets is a build-script-only change. See PLAN.md.
    jvm()

    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
