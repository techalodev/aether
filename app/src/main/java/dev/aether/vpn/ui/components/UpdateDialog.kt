package dev.aether.vpn.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aether.vpn.BuildConfig
import dev.aether.vpn.core.Updater
import dev.aether.vpn.ui.formatBytes

/** Release notes + download progress for an available update. */
@Composable
fun UpdateDialog(
    state: Updater.State,
    onUpdate: (Updater.Release) -> Unit,
    onInstall: (java.io.File) -> Unit,
    onDismiss: () -> Unit,
) {
    val release = when (state) {
        is Updater.State.Available -> state.release
        is Updater.State.Downloading -> state.release
        is Updater.State.Ready -> state.release
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.SystemUpdate, null) },
        title = { Text(if (release != null) "Aether ${release.version}" else "Update") },
        text = {
            Column {
                when (state) {
                    is Updater.State.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error)
                    else -> release?.let { r ->
                        Text(
                            "${BuildConfig.VERSION_NAME} → ${r.version} · ${formatBytes(r.size)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (r.notes.isNotBlank()) {
                            Spacer(Modifier.height(16.dp))
                            Text("What's new", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                r.notes
                                    .replace(Regex("(?m)^#+\\s*"), "")
                                    .replace("**", "")
                                    .replace(Regex("(?m)^\\s*[-*]\\s+"), "• "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
                if (state is Updater.State.Downloading) {
                    Spacer(Modifier.height(16.dp))
                    LinearWavyProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            when (state) {
                is Updater.State.Available -> TextButton(onClick = { onUpdate(state.release) }) { Text("Update") }
                is Updater.State.Ready -> TextButton(onClick = { onInstall(state.file) }) { Text("Install") }
                is Updater.State.Downloading -> TextButton(onClick = {}, enabled = false) { Text("Downloading…") }
                else -> TextButton(onClick = onDismiss) { Text("OK") }
            }
        },
        dismissButton = {
            if (state !is Updater.State.Failed) TextButton(onClick = onDismiss) { Text("Later") }
        },
    )
}
