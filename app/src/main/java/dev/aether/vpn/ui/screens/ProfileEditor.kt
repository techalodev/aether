package dev.aether.vpn.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.aether.vpn.core.LinkParser
import dev.aether.vpn.core.LinkWriter
import dev.aether.vpn.core.Node
import dev.aether.vpn.core.Protocol
import dev.aether.vpn.core.SubscriptionFetcher
import dev.aether.vpn.core.XrayConfig
import dev.aether.vpn.data.Profile
import dev.aether.vpn.ui.components.SectionHeader
import org.json.JSONObject

@Composable
fun ProfileEditor(profile: Profile, onDismiss: () -> Unit, onSave: (Profile) -> Unit) {
    val isJson = XrayConfig.isJson(profile.link)
    var name by remember { mutableStateOf(profile.name) }
    var node by remember { mutableStateOf(if (isJson) null else LinkParser.parse(profile.link)) }
    var raw by remember { mutableStateOf(if (isJson) runCatching { JSONObject(profile.link).toString(2) }.getOrDefault(profile.link) else profile.link) }
    var rawMode by remember { mutableStateOf(isJson || node == null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        error = null
        if (isJson) {
            val obj = runCatching { JSONObject(raw) }.getOrNull() ?: run { error = "Invalid JSON"; return }
            obj.put("remarks", name)
            onSave(profile.copy(name = name.ifBlank { profile.name }, link = obj.toString()))
            return
        }
        val parsed = if (rawMode) LinkParser.parse(raw) else node?.copy(name = name)
        if (parsed == null) {
            error = "Invalid link"
            return
        }
        val final = parsed.copy(name = name.ifBlank { parsed.name })
        onSave(
            profile.copy(
                name = final.name,
                link = LinkWriter.write(final),
                protocol = final.protocol.label,
                address = final.address,
                port = final.port,
                transport = SubscriptionFetcher.transportLabel(final.network),
                security = SubscriptionFetcher.securityLabel(final.security),
            )
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Edit") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
                    },
                    actions = { TextButton(onClick = ::save) { Text("Save") } },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                if (!isJson) {
                    Choice(
                        options = listOf("Fields", "Link"),
                        selected = if (rawMode) "Link" else "Fields",
                    ) { choice ->
                        if (choice == "Link" && !rawMode) {
                            node?.let { raw = LinkWriter.write(it.copy(name = name)) }
                            rawMode = true
                        } else if (choice == "Fields" && rawMode) {
                            val parsed = LinkParser.parse(raw)
                            if (parsed != null) {
                                node = parsed
                                name = parsed.name
                                rawMode = false
                                error = null
                            } else error = "Invalid link"
                        }
                    }
                }

                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

                val n = node
                if (rawMode || n == null) {
                    OutlinedTextField(
                        value = raw,
                        onValueChange = { raw = it },
                        label = { Text(if (isJson) "Xray JSON" else "Link") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                    )
                } else {
                    NodeFields(n) { node = it }
                }
            }
        }
    }
}

@Composable
private fun NodeFields(n: Node, onChange: (Node) -> Unit) {
    SectionHeader("Server", Modifier.padding(start = 0.dp))
    Field("Address", n.address) { onChange(n.copy(address = it.trim())) }
    Field("Port", n.port.toString(), KeyboardType.Number) { v -> v.toIntOrNull()?.let { onChange(n.copy(port = it)) } }
    Field(if (n.protocol == Protocol.VLESS || n.protocol == Protocol.VMESS) "UUID" else "Password", n.secret) {
        onChange(n.copy(secret = it.trim()))
    }
    when (n.protocol) {
        Protocol.VLESS -> {
            Field("Encryption", n.method ?: "none") { onChange(n.copy(method = it.trim())) }
            Field("Flow", n.flow.orEmpty()) { onChange(n.copy(flow = it.trim().ifBlank { null })) }
        }
        Protocol.VMESS -> Field("Security", n.method ?: "auto") { onChange(n.copy(method = it.trim())) }
        Protocol.SHADOWSOCKS -> Field("Method", n.method.orEmpty()) { onChange(n.copy(method = it.trim())) }
        Protocol.TROJAN -> Unit
        Protocol.HYSTERIA2 -> {
            Field("Obfs password", n.obfsPassword.orEmpty()) { onChange(n.copy(obfsPassword = it.ifBlank { null })) }
            Field("Port hopping", n.portHopping.orEmpty()) { onChange(n.copy(portHopping = it.trim().ifBlank { null })) }
        }
    }

    val hasTransport = n.protocol != Protocol.SHADOWSOCKS && n.protocol != Protocol.HYSTERIA2
    if (hasTransport) {
        SectionHeader("Transport", Modifier.padding(start = 0.dp))
        Choice(listOf("tcp", "ws", "grpc", "xhttp", "httpupgrade"), n.network) { onChange(n.copy(network = it)) }
        Field("Host", n.host.orEmpty()) { onChange(n.copy(host = it.trim().ifBlank { null })) }
        if (n.network == "grpc") {
            Field("Service name", n.serviceName.orEmpty()) { onChange(n.copy(serviceName = it.trim().ifBlank { null })) }
        } else {
            Field("Path", n.path.orEmpty()) { onChange(n.copy(path = it.trim().ifBlank { null })) }
        }
        if (n.network == "grpc" || n.network == "xhttp") {
            Field("Mode", n.mode.orEmpty()) { onChange(n.copy(mode = it.trim().ifBlank { null })) }
        }
    }

    if (n.protocol != Protocol.SHADOWSOCKS) {
        SectionHeader("Security", Modifier.padding(start = 0.dp))
        if (hasTransport) {
            Choice(listOf("none", "tls", "reality"), n.security ?: "none") {
                onChange(n.copy(security = it.takeIf { s -> s != "none" }))
            }
        }
        val security = if (n.protocol == Protocol.HYSTERIA2) "tls" else n.security
        if (security != null && security != "none") {
            Field("SNI", n.sni.orEmpty()) { onChange(n.copy(sni = it.trim().ifBlank { null })) }
            Field("Fingerprint", n.fingerprint.orEmpty()) { onChange(n.copy(fingerprint = it.trim().ifBlank { null })) }
            if (security == "reality") {
                Field("Public key", n.publicKey.orEmpty()) { onChange(n.copy(publicKey = it.trim().ifBlank { null })) }
                Field("Short ID", n.shortId.orEmpty()) { onChange(n.copy(shortId = it.trim().ifBlank { null })) }
                Field("SpiderX", n.spiderX.orEmpty()) { onChange(n.copy(spiderX = it.trim().ifBlank { null })) }
            } else {
                Field("ALPN", n.alpn.orEmpty()) { onChange(n.copy(alpn = it.trim().ifBlank { null })) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Allow insecure", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = n.insecure, onCheckedChange = { onChange(n.copy(insecure = it)) })
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Connected button group for picking one option (M3 Expressive). */
@Composable
fun Choice(options: List<String>, selected: String, modifier: Modifier = Modifier, label: (String) -> String = { it }, onSelect: (String) -> Unit) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { i, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(label(option), maxLines = 1, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
