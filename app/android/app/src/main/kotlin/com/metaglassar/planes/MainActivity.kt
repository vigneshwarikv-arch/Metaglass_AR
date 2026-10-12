package com.metaglassar.planes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.metaglassar.planes.ui.AppRoot
import com.metaglassar.planes.ui.PlanesTheme

class MainActivity : ComponentActivity() {
  private val vm: MainViewModel by viewModels()
  private val glasses get() = (application as PlanesApp).graph.glasses

  private val locationPermission =
      registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        vm.onLocationPermission(result.values.any { it })
      }

  /** Bluetooth permission comes first: DAT must not start without it (see GlassesConnector.start). */
  private val bluetoothPermission =
      registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
          glasses.start()
          glasses.startRegistration(this)
        }
      }

  /** Glasses microphone (opens the Meta AI app); only launched from a user tap. */
  private val glassesMicPermission =
      registerForActivityResult(Wearables.RequestPermissionContract()) { _ -> glasses.onPermissionsChanged() }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    vm.onLocationPermission(hasLocationPermission())
    if (granted(Manifest.permission.BLUETOOTH_CONNECT)) glasses.start()
    glasses.onIntent(intent)

    lifecycle.addObserver(
        object : DefaultLifecycleObserver {
          override fun onStart(owner: LifecycleOwner) = vm.setVisible(true)

          override fun onStop(owner: LifecycleOwner) = vm.setVisible(false)
        })

    setContent {
      PlanesTheme {
        AppRoot(
            vm = vm,
            onRequestLocation = {
              locationPermission.launch(
                  arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            onConnectGlasses = {
              if (granted(Manifest.permission.BLUETOOTH_CONNECT)) {
                glasses.start()
                glasses.startRegistration(this)
              } else {
                bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
              }
            },
            onDisconnectGlasses = { glasses.startUnregistration(this) },
            onEnableGlassesMic = { glassesMicPermission.launch(Permission.MICROPHONE) },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    glasses.onIntent(intent)
  }

  private fun hasLocationPermission() =
      granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

  private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
}
