package dev.aether.vpn.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.ui.graphics.graphicsLayer
import dev.aether.vpn.data.CountryPart
import dev.aether.vpn.data.Profile
import dev.aether.vpn.data.Subscription
import dev.aether.vpn.service.VpnState
import dev.aether.vpn.ui.MainViewModel
import dev.aether.vpn.ui.components.ConnectButton
import dev.aether.vpn.ui.components.ConnectVisual
import dev.aether.vpn.ui.components.SegmentGap
import dev.aether.vpn.ui.components.segmentShape
import dev.aether.vpn.ui.formatDuration
import dev.aether.vpn.ui.formatSpeed
import kotlinx.coroutines.delay

fun Context.clipboardText(): String? =
    getSystemService(ClipboardManager::class.java)?.primaryClip
        ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()

fun Context.shareText(text: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    startActivity(Intent.createChooser(send, title))
}

/** A home-screen group: a subscription, or null for manually imported configs. */
private data class Group(val sub: Subscription?, val title: String, val collapsed: Boolean, val profiles: List<Profile>)

@Composable
fun HomeScreen(vm: MainViewModel, onToggle: () -> Unit, onOpenSubscriptions: () -> Unit, scrollToTop: Int = 0) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Re-selecting the Home tab scrolls back to the top
    LaunchedEffect(scrollToTop) {
        if (scrollToTop > 0) listState.animateScrollToItem(0)
    }
    val data by vm.data.collectAsStateWithLifecycle()
    val state by vm.vpnState.collectAsStateWithLifecycle()
    val pings by vm.pings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Profile?>(null) }
    var renaming by remember { mutableStateOf<Subscription?>(null) }

    val groups = remember(data.profiles, data.subscriptions, data.localCollapsed) {
        val bySub = data.profiles.groupBy { it.subId }
        data.subscriptions.filter { it.showOnHome }.mapNotNull { s ->
            bySub[s.id]?.let { Group(s, s.name.ifBlank { "Subscription" }, s.collapsed, it) }
        } + listOfNotNull(bySub[null]?.let { Group(null, "Local", data.localCollapsed, it) })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Aether",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { scope.launch { listState.animateScrollToItem(0) } }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = 24.dp,
            ),
        ) {
            item(key = "hero") {
                Hero(
                    state = state,
                    vm = vm,
                    enabled = data.selected != null || state !is VpnState.Idle,
                    onToggle = onToggle,
                )
            }
            if (data.profiles.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        onAddSubscription = onOpenSubscriptions,
                        onPaste = { vm.import(context.clipboardText()) },
                    )
                }
            }
            groups.forEach { group ->
                val key = group.sub?.id ?: "local"
                item(key = "h-$key") {
                    GroupHeader(
                        group = group,
                        index = group.sub?.let { s -> data.subscriptions.indexOfFirst { it.id == s.id } } ?: -1,
                        count = data.subscriptions.size,
                        onToggle = {
                            if (group.sub == null) vm.toggleLocalCollapsed()
                            else vm.updateSubscription(group.sub.id) { it.copy(collapsed = !it.collapsed) }
                        },
                        onPing = { vm.ping(group.profiles) },
                        onRename = { renaming = group.sub },
                        onMove = { from, to -> vm.moveSubscription(from, to) },
                        onHide = { group.sub?.let { s -> vm.updateSubscription(s.id) { it.copy(showOnHome = false) } } },
                        modifier = Modifier.animateItem(),
                    )
                }
                if (!group.collapsed) {
                    itemsIndexed(group.profiles, key = { _, p -> p.id }) { i, p ->
                        Column(Modifier.animateItem()) {
                            SegmentGap()
                            // The header is segment 0 of the group, servers follow it
                            ProfileRow(
                                profile = p,
                                selected = p.id == data.selectedId,
                                ping = pings[p.id],
                                shape = segmentShape(i + 1, group.profiles.size + 1),
                                onClick = { vm.select(p) },
                                onPing = { vm.ping(listOf(p)) },
                                onEdit = { editing = p },
                                onShare = { context.shareText(p.link, p.name) },
                                onDelete = if (p.subId == null) ({ vm.deleteProfile(p) }) else null,
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { p ->
        ProfileEditor(
            profile = p,
            onDismiss = { editing = null },
            onSave = { vm.saveProfile(it); editing = null },
        )
    }
    renaming?.let { sub ->
        RenameDialog(
            initial = sub.name,
            onDismiss = { renaming = null },
            onSave = { name -> vm.updateSubscription(sub.id) { it.copy(name = name) }; renaming = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupHeader(
    group: Group,
    index: Int,
    count: Int,
    onToggle: () -> Unit,
    onPing: () -> Unit,
    onRename: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val arrow by animateFloatAsState(
        if (group.collapsed) -90f else 0f,
        MaterialTheme.motionScheme.fastSpatialSpec(), label = "arrow",
    )
    // Section card: fully rounded when collapsed, first segment of the group when expanded
    val bottomCorner by animateDpAsState(
        if (group.collapsed) 24.dp else 6.dp,
        MaterialTheme.motionScheme.fastSpatialSpec(), label = "corner",
    )
    val colors = MaterialTheme.colorScheme
    Box(modifier.padding(top = 16.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = bottomCorner, bottomEnd = bottomCorner))
                .background(colors.surfaceContainerHigh)
                .combinedClickable(
                    onClick = onToggle,
                    onLongClick = if (group.sub != null) ({ menu = true }) else null,
                )
                .padding(start = 20.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                group.title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onPing, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Rounded.NetworkPing, contentDescription = "Test latency", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(colors.secondaryContainer)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            ) {
                Text(
                    group.profiles.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSecondaryContainer,
                )
            }
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = if (group.collapsed) "Expand" else "Collapse",
                tint = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp).size(22.dp).rotate(arrow),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                onClick = { menu = false; onRename() },
            )
            if (index > 0) DropdownMenuItem(
                text = { Text("Move up") },
                leadingIcon = { Icon(Icons.Rounded.ArrowUpward, null) },
                onClick = { menu = false; onMove(index, index - 1) },
            )
            if (index in 0 until count - 1) DropdownMenuItem(
                text = { Text("Move down") },
                leadingIcon = { Icon(Icons.Rounded.ArrowDownward, null) },
                onClick = { menu = false; onMove(index, index + 1) },
            )
            DropdownMenuItem(
                text = { Text("Hide from Home") },
                leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null) },
                onClick = { menu = false; onHide() },
            )
        }
    }
}

@Composable
fun RenameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = { OutlinedTextField(value, { value = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onSave(value.trim()) }, enabled = value.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Hero(state: VpnState, vm: MainViewModel, enabled: Boolean, onToggle: () -> Unit) {
    val visual = when (state) {
        is VpnState.Connected -> ConnectVisual.Connected
        VpnState.Connecting, VpnState.Stopping -> ConnectVisual.Busy
        else -> ConnectVisual.Idle
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ConnectButton(visual = visual, enabled = enabled, onClick = onToggle)
        Spacer(Modifier.height(24.dp))
        AnimatedContent(
            targetState = when (state) {
                is VpnState.Connected -> "Connected"
                VpnState.Connecting -> "Connecting"
                VpnState.Stopping -> "Disconnecting"
                else -> "Disconnected"
            },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "status",
        ) { label ->
            Text(label, style = MaterialTheme.typography.headlineSmallEmphasized)
        }
        Box(
            Modifier.heightIn(min = 28.dp).animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                is VpnState.Connected -> ConnectedStats(state.since, vm)
                is VpnState.Error -> Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                else -> Unit
            }
        }
    }
}

@Composable
private fun ConnectedStats(since: Long, vm: MainViewModel) {
    val traffic by vm.traffic.collectAsStateWithLifecycle()
    val country by vm.country.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val parts = data.settings.countryParts
    val place = country?.let { c ->
        listOfNotNull(
            c.flag.takeIf { CountryPart.Flag in parts && it.isNotEmpty() },
            c.code.takeIf { CountryPart.Code in parts },
            c.name.takeIf { CountryPart.Name in parts },
        ).joinToString(" ").ifBlank { null }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (place != null) {
            Text(
                place,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Text(
            listOf(formatDuration(now - since), "↑ ${formatSpeed(traffic.up)}", "↓ ${formatSpeed(traffic.down)}")
                .joinToString("   "),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileRow(
    profile: Profile,
    selected: Boolean,
    ping: Int?,
    shape: Shape,
    onClick: () -> Unit,
    onPing: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) colors.primaryContainer else colors.surfaceContainer,
        MaterialTheme.motionScheme.defaultEffectsSpec(), label = "row",
    )
    val onContainer = if (selected) colors.onPrimaryContainer else colors.onSurface
    var menu by remember { mutableStateOf(false) }

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(container)
                .combinedClickable(onClick = onClick, onLongClick = onDelete?.let { { menu = true } })
                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val markColor by animateColorAsState(
                if (selected) colors.primary else colors.surfaceContainerHighest,
                MaterialTheme.motionScheme.defaultEffectsSpec(), label = "mark",
            )
            val markScale by animateFloatAsState(
                if (selected) 1f else 0.78f,
                MaterialTheme.motionScheme.fastSpatialSpec(), label = "markScale",
            )
            Box(
                Modifier
                    .size(32.dp)
                    .graphicsLayer { scaleX = markScale; scaleY = markScale }
                    .clip(MaterialShapes.Cookie12Sided.toShape())
                    .background(markColor),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.animation.AnimatedVisibility(visible = selected, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    Icon(Icons.Rounded.Check, null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name.trim(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOfNotNull(profile.protocol, profile.transport, profile.security).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainer.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                    AnimatedVisibility(visible = ping != null, enter = fadeIn(), exit = fadeOut()) {
                        PingBadge(ping ?: 0)
                    }
                }
                profile.description?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainer.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onPing, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Rounded.NetworkPing, contentDescription = "Test latency", tint = onContainer.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onEdit, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = onContainer.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onShare, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Share, contentDescription = "Share", tint = onContainer.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                onClick = { menu = false; onDelete?.invoke() },
            )
        }
    }
}

@Composable
private fun PingBadge(ms: Int) {
    val colors = MaterialTheme.colorScheme
    val mod = Modifier.padding(start = 8.dp)
    when {
        ms == 0 -> CircularWavyProgressIndicator(modifier = mod.size(14.dp))
        ms < 0 -> Text("· timeout", style = MaterialTheme.typography.bodySmall, color = colors.error, modifier = mod)
        else -> Text(
            "· $ms ms",
            style = MaterialTheme.typography.bodySmall,
            color = when {
                ms < 300 -> colors.primary
                ms < 800 -> colors.tertiary
                else -> colors.error
            },
            modifier = mod,
        )
    }
}

@Composable
private fun EmptyState(onAddSubscription: () -> Unit, onPaste: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(88.dp)
                .clip(MaterialShapes.Cookie12Sided.toShape())
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Layers, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("No configurations", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAddSubscription, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Add, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Subscription")
            }
            OutlinedButton(onClick = onPaste, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.ContentPaste, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Paste")
            }
        }
    }
}
