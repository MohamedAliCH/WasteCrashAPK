package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.QuestClaimOutcome
import com.example.model.*
import com.example.ui.components.BossMonsterAvatar
import com.example.ui.components.GlossyButton
import com.example.ui.components.RarityChip
import com.example.ui.components.StatPill
import com.example.ui.theme.*

@Composable
fun QuestsScreen(
    profile: UserProfile,
    claimedOutcome: QuestClaimOutcome?,
    onClaimQuest: (QuestDefinition) -> Unit,
    onDismissClaimOutcome: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf(QuestCategory.DAILY) }

    val quests = remember(selectedCategory) {
        GameCatalog.quests.filter { it.category == selectedCategory }
    }

    val dailyReady = GameCatalog.quests.count { q ->
        q.category == QuestCategory.DAILY &&
            (profile.questProgress[q.id] ?: 0) >= q.target &&
            profile.questClaimed[q.id] != true
    }
    val challengeReady = GameCatalog.quests.count { q ->
        q.category == QuestCategory.CHALLENGE &&
            (profile.questProgress[q.id] ?: 0) >= q.target &&
            profile.questClaimed[q.id] != true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header + Eco-Coins Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Quests & Challenges", style = MaterialTheme.typography.headlineMedium, color = DeepNavyInk)
                Text("Earn Eco-Coins & Boss Cards every battle", style = MaterialTheme.typography.labelMedium, color = MutedSlateText)
            }
            StatPill(
                value = "${profile.ecoCoins}",
                icon = Icons.Default.MonetizationOn,
                badgeColor = EcoEmeraldBright
            )
        }

        // Daily Reset Banner
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = SidiCobalt,
            border = BorderStroke(2.5.dp, DeepNavyInk)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = SaffronGold, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Active Cycle: ${profile.dailyQuestSeedDate}", style = MaterialTheme.typography.titleSmall, color = Color.White)
                        Text("Progress updates automatically after every 1v1 match!", style = MaterialTheme.typography.labelSmall, color = SidiSkyLight)
                    }
                }
            }
        }

        // Category Switcher: Daily Quests vs Elite Challenges
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceSoftBlue)
                .border(2.dp, DeepNavyInk, RoundedCornerShape(16.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            QuestTabChip(
                label = if (dailyReady > 0) "Daily Quests ($dailyReady)" else "Daily Quests",
                selected = selectedCategory == QuestCategory.DAILY,
                onClick = { selectedCategory = QuestCategory.DAILY },
                modifier = Modifier.weight(1f).testTag("quests_tab_daily")
            )
            QuestTabChip(
                label = if (challengeReady > 0) "Elite Challenges ($challengeReady)" else "Elite Challenges",
                selected = selectedCategory == QuestCategory.CHALLENGE,
                onClick = { selectedCategory = QuestCategory.CHALLENGE },
                modifier = Modifier.weight(1f).testTag("quests_tab_challenges")
            )
        }

        // Quests List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(quests, key = { it.id }) { quest ->
                val progress = (profile.questProgress[quest.id] ?: 0).coerceAtMost(quest.target)
                val claimed = profile.questClaimed[quest.id] == true
                val completed = progress >= quest.target

                QuestCardItem(
                    quest = quest,
                    progress = progress,
                    completed = completed,
                    claimed = claimed,
                    onClaim = { onClaimQuest(quest) }
                )
            }
        }
    }

    // Celebratory Quest Reward Dialog (Eco-Coins + Boss Cards!)
    if (claimedOutcome != null) {
        val boss = GameCatalog.bossById(claimedOutcome.rewardedBossId)
        AlertDialog(
            onDismissRequest = onDismissClaimOutcome,
            containerColor = SurfaceWhite,
            title = {
                Text("Quest Reward Claimed!", style = MaterialTheme.typography.headlineMedium, color = DeepNavyInk)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(boss.bodyColor.copy(alpha = 0.18f))
                            .border(3.dp, boss.rarity.frameColor, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        BossMonsterAvatar(boss = boss, modifier = Modifier.size(80.dp))
                    }
                    RarityChip(boss.rarity)
                    Text(
                        text = "+${claimedOutcome.cardsAwarded}x ${boss.name} Cards",
                        style = MaterialTheme.typography.titleLarge,
                        color = DeepNavyInk
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = EcoMintSoft,
                        border = BorderStroke(1.5.dp, EcoEmeraldDark)
                    ) {
                        Text(
                            text = "+${claimedOutcome.coinsAwarded} Eco-Coins Added!",
                            style = MaterialTheme.typography.titleSmall,
                            color = EcoEmeraldDark,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {
                GlossyButton(
                    text = "Awesome!",
                    onClick = onDismissClaimOutcome,
                    height = 46.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}

@Composable
private fun QuestTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) SaffronGold else Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = DeepNavyInk
            )
        }
    }
}

@Composable
private fun QuestCardItem(
    quest: QuestDefinition,
    progress: Int,
    completed: Boolean,
    claimed: Boolean,
    onClaim: () -> Unit
) {
    val frac = (progress.toFloat() / quest.target.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quest_card_${quest.id}"),
        shape = RoundedCornerShape(20.dp),
        color = if (claimed) Color(0xFFE9EFF7) else SurfaceWhite,
        border = BorderStroke(
            width = if (completed && !claimed) 3.dp else 2.dp,
            color = if (completed && !claimed) EcoEmerald else DeepNavyInk
        ),
        shadowElevation = if (claimed) 1.dp else 4.dp
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(quest.title, style = MaterialTheme.typography.titleMedium, color = DeepNavyInk)
                        RarityChip(quest.rewardRarity)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(quest.description, style = MaterialTheme.typography.bodyMedium, color = MutedSlateText)
                }
            }

            // Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(50))
                    .background(SurfaceSoftBlue)
                    .border(1.5.dp, DeepNavyInk, RoundedCornerShape(50))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(frac)
                        .background(
                            Brush.horizontalGradient(
                                if (completed) listOf(EcoEmeraldBright, EcoEmerald) else listOf(SidiAzure, SidiCobalt)
                            )
                        )
                )
                Text(
                    text = "$progress / ${quest.target}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (frac > 0.45f) Color.White else DeepNavyInk,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Rewards Strip + Claim Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(10.dp), color = EcoMintSoft) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = EcoEmeraldDark, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("+${quest.coinReward}", style = MaterialTheme.typography.labelMedium, color = EcoEmeraldDark)
                        }
                    }
                    Surface(shape = RoundedCornerShape(10.dp), color = SaffronCream) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Style, contentDescription = null, tint = SaffronGoldDark, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("+${quest.cardRewardCount} ${quest.rewardRarity.label} Card", style = MaterialTheme.typography.labelMedium, color = DeepNavyInk)
                        }
                    }
                }

                when {
                    claimed -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Claimed", tint = EcoEmeraldDark, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Claimed", style = MaterialTheme.typography.labelMedium, color = EcoEmeraldDark)
                        }
                    }
                    completed -> {
                        GlossyButton(
                            text = "CLAIM",
                            onClick = onClaim,
                            height = 44.dp,
                            modifier = Modifier.width(106.dp),
                            tag = "claim_quest_${quest.id}"
                        )
                    }
                    else -> {
                        Text("In Progress", style = MaterialTheme.typography.labelSmall, color = MutedSlateText)
                    }
                }
            }
        }
    }
}
