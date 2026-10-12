package com.metaglassar.planes.data

import com.metaglassar.planes.core.AircraftInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdsbdbLookupTest {
  // Real responses from api.adsbdb.com (Oct 2026), trimmed.
  private val aircraftAndRoute =
      """{"response":{"aircraft":{"type":"A320 251NSL","icao_type":"A20N","manufacturer":"Airbus","mode_s":"80139E","registration":"VT-TNR","registered_owner":"Vistara","url_photo":null},
      "flightroute":{"callsign":"AIC1JV","airline":{"name":"Air India","icao":"AIC","iata":"AI"},
      "origin":{"iata_code":"BOM","icao_code":"VABB","municipality":"Mumbai","name":"Chhatrapati Shivaji International Airport"},
      "destination":{"iata_code":"MAA","icao_code":"VOMM","municipality":"Chennai","name":"Chennai International Airport"}}}}"""

  private val routeOnly =
      """{"response":{"flightroute":{"callsign":"IGO2045","airline":{"name":"IndiGo Airlines","icao":"IGO","iata":"6E"},
      "origin":{"iata_code":"DEL","icao_code":"VIDP","municipality":"New Delhi","name":"Indira Gandhi International Airport"},
      "destination":{"iata_code":"ATQ","icao_code":"VIAR","municipality":"Amritsar","name":"Sri Guru Ram Dass Jee International Airport"}}}}"""

  @Test
  fun `parses aircraft and route`() {
    val info = AdsbdbLookup.parse(aircraftAndRoute)
    assertEquals("Air India", info.airline)
    assertEquals("A20N", info.typeCode)
    assertEquals("Airbus A320 251NSL", info.typeName)
    assertEquals("VT-TNR", info.registration)
    assertEquals("Mumbai", info.route!!.origin!!.city)
    assertEquals("MAA", info.route!!.destination!!.code)
  }

  @Test
  fun `unknown is empty`() {
    assertEquals(AircraftInfo(), AdsbdbLookup.parse("""{"response":"unknown aircraft"}"""))
  }

  @Test
  fun `unknown airframe falls back to the callsign route`() = runTest {
    val http = FakeHttp().apply {
      on("${AdsbdbLookup.API}/aircraft/", 404, """{"response":"unknown aircraft"}""")
      on("${AdsbdbLookup.API}/callsign/IGO2045", 200, routeOnly)
    }
    val info = AdsbdbLookup(http).lookup("8016d4", "IGO2045")
    assertEquals("IndiGo Airlines", info.airline)
    assertEquals("Amritsar", info.route!!.destination!!.city)
    assertNull(info.typeCode)
  }

  @Test
  fun `cache avoids repeat lookups but does not cache offline failures`() = runTest {
    val http = FakeHttp().apply { on("${AdsbdbLookup.API}/aircraft/", 200, aircraftAndRoute) }
    val lookup = CachingInfoLookup(AdsbdbLookup(http), clock = { 0L })
    lookup.lookup("80139e", "AIC1JV")
    lookup.lookup("80139e", "AIC1JV")
    assertEquals(1, http.calls.size)

    http.offline = true
    assertEquals(AircraftInfo(), lookup.lookup("aaaaaa", "X"))
    http.offline = false
    lookup.lookup("aaaaaa", "X")
    assertEquals(3, http.calls.size) // retried after coming back online
  }
}
