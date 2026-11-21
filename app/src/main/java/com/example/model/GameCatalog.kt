package com.example.model

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.R
import com.example.ui.theme.*

enum class WasteType(
    val displayName: String,
    val shortLabel: String,
    val primaryColor: Color,
    val darkColor: Color,
    val ecoTip: String
) {
    PLASTIC_BLUE("Plastic Bottle", "Plastic", TilePlasticBlue, TilePlasticBlueDark, "Rinse & crush plastic bottles to save coastal wildlife."),
    GLASS_GREEN("Glass Bottle", "Glass", TileGlassGreen, TileGlassGreenDark, "Glass can be recycled endlessly without losing purity."),
    ALUMINUM_YELLOW("Aluminum Can", "Can", TileAluminumYellow, TileAluminumYellowDark, "Recycling 1 can saves enough energy to run a TV for 3 hours."),
    ORGANIC_RED("Organic Core", "Organic", TileOrganicRed, TileOrganicRedDark, "Composting food scraps enriches Tunisian olive & palm groves."),
    CARDBOARD_TAN("Cardboard Box", "Box", TileCardboardTan, TileCardboardTanDark, "Flatten clean cardboard boxes to reduce landfill volume.")
}

enum class BossRarity(
    val label: String,
    val frameColor: Color,
    val accentColor: Color,
    val basePowerMultiplier: Float
) {
    COMMON("Common", RarityCommon, Color(0xFF94AFCB), 1.0f),
    RARE("Rare", RarityRare, Color(0xFFFCD34D), 1.18f),
    EPIC("Epic", RarityEpic, Color(0xFFC084FC), 1.38f),
    LEGENDARY("Legendary", RarityLegendary, RarityLegendaryGold, 1.62f)
}

enum class BossAbilityType {
    ROW_SWEEP,
    COL_SWEEP,
    ECO_BOMB,
    COLOR_PURGE,
    MEGA_STRIKE
}

data class TunisianPlace(
    val id: String,
    val name: String,
    val arenaId: Int,
    val regionTag: String,
    val ecoFact: String
)

data class ArenaDefinition(
    val id: Int,
    val name: String,
    val subtitle: String,
    val minTrophies: Int,
    val nextArenaTrophies: Int?,
    @DrawableRes val bannerRes: Int,
    val primaryColor: Color,
    val secondaryColor: Color,
    val places: List<TunisianPlace>
)

data class BossDefinition(
    val id: String,
    val name: String,
    val title: String,
    val arenaId: Int,
    val rarity: BossRarity,
    @DrawableRes val spriteRes: Int,
    val bodyColor: Color,
    val accentColor: Color,
    val preferredWaste: WasteType,
    val abilityType: BossAbilityType,
    val abilityName: String,
    val abilityDescription: String,
    val baseAbilityDamage: Int,
    val chargeRequired: Int,
    val bossBattleHp: Int,
    val lore: String
) {
    fun abilityDamageAtLevel(level: Int): Int {
        val lvl = level.coerceAtLeast(1)
        return (baseAbilityDamage * (1f + (lvl - 1) * 0.16f)).toInt()
    }

    fun tileBonusPercentAtLevel(level: Int): Int {
        val lvl = level.coerceAtLeast(1)
        return 10 + (lvl - 1) * 4
    }
}

enum class QuestCategory {
    DAILY,
    CHALLENGE
}

enum class QuestMetric {
    SINGLE_MATCH_BLUE_TILES,
    WIN_WITH_ONLY_EPIC_BOSSES,
    CLEAR_WAVES,
    USE_BOSS_ABILITIES,
    MATCH_TOTAL_TILES,
    PURIFY_BEACHES,
    HIGH_COMBO_CHAIN
}

data class QuestDefinition(
    val id: String,
    val title: String,
    val description: String,
    val category: QuestCategory,
    val metric: QuestMetric,
    val target: Int,
    val coinReward: Int,
    val cardRewardCount: Int,
    val rewardRarity: BossRarity
)

object GameCatalog {

    val arenas: List<ArenaDefinition> = listOf(
        ArenaDefinition(
            id = 0,
            name = "Arena 0: Beaches",
            subtitle = "Mediterranean Coastline",
            minTrophies = 0,
            nextArenaTrophies = 400,
            bannerRes = R.drawable.img_arena_beaches,
            primaryColor = SidiCobalt,
            secondaryColor = Color(0xFF00B4D8),
            places = listOf(
                TunisianPlace("place_hammamet", "Hammamet", 0, "Cap Bon", "Golden sands protected from single-use coastal plastics."),
                TunisianPlace("place_ghar_el_melh", "Ghar El Melh", 0, "Bizerte Lagoon", "Historic wetland lagoon thriving when marine debris is cleared."),
                TunisianPlace("place_mahdia", "Mahdia", 0, "Sahel Coast", "Crystal turquoise peninsula famous for clean reef waters."),
                TunisianPlace("place_sidi_bou_said", "Sidi Bou Said", 0, "Gulf of Tunis", "Iconic cobalt-and-white cliffside overlooking the Mediterranean."),
                TunisianPlace("place_kelibia", "Kelibia", 0, "Cap Bon Tip", "Fortress shoreline treasured for pristine sandy coves.")
            )
        ),
        ArenaDefinition(
            id = 1,
            name = "Arena 1: Cultural Monuments",
            subtitle = "Ancient Heritage & Medinas",
            minTrophies = 400,
            nextArenaTrophies = 900,
            bannerRes = R.drawable.img_arena_monuments,
            primaryColor = SaharanAmber,
            secondaryColor = Color(0xFFD97706),
            places = listOf(
                TunisianPlace("place_carthage", "Carthage", 1, "Tunis", "Millennia-old Punic & Roman ruins restored to sunlit splendor."),
                TunisianPlace("place_el_jem", "El Jem", 1, "Mahdia Gov.", "Colossal Roman amphitheatre preserved free of urban litter."),
                TunisianPlace("place_dougga", "Dougga", 1, "Beja Highlands", "UNESCO hilltop ruins surrounded by olive groves."),
                TunisianPlace("place_kairouan", "Kairouan Medina", 1, "Central Tunisia", "Sacred historic medina gleaming with clean limestone alleys."),
                TunisianPlace("place_ribat_monastir", "Ribat of Monastir", 1, "Monastir", "Coastal stone fortress watching over the azure bay.")
            )
        ),
        ArenaDefinition(
            id = 2,
            name = "Arena 2: Desert & Oases",
            subtitle = "Saharan Dunes & Springs",
            minTrophies = 900,
            nextArenaTrophies = null,
            bannerRes = R.drawable.img_arena_desert,
            primaryColor = Color(0xFFEA580C),
            secondaryColor = EcoEmerald,
            places = listOf(
                TunisianPlace("place_tozeur", "Tozeur", 2, "Djerid Oasis", "Lush date-palm oasis powered by sustainable spring irrigation."),
                TunisianPlace("place_douz", "Douz", 2, "Gateway to Sahara", "Rolling golden ergs kept spotless for desert travelers."),
                TunisianPlace("place_chebika", "Chebika", 2, "Atlas Mountain Oasis", "Emerald canyon waterfall shimmering between rocky peaks."),
                TunisianPlace("place_matmata", "Matmata", 2, "Dahar Hills", "Iconic troglodyte dwellings nestled in warm earthen craters."),
                TunisianPlace("place_chott_el_djerid", "Chott El Djerid", 2, "Great Salt Lake", "Mirrored salt flats reflecting vivid Saharan skies.")
            )
        )
    )

    val allPlaces: List<TunisianPlace> = arenas.flatMap { it.places }

    @DrawableRes val miniWave1Sprite: Int = R.drawable.sprite_miniboss_drop1
    @DrawableRes val miniWave2Sprite: Int = R.drawable.sprite_miniboss_drop2

    // 15 Unlockable Bosses (5 per Arena, Cumulative Pool) using custom uploaded sprites
    val bosses: List<BossDefinition> = listOf(
        // Arena 0: Beaches (5 Coastal Bosses)
        BossDefinition(
            id = "boss_b1", name = "Plasti-Crab", title = "Bottle-Claw Scuttler",
            arenaId = 0, rarity = BossRarity.COMMON,
            spriteRes = R.drawable.sprite_boss_crab,
            bodyColor = Color(0xFF1E88E5), accentColor = Color(0xFF90CAF9),
            preferredWaste = WasteType.PLASTIC_BLUE,
            abilityType = BossAbilityType.ROW_SWEEP,
            abilityName = "Tidal Row Sweep",
            abilityDescription = "Blasts an entire horizontal row and boosts Blue Plastic matches.",
            baseAbilityDamage = 220, chargeRequired = 14, bossBattleHp = 1450,
            lore = "Born from tangled beach bottles along Hammamet, now purified to sweep shorelines clean."
        ),
        BossDefinition(
            id = "boss_b2", name = "Scrap-Chomper", title = "Lagoon Junk Drifter",
            arenaId = 0, rarity = BossRarity.COMMON,
            spriteRes = R.drawable.sprite_boss_scrap,
            bodyColor = Color(0xFF10B981), accentColor = Color(0xFFA7F3D0),
            preferredWaste = WasteType.GLASS_GREEN,
            abilityType = BossAbilityType.COL_SWEEP,
            abilityName = "Emerald Reef Beam",
            abilityDescription = "Clears a full vertical column of tiles with sparkling glass energy.",
            baseAbilityDamage = 240, chargeRequired = 15, bossBattleHp = 1500,
            lore = "Collects sea-tumbled glass from Ghar El Melh into luminous eco-beams."
        ),
        BossDefinition(
            id = "boss_b3", name = "Trash-Maw", title = "Beachfront Litter Beast",
            arenaId = 0, rarity = BossRarity.RARE,
            spriteRes = R.drawable.sprite_boss_trashbeast,
            bodyColor = Color(0xFFF59E0B), accentColor = Color(0xFFFEF08A),
            preferredWaste = WasteType.ALUMINUM_YELLOW,
            abilityType = BossAbilityType.COLOR_PURGE,
            abilityName = "Solar Can Magnet",
            abilityDescription = "Magnetizes and crushes all Yellow Aluminum Cans on the 7x7 board!",
            baseAbilityDamage = 290, chargeRequired = 16, bossBattleHp = 1620,
            lore = "Prowls Mahdia's cliffs snatching stray soda cans out of the sea breeze."
        ),
        BossDefinition(
            id = "boss_b4", name = "Drum-Root", title = "Compost Barrel Titan",
            arenaId = 0, rarity = BossRarity.EPIC,
            spriteRes = R.drawable.sprite_boss_barrel,
            bodyColor = Color(0xFF8B5CF6), accentColor = Color(0xFFE9D5FF),
            preferredWaste = WasteType.ORGANIC_RED,
            abilityType = BossAbilityType.ECO_BOMB,
            abilityName = "Bio-Bloom Detonation",
            abilityDescription = "Detonates a 3x3 Eco-Bomb in the center of the board for massive Boss damage.",
            baseAbilityDamage = 360, chargeRequired = 17, bossBattleHp = 1750,
            lore = "An ancient sea-moss drum beneath Sidi Bou Said that turns organic waste into coral bloom."
        ),
        BossDefinition(
            id = "boss_b5", name = "Smog-Colossus", title = "Abyssal Refinery Sovereign",
            arenaId = 0, rarity = BossRarity.LEGENDARY,
            spriteRes = R.drawable.sprite_boss_factory,
            bodyColor = Color(0xFF06B6D4), accentColor = Color(0xFFFACC15),
            preferredWaste = WasteType.PLASTIC_BLUE,
            abilityType = BossAbilityType.MEGA_STRIKE,
            abilityName = "Azure Vortex Strike",
            abilityDescription = "Unleashes a devastating ocean-purifying surge plus cross-board tile crush.",
            baseAbilityDamage = 440, chargeRequired = 18, bossBattleHp = 1900,
            lore = "The legendary sovereign of Kelibia's deep waters, reborn as Tunisia's cleanest tide."
        ),

        // Arena 1: Cultural Monuments (5 Urban/Historical Bosses)
        BossDefinition(
            id = "boss_m1", name = "Cardboard-Drum", title = "Boxed Legionnaire",
            arenaId = 1, rarity = BossRarity.COMMON,
            spriteRes = R.drawable.sprite_boss_barrel,
            bodyColor = Color(0xFFB45309), accentColor = Color(0xFFFDE68A),
            preferredWaste = WasteType.CARDBOARD_TAN,
            abilityType = BossAbilityType.ROW_SWEEP,
            abilityName = "Phalanx Crush",
            abilityDescription = "Flattens an entire row of tiles and deals steady armor-piercing damage.",
            baseAbilityDamage = 260, chargeRequired = 14, bossBattleHp = 1750,
            lore = "Marches through Carthage's ruins folding stray packaging into neat recycling stacks."
        ),
        BossDefinition(
            id = "boss_m2", name = "Pillar-Golem", title = "Carthage Ruin Guardian",
            arenaId = 1, rarity = BossRarity.RARE,
            spriteRes = R.drawable.sprite_boss_golem,
            bodyColor = Color(0xFFDC2626), accentColor = Color(0xFFFCA5A5),
            preferredWaste = WasteType.ORGANIC_RED,
            abilityType = BossAbilityType.ECO_BOMB,
            abilityName = "Colosseum Eco-Slam",
            abilityDescription = "Smashes a 3x3 crater of tiles and hurls glowing energy into the Boss.",
            baseAbilityDamage = 330, chargeRequired = 16, bossBattleHp = 1850,
            lore = "Once clouded El Jem's grand arches, now fights for spotless Roman stone."
        ),
        BossDefinition(
            id = "boss_m3", name = "Furnace-Fiend", title = "Medina Soot Sweeper",
            arenaId = 1, rarity = BossRarity.RARE,
            spriteRes = R.drawable.sprite_boss_furnace,
            bodyColor = Color(0xFF059669), accentColor = Color(0xFF6EE7B7),
            preferredWaste = WasteType.GLASS_GREEN,
            abilityType = BossAbilityType.COLOR_PURGE,
            abilityName = "Green Glass Swoop",
            abilityDescription = "Instantly purifies every Green Glass Bottle on the board into projectiles.",
            baseAbilityDamage = 320, chargeRequired = 16, bossBattleHp = 1880,
            lore = "Guards Dougga's Capitol temple sorting recyclable glass from miles away."
        ),
        BossDefinition(
            id = "boss_m4", name = "Medina-Muck-Beast", title = "Alleyway Litter King",
            arenaId = 1, rarity = BossRarity.EPIC,
            spriteRes = R.drawable.sprite_boss_trashbeast,
            bodyColor = Color(0xFF7C3AED), accentColor = Color(0xFFF5D0FE),
            preferredWaste = WasteType.PLASTIC_BLUE,
            abilityType = BossAbilityType.COL_SWEEP,
            abilityName = "Kairouan Lantern Gale",
            abilityDescription = "Sweeps two adjacent columns clean with mystical amethyst wind.",
            baseAbilityDamage = 400, chargeRequired = 17, bossBattleHp = 2000,
            lore = "Swirls through Kairouan Medina turning plastic clutter into sparkling lantern light."
        ),
        BossDefinition(
            id = "boss_m5", name = "Ribat-Factory-Titan", title = "Fortress Iron Sovereign",
            arenaId = 1, rarity = BossRarity.LEGENDARY,
            spriteRes = R.drawable.sprite_boss_factory,
            bodyColor = Color(0xFF0284C7), accentColor = Color(0xFFFACC15),
            preferredWaste = WasteType.ALUMINUM_YELLOW,
            abilityType = BossAbilityType.MEGA_STRIKE,
            abilityName = "Monastir Bastion Cannon",
            abilityDescription = "Fires an ancient recycled-alloy blast for massive direct Boss damage.",
            baseAbilityDamage = 490, chargeRequired = 18, bossBattleHp = 2150,
            lore = "Forged from salvaged coastal metal at the Ribat of Monastir."
        ),

        // Arena 2: Desert & Oases (5 Saharan Bosses)
        BossDefinition(
            id = "boss_d1", name = "Sirocco-Twister", title = "Sand-Bottle Cyclone",
            arenaId = 2, rarity = BossRarity.COMMON,
            spriteRes = R.drawable.sprite_boss_tornado,
            bodyColor = Color(0xFFD97706), accentColor = Color(0xFFFEF3C7),
            preferredWaste = WasteType.ALUMINUM_YELLOW,
            abilityType = BossAbilityType.ROW_SWEEP,
            abilityName = "Sirocco Sand Whip",
            abilityDescription = "Slices a full row across the grid at blistering desert speed.",
            baseAbilityDamage = 290, chargeRequired = 14, bossBattleHp = 2050,
            lore = "Coils around stray wrappers in Douz until every dune shines pure gold."
        ),
        BossDefinition(
            id = "boss_d2", name = "Oasis-Claw-Crab", title = "Tozeur Palm Scuttler",
            arenaId = 2, rarity = BossRarity.RARE,
            spriteRes = R.drawable.sprite_boss_crab,
            bodyColor = Color(0xFFEA580C), accentColor = Color(0xFFFED7AA),
            preferredWaste = WasteType.CARDBOARD_TAN,
            abilityType = BossAbilityType.COLOR_PURGE,
            abilityName = "Tozeur Palm Snap",
            abilityDescription = "Crushes every Tan Cardboard Box on the board into oasis compost.",
            baseAbilityDamage = 360, chargeRequired = 16, bossBattleHp = 2180,
            lore = "Protects Tozeur's lush date palms from blown packaging and dry litter."
        ),
        BossDefinition(
            id = "boss_d3", name = "Canyon-Furnace", title = "Chebika Steam Crusher",
            arenaId = 2, rarity = BossRarity.EPIC,
            spriteRes = R.drawable.sprite_boss_furnace,
            bodyColor = Color(0xFF9333EA), accentColor = Color(0xFFDDD6FE),
            preferredWaste = WasteType.GLASS_GREEN,
            abilityType = BossAbilityType.ECO_BOMB,
            abilityName = "Waterfall Eco-Blast",
            abilityDescription = "Hurls a pressurized oasis sphere clearing a 3x3 cluster with huge impact.",
            baseAbilityDamage = 430, chargeRequired = 16, bossBattleHp = 2280,
            lore = "Keeps Chebika's emerald waterfall pools crystal clear."
        ),
        BossDefinition(
            id = "boss_d4", name = "Matmata-Stone-Titan", title = "Dahar Ruin Colossus",
            arenaId = 2, rarity = BossRarity.EPIC,
            spriteRes = R.drawable.sprite_boss_golem,
            bodyColor = Color(0xFFC026D3), accentColor = Color(0xFFF5D0FE),
            preferredWaste = WasteType.ORGANIC_RED,
            abilityType = BossAbilityType.COL_SWEEP,
            abilityName = "Earth-Dome Quake",
            abilityDescription = "Clears twin vertical columns and empowers organic tile combos.",
            baseAbilityDamage = 450, chargeRequired = 17, bossBattleHp = 2350,
            lore = "Sculpted from Matmata's terracotta hills to turn organic waste into fertile soil."
        ),
        BossDefinition(
            id = "boss_d5", name = "Djerid-Vortex-Lord", title = "Chott El Djerid Legend",
            arenaId = 2, rarity = BossRarity.LEGENDARY,
            spriteRes = R.drawable.sprite_boss_tornado,
            bodyColor = Color(0xFF0891B2), accentColor = Color(0xFFFDE047),
            preferredWaste = WasteType.PLASTIC_BLUE,
            abilityType = BossAbilityType.MEGA_STRIKE,
            abilityName = "Salt-Flat Solar Flare",
            abilityDescription = "Blinds pollution with a radiant desert nova dealing supreme Boss damage!",
            baseAbilityDamage = 550, chargeRequired = 18, bossBattleHp = 2500,
            lore = "Rises over the mirror-like Chott El Djerid salt flats, purifying Tunisia from coast to Sahara."
        )
    )

    val quests: List<QuestDefinition> = listOf(
        QuestDefinition(
            id = "q_daily_blue_100",
            title = "Plastic Tide Breaker",
            description = "Match 100 blue tiles (Plastic Bottles) in a single match",
            category = QuestCategory.DAILY,
            metric = QuestMetric.SINGLE_MATCH_BLUE_TILES,
            target = 100,
            coinReward = 180,
            cardRewardCount = 2,
            rewardRarity = BossRarity.RARE
        ),
        QuestDefinition(
            id = "q_daily_waves_6",
            title = "Wave Sweeper",
            description = "Clear 6 mini-trash waves across 1v1 matches",
            category = QuestCategory.DAILY,
            metric = QuestMetric.CLEAR_WAVES,
            target = 6,
            coinReward = 120,
            cardRewardCount = 1,
            rewardRarity = BossRarity.COMMON
        ),
        QuestDefinition(
            id = "q_daily_skills_5",
            title = "Eco-Deck Tactician",
            description = "Activate 5 charged Boss abilities during battles",
            category = QuestCategory.DAILY,
            metric = QuestMetric.USE_BOSS_ABILITIES,
            target = 5,
            coinReward = 150,
            cardRewardCount = 1,
            rewardRarity = BossRarity.RARE
        ),
        QuestDefinition(
            id = "q_daily_tiles_250",
            title = "Rapid Sorter",
            description = "Match 250 total recyclable tiles across matches",
            category = QuestCategory.DAILY,
            metric = QuestMetric.MATCH_TOTAL_TILES,
            target = 250,
            coinReward = 140,
            cardRewardCount = 2,
            rewardRarity = BossRarity.COMMON
        ),
        QuestDefinition(
            id = "q_challenge_epic_3",
            title = "Epic Purifier",
            description = "Win 3 matches using only Epic Bosses in your Eco-Deck",
            category = QuestCategory.CHALLENGE,
            metric = QuestMetric.WIN_WITH_ONLY_EPIC_BOSSES,
            target = 3,
            coinReward = 450,
            cardRewardCount = 3,
            rewardRarity = BossRarity.EPIC
        ),
        QuestDefinition(
            id = "q_challenge_beaches_2",
            title = "Mediterranean Guardian",
            description = "Purify 2 Beaches locations to 100% clean-up",
            category = QuestCategory.CHALLENGE,
            metric = QuestMetric.PURIFY_BEACHES,
            target = 2,
            coinReward = 260,
            cardRewardCount = 2,
            rewardRarity = BossRarity.RARE
        ),
        QuestDefinition(
            id = "q_challenge_combo_4",
            title = "Cascade Legend",
            description = "Trigger a 4x or higher Match-3 chain combo in battle",
            category = QuestCategory.CHALLENGE,
            metric = QuestMetric.HIGH_COMBO_CHAIN,
            target = 1,
            coinReward = 220,
            cardRewardCount = 2,
            rewardRarity = BossRarity.EPIC
        )
    )

    fun arenaForTrophies(trophies: Int): ArenaDefinition {
        return arenas.lastOrNull { trophies >= it.minTrophies } ?: arenas.first()
    }

    fun arenaById(arenaId: Int): ArenaDefinition {
        return arenas.firstOrNull { it.id == arenaId } ?: arenas.first()
    }

    fun placeById(placeId: String): TunisianPlace {
        return allPlaces.firstOrNull { it.id == placeId } ?: allPlaces.first()
    }

    fun bossById(bossId: String): BossDefinition {
        return bosses.firstOrNull { it.id == bossId } ?: bosses.first()
    }

    // Cumulative Boss Pool: higher Arenas include all earlier Bosses + new ones
    fun cumulativeBossPoolForArena(arenaId: Int): List<BossDefinition> {
        return bosses.filter { it.arenaId <= arenaId }
    }

    fun copiesNeededForNextLevel(currentLevel: Int, rarity: BossRarity): Int {
        val base = when (rarity) {
            BossRarity.COMMON -> 4
            BossRarity.RARE -> 3
            BossRarity.EPIC -> 2
            BossRarity.LEGENDARY -> 2
        }
        return base * currentLevel.coerceAtLeast(1)
    }

    fun coinsNeededForNextLevel(currentLevel: Int, rarity: BossRarity): Int {
        val base = when (rarity) {
            BossRarity.COMMON -> 80
            BossRarity.RARE -> 140
            BossRarity.EPIC -> 220
            BossRarity.LEGENDARY -> 350
        }
        return base * currentLevel.coerceAtLeast(1)
    }
}
