plugins {
    id("com.android.application")
}

android {
    namespace = "com.codex.women"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.codex.women"
        minSdk = 26
        targetSdk = 35
        versionCode = 20300
        versionName = "2.3.0"
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
}
