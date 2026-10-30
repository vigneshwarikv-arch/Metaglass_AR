package com.metaglassar.planes.data

import com.metaglassar.planes.core.GeoPoint
import com.metaglassar.planes.core.pilot.Airfield
import com.metaglassar.planes.core.pilot.AirfieldType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The bundled airport list (`airports/airports.json`, built by tools/airports/build_airports.py from
 * OurAirports, public domain; the iPhone app has an identical copy). ~24k airports, loaded once.
 */
object AirportDatabase {
  const val ATTRIBUTION = "Airports: OurAirports (public domain)"

  fun load(): List<Airfield> {
    val text =
        AirportDatabase::class.java.getResourceAsStream("/airports/airports.json")?.bufferedReader()?.use { it.readText() }
            ?: error("airports/airports.json missing from resources")
    return parse(text)
  }

  /** Rows: [ident, icao, iata, faa, name, city, country, lat, lon, elevationFt, type]. */
  fun parse(json: String): List<Airfield> =
      Json.parseToJsonElement(json).jsonObject["airports"]!!.jsonArray.map { e ->
        val r = e.jsonArray
        Airfield(
            ident = r.str(0)!!,
            icao = r.str(1),
            iata = r.str(2),
            faa = r.str(3),
            name = r.str(4)!!,
            city = r.str(5),
            country = r.str(6)!!,
            position = GeoPoint(r[7].jsonPrimitive.double, r[8].jsonPrimitive.double),
            elevationFt = r[9].takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull,
            type =
                when (r.str(10)) {
                  "L" -> AirfieldType.LARGE
                  "M" -> AirfieldType.MEDIUM
                  "W" -> AirfieldType.SEAPLANE
                  else -> AirfieldType.SMALL
                },
        )
      }

  private fun JsonArray.str(i: Int): String? = this[i].takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull
}
