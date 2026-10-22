plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ghoragraphics.khattlab"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ghoragraphics.nawakhtgar"
        minSdk = 24
        targetSdk = 34
        versionCode = 5
        versionName = "2.2"
    }

    // One fixed key, so every new APK installs over the old one (projects are kept)
    signingConfigs {
        create("shared") {
            storeFile = file("nawakhtgar.keystore")
            storePassword = "nawakhtgar"
            keyAlias = "nawakhtgar"
            keyPassword = "nawakhtgar"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            signingConfig = signingConfigs.getByName("shared")
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
