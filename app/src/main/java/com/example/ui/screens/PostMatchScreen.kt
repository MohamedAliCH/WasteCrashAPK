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
import androidx.compose.runtime.*
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

            // Eco-postcard: the place transforms from polluted to its real photo on victory
            var revealed by remember { mutableStateOf(false) }
            LaunchedEffect(state.wonMatch, state.matchId) {
                revealed = false
                if (state.wonMatch) {
                    kotlinx.coroutines.delay(650L)
                    revealed = true
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = DarkSurfaceCard,
                border = BorderStroke(2.dp, if (state.wonMatch) SaffronGold else DarkBorderSubtle)
            ) {
                Column {
                    com.example.ui.components.EnvironmentScene(
                        place = place,
                        pollution = if (revealed) 0f else local.pollutionLevel.coerceAtLeast(if (state.wonMatch) 0.6f else 0f),
                        purified = revealed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    )
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (place.arabicName.isNotBlank()) "${place.name} • ${place.arabicName}" else place.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = TextOnDark
                        )
                        Text(
                            text = place.ecoFact,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMutedOnDark
                        )
                    }
                }
            }

            // Head-to-Head Damage & Pollution Clearing Breakdown
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = DarkSurfaceCard,
                border = BorderStroke(2.dp, DarkBorderSubtle)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "1v1 Race Breakdown",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = TextOnDark
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ComparisonColumn(
                            title = local.name,
                            damage = local.totalDamage,
                            restoration = local.restorationPct,
                            accent = EcoEmeraldBright,
                            modifier = Modifier.weight(1f)
                        )
                        ComparisonColumn(
                            title = rival.name,
                            damage = rival.totalDamage,
                            restoration = rival.restorationPct,
                            accent = BossHpRedTop,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    HorizontalDivider(color = DarkBorderSubtle.copy(alpha = 0.45f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MiniMetric("Blue Tiles", "${state.blueTilesMatched}", CeramicBlueTop)
                        MiniMetric("Total Matched", "${state.totalTilesMatched}", SaffronGold)
                        MiniMetric("Best Combo", "${state.maxCombo}x", EcoEmeraldBright)
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
                    bgColor = DarkSurfaceCard,
                    borderColor = SaffronGold,
                    iconTint = SaffronGold,
                    modifier = Modifier.weight(1f)
                )
                PayoutBadge(
                    icon = Icons.Default.MonetizationOn,
                    label = "Eco-Coins",
                    value = "+$coinsEarned",
                    bgColor = DarkSurfaceCard,
                    borderColor = EcoEmeraldBright,
                    iconTint = EcoEmeraldBright,
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
                    color = DarkSurfaceCard,
                    border = BorderStroke(2.5.dp, boss.rarity.frameColor),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(82.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(boss.bodyColor.copy(alpha = 0.22f))
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
                                    color = EcoEmeraldBright
                                )
                                RarityChip(boss.rarity)
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = boss.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = TextOnDark
                            )
                            Text(
                                text = "Level $lvl  •  $copies / $needed copies for next level",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMutedOnDark
                            )
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (copies.toFloat() / needed.toFloat()).coerceIn(0f, 1f) },
                                color = EcoEmeraldBright,
                                trackColor = Color(0xFF061022),
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
                    color = DarkSurfaceCard,
                    border = BorderStroke(2.dp, SaffronGold)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Stars, contentDescription = null, tint = SaffronGold, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Quest Objective Completed!", style = MaterialTheme.typography.titleSmall, color = GlowBeamGold)
                            val names = reward.newlyCompletedQuestIds.mapNotNull { id ->
                                GameCatalog.quests.firstOrNull { it.id == id }?.title
                            }.joinToString(", ")
                            Text("Claim rewards in Quests: $names", style = MaterialTheme.typography.bodyMedium, color = TextOnDark)
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
                    topColor = Color(0xFF2B4D8A),
                    bottomColor = Color(0xFF18305C),
                    lipColor = Color(0xFF0C1A36),
                    textColor = Color.White,
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
        Text(text = "$damage DMG", style = MaterialTheme.typography.headlineMedium, color = TextOnDark)
        Text(text = "$restoration% Clean", style = MaterialTheme.typography.labelMedium, color = TextMutedOnDark)
    }
}

@Composable
private fun MiniMetric(label: String, value: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = tint)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMutedOnDark)
    }
}

@Composable
private fun PayoutBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    bgColor: Color,
    borderColor: Color,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = bgColor,
        border = BorderStroke(2.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(text = value, style = MaterialTheme.typography.headlineMedium, color = TextOnDark)
                Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMutedOnDark)
            }
        }
    }
}
