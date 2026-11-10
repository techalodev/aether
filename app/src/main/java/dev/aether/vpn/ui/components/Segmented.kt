package dev.aether.vpn.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

private val Outer = 24.dp
private val Inner = 6.dp

/** Expressive "connected" list shape: large outer corners, small inner corners. */
fun segmentShape(index: Int, count: Int): Shape {
    val top = if (index == 0) Outer else Inner
    val bottom = if (index == count - 1) Outer else Inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** Gap between segments of a connected group. */
@Composable
fun SegmentGap() = Spacer(Modifier.height(2.dp))

@Composable
fun SegmentedGroup(
    title: String?,
    modifier: Modifier = Modifier,
    items: List<@Composable (Shape) -> Unit>,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) SectionHeader(title)
        items.forEachIndexed { i, item ->
            if (i > 0) SegmentGap()
            item(segmentShape(i, items.size))
        }
    }
}
