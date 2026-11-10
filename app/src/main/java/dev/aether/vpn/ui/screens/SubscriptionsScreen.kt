package dev.aether.vpn.ui.screens

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.togetherWith
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.graphicsLayer
import dev.aether.vpn.data.RefreshReport
import dev.aether.vpn.data.Subscription
import dev.aether.vpn.ui.MainViewModel
import dev.aether.vpn.ui.components.SectionHeader
import dev.aether.vpn.ui.components.SegmentGap
import dev.aether.vpn.ui.components.segmentShape
import dev.aether.vpn.ui.formatBytes
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private data class SheetTarget(val sub: Subscription?)

@Composable
fun SubscriptionsScreen(vm: MainViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var sheet by remember { mutableStateOf<SheetTarget?>(null) }
    var details by remember { mutableStateOf<List<RefreshReport>?>(null) }

    // One popup per updated subscription, shown one after another
    val queue = remember { mutableStateListOf<RefreshReport>() }
    LaunchedEffect(Unit) {
        vm.reports.collect { reports -> if (vm.data.value.settings.updatePopup) queue.addAll(reports) }
    }

    // Local copy so drag-and-drop feels instant; persisted on every move
    var list by remember { mutableStateOf(data.subscriptions) }
    LaunchedEffect(data.subscriptions) { list = data.subscriptions }
    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val f = list.indexOfFirst { it.id == from.key }
        val t = list.indexOfFirst { it.id == to.key }
        if (f >= 0 && t >= 0) {
            list = list.toMutableList().apply { add(t, removeAt(f)) }
            vm.moveSubscription(f, t)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Subscriptions") },
                actions = {
                    if (data.subscriptions.isNotEmpty()) {
                        IconButton(onClick = { vm.refreshAll() }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Update all")
                        }
                    }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { sheet = SheetTarget(null) }) {
                Icon(Icons.Rounded.Add, contentDescription = "Add subscription")
            }
        },
        snackbarHost = {
            RefreshPopup(
                report = queue.firstOrNull(),
                seconds = data.settings.updatePopupSeconds,
                onDone = { if (queue.isNotEmpty()) queue.removeAt(0) },
                onOpen = { r ->
                    details = listOf(r)
                    queue.remove(r)
                },
            )
        },
    ) { padding ->
        if (data.subscriptions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(88.dp).clip(MaterialShapes.Cookie12Sided.toShape())
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Layers, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(36.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("No subscriptions", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = 120.dp,
                ),
            ) {
                itemsIndexed(list, key = { _, s -> s.id }) { i, sub ->
                    ReorderableItem(reorder, key = sub.id) { dragging ->
                        Column {
                            if (i > 0) SegmentGap()
                            SubscriptionCard(
                                sub = sub,
                                count = data.profiles.count { it.subId == sub.id },
                                refreshing = sub.id in refreshing,
                                dragging = dragging,
                                shape = segmentShape(i, list.size),
                                handle = { Modifier.draggableHandle() },
                                onRefresh = { vm.refresh(sub.id, manual = true) },
                                onEdit = { sheet = SheetTarget(sub) },
                                onToggleHome = { vm.updateSubscription(sub.id) { it.copy(showOnHome = !it.showOnHome) } },
                                onDelete = { vm.deleteSubscription(sub.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    sheet?.let { target ->
        SubscriptionSheet(
            initial = target.sub,
            globalUserAgent = data.settings.userAgent,
            globalHwid = data.settings.sendHwid,
            onDismiss = { sheet = null },
            onSave = { sub ->
                if (target.sub == null) vm.addSubscription(sub.name, sub.url) else vm.saveSubscription(sub)
                sheet = null
            },
        )
    }
    details?.let { DiffSheet(it) { details = null } }
}

// ---- card ---------------------------------------------------------------------------------------

@Composable
private fun SubscriptionCard(
    sub: Subscription,
    count: Int,
    refreshing: Boolean,
    dragging: Boolean,
    shape: Shape,
    handle: @Composable () -> Modifier,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onToggleHome: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    var expanded by rememberSaveable(sub.id) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (dragging) colors.surfaceContainerHighest else colors.surfaceContainer)
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.DragHandle,
                contentDescription = "Reorder",
                tint = colors.onSurfaceVariant,
                modifier = handle().padding(12.dp).size(20.dp),
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        sub.name.ifBlank { "Subscription" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!sub.showOnHome) {
                        Icon(
                            Icons.Rounded.VisibilityOff, contentDescription = "Hidden from Home",
                            tint = colors.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp).size(16.dp),
                        )
                    }
                }
                val updated = if (sub.updatedAt == 0L) "Never updated" else
                    DateUtils.getRelativeTimeSpanString(sub.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                Text("$count configs · $updated", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            AnimatedContent(targetState = refreshing, label = "refresh") { busy ->
                if (busy) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(32.dp)) }
                } else {
                    IconButton(onClick = onRefresh, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Update")
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = true }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (sub.showOnHome) "Hide from Home" else "Show on Home") },
                        leadingIcon = { Icon(if (sub.showOnHome) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null) },
                        onClick = { menu = false; onToggleHome() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }

        val body = Modifier.padding(start = 44.dp, end = 16.dp)
        sub.description?.takeIf { it.isNotBlank() }?.let { text ->
            Text(
                text.trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = body.padding(top = 8.dp).clip(MaterialTheme.shapes.small).clickable { expanded = !expanded },
            )
        }

        val used = sub.upload + sub.download
        val daysLeft = if (sub.expire > 0) ((sub.expire * 1000 - System.currentTimeMillis()) / DateUtils.DAY_IN_MILLIS) else null
        if (sub.total > 0 || daysLeft != null || used > 0) {
            Column(body.padding(top = 12.dp)) {
                if (sub.total > 0) {
                    val fraction = (used.toFloat() / sub.total).coerceIn(0f, 1f)
                    LinearWavyProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (fraction > 0.9f) colors.error else colors.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Row {
                    Text(
                        when {
                            sub.total > 0 -> "${formatBytes(used)} / ${formatBytes(sub.total)}"
                            used > 0 -> "${formatBytes(used)} · unlimited"
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (daysLeft != null) {
                        Text(
                            when {
                                daysLeft < 0 -> "Expired"
                                daysLeft == 0L -> "Expires today"
                                else -> "$daysLeft days"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (daysLeft < 3) colors.error else colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ---- add / edit sheet ---------------------------------------------------------------------------

private val INTERVALS = listOf(0, 1, 3, 6, 12, 24)

@Composable
private fun SubscriptionSheet(
    initial: Subscription?,
    globalUserAgent: String,
    globalHwid: Boolean,
    onDismiss: () -> Unit,
    onSave: (Subscription) -> Unit,
) {
    val context = LocalContext.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var url by rememberSaveable { mutableStateOf(initial?.url.orEmpty()) }
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var interval by rememberSaveable { mutableStateOf(initial?.autoUpdateHours ?: 12) }
    var userAgent by rememberSaveable { mutableStateOf(initial?.userAgent.orEmpty()) }
    var hwid by rememberSaveable { mutableStateOf(initial?.sendHwid?.let { if (it) "On" else "Off" } ?: "Default") }
    val valid = url.trim().let { it.startsWith("http://") || it.startsWith("https://") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (initial == null) "New subscription" else "Edit subscription",
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                trailingIcon = {
                    IconButton(onClick = { context.clipboardText()?.let { url = it.trim() } }) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(if (initial == null) "Name (optional)" else "Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (initial != null) {
                SectionHeader("Auto-update", Modifier.padding(start = 0.dp))
                Choice(
                    options = INTERVALS.map { it.toString() },
                    selected = interval.toString(),
                    label = { if (it == "0") "Off" else "${it}h" },
                ) { interval = it.toInt() }

                SectionHeader("Request", Modifier.padding(start = 0.dp))
                OutlinedTextField(
                    value = userAgent,
                    onValueChange = { userAgent = it },
                    label = { Text("User-Agent") },
                    placeholder = { Text(globalUserAgent) },
                    supportingText = { Text("Empty = global") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                UserAgentPresets(current = userAgent) { userAgent = it }
                Text("Send HWID", style = MaterialTheme.typography.bodyLarge)
                Choice(
                    options = listOf("Default", "On", "Off"),
                    selected = hwid,
                    label = { if (it == "Default") "Global (${if (globalHwid) "on" else "off"})" else it },
                ) { hwid = it }
                if (userAgent.isNotBlank() || hwid != "Default") {
                    OutlinedButton(
                        onClick = { userAgent = ""; hwid = "Default" },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Reset to global") }
                }
            }

            Button(
                onClick = {
                    val base = initial ?: Subscription(id = "", name = "", url = "")
                    onSave(
                        base.copy(
                            name = name.trim(),
                            url = url.trim(),
                            autoUpdateHours = interval,
                            userAgent = userAgent.trim().ifBlank { null },
                            sendHwid = when (hwid) { "On" -> true; "Off" -> false; else -> null },
                        )
                    )
                },
                enabled = valid,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Save") }
        }
    }
}

// ---- refresh popup ------------------------------------------------------------------------------

/**
 * Result of one subscription update: a themed card with a countdown strip on top.
 * Swipe sideways to dismiss, tap to see what changed.
 */
@Composable
private fun RefreshPopup(report: RefreshReport?, seconds: Int, onDone: () -> Unit, onOpen: (RefreshReport) -> Unit) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = report,
        transitionSpec = {
            (slideInVertically(motion.defaultSpatialSpec()) { it } + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.85f) + fadeIn(motion.defaultEffectsSpec()))
                .togetherWith(slideOutVertically(motion.fastSpatialSpec()) { it / 2 } + scaleOut(motion.fastSpatialSpec(), targetScale = 0.9f) + fadeOut(motion.fastEffectsSpec()))
        },
        contentKey = { it?.hashCode() },
        label = "popup",
    ) { r ->
        if (r == null) {
            Box(Modifier.fillMaxWidth())
            return@AnimatedContent
        }
        val countdown = remember(r) { Animatable(1f) }
        val swipe = rememberSwipeToDismissBoxState()
        LaunchedEffect(r) {
            countdown.animateTo(0f, tween(seconds * 1000, easing = LinearEasing))
            onDone()
        }
        LaunchedEffect(swipe.currentValue) {
            if (swipe.currentValue != SwipeToDismissBoxValue.Settled) onDone()
        }

        val colors = MaterialTheme.colorScheme
        val failed = r.error != null
        val accent = if (failed) colors.error else colors.primary
        SwipeToDismissBox(
            state = swipe,
            backgroundContent = {},
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = colors.surfaceContainerHigh,
                contentColor = colors.onSurface,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onOpen(r) },
            ) {
                Column {
                    LinearWavyProgressIndicator(
                        progress = { countdown.value },
                        color = accent,
                        trackColor = colors.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp),
                    )
                    Row(
                        Modifier.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .graphicsLayer { rotationZ = (1f - countdown.value) * 120f }
                                .clip(MaterialShapes.Cookie12Sided.toShape())
                                .background(if (failed) colors.errorContainer else colors.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (failed) Icons.Rounded.ErrorOutline else Icons.Rounded.Check,
                                contentDescription = null,
                                tint = if (failed) colors.onErrorContainer else colors.onPrimaryContainer,
                                modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = -(1f - countdown.value) * 120f },
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.subName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                when {
                                    failed -> r.error.orEmpty()
                                    !r.hasChanges -> "Up to date"
                                    else -> listOfNotNull(
                                        "+${r.added.size} added".takeIf { r.added.isNotEmpty() },
                                        "−${r.removed.size} removed".takeIf { r.removed.isNotEmpty() },
                                        "${r.updated.size} updated".takeIf { r.updated.isNotEmpty() },
                                    ).joinToString(" · ")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (failed) colors.error else colors.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "Details", tint = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}


@Composable
private fun DiffSheet(reports: List<RefreshReport>, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            reports.forEach { r ->
                item(key = "t-${r.subName}-${r.hashCode()}") {
                    Text(r.subName, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    when {
                        r.error != null -> Text(r.error, color = colors.error, style = MaterialTheme.typography.bodyMedium)
                        !r.hasChanges -> Text("No changes", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                listOf(
                    Triple("Added", r.added, colors.primary),
                    Triple("Removed", r.removed, colors.error),
                    Triple("Updated", r.updated, colors.tertiary),
                ).filter { it.second.isNotEmpty() }.forEach { (title, names, color) ->
                    item(key = "s-${r.hashCode()}-$title") {
                        Text(
                            "$title · ${names.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = color,
                            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                        )
                    }
                    itemsIndexed(names, key = { i, _ -> "n-${r.hashCode()}-$title-$i" }) { _, n ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).clip(MaterialShapes.Cookie12Sided.toShape()).background(color))
                            Spacer(Modifier.width(12.dp))
                            Text(n.trim(), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
