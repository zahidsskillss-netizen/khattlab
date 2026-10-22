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
        versionCode = 10
        versionName = "2.7"
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

// Only one library: Google's on-phone AI that finds the person / object in a photo
// (background removal). Its model is downloaded by Google Play services, so the APK stays small.
dependencies {
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")
}
