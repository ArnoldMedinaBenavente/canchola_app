import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    id("kotlin-kapt")
}
// --- AGREGA ESTO AQUÍ ---
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.canchola"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.canchola"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val url = localProperties.getProperty("BASE_URL") ?: "https://fallback.url/"
        buildConfigField("String", "BASE_URL", "\"$url\"")
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        buildConfig = true // Asegúrate de que esto esté activo
        viewBinding = true
    }


}

dependencies {

    implementation("androidx.core:core-ktx:1.13.1")

    // También asegúrate de que appcompat no esté en una versión experimental
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // Red y API (Retrofit)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")


    // Imágenes (Cargar fotos de doctores si las tienes en URL)
    implementation("com.github.bumptech.glide:glide:4.16.0")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // Opcional: Para usar LiveData con Corrutinas
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    // Ciclo de vida y ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")



    // Para Fragment/BottomSheet
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    // Para Activity
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")
    /// BASES DE DATOS
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    kapt("androidx.room:room-compiler:$room_version")


}