package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.example.ui.components.BossMonsterAvatar
import com.example.ui.components.GlossyButton
import com.example.ui.components.RarityChip
import com.example.ui.components.TunisiaMapCanvas
import com.example.ui.theme.*

@Composable
fun TunisiaMapScreen(
    profile: UserProfile,
    initialPlaceId: String? = null,
    onBack: () -> Unit,
    onStartBattle: () -> Unit
) {
    BackHandler(onBack = onBack)

    var selectedArenaFilter by remember { mutableIntStateOf(-1) }
    val currentArena = GameCatalog.arenaForTrophies(profile.trophies)
    val defaultPlace = remember(initialPlaceId, currentArena) {
        initialPlaceId?.let { GameCatalog.placeById(it) } ?: currentArena.places.first()
    }
    var selectedPlaceId by remember(initialPlaceId) { mutableStateOf(defaultPlace.id) }

    val visiblePlaces = remember(selectedArenaFilter) {
        if (selectedArenaFilter == -1) GameCatalog.allPlaces
        else GameCatalog.allPlaces.filter { it.arenaId == selectedArenaFilter }
    }

    val selectedPlace = remember(selectedPlaceId, visiblePlaces) {
        visiblePlaces.firstOrNull { it.id == selectedPlaceId } ?: visiblePlaces.first()
    }
    val selectedArena = GameCatalog.arenaById(selectedPlace.arenaId)
    val selectedUnlocked = profile.trophies >= selectedArena.minTrophies
    val guardianBoss = GameCatalog.bossById(selectedPlace.guardianBossId)
    val guardianUnlocked = (profile.bossLevels[guardianBoss.id] ?: 0) > 0
    val unlockedPlacesCount = GameCatalog.allPlaces.count {
        profile.trophies >= GameCatalog.arenaById(it.arenaId).minTrophies
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0C1E42), DarkBgPrimary, Color(0xFF040A18))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceCard)
                            .border(1.5.dp, DarkBorderSubtle, CircleShape)
                            .testTag("tunisia_map_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextOnDark
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Tunisia Eco-Map",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = TextOnDark
                        )
                        Text(
                            text = "Tap any pin across Tunisia to inspect its landmark",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMutedOnDark
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = SaffronGold.copy(alpha = 0.20f),
                    border = BorderStroke(1.5.dp, SaffronGold)
                ) {
                    Text(
                        text = "$unlockedPlacesCount/15 Open",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = GlowBeamGold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Arena Filter Chips
            val filters = listOf(
                -1 to "All (15)",
                0 to "Beaches",
                1 to "Monuments",
                2 to "Sahara"
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filters) { (arenaId, label) ->
                    val selected = selectedArenaFilter == arenaId
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedArenaFilter = arenaId
                            val nextList = if (arenaId == -1) GameCatalog.allPlaces else GameCatalog.allPlaces.filter { it.arenaId == arenaId }
                            if (nextList.none { it.id == selectedPlaceId }) {
                                selectedPlaceId = nextList.first().id
                            }
                        },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = DarkSurfaceCard,
                            labelColor = TextMutedOnDark,
                            selectedContainerColor = SaffronGold,
                            selectedLabelColor = DeepNavyInk
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = DarkBorderSubtle,
                            selectedBorderColor = GlowBeamGold
                        )
                    )
                }
            }

            // Interactive Geographic Map of Tunisia
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(330.dp)
                    .testTag("tunisia_interactive_map"),
                shape = RoundedCornerShape(26.dp),
                color = Color(0xFF040B1A),
                border = BorderStroke(
                    2.5.dp,
                    Brush.linearGradient(listOf(GlowBeamGold, SaffronGold, CeramicBlueTop))
                ),
                shadowElevation = 12.dp
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    TunisiaMapCanvas(
                        places = visiblePlaces,
                        selectedPlaceId = selectedPlace.id,
                        userTrophies = profile.trophies,
                        onPlaceSelected = { tapped -> selectedPlaceId = tapped.id },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Floating North/South Legend Pill
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF08142B).copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, DarkBorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LegendDot(CeramicBlueTop, "Beaches")
                            LegendDot(SaffronGold, "Heritage")
                            LegendDot(GlowBeamOrange, "Sahara")
                        }
                    }

                    // Active Pin Pill at bottom-right
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp),
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF08142B).copy(alpha = 0.90f),
                        border = BorderStroke(1.dp, SaffronGold.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Place, contentDescription = null, tint = SaffronGold, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = selectedPlace.name,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = TextOnDark
                            )
                        }
                    }
                }
            }

            // Quick Location Jump Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visiblePlaces, key = { it.id }) { place ->
                    val isSelected = place.id == selectedPlace.id
                    val placeArena = GameCatalog.arenaById(place.arenaId)
                    val isUnlocked = profile.trophies >= placeArena.minTrophies
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { selectedPlaceId = place.id }
                            .testTag("map_chip_${place.id}"),
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) SaffronGold else DarkSurfaceCard,
                        border = BorderStroke(
                            1.5.dp,
                            if (isSelected) Color.White else if (isUnlocked) DarkBorderSubtle else Color(0xFF1E3259)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isUnlocked) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (isSelected) DeepNavyInk else TextMutedOnDark,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = place.name,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) DeepNavyInk else if (isUnlocked) TextOnDark else TextMutedOnDark
                            )
                        }
                    }
                }
            }

            // Selected Location Detail Showcase Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = DarkSurfaceCard,
                border = BorderStroke(2.dp, if (selectedUnlocked) SaffronGold.copy(alpha = 0.8f) else DarkBorderSubtle),
                shadowElevation = 10.dp
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(136.dp)
                    ) {
                        Image(
                            painter = painterResource(id = selectedPlace.imageRes),
                            contentDescription = selectedPlace.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(if (selectedUnlocked) 1f else 0.55f)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, DarkBgPrimary.copy(alpha = 0.92f))
                                    )
                                )
                        )

                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp),
                            shape = RoundedCornerShape(50),
                            color = if (selectedUnlocked) EcoEmerald else DarkBgPrimary.copy(alpha = 0.90f),
                            border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.7f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (selectedUnlocked) Icons.Default.CheckCircle else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (selectedUnlocked) "${selectedPlace.regionTag} • UNLOCKED" else "Requires ${selectedArena.minTrophies}+ Trophies",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(14.dp)
                        ) {
                            Text(
                                text = selectedPlace.name,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White
                            )
                            Text(
                                text = "${selectedPlace.heritageHighlight} • ${selectedArena.name}",
                                style = MaterialTheme.typography.labelMedium,
                                color = SaffronCream
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = selectedPlace.ecoFact,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextOnDark
                        )

                        // Guardian Boss Card
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = DarkSurfaceElevated,
                            border = BorderStroke(1.8.dp, guardianBoss.rarity.frameColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(guardianBoss.bodyColor.copy(alpha = 0.22f))
                                        .border(1.5.dp, guardianBoss.rarity.frameColor, RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BossMonsterAvatar(
                                        boss = guardianBoss,
                                        animateIdle = selectedUnlocked,
                                        modifier = Modifier.size(54.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "LOCATION GUARDIAN",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SaffronGold
                                        )
                                        RarityChip(guardianBoss.rarity)
                                    }
                                    Text(
                                        text = guardianBoss.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = TextOnDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (guardianUnlocked) "${guardianBoss.abilityName} • Captured" else "${guardianBoss.abilityName} • Win battles to capture",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (guardianUnlocked) EcoEmeraldBright else TextMutedOnDark
                                    )
                                }
                            }
                        }

                        if (selectedUnlocked) {
                            GlossyButton(
                                text = "PLAY 1v1 IN ${selectedArena.subtitle.uppercase()}",
                                onClick = onStartBattle,
                                icon = Icons.Default.Bolt,
                                topColor = LimePlayTop,
                                bottomColor = LimePlayBottom,
                                lipColor = LimePlayLip,
                                height = 54.dp,
                                modifier = Modifier.fillMaxWidth(),
                                tag = "map_play_cta"
                            )
                        } else {
                            val needed = (selectedArena.minTrophies - profile.trophies).coerceAtLeast(0)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF0A162D),
                                border = BorderStroke(1.5.dp, SaffronGold.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = SaffronGold, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Earn $needed more Trophies to unlock ${selectedPlace.name} in 1v1 Roulette!",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SaffronCream
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextOnDark
        )
    }
}
