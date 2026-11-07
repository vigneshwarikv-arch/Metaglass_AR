package com.metaglassar.planes.data

import com.metaglassar.planes.core.GeoPoint
import com.metaglassar.planes.core.pilot.AirfieldType
import com.metaglassar.planes.core.pilot.Airfields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Same cases as AirportDatabaseTests in app/ios/PlanesKit/Tests/PlanesDataTests/Gdl90Tests.swift.
class AirportDatabaseTest {
  private val airports = AirportDatabase.load()

  @Test
  fun `bundled list loads`() {
    assertTrue(airports.size > 20_000)
    val pao = airports.first { it.ident == "KPAO" }
    assertEquals("Palo Alto Airport", pao.name)
    assertEquals("PAO", pao.faa)
    assertEquals(AirfieldType.MEDIUM, pao.type)
  }

  @Test
  fun `US fields with only an FAA identifier are included`() {
    val f = airports.first { it.ident == "0B5" }
    assertEquals(null, f.icao)
    assertEquals("US", f.country)
  }

  @Test
  fun `search and nearest on the real list`() {
    assertEquals("KPAO", Airfields.search("KPAO", airports).first().ident)
    assertEquals("VOMM", Airfields.search("MAA", airports).first().ident)
    val nearest = Airfields.nearest(GeoPoint(12.99, 80.17), airports).single()
    assertEquals("VOMM", nearest.airfield.ident)
  }
}
