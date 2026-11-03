plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.okan.ajanda"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.okan.ajanda"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    // İmza anahtarı repoya konmaz. keystore/ajanda.keystore varsa onunla, yoksa varsayılan debug anahtarıyla imzalanır.
    // Parolalar ortam değişkeninden okunur: AJANDA_STORE_PASSWORD / AJANDA_KEY_ALIAS / AJANDA_KEY_PASSWORD
    val ajandaKeystore = rootProject.file("keystore/ajanda.keystore")
    signingConfigs {
        if (ajandaKeystore.exists()) {
            create("ajanda") {
                storeFile = ajandaKeystore
                storePassword = System.getenv("AJANDA_STORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("AJANDA_KEY_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("AJANDA_KEY_PASSWORD") ?: "android"
            }
        }
    }
    val releaseSigning = signingConfigs.findByName("ajanda") ?: signingConfigs.getByName("debug")

    buildTypes {
        debug {
            signingConfig = releaseSigning
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = releaseSigning
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
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("roborazzi.test.record", "true")
                it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2/")
                it.maxHeapSize = "3g"
            }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    testImplementation(libs.junit)
    // Ekran görüntüsü testleri (JVM üzerinde Compose çizimi)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
