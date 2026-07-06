plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "dk.akait.hawidgets"
    compileSdk = 35

    defaultConfig {
        applicationId = "dk.akait.hawidgets"
        minSdk = 26
        targetSdk = 35
        versionCode = 38
        versionName = "0.2.38"
    }

    buildTypes {
        debug {
            buildConfigField("String", "DEV_URL", "\"https://home.rtr.dk:8123\"")
            buildConfigField(
                "String", "DEV_TOKEN",
                "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                "eyJpc3MiOiI4NzQ3MTRiOGJhMTM0OTExODNhMTVkNDdmYjIxODM1NyIsImlhdCI6MTc4MjQxNDM4NiwiZXhwIjoyMDk3Nzc0Mzg2fQ." +
                "R62LIUzVFfSpbsfrDgTiCTfGy7_9MOPg8waVThhNKLg\""
            )
        }
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // Glance widgets
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // Secure storage (Keystore-backed)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Secure WebView bridge (WebViewCompat.addWebMessageListener for externalAppV2)
    implementation("androidx.webkit:webkit:1.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Room
    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    ksp("androidx.room:room-compiler:2.7.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
