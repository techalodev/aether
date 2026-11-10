package dev.aether.vpn.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aether.vpn.BuildConfig
import dev.aether.vpn.core.GeoData
import dev.aether.vpn.core.SubscriptionFetcher
import dev.aether.vpn.core.XrayConfig
import dev.aether.vpn.data.RoutingRule
import dev.aether.vpn.data.RuleAction
import dev.aether.vpn.data.Settings
import dev.aether.vpn.data.SplitMode
import dev.aether.vpn.data.ThemeMode
import dev.aether.vpn.ui.MainViewModel
import dev.aether.vpn.ui.components.SectionHeader
import dev.aether.vpn.ui.components.SegmentGap
import dev.aether.vpn.ui.components.SegmentedGroup
import dev.aether.vpn.ui.components.segmentShape
import dev.aether.vpn.ui.formatBytes
import dev.aether.vpn.core.Updater
import dev.aether.vpn.ui.AppIcon
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CancellationException
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import dev.aether.vpn.ui.rememberBackgroundUnrestricted
import dev.aether.vpn.ui.requestBackgroundUnrestricted
import dev.aether.vpn.data.CountryPart
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ToggleButton
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libv2ray.Libv2ray

private const val REPO_URL = "https://github.com/techalodev/aether"

private enum class Page(val title: String, val subtitle: String, val icon: ImageVector) {
    Subscriptions("Subscriptions", "User-Agent, HWID", Icons.Rounded.Layers),
    Connection("Connection", "DNS, LAN, latency test", Icons.Rounded.Shield),
    Routing("Routing", "Rules, Russia direct", Icons.Rounded.Route),
    Split("Split tunneling", "Choose apps", Icons.AutoMirrored.Rounded.CallSplit),
    Appearance("Appearance", "Theme, app icon, exit country", Icons.Rounded.Palette),
    About("About", "Version, core", Icons.Rounded.Info),
}

private data class EditTarget(
    val title: String,
    val value: String,
    val default: String?,
    val copyable: Boolean = false,
    val userAgentPresets: Boolean = false,
    val apply: (Settings, String) -> Settings,
)

/** Quick picks for current client User-Agents. */
@Composable
internal fun UserAgentPresets(current: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Settings.USER_AGENT_PRESETS.forEach { (label, ua) ->
            FilterChip(
                selected = current == ua,
                onClick = { onPick(ua) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    var page by rememberSaveable { mutableStateOf<Page?>(null) }

    // Predictive back: the gesture drags the sub-page away and reveals the section list behind it
    val pageState = remember { SeekableTransitionState(page) }
    LaunchedEffect(page) { pageState.animateTo(page) }
    PredictiveBackHandler(enabled = page != null) { events ->
        try {
            events.collect { e -> pageState.seekTo(e.progress, targetState = null) }
            page = null
        } catch (e: CancellationException) {
            pageState.animateTo(page)
        }
    }

    rememberTransition(pageState, label = "settings-page").AnimatedContent(
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(320)))
                    .togetherWith(slideOutHorizontally(tween(320)) { -it / 3 } + fadeOut(tween(200)))
            } else {
                (fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.94f))
                    .togetherWith(slideOutHorizontally(tween(300)) { it / 3 } + scaleOut(tween(300), targetScale = 0.9f) + fadeOut(tween(300)))
            }
        },
    ) { current ->
        when (current) {
            null -> SettingsPage("Settings", onBack = null) { _ ->
                item { BackgroundCard() }
                item {
                    Column {
                        Page.entries.forEachIndexed { i, p ->
                            if (i > 0) SegmentGap()
                            NavRow(segmentShape(i, Page.entries.size), p.icon, p.title, p.subtitle) { page = p }
                        }
                    }
                }
            }
            else -> SettingsPage(current.title, onBack = { page = null }) { _ ->
                when (current) {
                    Page.Subscriptions -> subscriptionsPage(vm)
                    Page.Connection -> connectionPage(vm)
                    Page.Routing -> routingPage(vm)
                    Page.Split -> splitPage(vm)
                    Page.Appearance -> appearancePage(vm)
                    Page.About -> aboutPage(vm)
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(title: String, onBack: (() -> Unit)?, content: LazyListScope.(Unit) -> Unit) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
        ) { content(Unit) }
    }
}

// ---- pages --------------------------------------------------------------------------------------

private fun LazyListScope.subscriptionsPage(vm: MainViewModel) {
    item {
        val data by vm.data.collectAsStateWithLifecycle()
        val s = data.settings
        val context = LocalContext.current
        var edit by remember { mutableStateOf<EditTarget?>(null) }
        val hwid = remember { vm.deviceHwid() }
        SegmentedGroup(
            title = null,
            items = listOf(
                { shape ->
                    ValueRow(shape, "User-Agent", s.userAgent) {
                        edit = EditTarget("User-Agent", s.userAgent, Settings.DEFAULT_USER_AGENT, userAgentPresets = true) { st, v ->
                            st.copy(userAgent = v.ifBlank { Settings.DEFAULT_USER_AGENT })
                        }
                    }
                },
                { shape -> SwitchRow(shape, "Send HWID", s.sendHwid) { v -> vm.updateSettings { it.copy(sendHwid = v) } } },
                { shape ->
                    ValueRow(shape, "Device HWID", "$hwid · tap to copy", enabled = s.sendHwid) {
                        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("HWID", hwid))
                        vm.say("HWID copied")
                    }
                },
            ),
        )
        Hint("Applies to all subscriptions unless overridden in a subscription's settings. The HWID is your device's ID and can't be changed.")
        edit?.let { EditDialog(it, vm) { edit = null } }
    }
}

private fun LazyListScope.connectionPage(vm: MainViewModel) {
    item {
        val data by vm.data.collectAsStateWithLifecycle()
        val s = data.settings
        var edit by remember { mutableStateOf<EditTarget?>(null) }
        SegmentedGroup(
            title = null,
            items = listOf(
                { shape ->
                    ValueRow(shape, "DNS", s.remoteDns) {
                        edit = EditTarget("DNS", s.remoteDns, "1.1.1.1") { st, v -> st.copy(remoteDns = v.ifBlank { "1.1.1.1" }) }
                    }
                },
                { shape -> SwitchRow(shape, "Bypass LAN", s.bypassLan) { v -> vm.updateTunnelSettings { it.copy(bypassLan = v) } } },
                { shape ->
                    ValueRow(shape, "Latency test URL", s.testUrl) {
                        edit = EditTarget("Latency test URL", s.testUrl, Settings().testUrl) { st, v ->
                            st.copy(testUrl = v.ifBlank { Settings().testUrl })
                        }
                    }
                },
            ),
        )
        edit?.let { EditDialog(it, vm, tunnel = true) { edit = null } }
    }
}

private fun LazyListScope.routingPage(vm: MainViewModel) {
    item {
        val data by vm.data.collectAsStateWithLifecycle()
        val geo by vm.geo.collectAsStateWithLifecycle()
        val s = data.settings
        var confirmDownload by remember { mutableStateOf(false) }
        var editRule by remember { mutableStateOf<RoutingRule?>(null) }

        SectionHeader("Presets", Modifier.padding(start = 4.dp))
        Column(
            Modifier.fillMaxWidth().clip(segmentShape(0, 2)).background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(role = Role.Switch) { vm.updateTunnelSettings { it.copy(ruDirect = !it.ruDirect) } }
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Russia direct", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (geo.installed) ".ru .su .рф · category-ru · geoip:ru" else ".ru .su .рф · geo files not downloaded",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AetherSwitch(s.ruDirect)
            }
        }
        SegmentGap()
        val geoShape = segmentShape(1, 2)
        when {
            geo.progress != null -> RowContainer(geoShape, null) {
                Column(Modifier.weight(1f)) {
                    Text("Downloading geo files", style = MaterialTheme.typography.bodyLarge)
                    LinearWavyProgressIndicator(progress = { geo.progress ?: 0f }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                }
            }
            geo.installed -> ValueRow(
                geoShape, "Geo files",
                "${formatBytes(geo.sizeBytes)} · " + DateUtils.getRelativeTimeSpanString(geo.updatedAt).toString() + " · tap to update",
            ) { confirmDownload = true }
            else -> ValueRow(geoShape, "Download geo files", "geosite:category-ru, geoip:ru · ~${GeoData.APPROX_SIZE_MB} MB") { confirmDownload = true }
        }
        if (geo.installed && geo.progress == null) {
            TextButton(onClick = vm::deleteGeo, modifier = Modifier.padding(start = 8.dp)) { Text("Delete geo files") }
        }

        var editUrl by remember { mutableStateOf<EditTarget?>(null) }
        SegmentedGroup(
            title = "Geo sources",
            items = listOf(
                { shape ->
                    ValueRow(shape, "geoip.dat", s.geoipUrl) {
                        editUrl = EditTarget("geoip.dat URL", s.geoipUrl, Settings.DEFAULT_GEOIP_URL) { st, v ->
                            st.copy(geoipUrl = v.ifBlank { Settings.DEFAULT_GEOIP_URL })
                        }
                    }
                },
                { shape ->
                    ValueRow(shape, "geosite.dat", s.geositeUrl) {
                        editUrl = EditTarget("geosite.dat URL", s.geositeUrl, Settings.DEFAULT_GEOSITE_URL) { st, v ->
                            st.copy(geositeUrl = v.ifBlank { Settings.DEFAULT_GEOSITE_URL })
                        }
                    }
                },
            ),
        )
        Hint("Any Xray-compatible .dat files, e.g. Loyalsoldier/v2ray-rules-dat. Download again after changing.")
        editUrl?.let { EditDialog(it, vm) { editUrl = null } }

        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("Rules", Modifier.padding(start = 4.dp).weight(1f))
            IconButton(onClick = { editRule = RoutingRule(SubscriptionFetcher.newId(), "", RuleAction.Direct) }) {
                Icon(Icons.Rounded.Add, contentDescription = "Add rule")
            }
        }
        if (s.rules.isEmpty()) {
            Hint("domain, IP/CIDR, geosite: or geoip: — routed via Proxy, Direct or Block. Rules are checked top to bottom, before presets.")
        }
        s.rules.forEachIndexed { i, rule ->
            if (i > 0) SegmentGap()
            RowContainer(segmentShape(i, s.rules.size), onClick = { editRule = rule }) {
                Column(Modifier.weight(1f)) {
                    Text(
                        rule.action.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = when (rule.action) {
                            RuleAction.Proxy -> MaterialTheme.colorScheme.primary
                            RuleAction.Direct -> MaterialTheme.colorScheme.tertiary
                            RuleAction.Block -> MaterialTheme.colorScheme.error
                        },
                    )
                    Text(
                        XrayConfig.splitEntries(rule.values).joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { on ->
                        vm.updateTunnelSettings { st -> st.copy(rules = st.rules.map { if (it.id == rule.id) it.copy(enabled = on) else it }) }
                    },
                )
            }
        }

        if (confirmDownload) {
            AlertDialog(
                onDismissRequest = { confirmDownload = false },
                title = { Text("Download geo files?") },
                text = { Text("geoip.dat and geosite.dat from runetfreedom — about ${GeoData.APPROX_SIZE_MB} MB. Wi-Fi recommended.") },
                confirmButton = { TextButton(onClick = { confirmDownload = false; vm.downloadGeo() }) { Text("Download") } },
                dismissButton = { TextButton(onClick = { confirmDownload = false }) { Text("Cancel") } },
            )
        }
        editRule?.let { rule ->
            RuleDialog(
                rule = rule,
                isNew = s.rules.none { it.id == rule.id },
                onDismiss = { editRule = null },
                onSave = { saved ->
                    vm.updateTunnelSettings { st ->
                        st.copy(rules = if (st.rules.any { it.id == saved.id }) st.rules.map { if (it.id == saved.id) saved else it } else st.rules + saved)
                    }
                    editRule = null
                },
                onDelete = {
                    vm.updateTunnelSettings { st -> st.copy(rules = st.rules.filterNot { it.id == rule.id }) }
                    editRule = null
                },
            )
        }
    }
}

private data class AppEntry(val packageName: String, val label: String, val system: Boolean, val info: ApplicationInfo)

private fun LazyListScope.splitPage(vm: MainViewModel) {
    item(key = "split") {
        val context = LocalContext.current
        val data by vm.data.collectAsStateWithLifecycle()
        val s = data.settings
        var query by rememberSaveable { mutableStateOf("") }
        var showSystem by rememberSaveable { mutableStateOf(false) }
        val apps by produceState<List<AppEntry>?>(null) {
            value = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                pm.getInstalledApplications(0)
                    .filter { it.packageName != context.packageName }
                    .map {
                        AppEntry(
                            it.packageName, pm.getApplicationLabel(it).toString(),
                            it.flags and ApplicationInfo.FLAG_SYSTEM != 0, it,
                        )
                    }
                    .sortedBy { it.label.lowercase() }
            }
        }

        Choice(
            options = SplitMode.entries.map { it.name },
            selected = s.splitMode.name,
            label = { when (it) { "Off" -> "Off"; "Include" -> "Only selected"; else -> "Bypass selected" } },
        ) { m -> vm.updateTunnelSettings { it.copy(splitMode = SplitMode.valueOf(m)) } }
        Hint(
            when (s.splitMode) {
                SplitMode.Off -> "All apps use the VPN."
                SplitMode.Include -> "Only checked apps use the VPN."
                SplitMode.Exclude -> "Checked apps bypass the VPN."
            }
        )

        if (s.splitMode != SplitMode.Off) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable { showSystem = !showSystem }.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Show system apps", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Checkbox(checked = showSystem, onCheckedChange = { showSystem = it })
            }
            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            } else {
                val filtered = list.filter {
                    (showSystem || !it.system || it.packageName in s.splitApps) &&
                        (query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true))
                }.sortedByDescending { it.packageName in s.splitApps }
                Column {
                    filtered.forEachIndexed { i, app ->
                        if (i > 0) SegmentGap()
                        AppRow(app, checked = app.packageName in s.splitApps, shape = segmentShape(i, filtered.size)) { on ->
                            vm.updateTunnelSettings { st ->
                                st.copy(splitApps = if (on) st.splitApps + app.packageName else st.splitApps - app.packageName)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.appearancePage(vm: MainViewModel) {
    item {
        val data by vm.data.collectAsStateWithLifecycle()
        val s = data.settings
        val rows = buildList<@Composable (Shape) -> Unit> {
            add { shape ->
                RowContainer(shape, onClick = null) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("Theme", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.size(12.dp))
                        Choice(ThemeMode.entries.map { it.name }, s.theme.name) { m ->
                            vm.updateSettings { it.copy(theme = ThemeMode.valueOf(m)) }
                        }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add { shape -> SwitchRow(shape, "Dynamic color", s.dynamicColor) { v -> vm.updateSettings { it.copy(dynamicColor = v) } } }
            }
            add { shape -> SwitchRow(shape, "Floating navigation bar", s.floatingNavBar) { v -> vm.updateSettings { it.copy(floatingNavBar = v) } } }
            if (s.floatingNavBar) {
                add { shape -> SwitchRow(shape, "Show label on selected tab", s.navBarLabels) { v -> vm.updateSettings { it.copy(navBarLabels = v) } } }
            }
        }
        SegmentedGroup(null, items = rows)

        SectionHeader("App icon", Modifier.padding(start = 4.dp))
        AppIconPicker()

        SegmentedGroup(
            title = "Exit country",
            items = listOf(
                { shape ->
                    RowContainer(shape, onClick = null) {
                        Column(Modifier.fillMaxWidth()) {
                            Text("Show on Home", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Flag, country code and country name in English",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.size(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                                CountryPart.entries.forEachIndexed { i, part ->
                                    ToggleButton(
                                        checked = part in s.countryParts,
                                        onCheckedChange = { on ->
                                            vm.updateSettings { st -> st.copy(countryParts = if (on) st.countryParts + part else st.countryParts - part) }
                                        },
                                        shapes = when (i) {
                                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                            CountryPart.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(when (part) { CountryPart.Flag -> "Flag"; CountryPart.Code -> "Code"; CountryPart.Name -> "Name" }, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                },
            ),
        )

        SegmentedGroup(
            title = "Subscription update popup",
            items = listOf(
                { shape -> SwitchRow(shape, "Show result after manual update", s.updatePopup) { v -> vm.updateSettings { it.copy(updatePopup = v) } } },
                { shape ->
                    RowContainer(shape, onClick = null) {
                        Column(Modifier.fillMaxWidth().then(if (s.updatePopup) Modifier else Modifier.graphicsLayer { alpha = 0.38f })) {
                            Text("Hide after", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.size(12.dp))
                            Choice(listOf("3", "5", "8", "10"), s.updatePopupSeconds.toString(), label = { "${it}s" }) { v ->
                                if (s.updatePopup) vm.updateSettings { it.copy(updatePopupSeconds = v.toInt()) }
                            }
                        }
                    }
                },
            ),
        )
    }
}

private fun LazyListScope.aboutPage(vm: MainViewModel) {
    item {
        val context = LocalContext.current
        val data by vm.data.collectAsStateWithLifecycle()
        val update by vm.update.collectAsStateWithLifecycle()
        val core = remember { runCatching { Libv2ray.checkVersionX() }.getOrDefault("—") }
        val updateStatus = when (val u = update) {
            Updater.State.Idle -> "Tap to check"
            Updater.State.Checking -> "Checking…"
            Updater.State.UpToDate -> "You're on the latest version"
            is Updater.State.Available -> "Version ${u.release.version} available"
            is Updater.State.Downloading -> "Downloading ${(u.progress * 100).toInt()}%"
            is Updater.State.Ready -> "Ready to install ${u.release.version}"
            is Updater.State.Failed -> u.message
        }
        SegmentedGroup(
            null,
            items = listOf(
                { shape ->
                    ValueRow(shape, "Updates", updateStatus) {
                        when (update) {
                            is Updater.State.Available, is Updater.State.Downloading, is Updater.State.Ready -> vm.showUpdate()
                            Updater.State.Checking -> Unit
                            else -> vm.checkForUpdates()
                        }
                    }
                },
                { shape -> SwitchRow(shape, "Check for updates on launch", data.settings.checkUpdates) { v -> vm.updateSettings { it.copy(checkUpdates = v) } } },
            ),
        )
        Spacer(Modifier.size(16.dp))
        SegmentedGroup(
            null,
            items = listOf(
                { shape -> ValueRow(shape, "Version", BuildConfig.VERSION_NAME) },
                { shape -> ValueRow(shape, "Core", core) },
                { shape -> ValueRow(shape, "Font", "Google Sans Flex · SIL OFL 1.1") },
                { shape ->
                    ValueRow(shape, "Source code", "GitHub") {
                        context.startActivity(Intent(Intent.ACTION_VIEW, REPO_URL.toUri()))
                    }
                },
            ),
        )
    }
}

// ---- rows & dialogs -----------------------------------------------------------------------------

/** Launcher icon variants in a horizontally scrolling strip; only the selected one is labelled. */
@Composable
private fun AppIconPicker() {
    val context = LocalContext.current
    var current by remember { mutableStateOf(AppIcon.current(context)) }
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val cookie = MaterialShapes.Cookie12Sided.toShape()
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(colors.surfaceContainer)
            .padding(vertical = 16.dp),
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(AppIcon.entries, key = { it.name }) { icon ->
                val selected = icon == current
                val ring by animateDpAsState(if (selected) 3.dp else 0.dp, motion.fastSpatialSpec(), label = "ring")
                val scale by animateFloatAsState(if (selected) 1f else 0.86f, motion.fastSpatialSpec(), label = "scale")
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .clip(cookie)
                            .clickable {
                                if (!selected) {
                                    AppIcon.select(context, icon)
                                    current = icon
                                    // Launchers pick up the alias swap once Aether is in the background
                                    context.findActivity()?.finishAndRemoveTask()
                                }
                            }
                            .border(ring, colors.primary, cookie)
                            .padding(ring + 2.dp)
                            .clip(cookie),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Adaptive icons show the middle 72/108 of their layers
                        Image(painterResource(icon.background), icon.label, Modifier.requiredSize(84.dp))
                        Image(painterResource(icon.foreground), null, Modifier.requiredSize(84.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        icon.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) colors.primary else colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
    Hint("Aether closes to apply the new icon.")
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Shown only until Aether is exempt from battery optimisation. */
@Composable
private fun BackgroundCard() {
    val context = LocalContext.current
    val granted = rememberBackgroundUnrestricted()
    AnimatedVisibility(visible = !granted, exit = shrinkVertically() + fadeOut()) {
        Row(
            Modifier
                .padding(bottom = 16.dp)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .clickable { context.requestBackgroundUnrestricted() }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.BatteryAlert, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Allow background activity", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    "Keeps the VPN alive when the screen is off",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
    )
}

@Composable
private fun NavRow(shape: Shape, icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    RowContainer(shape, onClick) {
        Box(
            Modifier.size(40.dp).clip(MaterialShapes.Cookie12Sided.toShape()).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RowContainer(shape: Shape, onClick: (() -> Unit)?, role: Role = Role.Button, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (onClick != null) Modifier.clickable(role = role, onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun ValueRow(shape: Shape, title: String, value: String, enabled: Boolean = true, onClick: (() -> Unit)? = null) {
    val alpha = if (enabled) 1f else 0.38f
    RowContainer(shape, onClick.takeIf { enabled }) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AetherSwitch(checked: Boolean) {
    Switch(
        checked = checked,
        onCheckedChange = null,
        thumbContent = if (checked) {
            { Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
        } else null,
    )
}

@Composable
private fun SwitchRow(shape: Shape, title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    RowContainer(shape, onClick = { onChange(!checked) }, role = Role.Switch) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        AetherSwitch(checked)
    }
}

@Composable
private fun AppRow(app: AppEntry, checked: Boolean, shape: Shape, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(app.info).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Checkbox) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(40.dp)) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun EditDialog(target: EditTarget, vm: MainViewModel, tunnel: Boolean = false, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var value by rememberSaveable { mutableStateOf(target.value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(target.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value, { value = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (target.userAgentPresets) UserAgentPresets(value) { value = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (tunnel) vm.updateTunnelSettings { target.apply(it, value.trim()) } else vm.updateSettings { target.apply(it, value.trim()) }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (target.copyable) TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(target.title, value))
                }) { Text("Copy") }
                if (target.default != null) TextButton(onClick = { value = target.default }) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun RuleDialog(rule: RoutingRule, isNew: Boolean, onDismiss: () -> Unit, onSave: (RoutingRule) -> Unit, onDelete: () -> Unit) {
    var values by remember { mutableStateOf(rule.values) }
    var action by remember { mutableStateOf(rule.action) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New rule" else "Edit rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = values,
                    onValueChange = { values = it },
                    label = { Text("Domains / IPs") },
                    placeholder = { Text("example.com\ngeosite:google\n10.0.0.0/8") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                )
                Choice(RuleAction.entries.map { it.name }, action.name) { action = RuleAction.valueOf(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(rule.copy(values = values.trim(), action = action)) }, enabled = values.isNotBlank()) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
