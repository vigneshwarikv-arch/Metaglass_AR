package com.metaglassar.planes.core.pilot

import com.metaglassar.planes.core.Geo
import com.metaglassar.planes.core.GeoPoint

data class NearestAirfield(val airfield: Airfield, val distanceNm: Double, val bearingDeg: Double)

/** Nearest-airport and search over the bundled airport list. Plain scans: ~24k airports take a few ms. */
object Airfields {

  /** The [count] airports nearest to [position], nearest first. */
  fun nearest(position: GeoPoint, airfields: List<Airfield>, count: Int = 1): List<NearestAirfield> =
      airfields
          .asSequence()
          .map { it to Geo.distanceKm(position, it.position) }
          .sortedBy { it.second }
          .take(count)
          .map { (a, km) -> NearestAirfield(a, NavUnits.nm(km), Geo.bearingDeg(position, a.position)) }
          .toList()

  /**
   * Destination search for direct-to: by ICAO (KPAO), FAA (PAO, 0B5), IATA (MAA) or name/city.
   * Exact codes first, then code prefixes, then names; bigger airports first within each group.
   */
  fun search(query: String, airfields: List<Airfield>, limit: Int = 20): List<Airfield> {
    val q = query.trim().uppercase()
    if (q.length < 2) return emptyList()
    return airfields
        .mapNotNull { a -> rank(a, q)?.let { a to it } }
        .sortedWith(compareBy({ it.second }, { it.first.type.ordinal }, { it.first.name }))
        .take(limit)
        .map { it.first }
  }

  private fun rank(a: Airfield, q: String): Int? {
    val codes = listOfNotNull(a.ident, a.icao, a.iata, a.faa)
    if (codes.any { it == q }) return 0
    if (codes.any { it.startsWith(q) }) return 1
    if (q.length < 3) return null
    val words = listOfNotNull(a.name, a.city).map { it.uppercase() }
    if (words.any { w -> w.startsWith(q) || w.split(' ', '-', '/').any { it.startsWith(q) } }) return 2
    if (words.any { it.contains(q) }) return 3
    return null
  }
}
