plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "com.metaglassar.planes.glasses"
  compileSdk {
    version = release(36) { minorApiLevel = 1 }
  }
  defaultConfig {
    minSdk = 31
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  api(project(":core"))
  implementation(libs.kotlinx.coroutines.android)
  // Meta Wearables Device Access Toolkit 1.0 (Maven Central, no token needed).
  // Using it means agreeing to the Meta Wearables Developer Terms: https://wearables.developer.meta.com/terms
  api(libs.mwdat.core)
  implementation(libs.mwdat.motion)
  implementation(libs.mwdat.speech)
  implementation(libs.mwdat.display)
  // Simulated glasses for testing without hardware (Settings → Simulated glasses).
  implementation(libs.mwdat.mockdevice)
}
