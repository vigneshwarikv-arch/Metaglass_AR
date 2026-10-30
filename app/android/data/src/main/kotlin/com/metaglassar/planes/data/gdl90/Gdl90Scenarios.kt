package com.metaglassar.planes.data.gdl90

import com.metaglassar.planes.core.Brand
import com.metaglassar.planes.core.Geo
import com.metaglassar.planes.core.GeoPoint
import com.metaglassar.planes.core.pilot.NavUnits
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Scripted GDL 90 traffic for testing without flying (`gdl90/scenarios.json`, shared with the iPhone
 * app and tools/gdl90-replay). [frames] produces exactly what a Stratux would send in one second:
 * heartbeat, ForeFlight ID, ownship, ownship geometric altitude, traffic reports — all framed with CRC.
 */
class Gdl90Scenario(
    val id: String,
    val name: String,
    val durationSec: Int,
    private val own: Own,
    private val traffic: List<Traffic>,
) {
  data class Own(val icao: Int, val callsign: String, val start: GeoPoint, val altFt: Double, val trackDeg: Double, val gsKt: Double)

  sealed interface Traffic {
    val icao: Int

    data class Meet(
        override val icao: Int, val callsign: String, val relBearingDeg: Double, val distNm: Double,
        val relAltFt: Double, val meetSec: Double, val missNm: Double, val vsFpm: Double,
    ) : Traffic

    data class Orbit(
        override val icao: Int, val callsign: String, val center: GeoPoint, val radiusNm: Double,
        val gsKt: Double, val altFt: Double, val clockwise: Boolean, val startDeg: Double,
    ) : Traffic

    data class Ground(override val icao: Int, val callsign: String, val start: GeoPoint, val trackDeg: Double, val gsKt: Double) : Traffic

    data class Shadow(override val icao: Int) : Traffic
  }

  /** One aircraft's state at a moment. */
  data class State(
      val icao: Int, val callsign: String, val position: GeoPoint, val altFt: Double, val trackDeg: Double,
      val gsKt: Double, val vsFpm: Double, val airborne: Boolean, val addressType: Int = 0,
  )

  fun ownshipAt(tSec: Double): State =
      State(own.icao, own.callsign, along(own.start, own.trackDeg, own.gsKt, tSec), own.altFt, own.trackDeg, own.gsKt, 0.0, true)

  fun trafficAt(tSec: Double): List<State> = traffic.map { stateOf(it, tSec) }

  private fun stateOf(t: Traffic, tSec: Double): State =
      when (t) {
        is Traffic.Meet -> {
          val start = Geo.destination(own.start, own.trackDeg + t.relBearingDeg, NavUnits.km(t.distNm))
          val target = Geo.destination(ownshipAt(t.meetSec).position, own.trackDeg, NavUnits.km(t.missNm))
          val track = Geo.bearingDeg(start, target)
          val gs = NavUnits.nm(Geo.distanceKm(start, target)) / t.meetSec * 3600.0
          State(t.icao, t.callsign, along(start, track, gs, tSec), own.altFt + t.relAltFt + t.vsFpm * tSec / 60.0, track, gs, t.vsFpm, true)
        }
        is Traffic.Orbit -> {
          val dir = if (t.clockwise) 1.0 else -1.0
          val degPerSec = (t.gsKt / 3600.0) / (2 * Math.PI * t.radiusNm) * 360.0
          val angle = t.startDeg + dir * degPerSec * tSec
          State(
              t.icao, t.callsign, Geo.destination(t.center, angle, NavUnits.km(t.radiusNm)), t.altFt,
              Geo.normalizeDeg(angle + dir * 90.0), t.gsKt, 0.0, true)
        }
        is Traffic.Ground -> State(t.icao, "", along(t.start, t.trackDeg, t.gsKt, tSec), 0.0, t.trackDeg, t.gsKt, 0.0, false)
        is Traffic.Shadow -> ownshipAt(tSec).copy(icao = t.icao, callsign = "", addressType = 2)
      }

  /**
   * The datagram a receiver would send at [tSec] into the scenario (it loops after [durationSec]).
   * [utcSec] is the heartbeat time stamp (seconds since midnight UTC).
   */
  fun frames(tSec: Double, utcSec: Int, deviceName: String = "SimRcvr"): ByteArray {
    val t = tSec % durationSec
    val o = ownshipAt(t)
    val msgs = mutableListOf(
        Gdl90Encoder.heartbeat(gpsValid = true, timestampSec = utcSec),
        Gdl90Encoder.foreFlightId(deviceName, "${Brand.NAME} sim", geoAltitudeIsMsl = false),
        report(o, ownship = true),
        // Like Stratux: geometric altitude is height above the ellipsoid, ~100 ft below MSL in California.
        Gdl90Encoder.geoAltitude(o.altFt - ELLIPSOID_OFFSET_FT),
    )
    trafficAt(t).forEach { msgs += report(it, ownship = false) }
    return msgs.map(Gdl90::frame).reduce { a, b -> a + b }
  }

  private fun report(s: State, ownship: Boolean) =
      Gdl90Encoder.report(
          ownship = ownship, address = s.icao, latDeg = s.position.lat, lonDeg = s.position.lon,
          pressureAltitudeFt = s.altFt, airborne = s.airborne, trackDeg = s.trackDeg,
          horizontalVelocityKt = s.gsKt, verticalVelocityFpm = s.vsFpm, callsign = s.callsign,
          addressType = s.addressType, emitterCategory = if (s.airborne) 1 else 18)

  companion object {
    const val ELLIPSOID_OFFSET_FT = 100.0

    private fun along(start: GeoPoint, trackDeg: Double, gsKt: Double, tSec: Double) =
        Geo.destination(start, trackDeg, NavUnits.km(gsKt * tSec / 3600.0))

    fun loadAll(): List<Gdl90Scenario> {
      val text =
          Gdl90Scenario::class.java.getResourceAsStream("/gdl90/scenarios.json")?.bufferedReader()?.use { it.readText() }
              ?: error("gdl90/scenarios.json missing from resources")
      return parse(text)
    }

    fun parse(json: String): List<Gdl90Scenario> =
        Json.parseToJsonElement(json).jsonObject["scenarios"]!!.jsonArray.map { e ->
          val s = e.jsonObject
          val o = s["ownship"]!!.jsonObject
          Gdl90Scenario(
              id = s.str("id"),
              name = s.str("name"),
              durationSec = s["durationSec"]!!.jsonPrimitive.int,
              own = Own(o.hex("icao"), o.str("callsign"), GeoPoint(o.num("lat"), o.num("lon")), o.num("altFt"), o.num("trackDeg"), o.num("gsKt")),
              traffic = s["traffic"]!!.jsonArray.map { trafficOf(it.jsonObject) },
          )
        }

    private fun trafficOf(t: JsonObject): Traffic =
        when (val kind = t.str("kind")) {
          "meet" -> Traffic.Meet(t.hex("icao"), t.str("callsign"), t.num("relBearingDeg"), t.num("distNm"), t.num("relAltFt"), t.num("meetSec"), t.num("missNm"), t.numOr("vsFpm", 0.0))
          "orbit" -> Traffic.Orbit(t.hex("icao"), t.str("callsign"), GeoPoint(t.num("centerLat"), t.num("centerLon")), t.num("radiusNm"), t.num("gsKt"), t.num("altFt"), t["clockwise"]?.jsonPrimitive?.booleanOrNull ?: false, t.numOr("startDeg", 0.0))
          "ground" -> Traffic.Ground(t.hex("icao"), t.str("callsign"), GeoPoint(t.num("lat"), t.num("lon")), t.num("trackDeg"), t.num("gsKt"))
          "shadow" -> Traffic.Shadow(t.hex("icao"))
          else -> error("unknown traffic kind '$kind'")
        }

    private fun JsonObject.str(k: String) = this[k]!!.jsonPrimitive.content

    private fun JsonObject.num(k: String) = this[k]!!.jsonPrimitive.double

    private fun JsonObject.numOr(k: String, d: Double) = this[k]?.jsonPrimitive?.doubleOrNull ?: d

    private fun JsonObject.hex(k: String) = str(k).toInt(16)
  }
}
