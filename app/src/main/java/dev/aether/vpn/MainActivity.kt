package dev.aether.vpn

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aether.vpn.service.VpnController
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutHorizontally
import kotlinx.coroutines.CancellationException
import dev.aether.vpn.core.Updater
import dev.aether.vpn.ui.MainViewModel
import dev.aether.vpn.ui.components.FloatingNavBar
import dev.aether.vpn.ui.components.NavItem
import dev.aether.vpn.ui.components.UpdateDialog
import dev.aether.vpn.ui.isBackgroundUnrestricted
import dev.aether.vpn.ui.requestBackgroundUnrestricted
import dev.aether.vpn.ui.screens.HomeScreen
import dev.aether.vpn.ui.screens.SettingsScreen
import dev.aether.vpn.ui.screens.SubscriptionsScreen
import dev.aether.vpn.ui.theme.AetherTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleShare(intent)
        setContent {
            val data by vm.data.collectAsStateWithLifecycle()
            AetherTheme(mode = data.settings.theme, dynamicColor = data.settings.dynamicColor) {
                AetherRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            vm.import(intent.getStringExtra(Intent.EXTRA_TEXT))
        }
    }
}

private enum class Tab(val label: String, val selected: ImageVector, val unselected: ImageVector) {
    Home("Home", Icons.Rounded.Home, Icons.Outlined.Home),
    Subscriptions("Subscriptions", Icons.Rounded.Layers, Icons.Outlined.Layers),
    Settings("Settings", Icons.Rounded.Settings, Icons.Outlined.Settings),
}

@Composable
private fun AetherRoot(vm: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var homeScrollSignal by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    // Predictive back: on Subscriptions/Settings the gesture previews Home and commits on release
    val tabState = remember { SeekableTransitionState(tab) }
    LaunchedEffect(tab) { tabState.animateTo(tab) }
    PredictiveBackHandler(enabled = tab != 0) { events ->
        try {
            events.collect { e -> tabState.seekTo(e.progress, targetState = 0) }
            tab = 0
        } catch (e: CancellationException) {
            tabState.animateTo(tab)
        }
    }
    // On Home the back gesture sends the app to the background instead of closing it
    val activity = LocalActivity.current
    BackHandler(enabled = tab == 0) { activity?.moveTaskToBack(true) }

    // Ask once on first launch to lift background restrictions; can be skipped and granted later in Settings
    val data by vm.data.collectAsStateWithLifecycle()
    if (!data.batteryPromptDone && !context.isBackgroundUnrestricted()) {
        AlertDialog(
            onDismissRequest = { vm.finishBatteryPrompt() },
            icon = { Icon(Icons.Rounded.BatteryAlert, null) },
            title = { Text("Allow background activity") },
            text = { Text("Android may stop the VPN when the screen is off. Allow Aether to run without battery restrictions?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.finishBatteryPrompt()
                    context.requestBackgroundUnrestricted()
                }) { Text("Allow") }
            },
            dismissButton = { TextButton(onClick = { vm.finishBatteryPrompt() }) { Text("Skip") } },
        )
    }

    val updatePrompt by vm.updatePrompt.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    if (updatePrompt && (update is Updater.State.Available || update is Updater.State.Downloading || update is Updater.State.Ready)) {
        UpdateDialog(
            state = update,
            onUpdate = vm::downloadUpdate,
            onInstall = vm::installUpdate,
            onDismiss = vm::dismissUpdate,
        )
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val vpnPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) VpnController.start(context)
        else vm.say("VPN permission denied")
    }

    val toggleVpn: () -> Unit = {
        if (VpnController.isActive) {
            VpnController.stop(context)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            val consent = VpnService.prepare(context)
            if (consent != null) vpnPermission.launch(consent) else VpnController.start(context)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val selectTab: (Int) -> Unit = { index ->
                if (tab == index && index == 0) homeScrollSignal++
                tab = index
            }
            if (data.settings.floatingNavBar) {
                FloatingNavBar(
                    items = remember { Tab.entries.map { NavItem(it.label, it.selected, it.unselected) } },
                    selected = tab,
                    expandSelected = data.settings.navBarLabels,
                    onSelect = selectTab,
                )
            } else {
                ShortNavigationBar {
                    Tab.entries.forEachIndexed { index, t ->
                        ShortNavigationBarItem(
                            selected = tab == index,
                            onClick = { selectTab(index) },
                            icon = { Icon(if (tab == index) t.selected else t.unselected, contentDescription = t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        rememberTransition(tabState, label = "tab").AnimatedContent(
            transitionSpec = {
                if (targetState < initialState) {
                    // Going back: the current screen shrinks and slides away, Home grows in behind it
                    (fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.92f))
                        .togetherWith(fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.88f) + slideOutHorizontally(tween(300)) { it / 4 })
                } else {
                    (fadeIn(tween(220, delayMillis = 60)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220, delayMillis = 60)))
                        .togetherWith(fadeOut(tween(90)))
                }
            },
            modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
        ) { current ->
            when (Tab.entries[current]) {
                Tab.Home -> HomeScreen(vm, onToggle = toggleVpn, onOpenSubscriptions = { tab = 1 }, scrollToTop = homeScrollSignal)
                Tab.Subscriptions -> SubscriptionsScreen(vm)
                Tab.Settings -> SettingsScreen(vm)
            }
        }
    }
}
