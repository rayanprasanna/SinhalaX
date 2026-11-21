import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// Secrets live in local.properties (never committed). Add:
//   KLIPY_API_KEY=your_key
//   AZURE_TRANSLATOR_KEY=your_microsoft_key      (translate; free 2M chars/month)
//   AZURE_TRANSLATOR_REGION=your_resource_region (e.g. southeastasia; leave out for "global")
//   GOOGLE_TRANSLATE_API_KEY=your_google_cloud_key (translate fallback)
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val klipyApiKey: String = localProps.getProperty("KLIPY_API_KEY") ?: System.getenv("KLIPY_API_KEY") ?: ""
val azureTranslatorKey: String = localProps.getProperty("AZURE_TRANSLATOR_KEY") ?: System.getenv("AZURE_TRANSLATOR_KEY") ?: ""
val azureTranslatorRegion: String = localProps.getProperty("AZURE_TRANSLATOR_REGION") ?: System.getenv("AZURE_TRANSLATOR_REGION") ?: ""
val translateApiKey: String = localProps.getProperty("GOOGLE_TRANSLATE_API_KEY") ?: System.getenv("GOOGLE_TRANSLATE_API_KEY") ?: ""

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.valhalla.sinhalax"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.valhalla.sinhalax"
        minSdk = 24
        //noinspection EditedTargetSdkVersion
        targetSdk = 37
        versionCode = 24
        versionName = "0.10.0"

        buildConfigField("String", "KLIPY_API_KEY", "\"$klipyApiKey\"")
        buildConfigField("String", "GOOGLE_TRANSLATE_API_KEY", "\"$translateApiKey\"")
        buildConfigField("String", "AZURE_TRANSLATOR_KEY", "\"$azureTranslatorKey\"")
        buildConfigField("String", "AZURE_TRANSLATOR_REGION", "\"$azureTranslatorRegion\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

//noinspection WrongGradleMethod
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:engine"))
    implementation(project(":core:prediction"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.savedstate.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
}