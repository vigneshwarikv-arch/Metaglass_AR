package com.metaglassar.planes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metaglassar.planes.LocationState
import com.metaglassar.planes.PointUi
import com.metaglassar.planes.UiState
import com.metaglassar.planes.core.CompassPoint
import com.metaglassar.planes.core.Geo
import com.metaglassar.planes.core.Selection
import com.metaglassar.planes.core.TargetSelector
import com.metaglassar.planes.core.TurnDirection
import com.metaglassar.planes.core.Units
import com.metaglassar.planes.glasses.GlassesStatus
import kotlin.math.roundToInt

/** Field of view of the sky view, in degrees either side of where the phone points. */
private const val HALF_FOV_DEG = 35.0

@Composable
fun PointScreen(state: UiState, point: PointUi, onOpen: (String) -> Unit, onAsk: () -> Unit, onRequestLocation: () -> Unit) {
  if (state.location !is LocationState.Known) {
    return EmptyState(
        "Location needed",
        "Point to identify works out which plane is in front of you from where you are.",
        primary = "Allow location" to onRequestLocation,
    )
  }
  if (!state.compassAvailable) {
    return EmptyState("No compass on this phone", "Point to identify needs a compass. You can still use the Nearby list.")
  }

  Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("Hold your phone up and point the back of it at a plane.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

    val p = point.pointing
    SkyView(state, point, Modifier.fillMaxWidth())

    Text(
        p?.let { "Facing ${it.bearingDeg.roundToInt()}° ${CompassPoint.of(it.bearingDeg).short} · ${kotlin.math.abs(it.elevationDeg).roundToInt()}° ${if (it.elevationDeg >= 0) "up" else "down"}" }
            ?: "Reading compass…",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    if (point.compassUnreliable) {
      Text(
          "Compass needs calibrating: move your phone in a figure-of-eight a few times.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.secondary,
      )
    }

    when (val sel = point.selection) {
      is Selection.Hit -> ResultCard("That's", sel.target.aircraft.titleText(), sel.target, state) { onOpen(sel.target.aircraft.icao24) }
      is Selection.Miss -> {
        val turn =
            when (sel.turn) {
              TurnDirection.LEFT -> "Turn left about ${sel.turnDeg.roundToInt()}°"
              TurnDirection.RIGHT -> "Turn right about ${sel.turnDeg.roundToInt()}°"
              TurnDirection.UP -> "Tilt up about ${sel.turnDeg.roundToInt()}°"
              TurnDirection.DOWN -> "Tilt down about ${sel.turnDeg.roundToInt()}°"
            }
        ResultCard("No plane that way. $turn for the nearest:", sel.nearest.aircraft.titleText(), sel.nearest, state) {
          onOpen(sel.nearest.aircraft.icao24)
        }
      }
      Selection.NoAircraft -> Text("No planes in the sky near you right now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
      null -> Unit
    }

    val onGlasses = state.glasses is GlassesStatus.Connected
    Button(onClick = onAsk, modifier = Modifier.fillMaxWidth()) {
      Text(if (onGlasses) "Ask: answer on my glasses" else "Ask: say the answer")
    }
    Attribution(state.attributions)
  }
}

@Composable
private fun ResultCard(
    lead: String,
    title: String,
    seen: com.metaglassar.planes.core.SeenAircraft,
    state: UiState,
    onOpen: () -> Unit,
) {
  val unit = state.settings.unit
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(lead, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      Text(seen.aircraft.subtitleText(), style = MaterialTheme.typography.bodyMedium)
      Text(
          listOfNotNull(
                  Units.altitudeText(seen.aircraft.altitudeM),
                  "${Units.distanceText(seen.look.groundDistanceKm, unit)} ${CompassPoint.of(seen.look.bearingDeg).short}",
              )
              .joinToString(" · "),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      TextButton(onClick = onOpen) { Text("Plane details") }
    }
  }
}

/**
 * What's in front of the phone: crosshair in the middle, the pointing tolerance as a ring, and
 * each airborne plane placed by how far left/right and up/down it is from where you point.
 */
@Composable
private fun SkyView(state: UiState, point: PointUi, modifier: Modifier) {
  val colors = MaterialTheme.colorScheme
  val measurer = rememberTextMeasurer()
  val pointing = point.pointing
  val highlight = (point.selection as? Selection.Hit)?.target?.aircraft?.icao24
  Canvas(
      modifier
          .aspectRatio(1.3f)
          .clipToBounds()
          .semantics { contentDescription = "Sky view in the direction the phone points" }) {
    drawRoundRect(colors.surface, cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()))
    val c = Offset(size.width / 2, size.height / 2)
    val pxPerDeg = (size.width / 2) / HALF_FOV_DEG.toFloat()
    // Tolerance ring and crosshair.
    drawCircle(colors.primary.copy(alpha = 0.12f), radius = TargetSelector.DEFAULT_TOLERANCE_DEG.toFloat() * pxPerDeg, center = c)
    drawCircle(colors.primary, radius = TargetSelector.DEFAULT_TOLERANCE_DEG.toFloat() * pxPerDeg, center = c, style = Stroke(1.5.dp.toPx()))
    drawLine(colors.primary, Offset(c.x - 14.dp.toPx(), c.y), Offset(c.x + 14.dp.toPx(), c.y), 2.dp.toPx())
    drawLine(colors.primary, Offset(c.x, c.y - 14.dp.toPx()), Offset(c.x, c.y + 14.dp.toPx()), 2.dp.toPx())

    if (pointing == null) return@Canvas
    // Horizon line.
    val horizonY = c.y + pointing.elevationDeg.toFloat() * pxPerDeg
    if (horizonY in 0f..size.height) {
      drawLine(colors.outline, Offset(0f, horizonY), Offset(size.width, horizonY), 1.dp.toPx())
      drawText(measurer, "horizon", Offset(8.dp.toPx(), horizonY + 2.dp.toPx()), TextStyle(fontSize = 10.sp, color = colors.onSurfaceVariant))
    }
    state.nearby.filter { !it.aircraft.onGround }.forEach { seen ->
      val dx = Geo.signedBearingDiff(pointing.bearingDeg, seen.look.bearingDeg)
      val dy = seen.look.elevationDeg - pointing.elevationDeg
      if (kotlin.math.abs(dx) > HALF_FOV_DEG * 1.4 || kotlin.math.abs(dy) > HALF_FOV_DEG * 1.4) return@forEach
      val pos = Offset(c.x + dx.toFloat() * pxPerDeg, c.y - dy.toFloat() * pxPerDeg)
      val hit = seen.aircraft.icao24 == highlight
      drawCircle(if (hit) Beacon else colors.onSurface, radius = (if (hit) 9 else 6).dp.toPx(), center = pos)
      drawText(
          measurer,
          seen.aircraft.callsign ?: seen.aircraft.icao24.uppercase(),
          Offset(pos.x + 10.dp.toPx(), pos.y - 8.dp.toPx()),
          TextStyle(fontSize = 11.sp, color = if (hit) Beacon else colors.onSurfaceVariant, fontWeight = if (hit) FontWeight.Bold else FontWeight.Normal),
      )
    }
  }
}
