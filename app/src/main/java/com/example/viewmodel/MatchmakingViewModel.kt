package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.WasteCrushRepository
import com.example.engine.*
import com.example.model.*
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class MatchmakingPhase {
    IDLE,
    SEARCHING,
    ROULETTE,
    IN_BATTLE,
    FINISHED
}

data class MatchmakingUiState(
    val phase: MatchmakingPhase = MatchmakingPhase.IDLE,
    val arenaId: Int = 0,
    val searchElapsedSec: Int = 0,
    val trophyRangeLabel: String = "±150 Trophies",
    val rouletteHighlightIndex: Int = 0,
    val lockedPlaceId: String = "place_hammamet",
    val sharedBossId: String = "boss_b1",
    val isBotMatch: Boolean = false,
    val myName: String = "You",
    val myTrophies: Int = 0,
    val myDeckIds: List<String> = listOf("boss_b1", "boss_b2", "boss_b3"),
    val opponentName: String = "Searching...",
    val opponentTrophies: Int = 0,
    val opponentDeckIds: List<String> = listOf("boss_b1", "boss_b2", "boss_b3"),
    val errorMessage: String? = null
)

/**
 * Coordinates online Trophy-based 1v1 matchmaking via Firebase Firestore (`matchmakingQueue`),
 * spins the 5-place Tunisian Arena Roulette, creates the shared `liveMatches/{matchId}` session,
 * and hands live combat synchronization off to [GameStateManager].
 */
class MatchmakingViewModel(
    private val repository: WasteCrushRepository
) : ViewModel() {

    val gameStateManager = GameStateManager(
        repository = repository,
        scope = viewModelScope
    )

    private val _matchmakingState = MutableStateFlow(MatchmakingUiState())
    val matchmakingState: StateFlow<MatchmakingUiState> = _matchmakingState.asStateFlow()

    val sessionState: StateFlow<GameSessionState> = gameStateManager.state

    private var matchmakingJob: Job? = null
    private var queueObserverJob: Job? = null

    private val tunisianRivalNames = listOf(
        "Amine_TN", "Yasmine_Eco", "Sami_Carthage", "Nour_Djerba",
        "Mehdi_Sfax", "Ines_Hammamet", "Karim_Tozeur", "Salma_Bizerte"
    )

    fun startMatchmaking(profile: UserProfile) {
        cancelAndReset()
        val arena = GameCatalog.arenaForTrophies(profile.trophies)

        _matchmakingState.value = MatchmakingUiState(
            phase = MatchmakingPhase.SEARCHING,
            arenaId = arena.id,
            searchElapsedSec = 0,
            trophyRangeLabel = "±150 Trophies",
            myName = profile.displayName,
            myTrophies = profile.trophies,
            myDeckIds = profile.equippedDeckIds,
            opponentName = "Searching ${arena.name}..."
        )

        matchmakingJob = viewModelScope.launch {
            // Offline guest mode (no sign-in): short "search" then a trophy-scaled AI rival.
            if (repository.isGuestMode()) {
                for (sec in 1..2) {
                    delay(1000L)
                    _matchmakingState.value = _matchmakingState.value.copy(searchElapsedSec = sec, trophyRangeLabel = "Offline • AI rival")
                }
                launchTrophyScaledRivalMatch(profile, arena)
                return@launch
            }

            val enqueueRes = repository.enqueueForMatchmaking(profile, arena.id)
            if (enqueueRes.isFailure) {
                // Could not reach the queue: don't make the player wait for nobody.
                delay(1000L)
                launchTrophyScaledRivalMatch(profile, arena)
                return@launch
            }
            val foundTicket = enqueueRes.getOrNull()

            if (foundTicket != null) {
                launchHumanMatchAsCreator(profile, foundTicket, arena)
                return@launch
            }

            var pairedExternally = false
            queueObserverJob = launch {
                repository.observeMyQueueTicket()
                    .catch { err ->
                        val fsErr = generateSequence(err) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsErr == null && err is CancellationException) throw err
                    }
                    .collect { ticket ->
                        if (ticket != null && ticket.status == "MATCHED" && ticket.matchId.isNotBlank() && !pairedExternally) {
                            pairedExternally = true
                            joinExistingHumanMatch(profile, ticket.matchId)
                        }
                    }
            }

            // Wait up to 5 seconds for a live online Trophy-matched challenger (then an AI rival)
            var waited = 0
            while (waited < 5 && !pairedExternally) {
                delay(1000L)
                waited++
                val rangeLabel = when {
                    waited <= 2 -> "±150 Trophies"
                    waited <= 4 -> "±250 Trophies"
                    else -> "±300 Trophies (Arena Pool)"
                }
                _matchmakingState.value = _matchmakingState.value.copy(
                    searchElapsedSec = waited,
                    trophyRangeLabel = rangeLabel
                )
            }

            if (!pairedExternally) {
                queueObserverJob?.cancel()
                repository.leaveMatchmakingQueue()
                launchTrophyScaledRivalMatch(profile, arena)
            }
        }
    }

    private suspend fun launchHumanMatchAsCreator(
        profile: UserProfile,
        opponent: MatchmakingTicket,
        arena: ArenaDefinition
    ) {
        val chosenPlace = arena.places.random()
        val chosenBoss = GameCatalog.cumulativeBossPoolForArena(arena.id).random()
        val myBoard = Match3Engine.createInitialBoard()
        val oppBoard = Match3Engine.createInitialBoard()
        val myLevels = profile.equippedDeckIds.map { profile.bossLevels[it] ?: 1 }

        val p1 = PlayerLiveState(
            uid = profile.userId,
            name = profile.displayName,
            trophies = profile.trophies,
            deckBossIds = profile.equippedDeckIds,
            deckBossLevels = myLevels,
            waveIndex = 1,
            bossHp = chosenBoss.bossBattleHp,
            bossMaxHp = chosenBoss.bossBattleHp,
            totalDamage = 0,
            restorationPct = 0,
            boardFlat = Match3Engine.encodeBoardFlat(myBoard),
            skillCharges = listOf(0, 0, 0),
            lastAbility = ""
        )
        val p2 = PlayerLiveState(
            uid = opponent.userId,
            name = opponent.displayName,
            trophies = opponent.trophies,
            deckBossIds = opponent.deckBossIds,
            deckBossLevels = opponent.deckBossLevels,
            waveIndex = 1,
            bossHp = chosenBoss.bossBattleHp,
            bossMaxHp = chosenBoss.bossBattleHp,
            totalDamage = 0,
            restorationPct = 0,
            boardFlat = Match3Engine.encodeBoardFlat(oppBoard),
            skillCharges = listOf(0, 0, 0),
            lastAbility = ""
        )

        val liveMatch = LiveMatch(
            creatorUid = profile.userId,
            participantUids = listOf(profile.userId, opponent.userId),
            arenaId = arena.id,
            placeId = chosenPlace.id,
            bossId = chosenBoss.id,
            trophyBracket = arena.id,
            status = "ACTIVE",
            durationSec = 90,
            spectatorCount = 0,
            botMatch = false,
            winnerUid = "",
            player1 = p1,
            player2 = p2
        )

        // Create the match and claim the ticket atomically. If the opponent already left or was
        // taken by someone else, play an AI rival instead of a frozen "ghost" opponent.
        val matchId = repository.createMatchAndClaimTicket(liveMatch, opponent.userId).getOrNull()
        if (matchId == null) {
            launchTrophyScaledRivalMatch(profile, arena)
            return
        }

        spinPlaceRouletteAndLaunchSession(
            GameSessionConfig(
                matchId = matchId,
                profile = profile,
                arena = arena,
                place = chosenPlace,
                sharedBoss = chosenBoss,
                amIPlayer1 = true,
                isBotMatch = false,
                initialLocalBoard = myBoard,
                initialOpponentBoard = oppBoard,
                opponentUid = opponent.userId,
                opponentName = opponent.displayName,
                opponentTrophies = opponent.trophies,
                opponentDeckIds = opponent.deckBossIds,
                opponentDeckLevels = opponent.deckBossLevels
            )
        )
    }

    private suspend fun joinExistingHumanMatch(profile: UserProfile, matchId: String) {
        repository.leaveMatchmakingQueue()
        val match = repository.observeLiveMatch(matchId)
            .catch { err ->
                val fsErr = generateSequence(err) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && err is CancellationException) throw err
            }
            .firstOrNull { it != null } ?: return

        val arena = GameCatalog.arenaById(match.arenaId)
        val place = GameCatalog.placeById(match.placeId)
        val boss = GameCatalog.bossById(match.bossId)
        val myBoard = Match3Engine.createInitialBoard()
        val oppBoard = if (match.player1.boardFlat.size == 49) {
            Match3Engine.decodeBoardFlat(match.player1.boardFlat)
        } else Match3Engine.createInitialBoard()

        spinPlaceRouletteAndLaunchSession(
            GameSessionConfig(
                matchId = matchId,
                profile = profile,
                arena = arena,
                place = place,
                sharedBoss = boss,
                amIPlayer1 = false,
                isBotMatch = false,
                initialLocalBoard = myBoard,
                initialOpponentBoard = oppBoard,
                opponentUid = match.player1.uid,
                opponentName = match.player1.name,
                opponentTrophies = match.player1.trophies,
                opponentDeckIds = match.player1.deckBossIds,
                opponentDeckLevels = match.player1.deckBossLevels
            )
        )
    }

    private suspend fun launchTrophyScaledRivalMatch(profile: UserProfile, arena: ArenaDefinition) {
        val chosenPlace = arena.places.random()
        val bossPool = GameCatalog.cumulativeBossPoolForArena(arena.id)
        val chosenBoss = bossPool.random()
        val myBoard = Match3Engine.createInitialBoard()
        val oppBoard = Match3Engine.createInitialBoard()
        val myLevels = profile.equippedDeckIds.map { profile.bossLevels[it] ?: 1 }

        val rivalUid = "rival_${arena.id}_${Random.nextInt(1000, 9999)}"
        val rivalName = tunisianRivalNames.random()
        val rivalTrophies = (profile.trophies + Random.nextInt(-45, 55)).coerceAtLeast(0)
        val rivalDeck = bossPool.shuffled().take(3).map { it.id }
        val rivalLevels = rivalDeck.map { (1 + arena.id + Random.nextInt(0, 2)).coerceAtMost(5) }

        val p1 = PlayerLiveState(
            uid = profile.userId,
            name = profile.displayName,
            trophies = profile.trophies,
            deckBossIds = profile.equippedDeckIds,
            deckBossLevels = myLevels,
            waveIndex = 1,
            bossHp = chosenBoss.bossBattleHp,
            bossMaxHp = chosenBoss.bossBattleHp,
            totalDamage = 0,
            restorationPct = 0,
            boardFlat = Match3Engine.encodeBoardFlat(myBoard),
            skillCharges = listOf(0, 0, 0),
            lastAbility = ""
        )
        val p2 = PlayerLiveState(
            uid = rivalUid,
            name = rivalName,
            trophies = rivalTrophies,
            deckBossIds = rivalDeck,
            deckBossLevels = rivalLevels,
            waveIndex = 1,
            bossHp = chosenBoss.bossBattleHp,
            bossMaxHp = chosenBoss.bossBattleHp,
            totalDamage = 0,
            restorationPct = 0,
            boardFlat = Match3Engine.encodeBoardFlat(oppBoard),
            skillCharges = listOf(0, 0, 0),
            lastAbility = ""
        )

        val liveMatch = LiveMatch(
            creatorUid = profile.userId,
            participantUids = listOf(profile.userId, rivalUid),
            arenaId = arena.id,
            placeId = chosenPlace.id,
            bossId = chosenBoss.id,
            trophyBracket = arena.id,
            status = "ACTIVE",
            durationSec = 90,
            spectatorCount = 0,
            botMatch = true,
            winnerUid = "",
            player1 = p1,
            player2 = p2
        )

        val matchId = repository.createLiveMatch(liveMatch).getOrNull() ?: "local_${System.currentTimeMillis()}"

        spinPlaceRouletteAndLaunchSession(
            GameSessionConfig(
                matchId = matchId,
                profile = profile,
                arena = arena,
                place = chosenPlace,
                sharedBoss = chosenBoss,
                amIPlayer1 = true,
                isBotMatch = true,
                initialLocalBoard = myBoard,
                initialOpponentBoard = oppBoard,
                opponentUid = rivalUid,
                opponentName = rivalName,
                opponentTrophies = rivalTrophies,
                opponentDeckIds = rivalDeck,
                opponentDeckLevels = rivalLevels
            )
        )
    }

    private suspend fun spinPlaceRouletteAndLaunchSession(config: GameSessionConfig) {
        val targetIndex = config.arena.places.indexOfFirst { it.id == config.place.id }.coerceAtLeast(0)

        _matchmakingState.value = _matchmakingState.value.copy(
            phase = MatchmakingPhase.ROULETTE,
            arenaId = config.arena.id,
            lockedPlaceId = config.place.id,
            sharedBossId = config.sharedBoss.id,
            isBotMatch = config.isBotMatch,
            opponentName = config.opponentName,
            opponentTrophies = config.opponentTrophies,
            opponentDeckIds = config.opponentDeckIds
        )

        val totalSpins = 15 + targetIndex
        for (step in 0..totalSpins) {
            _matchmakingState.value = _matchmakingState.value.copy(
                rouletteHighlightIndex = step % config.arena.places.size
            )
            gameStateManager.soundPlayer.playRouletteTick()
            val delayMs = when {
                step < 8 -> 80L
                step < 13 -> 135L
                else -> 210L
            }
            delay(delayMs)
        }
        delay(600L)

        _matchmakingState.value = _matchmakingState.value.copy(phase = MatchmakingPhase.IN_BATTLE)
        gameStateManager.startSession(config) {
            _matchmakingState.value = _matchmakingState.value.copy(phase = MatchmakingPhase.FINISHED)
        }
    }

    fun onTileTapped(pos: GridPos) = gameStateManager.onTileTapped(pos)

    fun onTileSwiped(from: GridPos, to: GridPos) = gameStateManager.onTileSwiped(from, to)

    fun activateEcoDeckBoss(slotIndex: Int) = gameStateManager.activateEcoDeckSlot(slotIndex)

    fun toggleSound() = gameStateManager.toggleSound()

    fun toggleRivalPeek() = gameStateManager.toggleRivalPeek()

    fun cancelAndReset() {
        matchmakingJob?.cancel()
        queueObserverJob?.cancel()
        gameStateManager.stopSession()
        viewModelScope.launch { repository.leaveMatchmakingQueue() }
        _matchmakingState.value = MatchmakingUiState()
    }

    override fun onCleared() {
        super.onCleared()
        gameStateManager.release()
    }

    companion object {
        fun factory(repository: WasteCrushRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MatchmakingViewModel(repository) as T
                }
            }
    }
}
