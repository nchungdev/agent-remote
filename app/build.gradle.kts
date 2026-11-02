plugins {
    kotlin("multiplatform") version "2.2.20"
    kotlin("plugin.compose") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
    id("org.jetbrains.compose") version "1.9.0"
    id("com.android.application") version "8.10.1"
}

kotlin {
    applyDefaultHierarchyTemplate()
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    iosArm64()
    iosSimulatorArm64()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        val ktorVersion = "3.1.1"
        val coroutinesVersion = "1.10.1"
        val serializationVersion = "1.8.0"

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)

            // Coroutines
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

            // Serialization
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")

            // Shared Remote Tunnel UI & Core
            implementation("app.duinch:remote-tunnel-kotlin")

            // Ktor Client
            implementation("io.ktor:ktor-client-core:$ktorVersion")
            implementation("io.ktor:ktor-client-websockets:$ktorVersion")
            implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
            implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
        }

        androidMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
            implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
            implementation("androidx.activity:activity-compose:1.10.1")
            implementation("io.getstream:stream-webrtc-android:1.3.8")
            implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:$coroutinesVersion")
                implementation("io.ktor:ktor-client-cio:$ktorVersion")
            }
        }

        val iosMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-darwin:$ktorVersion")
            }
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
        }
    }
}

android {
    namespace = "app.duinch.agentremote"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.duinch.agentremote"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.1.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.duinch.agentremote.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg)
            packageName = "Agent Remote"
            packageVersion = "1.0.0"
            macOS {
                bundleID = "app.duinch.agentremote.desktop"
                dockName = "Agent Remote"
            }
        }
    }
}
