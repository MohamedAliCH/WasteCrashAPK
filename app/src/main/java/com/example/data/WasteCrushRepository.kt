package com.example.data

import android.content.Context
import com.example.R
import com.example.engine.Match3Engine
import com.example.engine.MatchTelemetry
import com.example.model.*
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class MatchRewardOutcome(
    val trophyDelta: Int,
    val coinsEarned: Int,
    val capturedBossId: String?,
    val updatedQuestProgress: Map<String, Int>,
    val newlyCompletedQuestIds: List<String>
)

data class QuestClaimOutcome(
    val coinsAwarded: Int,
    val rewardedBossId: String,
    val cardsAwarded: Int
)

class WasteCrushRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun todayDateKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun generateFriendCode(uid: String): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val hash = abs(uid.hashCode())
        val sb = StringBuilder("TN")
        var v = hash
        repeat(4) {
            sb.append(alphabet[v % alphabet.length])
            v = v / alphabet.length + 7
        }
        return sb.toString()
    }

    suspend fun ensureUserProfile(): Result<UserProfile> {
        var path = "users/unknown"
        return try {
            val uid = requireUserId()
            val user = auth.currentUser
            path = "users/$uid"
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            val today = todayDateKey()
            if (snap.exists()) {
                val existing = snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfile(userId = uid)
                // Reset daily quests if date rolled over
                if (existing.dailyQuestSeedDate != today) {
                    val dailyIds = GameCatalog.quests.filter { it.category == QuestCategory.DAILY }.map { it.id }.toSet()
                    val newProgress = existing.questProgress.filterKeys { it !in dailyIds }
                    val newClaimed = existing.questClaimed.filterKeys { it !in dailyIds }
                    docRef.update(
                        mapOf(
                            "dailyQuestSeedDate" to today,
                            "questProgress" to newProgress,
                            "questClaimed" to newClaimed,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    val refreshed = existing.copy(
                        dailyQuestSeedDate = today,
                        questProgress = newProgress,
                        questClaimed = newClaimed
                    )
                    syncPublicProfile(refreshed)
                    Result.success(refreshed)
                } else {
                    syncPublicProfile(existing)
                    Result.success(existing)
                }
            } else {
                val displayName = (user?.displayName?.takeIf { it.isNotBlank() } ?: "Tunisia Player").take(40)
                val code = generateFriendCode(uid)
                val starter = UserProfile(
                    id = uid,
                    userId = uid,
                    displayName = displayName,
                    friendCode = code,
                    trophies = 0,
                    ecoCoins = 250,
                    wins = 0,
                    losses = 0,
                    equippedDeckIds = listOf("boss_b1", "boss_b2", "boss_b3"),
                    bossLevels = mapOf(
                        "boss_b1" to 1,
                        "boss_b2" to 1,
                        "boss_b3" to 1,
                        "boss_b4" to 1
                    ),
                    bossCopies = mapOf(
                        "boss_b1" to 3,
                        "boss_b2" to 2,
                        "boss_b3" to 1,
                        "boss_b4" to 0
                    ),
                    friendUids = emptyList(),
                    dailyQuestSeedDate = today,
                    questProgress = emptyMap(),
                    questClaimed = emptyMap()
                )
                docRef.set(starter.toCreateMap()).await()
                syncPublicProfile(starter)
                Result.success(starter)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun syncPublicProfile(profile: UserProfile) {
        val path = "publicProfiles/${profile.userId}"
        try {
            val arena = GameCatalog.arenaForTrophies(profile.trophies)
            val pub = PublicProfile(
                userId = profile.userId,
                displayName = profile.displayName,
                friendCode = profile.friendCode,
                trophies = profile.trophies,
                arenaId = arena.id,
                wins = profile.wins,
                equippedDeckIds = profile.equippedDeckIds,
                publicVisible = true
            )
            db.collection("publicProfiles").document(profile.userId).set(pub.toWriteMap()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
        }
    }

    fun observeUserProfile(): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun updateEquippedDeck(newDeckIds: List<String>): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            require(newDeckIds.size == 3 && newDeckIds.distinct().size == 3) { "Eco-Deck must contain 3 distinct Bosses." }
            db.collection("users").document(uid).update(
                mapOf(
                    "equippedDeckIds" to newDeckIds,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            db.collection("publicProfiles").document(uid).update(
                mapOf(
                    "equippedDeckIds" to newDeckIds,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun upgradeBoss(profile: UserProfile, bossId: String): Result<Int> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val boss = GameCatalog.bossById(bossId)
            val currentLevel = profile.bossLevels[bossId] ?: 0
            require(currentLevel >= 1) { "Capture this Boss first to unlock upgrades." }
            require(currentLevel < 10) { "Boss is already at maximum Level 10." }
            val copiesNeeded = GameCatalog.copiesNeededForNextLevel(currentLevel, boss.rarity)
            val coinsNeeded = GameCatalog.coinsNeededForNextLevel(currentLevel, boss.rarity)
            val currentCopies = profile.bossCopies[bossId] ?: 0
            require(currentCopies >= copiesNeeded) { "Need $copiesNeeded copies (have $currentCopies)." }
            require(profile.ecoCoins >= coinsNeeded) { "Need $coinsNeeded Eco-Coins." }

            val newLevel = currentLevel + 1
            val updatedLevels = profile.bossLevels.toMutableMap().apply { put(bossId, newLevel) }
            val updatedCopies = profile.bossCopies.toMutableMap().apply { put(bossId, currentCopies - copiesNeeded) }
            val newCoins = profile.ecoCoins - coinsNeeded

            db.collection("users").document(uid).update(
                mapOf(
                    "bossLevels" to updatedLevels,
                    "bossCopies" to updatedCopies,
                    "ecoCoins" to newCoins,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(newLevel)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun applyMatchResult(
        profile: UserProfile,
        telemetry: MatchTelemetry
    ): Result<MatchRewardOutcome> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val trophyDelta = if (telemetry.won) 30 else -15
            val newTrophies = (profile.trophies + trophyDelta).coerceAtLeast(0)
            val coinsEarned = if (telemetry.won) 95 + telemetry.arenaId * 30 else 30 + telemetry.arenaId * 10
            val newCoins = profile.ecoCoins + coinsEarned
            val newWins = profile.wins + if (telemetry.won) 1 else 0
            val newLosses = profile.losses + if (telemetry.won) 0 else 1

            val updatedLevels = profile.bossLevels.toMutableMap()
            val updatedCopies = profile.bossCopies.toMutableMap()
            var capturedBossId: String? = null
            if (telemetry.won) {
                capturedBossId = telemetry.bossId
                if ((updatedLevels[telemetry.bossId] ?: 0) == 0) {
                    updatedLevels[telemetry.bossId] = 1
                    updatedCopies[telemetry.bossId] = (updatedCopies[telemetry.bossId] ?: 0)
                } else {
                    updatedCopies[telemetry.bossId] = (updatedCopies[telemetry.bossId] ?: 0) + 1
                }
            }

            val updatedQuests = Match3Engine.evaluateQuestUpdates(profile.questProgress, telemetry)
            val newlyCompleted = GameCatalog.quests.filter { q ->
                val before = profile.questProgress[q.id] ?: 0
                val after = updatedQuests[q.id] ?: 0
                before < q.target && after >= q.target
            }.map { it.id }

            db.collection("users").document(uid).update(
                mapOf(
                    "trophies" to newTrophies,
                    "ecoCoins" to newCoins,
                    "wins" to newWins,
                    "losses" to newLosses,
                    "bossLevels" to updatedLevels,
                    "bossCopies" to updatedCopies,
                    "questProgress" to updatedQuests,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val updatedProfile = profile.copy(
                trophies = newTrophies,
                ecoCoins = newCoins,
                wins = newWins,
                losses = newLosses
            )
            syncPublicProfile(updatedProfile)

            Result.success(
                MatchRewardOutcome(
                    trophyDelta = trophyDelta,
                    coinsEarned = coinsEarned,
                    capturedBossId = capturedBossId,
                    updatedQuestProgress = updatedQuests,
                    newlyCompletedQuestIds = newlyCompleted
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun claimQuestReward(profile: UserProfile, quest: QuestDefinition): Result<QuestClaimOutcome> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val currentProgress = profile.questProgress[quest.id] ?: 0
            val alreadyClaimed = profile.questClaimed[quest.id] == true
            require(currentProgress >= quest.target) { "Quest objective not completed yet." }
            require(!alreadyClaimed) { "Quest reward already claimed." }

            val arena = GameCatalog.arenaForTrophies(profile.trophies)
            val pool = GameCatalog.cumulativeBossPoolForArena(arena.id)
            val rarityMatches = pool.filter { it.rarity == quest.rewardRarity }.ifEmpty {
                GameCatalog.bosses.filter { it.rarity == quest.rewardRarity }.ifEmpty { pool }
            }
            val rewardedBoss = rarityMatches.random()

            val updatedLevels = profile.bossLevels.toMutableMap()
            val updatedCopies = profile.bossCopies.toMutableMap()
            if ((updatedLevels[rewardedBoss.id] ?: 0) == 0) {
                updatedLevels[rewardedBoss.id] = 1
                updatedCopies[rewardedBoss.id] = (updatedCopies[rewardedBoss.id] ?: 0) + (quest.cardRewardCount - 1).coerceAtLeast(0)
            } else {
                updatedCopies[rewardedBoss.id] = (updatedCopies[rewardedBoss.id] ?: 0) + quest.cardRewardCount
            }

            val updatedClaimed = profile.questClaimed.toMutableMap().apply { put(quest.id, true) }
            val newCoins = profile.ecoCoins + quest.coinReward

            db.collection("users").document(uid).update(
                mapOf(
                    "ecoCoins" to newCoins,
                    "bossLevels" to updatedLevels,
                    "bossCopies" to updatedCopies,
                    "questClaimed" to updatedClaimed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(
                QuestClaimOutcome(
                    coinsAwarded = quest.coinReward,
                    rewardedBossId = rewardedBoss.id,
                    cardsAwarded = quest.cardRewardCount
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Friends & Public Profiles ---
    suspend fun addFriendByCode(profile: UserProfile, rawCode: String): Result<PublicProfile> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val code = rawCode.trim().uppercase()
        val path = "publicProfiles"
        return try {
            require(code.length in 6..8) { "Enter a valid 6-character Friend Code (e.g. TN4K9P)." }
            val snap = db.collection("publicProfiles")
                .whereEqualTo("publicVisible", true)
                .whereEqualTo("friendCode", code)
                .limit(1)
                .get()
                .await()
            val found = snap.documents.firstOrNull()
                ?.toObject(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalArgumentException("No player found with Friend Code $code."))
            require(found.userId != uid) { "You cannot add your own Friend Code." }
            if (found.userId !in profile.friendUids) {
                val nextFriends = (profile.friendUids + found.userId).take(50)
                db.collection("users").document(uid).update(
                    mapOf(
                        "friendUids" to nextFriends,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(found)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun removeFriend(profile: UserProfile, friendUid: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val nextFriends = profile.friendUids.filterNot { it == friendUid }
            db.collection("users").document(uid).update(
                mapOf(
                    "friendUids" to nextFriends,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observePublicProfiles(): Flow<List<PublicProfile>> = flow {
        val uid = requireUserId()
        val path = "publicProfiles"
        emitAll(
            db.collection("publicProfiles")
                .whereEqualTo("publicVisible", true)
                .limit(80)
                .snapshots()
                .map { snap -> snap.toObjects(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    // --- Matchmaking & Live Matches ---
    suspend fun enqueueForMatchmaking(profile: UserProfile, arenaId: Int): Result<MatchmakingTicket?> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "matchmakingQueue"
        return try {
            // Look for an existing searching player near our Trophy count
            val waitingSnap = db.collection("matchmakingQueue")
                .whereEqualTo("status", "SEARCHING")
                .limit(20)
                .get()
                .await()
            val candidate = waitingSnap.toObjects(MatchmakingTicket::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .filter { it.userId != uid && it.arenaId == arenaId && abs(it.trophies - profile.trophies) <= 300 }
                .minByOrNull { abs(it.trophies - profile.trophies) }

            if (candidate != null) {
                return Result.success(candidate)
            }

            // Otherwise publish our own searching ticket
            val levels = profile.equippedDeckIds.map { profile.bossLevels[it] ?: 1 }
            val myTicket = MatchmakingTicket(
                userId = uid,
                displayName = profile.displayName,
                trophies = profile.trophies,
                arenaId = arenaId,
                deckBossIds = profile.equippedDeckIds,
                deckBossLevels = levels,
                status = "SEARCHING",
                matchId = ""
            )
            db.collection("matchmakingQueue").document(uid).set(myTicket.toCreateMap()).await()
            Result.success(null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeMyQueueTicket(): Flow<MatchmakingTicket?> = flow {
        val uid = requireUserId()
        val path = "matchmakingQueue/$uid"
        emitAll(
            db.collection("matchmakingQueue").document(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) snap.toObject(MatchmakingTicket::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    else null
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun claimOpponentTicket(opponentUid: String, matchId: String): Result<Unit> {
        val path = "matchmakingQueue/$opponentUid"
        return try {
            requireUserId()
            db.collection("matchmakingQueue").document(opponentUid).update(
                mapOf(
                    "status" to "MATCHED",
                    "matchId" to matchId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun leaveMatchmakingQueue(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val path = "matchmakingQueue/$uid"
        return try {
            db.collection("matchmakingQueue").document(uid).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun createLiveMatch(match: LiveMatch): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        var path = "liveMatches"
        return try {
            val docRef = db.collection("liveMatches").document()
            path = docRef.path
            val toWrite = match.copy(creatorUid = uid)
            docRef.set(toWrite.toCreateMap()).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun observeLiveMatch(matchId: String): Flow<LiveMatch?> = flow {
        val uid = requireUserId()
        val path = "liveMatches/$matchId"
        emitAll(
            db.collection("liveMatches").document(matchId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) snap.toObject(LiveMatch::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    else null
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun updateLiveMatchPlayers(
        matchId: String,
        status: String,
        winnerUid: String,
        player1: PlayerLiveState?,
        player2: PlayerLiveState?
    ): Result<Unit> {
        val path = "liveMatches/$matchId"
        return try {
            requireUserId()
            val map = mutableMapOf<String, Any>(
                "status" to status,
                "winnerUid" to winnerUid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (player1 != null) map["player1"] = player1.toMap()
            if (player2 != null) map["player2"] = player2.toMap()
            db.collection("liveMatches").document(matchId).update(map).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adjustSpectatorCount(matchId: String, delta: Long): Result<Unit> {
        val path = "liveMatches/$matchId"
        return try {
            requireUserId()
            db.collection("liveMatches").document(matchId)
                .update(mapOf("spectatorCount" to FieldValue.increment(delta)))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeActiveLiveMatches(): Flow<List<LiveMatch>> = flow {
        val uid = requireUserId()
        val path = "liveMatches"
        emitAll(
            db.collection("liveMatches")
                .whereEqualTo("status", "ACTIVE")
                .limit(30)
                .snapshots()
                .map { snap ->
                    snap.toObjects(LiveMatch::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    // --- Replays ---
    suspend fun saveReplay(replay: MatchReplay): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        var path = "replays"
        return try {
            val docRef = db.collection("replays").document()
            path = docRef.path
            val toWrite = replay.copy(creatorUid = uid, replayPublic = true)
            docRef.set(toWrite.toCreateMap()).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun observePublicReplays(): Flow<List<MatchReplay>> = flow {
        val uid = requireUserId()
        val path = "replays"
        emitAll(
            db.collection("replays")
                .whereEqualTo("replayPublic", true)
                .limit(45)
                .snapshots()
                .map { snap ->
                    snap.toObjects(MatchReplay::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getReplayById(replayId: String): Result<MatchReplay?> {
        val path = "replays/$replayId"
        return try {
            requireUserId()
            val snap = db.collection("replays").document(replayId).get().await()
            val replay = snap.toObject(MatchReplay::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            Result.success(replay)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }
}
