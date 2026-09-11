plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pofu.rider"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pofu.rider"
        minSdk = 26
        targetSdk = 35
        // CI her push'ta -PversionCode=<run number> geciyor; yerelde derlerken 1 kalir.
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "1.0-yerel"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // Debug anahtariyla imzaliyoruz ki CI imzali bir APK uretsin.
            // Play Store'a cikacaksan burayi kendi keystore'unla degistir.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
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
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // Model dosyalari sikistirilirsa Vosk acarken cok yavasliyor.
        jniLibs.useLegacyPackaging = true
    }
    androidResources {
        noCompress += listOf("mdl", "fst", "int", "ie", "dubm", "mat", "conf", "uuid")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.media:media:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Cihaz uzerinde konusma tanima. Anahtar istemez, internet istemez.
    implementation("com.alphacephei:vosk-android:0.3.47@aar")
    implementation("net.java.dev.jna:jna:5.13.0@aar")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
