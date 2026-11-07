package com.metaglassar.planes.ui.pilot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.metaglassar.planes.MainViewModel
import com.metaglassar.planes.glasses.GlassesStatus
import com.metaglassar.planes.glasses.SimulatedModel
import com.metaglassar.planes.pilot.PilotViewModel

/** Pilot mode routes: "pilot" (disclaimer, then the pilot screen), "pilot/destination", "pilot/settings". */
fun NavGraphBuilder.pilotRoutes(
    nav: NavHostController,
    vm: MainViewModel,
    onRequestLocation: () -> Unit,
    onPilotStarted: () -> Unit,
) {
  composable("pilot") { entry ->
    val pvm: PilotViewModel = viewModel(entry)
    val state by pvm.state.collectAsStateWithLifecycle()
    val settings by pvm.settings.collectAsStateWithLifecycle()
    if (!settings.disclaimerAccepted) {
      PilotDisclaimerScreen(onAccept = { pvm.acceptDisclaimer() }, onCancel = { nav.popBackStack() })
      return@composable
    }
    LaunchedEffect(Unit) {
      pvm.start()
      onPilotStarted()
    }
    // Turned off from the notification or by voice: leave the screen. Only once this screen has seen
    // pilot mode running, so the first frames (before the state arrives) don't close it.
    var seenActive by remember { mutableStateOf(false) }
    LaunchedEffect(state.active) {
      if (state.active) seenActive = true else if (seenActive) nav.popBackStack("pilot", inclusive = true)
    }
    val main by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
      PilotScreen(
          state = state,
          settings = settings,
          onOff = {
            pvm.stop()
            nav.popBackStack("pilot", inclusive = true)
          },
          onPickDestination = { nav.navigate("pilot/destination") },
          onClearDestination = { pvm.setDestination(null) },
          onSettings = { nav.navigate("pilot/settings") },
          onRequestLocation = onRequestLocation,
      )
      if (main.settings.simulatedGlasses) {
        SimulatedGlassesPilotPanel(vm, main.settings.simulatedModel, main.glasses is GlassesStatus.Connected)
      }
    }
  }
  composable("pilot/destination") {
    val pvm: PilotViewModel = viewModel(remember(it) { nav.getBackStackEntry("pilot") })
    val results by pvm.results.collectAsStateWithLifecycle()
    DestinationPickerScreen(
        results = results,
        onSearch = pvm::search,
        onPick = { r ->
          pvm.setDestination(r.airfield)
          nav.popBackStack()
        },
        onBack = { nav.popBackStack() },
    )
  }
  composable("pilot/settings") {
    val pvm: PilotViewModel = viewModel(remember(it) { nav.getBackStackEntry("pilot") })
    val settings by pvm.settings.collectAsStateWithLifecycle()
    PilotSettingsScreen(
        settings = settings,
        scenarios = pvm.scenarios,
        onChange = pvm::update,
        onShowDisclaimer = {
          pvm.stop()
          pvm.update { s -> s.copy(disclaimerAccepted = false) }
          nav.popBackStack("pilot", inclusive = true)
        },
        onBack = { nav.popBackStack() },
    )
  }
}

/** Testing without hardware: what the simulated Ray-Ban Display shows, and the voice "off". */
@Composable
private fun SimulatedGlassesPilotPanel(vm: MainViewModel, model: SimulatedModel, connected: Boolean) {
  Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text("Simulated glasses", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    OutlinedButton(onClick = vm::simPilotModeOff, modifier = Modifier.fillMaxWidth()) { Text("Say: “Pilot mode off”") }
    OutlinedButton(onClick = vm::simSayWhatsThatPlane, modifier = Modifier.fillMaxWidth()) { Text("Ask the glasses (pilot summary)") }
    if (model == SimulatedModel.RAY_BAN_DISPLAY) {
      Text("What the Display shows", style = MaterialTheme.typography.labelMedium)
      key(connected) {
        AndroidView(
            factory = { ctx -> vm.simDisplayPreview(ctx) ?: android.widget.TextView(ctx).apply { text = "Connecting simulated glasses…" } },
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
      }
    }
  }
}
