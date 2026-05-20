plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
}

allprojects {
    group = "life.pilot"
    version = providers.gradleProperty("VERSION_NAME").getOrElse("0.1.0-SNAPSHOT")
}

// Apply GitHub Packages publishing config to any submodule that applies
// the `maven-publish` plugin. Credentials are read from env vars so this
// is a no-op for local dev (publishToMavenLocal still works).
subprojects {
    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension>("publishing") {
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/pilot-life/pilot-kotlin")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR") ?: providers.gradleProperty("gpr.user").orNull
                        password = System.getenv("GITHUB_TOKEN") ?: providers.gradleProperty("gpr.token").orNull
                    }
                }
            }
        }
    }
}
