package com.metaglassar.planes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metaglassar.planes.FeedState
import com.metaglassar.planes.LocationState
import com.metaglassar.planes.UiState
import com.metaglassar.planes.core.CompassPoint
import com.metaglassar.planes.core.DistanceUnit
import com.metaglassar.planes.core.SeenAircraft
import com.metaglassar.planes.core.Units
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun NearbyScreen(
    state: UiState,
    onOpen: (String) -> Unit,
    onRequestLocation: () -> Unit,
    onUseSample: () -> Unit,
    onRetry: () -> Unit,
) {
  when (val loc = state.location) {
    LocationState.NeedsPermission ->
        return EmptyState(
            "Allow location to see planes near you",
            "Your location is only used on this phone to work out which planes are around you.",
            primary = "Allow location" to onRequestLocation,
            secondary = "Try sample data" to onUseSample,
        )
    LocationState.Disabled ->
        return EmptyState(
            "Location is turned off",
            "Turn on location in your phone's quick settings, then come back.",
            primary = "Try again" to onRetry,
            secondary = "Try sample data" to onUseSample,
        )
    LocationState.Searching -> return Loading("Finding your location…")
    is LocationState.Known -> Unit
  }

  val feed = state.feed
  if (state.nearby.isEmpty()) {
    when (feed) {
      FeedState.Loading -> return Loading("Looking for planes…")
      is FeedState.Error ->
          return EmptyState(
              "Can't load planes",
              feed.reason.message(),
              primary = "Try again" to onRetry,
              secondary = "Try sample data" to onUseSample,
          )
      else ->
          return Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
              EmptyState(
                  "No planes within ${Units.distanceText(state.settings.radiusKm.toDouble(), state.settings.unit)}",
                  "The sky is quiet right now. We'll keep checking.",
              )
            }
            Attribution(state.attributions)
          }
    }
  }

  LazyColumn(Modifier.fillMaxSize()) {
    item { FeedBanner(feed) }
    item {
      RadarMap(
          nearby = state.nearby,
          radiusKm = state.settings.radiusKm.toDouble(),
          headingDeg = null,
          unit = state.settings.unit,
          onTap = onOpen,
          modifier = Modifier.fillMaxWidth().padding(16.dp),
      )
    }
    item {
      Text(
          "${state.nearby.size} planes · nearest first",
          Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    items(state.nearby, key = { it.aircraft.icao24 }) { seen ->
      AircraftRow(seen, state, onClick = { onOpen(seen.aircraft.icao24) })
      HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
    item { Attribution(state.attributions) }
  }
}

@Composable
private fun Loading(text: String) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
      CircularProgressIndicator()
      Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
fun AircraftRow(seen: SeenAircraft, state: UiState, onClick: () -> Unit) {
  val a = seen.aircraft
  val unit = state.settings.unit
  Row(
      Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(a.titleText(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
      Text(a.subtitleText(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(
          listOfNotNull(
                  if (a.onGround) "On the ground" else Units.altitudeText(a.altitudeM),
                  Units.speedText(a.speedMs, unit).takeIf { !a.onGround },
              )
              .joinToString(" · "),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Column(horizontalAlignment = Alignment.End) {
      Text(Units.distanceText(seen.look.groundDistanceKm, unit), style = MaterialTheme.typography.titleSmall)
      Text(CompassPoint.of(seen.look.bearingDeg).short, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
  }
}

/**
 * Simple north-up map: you in the centre, rings at half and full radius, each plane drawn where it
 * is and pointing where it's heading. Tap a plane to open its details.
 */
@Composable
fun RadarMap(
    nearby: List<SeenAircraft>,
    radiusKm: Double,
    headingDeg: Double?,
    unit: DistanceUnit,
    onTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    highlight: String? = null,
) {
  val colors = MaterialTheme.colorScheme
  val measurer = rememberTextMeasurer()
  val labelStyle = TextStyle(fontSize = 10.sp, color = colors.onSurfaceVariant)
  Canvas(
      modifier
          .aspectRatio(1f)
          .semantics { contentDescription = "Map of ${nearby.size} planes around you, north up" }
          .pointerInput(nearby, radiusKm) {
            detectTapGestures { tap ->
              val r = size.width / 2f
              val c = Offset(r, r)
              nearby
                  .map { it to position(it, radiusKm, r * 0.92f, c) }
                  .minByOrNull { (_, p) -> hypot(p.x - tap.x, p.y - tap.y) }
                  ?.takeIf { (_, p) -> hypot(p.x - tap.x, p.y - tap.y) < 32.dp.toPx() }
                  ?.let { (seen, _) -> onTap(seen.aircraft.icao24) }
            }
          }
  ) {
    val r = size.minDimension / 2f
    val c = Offset(size.width / 2f, size.height / 2f)
    val outer = r * 0.92f
    drawCircle(colors.surface, radius = outer, center = c)
    drawCircle(colors.outline, radius = outer, center = c, style = Stroke(1.dp.toPx()))
    drawCircle(colors.outline, radius = outer / 2, center = c, style = Stroke(1.dp.toPx()))
    drawText(measurer, "N", Offset(c.x - 4.dp.toPx(), c.y - outer + 4.dp.toPx()), TextStyle(fontSize = 12.sp, color = colors.primary, fontWeight = FontWeight.Bold))
    drawText(measurer, Units.distanceText(radiusKm / 2, unit), Offset(c.x + 4.dp.toPx(), c.y + outer / 2 + 2.dp.toPx()), labelStyle)

    // Phone heading wedge (Point screen).
    headingDeg?.let { h ->
      rotate(h.toFloat(), c) {
        val wedge = Path().apply {
          moveTo(c.x, c.y)
          lineTo(c.x + outer * sin(Math.toRadians(-12.0)).toFloat(), c.y - outer * cos(Math.toRadians(12.0)).toFloat())
          lineTo(c.x + outer * sin(Math.toRadians(12.0)).toFloat(), c.y - outer * cos(Math.toRadians(12.0)).toFloat())
          close()
        }
        drawPath(wedge, colors.primary.copy(alpha = 0.15f))
      }
    }

    drawCircle(colors.primary, radius = 5.dp.toPx(), center = c)

    nearby.forEach { seen ->
      val p = position(seen, radiusKm, outer, c)
      val isHighlight = seen.aircraft.icao24 == highlight
      val color = when {
        isHighlight -> Beacon
        seen.aircraft.onGround -> colors.onSurfaceVariant
        else -> colors.onSurface
      }
      rotate((seen.aircraft.trackDeg ?: 0.0).toFloat(), p) {
        val s = (if (isHighlight) 9 else 7).dp.toPx()
        val plane = Path().apply {
          moveTo(p.x, p.y - s)
          lineTo(p.x + s * 0.7f, p.y + s * 0.7f)
          lineTo(p.x, p.y + s * 0.3f)
          lineTo(p.x - s * 0.7f, p.y + s * 0.7f)
          close()
        }
        drawPath(plane, color)
      }
      seen.aircraft.callsign?.let {
        drawText(measurer, it, Offset(p.x + 9.dp.toPx(), p.y - 6.dp.toPx()), labelStyle.copy(color = if (isHighlight) Beacon else labelStyle.color))
      }
    }
  }
}

private fun position(seen: SeenAircraft, radiusKm: Double, outerPx: Float, c: Offset): Offset {
  val frac = (seen.look.groundDistanceKm / radiusKm).coerceAtMost(1.0).toFloat()
  val b = Math.toRadians(seen.look.bearingDeg)
  return Offset(c.x + outerPx * frac * sin(b).toFloat(), c.y - outerPx * frac * cos(b).toFloat())
}
