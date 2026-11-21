package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.engine.GameSessionState
import com.example.model.GameCatalog
import com.example.model.UserProfile
import com.example.ui.components.BossMonsterAvatar
import com.example.ui.components.GlossyButton
import com.example.ui.components.RarityChip
import com.example.ui.theme.*

@Composable
fun PostMatchScreen(
    state: GameSessionState,
    profile: UserProfile?,
    onPlayAgain: () -> Unit,
    onWatchReplay: (String) -> Unit,
    onReturnHome: () -> Unit
) {
    val place = GameCatalog.placeById(state.placeId)
    val boss = GameCatalog.bossById(state.sharedBossId)
    val local = state.localPlayer
    val rival = state.opponentPlayer
    val reward = state.rewardOutcome
    val trophyDelta = reward?.trophyDelta ?: if (state.wonMatch) 30 else -15
    val coinsEarned = reward?.coinsEarned ?: if (state.wonMatch) 95 else 30

    val bgBrush = if (state.wonMatch) {
        Brush.verticalGradient(listOf(CeramicBlueBottom, Color(0xFF0E7490), BattleBoardDark))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF6B1D1D), BattleBoardDark, Color(0xFF0A1426)))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Victory / Defeat Glossy Banner
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (state.wonMatch) SaffronGold else CarthageCoral,
                border = BorderStroke(3.dp, DeepNavyInk),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (state.wonMatch) "VICTORY!" else "DEFEAT",
                        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (state.wonMatch) DeepNavyInk else Color.White
                    )
                    Text(
                        text = if (state.wonMatch) {
                            "${place.name} purified! You defeated ${boss.name} first."
                        } else {
                            "${rival.name} edged ahead at ${place.name}."
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (state.wonMatch) DeepNavyInk else SaffronCream,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Head-to-Head Damage & Pollution Clearing Breakdown
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = SurfaceWhite,
                border = BorderStroke(2.5.dp, DeepNavyInk)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "1v1 Race Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        color = DeepNavyInk
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ComparisonColumn(
                            title = local.name,
                            damage = local.totalDamage,
                            restoration = local.restorationPct,
                            accent = EcoEmeraldDark,
                            modifier = Modifier.weight(1f)
                        )
                        ComparisonColumn(
                            title = rival.name,
                            damage = rival.totalDamage,
                            restoration = rival.restorationPct,
                            accent = CarthageCoral,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    HorizontalDivider(color = SurfaceSoftBlue)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MiniMetric("Blue Tiles", "${state.blueTilesMatched}", TilePlasticBlue)
                        MiniMetric("Total Matched", "${state.totalTilesMatched}", SidiCobalt)
                        MiniMetric("Best Combo", "${state.maxCombo}x", SaharanAmber)
                    }
                }
            }

            // Trophy & Eco-Coin Payout Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PayoutBadge(
                    icon = Icons.Default.EmojiEvents,
                    label = "Trophies",
                    value = if (trophyDelta >= 0) "+$trophyDelta" else "$trophyDelta",
                    bgColor = SaffronCream,
                    iconTint = SaffronGoldDark,
                    modifier = Modifier.weight(1f)
                )
                PayoutBadge(
                    icon = Icons.Default.MonetizationOn,
                    label = "Eco-Coins",
                    value = "+$coinsEarned",
                    bgColor = EcoMintSoft,
                    iconTint = EcoEmeraldDark,
                    modifier = Modifier.weight(1f)
                )
            }

            // Winner Captured Boss Card Reveal with Real Uploaded Sprite!
            if (state.wonMatch) {
                val lvl = profile?.bossLevels?.get(boss.id) ?: 1
                val copies = profile?.bossCopies?.get(boss.id) ?: 1
                val needed = GameCatalog.copiesNeededForNextLevel(lvl.coerceAtLeast(1), boss.rarity)

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(3.dp, boss.rarity.frameColor),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(82.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(boss.bodyColor.copy(alpha = 0.16f))
                                .border(2.dp, boss.rarity.frameColor, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            BossMonsterAvatar(boss = boss, modifier = Modifier.size(72.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "+1 BOSS CARD CAPTURED!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EcoEmeraldDark
                                )
                                RarityChip(boss.rarity)
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = boss.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = DeepNavyInk
                            )
                            Text(
                                text = "Level $lvl  •  $copies / $needed copies for next level",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedSlateText
                            )
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (copies.toFloat() / needed.toFloat()).coerceIn(0f, 1f) },
                                color = EcoEmerald,
                                trackColor = SurfaceSoftBlue,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(50))
                            )
                        }
                    }
                }
            }

            // Quest Progress Ticker
            if (reward != null && reward.newlyCompletedQuestIds.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = SaffronCream,
                    border = BorderStroke(2.dp, SaffronGoldDark)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Stars, contentDescription = null, tint = SaffronGoldDark, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Quest Objective Completed!", style = MaterialTheme.typography.titleSmall, color = DeepNavyInk)
                            val names = reward.newlyCompletedQuestIds.mapNotNull { id ->
                                GameCatalog.quests.firstOrNull { it.id == id }?.title
                            }.joinToString(", ")
                            Text("Claim rewards in Quests: $names", style = MaterialTheme.typography.bodyMedium, color = DeepNavyInk)
                        }
                    }
                }
            }

            // Action Buttons: Watch Replay, Battle Again, Back to Hub
            if (!state.replayId.isNullOrBlank()) {
                GlossyButton(
                    text = "Watch Battle Replay",
                    onClick = { onWatchReplay(state.replayId) },
                    icon = Icons.Default.PlayCircle,
                    topColor = CeramicBlueTop,
                    bottomColor = CeramicBlueBottom,
                    lipColor = CeramicBlueLip,
                    modifier = Modifier.fillMaxWidth(),
                    tag = "post_match_watch_replay"
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlossyButton(
                    text = "Home Hub",
                    onClick = onReturnHome,
                    topColor = Color(0xFFE2E8F0),
                    bottomColor = Color(0xFFCBD5E1),
                    lipColor = Color(0xFF94A3B8),
                    textColor = DeepNavyInk,
                    modifier = Modifier.weight(1f),
                    tag = "post_match_home"
                )
                GlossyButton(
                    text = "Race Again",
                    onClick = onPlayAgain,
                    icon = Icons.Default.Bolt,
                    modifier = Modifier.weight(1f),
                    tag = "post_match_rematch"
                )
            }
        }
    }
}

@Composable
private fun ComparisonColumn(
    title: String,
    damage: Int,
    restoration: Int,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = accent, maxLines = 1)
        Text(text = "$damage DMG", style = MaterialTheme.typography.headlineMedium, color = DeepNavyInk)
        Text(text = "$restoration% Clean", style = MaterialTheme.typography.labelMedium, color = MutedSlateText)
    }
}

@Composable
private fun MiniMetric(label: String, value: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = tint)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MutedSlateText)
    }
}

@Composable
private fun PayoutBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    bgColor: Color,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = bgColor,
        border = BorderStroke(2.5.dp, DeepNavyInk)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(text = value, style = MaterialTheme.typography.headlineMedium, color = DeepNavyInk)
                Text(text = label, style = MaterialTheme.typography.labelSmall, color = MutedSlateText)
            }
        }
    }
}
