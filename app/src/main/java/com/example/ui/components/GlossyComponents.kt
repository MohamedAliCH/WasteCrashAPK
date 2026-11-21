package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.BOARD_SIZE
import com.example.engine.BoardTile
import com.example.engine.GridPos
import com.example.engine.SpecialTileType
import com.example.model.BossDefinition
import com.example.model.BossRarity
import com.example.model.GameCatalog
import com.example.model.WasteType
import com.example.ui.theme.*
import kotlin.math.abs

@Composable
fun GlossyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    topColor: Color = LimePlayTop,
    bottomColor: Color = LimePlayBottom,
    lipColor: Color = LimePlayLip,
    textColor: Color = Color.White,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    tag: String? = null
) {
    val actualTop = if (enabled) topColor else Color(0xFF94A3B8)
    val actualBottom = if (enabled) bottomColor else Color(0xFF64748B)
    val actualLip = if (enabled) lipColor else Color(0xFF475569)

    Box(
        modifier = modifier
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .height(height)
            .shadow(elevation = if (enabled) 6.dp else 1.dp, shape = RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(actualLip)
            .border(2.5.dp, DeepNavyInk, RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(bottom = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(17.dp))
                .background(Brush.verticalGradient(listOf(actualTop, actualBottom))),
            contentAlignment = Alignment.Center
        ) {
            // Glossy curved highlight sheen across top edge
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(0.92f)
                    .padding(top = 3.dp)
                    .height(height * 0.30f)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.34f))
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.7.sp
                    ),
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun GlossyIconTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    topColor: Color = CeramicBlueTop,
    bottomColor: Color = CeramicBlueBottom,
    lipColor: Color = CeramicBlueLip,
    badgeText: String? = null,
    tag: String? = null
) {
    Column(
        modifier = modifier
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .shadow(5.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(lipColor)
                    .border(2.5.dp, DeepNavyInk, RoundedCornerShape(18.dp))
                    .padding(bottom = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(15.dp))
                        .background(Brush.verticalGradient(listOf(topColor, bottomColor))),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(0.84f)
                            .padding(top = 3.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.32f))
                    )
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            if (!badgeText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .offset(x = 5.dp, y = (-4).dp)
                        .clip(RoundedCornerShape(50))
                        .background(CarthageCoral)
                        .border(1.5.dp, Color.White, RoundedCornerShape(50))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold),
                        color = Color.White
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = DeepNavyInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun WasteCrushLogoWordmark(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val fontSize = if (compact) 28.sp else 38.sp
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Glossy emerald recycling crest
        Box(
            modifier = Modifier
                .size(if (compact) 36.dp else 46.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(LimePlayTop, EcoEmeraldDark)))
                .border(2.5.dp, DeepNavyInk, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Recycling,
                contentDescription = "WasteCrush Leaf Emblem",
                tint = Color.White,
                modifier = Modifier.size(if (compact) 22.dp else 28.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(contentAlignment = Alignment.Center) {
            // Bold navy outline shadow layer
            Row(modifier = Modifier.offset(x = 2.dp, y = 2.5.dp)) {
                Text(
                    text = "Waste",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = fontSize,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = DeepNavyInk
                )
                Text(
                    text = "Crush",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = fontSize,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = DeepNavyInk
                )
            }
            // Vibrant Mediterranean foreground letters
            Row {
                Text(
                    text = "Waste",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = fontSize,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = Color(0xFF42E8FF)
                )
                Text(
                    text = "Crush",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = fontSize,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = Color(0xFFFFCA28)
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = SaffronGold,
            modifier = Modifier.size(if (compact) 18.dp else 24.dp)
        )
    }
}

@Composable
fun StatPill(
    value: String,
    icon: ImageVector,
    badgeColor: Color,
    iconTint: Color = DeepNavyInk,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .height(40.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(50),
        color = SurfaceWhite,
        border = BorderStroke(2.dp, DeepNavyInk),
        shadowElevation = 3.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
                    .border(1.5.dp, DeepNavyInk, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = DeepNavyInk
            )
        }
    }
}

@Composable
fun RecyclableTileArt(
    tile: BoardTile,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tileScale"
    )
    Box(
        modifier = modifier
            .scale(scale)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        tile.type.primaryColor,
                        tile.type.darkColor
                    )
                )
            )
            .border(
                width = if (isSelected) 2.5.dp else 1.5.dp,
                color = if (isSelected) GlowBeamGold else DeepNavyInk.copy(alpha = 0.85f),
                shape = RoundedCornerShape(10.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
            val w = size.width
            val h = size.height

            // Top glossy bubble highlight
            drawRoundRect(
                color = Color.White.copy(alpha = 0.30f),
                topLeft = Offset(w * 0.10f, h * 0.04f),
                size = Size(w * 0.80f, h * 0.25f),
                cornerRadius = CornerRadius(w * 0.22f, w * 0.22f)
            )

            when (tile.type) {
                WasteType.PLASTIC_BLUE -> {
                    // Contoured Plastic Water Bottle
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(w * 0.36f, h * 0.07f),
                        size = Size(w * 0.28f, h * 0.14f),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                    drawRoundRect(
                        color = Color(0xFFE0F2FE),
                        topLeft = Offset(w * 0.25f, h * 0.23f),
                        size = Size(w * 0.50f, h * 0.68f),
                        cornerRadius = CornerRadius(w * 0.18f, w * 0.18f)
                    )
                    drawRect(
                        color = Color(0xFF0284C7),
                        topLeft = Offset(w * 0.25f, h * 0.45f),
                        size = Size(w * 0.50f, h * 0.22f)
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.85f),
                        start = Offset(w * 0.35f, h * 0.30f),
                        end = Offset(w * 0.35f, h * 0.80f),
                        strokeWidth = w * 0.07f,
                        cap = StrokeCap.Round
                    )
                }

                WasteType.GLASS_GREEN -> {
                    // Slender Emerald Glass Bottle
                    drawRoundRect(
                        color = Color(0xFFFDE68A),
                        topLeft = Offset(w * 0.40f, h * 0.07f),
                        size = Size(w * 0.20f, h * 0.23f),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                    drawRoundRect(
                        color = Color(0xFFDCFCE7),
                        topLeft = Offset(w * 0.24f, h * 0.31f),
                        size = Size(w * 0.52f, h * 0.60f),
                        cornerRadius = CornerRadius(w * 0.18f, w * 0.18f)
                    )
                    drawLine(
                        color = Color(0xFF15803D),
                        start = Offset(w * 0.37f, h * 0.43f),
                        end = Offset(w * 0.37f, h * 0.80f),
                        strokeWidth = w * 0.09f,
                        cap = StrokeCap.Round
                    )
                }

                WasteType.ALUMINUM_YELLOW -> {
                    // Bright Soda Can with metallic rim & lightning emblem
                    drawRoundRect(
                        color = Color(0xFFE2E8F0),
                        topLeft = Offset(w * 0.24f, h * 0.12f),
                        size = Size(w * 0.52f, h * 0.14f),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawRoundRect(
                        color = Color(0xFFFEF9C3),
                        topLeft = Offset(w * 0.21f, h * 0.23f),
                        size = Size(w * 0.58f, h * 0.66f),
                        cornerRadius = CornerRadius(w * 0.13f, w * 0.13f)
                    )
                    val bolt = Path().apply {
                        moveTo(w * 0.56f, h * 0.31f)
                        lineTo(w * 0.37f, h * 0.56f)
                        lineTo(w * 0.51f, h * 0.56f)
                        lineTo(w * 0.43f, h * 0.79f)
                        lineTo(w * 0.65f, h * 0.49f)
                        lineTo(w * 0.50f, h * 0.49f)
                        close()
                    }
                    drawPath(bolt, color = Color(0xFFD97706))
                }

                WasteType.ORGANIC_RED -> {
                    // Organic Apple Core
                    drawLine(
                        color = Color(0xFF5D4037),
                        start = Offset(w * 0.50f, h * 0.09f),
                        end = Offset(w * 0.50f, h * 0.26f),
                        strokeWidth = w * 0.09f,
                        cap = StrokeCap.Round
                    )
                    drawOval(
                        color = Color(0xFF4ADE80),
                        topLeft = Offset(w * 0.52f, h * 0.07f),
                        size = Size(w * 0.28f, h * 0.16f)
                    )
                    drawRoundRect(
                        color = Color(0xFFFFF1F2),
                        topLeft = Offset(w * 0.35f, h * 0.34f),
                        size = Size(w * 0.30f, h * 0.36f),
                        cornerRadius = CornerRadius(w * 0.10f, w * 0.10f)
                    )
                    drawOval(
                        color = Color(0xFFFECDD3),
                        topLeft = Offset(w * 0.21f, h * 0.21f),
                        size = Size(w * 0.58f, h * 0.23f)
                    )
                    drawOval(
                        color = Color(0xFFFECDD3),
                        topLeft = Offset(w * 0.21f, h * 0.64f),
                        size = Size(w * 0.58f, h * 0.23f)
                    )
                    drawCircle(color = Color(0xFF5D4037), radius = w * 0.055f, center = Offset(w * 0.44f, h * 0.52f))
                    drawCircle(color = Color(0xFF5D4037), radius = w * 0.055f, center = Offset(w * 0.56f, h * 0.52f))
                }

                WasteType.CARDBOARD_TAN -> {
                    // Cardboard Parcel Box with open flaps feel
                    drawRoundRect(
                        color = Color(0xFFFFEDD5),
                        topLeft = Offset(w * 0.15f, h * 0.19f),
                        size = Size(w * 0.70f, h * 0.66f),
                        cornerRadius = CornerRadius(w * 0.10f, w * 0.10f)
                    )
                    drawRect(
                        color = Color(0xFF9A5B25),
                        topLeft = Offset(w * 0.43f, h * 0.19f),
                        size = Size(w * 0.14f, h * 0.66f)
                    )
                    drawLine(
                        color = Color(0xFF78350F),
                        start = Offset(w * 0.15f, h * 0.42f),
                        end = Offset(w * 0.85f, h * 0.42f),
                        strokeWidth = w * 0.07f
                    )
                }
            }

            // Special Power Tile Indicators (4-Match Line Blaster / 5-Match Eco-Bomb)
            when (tile.special) {
                SpecialTileType.LINE_H -> {
                    drawLine(
                        color = Color.White,
                        start = Offset(w * 0.05f, h * 0.5f),
                        end = Offset(w * 0.95f, h * 0.5f),
                        strokeWidth = w * 0.15f,
                        cap = StrokeCap.Round
                    )
                    drawCircle(GlowBeamGold, radius = w * 0.13f, center = Offset(w * 0.12f, h * 0.5f))
                    drawCircle(GlowBeamGold, radius = w * 0.13f, center = Offset(w * 0.88f, h * 0.5f))
                }
                SpecialTileType.LINE_V -> {
                    drawLine(
                        color = Color.White,
                        start = Offset(w * 0.5f, h * 0.05f),
                        end = Offset(w * 0.5f, h * 0.95f),
                        strokeWidth = w * 0.15f,
                        cap = StrokeCap.Round
                    )
                    drawCircle(GlowBeamGold, radius = w * 0.13f, center = Offset(w * 0.5f, h * 0.12f))
                    drawCircle(GlowBeamGold, radius = w * 0.13f, center = Offset(w * 0.5f, h * 0.88f))
                }
                SpecialTileType.ECO_BOMB -> {
                    drawCircle(
                        color = GlowBeamGold,
                        radius = w * 0.38f,
                        style = Stroke(width = w * 0.13f)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = w * 0.18f
                    )
                }
                SpecialTileType.NONE -> {}
            }
        }
    }
}

/**
 * Renders the custom uploaded Boss or Mini-Boss PNG sprite with breathing idle bob,
 * rarity aura glow, ground shadow, and tactile impact recoil + starburst flash on hit.
 */
@Composable
fun BossMonsterAvatar(
    boss: BossDefinition,
    modifier: Modifier = Modifier,
    hitFlash: Boolean = false,
    isMiniTrashWave: Boolean = false,
    waveIndex: Int = 3,
    animateIdle: Boolean = true
) {
    val spriteRes = when {
        isMiniTrashWave && waveIndex <= 1 -> GameCatalog.miniWave1Sprite
        isMiniTrashWave -> GameCatalog.miniWave2Sprite
        else -> boss.spriteRes
    }

    val infiniteTransition = rememberInfiniteTransition(label = "bossIdle")
    val idleBob by infiniteTransition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bossBob"
    )
    val hitScale by animateFloatAsState(
        targetValue = if (hitFlash) 1.14f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessHigh),
        label = "hitScale"
    )
    val hitRotation by animateFloatAsState(
        targetValue = if (hitFlash) -5f else 0f,
        animationSpec = tween(90),
        label = "hitRot"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Ground shadow & rarity radial energy aura behind sprite
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawOval(
                color = Color.Black.copy(alpha = 0.30f),
                topLeft = Offset(w * 0.14f, h * 0.84f),
                size = Size(w * 0.72f, h * 0.14f)
            )
            if (!isMiniTrashWave && (boss.rarity != BossRarity.COMMON || hitFlash)) {
                val auraColor = if (hitFlash) GlowBeamGold else boss.rarity.frameColor
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            auraColor.copy(alpha = if (hitFlash) 0.65f else 0.34f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.5f, h * 0.48f),
                        radius = w * 0.54f
                    ),
                    radius = w * 0.54f,
                    center = Offset(w * 0.5f, h * 0.48f)
                )
            }
        }

        Image(
            painter = painterResource(id = spriteRes),
            contentDescription = if (isMiniTrashWave) "Pollution Mini-Boss" else boss.name,
            contentScale = ContentScale.Fit,
            colorFilter = if (hitFlash) ColorFilter.tint(Color.White.copy(alpha = 0.42f)) else null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = if (animateIdle) idleBob else 0f
                    scaleX = hitScale
                    scaleY = hitScale
                    rotationZ = hitRotation
                }
        )
    }
}

@Composable
fun BossCardTile(
    boss: BossDefinition,
    level: Int,
    copies: Int,
    isEquipped: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unlocked = level > 0
    val needed = GameCatalog.copiesNeededForNextLevel(level.coerceAtLeast(1), boss.rarity)
    val canUpgrade = unlocked && copies >= needed && level < 10

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (unlocked) SurfaceWhite else Color(0xFFDCE5F2),
        border = BorderStroke(
            width = if (isEquipped) 3.dp else 2.dp,
            color = if (isEquipped) EcoEmerald else boss.rarity.frameColor
        ),
        shadowElevation = if (unlocked) 5.dp else 1.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(boss.rarity.frameColor)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = boss.rarity.label.take(4).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
                if (unlocked) {
                    Text(
                        text = "Lv.$level",
                        style = MaterialTheme.typography.labelMedium,
                        color = DeepNavyInk
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = MutedSlateText,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(
                                boss.bodyColor.copy(alpha = if (unlocked) 0.24f else 0.08f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                BossMonsterAvatar(
                    boss = boss,
                    animateIdle = false,
                    modifier = Modifier.size(58.dp)
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = boss.name,
                style = MaterialTheme.typography.labelMedium,
                color = DeepNavyInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            if (unlocked) {
                val frac = (copies.toFloat() / needed.toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(15.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFE2E8F0))
                        .border(1.dp, DeepNavyInk, RoundedCornerShape(50))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(frac)
                            .background(if (canUpgrade) EcoEmerald else SidiCobalt)
                    )
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (canUpgrade) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Upgrade ready",
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                        Text(
                            text = "$copies/$needed",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = if (frac > 0.45f) Color.White else DeepNavyInk
                        )
                    }
                }
            } else {
                Text(
                    text = "Arena ${boss.arenaId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedSlateText
                )
            }
        }
    }
}

@Composable
fun Match3BoardGrid(
    board: List<List<BoardTile>>,
    selectedTile: GridPos?,
    interactive: Boolean,
    onTileTap: (GridPos) -> Unit = {},
    onTileSwipe: (GridPos, GridPos) -> Unit = { _, _ -> },
    useSandstoneTheme: Boolean = true,
    modifier: Modifier = Modifier
) {
    val frameOuter = if (useSandstoneTheme) SandBoardOuter else BattleBoardDark
    val frameBorder = if (useSandstoneTheme) SandBoardBorder else DeepNavyInk
    val cellLight = if (useSandstoneTheme) SandCellLight else BattleBoardCell
    val cellDark = if (useSandstoneTheme) SandCellDark else BattleBoardCellAlt

    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .testTag("match3_board_grid"),
        shape = RoundedCornerShape(20.dp),
        color = frameOuter,
        border = BorderStroke(3.5.dp, frameBorder),
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (useSandstoneTheme) {
                        Brush.verticalGradient(listOf(SandBoardOuter, SandBoardInner))
                    } else {
                        Brush.verticalGradient(listOf(BattleBoardDark, BattleBoardDark))
                    }
                )
                .padding(6.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                for (r in 0 until BOARD_SIZE) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (c in 0 until BOARD_SIZE) {
                            val pos = GridPos(r, c)
                            val tile = board.getOrNull(r)?.getOrNull(c) ?: BoardTile(0L, WasteType.PLASTIC_BLUE)
                            val isSel = selectedTile == pos
                            val cellBg = if ((r + c) % 2 == 0) cellLight else cellDark

                            var dragAccumX by remember(r, c, tile.id) { mutableFloatStateOf(0f) }
                            var dragAccumY by remember(r, c, tile.id) { mutableFloatStateOf(0f) }
                            var swipeTriggered by remember(r, c, tile.id) { mutableStateOf(false) }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(1.2.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) GlowBeamGold.copy(alpha = 0.45f) else cellBg)
                                    .then(
                                        if (interactive) {
                                            Modifier
                                                .clickable { onTileTap(pos) }
                                                .pointerInput(r, c, tile.id) {
                                                    detectDragGestures(
                                                        onDragStart = {
                                                            dragAccumX = 0f
                                                            dragAccumY = 0f
                                                            swipeTriggered = false
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            if (swipeTriggered) return@detectDragGestures
                                                            dragAccumX += dragAmount.x
                                                            dragAccumY += dragAmount.y
                                                            val threshold = 22f
                                                            if (abs(dragAccumX) > threshold || abs(dragAccumY) > threshold) {
                                                                swipeTriggered = true
                                                                val target = if (abs(dragAccumX) > abs(dragAccumY)) {
                                                                    val dc = if (dragAccumX > 0) 1 else -1
                                                                    GridPos(r, (c + dc).coerceIn(0, BOARD_SIZE - 1))
                                                                } else {
                                                                    val dr = if (dragAccumY > 0) 1 else -1
                                                                    GridPos((r + dr).coerceIn(0, BOARD_SIZE - 1), c)
                                                                }
                                                                if (target != pos) onTileSwipe(pos, target)
                                                            }
                                                        }
                                                    )
                                                }
                                        } else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                RecyclableTileArt(
                                    tile = tile,
                                    isSelected = isSel,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RarityChip(rarity: BossRarity, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(rarity.frameColor)
            .border(1.5.dp, DeepNavyInk, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = rarity.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

@Composable
fun EcoBrandBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = EcoMintSoft,
        border = BorderStroke(1.5.dp, DeepNavyInk)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Recycling,
                contentDescription = null,
                tint = EcoEmeraldDark,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "TUNISIA ECO-ARENA",
                style = MaterialTheme.typography.labelSmall,
                color = DeepNavyInk
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = SaffronGoldDark,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
