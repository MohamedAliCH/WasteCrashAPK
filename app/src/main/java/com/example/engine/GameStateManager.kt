package com.example.engine

import com.example.data.MatchRewardOutcome
import com.example.data.WasteCrushRepository
import com.example.model.*
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

/** One mini trash monster of the current wave. */
data class MobState(
    val id: String,
    val hp: Int,
    val maxHp: Int
) {
    val alive: Boolean get() = hp > 0
}

data class PlayerBattleSnapshot(
    val uid: String = "",
    val name: String = "Challenger",
    val trophies: Int = 0,
    val deckBossIds: List<String> = listOf("boss_b1", "boss_b2", "boss_b3"),
    val deckBossLevels: List<Int> = listOf(1, 1, 1),
    val board: List<List<BoardTile>> = Match3Engine.DEFAULT_BOARD,
    val boardFlat: List<Int> = Match3Engine.DEFAULT_BOARD_FLAT,
    val waveIndex: Int = 1, // 1 = Wave 1 (2 minis), 2 = Wave 2 (3 minis), 3 = Shared Boss
    /** Mini monsters of the current wave (empty during the Boss stage). */
    val waveMobs: List<MobState> = emptyList(),
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
    val durationSec: Int = 90,
    /** Mini mob ids for wave 1 and wave 2. Empty = derived from the match id (same for both players). */
    val waves: List<List<String>> = emptyList()
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
    val opponentPlayer: PlayerBattleSnapshot = PlayerBattleSnapshot(name = "Rival"),
    val selectedTile: GridPos? = null,
    val isBoardAnimating: Boolean = false,
    val invalidSwapPair: Pair<GridPos, GridPos>? = null,
    val invalidSwapToken: Long = 0L,
    val popBurstTiles: List<Pair<GridPos, WasteType>> = emptyList(),
    val popBurstToken: Long = 0L,
    val hintPair: Pair<GridPos, GridPos>? = null,
    val comboFlashLevel: Int = 0,
    val hitFlashToken: Long = 0L,
    val lastDamageDealt: Int = 0,
    val comboBannerText: String = "",
    val activeBeams: List<EnergyBeamEffect> = emptyList(),
    val showRivalPeek: Boolean = false,
    /** Mini mob waves of this match (wave 1, wave 2). */
    val waveMobIds: List<List<String>> = emptyList(),
    /** Bumped every time a mini monster (or the Boss) is defeated, to drive the "purified" VFX. */
    val mobDefeatedToken: Long = 0L,
    val lastDefeatedName: String = "",
    /** PvP: timer reached 00:00 and we are waiting for player 1's authoritative result. */
    val awaitingResult: Boolean = false,
    // Quest & post-match telemetry
    val blueTilesMatched: Int = 0,
    val totalTilesMatched: Int = 0,
    val maxCombo: Int = 1,
    val abilitiesUsedCount: Int = 0,
    val wonMatch: Boolean = false,
    val rewardOutcome: MatchRewardOutcome? = null,
    val soundEnabled: Boolean = true
)

/** Pure wave/damage rules, kept separate from coroutines so they can be unit-tested. */
object BattleRules {
    const val WAVE1_BASE = 420
    const val WAVE2_BASE = 580
    /** Minis take x2 from their own material, so their HP pool is raised to keep match length. */
    const val MINI_HP_SCALE = 1.3f
    const val MINI_WEAKNESS_MULT = 2f
    const val BOSS_WEAKNESS_MULT = 1.5f

    fun waveTotalHp(wave: Int, arenaId: Int): Int = when (wave) {
        1 -> ((WAVE1_BASE + arenaId * 90) * MINI_HP_SCALE).toInt()
        else -> ((WAVE2_BASE + arenaId * 110) * MINI_HP_SCALE).toInt()
    }

    fun buildWave(ids: List<String>, totalHp: Int): List<MobState> {
        if (ids.isEmpty()) return emptyList()
        val weights = ids.map { GameCatalog.miniMobById(it).hpWeight }
        val sum = weights.sum().coerceAtLeast(0.01f)
        return ids.mapIndexed { i, id ->
            val hp = (totalHp * weights[i] / sum).toInt().coerceAtLeast(1)
            MobState(id, hp, hp)
        }
    }

    fun totalPool(arenaId: Int, bossMaxHp: Int): Int =
        waveTotalHp(1, arenaId) + waveTotalHp(2, arenaId) + bossMaxHp

    /** The monster currently being hit: first alive mini, or the Boss. */
    fun weaknessFor(waveIndex: Int, mobs: List<MobState>, boss: BossDefinition): Pair<WasteType, Float> {
        if (waveIndex < 3) {
            val target = mobs.firstOrNull { it.alive } ?: return boss.preferredWaste to 1f
            return GameCatalog.miniMobById(target.id).weakness to MINI_WEAKNESS_MULT
        }
        return boss.preferredWaste to BOSS_WEAKNESS_MULT
    }

    /** Cleared tiles weighted by the current target's weakness. */
    fun weightedCleared(counts: Map<WasteType, Int>, weakness: WasteType, mult: Float): Float =
        counts.entries.sumOf { (t, n) -> (n * if (t == weakness) mult else 1f).toDouble() }.toFloat()

    data class DamageOutcome(
        val waveIndex: Int,
        val mobs: List<MobState>,
        val bossHp: Int,
        val defeatedNames: List<String>,
        val waveTransitioned: Boolean
    )

    /** Applies [damage] to the minis in order (overflow carries over), then to the Boss. */
    fun applyDamage(
        damage: Int,
        waveIndex: Int,
        mobs: List<MobState>,
        bossHp: Int,
        waves: List<List<String>>,
        arenaId: Int,
        bossName: String
    ): DamageOutcome {
        var wave = waveIndex
        var current = mobs.toMutableList()
        var boss = bossHp
        var remaining = damage
        val defeated = mutableListOf<String>()
        var transitioned = false

        while (remaining > 0 && wave < 3) {
            val idx = current.indexOfFirst { it.alive }
            if (idx < 0) {
                wave++
                transitioned = true
                current = if (wave == 2) buildWave(waves.getOrElse(1) { emptyList() }, waveTotalHp(2, arenaId)).toMutableList()
                else mutableListOf()
                continue
            }
            val mob = current[idx]
            val dealt = remaining.coerceAtMost(mob.hp)
            current[idx] = mob.copy(hp = mob.hp - dealt)
            remaining -= dealt
            if (current[idx].hp <= 0) defeated += GameCatalog.miniMobById(mob.id).name
        }
        // A wave whose last mob just fell advances even if no damage is left over.
        if (wave < 3 && current.none { it.alive }) {
            wave++
            transitioned = true
            current = if (wave == 2) buildWave(waves.getOrElse(1) { emptyList() }, waveTotalHp(2, arenaId)).toMutableList()
            else mutableListOf()
        }
        if (wave == 3 && remaining > 0) {
            val before = boss
            boss = (boss - remaining).coerceAtLeast(0)
            if (before > 0 && boss == 0) defeated += bossName
        }
        return DamageOutcome(wave, current, boss, defeated, transitioned)
    }

    /** AI rival pacing: it plays roughly one move every 4 seconds (a bit faster in higher arenas). */
    const val BOT_MOVE_CHANCE = 0.28f

    data class BotTick(val board: List<List<BoardTile>>, val charges: List<Int>, val damage: Int, val abilityLabel: String?)

    /** One second of the AI rival: maybe a move, maybe a charged Eco-Deck ability. */
    fun botTick(
        board: List<List<BoardTile>>,
        skillCharges: List<Int>,
        deckIds: List<String>,
        deckLevels: List<Int>,
        arenaId: Int,
        random: Random
    ): BotTick {
        var b = board
        var dmg = 0
        val charges = skillCharges.toMutableList()
        while (charges.size < 3) charges.add(0)
        var label: String? = null

        val move = Match3Engine.findAnyValidMove(b)
        if (move != null && random.nextFloat() < BOT_MOVE_CHANCE + arenaId * 0.01f) {
            val res = Match3Engine.attemptSwap(b, move.first, move.second, random)
            if (res != null) {
                b = res.newBoard
                val avgLevel = deckLevels.average().toFloat().takeIf { !it.isNaN() }?.coerceAtLeast(1f) ?: 1f
                dmg = (res.totalCleared * (14 + arenaId * 3) * (1f + (avgLevel - 1f) * 0.1f) * (1f + (res.comboCount - 1) * 0.2f)).toInt()
                for (i in 0 until 3) charges[i] = (charges[i] + res.totalCleared * 3).coerceAtMost(100)
            }
        }
        val readySlot = charges.indexOfFirst { it >= 100 }
        if (readySlot >= 0) {
            val bossDef = GameCatalog.bossById(deckIds.getOrElse(readySlot) { "boss_b1" })
            val lvl = deckLevels.getOrElse(readySlot) { 1 }
            b = Match3Engine.applyBossAbility(b, bossDef, random).newBoard
            dmg += bossDef.abilityDamageAtLevel(lvl) / 3
            charges[readySlot] = 0
            label = "${bossDef.name}: ${bossDef.abilityName}"
        }
        return BotTick(b, charges, dmg, label)
    }

    /**
     * Same answer on both clients for the same data: more damage wins; a tie goes to the
     * higher clean-up %, then to the lexicographically smaller uid. Returns the winner uid.
     */
    fun timeoutWinner(p1: PlayerBattleSnapshot, p2: PlayerBattleSnapshot): String = when {
        p1.totalDamage != p2.totalDamage -> if (p1.totalDamage > p2.totalDamage) p1.uid else p2.uid
        p1.restorationPct != p2.restorationPct -> if (p1.restorationPct > p2.restorationPct) p1.uid else p2.uid
        else -> minOf(p1.uid, p2.uid)
    }
}

/**
 * Firebase-backed 1v1 Battle Session Manager.
 * Handles Match-3 grid resolution, Eco-Deck charged abilities, mini-mob waves -> shared Boss,
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
    private var swapAnimJob: Job? = null
    private var resultGraceJob: Job? = null
    private var onFinished: (Boolean) -> Unit = {}

    private var beamCounter = 1L
    private var lastOutboundPushMs = 0L
    private var lastReplayRecordSec = -1
    private var lastPlayerMoveMs = 0L
    private var pendingReplayNote = ""

    private val tilesMatchedByType = mutableMapOf<WasteType, Int>()
    private val recordedFrames = mutableListOf<ReplayFrame>()

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
        onFinished = onSessionFinished
        tilesMatchedByType.clear()
        recordedFrames.clear()
        lastOutboundPushMs = 0L
        lastReplayRecordSec = -1
        lastPlayerMoveMs = System.currentTimeMillis()
        pendingReplayNote = ""

        val arenaId = config.arena.id
        val waves = config.waves.takeIf { it.size >= 2 } ?: GameCatalog.matchWaves(config.matchId, arenaId)
        val wave1Total = BattleRules.waveTotalHp(1, arenaId)
        val myLevels = config.profile.equippedDeckIds.map { config.profile.bossLevels[it] ?: 1 }
        val now = System.currentTimeMillis()

        fun snapshot(
            uid: String, name: String, trophies: Int, deckIds: List<String>, deckLevels: List<Int>, board: List<List<BoardTile>>
        ): PlayerBattleSnapshot {
            val mobs = BattleRules.buildWave(waves[0], wave1Total)
            return PlayerBattleSnapshot(
                uid = uid,
                name = name,
                trophies = trophies,
                deckBossIds = deckIds,
                deckBossLevels = deckLevels,
                board = board,
                boardFlat = Match3Engine.encodeBoardFlat(board),
                waveIndex = 1,
                waveMobs = mobs,
                currentEnemyHp = mobs.sumOf { it.hp },
                currentEnemyMaxHp = mobs.sumOf { it.maxHp },
                bossHp = config.sharedBoss.bossBattleHp,
                bossMaxHp = config.sharedBoss.bossBattleHp,
                lastUpdatedMs = now
            )
        }

        val localSnapshot = snapshot(
            config.profile.userId, config.profile.displayName, config.profile.trophies,
            config.profile.equippedDeckIds, myLevels, config.initialLocalBoard
        )
        val oppSnapshot = snapshot(
            config.opponentUid, config.opponentName, config.opponentTrophies,
            config.opponentDeckIds, config.opponentDeckLevels, config.initialOpponentBoard
        )

        val isLocalFallback = config.matchId.startsWith("local_")
        _state.value = GameSessionState(
            isActive = true,
            isFinished = false,
            matchId = config.matchId,
            arenaId = arenaId,
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
            waveMobIds = waves,
            soundEnabled = soundPlayer.soundEnabled
        )

        recordReplayFrame("Battle started at ${config.place.name}! Shared Boss: ${config.sharedBoss.name}")

        // 1. Real-time Firestore listener for opponent grid, Boss HP, pollution level & spectators
        observeRemoteMatchSession(config.matchId, config.amIPlayer1, config.isBotMatch)

        // 2. Battle timer, VFX cleanup, rival simulation & periodic sync
        var oppWave1Hp = wave1Total
        var oppWave2Hp = BattleRules.waveTotalHp(2, arenaId)

        timerLoopJob = scope.launch {
            while (_state.value.isActive && !_state.value.isFinished) {
                delay(1000L)
                val s = _state.value
                if (!s.isActive || s.isFinished || s.awaitingResult) break

                val nextSec = (s.secondsRemaining - 1).coerceAtLeast(0)
                val nowMs = System.currentTimeMillis()
                val prunedBeams = s.activeBeams.filter { nowMs - it.createdAtMs < 700L }
                val nextHint = if (s.hintPair == null && !s.isBoardAnimating && nowMs - lastPlayerMoveMs >= 4500L) {
                    Match3Engine.findAnyValidMove(s.localPlayer.board)
                } else {
                    s.hintPair
                }

                _state.value = s.copy(
                    secondsRemaining = nextSec,
                    activeBeams = prunedBeams,
                    hintPair = nextHint,
                    comboBannerText = if (nowMs - s.hitFlashToken > 1400L) "" else s.comboBannerText
                )

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

                val latest = _state.value
                if (latest.localPlayer.bossHp <= 0) {
                    concludeSession(won = true)
                    break
                } else if (latest.opponentPlayer.bossHp <= 0) {
                    concludeSession(won = false)
                    break
                } else if (nextSec <= 0) {
                    onTimeUp()
                    break
                }
            }
        }
    }

    /**
     * Time is up. Bot/local matches and player 1 decide immediately with the shared tie-break.
     * Player 2 waits for player 1's FINISHED write (picked up by the observer) and only falls
     * back to the same deterministic rule if nothing arrives, so both sides never both "win".
     */
    private fun onTimeUp() {
        val s = _state.value
        val p1 = if (s.amIPlayer1) s.localPlayer else s.opponentPlayer
        val p2 = if (s.amIPlayer1) s.opponentPlayer else s.localPlayer
        val authoritative = s.isBotMatch || s.amIPlayer1 || s.matchId.startsWith("local_")
        if (authoritative) {
            concludeSession(won = BattleRules.timeoutWinner(p1, p2) == s.localPlayer.uid)
            return
        }
        _state.value = s.copy(awaitingResult = true, comboBannerText = "Time! Confirming result...")
        resultGraceJob?.cancel()
        resultGraceJob = scope.launch {
            delay(5000L)
            val cur = _state.value
            if (!cur.isFinished) {
                val q1 = if (cur.amIPlayer1) cur.localPlayer else cur.opponentPlayer
                val q2 = if (cur.amIPlayer1) cur.opponentPlayer else cur.localPlayer
                concludeSession(won = BattleRules.timeoutWinner(q1, q2) == cur.localPlayer.uid)
            }
        }
    }

    private fun observeRemoteMatchSession(
        matchId: String,
        amIPlayer1: Boolean,
        isBotMatch: Boolean
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
                    if (current.isFinished) return@collect

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

                        // Authoritative remote result (player 1 at time-up, or whoever killed the Boss)
                        if (live.status == "FINISHED" && live.winnerUid.isNotBlank()) {
                            val myUid = activeProfile?.userId.orEmpty()
                            concludeSession(won = live.winnerUid == myUid, publish = false)
                        } else if (updatedOpp.bossHp <= 0) {
                            concludeSession(won = false)
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

    fun onTileTapped(pos: GridPos) {
        val s = _state.value
        if (!s.isActive || s.isFinished || s.isBoardAnimating || s.awaitingResult) return
        val currentSel = s.selectedTile
        when {
            currentSel == null -> _state.value = s.copy(selectedTile = pos, hintPair = null)
            currentSel == pos -> _state.value = s.copy(selectedTile = null)
            Match3Engine.areAdjacent(currentSel, pos) -> performLocalSwap(currentSel, pos)
            else -> _state.value = s.copy(selectedTile = pos, hintPair = null)
        }
    }

    fun onTileSwiped(from: GridPos, to: GridPos) {
        val s = _state.value
        if (!s.isActive || s.isFinished || s.isBoardAnimating || s.awaitingResult) return
        if (Match3Engine.areAdjacent(from, to)) {
            performLocalSwap(from, to)
        }
    }

    private fun performLocalSwap(a: GridPos, b: GridPos) {
        val s = _state.value
        val stepped = Match3Engine.attemptSwapSteps(s.localPlayer.board, a, b)
        if (stepped == null) {
            val token = System.currentTimeMillis()
            lastPlayerMoveMs = token
            _state.value = s.copy(
                selectedTile = null,
                invalidSwapPair = a to b,
                invalidSwapToken = token
            )
            scope.launch {
                delay(280L)
                if (_state.value.invalidSwapToken == token) {
                    _state.value = _state.value.copy(invalidSwapPair = null)
                }
            }
            return
        }
        runSteppedResolution(
            stepped = stepped,
            animateInitialSwap = true,
            abilityBonusDamage = 0,
            usedAbilityLabel = null,
            consumedSlotIndex = null
        )
    }

    fun activateEcoDeckSlot(slotIndex: Int) {
        val s = _state.value
        if (!s.isActive || s.isFinished || s.isBoardAnimating || s.awaitingResult) return
        val charge = s.localPlayer.skillCharges.getOrElse(slotIndex) { 0 }
        if (charge < 100) return

        val bossId = s.localPlayer.deckBossIds.getOrElse(slotIndex) { return }
        val bossLvl = s.localPlayer.deckBossLevels.getOrElse(slotIndex) { 1 }
        val bossDef = GameCatalog.bossById(bossId)
        val stepped = Match3Engine.applyBossAbilitySteps(s.localPlayer.board, bossDef)

        soundPlayer.playAbilityBlast()
        pendingReplayNote = "${s.localPlayer.name} unleashed ${bossDef.name}'s ${bossDef.abilityName}!"

        runSteppedResolution(
            stepped = stepped,
            animateInitialSwap = false,
            abilityBonusDamage = bossDef.abilityDamageAtLevel(bossLvl),
            usedAbilityLabel = "${bossDef.name}: ${bossDef.abilityName}",
            consumedSlotIndex = slotIndex
        )
    }

    private fun runSteppedResolution(
        stepped: SteppedMatchResolution,
        animateInitialSwap: Boolean,
        abilityBonusDamage: Int,
        usedAbilityLabel: String?,
        consumedSlotIndex: Int?
    ) {
        val startState = _state.value
        lastPlayerMoveMs = System.currentTimeMillis()
        _state.value = startState.copy(
            selectedTile = null,
            hintPair = null,
            isBoardAnimating = true
        )

        swapAnimJob?.cancel()
        swapAnimJob = scope.launch {
            try {
                if (animateInitialSwap) {
                    val cur = _state.value
                    _state.value = cur.copy(
                        localPlayer = cur.localPlayer.copy(board = stepped.swappedBoard)
                    )
                    delay(125L)
                }

                stepped.steps.forEachIndexed { idx, step ->
                    if (!_state.value.isActive || _state.value.isFinished) return@launch
                    soundPlayer.playMatch(step.comboIndex)
                    val cur = _state.value
                    _state.value = cur.copy(
                        popBurstTiles = step.clearedTiles,
                        popBurstToken = System.currentTimeMillis(),
                        comboFlashLevel = step.comboIndex,
                        localPlayer = cur.localPlayer.copy(board = step.boardAfterGravity)
                    )
                    delay(if (idx == stepped.steps.lastIndex) 115L else 220L)
                }

                if (_state.value.isActive && !_state.value.isFinished) {
                    applyLocalResolution(
                        res = stepped.finalResolution,
                        abilityBonusDamage = abilityBonusDamage,
                        usedAbilityLabel = usedAbilityLabel,
                        consumedSlotIndex = consumedSlotIndex
                    )
                }
            } finally {
                _state.value = _state.value.copy(isBoardAnimating = false)
            }
        }
    }

    private fun applyLocalResolution(
        res: MatchResolution,
        abilityBonusDamage: Int,
        usedAbilityLabel: String?,
        consumedSlotIndex: Int?
    ) {
        val s = _state.value
        val local = s.localPlayer
        val sharedBoss = GameCatalog.bossById(s.sharedBossId)
        soundPlayer.playMatch(res.comboCount)

        for ((type, count) in res.countsByType) {
            tilesMatchedByType[type] = (tilesMatchedByType[type] ?: 0) + count
        }

        // Eco-Deck preferred recyclable synergy multiplier + skill charges
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

        // Each monster is made of one material and takes extra damage from it.
        val (weakness, weakMult) = BattleRules.weaknessFor(local.waveIndex, local.waveMobs, sharedBoss)
        val weakHits = res.countsByType[weakness] ?: 0
        val comboMult = 1f + (res.comboCount - 1) * 0.35f
        val tileDamage = (BattleRules.weightedCleared(res.countsByType, weakness, weakMult) * 25f * deckBonusMult * comboMult).toInt()
        val totalHit = tileDamage + abilityBonusDamage

        val outcome = BattleRules.applyDamage(
            damage = totalHit,
            waveIndex = local.waveIndex,
            mobs = local.waveMobs,
            bossHp = local.bossHp,
            waves = s.waveMobIds,
            arenaId = s.arenaId,
            bossName = sharedBoss.name
        )
        val waveIdx = outcome.waveIndex
        val bossHp = outcome.bossHp
        if (outcome.waveTransitioned) {
            pendingReplayNote = when (waveIdx) {
                2 -> "${local.name} cleared Wave 1!"
                else -> "${local.name} summoned the Shared Boss!"
            }
        }

        val curHp = if (waveIdx >= 3) bossHp else outcome.mobs.sumOf { it.hp }
        val curMaxHp = if (waveIdx >= 3) local.bossMaxHp else outcome.mobs.sumOf { it.maxHp }

        val totalPool = BattleRules.totalPool(s.arenaId, local.bossMaxHp)
        val newTotalDamage = local.totalDamage + totalHit
        val restorationPct = if (bossHp <= 0) 100 else ((newTotalDamage.toFloat() / totalPool.toFloat()) * 100f).toInt().coerceIn(0, 99)
        val pollutionLevel = (1f - restorationPct / 100f).coerceIn(0f, 1f)

        val nowMs = System.currentTimeMillis()
        val dominantType = res.countsByType.maxByOrNull { it.value }?.key ?: WasteType.PLASTIC_BLUE
        val newBeams = buildBeamBurst(res, dominantType, totalHit, usedAbilityLabel != null, nowMs)

        val comboText = when {
            outcome.defeatedNames.isNotEmpty() -> "${outcome.defeatedNames.last()} purified! -$totalHit"
            usedAbilityLabel != null -> "ECO-SKILL! -$totalHit"
            res.comboCount >= 4 -> "${res.comboCount}x MEGA CASCADE! -$totalHit"
            res.comboCount >= 2 -> "${res.comboCount}x COMBO! -$totalHit"
            weakHits > 0 -> "WEAK SPOT x${if (local.waveIndex < 3) "2" else "1.5"}! -$totalHit"
            res.spawnedSpecialCount > 0 -> "POWER TILE! -$totalHit"
            else -> "-$totalHit HP"
        }

        val nextLocal = local.copy(
            board = res.newBoard,
            boardFlat = Match3Engine.encodeBoardFlat(res.newBoard),
            waveIndex = waveIdx,
            waveMobs = outcome.mobs,
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
            mobDefeatedToken = if (outcome.defeatedNames.isNotEmpty()) nowMs else s.mobDefeatedToken,
            lastDefeatedName = outcome.defeatedNames.lastOrNull() ?: s.lastDefeatedName,
            blueTilesMatched = tilesMatchedByType[WasteType.PLASTIC_BLUE] ?: 0,
            totalTilesMatched = s.totalTilesMatched + res.totalCleared,
            maxCombo = maxOf(s.maxCombo, res.comboCount),
            abilitiesUsedCount = s.abilitiesUsedCount + if (consumedSlotIndex != null) 1 else 0
        )
        if (outcome.defeatedNames.isNotEmpty() && pendingReplayNote.isBlank()) {
            pendingReplayNote = "${local.name} purified ${outcome.defeatedNames.joinToString()}!"
        }

        if (bossHp <= 0) {
            concludeSession(won = true)
        } else {
            // Immediate push on wave clears / Eco-Deck abilities, or ~450ms throttled push on moves
            requestRealtimeSync(immediate = outcome.waveTransitioned || consumedSlotIndex != null)
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
        val tick = BattleRules.botTick(opp.board, opp.skillCharges, opp.deckBossIds, opp.deckBossLevels, arenaId, Random.Default)
        val oppBoard = tick.board
        val dmg = tick.damage
        val charges = tick.charges
        val lastAbility = tick.abilityLabel ?: opp.lastAbilityText
        if (tick.abilityLabel != null) pendingReplayNote = "${opp.name} unleashed ${tick.abilityLabel.substringAfter(": ")}!"

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

        val totalPool = BattleRules.totalPool(arenaId, opp.bossMaxHp)
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
     * Pushes the local player's grid, Boss HP, wave index, skill charges and restoration to
     * Firestore `liveMatches/{matchId}`, throttled (~450ms). Only our own player field is written
     * (plus the bot's in bot matches), which is all the security rules allow.
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

    private suspend fun pushSessionSnapshotToFirestore(
        finished: Boolean,
        winnerUid: String,
        snapshot: GameSessionState = _state.value
    ) {
        val s = snapshot
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

    /**
     * Ends the match exactly once. It never suspends: the jobs that may be calling it (timer,
     * live observer, swap animation) are cancelled, and persistence runs in its own
     * non-cancellable coroutine so rewards and the replay are always written.
     */
    private fun concludeSession(won: Boolean, publish: Boolean = true) {
        val s = _state.value
        if (s.isFinished) return

        val profile = activeProfile
        val winnerUid = if (won) (profile?.userId ?: "player") else s.opponentPlayer.uid.ifBlank { "rival" }
        val winnerName = if (won) s.localPlayer.name else s.opponentPlayer.name
        if (won) soundPlayer.playVictory()

        recordReplayFrame("$winnerName purified ${GameCatalog.placeById(s.placeId).name} and won!")
        val finishedState = s.copy(isActive = false, isFinished = true, awaitingResult = false, wonMatch = won)
        _state.value = finishedState
        timerLoopJob?.cancel()
        liveObserverJob?.cancel()
        throttledSyncJob?.cancel()
        swapAnimJob?.cancel()
        resultGraceJob?.cancel()
        onFinished(won)

        val frames = recordedFrames.toList()
        val matchedByType = tilesMatchedByType.toMap()

        scope.launch(start = CoroutineStart.ATOMIC) {
            withContext(NonCancellable) {
                if (publish) pushSessionSnapshotToFirestore(finished = true, winnerUid = winnerUid, snapshot = finishedState)

                // Persist Battle Replay for Spectator & Friends playback
                var savedReplayId: String? = null
                if (profile != null && (s.amIPlayer1 || s.isBotMatch)) {
                    savedReplayId = repository.saveReplay(buildReplay(s, profile.userId, winnerUid, frames)).getOrNull()
                }

                // Persist Trophies, Eco-Coins, Boss card & Daily Quest telemetry
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
                        tilesMatchedByType = matchedByType,
                        deckBossIds = s.localPlayer.deckBossIds
                    )
                    val reward = repository.applyMatchResult(profile, telemetry).getOrNull()
                    if (_state.value.matchId == s.matchId) {
                        _state.value = _state.value.copy(rewardOutcome = reward, replayId = savedReplayId)
                    }
                }
            }
        }
    }

    private fun buildReplay(s: GameSessionState, creatorUid: String, winnerUid: String, frames: List<ReplayFrame>): MatchReplay {
        val p1 = if (s.amIPlayer1) s.localPlayer else s.opponentPlayer
        val p2 = if (s.amIPlayer1) s.opponentPlayer else s.localPlayer
        fun summary(p: PlayerBattleSnapshot) = ReplaySummary(
            uid = p.uid,
            name = p.name,
            trophies = p.trophies,
            deckBossIds = p.deckBossIds,
            deckBossLevels = p.deckBossLevels,
            totalDamage = p.totalDamage,
            restorationPct = p.restorationPct
        )
        return MatchReplay(
            creatorUid = creatorUid,
            participantUids = listOf(creatorUid, s.opponentPlayer.uid.ifBlank { "rival_uid" }),
            replayPublic = true,
            arenaId = s.arenaId,
            placeId = s.placeId,
            bossId = s.sharedBossId,
            trophyBracket = s.arenaId,
            winnerUid = winnerUid,
            durationSec = (s.totalDurationSec - s.secondsRemaining).coerceIn(1, 300),
            player1Summary = summary(p1),
            player2Summary = summary(p2),
            frames = frames.ifEmpty { listOf(ReplayFrame(elapsedSec = 1, p1Board = p1.boardFlat, p2Board = p2.boardFlat)) }
        )
    }

    fun stopSession() {
        timerLoopJob?.cancel()
        liveObserverJob?.cancel()
        throttledSyncJob?.cancel()
        swapAnimJob?.cancel()
        resultGraceJob?.cancel()
        _state.value = GameSessionState(soundEnabled = soundPlayer.soundEnabled)
    }

    fun release() {
        stopSession()
        soundPlayer.release()
    }
}
