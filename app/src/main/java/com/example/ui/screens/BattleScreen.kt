package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.GameSessionState
import com.example.engine.GridPos
import com.example.engine.SessionSyncStatus
import com.example.model.GameCatalog
import com.example.ui.components.BossMonsterAvatar
import com.example.ui.components.EnemyStage
import com.example.ui.components.EnvironmentScene
import com.example.ui.components.Match3BoardGrid
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BattleScreen(
    state: GameSessionState,
    onTileTap: (GridPos) -> Unit,
    onTileSwipe: (GridPos, GridPos) -> Unit,
    onActivateBossSlot: (Int) -> Unit,
    onToggleSound: () -> Unit,
    onToggleRivalPeek: () -> Unit,
    onForfeit: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var showQuitDialog by remember { mutableStateOf(false) }

    BackHandler { showQuitDialog = true }

    val arena = GameCatalog.arenaById(state.arenaId)
    val place = GameCatalog.placeById(state.placeId)
    val sharedBoss = GameCatalog.bossById(state.sharedBossId)
    val local = state.localPlayer
    val rival = state.opponentPlayer

    // Brief hit-flash + starburst impact pulse when damage lands
    var isFlashing by remember { mutableStateOf(false) }
    LaunchedEffect(state.hitFlashToken) {
        if (state.hitFlashToken > 0L) {
            isFlashing = true
            delay(170L)
            isFlashing = false
        }
    }

    LaunchedEffect(state.invalidSwapToken) {
        if (state.invalidSwapToken > 0L && com.example.data.SettingsStore.current.hapticsEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val mins = state.secondsRemaining / 60
    val secs = state.secondsRemaining % 60
    val timerText = String.format("%02d:%02d", mins, secs)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF061024), Color(0xFF0C2046), Color(0xFF050D1E))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .align(Alignment.Center)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. TOP PREMIUM 1v1 LIVE RACE & FIREBASE SYNC HUD
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = DeepNavyInk.copy(alpha = 0.92f),
                border = BorderStroke(2.dp, CeramicBlueTop.copy(alpha = 0.8f)),
                shadowElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Local Player + Cloud Sync Indicator
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                val dotColor = when (state.syncStatus) {
                                    SessionSyncStatus.LIVE_SYNCED -> LimePlayTop
                                    SessionSyncStatus.SYNCING -> SaffronGold
                                    else -> Color(0xFF94A3B8)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                Text(
                                    text = local.name,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = LimePlayTop,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = if (local.waveIndex < 3) "Wave ${local.waveIndex}/2 • ${local.restorationPct}% Clean" else "BOSS • ${local.bossHp} HP",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }

                        // Center: Glossy Countdown Timer + Spectators
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (state.secondsRemaining <= 15) CarthageCoral else SaffronGold,
                                border = BorderStroke(2.dp, Color.White)
                            ) {
                                Text(
                                    text = timerText,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = if (state.secondsRemaining <= 15) Color.White else DeepNavyInk,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                                )
                            }
                            if (state.spectatorCount > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, tint = SidiSkyLight, modifier = Modifier.size(11.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("${state.spectatorCount} live", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = SidiSkyLight)
                                }
                            }
                        }

                        // Right: Opponent Status + Live Peek / Sound / Quit
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rival.name,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color(0xFFFF8A80),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (rival.waveIndex < 3) "Wave ${rival.waveIndex}/2" else "BOSS ${rival.bossHp}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                            IconButton(
                                onClick = onToggleRivalPeek,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("toggle_rival_peek_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = "Peek opponent board",
                                    tint = if (state.showRivalPeek) SaffronGold else SidiSkyLight,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onToggleSound,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (state.soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                    contentDescription = "Toggle audio",
                                    tint = SidiSkyLight,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { showQuitDialog = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Leave match",
                                    tint = SidiSkyLight,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Head-to-Head Environmental Restoration Race Progress Bars
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RaceProgressMiniBar(
                            label = "YOU • CLEAN ${local.restorationPct}%",
                            fraction = local.restorationPct / 100f,
                            fillBrush = Brush.horizontalGradient(listOf(EcoEmerald, LimePlayTop)),
                            modifier = Modifier.weight(1f)
                        )
                        RaceProgressMiniBar(
                            label = "RIVAL • CLEAN ${rival.restorationPct}%",
                            fraction = rival.restorationPct / 100f,
                            fillBrush = Brush.horizontalGradient(listOf(CarthageCoralDark, BossHpRedTop)),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 2. UPPER TUNISIAN LANDMARK BATTLEFIELD & REAL-TIME ENVIRONMENTAL RESTORATION
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .shadow(10.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .border(
                        2.5.dp,
                        Brush.linearGradient(listOf(GlowBeamGold, SaffronGold, CeramicBlueTop)),
                        RoundedCornerShape(22.dp)
                    )
            ) {
                // The place reacts to the fight: cartoon + litter while polluted, real photo once purified
                EnvironmentScene(
                    place = place,
                    pollution = local.pollutionLevel,
                    purified = local.bossHp <= 0 || (state.isFinished && state.wonMatch),
                    modifier = Modifier.fillMaxSize()
                )

                // Radiant Golden-White Energy Beams + Starburst Impact Sparks (grid_58768252.jpg style)
                if (state.activeBeams.isNotEmpty() || isFlashing) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val bossCenter = Offset(w * 0.5f, h * 0.44f)

                        state.activeBeams.forEach { beam ->
                            val start = Offset(w * beam.startColFraction, h * 0.99f)
                            // Outer warm golden beam aura
                            drawLine(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        GlowBeamGold.copy(alpha = 0.85f),
                                        beam.wasteType.primaryColor.copy(alpha = 0.65f),
                                        GlowBeamCore
                                    ),
                                    start = start,
                                    end = bossCenter
                                ),
                                start = start,
                                end = bossCenter,
                                strokeWidth = if (beam.isCritical) 18f else 12f,
                                cap = StrokeCap.Round
                            )
                            // Inner blinding white core
                            drawLine(
                                color = GlowBeamCore.copy(alpha = 0.92f),
                                start = start,
                                end = bossCenter,
                                strokeWidth = if (beam.isCritical) 7f else 4.5f,
                                cap = StrokeCap.Round
                            )
                            // 3 staggered glowing orbs rising along the beam path into the Boss
                            listOf(0.26f, 0.52f, 0.78f).forEachIndexed { idx, t ->
                                val orbPos = Offset(
                                    x = start.x + (bossCenter.x - start.x) * t,
                                    y = start.y + (bossCenter.y - start.y) * t
                                )
                                val outerR = if (idx == 1) 15f else 11f
                                val innerR = if (idx == 1) 9f else 6f
                                drawCircle(color = GlowBeamGold, radius = outerR, center = orbPos)
                                drawCircle(color = GlowBeamCore, radius = innerR, center = orbPos)
                            }
                        }

                        // Starburst impact flare directly on the Boss when struck!
                        if (isFlashing) {
                            val impactCenter = Offset(w * 0.54f, h * 0.45f)
                            for (i in 0 until 8) {
                                val angle = (i * Math.PI / 4.0)
                                val rayLen = if (i % 2 == 0) 44f else 28f
                                val end = Offset(
                                    impactCenter.x + (cos(angle) * rayLen).toFloat(),
                                    impactCenter.y + (sin(angle) * rayLen).toFloat()
                                )
                                drawLine(
                                    color = GlowBeamGold,
                                    start = impactCenter,
                                    end = end,
                                    strokeWidth = 6f,
                                    cap = StrokeCap.Round
                                )
                            }
                            drawCircle(color = GlowBeamCore, radius = 18f, center = impactCenter)
                        }
                    }
                }

                // Location + Environmental Pollution Gauge Top Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = CeramicBlueBottom.copy(alpha = 0.92f),
                        border = BorderStroke(2.dp, Color.White)
                    ) {
                        Text(
                            text = "${place.name} • ${place.regionTag}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    val smogPct = (local.pollutionLevel * 100f).toInt().coerceIn(0, 100)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (smogPct <= 25) EcoEmerald else DeepNavyInk.copy(alpha = 0.88f),
                        border = BorderStroke(1.5.dp, if (smogPct <= 25) LimePlayTop else SaffronGold)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (smogPct <= 25) Icons.Default.Eco else Icons.Default.Cloud,
                                contentDescription = null,
                                tint = if (smogPct <= 25) Color.White else SaffronGold,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (smogPct == 0) "PURIFIED!" else "Smog $smogPct%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White
                            )
                        }
                    }
                }

                // Center enemies: mini trash monsters of waves 1-2, then the shared Boss
                EnemyStage(
                    waveIndex = local.waveIndex,
                    mobs = local.waveMobs,
                    boss = sharedBoss,
                    bossHp = local.bossHp,
                    bossMaxHp = local.bossMaxHp,
                    hitFlash = isFlashing,
                    defeatedToken = state.mobDefeatedToken,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 14.dp)
                )

                // Floating Combo / Damage Pop-Up Banner with spring pop-in
                if (state.comboBannerText.isNotBlank()) {
                    val comboScale = remember(state.hitFlashToken) { Animatable(0.65f) }
                    LaunchedEffect(state.hitFlashToken) {
                        comboScale.animateTo(1f, spring(dampingRatio = 0.52f, stiffness = 580f))
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp)
                            .scale(comboScale.value),
                        shape = RoundedCornerShape(50),
                        color = SaffronGold,
                        border = BorderStroke(2.dp, DeepNavyInk),
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = state.comboBannerText,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = DeepNavyInk,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp)
                        )
                    }
                }

                // Toggleable Real-Time Rival Mini-Board Peek Overlay (proves live 2-player grid sync!)
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.showRivalPeek,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = DeepNavyInk.copy(alpha = 0.94f),
                        border = BorderStroke(2.dp, CarthageCoral),
                        shadowElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .width(114.dp)
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "RIVAL LIVE",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold),
                                color = Color(0xFFFF8A80)
                            )
                            Spacer(Modifier.height(3.dp))
                            Match3BoardGrid(
                                board = rival.board,
                                selectedTile = null,
                                interactive = false,
                                useSandstoneTheme = true,
                                modifier = Modifier.size(96.dp)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "Smog ${(rival.pollutionLevel * 100).toInt()}% • ${rival.bossHp}HP",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // 3. MIDDLE 3-SLOT ECO-DECK SKILL BAR (Custom Boss Sprites + Shimmering Ready State)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                local.deckBossIds.take(3).forEachIndexed { idx, bossId ->
                    val boss = GameCatalog.bossById(bossId)
                    val charge = local.skillCharges.getOrElse(idx) { 0 }.coerceIn(0, 100)
                    val ready = charge >= 100

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .shadow(if (ready) 6.dp else 2.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = ready) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onActivateBossSlot(idx)
                            }
                            .testTag("eco_deck_skill_slot_$idx"),
                        shape = RoundedCornerShape(16.dp),
                        color = if (ready) EcoEmerald else DeepNavyInk.copy(alpha = 0.90f),
                        border = BorderStroke(
                            width = if (ready) 2.5.dp else 1.8.dp,
                            color = if (ready) GlowBeamGold else boss.rarity.frameColor
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(boss.bodyColor.copy(alpha = 0.25f))
                                    .border(1.5.dp, if (ready) GlowBeamGold else boss.rarity.frameColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                BossMonsterAvatar(
                                    boss = boss,
                                    animateIdle = ready,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (ready) "CAST!" else boss.name,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = if (ready) Color.White else SaffronCream,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFF0A162C))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(charge / 100f)
                                            .background(if (ready) GlowBeamGold else boss.preferredWaste.primaryColor)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$charge%",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = Color.White
                                    )
                                    if (ready) {
                                        Icon(Icons.Default.Bolt, contentDescription = "Ready", tint = GlowBeamGold, modifier = Modifier.size(13.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. LOWER OBSIDIAN-GOLD 7x7 MATCH-3 GRID (Unlimited Moves Speed Race)
            Match3BoardGrid(
                board = local.board,
                selectedTile = state.selectedTile,
                interactive = true,
                useSandstoneTheme = false,
                invalidSwapPair = state.invalidSwapPair,
                invalidSwapToken = state.invalidSwapToken,
                popBurstTiles = state.popBurstTiles,
                popBurstToken = state.popBurstToken,
                hintPair = state.hintPair,
                onTileTap = { pos ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTileTap(pos)
                },
                onTileSwipe = { from, to ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTileSwipe(from, to)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
            )
        }
    }

    if (showQuitDialog) {
        AlertDialog(
            onDismissRequest = { showQuitDialog = false },
            containerColor = DarkSurfaceCard,
            title = { Text("Leave 1v1 Battle?", style = MaterialTheme.typography.titleLarge, color = TextOnDark) },
            text = { Text("Leaving now ends your speed race and returns to the Battle Hub.", color = TextMutedOnDark) },
            confirmButton = {
                TextButton(onClick = {
                    showQuitDialog = false
                    onForfeit()
                }) { Text("Leave", color = BossHpRedTop) }
            },
            dismissButton = {
                TextButton(onClick = { showQuitDialog = false }) { Text("Keep Racing", color = CeramicBlueTop) }
            }
        )
    }
}

@Composable
private fun RaceProgressMiniBar(
    label: String,
    fraction: Float,
    fillBrush: Brush,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold),
            color = Color.White
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(BossHpTrack)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0.03f, 1f))
                    .background(fillBrush)
            )
        }
    }
}
