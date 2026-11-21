package com.example.engine

import com.example.data.MatchRewardOutcome
import com.example.data.WasteCrushRepository
import com.example.model.*
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class SessionSyncStatus {
    IDLE,
    SYNCING,
    LIVE_SYNCED,
    LOCAL_FALLBACK
}

data class EnergyBeamEffect(
    val id: Long,
    val wasteType: WasteType,
    val startColFraction: Float,
    val startRowFraction: Float,
    val damageAmount: Int,
    val isCritical: Boolean,
    val createdAtMs: Long
)

data class PlayerBattleSnapshot(
    val uid: String = "",
    val name: String = "Challenger",
    val trophies: Int = 0,
    val deckBossIds: List<String> = listOf("boss_b1", "boss_b2", "boss_b3"),
    val deckBossLevels: List<Int> = listOf(1, 1, 1),
    val board: List<List<BoardTile>> = Match3Engine.createInitialBoard(),
    val boardFlat: List<Int> = Match3Engine.encodeBoardFlat(Match3Engine.createInitialBoard()),
    val waveIndex: Int = 1, // 1 = Wave 1, 2 = Wave 2, 3 = Shared Boss
    val currentEnemyHp: Int = 420,
    val currentEnemyMaxHp: Int = 420,
    val bossHp: Int = 1500,
    val bossMaxHp: Int = 1500,
    val totalDamage: Int = 0,
    /** Environmental pollution level: 1.0f = heavily polluted, 0.0f = completely purified. */
    val pollutionLevel: Float = 1.0f,
    /** Environmental restoration percentage: 0% polluted -> 100% completely clean. */
    val restorationPct: Int = 0,
    val skillCharges: List<Int> = listOf(0, 0, 0),
    val lastAbilityText: String = "",
    val lastUpdatedMs: Long = 0L
) {
    fun toPlayerLiveState(): PlayerLiveState = PlayerLiveState(
        uid = uid.ifBlank { "player_uid" },
        name = name,
        trophies = trophies,
        deckBossIds = deckBossIds,
        deckBossLevels = deckBossLevels,
        waveIndex = waveIndex,
        bossHp = bossHp,
        bossMaxHp = bossMaxHp,
        totalDamage = totalDamage,
        restorationPct = restorationPct,
        boardFlat = boardFlat,
        skillCharges = skillCharges,
        lastAbility = lastAbilityText
    )
}

data class GameSessionConfig(
    val matchId: String,
    val profile: UserProfile,
    val arena: ArenaDefinition,
    val place: TunisianPlace,
    val sharedBoss: BossDefinition,
    val amIPlayer1: Boolean,
    val isBotMatch: Boolean,
    val initialLocalBoard: List<List<BoardTile>>,
    val initialOpponentBoard: List<List<BoardTile>>,
    val opponentUid: String,
    val opponentName: String,
    val opponentTrophies: Int,
    val opponentDeckIds: List<String>,
    val opponentDeckLevels: List<Int>,
    val durationSec: Int = 90
)

data class GameSessionState(
    val isActive: Boolean = false,
    val isFinished: Boolean = false,
    val matchId: String = "",
    val replayId: String? = null,
    val arenaId: Int = 0,
    val placeId: String = "place_hammamet",
    val sharedBossId: String = "boss_b1",
    val amIPlayer1: Boolean = true,
    val isBotMatch: Boolean = false,
    val secondsRemaining: Int = 90,
    val totalDurationSec: Int = 90,
    val spectatorCount: Int = 0,
    val syncStatus: SessionSyncStatus = SessionSyncStatus.IDLE,
    val lastCloudSyncMs: Long = 0L,
    val localPlayer: PlayerBattleSnapshot = PlayerBattleSnapshot(),
    val opponentPlayer: PlayerBattleSnapshot = PlayerBattleSnapshot(
        name = "Rival",
        board = Match3Engine.createInitialBoard(Random(99))
    ),
    val selectedTile: GridPos? = null,
    val hitFlashToken: Long = 0L,
    val lastDamageDealt: Int = 0,
    val comboBannerText: String = "",
    val activeBeams: List<EnergyBeamEffect> = emptyList(),
    val showRivalPeek: Boolean = false,
    // Quest & post-match telemetry
    val blueTilesMatched: Int = 0,
    val totalTilesMatched: Int = 0,
    val maxCombo: Int = 1,
    val abilitiesUsedCount: Int = 0,
    val wonMatch: Boolean = false,
    val rewardOutcome: MatchRewardOutcome? = null,
    val soundEnabled: Boolean = true
)

/**
 * Authoritative Firebase-backed 1v1 Battle Session Manager.
 * Handles Match-3 grid resolution, Eco-Deck charged abilities, wave-to-Boss progression,
 * real-time environmental pollution clearing, rival synchronization via Firestore `liveMatches/{matchId}`,
 * Replay frame capture, and post-match Trophy/Coin/Boss/Quest persistence.
 */
class GameStateManager(
    private val repository: WasteCrushRepository,
    private val scope: CoroutineScope,
    val soundPlayer: ArcadeSoundPlayer = ArcadeSoundPlayer()
) {
    private val _state = MutableStateFlow(GameSessionState())
    val state: StateFlow<GameSessionState> = _state.asStateFlow()

    private var activeProfile: UserProfile? = null
    private var timerLoopJob: Job? = null
    private var liveObserverJob: Job? = null
    private var throttledSyncJob: Job? = null

    private var beamCounter = 1L
    private var lastOutboundPushMs = 0L
    private var lastReplayRecordSec = -1
    private var pendingReplayNote = ""

    private val tilesMatchedByType = mutableMapOf<WasteType, Int>()
    private val recordedFrames = mutableListOf<ReplayFrame>()

    private val wave1HpBase = 420
    private val wave2HpBase = 580

    fun toggleSound() {
        val next = !_state.value.soundEnabled
        soundPlayer.soundEnabled = next
        _state.value = _state.value.copy(soundEnabled = next)
    }

    fun toggleRivalPeek() {
        _state.value = _state.value.copy(showRivalPeek = !_state.value.showRivalPeek)
    }

    fun startSession(
        config: GameSessionConfig,
        onSessionFinished: (won: Boolean) -> Unit
    ) {
        stopSession()
        activeProfile = config.profile
        tilesMatchedByType.clear()
        recordedFrames.clear()
        lastOutboundPushMs = 0L
        lastReplayRecordSec = -1
        pendingReplayNote = ""

        val wave1Hp = wave1HpBase + config.arena.id * 90
        val myLevels = config.profile.equippedDeckIds.map { config.profile.bossLevels[it] ?: 1 }
        val localFlat = Match3Engine.encodeBoardFlat(config.initialLocalBoard)
        val oppFlat = Match3Engine.encodeBoardFlat(config.initialOpponentBoard)
        val now = System.currentTimeMillis()

        val localSnapshot = PlayerBattleSnapshot(
            uid = config.profile.userId,
            name = config.profile.displayName,
            trophies = config.profile.trophies,
            deckBossIds = config.profile.equippedDeckIds,
            deckBossLevels = myLevels,
            board = config.initialLocalBoard,
            boardFlat = localFlat,
            waveIndex = 1,
            currentEnemyHp = wave1Hp,
            currentEnemyMaxHp = wave1Hp,
            bossHp = config.sharedBoss.bossBattleHp,
            bossMaxHp = config.sharedBoss.bossBattleHp,
            totalDamage = 0,
            pollutionLevel = 1.0f,
            restorationPct = 0,
            skillCharges = listOf(0, 0, 0),
            lastAbilityText = "",
            lastUpdatedMs = now
        )

        val oppSnapshot = PlayerBattleSnapshot(
            uid = config.opponentUid,
            name = config.opponentName,
            trophies = config.opponentTrophies,
            deckBossIds = config.opponentDeckIds,
            deckBossLevels = config.opponentDeckLevels,
            board = config.initialOpponentBoard,
            boardFlat = oppFlat,
            waveIndex = 1,
            currentEnemyHp = wave1Hp,
            currentEnemyMaxHp = wave1Hp,
            bossHp = config.sharedBoss.bossBattleHp,
            bossMaxHp = config.sharedBoss.bossBattleHp,
            totalDamage = 0,
            pollutionLevel = 1.0f,
            restorationPct = 0,
            skillCharges = listOf(0, 0, 0),
            lastAbilityText = "",
            lastUpdatedMs = now
        )

        val isLocalFallback = config.matchId.startsWith("local_")
        _state.value = GameSessionState(
            isActive = true,
            isFinished = false,
            matchId = config.matchId,
            arenaId = config.arena.id,
            placeId = config.place.id,
            sharedBossId = config.sharedBoss.id,
            amIPlayer1 = config.amIPlayer1,
            isBotMatch = config.isBotMatch,
            secondsRemaining = config.durationSec,
            totalDurationSec = config.durationSec,
            spectatorCount = 0,
            syncStatus = if (isLocalFallback) SessionSyncStatus.LOCAL_FALLBACK else SessionSyncStatus.LIVE_SYNCED,
            lastCloudSyncMs = now,
            localPlayer = localSnapshot,
            opponentPlayer = oppSnapshot,
            soundEnabled = soundPlayer.soundEnabled
        )

        recordReplayFrame("Battle started at ${config.place.name}! Shared Boss: ${config.sharedBoss.name}")

        // 1. Start real-time Firestore listener for opponent grid, Boss HP, pollution level & spectators
        observeRemoteMatchSession(config.matchId, config.amIPlayer1, config.isBotMatch, onSessionFinished)

        // 2. Start authoritative 90-second battle timer, VFX cleanup, rival simulation & periodic sync
        var oppWave1Hp = wave1Hp
        var oppWave2Hp = wave2HpBase + config.arena.id * 110

        timerLoopJob = scope.launch {
            while (_state.value.isActive && !_state.value.isFinished) {
                delay(1000L)
                val s = _state.value
                if (!s.isActive || s.isFinished) break

                val nextSec = (s.secondsRemaining - 1).coerceAtLeast(0)
                val nowMs = System.currentTimeMillis()
                val prunedBeams = s.activeBeams.filter { nowMs - it.createdAtMs < 700L }

                _state.value = s.copy(
                    secondsRemaining = nextSec,
                    activeBeams = prunedBeams,
                    comboBannerText = if (nowMs - s.hitFlashToken > 1400L) "" else s.comboBannerText
                )

                // Step rival board & combat in trophy-scaled fallback matches
                if (s.isBotMatch && nextSec > 0) {
                    val nextWaves = stepTrophyScaledRival(oppWave1Hp, oppWave2Hp, s.arenaId)
                    oppWave1Hp = nextWaves.first
                    oppWave2Hp = nextWaves.second
                }

                // Capture replay frame every 2 seconds & guarantee cloud heartbeat sync
                val elapsed = s.totalDurationSec - nextSec
                if (elapsed % 2 == 0 && elapsed != lastReplayRecordSec) {
                    lastReplayRecordSec = elapsed
                    val note = pendingReplayNote
                    pendingReplayNote = ""
                    recordReplayFrame(note)
                    requestRealtimeSync(immediate = false)
                }

                // Evaluate real-time win/loss conditions
                val latest = _state.value
                if (latest.localPlayer.bossHp <= 0) {
                    concludeSession(won = true, onSessionFinished = onSessionFinished)
                    break
                } else if (latest.opponentPlayer.bossHp <= 0) {
                    concludeSession(won = false, onSessionFinished = onSessionFinished)
                    break
                } else if (nextSec <= 0) {
                    val wonOnDamage = latest.localPlayer.totalDamage >= latest.opponentPlayer.totalDamage
                    concludeSession(won = wonOnDamage, onSessionFinished = onSessionFinished)
                    break
                }
            }
        }
    }

    private fun observeRemoteMatchSession(
        matchId: String,
        amIPlayer1: Boolean,
        isBotMatch: Boolean,
        onSessionFinished: (Boolean) -> Unit
    ) {
        if (matchId.startsWith("local_")) return
        liveObserverJob?.cancel()
        liveObserverJob = scope.launch {
            repository.observeLiveMatch(matchId)
                .catch { err ->
                    val fsErr = generateSequence(err) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && err is CancellationException) throw err
                }
                .collect { live ->
                    if (live == null) return@collect
                    val current = _state.value
                    if (!current.isActive || current.isFinished) return@collect

                    val remoteOpp = if (amIPlayer1) live.player2 else live.player1
                    if (!isBotMatch) {
                        val decodedBoard = if (remoteOpp.boardFlat.size == 49) {
                            Match3Engine.decodeBoardFlat(remoteOpp.boardFlat)
                        } else current.opponentPlayer.board

                        val oppPollution = (1f - remoteOpp.restorationPct / 100f).coerceIn(0f, 1f)
                        val updatedOpp = current.opponentPlayer.copy(
                            uid = remoteOpp.uid.ifBlank { current.opponentPlayer.uid },
                            name = remoteOpp.name.ifBlank { current.opponentPlayer.name },
                            trophies = remoteOpp.trophies,
                            deckBossIds = remoteOpp.deckBossIds.ifEmpty { current.opponentPlayer.deckBossIds },
                            deckBossLevels = remoteOpp.deckBossLevels.ifEmpty { current.opponentPlayer.deckBossLevels },
                            board = decodedBoard,
                            boardFlat = if (remoteOpp.boardFlat.size == 49) remoteOpp.boardFlat else current.opponentPlayer.boardFlat,
                            waveIndex = remoteOpp.waveIndex,
                            bossHp = remoteOpp.bossHp,
                            bossMaxHp = remoteOpp.bossMaxHp.coerceAtLeast(1),
                            totalDamage = remoteOpp.totalDamage,
                            restorationPct = remoteOpp.restorationPct.coerceIn(0, 100),
                            pollutionLevel = oppPollution,
                            skillCharges = remoteOpp.skillCharges.ifEmpty { listOf(0, 0, 0) },
                            lastAbilityText = remoteOpp.lastAbility,
                            lastUpdatedMs = System.currentTimeMillis()
                        )

                        _state.value = current.copy(
                            spectatorCount = live.spectatorCount,
                            syncStatus = SessionSyncStatus.LIVE_SYNCED,
                            lastCloudSyncMs = System.currentTimeMillis(),
                            opponentPlayer = updatedOpp
                        )

                        // Instant remote victory detection
                        if (live.status == "FINISHED" && live.winnerUid.isNotBlank()) {
                            val myUid = activeProfile?.userId.orEmpty()
                            concludeSession(won = live.winnerUid == myUid, onSessionFinished = onSessionFinished)
                        } else if (updatedOpp.bossHp <= 0) {
                            concludeSession(won = false, onSessionFinished = onSessionFinished)
                        }
                    } else {
                        _state.value = current.copy(
                            spectatorCount = live.spectatorCount,
                            syncStatus = SessionSyncStatus.LIVE_SYNCED
                        )
                    }
                }
        }
    }

    fun onTileTapped(pos: GridPos, onSessionFinished: (Boolean) -> Unit = {}) {
        val s = _state.value
        if (!s.isActive || s.isFinished) return
        val currentSel = s.selectedTile
        when {
            currentSel == null -> _state.value = s.copy(selectedTile = pos)
            currentSel == pos -> _state.value = s.copy(selectedTile = null)
            Match3Engine.areAdjacent(currentSel, pos) -> performLocalSwap(currentSel, pos, onSessionFinished)
            else -> _state.value = s.copy(selectedTile = pos)
        }
    }

    fun onTileSwiped(from: GridPos, to: GridPos, onSessionFinished: (Boolean) -> Unit = {}) {
        val s = _state.value
        if (!s.isActive || s.isFinished) return
        if (Match3Engine.areAdjacent(from, to)) {
            performLocalSwap(from, to, onSessionFinished)
        }
    }

    private fun performLocalSwap(a: GridPos, b: GridPos, onSessionFinished: (Boolean) -> Unit) {
        val s = _state.value
        val res = Match3Engine.attemptSwap(s.localPlayer.board, a, b)
        if (res == null) {
            _state.value = s.copy(selectedTile = null)
            return
        }
        applyLocalResolution(
            res = res,
            abilityBonusDamage = 0,
            usedAbilityLabel = null,
            consumedSlotIndex = null,
            onSessionFinished = onSessionFinished
        )
    }

    fun activateEcoDeckSlot(slotIndex: Int, onSessionFinished: (Boolean) -> Unit = {}) {
        val s = _state.value
        if (!s.isActive || s.isFinished) return
        val charge = s.localPlayer.skillCharges.getOrElse(slotIndex) { 0 }
        if (charge < 100) return

        val bossId = s.localPlayer.deckBossIds.getOrElse(slotIndex) { return }
        val bossLvl = s.localPlayer.deckBossLevels.getOrElse(slotIndex) { 1 }
        val bossDef = GameCatalog.bossById(bossId)
        val res = Match3Engine.applyBossAbility(s.localPlayer.board, bossDef)

        soundPlayer.playAbilityBlast()
        pendingReplayNote = "${s.localPlayer.name} unleashed ${bossDef.name}'s ${bossDef.abilityName}!"

        applyLocalResolution(
            res = res,
            abilityBonusDamage = bossDef.abilityDamageAtLevel(bossLvl),
            usedAbilityLabel = "${bossDef.name}: ${bossDef.abilityName}",
            consumedSlotIndex = slotIndex,
            onSessionFinished = onSessionFinished
        )
    }

    private fun applyLocalResolution(
        res: MatchResolution,
        abilityBonusDamage: Int,
        usedAbilityLabel: String?,
        consumedSlotIndex: Int?,
        onSessionFinished: (Boolean) -> Unit
    ) {
        val s = _state.value
        val local = s.localPlayer
        soundPlayer.playMatch(res.comboCount)

        for ((type, count) in res.countsByType) {
            tilesMatchedByType[type] = (tilesMatchedByType[type] ?: 0) + count
        }

        // Compute Eco-Deck preferred recyclable synergy multiplier + skill charges
        var deckBonusMult = 1f
        val newCharges = local.skillCharges.toMutableList()
        while (newCharges.size < 3) newCharges.add(0)

        for (i in 0 until 3) {
            val bId = local.deckBossIds.getOrNull(i) ?: continue
            val bDef = GameCatalog.bossById(bId)
            val bLvl = local.deckBossLevels.getOrElse(i) { 1 }
            val matchedFav = res.countsByType[bDef.preferredWaste] ?: 0
            if (matchedFav > 0) {
                deckBonusMult += bDef.tileBonusPercentAtLevel(bLvl) / 100f
            }
            if (consumedSlotIndex == i) {
                newCharges[i] = 0
            } else {
                val gain = ((res.totalCleared + matchedFav * 2) * 100f / bDef.chargeRequired.toFloat()).toInt()
                newCharges[i] = (newCharges[i] + gain.coerceAtLeast(8)).coerceAtMost(100)
            }
        }

        val comboMult = 1f + (res.comboCount - 1) * 0.35f
        val tileDamage = (res.totalCleared * 25f * deckBonusMult * comboMult).toInt()
        val totalHit = tileDamage + abilityBonusDamage

        // Apply damage across Wave 1 -> Wave 2 -> Shared Boss
        var waveIdx = local.waveIndex
        var curHp = local.currentEnemyHp
        var curMaxHp = local.currentEnemyMaxHp
        var bossHp = local.bossHp
        var remaining = totalHit
        var waveTransitioned = false

        if (waveIdx == 1 && remaining > 0) {
            val dealt = remaining.coerceAtMost(curHp)
            curHp -= dealt
            remaining -= dealt
            if (curHp <= 0) {
                waveIdx = 2
                curMaxHp = wave2HpBase + s.arenaId * 110
                curHp = curMaxHp
                waveTransitioned = true
                pendingReplayNote = "${local.name} cleared Wave 1!"
            }
        }
        if (waveIdx == 2 && remaining > 0) {
            val dealt = remaining.coerceAtMost(curHp)
            curHp -= dealt
            remaining -= dealt
            if (curHp <= 0) {
                waveIdx = 3
                curMaxHp = local.bossMaxHp
                curHp = bossHp
                waveTransitioned = true
                pendingReplayNote = "${local.name} summoned the Shared Boss!"
            }
        }
        if (waveIdx == 3 && remaining > 0) {
            bossHp = (bossHp - remaining).coerceAtLeast(0)
            curHp = bossHp
        }

        val totalPool = (wave1HpBase + s.arenaId * 90) + (wave2HpBase + s.arenaId * 110) + local.bossMaxHp
        val newTotalDamage = local.totalDamage + totalHit
        val restorationPct = if (bossHp <= 0) 100 else ((newTotalDamage.toFloat() / totalPool.toFloat()) * 100f).toInt().coerceIn(0, 99)
        val pollutionLevel = (1f - restorationPct / 100f).coerceIn(0f, 1f)

        val nowMs = System.currentTimeMillis()
        val dominantType = res.countsByType.maxByOrNull { it.value }?.key ?: WasteType.PLASTIC_BLUE
        val newBeams = buildBeamBurst(res, dominantType, totalHit, usedAbilityLabel != null, nowMs)

        val comboText = when {
            usedAbilityLabel != null -> "ECO-SKILL! -$totalHit"
            res.comboCount >= 4 -> "${res.comboCount}x MEGA CASCADE! -$totalHit"
            res.comboCount >= 2 -> "${res.comboCount}x COMBO! -$totalHit"
            res.spawnedSpecialCount > 0 -> "POWER TILE! -$totalHit"
            else -> "-$totalHit HP"
        }

        val nextLocal = local.copy(
            board = res.newBoard,
            boardFlat = Match3Engine.encodeBoardFlat(res.newBoard),
            waveIndex = waveIdx,
            currentEnemyHp = curHp,
            currentEnemyMaxHp = curMaxHp,
            bossHp = bossHp,
            totalDamage = newTotalDamage,
            restorationPct = restorationPct,
            pollutionLevel = pollutionLevel,
            skillCharges = newCharges,
            lastAbilityText = usedAbilityLabel ?: local.lastAbilityText,
            lastUpdatedMs = nowMs
        )

        _state.value = s.copy(
            localPlayer = nextLocal,
            selectedTile = null,
            hitFlashToken = nowMs,
            lastDamageDealt = totalHit,
            comboBannerText = comboText,
            activeBeams = (s.activeBeams + newBeams).takeLast(9),
            blueTilesMatched = tilesMatchedByType[WasteType.PLASTIC_BLUE] ?: 0,
            totalTilesMatched = s.totalTilesMatched + res.totalCleared,
            maxCombo = maxOf(s.maxCombo, res.comboCount),
            abilitiesUsedCount = s.abilitiesUsedCount + if (consumedSlotIndex != null) 1 else 0
        )

        if (bossHp <= 0) {
            scope.launch { concludeSession(won = true, onSessionFinished = onSessionFinished) }
        } else {
            // Immediate real-time cloud push on wave clears / Eco-Deck abilities, or 450ms throttled push on moves
            requestRealtimeSync(immediate = waveTransitioned || consumedSlotIndex != null)
        }
    }

    private fun buildBeamBurst(
        res: MatchResolution,
        dominantType: WasteType,
        totalHit: Int,
        isAbility: Boolean,
        nowMs: Long
    ): List<EnergyBeamEffect> {
        val sampleCols = res.matchedPositions
            .map { it.col }
            .distinct()
            .take(3)
            .ifEmpty { listOf(3) }
        val avgRow = if (res.matchedPositions.isNotEmpty()) {
            (res.matchedPositions.map { it.row }.average().toFloat() / (BOARD_SIZE - 1).toFloat()).coerceIn(0.15f, 0.9f)
        } else 0.5f

        return sampleCols.mapIndexed { idx, col ->
            EnergyBeamEffect(
                id = beamCounter++,
                wasteType = dominantType,
                startColFraction = ((col + 0.5f) / BOARD_SIZE.toFloat()).coerceIn(0.08f, 0.92f),
                startRowFraction = avgRow,
                damageAmount = if (idx == 0) totalHit else 0,
                isCritical = isAbility || res.comboCount >= 2,
                createdAtMs = nowMs
            )
        }
    }

    private fun stepTrophyScaledRival(
        oppWave1Remaining: Int,
        oppWave2Remaining: Int,
        arenaId: Int
    ): Pair<Int, Int> {
        val s = _state.value
        val opp = s.opponentPlayer
        var w1 = oppWave1Remaining
        var w2 = oppWave2Remaining
        var oppBoard = opp.board
        val move = Match3Engine.findAnyValidMove(oppBoard)
        var dmg = 0
        val charges = opp.skillCharges.toMutableList()
        while (charges.size < 3) charges.add(0)
        var lastAbility = opp.lastAbilityText

        if (move != null && Random.nextFloat() < 0.78f) {
            val res = Match3Engine.attemptSwap(oppBoard, move.first, move.second)
            if (res != null) {
                oppBoard = res.newBoard
                val avgLevel = opp.deckBossLevels.average().toFloat().coerceAtLeast(1f)
                dmg = (res.totalCleared * (19 + arenaId * 3) * (1f + (avgLevel - 1f) * 0.1f) * (1f + (res.comboCount - 1) * 0.2f)).toInt()
                for (i in 0 until 3) {
                    charges[i] = (charges[i] + res.totalCleared * 4).coerceAtMost(100)
                }
            }
        }

        val readySlot = charges.indexOfFirst { it >= 100 }
        if (readySlot >= 0) {
            val bossId = opp.deckBossIds.getOrElse(readySlot) { "boss_b1" }
            val bossLvl = opp.deckBossLevels.getOrElse(readySlot) { 1 }
            val bossDef = GameCatalog.bossById(bossId)
            val abRes = Match3Engine.applyBossAbility(oppBoard, bossDef)
            oppBoard = abRes.newBoard
            dmg += bossDef.abilityDamageAtLevel(bossLvl) / 2
            charges[readySlot] = 0
            lastAbility = "${bossDef.name}: ${bossDef.abilityName}"
            pendingReplayNote = "${opp.name} unleashed ${bossDef.abilityName}!"
        }

        var waveIdx = opp.waveIndex
        var bossHp = opp.bossHp
        var remainingDmg = dmg

        if (waveIdx == 1 && remainingDmg > 0) {
            val dealt = remainingDmg.coerceAtMost(w1)
            w1 -= dealt
            remainingDmg -= dealt
            if (w1 <= 0) {
                waveIdx = 2
                pendingReplayNote = "${opp.name} cleared Wave 1!"
            }
        }
        if (waveIdx == 2 && remainingDmg > 0) {
            val dealt = remainingDmg.coerceAtMost(w2)
            w2 -= dealt
            remainingDmg -= dealt
            if (w2 <= 0) {
                waveIdx = 3
                pendingReplayNote = "${opp.name} summoned the Shared Boss!"
            }
        }
        if (waveIdx == 3 && remainingDmg > 0) {
            bossHp = (bossHp - remainingDmg).coerceAtLeast(0)
        }

        val totalPool = (wave1HpBase + arenaId * 90) + (wave2HpBase + arenaId * 110) + opp.bossMaxHp
        val newTotalDmg = opp.totalDamage + dmg
        val restPct = if (bossHp <= 0) 100 else ((newTotalDmg.toFloat() / totalPool.toFloat()) * 100f).toInt().coerceIn(0, 99)
        val pollution = (1f - restPct / 100f).coerceIn(0f, 1f)

        _state.value = s.copy(
            opponentPlayer = opp.copy(
                board = oppBoard,
                boardFlat = Match3Engine.encodeBoardFlat(oppBoard),
                waveIndex = waveIdx,
                bossHp = bossHp,
                totalDamage = newTotalDmg,
                restorationPct = restPct,
                pollutionLevel = pollution,
                skillCharges = charges,
                lastAbilityText = lastAbility,
                lastUpdatedMs = System.currentTimeMillis()
            )
        )
        return w1 to w2
    }

    /**
     * Pushes local player's 7x7 tile grid, Boss HP, wave index, skill charges, and pollution restoration
     * to Firestore `liveMatches/{matchId}` with smart throttling (~450ms) so human opponents and
     * spectators stay synchronized in near real-time without flooding writes.
     */
    private fun requestRealtimeSync(immediate: Boolean) {
        val s = _state.value
        if (s.matchId.isBlank() || s.matchId.startsWith("local_")) return

        val now = System.currentTimeMillis()
        val elapsedSincePush = now - lastOutboundPushMs
        val minIntervalMs = 450L

        if (immediate || elapsedSincePush >= minIntervalMs) {
            throttledSyncJob?.cancel()
            lastOutboundPushMs = now
            scope.launch { pushSessionSnapshotToFirestore(finished = false, winnerUid = "") }
        } else if (throttledSyncJob?.isActive != true) {
            val waitMs = (minIntervalMs - elapsedSincePush).coerceAtLeast(80L)
            throttledSyncJob = scope.launch {
                delay(waitMs)
                lastOutboundPushMs = System.currentTimeMillis()
                pushSessionSnapshotToFirestore(finished = false, winnerUid = "")
            }
        }
    }

    private suspend fun pushSessionSnapshotToFirestore(finished: Boolean, winnerUid: String) {
        val s = _state.value
        if (s.matchId.isBlank() || s.matchId.startsWith("local_")) return

        _state.value = _state.value.copy(syncStatus = SessionSyncStatus.SYNCING)
        val myLive = s.localPlayer.toPlayerLiveState()
        val oppLive = if (s.isBotMatch) s.opponentPlayer.toPlayerLiveState() else null

        val result = repository.updateLiveMatchPlayers(
            matchId = s.matchId,
            status = if (finished) "FINISHED" else "ACTIVE",
            winnerUid = winnerUid,
            player1 = if (s.amIPlayer1) myLive else oppLive,
            player2 = if (s.amIPlayer1) oppLive else myLive
        )
        _state.value = _state.value.copy(
            syncStatus = if (result.isSuccess) SessionSyncStatus.LIVE_SYNCED else SessionSyncStatus.LOCAL_FALLBACK,
            lastCloudSyncMs = System.currentTimeMillis()
        )
    }

    private fun recordReplayFrame(eventText: String) {
        val s = _state.value
        val elapsed = (s.totalDurationSec - s.secondsRemaining).coerceAtLeast(0)
        val p1 = if (s.amIPlayer1) s.localPlayer else s.opponentPlayer
        val p2 = if (s.amIPlayer1) s.opponentPlayer else s.localPlayer

        val frame = ReplayFrame(
            elapsedSec = elapsed,
            p1Board = p1.boardFlat,
            p2Board = p2.boardFlat,
            p1BossHp = p1.bossHp,
            p2BossHp = p2.bossHp,
            p1Wave = p1.waveIndex,
            p2Wave = p2.waveIndex,
            p1Damage = p1.totalDamage,
            p2Damage = p2.totalDamage,
            p1Restoration = p1.restorationPct,
            p2Restoration = p2.restorationPct,
            p1Charges = p1.skillCharges,
            p2Charges = p2.skillCharges,
            eventText = eventText
        )
        if (recordedFrames.size < 44) {
            recordedFrames.add(frame)
        }
    }

    private suspend fun concludeSession(won: Boolean, onSessionFinished: (Boolean) -> Unit) {
        val s = _state.value
        if (s.isFinished) return

        val profile = activeProfile
        val winnerUid = if (won) (profile?.userId ?: "player") else s.opponentPlayer.uid.ifBlank { "rival" }
        val winnerName = if (won) s.localPlayer.name else s.opponentPlayer.name
        if (won) soundPlayer.playVictory()

        recordReplayFrame("$winnerName purified ${GameCatalog.placeById(s.placeId).name} and won!")
        _state.value = s.copy(isActive = false, isFinished = true, wonMatch = won)
        timerLoopJob?.cancel()
        liveObserverJob?.cancel()
        throttledSyncJob?.cancel()
        onSessionFinished(won)

        pushSessionSnapshotToFirestore(finished = true, winnerUid = winnerUid)

        // Persist Battle Replay for Spectator & Friends playback
        var savedReplayId: String? = null
        if (profile != null && (s.amIPlayer1 || s.isBotMatch)) {
            val p1 = if (s.amIPlayer1) s.localPlayer else s.opponentPlayer
            val p2 = if (s.amIPlayer1) s.opponentPlayer else s.localPlayer

            val replay = MatchReplay(
                creatorUid = profile.userId,
                participantUids = listOf(profile.userId, s.opponentPlayer.uid.ifBlank { "rival_uid" }),
                replayPublic = true,
                arenaId = s.arenaId,
                placeId = s.placeId,
                bossId = s.sharedBossId,
                trophyBracket = s.arenaId,
                winnerUid = winnerUid,
                durationSec = (s.totalDurationSec - s.secondsRemaining).coerceIn(1, 300),
                player1Summary = ReplaySummary(
                    uid = p1.uid,
                    name = p1.name,
                    trophies = p1.trophies,
                    deckBossIds = p1.deckBossIds,
                    deckBossLevels = p1.deckBossLevels,
                    totalDamage = p1.totalDamage,
                    restorationPct = p1.restorationPct
                ),
                player2Summary = ReplaySummary(
                    uid = p2.uid,
                    name = p2.name,
                    trophies = p2.trophies,
                    deckBossIds = p2.deckBossIds,
                    deckBossLevels = p2.deckBossLevels,
                    totalDamage = p2.totalDamage,
                    restorationPct = p2.restorationPct
                ),
                frames = recordedFrames.toList().ifEmpty {
                    listOf(
                        ReplayFrame(
                            elapsedSec = 1,
                            p1Board = p1.boardFlat,
                            p2Board = p2.boardFlat
                        )
                    )
                }
            )
            savedReplayId = repository.saveReplay(replay).getOrNull()
        }

        // Persist Trophies, Eco-Coins, Boss Capture card copy & Daily Quests telemetry
        if (profile != null) {
            val wavesCleared = (s.localPlayer.waveIndex - 1).coerceAtLeast(0) + if (s.localPlayer.bossHp <= 0) 1 else 0
            val telemetry = MatchTelemetry(
                won = won,
                arenaId = s.arenaId,
                placeId = s.placeId,
                bossId = s.sharedBossId,
                totalDamage = s.localPlayer.totalDamage,
                restorationPct = s.localPlayer.restorationPct,
                wavesCleared = wavesCleared,
                abilitiesUsed = s.abilitiesUsedCount,
                maxCombo = s.maxCombo,
                totalTilesMatched = s.totalTilesMatched,
                tilesMatchedByType = tilesMatchedByType.toMap(),
                deckBossIds = s.localPlayer.deckBossIds
            )
            val reward = repository.applyMatchResult(profile, telemetry).getOrNull()
            _state.value = _state.value.copy(
                rewardOutcome = reward,
                replayId = savedReplayId
            )
        }
    }

    fun stopSession() {
        timerLoopJob?.cancel()
        liveObserverJob?.cancel()
        throttledSyncJob?.cancel()
        _state.value = GameSessionState(soundEnabled = soundPlayer.soundEnabled)
    }

    fun release() {
        stopSession()
        soundPlayer.release()
    }
}
