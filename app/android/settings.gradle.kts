pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
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

rootProject.name = "WhatsThatPlane"

// core: pure Kotlin engine (no Android). data: aircraft + route connectors (pure Kotlin).
// glasses: Meta glasses output connector (DAT). app: the phone app.
include(":core", ":data", ":glasses", ":app")
