plugins {
  alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin: no Android imports allowed here, so the engine runs (and is tested) on any JVM.
kotlin { jvmToolchain(17) }

dependencies {
  testImplementation(libs.junit)
}
