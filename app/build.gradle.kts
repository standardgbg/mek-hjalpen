plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "se.mekhjalpen"
    compileSdk = 35

    defaultConfig {
        applicationId = "se.mekhjalpen"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.6"
    }

    signingConfigs {
        create("mek") {
            storeFile = file("mek.keystore")
            storePassword = "mekhjalpen"
            keyAlias = "mek"
            keyPassword = "mekhjalpen"
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("mek")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("mek")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
}
