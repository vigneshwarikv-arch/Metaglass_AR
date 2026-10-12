package com.metaglassar.planes.data

import com.metaglassar.planes.core.FailureReason
import com.metaglassar.planes.core.GeoPoint
import com.metaglassar.planes.core.SourceResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSkySourceTest {
  private val chennai = GeoPoint(13.0, 80.2)

  // Shape copied from a real /states/all response (Oct 2026), values trimmed.
  private val body =
      """
      {"time":1791600000,"states":[
        ["80139e","AIC1JV  ","India",1791599998,1791599999,80.21,12.95,1828.8,false,95.2,10.5,-4.2,null,1889.76,"1234",false,0],
        ["8016d4","IGO971  ","India",1791599998,1791599999,80.16,12.99,null,true,0.0,90.0,null,null,null,null,false,0],
        ["89653c","        ","United Arab Emirates",null,1791599990,null,null,11582.4,false,250.0,300.0,0.0,null,11620.5,null,false,0]
      ]}
      """.trimIndent()

  @Test
  fun `parses state vectors and skips ones without a position`() {
    val list = OpenSkySource.parseStates(body)
    assertEquals(2, list.size)
    val a = list[0]
    assertEquals("80139e", a.icao24)
    assertEquals("AIC1JV", a.callsign)
    assertEquals(12.95, a.position.lat, 1e-9)
    assertEquals(80.21, a.position.lon, 1e-9)
    assertEquals(1889.76, a.altitudeM!!, 1e-9) // geometric altitude preferred
    assertEquals(95.2, a.speedMs!!, 1e-9)
    assertFalse(a.onGround)
    assertTrue(list[1].onGround)
    assertNull(list[1].altitudeM)
  }

  @Test
  fun `empty sky returns states null`() {
    assertTrue(OpenSkySource.parseStates("""{"time":1,"states":null}""").isEmpty())
  }

  @Test
  fun `anonymous request uses a bounding box and no auth`() = runTest {
    val http = FakeHttp().apply { on(OpenSkySource.API, 200, body) }
    val result = OpenSkySource(http).aircraftNear(chennai, 50.0)
    assertTrue(result is SourceResult.Success)
    assertEquals(2, (result as SourceResult.Success).aircraft.size)
    assertEquals(OpenSkySource.ATTRIBUTION, result.attribution)
    assertTrue(http.calls.single().contains("lamin="))
  }

  @Test
  fun `rate limit is reported with retry-after`() = runTest {
    val http = FakeHttp().apply { on(OpenSkySource.API, 429, "", mapOf("X-Rate-Limit-Retry-After-Seconds" to "120")) }
    val result = OpenSkySource(http).aircraftNear(chennai, 50.0) as SourceResult.Failure
    assertEquals(FailureReason.RateLimited(120), result.reason)
  }

  @Test
  fun `offline is reported as no internet`() = runTest {
    val http = FakeHttp().apply { offline = true }
    val result = OpenSkySource(http).aircraftNear(chennai, 50.0) as SourceResult.Failure
    assertEquals(FailureReason.NoInternet, result.reason)
  }

  @Test
  fun `credentials fetch a token once and reuse it`() = runTest {
    val http = FakeHttp().apply {
      on(OpenSkySource.TOKEN_URL, 200, """{"access_token":"abc","expires_in":1800}""")
      on(OpenSkySource.API, 200, body)
    }
    val source = OpenSkySource(http, OpenSkyCredentials("id", "secret"), clock = { 0L })
    source.aircraftNear(chennai, 50.0)
    source.aircraftNear(chennai, 50.0)
    assertEquals(1, http.calls.count { it.startsWith("POST") })
    assertEquals(10_000L, source.minRefreshIntervalMs)
  }

  @Test
  fun `bad credentials are reported as unauthorized`() = runTest {
    val http = FakeHttp().apply { on(OpenSkySource.TOKEN_URL, 401, "") }
    val result = OpenSkySource(http, OpenSkyCredentials("id", "wrong")).aircraftNear(chennai, 50.0)
    assertTrue((result as SourceResult.Failure).reason is FailureReason.Unauthorized)
  }
}
