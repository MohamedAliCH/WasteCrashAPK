package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.GameCatalog
import com.example.model.UserProfile
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun HomeScreen(
    profile: UserProfile,
    liveMatchesCount: Int,
    claimableQuestsCount: Int,
    onBattleClicked: () -> Unit,
    onOpenDeck: () -> Unit,
    onOpenArenas: () -> Unit,
    onOpenSpectator: () -> Unit,
    onOpenQuests: () -> Unit,
    onSignOut: () -> Unit
) {
    val arena = GameCatalog.arenaForTrophies(profile.trophies)
    val nextThreshold = arena.nextArenaTrophies
    val trophyProgress = if (nextThreshold != null) {
        ((profile.trophies - arena.minTrophies).toFloat() / (nextThreshold - arena.minTrophies).toFloat()).coerceIn(0f, 1f)
    } else 1f
    val leadBoss = GameCatalog.bossById(profile.equippedDeckIds.firstOrNull() ?: "boss_b1")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Top Player Profile + WasteCrush Wordmark Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(CeramicBlueTop, CeramicBlueBottom)))
                        .border(2.5.dp, DeepNavyInk, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = profile.displayName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = DeepNavyInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Code: ${profile.friendCode}  •  ${profile.wins}W-${profile.losses}L",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedSlateText
                    )
                }
            }

            IconButton(
                onClick = onSignOut,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Sign out",
                    tint = MutedSlateText
                )
            }
        }

        // Economy & Competitive Stat Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatPill(
                value = "${profile.trophies}",
                icon = Icons.Default.EmojiEvents,
                badgeColor = SaffronGold,
                modifier = Modifier.weight(1f),
                onClick = onOpenArenas
            )
            StatPill(
                value = "${profile.ecoCoins}",
                icon = Icons.Default.MonetizationOn,
                badgeColor = LimePlayTop,
                modifier = Modifier.weight(1f),
                onClick = onOpenDeck
            )
            Surface(
                modifier = Modifier
                    .height(40.dp)
                    .clickable(onClick = onOpenQuests)
                    .testTag("home_quests_pill"),
                shape = RoundedCornerShape(50),
                color = if (claimableQuestsCount > 0) SaffronCream else SurfaceWhite,
                border = BorderStroke(2.dp, if (claimableQuestsCount > 0) SaffronGoldDark else DeepNavyInk),
                shadowElevation = 2.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TaskAlt,
                        contentDescription = "Daily Quests",
                        tint = if (claimableQuestsCount > 0) EcoEmeraldDark else CeramicBlueBottom,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (claimableQuestsCount > 0) "$claimableQuestsCount Ready!" else "Quests",
                        style = MaterialTheme.typography.labelMedium,
                        color = DeepNavyInk
                    )
                }
            }
        }

        // 2. Premium Sunlit Tunisian Arena Showcase Hero Card (homepage_22684661.jpg inspired!)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .clickable(onClick = onOpenArenas)
                .testTag("home_arena_card"),
            shape = RoundedCornerShape(26.dp),
            color = SurfaceWhite,
            border = BorderStroke(3.dp, DeepNavyInk),
            shadowElevation = 8.dp
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(206.dp)
                ) {
                    Image(
                        painter = painterResource(id = arena.bannerRes),
                        contentDescription = arena.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        DeepNavyInk.copy(alpha = 0.25f),
                                        Color.Transparent,
                                        DeepNavyInk.copy(alpha = 0.88f)
                                    )
                                )
                            )
                    )

                    // Glossy WasteCrush Logo Wordmark floating at top-center
                    WasteCrushLogoWordmark(
                        compact = true,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                    )

                    // Live featured Boss sprite standing proudly on the Tunisian landmark!
                    BossMonsterAvatar(
                        boss = leadBoss,
                        modifier = Modifier
                            .size(92.dp)
                            .align(Alignment.BottomEnd)
                            .padding(end = 10.dp, bottom = 10.dp)
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(0.70f)
                            .padding(14.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = arena.primaryColor,
                            border = BorderStroke(1.5.dp, Color.White)
                        ) {
                            Text(
                                text = arena.subtitle.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = arena.name,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = arena.places.joinToString(" • ") { it.name },
                            style = MaterialTheme.typography.labelSmall,
                            color = SaffronCream,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Trophy Progress Bar toward Next Arena
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (nextThreshold != null) "Next Arena at $nextThreshold Trophies" else "Max Arena Reached — Saharan Master!",
                            style = MaterialTheme.typography.labelMedium,
                            color = DeepNavyInk
                        )
                        Text(
                            text = "Trophy Road >",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = CeramicBlueBottom
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(19.dp)
                            .clip(RoundedCornerShape(50))
                            .background(SurfaceSoftBlue)
                            .border(2.dp, DeepNavyInk, RoundedCornerShape(50))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(trophyProgress.coerceAtLeast(0.06f))
                                .background(Brush.horizontalGradient(listOf(SaffronGold, GlowBeamGold)))
                        )
                        Text(
                            text = if (nextThreshold != null) "${profile.trophies} / $nextThreshold" else "${profile.trophies} Trophies",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = DeepNavyInk,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }

        // 3. Sparkling 3D Lime-Green PLAY 1v1 CTA (homepage_22684661.jpg style!)
        GlossyButton(
            text = "PLAY 1v1 BATTLE",
            onClick = onBattleClicked,
            icon = Icons.Default.Bolt,
            topColor = LimePlayTop,
            bottomColor = LimePlayBottom,
            lipColor = LimePlayLip,
            height = 68.dp,
            modifier = Modifier.fillMaxWidth(),
            tag = "battle_start_button"
        )

        // 4. Glossy Ceramic-Blue 3D Square Icon Tiles (MAP / DECKS / WATCH TV / QUESTS)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            GlossyIconTile(
                label = "ARENAS",
                icon = Icons.Default.Public,
                onClick = onOpenArenas,
                modifier = Modifier.weight(1f),
                tag = "home_tile_arenas"
            )
            GlossyIconTile(
                label = "ECO-DECK",
                icon = Icons.Default.Style,
                onClick = onOpenDeck,
                modifier = Modifier.weight(1f),
                tag = "home_tile_decks"
            )
            GlossyIconTile(
                label = "WATCH TV",
                icon = Icons.Default.LiveTv,
                onClick = onOpenSpectator,
                badgeText = if (liveMatchesCount > 0) "$liveMatchesCount" else null,
                modifier = Modifier.weight(1f),
                tag = "home_spectator_button"
            )
            GlossyIconTile(
                label = "QUESTS",
                icon = Icons.Default.Stars,
                onClick = onOpenQuests,
                topColor = Color(0xFFFFD54F),
                bottomColor = SaffronGold,
                lipColor = SaffronGoldDark,
                badgeText = if (claimableQuestsCount > 0) "$claimableQuestsCount" else null,
                modifier = Modifier.weight(1f),
                tag = "home_quests_button"
            )
        }

        // 5. Equipped 3-Slot Eco-Deck Showcase Strip with Real Boss Sprites
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenDeck),
            shape = RoundedCornerShape(22.dp),
            color = SurfaceWhite,
            border = BorderStroke(2.5.dp, DeepNavyInk),
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = CeramicBlueBottom,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Active Eco-Deck (3 Slots)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = DeepNavyInk
                        )
                    }
                    Text(
                        text = "Upgrade >",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = CeramicBlueBottom
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    profile.equippedDeckIds.take(3).forEach { bossId ->
                        val boss = GameCatalog.bossById(bossId)
                        val lvl = profile.bossLevels[bossId] ?: 1
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = boss.bodyColor.copy(alpha = 0.12f),
                            border = BorderStroke(2.dp, boss.rarity.frameColor)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                BossMonsterAvatar(
                                    boss = boss,
                                    animateIdle = false,
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = boss.name,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = DeepNavyInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Lv.$lvl • ${boss.preferredWaste.shortLabel}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                                    color = MutedSlateText,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
