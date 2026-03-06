plugins {
    `kotlin-dsl`
}

group = "com.openpad.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "openpad.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "openpad.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "openpad.android.compose"
            implementationClass = "ComposeConventionPlugin"
        }
        register("hilt") {
            id = "openpad.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("mavenPublish") {
            id = "openpad.maven.publish"
            implementationClass = "MavenPublishConventionPlugin"
        }
    }
}
