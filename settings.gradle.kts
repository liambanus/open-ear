pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}
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
