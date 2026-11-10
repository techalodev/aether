package dev.aether.vpn.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private val ItemWidth = 64.dp
private val BarHeight = 52.dp
private val IndicatorShape = RoundedCornerShape(11.dp)

data class NavItem(val label: String, val selectedIcon: ImageVector, val icon: ImageVector)

/**
 * Small floating navigation bar with icon-only tabs. The selection indicator slides between
 * tabs; with [expandSelected] the selected tab instead widens to reveal its label.
 */
@Composable
fun FloatingNavBar(items: List<NavItem>, selected: Int, expandSelected: Boolean, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceContainer,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
        ) {
            if (expandSelected) {
                Row(Modifier.height(BarHeight).padding(5.dp)) {
                    items.forEachIndexed { index, item ->
                        NavBarItem(item, index == selected, showLabel = true, Modifier.fillMaxHeight()) { onSelect(index) }
                    }
                }
            } else {
                Box(Modifier.width(ItemWidth * items.size).height(BarHeight)) {
                    val indicatorX by animateDpAsState(ItemWidth * selected, motion.defaultSpatialSpec(), label = "indicator")
                    Box(
                        Modifier
                            .offset(x = indicatorX)
                            .width(ItemWidth)
                            .fillMaxHeight()
                            .padding(5.dp)
                            .background(colors.secondaryContainer, IndicatorShape),
                    )
                    Row(Modifier.fillMaxHeight()) {
                        items.forEachIndexed { index, item ->
                            NavBarItem(item, index == selected, showLabel = false, Modifier.width(ItemWidth).fillMaxHeight()) { onSelect(index) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavBarItem(item: NavItem, selected: Boolean, showLabel: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.85f else 1f, motion.fastSpatialSpec(), label = "press")
    val tint by animateColorAsState(
        if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        motion.defaultEffectsSpec(), label = "tint",
    )
    // In expanding mode each tab draws its own indicator, since tab widths change
    val container by animateColorAsState(
        if (showLabel && selected) colors.secondaryContainer else colors.secondaryContainer.copy(alpha = 0f),
        motion.defaultEffectsSpec(), label = "container",
    )
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .widthIn(min = if (showLabel) 54.dp else 0.dp)
            .clip(IndicatorShape)
            .background(container)
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            }
            .padding(horizontal = if (showLabel) 15.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(
            if (selected) item.selectedIcon else item.icon,
            contentDescription = item.label,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        if (showLabel) {
            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkHorizontally(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = tint,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
