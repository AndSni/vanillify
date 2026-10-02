package app.vanillify.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel

enum class Tab(val label: String, val selected: ImageVector, val unselected: ImageVector) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    APPS("Apps", Icons.Filled.Apps, Icons.Outlined.Apps),
    PRIVACY("Privacy", Icons.Filled.Shield, Icons.Outlined.Shield),
    HISTORY("History", Icons.Filled.History, Icons.Outlined.History),
}

@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var reviewing by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.message.collect { snackbar.showSnackbar(it) }
    }

    if (reviewing) {
        BackHandler { reviewing = false }
        ReviewScreen(vm, onClose = { reviewing = false })
        return
    }
    BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(if (tab == t) t.selected else t.unselected, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        // Each tab draws its own top app bar, so only the bottom bar's space is taken here.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            Tab.HOME -> HomeScreen(vm, modifier, onVanillify = { reviewing = true }, onOpen = { tab = it })
            Tab.APPS -> AppsScreen(vm, modifier)
            Tab.PRIVACY -> PrivacyScreen(vm, modifier)
            Tab.HISTORY -> HistoryScreen(vm, modifier)
        }
    }
}
