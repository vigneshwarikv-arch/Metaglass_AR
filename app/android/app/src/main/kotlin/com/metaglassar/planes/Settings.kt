package com.metaglassar.planes

import android.content.Context
import com.metaglassar.planes.core.DistanceUnit
import com.metaglassar.planes.data.SampleSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DataMode { LIVE, SAMPLE }

data class Settings(
    val unit: DistanceUnit = DistanceUnit.KILOMETRES,
    val radiusKm: Int = 50,
    val dataMode: DataMode = DataMode.LIVE,
    val sampleScene: String = SampleSource.CHENNAI,
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
        .apply()
    _settings.value = s
  }

  private fun load() =
      Settings(
          unit = enumOr(prefs.getString("unit", null), DistanceUnit.KILOMETRES),
          radiusKm = prefs.getInt("radiusKm", 50),
          dataMode = enumOr(prefs.getString("dataMode", null), DataMode.LIVE),
          sampleScene = prefs.getString("sampleScene", null) ?: SampleSource.CHENNAI,
      )

  private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
      name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
}
