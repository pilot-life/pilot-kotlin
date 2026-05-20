import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    `maven-publish`
}

kotlin {
    jvmToolchain(17)

    androidTarget {
        publishLibraryVariants("release")
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
            }
        }
    }

    val xcf = XCFramework("PilotPartnerUi")
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "PilotPartnerUi"
            // Compose Multiplatform on iOS doesn't support static frameworks
            // (it ships its own dynamic libs at runtime), so we link dynamic.
            isStatic = false
            xcf.add(this)
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(project(":pilot-partner-sdk"))

            // Compose Multiplatform — package names remain androidx.compose.*
            // The org.jetbrains.compose plugin rewires them to the JetBrains
            // fork on iOS and to androidx on Android automatically.
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)

            api(libs.jetbrains.lifecycle.viewmodel)
            api(libs.jetbrains.lifecycle.viewmodel.compose)
            api(libs.jetbrains.lifecycle.runtime.compose)

            implementation(libs.coil3.compose)
            implementation(libs.coil3.network.ktor)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ionspin.bignum)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.assertk)
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.lifecycle.runtime.ktx)
        }
    }
}

tasks.register("assembleXCFramework") {
    group = "build"
    description = "Assembles PilotPartnerUi.xcframework (Release) for iOS SwiftPM distribution."
    dependsOn("assemblePilotPartnerUiReleaseXCFramework")
}

android {
    namespace = "life.pilot.partner.ui"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // androidTest deps — instrumented tests stay Android-only.
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    debugImplementation(libs.compose.ui.test.manifest)
}

afterEvaluate {
    publishing {
        publications {
            withType<MavenPublication>().configureEach {
                pom {
                    name.set("Pilot Partner UI (Compose Multiplatform)")
                    description.set("Compose Multiplatform components for displaying Pilot partner-API events and tickets — Android + iOS.")
                    url.set("https://github.com/pilot-life/pilot-kotlin")
                    licenses {
                        license { name.set("Proprietary") }
                    }
                }
            }
        }
    }
}
