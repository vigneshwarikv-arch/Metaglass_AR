package com.metaglassar.planes.core

/**
 * Short spoken names for common ICAO type designators ("A20N" is hard to say; "A320neo" is not).
 * Unknown codes fall back to the readable type name from the lookup service, then to the code.
 */
object AircraftTypes {
  private val spoken =
      mapOf(
          "A319" to "A319", "A320" to "A320", "A321" to "A321",
          "A19N" to "A319neo", "A20N" to "A320neo", "A21N" to "A321neo",
          "A332" to "A330", "A333" to "A330", "A339" to "A330neo",
          "A343" to "A340", "A346" to "A340",
          "A359" to "A350", "A35K" to "A350-1000", "A388" to "A380",
          "BCS1" to "A220", "BCS3" to "A220",
          "B737" to "737", "B738" to "737", "B739" to "737",
          "B37M" to "737 MAX", "B38M" to "737 MAX", "B39M" to "737 MAX",
          "B744" to "747", "B748" to "747-8",
          "B752" to "757", "B763" to "767", "B764" to "767",
          "B772" to "777", "B77L" to "777", "B773" to "777", "B77W" to "777",
          "B788" to "787", "B789" to "787", "B78X" to "787",
          "AT72" to "ATR 72", "AT76" to "ATR 72", "AT75" to "ATR 72", "AT45" to "ATR 42",
          "DH8D" to "Dash 8", "Q400" to "Dash 8",
          "E170" to "Embraer 170", "E175" to "Embraer 175", "E190" to "Embraer 190", "E195" to "Embraer 195",
          "E75L" to "Embraer 175", "E290" to "Embraer E2", "E295" to "Embraer E2",
          "CRJ7" to "CRJ-700", "CRJ9" to "CRJ-900",
          "C172" to "Cessna 172", "C182" to "Cessna 182", "C208" to "Cessna Caravan",
          "PC12" to "Pilatus PC-12", "SR22" to "Cirrus SR22", "P28A" to "Piper Cherokee",
          "B06" to "helicopter", "R44" to "Robinson R44 helicopter", "EC35" to "helicopter", "EC45" to "helicopter",
      )

  fun spokenName(info: AircraftInfo): String? =
      info.typeCode?.uppercase()?.let { spoken[it] } ?: info.typeName ?: info.typeCode
}
