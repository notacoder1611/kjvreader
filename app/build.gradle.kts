plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.realsneed.biblereader"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.realsneed.biblereader"
        minSdk = 21
        targetSdk = 34
        versionCode = 40
        versionName = "0.25.6"
        resourceConfigurations += listOf("en")

    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Deliberately minimal: no Compose, no Firebase, no ads, no analytics.
    implementation("androidx.appcompat:appcompat:1.7.0")
    // Swipe page turning + the chip grids in the passage picker.
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
}
