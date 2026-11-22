package com.example

import com.example.data.RewardRules
import com.example.engine.BOARD_SIZE
import com.example.engine.BattleRules
import com.example.engine.BoardTile
import com.example.engine.Match3Engine
import com.example.engine.MatchTelemetry
import com.example.engine.MobState
import com.example.engine.PlayerBattleSnapshot
import com.example.model.GameCatalog
import com.example.model.UserProfile
import com.example.model.WasteType
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class BattleRulesTest {

    private val waves = listOf(listOf("mob_sachet", "mob_bouteille"), listOf("mob_canette", "mob_tesson", "mob_goutte"))

    @Test
    fun `catalog has 15 places with art and 15 bosses with team sprites`() {
        assertEquals(15, GameCatalog.allPlaces.size)
        assertEquals(15, GameCatalog.bosses.size)
        assertEquals(15, GameCatalog.bosses.map { it.id }.distinct().size)
        assertEquals(listOf(0, 300, 600), GameCatalog.arenas.map { it.minTrophies })
        // The four places with painted pollution have both a cartoon and a litter overlay
        val painted = GameCatalog.allPlaces.filter { it.dirtyRes != null }.map { it.id }.toSet()
        assertEquals(setOf("place_hammamet", "place_ichkeul", "place_carthage", "place_tozeur"), painted)
        assertTrue(GameCatalog.allPlaces.filter { it.dirtyRes != null }.all { it.toonRes != null })
    }

    @Test
    fun `match waves are deterministic per match and use the arena minis`() {
        for (arena in 0..2) {
            val a = GameCatalog.matchWaves("match_abc", arena)
            val b = GameCatalog.matchWaves("match_abc", arena)
            assertEquals(a, b)
            assertEquals(2, a[0].size)
            assertEquals(3, a[1].size)
            val pool = GameCatalog.minisForArena(arena).map { it.id }.toSet()
            assertTrue(a.flatten().all { it in pool })
        }
    }

    @Test
    fun `wave hp is split across mobs and sums to the wave total`() {
        val total = BattleRules.waveTotalHp(1, 0)
        val mobs = BattleRules.buildWave(waves[0], total)
        assertEquals(2, mobs.size)
        assertTrue(kotlin.math.abs(mobs.sumOf { it.hp } - total) <= 2)
        assertTrue(mobs.all { it.alive && it.hp == it.maxHp })
    }

    @Test
    fun `damage kills mobs in order, overflows, and advances waves to the boss`() {
        val wave1 = BattleRules.buildWave(waves[0], BattleRules.waveTotalHp(1, 0))
        // Kill only the first mob, a bit of overflow into the second
        val hit1 = BattleRules.applyDamage(wave1[0].hp + 10, 1, wave1, 1500, waves, 0, "Boss")
        assertEquals(1, hit1.waveIndex)
        assertFalse(hit1.mobs[0].alive)
        assertEquals(wave1[1].hp - 10, hit1.mobs[1].hp)
        assertEquals(listOf("Sachet volant"), hit1.defeatedNames)

        // Finish wave 1 exactly: wave 2 (3 mobs) appears even with no damage left over
        val hit2 = BattleRules.applyDamage(hit1.mobs[1].hp, 1, hit1.mobs, 1500, waves, 0, "Boss")
        assertEquals(2, hit2.waveIndex)
        assertTrue(hit2.waveTransitioned)
        assertEquals(3, hit2.mobs.size)
        assertEquals(waves[1], hit2.mobs.map { it.id })

        // Huge hit clears wave 2 and the rest goes into the boss
        val wave2Hp = hit2.mobs.sumOf { it.hp }
        val hit3 = BattleRules.applyDamage(wave2Hp + 400, 2, hit2.mobs, 1500, waves, 0, "Boss")
        assertEquals(3, hit3.waveIndex)
        assertEquals(1100, hit3.bossHp)
        assertTrue(hit3.mobs.isEmpty())

        val kill = BattleRules.applyDamage(5000, 3, emptyList(), 1100, waves, 0, "Plasticos")
        assertEquals(0, kill.bossHp)
        assertEquals(listOf("Plasticos"), kill.defeatedNames)
    }

    @Test
    fun `weakness targets the first alive mob's material`() {
        val boss = GameCatalog.bossById("boss_b1")
        val mobs = listOf(MobState("mob_sachet", 0, 100), MobState("mob_canette", 80, 100))
        val (weak, mult) = BattleRules.weaknessFor(1, mobs, boss)
        assertEquals(WasteType.ALUMINUM_YELLOW, weak)
        assertEquals(2f, mult)
        val counts = mapOf(WasteType.ALUMINUM_YELLOW to 3, WasteType.GLASS_GREEN to 3)
        assertEquals(9f, BattleRules.weightedCleared(counts, weak, mult))
        assertEquals(boss.preferredWaste to 1.5f, BattleRules.weaknessFor(3, emptyList(), boss))
    }

    @Test
    fun `timeout winner is identical on both clients and breaks ties deterministically`() {
        val a = PlayerBattleSnapshot(uid = "alice", totalDamage = 900, restorationPct = 40)
        val b = PlayerBattleSnapshot(uid = "bob", totalDamage = 900, restorationPct = 40)
        assertEquals(BattleRules.timeoutWinner(a, b), BattleRules.timeoutWinner(a, b))
        assertEquals("alice", BattleRules.timeoutWinner(a, b))
        assertEquals("alice", BattleRules.timeoutWinner(b, a)) // order doesn't matter on a full tie
        assertEquals("bob", BattleRules.timeoutWinner(a, b.copy(totalDamage = 901)))
        assertEquals("bob", BattleRules.timeoutWinner(a, b.copy(restorationPct = 41)))
    }

    @Test
    fun `boards never keep leftover matches after resolution`() {
        val rng = Random(1234)
        repeat(200) {
            val board = Match3Engine.createInitialBoard(rng)
            val move = Match3Engine.findAnyValidMove(board) ?: return@repeat
            val res = Match3Engine.attemptSwap(board, move.first, move.second, rng) ?: return@repeat
            assertFalse("leftover run", hasRun(res.newBoard))
            assertTrue(Match3Engine.hasValidMove(res.newBoard))
        }
    }

    @Test
    fun `breakResidualMatches removes runs`() {
        val types = WasteType.entries
        // Striped board with a forced horizontal run of PLASTIC in row 0
        val board = List(BOARD_SIZE) { r ->
            List(BOARD_SIZE) { c ->
                val t = if (r == 0 && c < 4) WasteType.PLASTIC_BLUE else types[(r * 2 + c * 3 + 1) % types.size]
                BoardTile(id = (r * BOARD_SIZE + c).toLong(), type = t)
            }
        }
        assertTrue(hasRun(board))
        assertFalse(hasRun(Match3Engine.breakResidualMatches(board, Random(1))))
    }

    @Test
    fun `match result rewards and trophy floor`() {
        val profile = UserProfile(userId = "u", trophies = 5, ecoCoins = 100, bossLevels = mapOf("boss_b1" to 1), bossCopies = mapOf("boss_b1" to 0))
        val tele = MatchTelemetry(false, 0, "place_hammamet", "boss_b5", 100, 10, 1, 0, 1, 20, emptyMap(), listOf("boss_b1"))
        val (lost, outcome) = RewardRules.matchResult(profile, tele)
        assertEquals(0, lost.trophies)
        assertEquals(-5, outcome.trophyDelta) // real delta after the floor
        assertEquals(1, lost.losses)

        val (won, wonOutcome) = RewardRules.matchResult(profile, tele.copy(won = true))
        assertEquals(35, won.trophies)
        assertEquals(1, won.bossLevels["boss_b5"])
        assertEquals("boss_b5", wonOutcome.capturedBossId)
        assertEquals(100 + RewardRules.coinsFor(true, 0), won.ecoCoins)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `upgrade requires enough copies`() {
        RewardRules.upgradeBoss(UserProfile(bossLevels = mapOf("boss_b1" to 1), bossCopies = mapOf("boss_b1" to 0), ecoCoins = 9999), "boss_b1")
    }

    @Test
    fun `AI rival needs roughly a full match to purify a place`() {
        for (arena in 0..2) {
            val pool = BattleRules.totalPool(arena, GameCatalog.cumulativeBossPoolForArena(arena).map { it.bossBattleHp }.average().toInt())
            val times = (1..20).map { seed ->
                val rng = Random(seed * 31 + arena)
                var board = Match3Engine.createInitialBoard(rng)
                var charges = listOf(0, 0, 0)
                var dealt = 0
                var sec = 0
                while (dealt < pool && sec < 400) {
                    val t = BattleRules.botTick(board, charges, listOf("boss_b1", "boss_b2", "boss_b3"), listOf(1 + arena, 1 + arena, 1 + arena), arena, rng)
                    board = t.board; charges = t.charges; dealt += t.damage; sec++
                }
                sec
            }
            val avg = times.average()
            println("arena $arena: AI purifies in ${avg.toInt()}s (min ${times.min()}, max ${times.max()})")
            assertTrue("arena $arena too fast: $avg s", avg >= 70)
            assertTrue("arena $arena too slow: $avg s", avg <= 130)
        }
    }

    private fun hasRun(board: List<List<BoardTile>>): Boolean {
        for (r in 0 until BOARD_SIZE) for (c in 0 until BOARD_SIZE - 2) {
            if (board[r][c].type == board[r][c + 1].type && board[r][c].type == board[r][c + 2].type) return true
        }
        for (c in 0 until BOARD_SIZE) for (r in 0 until BOARD_SIZE - 2) {
            if (board[r][c].type == board[r + 1][c].type && board[r][c].type == board[r + 2][c].type) return true
        }
        return false
    }
}
