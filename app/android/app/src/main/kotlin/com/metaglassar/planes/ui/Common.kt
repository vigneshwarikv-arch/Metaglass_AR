package com.metaglassar.planes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.metaglassar.planes.FeedState
import com.metaglassar.planes.core.Aircraft
import com.metaglassar.planes.core.AircraftTypes
import com.metaglassar.planes.core.Answer
import com.metaglassar.planes.core.FailureReason

/** "IndiGo" / "Flight AIC540" / "Aircraft 80139E". */
fun Aircraft.titleText(): String = info.airline ?: callsign?.let { "Flight $it" } ?: "Aircraft ${icao24.uppercase()}"

/** "AIC540 · A320neo · DEL → MAA". */
fun Aircraft.subtitleText(): String =
    listOfNotNull(
            callsign.takeIf { info.airline != null },
            AircraftTypes.spokenName(info),
            info.route?.let { r -> "${r.origin?.code ?: "?"} → ${r.destination?.code ?: "?"}" },
        )
        .joinToString(" · ")
        .ifEmpty { "Type and route unknown" }

fun FailureReason.message(): String =
    when (this) {
      FailureReason.NoInternet -> "You're offline. Check your connection."
      is FailureReason.RateLimited ->
          "Live data limit reached for now" + (retryAfterSec?.let { " — try again in ${formatWait(it)}." } ?: ".")
      is FailureReason.Http -> "Flight data service error ($code)."
      is FailureReason.Unauthorized -> "OpenSky login failed. Check the credentials in local.properties."
      is FailureReason.Other -> message
    }

private fun formatWait(sec: Long) = if (sec < 120) "$sec s" else "${sec / 60} min"

@Composable
fun FeedBanner(feed: FeedState) {
  val text =
      when (feed) {
        is FeedState.Stale -> feed.reason.message() + " Showing the last data."
        else -> return
      }
  Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
    Text(text, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
  }
}

@Composable
fun Attribution(lines: List<String>, modifier: Modifier = Modifier) {
  if (lines.isEmpty()) return
  Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
    lines.forEach {
      Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

/** Centered message with optional buttons, for empty and error states. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    primary: Pair<String, () -> Unit>? = null,
    secondary: Pair<String, () -> Unit>? = null,
) {
  Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
      Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        primary?.let { (label, action) -> Button(onClick = action) { Text(label) } }
        secondary?.let { (label, action) -> OutlinedButton(onClick = action) { Text(label) } }
      }
    }
  }
}

/** The last spoken answer, shown at the top of every screen. */
@Composable
fun AnswerBanner(answer: Answer, onDismiss: () -> Unit) {
  Row(
      Modifier.fillMaxWidth()
          .padding(12.dp)
          .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
          .padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        answer.spoken,
        Modifier.weight(1f),
        color = MaterialTheme.colorScheme.onPrimary,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.width(4.dp))
    TextButton(onClick = onDismiss) { Text("OK", color = MaterialTheme.colorScheme.onPrimary) }
  }
}
