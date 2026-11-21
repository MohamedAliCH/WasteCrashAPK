package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BossDefinition
import com.example.model.BossRarity
import com.example.model.GameCatalog
import com.example.model.UserProfile
import com.example.ui.components.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BossCollectionScreen(
    profile: UserProfile,
    onEquipBoss: (slotIndex: Int, bossId: String) -> Unit,
    onUpgradeBoss: (bossId: String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("ALL") }
    var inspectedBoss by remember { mutableStateOf<BossDefinition?>(null) }

    val filters = listOf(
        "ALL" to "All (15)",
        "A0" to "Beaches",
        "A1" to "Monuments",
        "A2" to "Desert",
        "EPIC" to "Epic Only"
    )

    val filteredBosses = remember(selectedFilter) {
        when (selectedFilter) {
            "A0" -> GameCatalog.bosses.filter { it.arenaId == 0 }
            "A1" -> GameCatalog.bosses.filter { it.arenaId == 1 }
            "A2" -> GameCatalog.bosses.filter { it.arenaId == 2 }
            "EPIC" -> GameCatalog.bosses.filter { it.rarity == BossRarity.EPIC }
            else -> GameCatalog.bosses
        }
    }

    val unlockedCount = GameCatalog.bosses.count { (profile.bossLevels[it.id] ?: 0) > 0 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header + Eco-Coins
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Eco-Deck & Bosses", style = MaterialTheme.typography.headlineMedium, color = DeepNavyInk)
                Text("Purified $unlockedCount / 15 Tunisian Bosses", style = MaterialTheme.typography.labelMedium, color = MutedSlateText)
            }
            StatPill(
                value = "${profile.ecoCoins}",
                icon = Icons.Default.MonetizationOn,
                badgeColor = EcoEmeraldBright
            )
        }

        // Active 3-Slot Eco-Deck Panel
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = SidiCobalt,
            border = BorderStroke(2.5.dp, DeepNavyInk)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = SaffronGold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ACTIVE ECO-DECK (3 SLOTS)", style = MaterialTheme.typography.titleSmall, color = Color.White)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    profile.equippedDeckIds.take(3).forEachIndexed { slotIdx, bossId ->
                        val boss = GameCatalog.bossById(bossId)
                        val lvl = profile.bossLevels[bossId] ?: 1
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { inspectedBoss = boss }
                                .testTag("deck_slot_card_$slotIdx"),
                            shape = RoundedCornerShape(14.dp),
                            color = SurfaceWhite,
                            border = BorderStroke(2.dp, boss.rarity.frameColor)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("SLOT ${slotIdx + 1}", style = MaterialTheme.typography.labelSmall, color = MutedSlateText)
                                BossMonsterAvatar(boss = boss, modifier = Modifier.size(46.dp))
                                Text(
                                    text = boss.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DeepNavyInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text("Lv.$lvl", style = MaterialTheme.typography.labelSmall, color = EcoEmeraldDark)
                            }
                        }
                    }
                }
            }
        }

        // Filter Chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filters) { (key, label) ->
                val selected = selectedFilter == key
                FilterChip(
                    selected = selected,
                    onClick = { selectedFilter = key },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SaffronGold,
                        selectedLabelColor = DeepNavyInk
                    )
                )
            }
        }

        // 15-Boss Collection Grid
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filteredBosses, key = { it.id }) { boss ->
                val lvl = profile.bossLevels[boss.id] ?: 0
                val copies = profile.bossCopies[boss.id] ?: 0
                val equipped = boss.id in profile.equippedDeckIds
                BossCardTile(
                    boss = boss,
                    level = lvl,
                    copies = copies,
                    isEquipped = equipped,
                    onClick = { inspectedBoss = boss },
                    modifier = Modifier.testTag("boss_card_${boss.id}")
                )
            }
        }
    }

    // Boss Detail, Upgrade & Slot Assignment Bottom Sheet
    inspectedBoss?.let { boss ->
        val lvl = profile.bossLevels[boss.id] ?: 0
        val unlocked = lvl > 0
        val copies = profile.bossCopies[boss.id] ?: 0
        val neededCopies = GameCatalog.copiesNeededForNextLevel(lvl.coerceAtLeast(1), boss.rarity)
        val neededCoins = GameCatalog.coinsNeededForNextLevel(lvl.coerceAtLeast(1), boss.rarity)
        val canUpgrade = unlocked && copies >= neededCopies && profile.ecoCoins >= neededCoins && lvl < 10

        ModalBottomSheet(
            onDismissRequest = { inspectedBoss = null },
            containerColor = SurfaceWhite
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(82.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(boss.bodyColor.copy(alpha = 0.16f))
                            .border(2.5.dp, boss.rarity.frameColor, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        BossMonsterAvatar(boss = boss, modifier = Modifier.size(72.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RarityChip(boss.rarity)
                            Text("Arena ${boss.arenaId}", style = MaterialTheme.typography.labelSmall, color = MutedSlateText)
                        }
                        Text(boss.name, style = MaterialTheme.typography.headlineMedium, color = DeepNavyInk)
                        Text(boss.title, style = MaterialTheme.typography.bodyMedium, color = MutedSlateText)
                        Text(
                            text = if (unlocked) "Level $lvl  •  $copies/$neededCopies copies" else "Locked — Win in Arena ${boss.arenaId}+ to capture",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (unlocked) EcoEmeraldDark else CarthageCoral
                        )
                    }
                }

                // Active Battle Ability Box
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceSoftBlue,
                    border = BorderStroke(1.5.dp, DeepNavyInk)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("ACTIVE SKILL: ${boss.abilityName}", style = MaterialTheme.typography.titleSmall, color = SidiCobalt)
                        Text(boss.abilityDescription, style = MaterialTheme.typography.bodyMedium, color = DeepNavyInk)
                        Text(
                            text = "Power: ${boss.abilityDamageAtLevel(lvl.coerceAtLeast(1))} DMG  •  +${boss.tileBonusPercentAtLevel(lvl.coerceAtLeast(1))}% on ${boss.preferredWaste.displayName}s",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = EcoEmeraldDark
                        )
                    }
                }

                Text(boss.lore, style = MaterialTheme.typography.bodyMedium, color = MutedSlateText)

                // Upgrade CTA
                if (unlocked) {
                    GlossyButton(
                        text = if (lvl >= 10) "MAX LEVEL REACHED" else "Upgrade to Lv.${lvl + 1} ($neededCoins Coins)",
                        onClick = { onUpgradeBoss(boss.id) },
                        icon = Icons.Default.ArrowUpward,
                        enabled = canUpgrade,
                        modifier = Modifier.fillMaxWidth(),
                        tag = "upgrade_boss_button"
                    )

                    // Equip into Slot 1 / 2 / 3
                    Text("Equip into Eco-Deck Slot:", style = MaterialTheme.typography.labelMedium, color = DeepNavyInk)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (slot in 0..2) {
                            val isHere = profile.equippedDeckIds.getOrNull(slot) == boss.id
                            OutlinedButton(
                                onClick = {
                                    onEquipBoss(slot, boss.id)
                                    inspectedBoss = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("equip_slot_$slot"),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(2.dp, if (isHere) EcoEmerald else SidiCobalt)
                            ) {
                                Text(
                                    text = if (isHere) "In Slot ${slot + 1}" else "Slot ${slot + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isHere) EcoEmeraldDark else SidiCobalt
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}
