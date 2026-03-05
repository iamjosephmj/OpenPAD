plugins {
    alias(libs.plugins.openpad.android.library)
    alias(libs.plugins.openpad.android.compose)
    alias(libs.plugins.openpad.hilt)
    `maven-publish`
}

val publishVersion = "1.0.5"
val publishGroupId = "com.github.openpad"

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
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.core)
    androidTestImplementation(libs.test.ext.junit)
}

afterEvaluate {
    publishing {
        publications {
            create("release", MavenPublication::class) {
                from(components["release"])
                groupId = publishGroupId
                artifactId = "OpenPAD"
                version = publishVersion
                pom {
                    name.set("OpenPAD")
                    description.set("On-device Presentation Attack Detection (face liveness) for Android")
                    url.set("https://github.com/openpad/OpenPAD")
                    licenses {
                        license {
                            name.set("Apache-2.0")
                            url.set("https://opensource.org/licenses/Apache-2.0")
                        }
                    }
                }
            }
            create("debug", MavenPublication::class) {
                from(components["debug"])
                groupId = publishGroupId
                artifactId = "OpenPAD-debug"
                version = publishVersion
            }
        }
    }
}
