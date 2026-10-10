import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

// Machine-local values (never committed): see app/android/README.md.
val localProperties =
    Properties().apply {
      rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }

fun local(key: String, default: String = "") = localProperties.getProperty(key, default)

fun quoted(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
  namespace = "com.metaglassar.planes"
  compileSdk {
    version = release(36) { minorApiLevel = 1 }
  }

  defaultConfig {
    applicationId = "com.metaglassar.planes"
    minSdk = 31 // Android 12; DAT needs Android 10+, BLUETOOTH_CONNECT handling is simpler on 12+.
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0"

    // Optional OpenSky API client (free account). Empty = anonymous access (400 requests/day).
    buildConfigField("String", "OPENSKY_CLIENT_ID", quoted(local("opensky.clientId")))
    buildConfigField("String", "OPENSKY_CLIENT_SECRET", quoted(local("opensky.clientSecret")))

    // DAT attestation IDs from the Wearables Developer Center. "0" = Developer Mode (attestation skipped).
    manifestPlaceholders["mwdat_application_id"] = local("mwdat.applicationId", "0")
    manifestPlaceholders["mwdat_client_token"] = local("mwdat.clientToken", "0")
  }

  buildTypes {
    release {
      isMinifyEnabled = false
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
}

dependencies {
  implementation(project(":core"))
  implementation(project(":data"))
  implementation(project(":glasses"))

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
