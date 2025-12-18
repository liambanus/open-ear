
plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.serialization")
}


android {
  namespace = "com.openear.maestro"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.openear.maestro"
    minSdk = 26
    targetSdk = 34
    ndkVersion = "27.0.12077973"

    externalNativeBuild {
      cmake {
        arguments(
          "-DBUILD_JNI_LIB=ON",
          "-DBUILD_SHARED_LIBS=OFF",
          "-DGGML_CPU_KLEIDIAI=OFF"
        )
      }
    }

    ndk {
      abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
    }
  }

  buildFeatures { compose = true }

  composeOptions {
    kotlinCompilerExtensionVersion = "1.5.14"
  }

  buildTypes {
    debug { isJniDebuggable = true }
    release {
      isMinifyEnabled = false
      externalNativeBuild {
        cmake { arguments += "-DCMAKE_BUILD_TYPE=Release" }
      }
    }
  }

  externalNativeBuild {
    cmake {
      path = file("../external/speech-to-text/CMakeLists.txt")
      version = "3.27.0+"
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions { jvmTarget = "17" }
}

dependencies {
  implementation(project(":external:speech-to-text"))

  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.activity:activity-compose:1.9.3")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

  implementation(platform("androidx.compose:compose-bom:2024.02.00"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.material3:material3")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
//  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")


  debugImplementation("androidx.compose.ui:ui-tooling")

// Whisper Integ
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.2")


}
