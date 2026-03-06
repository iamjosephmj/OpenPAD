plugins {
    alias(libs.plugins.openpad.android.library)
    alias(libs.plugins.openpad.android.compose)
    alias(libs.plugins.openpad.hilt)
    alias(libs.plugins.openpad.maven.publish)
}

extra["publish.groupId"] = "com.github.openpad"
extra["publish.artifactId"] = "OpenPAD"
extra["publish.version"] = "1.0.5"

android {
    namespace = "com.openpad.core"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                cFlags("-std=c99")
            }
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            consumerProguardFiles("consumer-rules.pro")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // ML inference
    implementation(libs.litert)
    implementation(libs.litert.gpu)

    // Brotli decompression for .pad model assets
    implementation(libs.brotli.dec)

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.compose)

    // Compose extras
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.core.ktx)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.core)
    androidTestImplementation(libs.test.ext.junit)
}

