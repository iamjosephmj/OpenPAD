plugins {
    alias(libs.plugins.openpad.android.application)
    alias(libs.plugins.openpad.android.compose)
    alias(libs.plugins.openpad.hilt)
}

android {
    namespace = "com.openpad.app"

    defaultConfig {
        applicationId = "com.openpad.app"
        versionCode = 1
        versionName = "1.0"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":pad-core"))

    implementation(libs.compose.material.icons)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.compose)

    implementation(libs.timber)
    implementation(libs.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.test.core)
    testImplementation(libs.test.ext.junit)
}
