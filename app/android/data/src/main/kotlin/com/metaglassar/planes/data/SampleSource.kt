package com.metaglassar.planes.data

import com.metaglassar.planes.core.Aircraft
import com.metaglassar.planes.core.AircraftInfo
import com.metaglassar.planes.core.AircraftInfoLookup
import com.metaglassar.planes.core.AircraftSource
import com.metaglassar.planes.core.Airport
import com.metaglassar.planes.core.Geo
import com.metaglassar.planes.core.GeoPoint
import com.metaglassar.planes.core.Route
import com.metaglassar.planes.core.SourceResult
import com.metaglassar.planes.core.Units
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** A place with recorded (synthetic) traffic: Chennai and Milpitas. */
data class SampleScene(
    val id: String,
    val name: String,
    val center: GeoPoint,
    val aircraft: List<Aircraft>,
)

/**
 * Offline data connector backed by `sample/flights.json`. Planes drift along their tracks at
 * [speedFactor] of real speed and the recording loops every [loopSeconds]: they visibly move, but
 * stay within a few km of their recorded positions so testers can always find them.
 * In sample mode the app also uses the scene's centre as the user's location.
 */
class SampleSource(
    val scene: SampleScene,
    private val clock: () -> Long = System::currentTimeMillis,
    private val loopSeconds: Long = 300,
    private val speedFactor: Double = 0.1,
) : AircraftSource, AircraftInfoLookup {

  override val name = "Sample data — ${scene.name}"
  override val minRefreshIntervalMs = 2_000L
  override val attribution: String? = "Sample data (not real flights)"

  private val startMs = clock()

  override suspend fun aircraftNear(center: GeoPoint, radiusKm: Double): SourceResult =
      SourceResult.Success(snapshot(), clock(), attribution)

  /** Positions advanced by elapsed time along each aircraft's track. */
  fun snapshot(): List<Aircraft> {
    val t = ((clock() - startMs) / 1000.0) % loopSeconds * speedFactor
    return scene.aircraft.map { a ->
      val speed = a.speedMs ?: 0.0
      val track = a.trackDeg
      if (a.onGround || speed <= 0.0 || track == null) a
      else {
        val moved = Geo.destination(a.position, track, speed * t / 1000.0)
        val alt = a.altitudeM?.let { (it + (a.verticalRateMs ?: 0.0) * t).coerceAtLeast(150.0) }
        a.copy(position = moved, altitudeM = alt)
      }
    }
  }

  override suspend fun lookup(icao24: String, callsign: String?): AircraftInfo =
      scene.aircraft.firstOrNull { it.icao24 == icao24 }?.info ?: AircraftInfo()

  companion object {
    const val CHENNAI = "chennai"
    const val MILPITAS = "milpitas"

    private const val KT_TO_MS = 0.514444
    private const val FPM_TO_MS = 0.00508

    /** Loads all scenes from the bundled JSON resource. */
    fun loadScenes(): List<SampleScene> {
      val text =
          SampleSource::class.java.getResourceAsStream("/sample/flights.json")?.bufferedReader()?.use { it.readText() }
              ?: error("sample/flights.json missing from resources")
      return parseScenes(text)
    }

    fun parseScenes(text: String): List<SampleScene> {
      val root = Json.parseToJsonElement(text).jsonObject
      val airports =
          root["airports"]!!.jsonObject.mapValues { (_, v) ->
            val o = v.jsonObject
            Airport(icao = o.s("icao"), iata = o.s("iata"), name = o.s("name"), city = o.s("city"))
          }
      return root["scenes"]!!.jsonArray.map { sceneEl ->
        val scene = sceneEl.jsonObject
        val c = scene["center"]!!.jsonObject
        val center = GeoPoint(c.d("lat")!!, c.d("lon")!!, c.d("altitudeM") ?: 0.0)
        val flights =
            scene["flights"]!!.jsonArray.map { fEl ->
              val f = fEl.jsonObject
              val from = f.s("from")?.let(airports::get)
              val to = f.s("to")?.let(airports::get)
              Aircraft(
                  icao24 = f.s("icao24")!!,
                  callsign = f.s("callsign"),
                  position = Geo.destination(center, f.d("bearingDeg")!!, f.d("distanceKm")!!).copy(altitudeM = 0.0),
                  altitudeM = f.d("altitudeFt")?.let { it / Units.FEET_PER_METRE },
                  speedMs = f.d("speedKt")?.let { it * KT_TO_MS },
                  trackDeg = f.d("trackDeg"),
                  verticalRateMs = f.d("verticalRateFpm")?.let { it * FPM_TO_MS },
                  onGround = (f["onGround"] as? JsonPrimitive)?.booleanOrNull ?: false,
                  info =
                      AircraftInfo(
                          airline = f.s("airline"),
                          typeCode = f.s("typeCode"),
                          typeName = f.s("typeName"),
                          registration = f.s("registration"),
                          route = if (from != null || to != null) Route(from, to) else null,
                      ),
              )
            }
        SampleScene(scene.s("id")!!, scene.s("name")!!, center, flights)
      }
    }

    private fun JsonObject.s(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.d(key: String) = (this[key] as? JsonPrimitive)?.doubleOrNull
  }
}
