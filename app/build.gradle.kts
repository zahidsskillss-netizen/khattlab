plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ghoragraphics.khattlab"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ghoragraphics.khattlab"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
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
    lint {
        abortOnError = false
    }
}

// No external libraries: the app uses only the Android framework,
// which keeps the APK small and the GitHub build fast.
dependencies {
}
