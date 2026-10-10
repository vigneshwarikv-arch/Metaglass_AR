package com.metaglassar.planes.data

import com.metaglassar.planes.core.Aircraft
import com.metaglassar.planes.core.AircraftSource
import com.metaglassar.planes.core.FailureReason
import com.metaglassar.planes.core.Geo
import com.metaglassar.planes.core.GeoPoint
import com.metaglassar.planes.core.SourceResult
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** OpenSky API client credentials (OAuth2 client-credentials flow). Read from local.properties; never committed. */
data class OpenSkyCredentials(val clientId: String, val clientSecret: String)

/**
 * Live aircraft positions from the OpenSky Network REST API (`/states/all` with a bounding box).
 *
 * Terms: free for NON-COMMERCIAL use only — a commercial feed is needed before a paid launch.
 * Rate limits (2026 docs): anonymous 400 credits/day at 10 s resolution; a free account 4,000/day at
 * 5 s. A box under 25 square degrees costs 1 credit, which our 50–100 km circles always are.
 */
class OpenSkySource(
    private val http: HttpTransport,
    private val credentials: OpenSkyCredentials? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) : AircraftSource {

  override val name = "OpenSky Network (live)"

  /** Anonymous: 400 requests/day, so poll gently. Authenticated: 4,000/day. */
  override val minRefreshIntervalMs: Long = if (credentials != null) 10_000 else 30_000

  private val tokenLock = Mutex()
  private var token: String? = null
  private var tokenExpiresAtMs = 0L

  override suspend fun aircraftNear(center: GeoPoint, radiusKm: Double): SourceResult =
      withContext(Dispatchers.IO) {
        val box = Geo.boundingBox(center, radiusKm.coerceAtMost(MAX_RADIUS_KM))
        val url =
            "$API/states/all?lamin=${box.latMin}&lomin=${box.lonMin}&lamax=${box.latMax}&lomax=${box.lonMax}"
        try {
          val auth = bearerToken()
          var response = http.get(url, auth?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap())
          if (response.code == 401 && credentials != null) {
            // Token expired early: fetch a new one once.
            invalidateToken()
            response = http.get(url, mapOf("Authorization" to "Bearer ${bearerToken()}"))
          }
          when (response.code) {
            200 -> SourceResult.Success(parseStates(response.body), clock(), ATTRIBUTION)
            429 ->
                SourceResult.Failure(
                    FailureReason.RateLimited(response.header("X-Rate-Limit-Retry-After-Seconds")?.toLongOrNull()))
            401, 403 -> SourceResult.Failure(FailureReason.Unauthorized("OpenSky rejected the credentials"))
            else -> SourceResult.Failure(FailureReason.Http(response.code))
          }
        } catch (e: IOException) {
          SourceResult.Failure(FailureReason.NoInternet)
        } catch (e: TokenException) {
          SourceResult.Failure(FailureReason.Unauthorized(e.message ?: "OpenSky login failed"))
        } catch (e: IllegalArgumentException) {
          // Malformed JSON.
          SourceResult.Failure(FailureReason.Other("Unexpected response from OpenSky"))
        }
      }

  private suspend fun bearerToken(): String? {
    val creds = credentials ?: return null
    return tokenLock.withLock {
      token?.takeIf { clock() < tokenExpiresAtMs }
          ?: run {
            val response =
                http.postForm(
                    TOKEN_URL,
                    mapOf(
                        "grant_type" to "client_credentials",
                        "client_id" to creds.clientId,
                        "client_secret" to creds.clientSecret,
                    ),
                )
            if (response.code != 200) throw TokenException("OpenSky login failed (HTTP ${response.code})")
            val obj = Json.parseToJsonElement(response.body).jsonObject
            val newToken = obj["access_token"]?.jsonPrimitive?.content ?: throw TokenException("No token in response")
            val expiresInSec = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 1800
            token = newToken
            // Refresh a minute early.
            tokenExpiresAtMs = clock() + (expiresInSec - 60).coerceAtLeast(60) * 1000
            newToken
          }
    }
  }

  private suspend fun invalidateToken() = tokenLock.withLock { token = null }

  private class TokenException(message: String) : Exception(message)

  companion object {
    const val API = "https://opensky-network.org/api"
    const val TOKEN_URL = "https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token"
    const val ATTRIBUTION = "Flight positions: The OpenSky Network (opensky-network.org)"
    const val MAX_RADIUS_KM = 150.0

    /**
     * Parses `/states/all`. Each state is an array: 0 icao24, 1 callsign, 2 origin_country,
     * 3 time_position, 4 last_contact, 5 longitude, 6 latitude, 7 baro_altitude, 8 on_ground,
     * 9 velocity, 10 true_track, 11 vertical_rate, 12 sensors, 13 geo_altitude, 14 squawk, ...
     */
    fun parseStates(body: String): List<Aircraft> {
      val root = Json.parseToJsonElement(body).jsonObject
      val states = root["states"]?.takeIf { it !is JsonNull }?.jsonArray ?: return emptyList()
      return states.mapNotNull { element ->
        val s = element as? JsonArray ?: return@mapNotNull null
        fun str(i: Int) = (s.getOrNull(i) as? JsonPrimitive)?.takeIf { it.isString }?.content
        fun num(i: Int) = (s.getOrNull(i) as? JsonPrimitive)?.doubleOrNull
        val lat = num(6) ?: return@mapNotNull null
        val lon = num(5) ?: return@mapNotNull null
        val icao24 = str(0)?.lowercase() ?: return@mapNotNull null
        Aircraft(
            icao24 = icao24,
            callsign = str(1)?.trim()?.takeIf { it.isNotEmpty() },
            position = GeoPoint(lat, lon),
            altitudeM = num(13) ?: num(7),
            speedMs = num(9),
            trackDeg = num(10),
            verticalRateMs = num(11),
            onGround = (s.getOrNull(8) as? JsonPrimitive)?.booleanOrNull ?: false,
            originCountry = str(2),
            lastSeenEpochSec = (s.getOrNull(4) as? JsonPrimitive)?.longOrNull,
        )
      }
    }
  }
}
