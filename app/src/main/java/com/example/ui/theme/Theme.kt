package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val WasteCrushColorScheme = lightColorScheme(
    primary = SidiCobalt,
    onPrimary = Color.White,
    primaryContainer = SidiSkyLight,
    onPrimaryContainer = DeepNavyInk,
    secondary = EcoEmerald,
    onSecondary = Color.White,
    secondaryContainer = EcoMintSoft,
    onSecondaryContainer = DeepNavyInk,
    tertiary = SaffronGold,
    onTertiary = DeepNavyInk,
    tertiaryContainer = SaffronCream,
    onTertiaryContainer = DeepNavyInk,
    error = CarthageCoral,
    onError = Color.White,
    background = LimestoneBg,
    onBackground = DeepNavyInk,
    surface = SurfaceWhite,
    onSurface = DeepNavyInk,
    surfaceVariant = SurfaceSoftBlue,
    onSurfaceVariant = MutedSlateText,
    outline = DeepNavyInk
)

val WasteCrushShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun WasteCrushTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WasteCrushColorScheme,
        typography = WasteCrushTypography,
        shapes = WasteCrushShapes,
        content = content
    )
}
