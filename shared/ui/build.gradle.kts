plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    // Desktop is the first buildable Compose target in this environment.
    // Android/iOS/Web(Wasm) targets are added in the customer/kitchen/admin
    // slices once the matching SDKs are available. See PLAN.md.
    jvm("desktop")

    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.brodogfyld.ui.MainKt"
    }
}
