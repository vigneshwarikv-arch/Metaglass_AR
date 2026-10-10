package com.metaglassar.planes

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.metaglassar.planes.core.Geo
import com.metaglassar.planes.core.GeoPoint
import com.metaglassar.planes.core.Pointing
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Phone GPS / network location as a flow. Caller must hold a location permission. */
class LocationSource(context: Context) {
  private val lm = context.getSystemService(LocationManager::class.java)

  @SuppressLint("MissingPermission")
  fun updates(): Flow<GeoPoint> = callbackFlow {
    val listener = LocationListener { loc -> trySend(loc.toGeoPoint()) }
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { lm.isProviderEnabled(it) }
    providers
        .mapNotNull { lm.getLastKnownLocation(it) }
        .maxByOrNull { it.time }
        ?.let { trySend(it.toGeoPoint()) }
    providers.forEach { lm.requestLocationUpdates(it, 5_000L, 25f, listener, Looper.getMainLooper()) }
    awaitClose { lm.removeUpdates(listener) }
  }

  fun anyProviderEnabled(): Boolean =
      lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

  private fun Location.toGeoPoint() = GeoPoint(latitude, longitude, if (hasAltitude()) altitude else 0.0)
}

/** Magnetic declination (east positive) at a location, to turn compass bearings into true bearings. */
fun declinationAt(p: GeoPoint): Double =
    GeomagneticField(p.lat.toFloat(), p.lon.toFloat(), p.altitudeM.toFloat(), System.currentTimeMillis()).declination.toDouble()

/**
 * Where the phone's back (camera side) is pointing, from the rotation-vector sensor (fused
 * accelerometer + gyroscope + magnetometer). Bearing is relative to MAGNETIC north; add the
 * declination for true north.
 */
class PhoneOrientationSource(context: Context) {
  private val sm = context.getSystemService(SensorManager::class.java)
  private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

  val available: Boolean get() = sensor != null

  data class Reading(val magneticPointing: Pointing, val accuracy: Int)

  fun updates(): Flow<Reading> = callbackFlow {
    val s = sensor ?: run { close(); return@callbackFlow }
    val r = FloatArray(9)
    var accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
    // Low-pass filter on the pointing vector (east, north, up) to steady the readout.
    var e = 0.0
    var n = 0.0
    var u = 0.0
    var first = true
    val listener =
        object : SensorEventListener {
          override fun onSensorChanged(event: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(r, event.values)
            // World = R · device. The back of the phone is device −Z.
            val east = -r[2].toDouble()
            val north = -r[5].toDouble()
            val up = -r[8].toDouble()
            val k = if (first) 1.0 else 0.15
            first = false
            e += k * (east - e)
            n += k * (north - n)
            u += k * (up - u)
            val len = sqrt(e * e + n * n + u * u).takeIf { it > 1e-6 } ?: return
            val bearing = Geo.normalizeDeg(Math.toDegrees(atan2(e, n)))
            val elevation = Math.toDegrees(asin((u / len).coerceIn(-1.0, 1.0)))
            trySend(Reading(Pointing(bearing, elevation), accuracy))
          }

          override fun onAccuracyChanged(sensor: Sensor, acc: Int) {
            accuracy = acc
          }
        }
    sm.registerListener(listener, s, SensorManager.SENSOR_DELAY_GAME)
    awaitClose { sm.unregisterListener(listener) }
  }
}
