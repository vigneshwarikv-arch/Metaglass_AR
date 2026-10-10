package com.metaglassar.planes.ui

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.metaglassar.planes.Settings
import com.metaglassar.planes.core.Brand
import com.metaglassar.planes.core.CompassPoint
import com.metaglassar.planes.core.Pointing
import com.metaglassar.planes.glasses.SimulatedModel
import kotlin.math.roundToInt

/** Callbacks for the simulated-glasses controls (see MainViewModel "Simulated glasses"). */
class SimControls(
    val head: Pointing,
    /** True once the app's glasses connector is connected to the simulated pair. */
    val connected: Boolean,
    val onToggle: (Boolean) -> Unit,
    val onModel: (SimulatedModel) -> Unit,
    val onHead: (Pointing) -> Unit,
    val onLookAtNearest: () -> Unit,
    val onSayWhatsThatPlane: () -> Unit,
    val onVoiceLaunch: () -> Unit,
    val displayPreview: (Context) -> View?,
)

/**
 * Testing without hardware: DAT MockDeviceKit pretends to be connected Meta glasses. The real
 * glasses code path runs end to end; only the hardware is simulated.
 */
@Composable
fun SimulatedGlassesSection(s: Settings, sim: SimControls) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text("Simulated glasses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text(
              "For testing without real glasses. Uses Meta's MockDeviceKit.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(checked = s.simulatedGlasses, onCheckedChange = sim.onToggle)
      }
      if (!s.simulatedGlasses) return@Column

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SimulatedModel.entries.forEach { m ->
          FilterChip(s.simulatedModel == m, { sim.onModel(m) }, { Text(m.label) })
        }
      }

      val head = sim.head
      Text(
          "Head facing ${head.bearingDeg.roundToInt()}° ${CompassPoint.of(head.bearingDeg).short} · ${head.elevationDeg.roundToInt()}° up",
          fontWeight = FontWeight.SemiBold,
      )
      Text("Turn head", style = MaterialTheme.typography.labelMedium)
      Slider(value = head.bearingDeg.toFloat(), onValueChange = { sim.onHead(head.copy(bearingDeg = it.toDouble())) }, valueRange = 0f..359f)
      Text("Look up", style = MaterialTheme.typography.labelMedium)
      Slider(value = head.elevationDeg.toFloat(), onValueChange = { sim.onHead(head.copy(elevationDeg = it.toDouble())) }, valueRange = -5f..85f)
      OutlinedButton(onClick = sim.onLookAtNearest, modifier = Modifier.fillMaxWidth()) { Text("Look at the nearest plane") }
      Button(onClick = sim.onSayWhatsThatPlane, modifier = Modifier.fillMaxWidth()) { Text("Say: “What's that plane?”") }
      OutlinedButton(onClick = sim.onVoiceLaunch, modifier = Modifier.fillMaxWidth()) { Text("Say: “Hey Meta, start ${Brand.NAME}”") }

      if (s.simulatedModel == SimulatedModel.RAY_BAN_DISPLAY) {
        Text("What the Display shows", style = MaterialTheme.typography.labelMedium)
        // Re-create the preview once the simulated glasses are connected (pairing takes a moment).
        key(sim.connected, s.simulatedModel) {
          AndroidView(
              factory = { ctx ->
                sim.displayPreview(ctx) ?: android.widget.TextView(ctx).apply { text = "Connecting simulated glasses…" }
              },
              modifier = Modifier.fillMaxWidth().aspectRatio(1f), // the Ray-Ban Display screen is square
          )
        }
      }
    }
  }
}
