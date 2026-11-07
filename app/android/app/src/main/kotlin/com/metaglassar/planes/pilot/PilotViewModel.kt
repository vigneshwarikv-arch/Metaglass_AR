package com.metaglassar.planes.pilot

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.metaglassar.planes.PilotSettings
import com.metaglassar.planes.PlanesApp
import com.metaglassar.planes.core.Geo
import com.metaglassar.planes.core.pilot.Airfield
import com.metaglassar.planes.core.pilot.Airfields
import com.metaglassar.planes.core.pilot.NavUnits
import com.metaglassar.planes.data.gdl90.Gdl90Scenario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One search result for direct-to, with its distance from where we are (if known). */
data class DestinationResult(val airfield: Airfield, val distanceNm: Double?)

class PilotViewModel(app: Application) : AndroidViewModel(app) {
  private val graph = (app as PlanesApp).graph
  private val runtime = graph.pilot

  val state: StateFlow<PilotState> = runtime.state
  val settings: StateFlow<PilotSettings> =
      graph.settings.settings.map { it.pilot }.stateIn(viewModelScope, SharingStarted.Eagerly, graph.settings.settings.value.pilot)

  val scenarios: List<Gdl90Scenario> get() = runtime.scenarios

  private val _results = MutableStateFlow<List<DestinationResult>>(emptyList())
  val results: StateFlow<List<DestinationResult>> = _results.asStateFlow()

  fun acceptDisclaimer() = update { it.copy(disclaimerAccepted = true) }

  fun start() = runtime.start()

  fun stop() = runtime.stop()

  fun update(transform: (PilotSettings) -> PilotSettings) = graph.settings.update { it.copy(pilot = transform(it.pilot)) }

  fun search(query: String) {
    viewModelScope.launch {
      val here = state.value.ownship?.position
      _results.value =
          withContext(Dispatchers.Default) {
            Airfields.search(query, runtime.airports).map { a ->
              DestinationResult(a, here?.let { NavUnits.nm(Geo.distanceKm(it, a.position)) })
            }
          }
    }
  }

  fun setDestination(a: Airfield?) = runtime.setDestination(a)
}
