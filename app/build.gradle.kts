import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val signingFile = rootProject.file("keystore.properties")
val signingFileProps = Properties().apply {
    if (signingFile.exists()) signingFile.inputStream().use { load(it) }
}

fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() }
        ?: (project.findProperty(prop) as String?)?.takeIf { it.isNotBlank() }
        ?: signingFileProps.getProperty(prop)?.takeIf { it.isNotBlank() }

val releaseStorePath = signingValue("NOXORA_KEYSTORE_PATH", "noxoraKeystorePath") ?: "noxora.jks"
val releaseStorePassword = signingValue("NOXORA_STORE_PASSWORD", "noxoraStorePassword")
val releaseKeyPassword = signingValue("NOXORA_KEY_PASSWORD", "noxoraKeyPassword")
val releaseKeyAlias = signingValue("NOXORA_KEY_ALIAS", "noxoraKeyAlias") ?: "noxora"
val releaseStoreFile = file(releaseStorePath).takeIf { it.exists() } ?: rootProject.file(releaseStorePath).takeIf { it.exists() }
val canSignRelease = releaseStoreFile != null && releaseStorePassword != null && releaseKeyPassword != null

android {
    namespace = "com.uplink.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.uplink.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "4.0.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
        if (canSignRelease) {
            create("noxora") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (canSignRelease) {
                signingConfig = signingConfigs.getByName("noxora")
            } else {
                signingConfig = signingConfigs.getByName("debug")
                logger.warn("NOXORA_* signing values not set: release is signed with the debug key (V1, V2, V3). Set NOXORA_STORE_PASSWORD, NOXORA_KEY_PASSWORD and NOXORA_KEYSTORE_PATH to sign with the Noxora key.")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA",
                "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*",
                "META-INF/INDEX.LIST", "META-INF/versions/**", "META-INF/AL2.0", "META-INF/LGPL2.1",
                "plugin.properties", "about.html", "META-INF/ECLIPSE_*",
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.github.mwiede:jsch:0.2.18")
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.13.3.202401111512-r")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
}
