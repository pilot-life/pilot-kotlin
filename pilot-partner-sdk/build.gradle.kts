plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `java-library`
    `maven-publish`
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        freeCompilerArgs.addAll(
            "-Xjvm-default=all",
            "-opt-in=kotlin.RequiresOptIn",
        )
    }
}

dependencies {
    api(libs.kotlin.stdlib)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)

    api(libs.okhttp)
    // logging is `api` because PilotPartnerClient.Builder.logging() takes
    // HttpLoggingInterceptor.Level — consumers need the type to compile.
    api(libs.okhttp.logging)
    api(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.assertk)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
            artifactId = "pilot-partner-sdk"
            pom {
                name.set("Pilot Partner SDK")
                description.set("Kotlin/JVM client for the Pilot Partner Inventory API (PIL-2370).")
                url.set("https://github.com/pilot-life/pilot-kotlin")
                licenses {
                    license {
                        name.set("Proprietary")
                    }
                }
            }
        }
    }
}
