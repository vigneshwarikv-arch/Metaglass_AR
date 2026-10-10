package com.metaglassar.planes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metaglassar.planes.UiState
import com.metaglassar.planes.core.Airport
import com.metaglassar.planes.core.CompassPoint
import com.metaglassar.planes.core.SeenAircraft
import com.metaglassar.planes.core.Units
import kotlin.math.roundToInt

private const val UNKNOWN = "Unknown"

@Composable
fun DetailsScreen(seen: SeenAircraft?, state: UiState, onBack: () -> Unit) {
  Column(Modifier.fillMaxSize()) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
      IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
      Text("Plane details", style = MaterialTheme.typography.titleMedium)
    }
    if (seen == null) {
      EmptyState("This plane is out of range", "It has flown out of the area or stopped broadcasting.")
      return
    }
    val a = seen.aircraft
    val unit = state.settings.unit
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(a.titleText(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

      // Route
      Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
          AirportBlock(a.info.route?.origin, "From", Modifier.weight(1f))
          Text("→", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
          AirportBlock(a.info.route?.destination, "To", Modifier.weight(1f), end = true)
        }
      }

      Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
          Fact("Flight / callsign", a.callsign)
          Fact("Airline", a.info.airline)
          Fact("Aircraft type", a.info.typeName ?: a.info.typeCode)
          Fact("Registration", a.info.registration)
          Fact("Altitude", if (a.onGround) "On the ground" else Units.altitudeText(a.altitudeM))
          Fact("Speed", Units.speedText(a.speedMs, unit))
          Fact("Heading", a.trackDeg?.let { "${it.roundToInt()}° ${CompassPoint.of(it).short}" })
          Fact("Climb / descent", a.verticalRateMs?.let { vr ->
            val fpm = (vr * 196.85).roundToInt()
            when {
              fpm > 200 -> "Climbing ${Units.groupThousands(fpm.toLong())} ft/min"
              fpm < -200 -> "Descending ${Units.groupThousands(-fpm.toLong())} ft/min"
              else -> "Level"
            }
          })
          Fact("From you", "${Units.distanceText(seen.look.groundDistanceKm, unit)} ${CompassPoint.of(seen.look.bearingDeg).spoken}, ${seen.look.elevationDeg.roundToInt()}° above the horizon")
          Fact("Transponder (ICAO 24-bit)", a.icao24.uppercase(), last = true)
        }
      }
      Text(
          "Routes come from community data and can occasionally be out of date.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Attribution(state.attributions, Modifier.padding(bottom = 16.dp))
    }
  }
}

@Composable
private fun AirportBlock(airport: Airport?, label: String, modifier: Modifier, end: Boolean = false) {
  Column(modifier, horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(airport?.code ?: UNKNOWN, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(airport?.city ?: airport?.name ?: "", style = MaterialTheme.typography.bodySmall)
  }
}

@Composable
private fun Fact(label: String, value: String?, last: Boolean = false) {
  Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    Text(
        value ?: UNKNOWN,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (value == null) FontWeight.Normal else FontWeight.SemiBold,
        color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
  }
  if (!last) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}
