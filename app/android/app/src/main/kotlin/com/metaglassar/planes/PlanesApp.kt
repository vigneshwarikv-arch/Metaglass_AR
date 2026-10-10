package com.metaglassar.planes

import android.app.Application
import com.metaglassar.planes.core.GeoPoint
import com.metaglassar.planes.data.AdsbdbLookup
import com.metaglassar.planes.data.CachingAircraftSource
import com.metaglassar.planes.data.CachingInfoLookup
import com.metaglassar.planes.data.OpenSkyCredentials
import com.metaglassar.planes.data.OpenSkySource
import com.metaglassar.planes.data.SampleSource
import com.metaglassar.planes.data.UrlConnectionTransport
import com.metaglassar.planes.glasses.DatGlassesConnector
import com.metaglassar.planes.glasses.GlassesConnector
import com.metaglassar.planes.glasses.SpokenOutput

/** Wires connectors to the engine. One instance for the app's lifetime. */
class PlanesApp : Application() {
  lateinit var graph: AppGraph
    private set

  override fun onCreate() {
    super.onCreate()
    graph = AppGraph(this)
  }
}

class AppGraph(app: Application) {
  val settings = SettingsStore(app)

  private val http = UrlConnectionTransport(userAgent = "MetaglassAR-prototype/0.1 (non-commercial testing)")

  val openSkyCredentials: OpenSkyCredentials? =
      if (BuildConfig.OPENSKY_CLIENT_ID.isNotBlank() && BuildConfig.OPENSKY_CLIENT_SECRET.isNotBlank()) {
        OpenSkyCredentials(BuildConfig.OPENSKY_CLIENT_ID, BuildConfig.OPENSKY_CLIENT_SECRET)
      } else null

  val liveSource = CachingAircraftSource(OpenSkySource(http, openSkyCredentials))
  val liveLookup = CachingInfoLookup(AdsbdbLookup(http))

  val sampleScenes = SampleSource.loadScenes()
  private val sampleSources = sampleScenes.associate { it.id to SampleSource(it) }

  fun sampleSource(sceneId: String): SampleSource = sampleSources[sceneId] ?: sampleSources.values.first()

  val location = LocationSource(app)
  val orientation = PhoneOrientationSource(app)
  val phoneVoice = SpokenOutput(app)

  /** Last known user position, for the glasses' magnetic declination. */
  @Volatile var lastUserPosition: GeoPoint? = null

  val glasses: GlassesConnector =
      DatGlassesConnector(app, declinationDeg = { lastUserPosition?.let(::declinationAt) ?: 0.0 })
}
