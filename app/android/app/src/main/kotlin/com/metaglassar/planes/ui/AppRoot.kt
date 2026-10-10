package com.metaglassar.planes.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.metaglassar.planes.MainViewModel

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs =
    listOf(
        Tab("nearby", "Nearby", Icons.Filled.Place),
        Tab("point", "Point", Icons.Filled.Search),
        Tab("settings", "Settings", Icons.Filled.Settings),
    )

@Composable
fun AppRoot(
    vm: MainViewModel,
    onRequestLocation: () -> Unit,
    onConnectGlasses: () -> Unit,
    onDisconnectGlasses: () -> Unit,
    onEnableGlassesMic: () -> Unit,
) {
  val state by vm.state.collectAsStateWithLifecycle()
  val nav = rememberNavController()
  val backStack by nav.currentBackStackEntryAsState()
  val current = backStack?.destination?.route

  Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
      bottomBar = {
        if (current?.startsWith("details") != true) {
          NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            tabs.forEach { tab ->
              NavigationBarItem(
                  selected = current == tab.route,
                  onClick = {
                    nav.navigate(tab.route) {
                      popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                      launchSingleTop = true
                      restoreState = true
                    }
                  },
                  icon = { Icon(tab.icon, contentDescription = null) },
                  label = { Text(tab.label) },
              )
            }
          }
        }
      },
  ) { padding ->
    Column(Modifier.padding(padding)) {
      state.lastAnswer?.let { AnswerBanner(it, onDismiss = vm::dismissAnswer) }
      NavHost(nav, startDestination = "nearby") {
        composable("nearby") {
          NearbyScreen(
              state = state,
              onOpen = { nav.navigate("details/$it") },
              onRequestLocation = onRequestLocation,
              onUseSample = { vm.updateSettings { s -> s.copy(dataMode = com.metaglassar.planes.DataMode.SAMPLE) } },
              onRetry = vm::retry,
          )
        }
        composable("point") {
          PointScreen(
              state = state,
              onOpen = { nav.navigate("details/$it") },
              onAsk = { vm.ask() },
              onRequestLocation = onRequestLocation,
          )
        }
        composable("settings") {
          SettingsScreen(
              state = state,
              scenes = vm.sampleScenes,
              onChange = vm::updateSettings,
              onConnectGlasses = onConnectGlasses,
              onDisconnectGlasses = onDisconnectGlasses,
              onEnableGlassesMic = onEnableGlassesMic,
          )
        }
        composable("details/{icao24}") { entry ->
          val id = entry.arguments?.getString("icao24").orEmpty()
          DetailsScreen(seen = vm.aircraft(id), state = state, onBack = { nav.popBackStack() })
        }
      }
    }
  }
}
