plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.deliverybox"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.deliverybox"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.google.android.material:material:1.11.0")

    // Firebase BoM - 버전 자동 관리
    implementation(platform("com.google.firebase:firebase-bom:32.8.0"))

    // Firebase 라이브러리
    // Firebase Authentication
    implementation("com.google.firebase:firebase-auth-ktx")
    // Firebase Firestore
    implementation("com.google.firebase:firebase-firestore-ktx")
    // Firebase Cloud Messaging
    implementation("com.google.firebase:firebase-messaging-ktx")


}