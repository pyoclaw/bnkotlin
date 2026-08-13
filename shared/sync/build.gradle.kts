plugins {
    alias(libs.plugins.kotlinMultiplatform)
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
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
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
