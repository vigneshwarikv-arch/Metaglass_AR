package com.metaglassar.planes.ui.pilot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metaglassar.planes.PilotSettings
import com.metaglassar.planes.core.Brand
import com.metaglassar.planes.core.CompassPoint
import com.metaglassar.planes.core.pilot.AltitudeDatum
import com.metaglassar.planes.core.pilot.NavUnits
import com.metaglassar.planes.core.pilot.PilotText
import com.metaglassar.planes.core.pilot.PositionSource
import com.metaglassar.planes.core.pilot.TrafficRules
import com.metaglassar.planes.core.pilot.TrafficTarget
import com.metaglassar.planes.data.AirportDatabase
import com.metaglassar.planes.data.gdl90.Gdl90Scenario
import com.metaglassar.planes.pilot.DestinationResult
import com.metaglassar.planes.pilot.PhoneGps
import com.metaglassar.planes.pilot.PilotState
import com.metaglassar.planes.pilot.ReceiverMode
import com.metaglassar.planes.ui.Beacon
import kotlin.math.roundToInt

/*
 * Pilot mode screens. Safety rules (CLAUDE.md): supplementary aid only, VFR daytime; GPS altitude
 * always labelled GPS; nothing that looks like an altimeter or airspeed indicator; one-tap off.
 */

private val Off = Color(0xFFC62828)

// region disclaimer

/** Shown once; pilot mode only opens after the pilot accepts it. */
@Composable
fun PilotDisclaimerScreen(onAccept: () -> Unit, onCancel: () -> Unit) {
  var understood by rememberSaveable { mutableStateOf(false) }
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Text("Before you use pilot mode", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    listOf(
            "Pilot mode is a supplementary situational-awareness aid. It is NOT a certified or primary instrument and must not be used for navigation or collision avoidance.",
            "VFR and daytime only. Keep looking outside. You remain responsible for see-and-avoid and for following ATC, your charts and your aircraft's instruments.",
            "Traffic comes only from your portable ADS-B receiver. Many aircraft are not shown (no ADS-B Out, out of range, or filtered). No traffic shown does not mean no traffic.",
            "GPS altitude is labelled GPS. It is not your altimeter and can differ from it by hundreds of feet. There is no airspeed.",
            "The phone, receiver or glasses can fail, lag or show wrong data at any time.",
            "Turn pilot mode off with one tap on OFF, from the notification, or by saying “pilot mode off” to your glasses.",
        )
        .forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
    Row(Modifier.fillMaxWidth().clickable { understood = !understood }, verticalAlignment = Alignment.CenterVertically) {
      Checkbox(understood, { understood = it })
      Text("I understand and accept this.", fontWeight = FontWeight.SemiBold)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Button(onClick = onAccept, enabled = understood) { Text("Accept and open pilot mode") }
      OutlinedButton(onClick = onCancel) { Text("Cancel") }
    }
  }
}

// endregion

// region main screen

@Composable
fun PilotScreen(
    state: PilotState,
    settings: PilotSettings,
    onOff: () -> Unit,
    onPickDestination: () -> Unit,
    onClearDestination: () -> Unit,
    onSettings: () -> Unit,
    onRequestLocation: () -> Unit,
) {
  BackHandler(onBack = onOff)
  // The caller scrolls (the simulated-glasses panel can sit below this).
  Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    // Header: what this is, and the one-tap off.
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("PILOT MODE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text("Supplementary aid only · VFR · not certified", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      TextButton(onClick = onSettings) { Text("Settings") }
      Button(onClick = onOff, colors = ButtonDefaults.buttonColors(containerColor = Off, contentColor = Color.White)) {
        Text("OFF", fontWeight = FontWeight.Black)
      }
    }

    ReceiverStatusRow(state)
    if (state.phoneGps == PhoneGps.NO_PERMISSION && state.ownship == null) {
      OutlinedButton(onClick = onRequestLocation) { Text("Allow location for GPS readouts") }
    }

    Readouts(state)
    DirectToCard(state, onPickDestination, onClearDestination)
    NearestCard(state)
    TrafficCard(state, settings)

    Text(
        listOf("Not a certified instrument. GPS altitude is not an altimeter.", AirportDatabase.ATTRIBUTION).joinToString("  ·  "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun ReceiverStatusRow(state: PilotState) {
  val r = state.receiver
  val (dot, text) =
      when {
        state.receiverMode == ReceiverMode.OFF -> Color.Gray to "ADS-B receiver off"
        state.receiverError != null -> Off to "Receiver: ${state.receiverError}"
        !state.receiverConnected -> Beacon to (if (state.receiverMode == ReceiverMode.SIMULATED) "Simulated receiver: starting…" else "Receiver: no data — join its Wi-Fi")
        else -> {
          val name = (if (state.receiverMode == ReceiverMode.SIMULATED) "Simulated receiver" else r.deviceName ?: "Receiver")
          Color(0xFF2E7D32) to "$name · connected · ${if (r.gpsValid) "receiver GPS OK" else "receiver has no GPS fix"}"
        }
      }
  val gpsFrom =
      when (state.ownship?.source) {
        PositionSource.RECEIVER -> "Position from receiver GPS"
        PositionSource.PHONE -> "Position from phone GPS"
        null -> if (state.phoneGps == PhoneGps.NO_PERMISSION) "No position: location not allowed" else "Waiting for GPS…"
      }
  Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(10.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(10.dp).background(dot, RoundedCornerShape(5.dp)))
      Spacer(Modifier.width(8.dp))
      Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
    Text(gpsFrom, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun Readouts(state: PilotState) {
  val own = state.ownship
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Readout("GS", own?.groundspeedKt?.roundToInt()?.toString() ?: PilotText.DASH, "kt", Modifier.weight(1f))
    Readout("TRK", own?.trackDeg?.let { NavUnits.degreesText(it) } ?: PilotText.DASH, "° true", Modifier.weight(1f))
  }
  val altText = PilotText.gpsAltitude(own?.gpsAltitudeFt).removeSuffix(" ft GPS")
  Readout("GPS ALT", altText, "ft GPS", Modifier.fillMaxWidth())
  own?.let {
    Text(
        if (it.gpsAltitudeDatum == AltitudeDatum.ELLIPSOID)
            "GPS height above the WGS-84 ellipsoid: can differ from MSL by 100 ft or more. Not your altimeter."
        else "GPS altitude (MSL). Not your altimeter.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** A plain number tile. Deliberately not a dial or tape, so it can't be mistaken for an instrument. */
@Composable
private fun Readout(label: String, value: String, unit: String, modifier: Modifier) {
  Column(modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
    Row(verticalAlignment = Alignment.Bottom) {
      // Tabular digits keep numbers from jumping around as they change.
      Text(value, style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
      Spacer(Modifier.width(6.dp))
      Text(unit, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
    }
  }
}

@Composable
private fun DirectToCard(state: PilotState, onPick: () -> Unit, onClear: () -> Unit) {
  PilotCard {
    val d = state.directTo
    val dest = state.destination
    if (dest == null) {
      Text("Direct-to", fontWeight = FontWeight.Bold)
      Text("Pick your destination airport before takeoff.", style = MaterialTheme.typography.bodySmall)
      Button(onClick = onPick) { Text("Set destination") }
      return@PilotCard
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      // Arrow relative to our track (track-up); north-up bearing when we have no track.
      DirectArrow(degrees = d?.guidance?.turnDeg ?: d?.guidance?.bearingDeg, trackUp = d?.guidance?.turnDeg != null)
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text("→ ${dest.ident}", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(dest.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        if (d != null) {
          Text(
              "${TrafficRules.distanceText(d.guidance.distanceNm)} · ${PilotText.ete(d.guidance.eteMin)} · BRG ${NavUnits.degreesText(d.guidance.bearingDeg)}°",
              fontWeight = FontWeight.SemiBold)
          d.guidance.turnDeg?.let { t ->
            val n = kotlin.math.abs(t).roundToInt()
            Text(if (n < 3) "On course" else "Turn ${if (t > 0) "right" else "left"} $n°", style = MaterialTheme.typography.bodySmall)
          }
        } else {
          Text("Waiting for GPS…", style = MaterialTheme.typography.bodySmall)
        }
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(onClick = onPick) { Text("Change") }
      TextButton(onClick = onClear) { Text("Clear") }
    }
  }
}

@Composable
private fun DirectArrow(degrees: Double?, trackUp: Boolean) {
  val color = MaterialTheme.colorScheme.primary
  val ring = MaterialTheme.colorScheme.outline
  Box(contentAlignment = Alignment.TopCenter) {
    Canvas(Modifier.size(72.dp)) {
      drawCircle(ring, radius = size.minDimension / 2 - 2f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
      if (degrees != null) {
        rotate(degrees.toFloat()) {
          val w = size.width
          val h = size.height
          val path = Path().apply {
            moveTo(w / 2, h * 0.12f)
            lineTo(w * 0.72f, h * 0.5f)
            lineTo(w * 0.56f, h * 0.5f)
            lineTo(w * 0.56f, h * 0.84f)
            lineTo(w * 0.44f, h * 0.84f)
            lineTo(w * 0.44f, h * 0.5f)
            lineTo(w * 0.28f, h * 0.5f)
            close()
          }
          drawPath(path, color)
        }
      } else {
        drawCircle(ring, radius = 6f, center = Offset(size.width / 2, size.height / 2))
      }
    }
    if (!trackUp && degrees != null) Text("N↑", style = MaterialTheme.typography.labelSmall)
  }
}

@Composable
private fun NearestCard(state: PilotState) {
  PilotCard {
    Text("Nearest airport", fontWeight = FontWeight.Bold)
    val n = state.nearest
    when {
      n != null -> {
        Text(
            "${n.airfield.ident}  ${TrafficRules.distanceText(n.distanceNm)} ${CompassPoint.of(n.bearingDeg).short} (${NavUnits.degreesText(n.bearingDeg)}°)",
            fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(listOfNotNull(n.airfield.name, n.airfield.city).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
      }
      !state.receiverConnected -> Text("Connect an ADS-B receiver to show the nearest airport and traffic.", style = MaterialTheme.typography.bodySmall)
      else -> Text("Waiting for GPS…", style = MaterialTheme.typography.bodySmall)
    }
  }
}

@Composable
private fun TrafficCard(state: PilotState, settings: PilotSettings) {
  PilotCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("Traffic", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
      Text(
          "within ${settings.calloutRangeNm.roundToInt()} NM, ±${settings.calloutAltitudeBandFt} ft · voice ${if (settings.voiceCallouts) "on" else "off"}",
          style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (!state.receiverConnected) {
      Text("No receiver data, so no traffic is shown. Look outside.", style = MaterialTheme.typography.bodySmall)
      return@PilotCard
    }
    state.lastCallout?.let {
      Text("Last callout: “${it.spoken}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (state.traffic.isEmpty()) {
      Text("No traffic shown nearby. That does not mean there is none.", style = MaterialTheme.typography.bodySmall)
    }
    state.traffic.take(5).forEachIndexed { i, t ->
      if (i > 0) HorizontalDivider()
      TrafficRow(t, highlight = i == 0)
    }
  }
}

@Composable
private fun TrafficRow(t: TrafficTarget, highlight: Boolean) {
  Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(
        TrafficRules.short(t),
        Modifier.weight(1f),
        fontSize = if (highlight) 20.sp else 16.sp,
        fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
        color = if (highlight && t.converging) Beacon else MaterialTheme.colorScheme.onSurface,
    )
    Text(t.aircraft.callsign ?: t.aircraft.icao24.uppercase(), style = MaterialTheme.typography.labelMedium)
  }
}

@Composable
private fun PilotCard(content: @Composable () -> Unit) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
  }
}

// endregion

// region destination picker

@Composable
fun DestinationPickerScreen(
    results: List<DestinationResult>,
    onSearch: (String) -> Unit,
    onPick: (DestinationResult) -> Unit,
    onBack: () -> Unit,
) {
  var query by rememberSaveable { mutableStateOf("") }
  LaunchedEffect(query) { onSearch(query) }
  Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("Destination", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
      TextButton(onClick = onBack) { Text("Cancel") }
    }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("ICAO, FAA, IATA or name (e.g. KPAO, PAO, Palo Alto)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        modifier = Modifier.fillMaxWidth(),
    )
    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      items(results, key = { it.airfield.ident + it.airfield.country }) { r ->
        Column(Modifier.fillMaxWidth().clickable { onPick(r) }.padding(vertical = 8.dp)) {
          Row {
            Text(r.airfield.ident, fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp))
            Text(r.airfield.name, Modifier.weight(1f), maxLines = 1)
            r.distanceNm?.let { Text(TrafficRules.distanceText(it), style = MaterialTheme.typography.labelMedium) }
          }
          Text(
              listOfNotNull(r.airfield.city, r.airfield.country, r.airfield.iata?.let { "IATA $it" }).joinToString(" · "),
              style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
      }
    }
    Text(AirportDatabase.ATTRIBUTION, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

// endregion

// region settings

@Composable
fun PilotSettingsScreen(
    settings: PilotSettings,
    scenarios: List<Gdl90Scenario>,
    onChange: ((PilotSettings) -> PilotSettings) -> Unit,
    onShowDisclaimer: () -> Unit,
    onBack: () -> Unit,
) {
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("Pilot settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
      TextButton(onClick = onBack) { Text("Done") }
    }

    SettingsCard("ADS-B receiver (Wi-Fi)") {
      SwitchRow("Listen for a receiver", settings.receiverEnabled) { on -> onChange { it.copy(receiverEnabled = on) } }
      Hint("Stratux or Stratus 4: join the receiver's Wi-Fi network, then come back here. The app listens for GDL 90 on UDP port 4000.")
    }

    SettingsCard("Simulated receiver") {
      SwitchRow("Use simulated receiver", settings.simulatedReceiver) { on -> onChange { it.copy(simulatedReceiver = on) } }
      Hint("For testing on the ground: plays scripted traffic through the same decoder. Turn it off before flight.")
      if (settings.simulatedReceiver) {
        scenarios.forEach { sc ->
          FilterChip(settings.simulatedScenario == sc.id, { onChange { it.copy(simulatedScenario = sc.id) } }, { Text(sc.name) })
        }
      }
    }

    SettingsCard("Traffic callouts") {
      SwitchRow("Speak callouts", settings.voiceCallouts) { on -> onChange { it.copy(voiceCallouts = on) } }
      var range by remember(settings.calloutRangeNm) { mutableFloatStateOf(settings.calloutRangeNm.toFloat()) }
      Text("Range: ${range.roundToInt()} NM")
      Slider(range, { range = it }, onValueChangeFinished = { onChange { it.copy(calloutRangeNm = range.roundToInt().toDouble()) } }, valueRange = 2f..10f, steps = 7)
      var band by remember(settings.calloutAltitudeBandFt) { mutableFloatStateOf(settings.calloutAltitudeBandFt.toFloat()) }
      Text("Altitude band: ±${(band / 500).roundToInt() * 500} ft")
      Slider(band, { band = it }, onValueChangeFinished = { onChange { it.copy(calloutAltitudeBandFt = (band / 500).roundToInt() * 500) } }, valueRange = 500f..3000f, steps = 4)
      Hint("One callout at a time, closest or converging first. The same aircraft is repeated only when it gets meaningfully closer.")
    }

    SettingsCard("Safety") {
      Hint("Supplementary aid only. VFR, daytime. GPS altitude is not an altimeter. Say “pilot mode off” to your glasses or tap OFF.")
      OutlinedButton(onClick = onShowDisclaimer) { Text("Show the disclaimer again") }
    }

    Text("${Brand.NAME} pilot mode · beta", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
  }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      content()
    }
  }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(label, Modifier.weight(1f))
    Switch(checked, onChange)
  }
}

@Composable
private fun Hint(text: String) {
  Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// endregion
