import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystorePropertiesFile = rootProject.file("local.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.signalchain.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.signalchain.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            val storeFileProp = keystoreProperties.getProperty("RELEASE_STORE_FILE")
            if (storeFileProp != null && file(storeFileProp).exists()) {
                storeFile = file(storeFileProp)
                storePassword = keystoreProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = keystoreProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = keystoreProperties.getProperty("RELEASE_KEY_PASSWORD")
            } else {
                val isReleaseBuildRequested = gradle.startParameter.taskNames.any {
                    it.contains("Release", ignoreCase = true)
                }
                if (isReleaseBuildRequested) {
                    throw GradleException(
                        "Release keystore not found or not configured in local.properties. " +
                        "Cannot assemble release without a valid signing configuration."
                    )
                } else {
                    logger.warn("Release keystore not found or not configured in local.properties. Falling back to debug signing.")
                }
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val storeFileProp = keystoreProperties.getProperty("RELEASE_STORE_FILE")
            if (storeFileProp != null && file(storeFileProp).exists()) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                val isReleaseBuildRequested = gradle.startParameter.taskNames.any {
                    it.contains("Release", ignoreCase = true)
                }
                if (isReleaseBuildRequested) {
                    throw GradleException("Release keystore not found. Refusing to produce debug-signed release APK.")
                }
                signingConfig = signingConfigs.getByName("debug")
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    sourceSets.getByName("test").resources.srcDir("src/main/assets")
}

dependencies {
    // Minimal Compose UI (BOM keeps versions in sync)
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")

    // On-device inference for the CNN spectral-mask denoiser (exported to ONNX)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.18.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.microsoft.onnxruntime:onnxruntime:1.18.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

// Keep debug as one universal APK with all existing ABIs; release has three
// targeted APKs plus the universal artifact. x86 remains in the universal APK.
androidComponents {
    onVariants(selector().all()) { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters.firstOrNull { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }?.identifier
            if ((variant.buildType == "debug" && abi != null) || (variant.buildType == "release" && abi == "x86")) output.enabled.set(false)
        }
    }
}
