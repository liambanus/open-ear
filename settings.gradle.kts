pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
  plugins {
    id("org.jetbrains.kotlin.android") version "1.9.24"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24"
//    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")


  }
}

//pluginManagement {
//  repositories {
//    google()
//    mavenCentral()
//    gradlePluginPortal()
//  }
//}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "open-ear"
//include ':app' // For the original Ionic/Capacitor android app
//include ':maestro' // For our new native app
include(":app")      // For the original Ionic/Capacitor android app
include(":maestro")  // For our new native app
include(":external:speech-to-text")

