package com.metaglassar.planes.data

import com.metaglassar.planes.core.Aircraft
import com.metaglassar.planes.core.AircraftInfo
import com.metaglassar.planes.core.AircraftInfoLookup
import com.metaglassar.planes.core.AircraftSource
import com.metaglassar.planes.core.FailureReason
import com.metaglassar.planes.core.Geo
import com.metaglassar.planes.core.GeoPoint
import com.metaglassar.planes.core.SourceResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Wraps any [AircraftSource] so callers can ask as often as they like:
 * - repeated calls within the source's minimum interval (and for roughly the same place) get the cached answer;
 * - after a rate limit, no calls are made until the provider's retry-after time has passed;
 * - if a fetch fails, the last good answer is returned marked as stale, with the reason.
 */
class CachingAircraftSource(
    private val delegate: AircraftSource,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Re-fetch if the user moved further than this since the cached fetch. */
    private val moveThresholdKm: Double = 2.0,
) : AircraftSource by delegate {

  private val lock = Mutex()
  private var last: Cached? = null
  private var blockedUntilMs = 0L
  private var blockedReason: FailureReason? = null

  private data class Cached(val center: GeoPoint, val radiusKm: Double, val result: SourceResult.Success)

  override suspend fun aircraftNear(center: GeoPoint, radiusKm: Double): SourceResult =
      lock.withLock {
        val now = clock()
        val cached = last
        val sameArea =
            cached != null && cached.radiusKm >= radiusKm && Geo.distanceKm(cached.center, center) < moveThresholdKm
        if (sameArea && now - cached!!.result.fetchedAtMs < delegate.minRefreshIntervalMs) return cached.result
        if (now < blockedUntilMs) {
          return if (cached != null) cached.result.copy(staleBecause = blockedReason) else SourceResult.Failure(blockedReason!!)
        }

        when (val result = delegate.aircraftNear(center, radiusKm)) {
          is SourceResult.Success -> {
            last = Cached(center, radiusKm, result)
            blockedReason = null
            result
          }
          is SourceResult.Failure -> {
            val reason = result.reason
            if (reason is FailureReason.RateLimited) {
              blockedUntilMs = now + (reason.retryAfterSec ?: 60L) * 1000
              blockedReason = reason
            }
            if (cached != null && Geo.distanceKm(cached.center, center) < 25.0) {
              cached.result.copy(staleBecause = reason)
            } else {
              result
            }
          }
        }
      }
}

/** Remembers lookups (by icao24 + callsign) so each flight is looked up once per [ttlMs]. */
class CachingInfoLookup(
    private val delegate: AircraftInfoLookup,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ttlMs: Long = 6 * 60 * 60 * 1000L,
    private val maxEntries: Int = 500,
) : AircraftInfoLookup by delegate {

  private val lock = Mutex()
  private val cache = object : LinkedHashMap<String, Pair<Long, AircraftInfo>>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, AircraftInfo>>) = size > maxEntries
  }

  override suspend fun lookup(icao24: String, callsign: String?): AircraftInfo {
    val key = "$icao24|${callsign.orEmpty()}"
    lock.withLock { cache[key]?.takeIf { clock() - it.first < ttlMs }?.let { return it.second } }
    val info =
        try {
          delegate.lookup(icao24, callsign)
        } catch (e: AdsbdbLookup.LookupUnavailable) {
          return AircraftInfo() // offline: don't cache, try again next time
        }
    lock.withLock { cache[key] = clock() to info }
    return info
  }
}

/** Adds lookup info to the [limit] nearest aircraft (the ones on screen), a few requests at a time. */
suspend fun enrich(
    aircraft: List<Aircraft>,
    lookup: AircraftInfoLookup,
    limit: Int = 20,
    parallelism: Int = 4,
): List<Aircraft> = coroutineScope {
  val gate = Semaphore(parallelism)
  aircraft
      .mapIndexed { i, a ->
        async {
          if (i >= limit) a
          else gate.withPermit { a.copy(info = merge(a.info, lookup.lookup(a.icao24, a.callsign))) }
        }
      }
      .map { it.await() }
}

private fun merge(base: AircraftInfo, extra: AircraftInfo) =
    AircraftInfo(
        airline = base.airline ?: extra.airline,
        typeCode = base.typeCode ?: extra.typeCode,
        typeName = base.typeName ?: extra.typeName,
        registration = base.registration ?: extra.registration,
        route = base.route ?: extra.route,
    )
