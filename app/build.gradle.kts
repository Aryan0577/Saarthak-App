plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {


    namespace = "com.example.saarthak"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.saarthak.beta"
        minSdk = 30
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)


// JSON parsing
    implementation ("com.squareup.okhttp3:okhttp:4.9.3")

    // CameraX
    val camerax_version = "1.3.0-rc01"
    implementation ("androidx.camera:camera-core:$camerax_version")
    implementation ("androidx.camera:camera-camera2:$camerax_version")
    implementation ("androidx.camera:camera-lifecycle:$camerax_version")
    implementation ("androidx.camera:camera-view:$camerax_version")

    // ML Kit for text recognition

    // TensorFlow Lite dependencies
    implementation (libs.tensorflow.lite)
    implementation (libs.tensorflow.lite.gpu)
    implementation ("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation ("org.tensorflow:tensorflow-lite-select-tf-ops:2.3.0")


    implementation ("androidx.compose.material:material-icons-extended:1.3.1")

    implementation ("com.google.mlkit:object-detection:17.0.2")
    implementation ("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.ai.client.generativeai:generativeai:0.7.0")
    implementation ("androidx.core:core-ktx:1.10.0")
    implementation (libs.tensorflow.tensorflow.lite.metadata)
    implementation ("org.tensorflow:tensorflow-lite-task-vision:0.4.2")
    implementation ("com.squareup.retrofit2:retrofit:2.9.0")
    implementation ("com.squareup.retrofit2:converter-gson:2.9.0")
    // Accompanist for permissions handling
    implementation ("com.google.accompanist:accompanist-permissions:0.31.1-alpha")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}