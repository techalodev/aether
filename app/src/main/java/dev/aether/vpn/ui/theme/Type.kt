package dev.aether.vpn.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.aether.vpn.R

private fun flex(weight: Int) = Font(
    R.font.google_sans_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val GoogleSansFlex = FontFamily(flex(300), flex(400), flex(500), flex(600), flex(700), flex(800))

private fun TextStyle.flex() = copy(fontFamily = GoogleSansFlex)

val AetherTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.flex(),
        displayMedium = displayMedium.flex(),
        displaySmall = displaySmall.flex(),
        headlineLarge = headlineLarge.flex(),
        headlineMedium = headlineMedium.flex(),
        headlineSmall = headlineSmall.flex(),
        titleLarge = titleLarge.flex(),
        titleMedium = titleMedium.flex(),
        titleSmall = titleSmall.flex(),
        bodyLarge = bodyLarge.flex(),
        bodyMedium = bodyMedium.flex(),
        bodySmall = bodySmall.flex(),
        labelLarge = labelLarge.flex(),
        labelMedium = labelMedium.flex(),
        labelSmall = labelSmall.flex(),
        displayLargeEmphasized = displayLargeEmphasized.flex(),
        displayMediumEmphasized = displayMediumEmphasized.flex(),
        displaySmallEmphasized = displaySmallEmphasized.flex(),
        headlineLargeEmphasized = headlineLargeEmphasized.flex(),
        headlineMediumEmphasized = headlineMediumEmphasized.flex(),
        headlineSmallEmphasized = headlineSmallEmphasized.flex(),
        titleLargeEmphasized = titleLargeEmphasized.flex(),
        titleMediumEmphasized = titleMediumEmphasized.flex(),
        titleSmallEmphasized = titleSmallEmphasized.flex(),
        bodyLargeEmphasized = bodyLargeEmphasized.flex(),
        bodyMediumEmphasized = bodyMediumEmphasized.flex(),
        bodySmallEmphasized = bodySmallEmphasized.flex(),
        labelLargeEmphasized = labelLargeEmphasized.flex(),
        labelMediumEmphasized = labelMediumEmphasized.flex(),
        labelSmallEmphasized = labelSmallEmphasized.flex(),
    )
}
