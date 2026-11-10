package dev.aether.vpn.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class ConnectVisual { Idle, Busy, Connected }

@Composable
fun ConnectButton(
    visual: ConnectVisual,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val shape = MaterialShapes.Cookie12Sided.toShape()

    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.9f
            visual == ConnectVisual.Connected -> 1.04f
            else -> 1f
        },
        animationSpec = motion.fastSpatialSpec(),
        label = "scale",
    )
    val container by animateColorAsState(
        targetValue = when (visual) {
            ConnectVisual.Idle -> colors.surfaceContainerHighest
            ConnectVisual.Busy -> colors.secondaryContainer
            ConnectVisual.Connected -> colors.primary
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "container",
    )
    val content by animateColorAsState(
        targetValue = when (visual) {
            ConnectVisual.Idle -> colors.onSurfaceVariant
            ConnectVisual.Busy -> colors.onSecondaryContainer
            ConnectVisual.Connected -> colors.onPrimary
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "content",
    )

    val spin = rememberInfiniteTransition(label = "spin")
    val rotation by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(if (visual == ConnectVisual.Busy) 4_000 else 30_000, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "rotation",
    )

    Box(
        modifier = modifier
            .size(208.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics { contentDescription = if (visual == ConnectVisual.Idle) "Connect" else "Disconnect" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = if (visual == ConnectVisual.Idle) 0f else rotation }
                .clip(shape)
                .background(container)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = {
                        haptics.performHapticFeedback(
                            if (visual == ConnectVisual.Idle) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
                        )
                        onClick()
                    },
                )
        )
        if (visual == ConnectVisual.Busy) {
            LoadingIndicator(color = content, modifier = Modifier.size(72.dp))
        } else {
            Icon(
                Icons.Rounded.PowerSettingsNew,
                contentDescription = null,
                tint = if (enabled) content else content.copy(alpha = 0.38f),
                modifier = Modifier.size(64.dp),
            )
        }
    }
}
