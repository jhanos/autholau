plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace   = "com.autholau"
    compileSdk  = 34
    buildToolsVersion = "35.0.1"

    defaultConfig {
        applicationId = "com.autholau"
        minSdk        = 26
        targetSdk     = 34
        versionCode   = 1
        versionName   = "1.0"
    }

    signingConfigs {
        create("releaseKey") {
            storeFile     = file("/home/jhanos/.android/jhanos-android.keystore")
            storePassword = "android"
            keyAlias      = "my-key-alias"
            keyPassword   = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled   = false
            isShrinkResources = false
            signingConfig     = signingConfigs.getByName("releaseKey")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }

    dependenciesInfo {
        includeInApk    = false
        includeInBundle = false
    }

    lint {
        checkReleaseBuilds = false
        abortOnError       = false
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
