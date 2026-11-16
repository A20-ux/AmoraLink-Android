plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.amoralink.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.amoralink.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
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
    // 华为 Push Kit（HMS Core）实时推送
    implementation("com.huawei.hms:push:6.12.0.300")
}
