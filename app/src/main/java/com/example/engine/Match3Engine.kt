package com.example.engine

import com.example.model.BossAbilityType
import com.example.model.BossDefinition
import com.example.model.BossRarity
import com.example.model.GameCatalog
import com.example.model.QuestMetric
import com.example.model.WasteType
import kotlin.math.abs
import kotlin.random.Random

const val BOARD_SIZE = 7

enum class SpecialTileType {
    NONE,
    LINE_H,
    LINE_V,
    ECO_BOMB
}

data class BoardTile(
    val id: Long,
    val type: WasteType,
    val special: SpecialTileType = SpecialTileType.NONE
)

data class GridPos(val row: Int, val col: Int)

data class MatchResolution(
    val newBoard: List<List<BoardTile>>,
    val matchedPositions: Set<GridPos>,
    val countsByType: Map<WasteType, Int>,
    val totalCleared: Int,
    val comboCount: Int,
    val spawnedSpecialCount: Int
)

data class MatchTelemetry(
    val won: Boolean,
    val arenaId: Int,
    val placeId: String,
    val bossId: String,
    val totalDamage: Int,
    val restorationPct: Int,
    val wavesCleared: Int,
    val abilitiesUsed: Int,
    val maxCombo: Int,
    val totalTilesMatched: Int,
    val tilesMatchedByType: Map<WasteType, Int>,
    val deckBossIds: List<String>
)

object Match3Engine {
    private var nextTileId = 1L

    private fun nextId(): Long {
        nextTileId += 1L
        return nextTileId
    }

    fun createInitialBoard(random: Random = Random.Default): List<List<BoardTile>> {
        val types = WasteType.entries
        val grid = Array(BOARD_SIZE) { arrayOfNulls<BoardTile>(BOARD_SIZE) }
        for (r in 0 until BOARD_SIZE) {
            for (c in 0 until BOARD_SIZE) {
                val forbidden = mutableSetOf<WasteType>()
                if (c >= 2 && grid[r][c - 1]?.type == grid[r][c - 2]?.type) {
                    grid[r][c - 1]?.type?.let { forbidden.add(it) }
                }
                if (r >= 2 && grid[r - 1][c]?.type == grid[r - 2][c]?.type) {
                    grid[r - 1][c]?.type?.let { forbidden.add(it) }
                }
                val candidates = types.filterNot { it in forbidden }
                val chosen = candidates[random.nextInt(candidates.size)]
                grid[r][c] = BoardTile(id = nextId(), type = chosen)
            }
        }
        val result = grid.map { row -> row.map { it!! } }
        return if (hasValidMove(result)) result else ensurePlayableBoard(result, random)
    }

    fun encodeBoardFlat(board: List<List<BoardTile>>): List<Int> {
        val flat = ArrayList<Int>(BOARD_SIZE * BOARD_SIZE)
        for (r in 0 until BOARD_SIZE) {
            for (c in 0 until BOARD_SIZE) {
                val tile = board.getOrNull(r)?.getOrNull(c)
                if (tile != null) {
                    flat.add(tile.type.ordinal + tile.special.ordinal * 10)
                } else {
                    flat.add(0)
                }
            }
        }
        return flat
    }

    fun decodeBoardFlat(flat: List<Int>): List<List<BoardTile>> {
        val types = WasteType.entries
        val specials = SpecialTileType.entries
        if (flat.size < BOARD_SIZE * BOARD_SIZE) {
            return createInitialBoard(Random(42))
        }
        return List(BOARD_SIZE) { r ->
            List(BOARD_SIZE) { c ->
                val raw = flat[r * BOARD_SIZE + c]
                val typeOrd = (raw % 10).coerceIn(0, types.lastIndex)
                val specOrd = (raw / 10).coerceIn(0, specials.lastIndex)
                BoardTile(
                    id = (r * BOARD_SIZE + c + 1).toLong(),
                    type = types[typeOrd],
                    special = specials[specOrd]
                )
            }
        }
    }

    fun areAdjacent(a: GridPos, b: GridPos): Boolean {
        return abs(a.row - b.row) + abs(a.col - b.col) == 1
    }

    fun attemptSwap(
        board: List<List<BoardTile>>,
        a: GridPos,
        b: GridPos,
        random: Random = Random.Default
    ): MatchResolution? {
        if (!areAdjacent(a, b)) return null
        val mutable = board.map { it.toMutableList() }.toMutableList()
        val tileA = mutable[a.row][a.col]
        val tileB = mutable[b.row][b.col]
        mutable[a.row][a.col] = tileB
        mutable[b.row][b.col] = tileA

        // Special-tile instant swap activation (e.g., swapping an Eco-Bomb or Line Blaster)
        val specialTriggered = tileA.special != SpecialTileType.NONE || tileB.special != SpecialTileType.NONE
        val initialMatches = findMatchGroups(mutable)
        if (initialMatches.isEmpty() && !specialTriggered) {
            return null
        }

        val totalCounts = mutableMapOf<WasteType, Int>()
        val allMatchedPositions = mutableSetOf<GridPos>()
        var totalCleared = 0
        var comboCount = 0
        var spawnedSpecials = 0

        if (initialMatches.isEmpty() && specialTriggered) {
            comboCount = 1
            val burst = mutableSetOf<GridPos>()
            if (tileA.special != SpecialTileType.NONE) {
                burst.addAll(expandSpecial(mutable, b, tileA.special))
            }
            if (tileB.special != SpecialTileType.NONE) {
                burst.addAll(expandSpecial(mutable, a, tileB.special))
            }
            allMatchedPositions.addAll(burst)
            for (pos in burst) {
                val t = mutable[pos.row][pos.col]
                totalCounts[t.type] = (totalCounts[t.type] ?: 0) + 1
            }
            totalCleared += burst.size
            refillCleared(mutable, burst, emptyMap(), random)
        }

        while (true) {
            val groups = findMatchGroups(mutable)
            if (groups.isEmpty()) break
            comboCount++

            val toClear = mutableSetOf<GridPos>()
            val spawns = mutableMapOf<GridPos, BoardTile>()

            for (group in groups) {
                toClear.addAll(group.positions)
                if (group.positions.size >= 5) {
                    val anchor = group.positions[group.positions.size / 2]
                    spawns[anchor] = BoardTile(nextId(), group.type, SpecialTileType.ECO_BOMB)
                    spawnedSpecials++
                } else if (group.positions.size == 4) {
                    val anchor = group.positions[1]
                    val spec = if (group.horizontal) SpecialTileType.LINE_H else SpecialTileType.LINE_V
                    spawns[anchor] = BoardTile(nextId(), group.type, spec)
                    spawnedSpecials++
                }
            }

            // Expand any special tiles caught in the match
            val extraBurst = mutableSetOf<GridPos>()
            for (pos in toClear) {
                val tile = mutable[pos.row][pos.col]
                if (tile.special != SpecialTileType.NONE) {
                    extraBurst.addAll(expandSpecial(mutable, pos, tile.special))
                }
            }
            toClear.addAll(extraBurst)

            allMatchedPositions.addAll(toClear)
            for (pos in toClear) {
                val tile = mutable[pos.row][pos.col]
                totalCounts[tile.type] = (totalCounts[tile.type] ?: 0) + 1
            }
            totalCleared += toClear.size
            refillCleared(mutable, toClear, spawns, random)
            if (comboCount >= 10) break
        }

        val finalBoard = ensurePlayableBoard(mutable.map { it.toList() }, random)
        return MatchResolution(
            newBoard = finalBoard,
            matchedPositions = allMatchedPositions,
            countsByType = totalCounts,
            totalCleared = totalCleared,
            comboCount = comboCount.coerceAtLeast(1),
            spawnedSpecialCount = spawnedSpecials
        )
    }

    fun applyBossAbility(
        board: List<List<BoardTile>>,
        boss: BossDefinition,
        random: Random = Random.Default
    ): MatchResolution {
        val mutable = board.map { it.toMutableList() }.toMutableList()
        val burst = mutableSetOf<GridPos>()

        when (boss.abilityType) {
            BossAbilityType.ROW_SWEEP -> {
                val targetRow = random.nextInt(1, BOARD_SIZE - 1)
                for (c in 0 until BOARD_SIZE) burst.add(GridPos(targetRow, c))
            }
            BossAbilityType.COL_SWEEP -> {
                val targetCol = random.nextInt(1, BOARD_SIZE - 1)
                for (r in 0 until BOARD_SIZE) burst.add(GridPos(r, targetCol))
                if (boss.rarity == BossRarity.EPIC || boss.rarity == BossRarity.LEGENDARY) {
                    val secondCol = (targetCol + 1).coerceAtMost(BOARD_SIZE - 1)
                    for (r in 0 until BOARD_SIZE) burst.add(GridPos(r, secondCol))
                }
            }
            BossAbilityType.ECO_BOMB -> {
                val cr = random.nextInt(1, BOARD_SIZE - 1)
                val cc = random.nextInt(1, BOARD_SIZE - 1)
                for (dr in -1..1) {
                    for (dc in -1..1) {
                        burst.add(GridPos(cr + dr, cc + dc))
                    }
                }
            }
            BossAbilityType.COLOR_PURGE -> {
                for (r in 0 until BOARD_SIZE) {
                    for (c in 0 until BOARD_SIZE) {
                        if (mutable[r][c].type == boss.preferredWaste) {
                            burst.add(GridPos(r, c))
                        }
                    }
                }
                if (burst.isEmpty()) {
                    for (c in 0 until BOARD_SIZE) burst.add(GridPos(3, c))
                }
            }
            BossAbilityType.MEGA_STRIKE -> {
                val center = 3
                for (i in 0 until BOARD_SIZE) {
                    burst.add(GridPos(center, i))
                    burst.add(GridPos(i, center))
                }
            }
        }

        val counts = mutableMapOf<WasteType, Int>()
        for (pos in burst) {
            val t = mutable[pos.row][pos.col]
            counts[t.type] = (counts[t.type] ?: 0) + 1
        }
        var totalCleared = burst.size
        refillCleared(mutable, burst, emptyMap(), random)

        // Resolve any cascades triggered by the ability!
        var combos = 1
        while (combos < 6) {
            val groups = findMatchGroups(mutable)
            if (groups.isEmpty()) break
            combos++
            val cascadeClear = groups.flatMap { it.positions }.toSet()
            for (pos in cascadeClear) {
                val t = mutable[pos.row][pos.col]
                counts[t.type] = (counts[t.type] ?: 0) + 1
            }
            totalCleared += cascadeClear.size
            refillCleared(mutable, cascadeClear, emptyMap(), random)
        }

        val finalBoard = ensurePlayableBoard(mutable.map { it.toList() }, random)
        return MatchResolution(
            newBoard = finalBoard,
            matchedPositions = burst,
            countsByType = counts,
            totalCleared = totalCleared,
            comboCount = combos,
            spawnedSpecialCount = 0
        )
    }

    fun findAnyValidMove(board: List<List<BoardTile>>): Pair<GridPos, GridPos>? {
        val mutable = board.map { it.toMutableList() }.toMutableList()
        for (r in 0 until BOARD_SIZE) {
            for (c in 0 until BOARD_SIZE) {
                if (c + 1 < BOARD_SIZE) {
                    swapInPlace(mutable, r, c, r, c + 1)
                    val ok = findMatchGroups(mutable).isNotEmpty()
                    swapInPlace(mutable, r, c, r, c + 1)
                    if (ok) return GridPos(r, c) to GridPos(r, c + 1)
                }
                if (r + 1 < BOARD_SIZE) {
                    swapInPlace(mutable, r, c, r + 1, c)
                    val ok = findMatchGroups(mutable).isNotEmpty()
                    swapInPlace(mutable, r, c, r + 1, c)
                    if (ok) return GridPos(r, c) to GridPos(r + 1, c)
                }
            }
        }
        return null
    }

    fun hasValidMove(board: List<List<BoardTile>>): Boolean = findAnyValidMove(board) != null

    fun ensurePlayableBoard(board: List<List<BoardTile>>, random: Random = Random.Default): List<List<BoardTile>> {
        if (hasValidMove(board)) return board
        return createInitialBoard(random)
    }

    private fun swapInPlace(grid: MutableList<MutableList<BoardTile>>, r1: Int, c1: Int, r2: Int, c2: Int) {
        val tmp = grid[r1][c1]
        grid[r1][c1] = grid[r2][c2]
        grid[r2][c2] = tmp
    }

    private data class MatchGroup(
        val type: WasteType,
        val positions: List<GridPos>,
        val horizontal: Boolean
    )

    private fun findMatchGroups(grid: List<List<BoardTile>>): List<MatchGroup> {
        val groups = mutableListOf<MatchGroup>()
        // Horizontal runs
        for (r in 0 until BOARD_SIZE) {
            var c = 0
            while (c < BOARD_SIZE) {
                val type = grid[r][c].type
                var end = c + 1
                while (end < BOARD_SIZE && grid[r][end].type == type) end++
                if (end - c >= 3) {
                    groups.add(MatchGroup(type, (c until end).map { GridPos(r, it) }, true))
                }
                c = end
            }
        }
        // Vertical runs
        for (c in 0 until BOARD_SIZE) {
            var r = 0
            while (r < BOARD_SIZE) {
                val type = grid[r][c].type
                var end = r + 1
                while (end < BOARD_SIZE && grid[end][c].type == type) end++
                if (end - r >= 3) {
                    groups.add(MatchGroup(type, (r until end).map { GridPos(it, c) }, false))
                }
                r = end
            }
        }
        return groups
    }

    private fun expandSpecial(
        grid: List<List<BoardTile>>,
        origin: GridPos,
        special: SpecialTileType
    ): Set<GridPos> {
        val affected = mutableSetOf<GridPos>()
        when (special) {
            SpecialTileType.LINE_H -> {
                for (c in 0 until BOARD_SIZE) affected.add(GridPos(origin.row, c))
            }
            SpecialTileType.LINE_V -> {
                for (r in 0 until BOARD_SIZE) affected.add(GridPos(r, origin.col))
            }
            SpecialTileType.ECO_BOMB -> {
                for (dr in -1..1) {
                    for (dc in -1..1) {
                        val nr = origin.row + dr
                        val nc = origin.col + dc
                        if (nr in 0 until BOARD_SIZE && nc in 0 until BOARD_SIZE) {
                            affected.add(GridPos(nr, nc))
                        }
                    }
                }
            }
            SpecialTileType.NONE -> {}
        }
        return affected
    }

    private fun refillCleared(
        grid: MutableList<MutableList<BoardTile>>,
        cleared: Set<GridPos>,
        spawns: Map<GridPos, BoardTile>,
        random: Random
    ) {
        val types = WasteType.entries
        for (c in 0 until BOARD_SIZE) {
            val survivors = mutableListOf<BoardTile>()
            for (r in BOARD_SIZE - 1 downTo 0) {
                val pos = GridPos(r, c)
                if (pos in spawns) {
                    survivors.add(spawns[pos]!!)
                } else if (pos !in cleared) {
                    survivors.add(grid[r][c])
                }
            }
            while (survivors.size < BOARD_SIZE) {
                survivors.add(BoardTile(id = nextId(), type = types[random.nextInt(types.size)]))
            }
            for (r in BOARD_SIZE - 1 downTo 0) {
                grid[r][c] = survivors[BOARD_SIZE - 1 - r]
            }
        }
    }

    fun evaluateQuestUpdates(
        currentProgress: Map<String, Int>,
        telemetry: MatchTelemetry
    ): Map<String, Int> {
        val updated = currentProgress.toMutableMap()
        val blueCount = telemetry.tilesMatchedByType[WasteType.PLASTIC_BLUE] ?: 0
        val allEpicDeck = telemetry.deckBossIds.isNotEmpty() &&
            telemetry.deckBossIds.all { GameCatalog.bossById(it).rarity == BossRarity.EPIC }

        for (quest in GameCatalog.quests) {
            val prev = updated[quest.id] ?: 0
            val next = when (quest.metric) {
                QuestMetric.SINGLE_MATCH_BLUE_TILES -> maxOf(prev, blueCount)
                QuestMetric.WIN_WITH_ONLY_EPIC_BOSSES -> if (telemetry.won && allEpicDeck) prev + 1 else prev
                QuestMetric.CLEAR_WAVES -> prev + telemetry.wavesCleared
                QuestMetric.USE_BOSS_ABILITIES -> prev + telemetry.abilitiesUsed
                QuestMetric.MATCH_TOTAL_TILES -> prev + telemetry.totalTilesMatched
                QuestMetric.PURIFY_BEACHES -> if (telemetry.arenaId == 0 && telemetry.restorationPct >= 100) prev + 1 else prev
                QuestMetric.HIGH_COMBO_CHAIN -> if (telemetry.maxCombo >= 4) maxOf(prev, 1) else prev
            }
            updated[quest.id] = next.coerceAtMost(quest.target)
        }
        return updated
    }
}
