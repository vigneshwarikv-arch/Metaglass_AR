plugins {
  alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin data connectors (OpenSky, adsbdb, sample data). HTTP uses java.net.HttpURLConnection,
// which exists on both Android and the JVM, so the same code runs in the app and in tests.
kotlin { jvmToolchain(17) }

dependencies {
  api(project(":core"))
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
