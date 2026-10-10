package com.metaglassar.planes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.metaglassar.planes.core.Aircraft
import com.metaglassar.planes.core.AircraftInfoLookup
import com.metaglassar.planes.core.AircraftSource
import com.metaglassar.planes.core.Answer
import com.metaglassar.planes.core.AnswerBuilder
import com.metaglassar.planes.core.FailureReason
import com.metaglassar.planes.core.Geo
import com.metaglassar.planes.core.GeoPoint
import com.metaglassar.planes.core.Pointing
import com.metaglassar.planes.core.SeenAircraft
import com.metaglassar.planes.core.Selection
import com.metaglassar.planes.core.SourceResult
import com.metaglassar.planes.core.TargetSelector
import com.metaglassar.planes.data.SampleSource
import com.metaglassar.planes.data.enrich
import com.metaglassar.planes.glasses.AskTrigger
import com.metaglassar.planes.glasses.GlassesStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LocationState {
  data object NeedsPermission : LocationState
  data object Disabled : LocationState
  data object Searching : LocationState
  data class Known(val point: GeoPoint, val isSample: Boolean) : LocationState
}

sealed interface FeedState {
  data object Loading : FeedState
  data class Ok(val fetchedAtMs: Long) : FeedState
  /** Showing older data because the latest fetch failed. */
  data class Stale(val reason: FailureReason, val fetchedAtMs: Long) : FeedState
  data class Error(val reason: FailureReason) : FeedState
}

data class UiState(
    val settings: Settings = Settings(),
    val location: LocationState = LocationState.Searching,
    val nearby: List<SeenAircraft> = emptyList(),
    val feed: FeedState = FeedState.Loading,
    val attributions: List<String> = emptyList(),
    val compassAvailable: Boolean = true,
    val glasses: GlassesStatus = GlassesStatus.NotRegistered,
    val lastAnswer: Answer? = null,
    val hasOpenSkyCredentials: Boolean = false,
)

/**
 * Compass-driven state, kept apart from [UiState] because it changes many times a second:
 * only the Point screen reads it, so the Nearby list and map don't redraw on every sensor tick.
 */
data class PointUi(
    /** Phone pointing direction, true north. */
    val pointing: Pointing? = null,
    val compassUnreliable: Boolean = false,
    val selection: Selection? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
  private val graph = (app as PlanesApp).graph
  private val _state =
      MutableStateFlow(
          UiState(
              settings = graph.settings.settings.value,
              hasOpenSkyCredentials = graph.openSkyCredentials != null,
              compassAvailable = graph.orientation.available,
          ))
  val state: StateFlow<UiState> = _state.asStateFlow()
  private val _point = MutableStateFlow(PointUi())
  val point: StateFlow<PointUi> = _point.asStateFlow()
  val sampleScenes = graph.sampleScenes

  private val locationPermission = MutableStateFlow(false)
  private var visibleJob: Job? = null
  private val refreshNow = MutableStateFlow(0)
  private var allAircraft: List<Aircraft> = emptyList()

  init {
    viewModelScope.launch { graph.settings.settings.collect { s -> _state.update { it.copy(settings = s) } } }
    viewModelScope.launch { graph.glasses.status.collect { g -> _state.update { it.copy(glasses = g) } } }
    viewModelScope.launch { graph.glasses.askRequests.collect { trigger -> ask(trigger) } }
  }

  fun onLocationPermission(granted: Boolean) {
    locationPermission.value = granted
  }

  fun updateSettings(transform: (Settings) -> Settings) = graph.settings.update(transform)

  fun retry() {
    refreshNow.value++
  }

  /** Start data refresh and sensors while the UI is visible; stop when it isn't (saves quota and battery). */
  fun setVisible(visible: Boolean) {
    visibleJob?.cancel()
    visibleJob = null
    if (!visible) return
    visibleJob = viewModelScope.launch {
      launch { runLocationAndFeed() }
      launch { runOrientation() }
    }
  }

  private suspend fun runLocationAndFeed() {
    val settingsFlow = graph.settings.settings
    combine(settingsFlow.map { it.dataMode to it.sampleScene }.distinctUntilChanged(), locationPermission) { mode, perm ->
          mode to perm
        }
        .collectLatest { (mode, permitted) ->
          val (dataMode, sceneId) = mode
          if (dataMode == DataMode.SAMPLE) {
            val sample = graph.sampleSource(sceneId)
            val point = sample.scene.center
            setLocation(LocationState.Known(point, isSample = true))
            feedLoop(flowOf(point), sample, sample)
          } else if (!permitted) {
            setLocation(LocationState.NeedsPermission)
            clearAircraft()
          } else if (!graph.location.anyProviderEnabled()) {
            setLocation(LocationState.Disabled)
            clearAircraft()
          } else {
            setLocation(LocationState.Searching)
            val locations = graph.location.updates()
            feedLoop(locations, graph.liveSource, graph.liveLookup)
          }
        }
  }

  private suspend fun feedLoop(locations: Flow<GeoPoint>, source: AircraftSource, lookup: AircraftInfoLookup) {
    _state.update { it.copy(feed = FeedState.Loading) }
    combine(locations, graph.settings.settings.map { it.radiusKm }.distinctUntilChanged(), refreshNow) { p, r, _ -> p to r }
        .collectLatest { (point, radiusKm) ->
          setLocation(LocationState.Known(point, isSample = source is SampleSource))
          while (true) {
            fetchOnce(point, radiusKm.toDouble(), source, lookup)
            delay(source.minRefreshIntervalMs)
          }
        }
  }

  private suspend fun fetchOnce(point: GeoPoint, radiusKm: Double, source: AircraftSource, lookup: AircraftInfoLookup) {
    when (val result = source.aircraftNear(point, radiusKm)) {
      is SourceResult.Success -> {
        val near = TargetSelector.nearby(point, result.aircraft, radiusKm)
        // Show positions straight away, then fill in airline/route for the nearest ones.
        publish(point, near.map { it.aircraft }, radiusKm, result, lookup)
        val enriched = enrich(near.map { it.aircraft }, lookup, limit = 15)
        publish(point, enriched, radiusKm, result, lookup)
      }
      is SourceResult.Failure -> _state.update { it.copy(feed = FeedState.Error(result.reason)) }
    }
  }

  private fun publish(point: GeoPoint, aircraft: List<Aircraft>, radiusKm: Double, result: SourceResult.Success, lookup: AircraftInfoLookup) {
    allAircraft = aircraft
    val feed = result.staleBecause?.let { FeedState.Stale(it, result.fetchedAtMs) } ?: FeedState.Ok(result.fetchedAtMs)
    _state.update {
      it.copy(
          nearby = TargetSelector.nearby(point, aircraft, radiusKm),
          feed = feed,
          attributions = listOfNotNull(result.attribution, lookup.attribution).distinct(),
      )
    }
    _point.update { it.copy(selection = it.pointing?.let { p -> TargetSelector.select(point, aircraft, p) }) }
  }

  private suspend fun runOrientation() {
    graph.orientation.updates().collect { reading ->
      val user = (state.value.location as? LocationState.Known)?.point
      val trueBearing = Geo.normalizeDeg(reading.magneticPointing.bearingDeg + (user?.let(::declinationAt) ?: 0.0))
      val pointing = reading.magneticPointing.copy(bearingDeg = trueBearing)
      _point.value =
          PointUi(
              pointing = pointing,
              compassUnreliable = reading.accuracy <= android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_LOW,
              selection = user?.let { u -> TargetSelector.select(u, allAircraft, pointing) },
          )
    }
  }

  private fun setLocation(l: LocationState) {
    if (l is LocationState.Known) graph.lastUserPosition = l.point
    _state.update { it.copy(location = l) }
  }

  private fun clearAircraft() {
    allAircraft = emptyList()
    _state.update { it.copy(nearby = emptyList(), attributions = emptyList()) }
    _point.update { it.copy(selection = null) }
  }

  fun aircraft(icao24: String): SeenAircraft? = state.value.nearby.firstOrNull { it.aircraft.icao24 == icao24 }

  /**
   * "What's that plane?": picks the aircraft in the direction of the glasses (head) or, without
   * head tracking, the phone; then speaks the answer on the glasses (or the phone) and shows it.
   */
  fun ask(trigger: AskTrigger = AskTrigger.PHONE_BUTTON) {
    viewModelScope.launch {
      val s = state.value
      val user = (s.location as? LocationState.Known)?.point
      val answer =
          if (user == null) {
            Answer("I don't know where you are yet. Turn on location and try again.", null)
          } else {
            val pointing = graph.glasses.headPointing.value ?: point.value.pointing
            val selection =
                if (pointing != null) TargetSelector.select(user, allAircraft, pointing)
                else nearestAirborne(user)
            AnswerBuilder.answer(selection, s.settings.unit)
          }
      _state.update { it.copy(lastAnswer = answer) }
      if (s.glasses is GlassesStatus.Connected) graph.glasses.deliver(answer) else graph.phoneVoice.speak(answer.spoken)
    }
  }

  private fun nearestAirborne(user: GeoPoint): Selection =
      TargetSelector.nearby(user, allAircraft.filter { !it.onGround }, Double.MAX_VALUE).firstOrNull()?.let { Selection.Hit(it, 0.0) }
          ?: Selection.NoAircraft

  fun dismissAnswer() = _state.update { it.copy(lastAnswer = null) }
}
