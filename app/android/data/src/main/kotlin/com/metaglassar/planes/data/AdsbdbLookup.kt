package com.metaglassar.planes.data

import com.metaglassar.planes.core.AircraftInfo
import com.metaglassar.planes.core.AircraftInfoLookup
import com.metaglassar.planes.core.Airport
import com.metaglassar.planes.core.Route
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Airline, aircraft type, registration and route from adsbdb.com — free, no API key, for prototyping.
 *
 * One call per aircraft: `GET /v0/aircraft/{icao24}?callsign={callsign}` returns both the airframe
 * (type, registration) and, when the callsign is known, the flight route. If the airframe is
 * unknown (404) we fall back to `GET /v0/callsign/{callsign}` for the route alone.
 * Community route data can be out of date for a given day; treat it as "usually right".
 *
 * Terms (checked Oct 2026): the adsbdb code is MIT-licensed; its route data comes from community
 * sources whose owners ask for permission before reuse. Fine for prototyping and testing; replace
 * with a licensed route/schedule feed before a paid launch. Be gentle: cache results (see
 * [CachingInfoLookup]) and only look up the aircraft a user is actually looking at.
 */
class AdsbdbLookup(
    private val http: HttpTransport,
) : AircraftInfoLookup {

  override val attribution = "Aircraft and route data: adsbdb.com"

  override suspend fun lookup(icao24: String, callsign: String?): AircraftInfo =
      withContext(Dispatchers.IO) {
        val query = callsign?.let { "?callsign=" + URLEncoder.encode(it, "UTF-8") } ?: ""
        try {
          val response = http.get("$API/aircraft/${icao24.lowercase()}$query")
          when {
            response.code == 200 -> parse(response.body)
            // Unknown airframe hides the route too, so ask for the route by callsign alone.
            callsign != null -> {
              val byCallsign = http.get("$API/callsign/" + URLEncoder.encode(callsign, "UTF-8"))
              if (byCallsign.code == 200) parse(byCallsign.body) else AircraftInfo()
            }
            else -> AircraftInfo()
          }
        } catch (e: IOException) {
          throw LookupUnavailable(e)
        } catch (e: IllegalArgumentException) {
          AircraftInfo()
        }
      }

  /** Thrown when the service can't be reached, so callers don't cache "unknown" for a network blip. */
  class LookupUnavailable(cause: Throwable) : Exception(cause)

  companion object {
    const val API = "https://api.adsbdb.com/v0"

    fun parse(body: String): AircraftInfo {
      val response = Json.parseToJsonElement(body).jsonObject["response"] as? JsonObject ?: return AircraftInfo()
      val aircraft = response["aircraft"] as? JsonObject
      val route = response["flightroute"] as? JsonObject
      val airline = route?.get("airline") as? JsonObject
      return AircraftInfo(
          airline = airline.str("name"),
          typeCode = aircraft.str("icao_type"),
          typeName = listOfNotNull(aircraft.str("manufacturer"), aircraft.str("type")).joinToString(" ").ifEmpty { null },
          registration = aircraft.str("registration"),
          route =
              route?.let {
                Route(origin = airport(it["origin"]), destination = airport(it["destination"]))
                    .takeIf { r -> r.origin != null || r.destination != null }
              },
      )
    }

    private fun airport(e: JsonElement?): Airport? {
      val o = e as? JsonObject ?: return null
      return Airport(
          icao = o.str("icao_code"),
          iata = o.str("iata_code"),
          name = o.str("name"),
          city = o.str("municipality"),
      )
    }

    private fun JsonObject?.str(key: String): String? =
        (this?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
  }
}
