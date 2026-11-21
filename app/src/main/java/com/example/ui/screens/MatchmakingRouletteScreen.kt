package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.GameCatalog
import com.example.ui.components.BossMonsterAvatar
import com.example.ui.components.GlossyButton
import com.example.ui.components.RarityChip
import com.example.ui.theme.*
import com.example.viewmodel.MatchmakingPhase
import com.example.viewmodel.MatchmakingUiState

@Composable
fun MatchmakingRouletteScreen(
    state: MatchmakingUiState,
    onCancel: () -> Unit
) {
    val arena = GameCatalog.arenaById(state.arenaId)
    val boss = GameCatalog.bossById(state.sharedBossId)

    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.93f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(680, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(CeramicBlueLip, BattleBoardDark, Color(0xFF061024))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .align(Alignment.Center)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Status Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = EcoEmerald.copy(alpha = 0.25f),
                    border = BorderStroke(1.5.dp, LimePlayTop)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = LimePlayTop, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (state.phase == MatchmakingPhase.SEARCHING) {
                                "TROPHY MATCHMAKING • ${state.searchElapsedSec}s"
                            } else {
                                "1v1 SESSION LOCKED!"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }

                if (state.phase == MatchmakingPhase.SEARCHING) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("cancel_matchmaking_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel Matchmaking", tint = Color.White)
                    }
                }
            }

            if (state.phase == MatchmakingPhase.SEARCHING) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(176.dp)
                            .scale(pulse)
                            .clip(CircleShape)
                            .background(CeramicBlueBottom.copy(alpha = 0.30f))
                            .border(4.dp, SaffronGold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = arena.bannerRes),
                            contentDescription = arena.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(142.dp)
                                .clip(CircleShape)
                        )
                    }

                    Text(
                        text = "Finding 1v1 Rival...",
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = DeepNavyInk,
                        border = BorderStroke(1.5.dp, SaffronGold)
                    ) {
                        Text(
                            text = "${arena.name}  •  ${state.myTrophies} 🏆 (${state.trophyRangeLabel})",
                            style = MaterialTheme.typography.labelLarge,
                            color = SaffronGold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                    CircularProgressIndicator(color = LimePlayTop, strokeWidth = 4.dp)
                    Text(
                        text = "Syncing with Firebase Arena Queue across Tunisia's ${arena.subtitle}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SidiSkyLight,
                        textAlign = TextAlign.Center
                    )
                }

                GlossyButton(
                    text = "Cancel Search",
                    onClick = onCancel,
                    topColor = BossHpRedTop,
                    bottomColor = CarthageCoral,
                    lipColor = CarthageCoralDark,
                    modifier = Modifier.fillMaxWidth(0.72f),
                    tag = "cancel_search_cta"
                )
            } else {
                // VS Competitor Showcase + Animated 5-Place Tunisia Roulette
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompetitorPreviewCard(
                            name = state.myName,
                            trophies = state.myTrophies,
                            deckIds = state.myDeckIds,
                            borderColor = LimePlayTop,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Brush.verticalGradient(listOf(GlowBeamGold, SaffronGold)))
                                .border(2.5.dp, DeepNavyInk, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "VS",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = DeepNavyInk
                            )
                        }
                        CompetitorPreviewCard(
                            name = state.opponentName,
                            trophies = state.opponentTrophies,
                            deckIds = state.opponentDeckIds,
                            borderColor = CarthageCoral,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Place Roulette Wheel Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = SurfaceWhite,
                        border = BorderStroke(3.dp, SaffronGold)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Place, contentDescription = null, tint = SidiCobalt, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "TUNISIA PLACE ROULETTE",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = DeepNavyInk
                                )
                            }
                            Spacer(Modifier.height(8.dp))

                            arena.places.forEachIndexed { idx, place ->
                                val isHighlighted = idx == state.rouletteHighlightIndex
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isHighlighted) SaffronGold else SurfaceSoftBlue,
                                    border = BorderStroke(
                                        width = if (isHighlighted) 2.5.dp else 1.dp,
                                        color = if (isHighlighted) DeepNavyInk else Color.Transparent
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = place.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = DeepNavyInk
                                        )
                                        Text(
                                            text = place.regionTag,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isHighlighted) DeepNavyInk else MutedSlateText
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Shared Boss Preview Card with Real Uploaded Sprite!
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = BattleBoardCell,
                        border = BorderStroke(2.5.dp, boss.rarity.frameColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BossMonsterAvatar(boss = boss, modifier = Modifier.size(66.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "SHARED BOSS SUMMON",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SaffronGold
                                    )
                                    RarityChip(boss.rarity)
                                }
                                Text(
                                    text = boss.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "First to purify captures +1 ${boss.name} card!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SidiSkyLight
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun CompetitorPreviewCard(
    name: String,
    trophies: Int,
    deckIds: List<String>,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = BattleBoardCell,
        border = BorderStroke(2.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = SaffronGold, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
                Text(text = "$trophies", style = MaterialTheme.typography.labelSmall, color = SaffronGold)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                deckIds.take(3).forEach { id ->
                    val b = GameCatalog.bossById(id)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(b.bodyColor.copy(alpha = 0.25f))
                            .border(1.5.dp, b.rarity.frameColor, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        BossMonsterAvatar(boss = b, animateIdle = false, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}
