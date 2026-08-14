plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    // JVM first; Android/iOS/Web targets are added with the customer/kitchen
    // apps once the matching SDKs are available (PLAN.md). All schema and
    // generated code lives in commonMain so adding targets is build-script-only.
    jvm()

    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:domain"))
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

sqldelight {
    databases {
        create("KitchenDatabase") {
            packageName.set("com.brodogfyld.sync.db")
        }
    }
}
