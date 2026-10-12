package com.metaglassar.planes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metaglassar.planes.DataMode
import com.metaglassar.planes.Settings
import com.metaglassar.planes.UiState
import com.metaglassar.planes.core.Brand
import com.metaglassar.planes.core.DistanceUnit
import com.metaglassar.planes.core.Units
import com.metaglassar.planes.data.SampleScene
import com.metaglassar.planes.glasses.GlassesStatus
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    state: UiState,
    scenes: List<SampleScene>,
    onChange: ((Settings) -> Settings) -> Unit,
    onConnectGlasses: () -> Unit,
    onDisconnectGlasses: () -> Unit,
    onEnableGlassesMic: () -> Unit,
    sim: SimControls,
) {
  val s = state.settings
  Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

    Section("Units") {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(s.unit == DistanceUnit.KILOMETRES, { onChange { it.copy(unit = DistanceUnit.KILOMETRES) } }, { Text("Kilometres") })
        FilterChip(s.unit == DistanceUnit.MILES, { onChange { it.copy(unit = DistanceUnit.MILES) } }, { Text("Miles") })
      }
      Hint("Altitude is always in feet, like pilots use.")
    }

    Section("Search radius") {
      var radius by remember(s.radiusKm) { mutableFloatStateOf(s.radiusKm.toFloat()) }
      Text(Units.distanceText(radius.toDouble(), s.unit))
      Slider(
          value = radius,
          onValueChange = { radius = it },
          onValueChangeFinished = { onChange { it.copy(radiusKm = radius.roundToInt()) } },
          valueRange = 10f..100f,
          steps = 8,
      )
    }

    Section("Flight data") {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(s.dataMode == DataMode.LIVE, { onChange { it.copy(dataMode = DataMode.LIVE) } }, { Text("Live") })
        FilterChip(s.dataMode == DataMode.SAMPLE, { onChange { it.copy(dataMode = DataMode.SAMPLE) } }, { Text("Sample (offline)") })
      }
      if (s.dataMode == DataMode.LIVE) {
        Hint(
            if (state.hasOpenSkyCredentials) "OpenSky account connected: updates every 10 seconds."
            else "Using OpenSky without an account: updates every 30 seconds, about 400 updates a day.")
      } else {
        Hint("Made-up flights for testing anywhere. Your location is set to the sample place.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          scenes.forEach { scene ->
            FilterChip(s.sampleScene == scene.id, { onChange { it.copy(sampleScene = scene.id) } }, { Text(scene.name) })
          }
        }
      }
    }

    Section("Meta glasses") {
      val g = state.glasses
      Text(glassesText(g), fontWeight = FontWeight.SemiBold)
      when (g) {
        GlassesStatus.NotRegistered, is GlassesStatus.Error ->
            Button(onClick = onConnectGlasses) { Text("Connect glasses") }
        is GlassesStatus.Connected -> {
          if (!g.listening) {
            Hint("Allow the glasses microphone so you can ask “what's that plane?” at any time.")
            Button(onClick = onEnableGlassesMic) { Text("Allow glasses microphone") }
          }
          OutlinedButton(onClick = onDisconnectGlasses) { Text("Disconnect") }
        }
        GlassesStatus.NoGlasses -> OutlinedButton(onClick = onDisconnectGlasses) { Text("Disconnect") }
        else -> Unit
      }
      Hint("Say “Hey Meta, start ${Brand.NAME}”, or with the microphone allowed, “What's that plane?”. Works with Ray-Ban Meta and Meta Ray-Ban Display.")
    }

    SimulatedGlassesSection(s, sim)

    Text(
        "${Brand.NAME} · prototype · not affiliated with Meta or Ray-Ban.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private fun glassesText(g: GlassesStatus) =
    when (g) {
      is GlassesStatus.Unavailable -> "Glasses unavailable: ${g.reason}"
      GlassesStatus.NotRegistered -> "Not connected"
      GlassesStatus.Registering -> "Connecting to Meta AI…"
      GlassesStatus.NoGlasses -> "Connected to Meta AI · glasses not nearby"
      GlassesStatus.Connecting -> "Connecting to glasses…"
      is GlassesStatus.Connected ->
          listOfNotNull(
                  "Connected" + (g.deviceName?.let { " to $it" } ?: ""),
                  if (g.headTracking) "head tracking on" else "no head tracking yet",
                  if (g.hasDisplay) "display card on" else null,
                  if (g.listening) "listening" else null,
              )
              .joinToString(" · ")
      is GlassesStatus.Error -> "Problem: ${g.message}"
    }

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      content()
    }
  }
}

@Composable
private fun Hint(text: String) {
  Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
