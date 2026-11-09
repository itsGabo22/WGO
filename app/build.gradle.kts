import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Optional service keys from the git-ignored local.properties (see local.properties.example). Missing keys become
// empty strings, so the build never needs them. Values are never printed or logged.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

fun localStringField(key: String): String {
    val value = localProperties.getProperty(key).orEmpty().trim()
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

android {
    namespace = "com.wgo.navigator"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wgo.navigator"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "LLM_API_KEY", localStringField("LLM_API_KEY"))
        buildConfigField("String", "LLM_BASE_URL", localStringField("LLM_BASE_URL"))
        buildConfigField("String", "LLM_MODEL", localStringField("LLM_MODEL"))
        buildConfigField("String", "WGO_CONTACT", localStringField("WGO_CONTACT"))
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    // Fused location (GPS/Wi-Fi/cell fusion runs in the Play services process; falls back to LocationManager).
    implementation("com.google.android.gms:play-services-location:21.3.0")
    
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.wear.compose:compose-material:1.3.0")
    implementation("androidx.wear.compose:compose-foundation:1.3.0")
    implementation("androidx.wear.compose:compose-navigation:1.3.0")
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    
    constraints {
        // Play services still pulls androidx.fragment 1.0.0 transitively; registerForActivityResult needs >= 1.3.0
        // (lint InvalidFragmentVersionForActivityResult). This only raises the transitive version, it adds nothing.
        implementation("androidx.fragment:fragment:1.6.2")
    }

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
