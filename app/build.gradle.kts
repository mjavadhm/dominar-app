import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")  // Required with Kotlin 2.0+
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
}

// Neshan keys (https://platform.neshan.org):
// - NESHAN_SDK_KEY: map SDK key (registered with package name + SHA1), injected
//   into AndroidManifest as org.maplibre.android.API_KEY.
// - NESHAN_API_KEY: web-services key (search / reverse geocode / routing).
// - NESHAN_WEB_KEY (optional): web SDK key; some tile services (e.g. traffic)
//   may only be enabled for web keys, so the traffic overlay also tries it.
// Resolution order: local.properties > gradle property > environment variable.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(name: String): String =
    localProperties.getProperty(name)
        ?: (project.findProperty(name) as String?)
        ?: System.getenv(name)
        ?: ""

val neshanApiKey = secret("NESHAN_API_KEY")
val neshanSdkKey = secret("NESHAN_SDK_KEY")
val neshanWebKey = secret("NESHAN_WEB_KEY")

android {
    namespace = "com.dominar.ride"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dominar.ride"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "NESHAN_API_KEY", "\"$neshanApiKey\"")
        buildConfigField("String", "NESHAN_SDK_KEY", "\"$neshanSdkKey\"")
        buildConfigField("String", "NESHAN_WEB_KEY", "\"$neshanWebKey\"")
        manifestPlaceholders["NESHAN_SDK_KEY"] = neshanSdkKey

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }
    // NOTE: composeOptions.kotlinCompilerExtensionVersion is NOT needed with Kotlin 2.0+
    // The compose compiler is now bundled and managed via the kotlin.plugin.compose plugin.
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.activity:activity-compose:1.9.2")

    // Compose BOM (2024.09.00 aligns with Kotlin 2.0+ / Compose 1.7)
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")

    // Lifecycle ViewModel Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")

    // Compose Navigation
    implementation("androidx.navigation:navigation-compose:2.8.1")

    // Hilt For DI (2.57.1 is compatible with Kotlin 2.2)
    implementation("com.google.dagger:hilt-android:2.57.1")
    kapt("com.google.dagger:hilt-compiler:2.57.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Room (local database for Garage & Performance data)
    // 2.7.2+ is required with Kotlin 2.2: older room-compiler versions can't
    // read Kotlin 2.2 class metadata ("maximum supported version is 2.0.0").
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    kapt("androidx.room:room-compiler:2.7.2")

    // WorkManager (daily garage reminder checks)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Neshan map SDK (new MapLibre-based SDK, published on Maven Central)
    implementation("org.neshan.maplibre:android-sdk-opengl:13.4.1")

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Allow references to generated code (Hilt)
kapt {
    correctErrorTypes = true
}
