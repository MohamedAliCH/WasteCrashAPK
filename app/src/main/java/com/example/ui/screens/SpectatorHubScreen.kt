package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.components.BossMonsterAvatar
import com.example.ui.components.GlossyButton
import com.example.ui.theme.*

@Composable
fun SpectatorHubScreen(
    profile: UserProfile,
    activeMatchesState: UiState<List<LiveMatch>>,
    replaysState: UiState<List<MatchReplay>>,
    publicProfilesState: UiState<List<PublicProfile>>,
    onWatchLiveMatch: (String) -> Unit,
    onWatchReplay: (String) -> Unit,
    onAddFriend: (String) -> Unit,
    onRemoveFriend: (String, String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Live TV, 1 = Friends & Replays
    var selectedTrophyBracket by remember { mutableIntStateOf(-1) } // -1 = All, 0 = 0-399, 1 = 400-899, 2 = 900+
    var replayFilterFriendsOnly by remember { mutableStateOf(false) }
    var friendCodeInput by remember { mutableStateOf("") }
    var copiedNotice by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val allPublicProfiles = (publicProfilesState as? UiState.Success)?.data ?: emptyList()
    val friendProfiles = remember(allPublicProfiles, profile.friendUids) {
        allPublicProfiles.filter { it.userId in profile.friendUids }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("WasteCrush TV & Replays", style = MaterialTheme.typography.headlineMedium, color = DeepNavyInk)
                Text("Spectate live Trophy races or watch friends' battles", style = MaterialTheme.typography.labelMedium, color = MutedSlateText)
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = CarthageCoral,
                border = BorderStroke(1.5.dp, DeepNavyInk)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    val liveCount = (activeMatchesState as? UiState.Success)?.data?.size ?: 0
                    Text("$liveCount LIVE", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }

        // Segmented Mode Switcher: Live Arena TV vs Friends & Replays
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceSoftBlue)
                .border(2.dp, DeepNavyInk, RoundedCornerShape(16.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TabPillButton(
                text = "Live Matches",
                icon = Icons.Default.LiveTv,
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                modifier = Modifier.weight(1f).testTag("spectator_tab_live")
            )
            TabPillButton(
                text = "Friends & Replays",
                icon = Icons.Default.History,
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                modifier = Modifier.weight(1f).testTag("spectator_tab_replays")
            )
        }

        if (selectedTab == 0) {
            // Trophy Bracket Filter Row
            val brackets = listOf(
                -1 to "All Trophies",
                0 to "0–399 Beaches",
                1 to "400–899 Monuments",
                2 to "900+ Desert"
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(brackets) { (bracketId, label) ->
                    FilterChip(
                        selected = selectedTrophyBracket == bracketId,
                        onClick = { selectedTrophyBracket = bracketId },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SaffronGold,
                            selectedLabelColor = DeepNavyInk
                        )
                    )
                }
            }

            when (activeMatchesState) {
                is UiState.Loading -> {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SidiCobalt)
                    }
                }
                is UiState.Error -> {
                    EmptyInfoCard(
                        icon = Icons.Default.ErrorOutline,
                        title = "Stream Unavailable",
                        subtitle = activeMatchesState.message
                    )
                }
                is UiState.Success -> {
                    val matches = activeMatchesState.data.filter {
                        selectedTrophyBracket == -1 || it.trophyBracket == selectedTrophyBracket
                    }
                    if (matches.isEmpty()) {
                        EmptyInfoCard(
                            icon = Icons.Default.LiveTv,
                            title = "No Live Matches in This Trophy Tier Right Now",
                            subtitle = "Live 1v1 races appear here automatically as players battle! Switch to Friends & Replays to watch recorded matches and inspect opponent screens & Eco-Decks."
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(matches, key = { it.id }) { match ->
                                LiveMatchSpectateCard(
                                    match = match,
                                    onWatch = { onWatchLiveMatch(match.id) }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Friends Manager + Friend & Recent Replays
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Your Friend Code + Add Friend Input Card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = SurfaceWhite,
                        border = BorderStroke(2.dp, DeepNavyInk)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Your Friend Code", style = MaterialTheme.typography.labelSmall, color = MutedSlateText)
                                    Text(profile.friendCode, style = MaterialTheme.typography.titleLarge, color = SidiCobalt)
                                }
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(profile.friendCode))
                                        copiedNotice = true
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Friend Code", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (copiedNotice) "Copied!" else "Copy Code")
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = friendCodeInput,
                                    onValueChange = { friendCodeInput = it.uppercase().take(8) },
                                    placeholder = { Text("Enter Friend Code (e.g. TN4K9P)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("friend_code_input"),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                GlossyButton(
                                    text = "Add",
                                    onClick = {
                                        if (friendCodeInput.isNotBlank()) {
                                            onAddFriend(friendCodeInput)
                                            friendCodeInput = ""
                                        }
                                    },
                                    height = 52.dp,
                                    modifier = Modifier.width(86.dp),
                                    tag = "add_friend_button"
                                )
                            }

                            if (friendProfiles.isNotEmpty()) {
                                Text("Friends (${friendProfiles.size}):", style = MaterialTheme.typography.labelMedium, color = DeepNavyInk)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(friendProfiles, key = { it.userId }) { f ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = SurfaceSoftBlue,
                                            border = BorderStroke(1.5.dp, SidiCobalt)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(f.displayName, style = MaterialTheme.typography.labelMedium, color = DeepNavyInk)
                                                    Text("${f.trophies} Trophies • ${f.friendCode}", style = MaterialTheme.typography.labelSmall, color = MutedSlateText)
                                                }
                                                Spacer(Modifier.width(6.dp))
                                                IconButton(
                                                    onClick = { onRemoveFriend(f.userId, f.displayName) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Remove friend", tint = MutedSlateText, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Filter Toggle: All Replays vs Friends Only
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recent Battle Replays", style = MaterialTheme.typography.titleMedium, color = DeepNavyInk)
                        FilterChip(
                            selected = replayFilterFriendsOnly,
                            onClick = { replayFilterFriendsOnly = !replayFilterFriendsOnly },
                            label = { Text(if (replayFilterFriendsOnly) "Friends Only" else "All + Mine") },
                            leadingIcon = { Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }

                when (replaysState) {
                    is UiState.Loading -> {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = SidiCobalt)
                            }
                        }
                    }
                    is UiState.Error -> {
                        item {
                            EmptyInfoCard(Icons.Default.ErrorOutline, "Replays Unavailable", replaysState.message)
                        }
                    }
                    is UiState.Success -> {
                        val replays = replaysState.data.filter { rep ->
                            if (!replayFilterFriendsOnly) true
                            else rep.participantUids.any { it in profile.friendUids }
                        }
                        if (replays.isEmpty()) {
                            item {
                                EmptyInfoCard(
                                    icon = Icons.Default.PlayCircleOutline,
                                    title = if (replayFilterFriendsOnly) "No Friends' Replays Yet" else "No Recorded Replays Yet",
                                    subtitle = "Complete a 1v1 Battle or add a friend using their Friend Code to watch full 7x7 board replays and inspect opponent decks!"
                                )
                            }
                        } else {
                            items(replays, key = { it.id }) { replay ->
                                val involvesFriend = replay.participantUids.any { it in profile.friendUids }
                                ReplayListCard(
                                    replay = replay,
                                    isFriendMatch = involvesFriend,
                                    onWatch = { onWatchReplay(replay.id) }
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
private fun TabPillButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) SidiCobalt else Color.Transparent
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) Color.White else DeepNavyInk, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) Color.White else DeepNavyInk)
        }
    }
}

@Composable
private fun LiveMatchSpectateCard(
    match: LiveMatch,
    onWatch: () -> Unit
) {
    val place = GameCatalog.placeById(match.placeId)
    val boss = GameCatalog.bossById(match.bossId)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onWatch),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceWhite,
        border = BorderStroke(2.5.dp, DeepNavyInk),
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(CarthageCoral))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${place.name} • Arena ${match.arenaId}",
                        style = MaterialTheme.typography.labelMedium,
                        color = SidiCobalt
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Visibility, contentDescription = null, tint = MutedSlateText, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${match.spectatorCount}", style = MaterialTheme.typography.labelMedium, color = DeepNavyInk)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(match.player1.name, style = MaterialTheme.typography.titleSmall, color = DeepNavyInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${match.player1.trophies} 🏆 • ${match.player1.restorationPct}% Clean", style = MaterialTheme.typography.labelSmall, color = EcoEmeraldDark)
                }
                BossMonsterAvatar(boss = boss, modifier = Modifier.size(44.dp))
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(match.player2.name, style = MaterialTheme.typography.titleSmall, color = DeepNavyInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${match.player2.trophies} 🏆 • ${match.player2.restorationPct}% Clean", style = MaterialTheme.typography.labelSmall, color = CarthageCoral)
                }
            }

            GlossyButton(
                text = "Spectate Live (Both Screens & Decks)",
                onClick = onWatch,
                icon = Icons.Default.Visibility,
                topColor = SidiAzure,
                bottomColor = SidiCobalt,
                lipColor = SidiCobaltDark,
                height = 46.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ReplayListCard(
    replay: MatchReplay,
    isFriendMatch: Boolean,
    onWatch: () -> Unit
) {
    val place = GameCatalog.placeById(replay.placeId)
    val boss = GameCatalog.bossById(replay.bossId)
    val p1Won = replay.winnerUid == replay.player1Summary.uid

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onWatch),
        shape = RoundedCornerShape(18.dp),
        color = SurfaceWhite,
        border = BorderStroke(2.dp, if (isFriendMatch) EcoEmerald else DeepNavyInk),
        shadowElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isFriendMatch) {
                        Surface(shape = RoundedCornerShape(50), color = EcoMintSoft) {
                            Text("FRIEND", style = MaterialTheme.typography.labelSmall, color = EcoEmeraldDark, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                    Text("${place.name} • ${replay.durationSec}s", style = MaterialTheme.typography.labelMedium, color = SidiCobalt)
                }
                Text("Boss: ${boss.name}", style = MaterialTheme.typography.labelSmall, color = MutedSlateText)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${replay.player1Summary.name}${if (p1Won) " (WIN)" else ""}",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (p1Won) EcoEmeraldDark else DeepNavyInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("${replay.player1Summary.totalDamage} DMG • ${replay.player1Summary.trophies} 🏆", style = MaterialTheme.typography.labelSmall, color = MutedSlateText)
                }
                Text("VS", style = MaterialTheme.typography.labelLarge, color = SaffronGoldDark)
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${replay.player2Summary.name}${if (!p1Won) " (WIN)" else ""}",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (!p1Won) EcoEmeraldDark else DeepNavyInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("${replay.player2Summary.totalDamage} DMG • ${replay.player2Summary.trophies} 🏆", style = MaterialTheme.typography.labelSmall, color = MutedSlateText)
                }
            }

            GlossyButton(
                text = "Watch Replay & Inspect Decks",
                onClick = onWatch,
                icon = Icons.Default.PlayArrow,
                height = 46.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun EmptyInfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceWhite,
        border = BorderStroke(2.dp, DeepNavyInk)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = SidiCobalt, modifier = Modifier.size(44.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = DeepNavyInk)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MutedSlateText)
        }
    }
}
