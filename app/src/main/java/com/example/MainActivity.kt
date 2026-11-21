package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AuthManager
import com.example.data.WasteCrushRepository
import com.example.model.GameCatalog
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.viewmodel.*
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WasteCrushTheme {
                WasteCrushRoot()
            }
        }
    }
}

private enum class HubTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    BATTLE("Battle", Icons.Filled.Bolt, Icons.Outlined.Bolt, "nav_battle"),
    DECK("Decks", Icons.Filled.Style, Icons.Outlined.Style, "nav_decks"),
    ARENAS("Arenas", Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents, "nav_arenas"),
    WATCH("Watch", Icons.Filled.LiveTv, Icons.Outlined.LiveTv, "nav_watch"),
    QUESTS("Quests", Icons.Filled.TaskAlt, Icons.Outlined.TaskAlt, "nav_quests")
}

private sealed interface FullScreenOverlay {
    data object None : FullScreenOverlay
    data object BattleFlow : FullScreenOverlay
    data class SpectatorLive(val matchId: String) : FullScreenOverlay
    data class SpectatorReplay(val replayId: String) : FullScreenOverlay
}

@Composable
private fun WasteCrushRoot() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    if (currentUser == null) {
        AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
    } else {
        val uid = currentUser!!.uid
        key(uid) {
            AuthenticatedWasteCrushApp(currentUserId = uid)
        }
    }
}

@Composable
private fun AuthenticatedWasteCrushApp(currentUserId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val repository = remember(currentUserId) { WasteCrushRepository(context) }

    val mainVm: MainViewModel = viewModel(
        key = "main_$currentUserId",
        factory = MainViewModel.factory(repository)
    )
    val matchmakingVm: MatchmakingViewModel = viewModel(
        key = "matchmaking_$currentUserId",
        factory = MatchmakingViewModel.factory(repository)
    )
    val spectatorVm: SpectatorViewModel = viewModel(
        key = "spectator_$currentUserId",
        factory = SpectatorViewModel.factory(repository)
    )

    val profileState by mainVm.profileState.collectAsStateWithLifecycle()
    val publicProfilesState by mainVm.publicProfilesState.collectAsStateWithLifecycle()
    val activeMatchesState by mainVm.activeMatchesState.collectAsStateWithLifecycle()
    val replaysState by mainVm.replaysState.collectAsStateWithLifecycle()
    val statusBanner by mainVm.statusBanner.collectAsStateWithLifecycle()
    val claimedOutcome by mainVm.claimedOutcome.collectAsStateWithLifecycle()

    val matchmakingState by matchmakingVm.matchmakingState.collectAsStateWithLifecycle()
    val sessionState by matchmakingVm.sessionState.collectAsStateWithLifecycle()
    val spectatorState by spectatorVm.state.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(HubTab.BATTLE) }
    var overlay by remember { mutableStateOf<FullScreenOverlay>(FullScreenOverlay.None) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            mainVm.clearBanner()
        }
    }

    // Handle full-screen immersive flows (Matchmaking, Battle, Post-Match, Spectator Theatre)
    when (val activeOverlay = overlay) {
        is FullScreenOverlay.BattleFlow -> {
            val currentProfile = (profileState as? UiState.Success)?.data
            when (matchmakingState.phase) {
                MatchmakingPhase.SEARCHING, MatchmakingPhase.ROULETTE -> {
                    MatchmakingRouletteScreen(
                        state = matchmakingState,
                        onCancel = {
                            matchmakingVm.cancelAndReset()
                            overlay = FullScreenOverlay.None
                        }
                    )
                }
                MatchmakingPhase.IN_BATTLE -> {
                    BattleScreen(
                        state = sessionState,
                        onTileTap = matchmakingVm::onTileTapped,
                        onTileSwipe = matchmakingVm::onTileSwiped,
                        onActivateBossSlot = matchmakingVm::activateEcoDeckBoss,
                        onToggleSound = matchmakingVm::toggleSound,
                        onToggleRivalPeek = matchmakingVm::toggleRivalPeek,
                        onForfeit = {
                            matchmakingVm.cancelAndReset()
                            overlay = FullScreenOverlay.None
                        }
                    )
                }
                MatchmakingPhase.FINISHED -> {
                    PostMatchScreen(
                        state = sessionState,
                        profile = currentProfile,
                        onPlayAgain = {
                            if (currentProfile != null) {
                                matchmakingVm.startMatchmaking(currentProfile)
                            }
                        },
                        onWatchReplay = { replayId ->
                            matchmakingVm.cancelAndReset()
                            spectatorVm.openReplay(replayId)
                            overlay = FullScreenOverlay.SpectatorReplay(replayId)
                        },
                        onReturnHome = {
                            matchmakingVm.cancelAndReset()
                            overlay = FullScreenOverlay.None
                        }
                    )
                }
                MatchmakingPhase.IDLE -> {
                    overlay = FullScreenOverlay.None
                }
            }
            return
        }

        is FullScreenOverlay.SpectatorLive, is FullScreenOverlay.SpectatorReplay -> {
            SpectatorTheatreScreen(
                state = spectatorState,
                onSetPerspective = spectatorVm::setPerspective,
                onToggleDeckInspector = spectatorVm::toggleDeckInspector,
                onTogglePlayPause = spectatorVm::toggleReplayPlayPause,
                onCycleSpeed = spectatorVm::cycleReplaySpeed,
                onScrubFrame = spectatorVm::scrubToFrame,
                onSendCheer = spectatorVm::sendCheer,
                onBack = {
                    spectatorVm.stopWatching()
                    overlay = FullScreenOverlay.None
                }
            )
            return
        }

        FullScreenOverlay.None -> {
            // Render adaptive 5-tab Hub Shell
        }
    }

    val liveMatchesCount = (activeMatchesState as? UiState.Success)?.data?.size ?: 0
    val currentProfile = (profileState as? UiState.Success)?.data
    val claimableQuestsCount = remember(currentProfile) {
        if (currentProfile == null) 0
        else GameCatalog.quests.count { q ->
            (currentProfile.questProgress[q.id] ?: 0) >= q.target && currentProfile.questClaimed[q.id] != true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(LimestoneBg)
    ) {
        val useNavRail = maxWidth >= 680.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useNavRail) {
                NavigationRail(
                    containerColor = SurfaceWhite,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
                ) {
                    HubTab.entries.forEach { tab ->
                        val selected = currentTab == tab
                        NavigationRailItem(
                            selected = selected,
                            onClick = { currentTab = tab },
                            icon = {
                                TabIconWithBadge(
                                    tab = tab,
                                    selected = selected,
                                    claimableQuests = claimableQuestsCount,
                                    liveCount = liveMatchesCount
                                )
                            },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag(tab.tag)
                        )
                    }
                }
            }

            Scaffold(
                containerColor = LimestoneBg,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    if (!useNavRail) {
                        NavigationBar(
                            containerColor = SurfaceWhite,
                            tonalElevation = 8.dp
                        ) {
                            HubTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        TabIconWithBadge(
                                            tab = tab,
                                            selected = selected,
                                            claimableQuests = claimableQuestsCount,
                                            liveCount = liveMatchesCount
                                        )
                                    },
                                    label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = SidiCobalt,
                                        selectedTextColor = SidiCobalt,
                                        indicatorColor = SidiSkyLight
                                    ),
                                    modifier = Modifier.testTag(tab.tag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (val pState = profileState) {
                        is UiState.Loading -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = SidiCobalt)
                                Spacer(Modifier.height(10.dp))
                                Text("Loading Tunisia Eco-Arena...", style = MaterialTheme.typography.titleMedium, color = DeepNavyInk)
                            }
                        }
                        is UiState.Error -> {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.CloudOff, contentDescription = null, tint = CarthageCoral, modifier = Modifier.size(48.dp))
                                Text("Connection Error", style = MaterialTheme.typography.headlineMedium, color = DeepNavyInk)
                                Text(pState.message, style = MaterialTheme.typography.bodyMedium, color = MutedSlateText)
                            }
                        }
                        is UiState.Success -> {
                            val profile: UserProfile = pState.data
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 600.dp)
                                    .align(Alignment.TopCenter)
                            ) {
                                when (currentTab) {
                                    HubTab.BATTLE -> HomeScreen(
                                        profile = profile,
                                        liveMatchesCount = liveMatchesCount,
                                        claimableQuestsCount = claimableQuestsCount,
                                        onBattleClicked = {
                                            matchmakingVm.startMatchmaking(profile)
                                            overlay = FullScreenOverlay.BattleFlow
                                        },
                                        onOpenDeck = { currentTab = HubTab.DECK },
                                        onOpenArenas = { currentTab = HubTab.ARENAS },
                                        onOpenSpectator = { currentTab = HubTab.WATCH },
                                        onOpenQuests = { currentTab = HubTab.QUESTS },
                                        onSignOut = {
                                            AuthManager.signOut(credentialManager, scope)
                                        }
                                    )

                                    HubTab.DECK -> BossCollectionScreen(
                                        profile = profile,
                                        onEquipBoss = { slot, bossId -> mainVm.equipBossInSlot(profile, slot, bossId) },
                                        onUpgradeBoss = { bossId -> mainVm.upgradeBoss(profile, bossId) }
                                    )

                                    HubTab.ARENAS -> ArenasTrophyRoadScreen(profile = profile)

                                    HubTab.WATCH -> SpectatorHubScreen(
                                        profile = profile,
                                        activeMatchesState = activeMatchesState,
                                        replaysState = replaysState,
                                        publicProfilesState = publicProfilesState,
                                        onWatchLiveMatch = { matchId ->
                                            spectatorVm.openLiveMatch(matchId)
                                            overlay = FullScreenOverlay.SpectatorLive(matchId)
                                        },
                                        onWatchReplay = { replayId ->
                                            spectatorVm.openReplay(replayId)
                                            overlay = FullScreenOverlay.SpectatorReplay(replayId)
                                        },
                                        onAddFriend = { code -> mainVm.addFriend(profile, code) },
                                        onRemoveFriend = { fUid, fName -> mainVm.removeFriend(profile, fUid, fName) }
                                    )

                                    HubTab.QUESTS -> QuestsScreen(
                                        profile = profile,
                                        claimedOutcome = claimedOutcome,
                                        onClaimQuest = { quest -> mainVm.claimQuest(profile, quest) },
                                        onDismissClaimOutcome = mainVm::clearClaimedOutcome
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
private fun TabIconWithBadge(
    tab: HubTab,
    selected: Boolean,
    claimableQuests: Int,
    liveCount: Int
) {
    val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
    when {
        tab == HubTab.QUESTS && claimableQuests > 0 -> {
            BadgedBox(badge = { Badge(containerColor = EcoEmerald) { Text("$claimableQuests", color = Color.White) } }) {
                Icon(icon, contentDescription = tab.label)
            }
        }
        tab == HubTab.WATCH && liveCount > 0 -> {
            BadgedBox(badge = { Badge(containerColor = CarthageCoral) { Text("$liveCount", color = Color.White) } }) {
                Icon(icon, contentDescription = tab.label)
            }
        }
        else -> {
            Icon(icon, contentDescription = tab.label)
        }
    }
}
