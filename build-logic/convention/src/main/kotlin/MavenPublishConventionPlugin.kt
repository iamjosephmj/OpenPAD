import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get

class MavenPublishConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("maven-publish")

            extensions.configure<LibraryExtension> {
                publishing {
                    singleVariant("release") { withSourcesJar() }
                    singleVariant("debug")
                }
            }

            afterEvaluate {
                val groupId = findProperty("publish.groupId") as? String ?: "com.github.openpad"
                val artifactId = findProperty("publish.artifactId") as? String ?: project.name
                val ver = findProperty("publish.version") as? String ?: "1.0.0"

                extensions.configure<PublishingExtension> {
                    publications {
                        create<MavenPublication>("release") {
                            from(components["release"])
                            this.groupId = groupId
                            this.artifactId = artifactId
                            this.version = ver
                            pom {
                                name.set(artifactId)
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
                        create<MavenPublication>("debug") {
                            from(components["debug"])
                            this.groupId = groupId
                            this.artifactId = "$artifactId-debug"
                            this.version = ver
                        }
                    }
                }
            }
        }
    }
}
