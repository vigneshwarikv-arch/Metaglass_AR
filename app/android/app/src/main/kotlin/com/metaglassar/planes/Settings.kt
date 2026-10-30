package com.metaglassar.planes

import android.content.Context
import com.metaglassar.planes.core.DistanceUnit
import com.metaglassar.planes.core.pilot.TrafficRules
import com.metaglassar.planes.data.SampleSource
import com.metaglassar.planes.glasses.SimulatedModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DataMode { LIVE, SAMPLE }

data class Settings(
    val unit: DistanceUnit = DistanceUnit.KILOMETRES,
    val radiusKm: Int = 50,
    val dataMode: DataMode = DataMode.LIVE,
    val sampleScene: String = SampleSource.CHENNAI,
    /** Testing: use DAT MockDeviceKit glasses instead of real ones. */
    val simulatedGlasses: Boolean = false,
    val simulatedModel: SimulatedModel = SimulatedModel.RAY_BAN_DISPLAY,
    val pilot: PilotSettings = PilotSettings(),
)

/** Pilot mode (MVP 2) settings. */
data class PilotSettings(
    /** The one-time safety disclaimer has been accepted. */
    val disclaimerAccepted: Boolean = false,
    /** Listen for a Wi-Fi ADS-B receiver (GDL 90 on UDP 4000). */
    val receiverEnabled: Boolean = true,
    /** Testing: play a scripted scenario instead of listening to a real receiver. */
    val simulatedReceiver: Boolean = false,
    val simulatedScenario: String = "converging",
    val calloutRangeNm: Double = TrafficRules.DEFAULT_RANGE_NM,
    val calloutAltitudeBandFt: Int = TrafficRules.DEFAULT_ALTITUDE_BAND_FT.toInt(),
    val voiceCallouts: Boolean = true,
    /** Direct-to destination (airport ident), kept across restarts. */
    val destination: String? = null,
)

/** User settings, saved in SharedPreferences. */
class SettingsStore(context: Context) {
  private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
  private val _settings = MutableStateFlow(load())
  val settings: StateFlow<Settings> = _settings.asStateFlow()

  fun update(transform: (Settings) -> Settings) {
    val s = transform(_settings.value)
    prefs.edit()
        .putString("unit", s.unit.name)
        .putInt("radiusKm", s.radiusKm)
        .putString("dataMode", s.dataMode.name)
        .putString("sampleScene", s.sampleScene)
        .putBoolean("simulatedGlasses", s.simulatedGlasses)
        .putString("simulatedModel", s.simulatedModel.name)
        .putBoolean("pilot.disclaimerAccepted", s.pilot.disclaimerAccepted)
        .putBoolean("pilot.receiverEnabled", s.pilot.receiverEnabled)
        .putBoolean("pilot.simulatedReceiver", s.pilot.simulatedReceiver)
        .putString("pilot.simulatedScenario", s.pilot.simulatedScenario)
        .putFloat("pilot.calloutRangeNm", s.pilot.calloutRangeNm.toFloat())
        .putInt("pilot.calloutAltitudeBandFt", s.pilot.calloutAltitudeBandFt)
        .putBoolean("pilot.voiceCallouts", s.pilot.voiceCallouts)
        .putString("pilot.destination", s.pilot.destination)
        .apply()
    _settings.value = s
  }

  private fun load() =
      Settings(
          unit = enumOr(prefs.getString("unit", null), DistanceUnit.KILOMETRES),
          radiusKm = prefs.getInt("radiusKm", 50),
          dataMode = enumOr(prefs.getString("dataMode", null), DataMode.LIVE),
          sampleScene = prefs.getString("sampleScene", null) ?: SampleSource.CHENNAI,
          simulatedGlasses = prefs.getBoolean("simulatedGlasses", false),
          simulatedModel = enumOr(prefs.getString("simulatedModel", null), SimulatedModel.RAY_BAN_DISPLAY),
          pilot =
              PilotSettings().let { d ->
                PilotSettings(
                    disclaimerAccepted = prefs.getBoolean("pilot.disclaimerAccepted", d.disclaimerAccepted),
                    receiverEnabled = prefs.getBoolean("pilot.receiverEnabled", d.receiverEnabled),
                    simulatedReceiver = prefs.getBoolean("pilot.simulatedReceiver", d.simulatedReceiver),
                    simulatedScenario = prefs.getString("pilot.simulatedScenario", null) ?: d.simulatedScenario,
                    calloutRangeNm = prefs.getFloat("pilot.calloutRangeNm", d.calloutRangeNm.toFloat()).toDouble(),
                    calloutAltitudeBandFt = prefs.getInt("pilot.calloutAltitudeBandFt", d.calloutAltitudeBandFt),
                    voiceCallouts = prefs.getBoolean("pilot.voiceCallouts", d.voiceCallouts),
                    destination = prefs.getString("pilot.destination", null),
                )
              },
      )

  private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
      name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
}
