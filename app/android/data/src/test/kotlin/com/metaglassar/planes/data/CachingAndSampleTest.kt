package com.metaglassar.planes.data

import com.metaglassar.planes.core.AircraftSource
import com.metaglassar.planes.core.AnswerBuilder
import com.metaglassar.planes.core.FailureReason
import com.metaglassar.planes.core.GeoPoint
import com.metaglassar.planes.core.Pointing
import com.metaglassar.planes.core.Selection
import com.metaglassar.planes.core.SourceResult
import com.metaglassar.planes.core.TargetSelector
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CachingAndSampleTest {
  private val here = GeoPoint(13.0, 80.2)

  private class ScriptedSource(var next: SourceResult) : AircraftSource {
    var calls = 0
    override val name = "scripted"
    override val minRefreshIntervalMs = 10_000L
    override suspend fun aircraftNear(center: GeoPoint, radiusKm: Double): SourceResult {
      calls++
      return next
    }
  }

  @Test
  fun `cache serves repeat calls inside the refresh interval`() = runTest {
    var now = 0L
    val source = ScriptedSource(SourceResult.Success(emptyList(), 0L, null))
    val cached = CachingAircraftSource(source, clock = { now })
    cached.aircraftNear(here, 50.0)
    now = 5_000
    cached.aircraftNear(here, 50.0)
    assertEquals(1, source.calls)
  }

  @Test
  fun `rate limit returns stale data and pauses calls until retry-after`() = runTest {
    var now = 0L
    val source = ScriptedSource(SourceResult.Success(emptyList(), 0L, null))
    val cached = CachingAircraftSource(source, clock = { now })
    cached.aircraftNear(here, 50.0)

    now = 20_000
    source.next = SourceResult.Failure(FailureReason.RateLimited(60))
    val stale = cached.aircraftNear(here, 50.0) as SourceResult.Success
    assertEquals(FailureReason.RateLimited(60), stale.staleBecause)

    now = 50_000 // still inside the 60 s back-off
    cached.aircraftNear(here, 50.0)
    assertEquals(2, source.calls)

    now = 90_000
    source.next = SourceResult.Success(emptyList(), now, null)
    assertTrue((cached.aircraftNear(here, 50.0) as SourceResult.Success).staleBecause == null)
    assertEquals(3, source.calls)
  }

  @Test
  fun `failure with nothing cached is passed through`() = runTest {
    val cached = CachingAircraftSource(ScriptedSource(SourceResult.Failure(FailureReason.NoInternet)))
    assertEquals(SourceResult.Failure(FailureReason.NoInternet), cached.aircraftNear(here, 50.0))
  }

  @Test
  fun `sample scenes load and the planes near Chennai make sense`() = runTest {
    val scenes = SampleSource.loadScenes()
    assertEquals(listOf(SampleSource.CHENNAI, SampleSource.MILPITAS), scenes.map { it.id })
    val chennai = scenes.first()
    val source = SampleSource(chennai, clock = { 0L })
    val all = (source.aircraftNear(chennai.center, 50.0) as SourceResult.Success).aircraft
    val nearby = TargetSelector.nearby(chennai.center, all, 50.0)
    assertTrue(nearby.size in 6..10)
    assertTrue(nearby.none { it.aircraft.icao24 == "sample10" }) // 70 km away: outside the radius

    // The IndiGo flight 25 km north at 32,000 ft: point at it and hear it.
    val target = nearby.first { it.aircraft.callsign == "IGO2045" }
    val selection =
        TargetSelector.select(chennai.center, all, Pointing(target.look.bearingDeg, target.look.elevationDeg))
    val answer = AnswerBuilder.answer(selection)
    assertTrue(selection is Selection.Hit)
    assertEquals("IndiGo A320, Delhi to Chennai, 32,000 feet, 25 kilometres north.", answer.spoken)
  }

  @Test
  fun `sample planes move over time`() = runTest {
    var now = 0L
    val scene = SampleSource.loadScenes().last()
    val source = SampleSource(scene, clock = { now })
    val before = source.snapshot().first { it.callsign == "UAL837" }.position
    now = 30_000
    val after = source.snapshot().first { it.callsign == "UAL837" }.position
    assertTrue(before != after)
  }
}
