package com.example.data

import java.io.File
import java.io.InputStream

/**
 * Singleton repository holding loaded and validated game definitions.
 */
object GameCatalog {
    private val parser = DataParser()

    var creatures: List<CreatureDefinition> = emptyList()
        private set
    var spells: List<SpellDefinition> = emptyList()
        private set
    var terrain: Map<TerrainType, TerrainDefinition> = emptyMap()
        private set
    var buildings: List<BuildingDefinition> = emptyList()
        private set
    var atlas: TextureAtlasDefinition? = null
        private set

    private var isInitialized = false

    /**
     * Initializes the catalog with provided JSON strings, or loads standard defaults.
     */
    @Synchronized
    fun initialize(
        creaturesJson: String? = null,
        spellsJson: String? = null,
        terrainJson: String? = null,
        buildingsJson: String? = null,
        atlasJson: String? = null
    ) {
        val cJson = creaturesJson ?: DEFAULT_CREATURES_JSON
        val sJson = spellsJson ?: DEFAULT_SPELLS_JSON
        val tJson = terrainJson ?: DEFAULT_TERRAIN_JSON
        val bJson = buildingsJson ?: DEFAULT_BUILDINGS_JSON
        val aJson = atlasJson ?: DEFAULT_ATLAS_JSON

        creatures = parser.parseCreatures(cJson)
        spells = parser.parseSpells(sJson)
        terrain = parser.parseTerrain(tJson)
        buildings = parser.parseBuildings(bJson, creatures)
        atlas = parser.parseAtlas(aJson)
        isInitialized = true
    }

    fun ensureInitialized() {
        if (!isInitialized) {
            initialize()
        }
    }

    fun getCreature(id: String): CreatureDefinition {
        ensureInitialized()
        return creatures.firstOrNull { it.id.equals(id, ignoreCase = true) }
            ?: throw IllegalArgumentException("Creature '$id' not found in catalog")
    }

    fun getSpell(id: String): SpellDefinition {
        ensureInitialized()
        return spells.firstOrNull { it.id.equals(id, ignoreCase = true) }
            ?: throw IllegalArgumentException("Spell '$id' not found in catalog")
    }

    fun getBuilding(id: String): BuildingDefinition {
        ensureInitialized()
        return buildings.firstOrNull { it.id.equals(id, ignoreCase = true) }
            ?: throw IllegalArgumentException("Building '$id' not found in catalog")
    }

    const val DEFAULT_CREATURES_JSON = """{
  "version": "1.0.0",
  "creatures": [
    {
      "id": "pikeman",
      "name": "Pikeman",
      "faction": "CASTLE",
      "tier": 1,
      "attack": 4,
      "defense": 5,
      "minDamage": 1,
      "maxDamage": 3,
      "health": 10,
      "speed": 4,
      "growth": 14,
      "cost": { "gold": 60 },
      "shots": 0,
      "isRanged": false,
      "isFlying": false,
      "isWide": false,
      "retaliations": 1,
      "abilities": ["SPEAR_WALL"]
    },
    {
      "id": "halberdier",
      "name": "Halberdier",
      "faction": "CASTLE",
      "tier": 1,
      "attack": 6,
      "defense": 5,
      "minDamage": 2,
      "maxDamage": 3,
      "health": 10,
      "speed": 5,
      "growth": 14,
      "cost": { "gold": 75 },
      "shots": 0,
      "isRanged": false,
      "isFlying": false,
      "isWide": false,
      "retaliations": 1,
      "abilities": ["SPEAR_WALL"]
    },
    {
      "id": "archer",
      "name": "Archer",
      "faction": "CASTLE",
      "tier": 2,
      "attack": 6,
      "defense": 3,
      "minDamage": 2,
      "maxDamage": 3,
      "health": 10,
      "speed": 4,
      "growth": 9,
      "cost": { "gold": 100 },
      "shots": 12,
      "isRanged": true,
      "isFlying": false,
      "isWide": false,
      "retaliations": 1,
      "abilities": ["RANGED"]
    },
    {
      "id": "marksman",
      "name": "Marksman",
      "faction": "CASTLE",
      "tier": 2,
      "attack": 6,
      "defense": 3,
      "minDamage": 2,
      "maxDamage": 3,
      "health": 10,
      "speed": 6,
      "growth": 9,
      "cost": { "gold": 150 },
      "shots": 24,
      "isRanged": true,
      "isFlying": false,
      "isWide": false,
      "retaliations": 1,
      "abilities": ["RANGED", "DOUBLE_SHOT"]
    },
    {
      "id": "griffin",
      "name": "Griffin",
      "faction": "CASTLE",
      "tier": 3,
      "attack": 8,
      "defense": 8,
      "minDamage": 3,
      "maxDamage": 6,
      "health": 25,
      "speed": 6,
      "growth": 7,
      "cost": { "gold": 200 },
      "shots": 0,
      "isRanged": false,
      "isFlying": true,
      "isWide": true,
      "retaliations": 2,
      "abilities": ["FLYING", "UNLIMITED_RETALIATION_2"]
    },
    {
      "id": "royal_griffin",
      "name": "Royal Griffin",
      "faction": "CASTLE",
      "tier": 3,
      "attack": 9,
      "defense": 9,
      "minDamage": 3,
      "maxDamage": 6,
      "health": 25,
      "speed": 9,
      "growth": 7,
      "cost": { "gold": 240 },
      "shots": 0,
      "isRanged": false,
      "isFlying": true,
      "isWide": true,
      "retaliations": 999,
      "abilities": ["FLYING", "UNLIMITED_RETALIATION"]
    },
    {
      "id": "swordsman",
      "name": "Swordsman",
      "faction": "CASTLE",
      "tier": 4,
      "attack": 10,
      "defense": 12,
      "minDamage": 6,
      "maxDamage": 9,
      "health": 35,
      "speed": 5,
      "growth": 4,
      "cost": { "gold": 300 },
      "shots": 0,
      "isRanged": false,
      "isFlying": false,
      "isWide": false,
      "retaliations": 1,
      "abilities": []
    },
    {
      "id": "crusader",
      "name": "Crusader",
      "faction": "CASTLE",
      "tier": 4,
      "attack": 12,
      "defense": 12,
      "minDamage": 7,
      "maxDamage": 10,
      "health": 35,
      "speed": 6,
      "growth": 4,
      "cost": { "gold": 400 },
      "shots": 0,
      "isRanged": false,
      "isFlying": false,
      "isWide": false,
      "retaliations": 1,
      "abilities": ["DOUBLE_STRIKE"]
    },
    {
      "id": "monk",
      "name": "Monk",
      "faction": "CASTLE",
      "tier": 5,
      "attack": 12,
      "defense": 7,
      "minDamage": 10,
      "maxDamage": 12,
      "health": 30,
      "speed": 5,
      "growth": 3,
      "cost": { "gold": 400 },
      "shots": 12,
      "isRanged": true,
      "isFlying": false,
      "isWide": false,
      "retaliations": 1,
      "abilities": ["RANGED"]
    },
    {
      "id": "zealot",
      "name": "Zealot",
      "faction": "CASTLE",
      "tier": 5,
      "attack": 12,
      "defense": 10,
      "minDamage": 10,
      "maxDamage": 12,
      "health": 30,
      "speed": 7,
      "growth": 3,
      "cost": { "gold": 450 },
      "shots": 24,
      "isRanged": true,
      "isFlying": false,
      "isWide": false,
      "retaliations": 1,
      "abilities": ["RANGED", "NO_MELEE_PENALTY"],
      "activeAbilities": [
        {
          "id": "zealot_dispel",
          "name": "Purifying Dispel",
          "effectType": "DISPEL_MAGIC",
          "description": "Cleanses all negative debuffs and curses from an allied stack.",
          "cooldownRounds": 2,
          "maxCharges": -1,
          "targetType": "ALLY_STACK",
          "power": 0,
          "range": 0
        }
      ]
    },
    {
      "id": "cavalier",
      "name": "Cavalier",
      "faction": "CASTLE",
      "tier": 6,
      "attack": 15,
      "defense": 15,
      "minDamage": 15,
      "maxDamage": 25,
      "health": 100,
      "speed": 7,
      "growth": 2,
      "cost": { "gold": 1000 },
      "shots": 0,
      "isRanged": false,
      "isFlying": false,
      "isWide": true,
      "retaliations": 1,
      "abilities": ["JOUSTING"]
    },
    {
      "id": "champion",
      "name": "Champion",
      "faction": "CASTLE",
      "tier": 6,
      "attack": 16,
      "defense": 16,
      "minDamage": 20,
      "maxDamage": 25,
      "health": 100,
      "speed": 9,
      "growth": 2,
      "cost": { "gold": 1200 },
      "shots": 0,
      "isRanged": false,
      "isFlying": false,
      "isWide": true,
      "retaliations": 1,
      "abilities": ["CHAMPION_JOUSTING"]
    },
    {
      "id": "angel",
      "name": "Angel",
      "faction": "CASTLE",
      "tier": 7,
      "attack": 20,
      "defense": 20,
      "minDamage": 50,
      "maxDamage": 50,
      "health": 200,
      "speed": 12,
      "growth": 1,
      "cost": { "gold": 3000, "gems": 1 },
      "shots": 0,
      "isRanged": false,
      "isFlying": true,
      "isWide": false,
      "retaliations": 1,
      "abilities": ["FLYING", "MORALE_BOOST"]
    },
    {
      "id": "archangel",
      "name": "Archangel",
      "faction": "CASTLE",
      "tier": 7,
      "attack": 30,
      "defense": 30,
      "minDamage": 50,
      "maxDamage": 50,
      "health": 250,
      "speed": 18,
      "growth": 1,
      "cost": { "gold": 5000, "gems": 3 },
      "shots": 0,
      "isRanged": false,
      "isFlying": true,
      "isWide": false,
      "retaliations": 1,
      "abilities": ["FLYING", "MORALE_BOOST_2", "RESURRECTION"],
      "activeAbilities": [
        {
          "id": "archangel_resurrection",
          "name": "Resurrection",
          "effectType": "RESURRECTION",
          "description": "Revives fallen allied creatures in a target stack (1 charge per battle).",
          "cooldownRounds": 0,
          "maxCharges": 1,
          "targetType": "ALLY_STACK",
          "power": 100,
          "range": 0
        }
      ]
    },
    {
      "id": "imp",
      "name": "Imp",
      "faction": "INFERNO",
      "tier": 1,
      "attack": 2,
      "defense": 3,
      "minDamage": 1,
      "maxDamage": 2,
      "health": 4,
      "speed": 5,
      "growth": 15,
      "cost": { "gold": 50 },
      "shots": 0,
      "isRanged": false,
      "isFlying": false,
      "isWide": false,
      "retaliations": 1,
      "abilities": []
    },
    {
      "id": "familiar",
      "name": "Familiar",
      "faction": "INFERNO",
      "tier": 1,
      "attack": 4,
      "defense": 4,
      "minDamage": 1,
      "maxDamage": 2,
      "health": 4,
      "speed": 7,
      "growth": 15,
      "cost": { "gold": 60 },
      "shots": 0,
      "isRanged": false,
      "isFlying": false,
      "isWide": false,
      "retaliations": 1,
      "abilities": ["MANA_CHANNELING"]
    },
    {
      "id": "cerberus",
      "name": "Cerberus",
      "faction": "INFERNO",
      "tier": 3,
      "attack": 10,
      "defense": 8,
      "minDamage": 2,
      "maxDamage": 7,
      "health": 25,
      "speed": 8,
      "growth": 5,
      "cost": { "gold": 250 },
      "shots": 0,
      "isRanged": false,
      "isFlying": false,
      "isWide": true,
      "retaliations": 1,
      "abilities": ["NO_ENEMY_RETALIATION", "THREE_HEADED_ATTACK"]
    },
    {
      "id": "arch_devil",
      "name": "Arch Devil",
      "faction": "INFERNO",
      "tier": 7,
      "attack": 26,
      "defense": 28,
      "minDamage": 30,
      "maxDamage": 40,
      "health": 200,
      "speed": 17,
      "growth": 1,
      "cost": { "gold": 4500, "mercury": 4 },
      "shots": 0,
      "isRanged": false,
      "isFlying": true,
      "isWide": false,
      "retaliations": 1,
      "abilities": ["TELEPORT_MOVE", "NO_ENEMY_RETALIATION", "ENEMY_LUCK_PENALTY"],
      "activeAbilities": [
        {
          "id": "arch_devil_teleport",
          "name": "Abyssal Step",
          "effectType": "TELEPORT",
          "description": "Teleports instantly to any unoccupied hex on the battlefield.",
          "cooldownRounds": 2,
          "maxCharges": -1,
          "targetType": "EMPTY_HEX",
          "power": 0,
          "range": 0
        }
      ]
    },
    {
      "id": "pit_fiend",
      "name": "Pit Fiend",
      "faction": "INFERNO",
      "tier": 5,
      "attack": 13,
      "defense": 13,
      "minDamage": 13,
      "maxDamage": 17,
      "health": 45,
      "speed": 6,
      "growth": 3,
      "cost": { "gold": 500 },
      "shots": 0,
      "isRanged": false,
      "isFlying": false,
      "isWide": false,
      "retaliations": 1,
      "abilities": [],
      "activeAbilities": [
        {
          "id": "pit_fiend_demon_summon",
          "name": "Raise Demon",
          "effectType": "SUMMON_DEMONS",
          "description": "Raises demonic reinforcements from fallen friendly units.",
          "cooldownRounds": 2,
          "maxCharges": 2,
          "targetType": "ALLY_STACK",
          "power": 50,
          "range": 0
        }
      ]
    },
    {
      "id": "efreeti",
      "name": "Efreeti",
      "faction": "INFERNO",
      "tier": 6,
      "attack": 16,
      "defense": 12,
      "minDamage": 16,
      "maxDamage": 24,
      "health": 90,
      "speed": 9,
      "growth": 2,
      "cost": { "gold": 900 },
      "shots": 0,
      "isRanged": false,
      "isFlying": true,
      "isWide": true,
      "retaliations": 1,
      "abilities": ["FLYING"],
      "activeAbilities": [
        {
          "id": "efreeti_fire_shield",
          "name": "Fire Shield",
          "effectType": "FIRE_SHIELD",
          "description": "Surrounds stack in a blazing barrier returning 20% melee damage to attackers.",
          "cooldownRounds": 3,
          "maxCharges": -1,
          "targetType": "SELF",
          "power": 20,
          "range": 0
        }
      ]
    }
  ]
}"""

    const val DEFAULT_SPELLS_JSON = """{
  "version": "1.0.0",
  "spells": [
    {
      "id": "magic_arrow",
      "name": "Magic Arrow",
      "school": "ALL",
      "level": 1,
      "manaCost": 5,
      "baseDamage": 10,
      "powerMultiplier": 10,
      "type": "DAMAGE_TARGET",
      "description": "Deals direct magical damage to a single enemy stack."
    },
    {
      "id": "lightning_bolt",
      "name": "Lightning Bolt",
      "school": "AIR",
      "level": 2,
      "manaCost": 8,
      "baseDamage": 20,
      "powerMultiplier": 25,
      "type": "DAMAGE_TARGET",
      "description": "Strikes target with lightning from above."
    },
    {
      "id": "fireball",
      "name": "Fireball",
      "school": "FIRE",
      "level": 3,
      "manaCost": 15,
      "baseDamage": 30,
      "powerMultiplier": 15,
      "radius": 1,
      "type": "DAMAGE_AREA",
      "description": "Engulfs target hex and adjacent hexes in flames."
    },
    {
      "id": "haste",
      "name": "Haste",
      "school": "AIR",
      "level": 1,
      "manaCost": 6,
      "statModifier": 3,
      "targetStat": "SPEED",
      "type": "BUFF_TARGET",
      "description": "Increases unit speed and combat movement range."
    },
    {
      "id": "slow",
      "name": "Slow",
      "school": "EARTH",
      "level": 1,
      "manaCost": 6,
      "speedFactor": 0.5,
      "targetStat": "SPEED",
      "type": "DEBUFF_TARGET",
      "description": "Reduces unit speed by 50% rounded down."
    },
    {
      "id": "cure",
      "name": "Cure",
      "school": "WATER",
      "level": 1,
      "manaCost": 6,
      "baseHeal": 10,
      "powerMultiplier": 5,
      "type": "HEAL_TARGET",
      "description": "Removes negative spells and heals wounded creatures."
    },
    {
      "id": "resurrection",
      "name": "Resurrection",
      "school": "EARTH",
      "level": 4,
      "manaCost": 20,
      "baseHealth": 50,
      "powerMultiplier": 50,
      "type": "RESURRECT_TARGET",
      "description": "Restores dead creatures to the target friendly stack."
    },
    {
      "id": "meteor_shower",
      "name": "Meteor Shower",
      "school": "EARTH",
      "level": 4,
      "manaCost": 16,
      "baseDamage": 25,
      "powerMultiplier": 25,
      "radius": 2,
      "type": "DAMAGE_AREA",
      "description": "Bombards a wide 2-hex radius area with falling meteors."
    },
    {
      "id": "chain_lightning",
      "name": "Chain Lightning",
      "school": "AIR",
      "level": 4,
      "manaCost": 24,
      "baseDamage": 40,
      "powerMultiplier": 25,
      "type": "DAMAGE_TARGET",
      "description": "Strikes target with high-voltage lightning cascading across combatants."
    }
  ]
}"""

    const val DEFAULT_TERRAIN_JSON = """{
  "version": "1.0.0",
  "terrains": [
    {
      "id": "GRASS",
      "name": "Grass",
      "baseMovementCost": 100,
      "nativeFaction": "CASTLE",
      "isPassable": true,
      "tacticalMovementPenalty": 0,
      "defenseBonus": 0
    },
    {
      "id": "DIRT",
      "name": "Dirt",
      "baseMovementCost": 100,
      "nativeFaction": "NONE",
      "isPassable": true,
      "tacticalMovementPenalty": 0,
      "defenseBonus": 0
    },
    {
      "id": "ROUGH",
      "name": "Rough",
      "baseMovementCost": 125,
      "nativeFaction": "STRONGHOLD",
      "isPassable": true,
      "tacticalMovementPenalty": 1,
      "defenseBonus": 1
    },
    {
      "id": "DESERT",
      "name": "Desert",
      "baseMovementCost": 150,
      "nativeFaction": "NONE",
      "isPassable": true,
      "tacticalMovementPenalty": 1,
      "defenseBonus": 1
    },
    {
      "id": "SNOW",
      "name": "Snow",
      "baseMovementCost": 150,
      "nativeFaction": "TOWER",
      "isPassable": true,
      "tacticalMovementPenalty": 1,
      "defenseBonus": 1
    },
    {
      "id": "SWAMP",
      "name": "Swamp",
      "baseMovementCost": 175,
      "nativeFaction": "FORTRESS",
      "isPassable": true,
      "tacticalMovementPenalty": 2,
      "defenseBonus": 2
    },
    {
      "id": "LAVA",
      "name": "Lava",
      "baseMovementCost": 100,
      "nativeFaction": "INFERNO",
      "isPassable": true,
      "tacticalMovementPenalty": 0,
      "defenseBonus": 1
    },
    {
      "id": "SUBTERRANEAN",
      "name": "Subterranean",
      "baseMovementCost": 125,
      "nativeFaction": "DUNGEON",
      "isPassable": true,
      "tacticalMovementPenalty": 1,
      "defenseBonus": 1
    },
    {
      "id": "WATER",
      "name": "Water",
      "baseMovementCost": 100,
      "nativeFaction": "COVE",
      "isPassable": false,
      "tacticalMovementPenalty": 0,
      "defenseBonus": 0
    },
    {
      "id": "ROCK",
      "name": "Rock Obstacle",
      "baseMovementCost": 999999,
      "nativeFaction": "NONE",
      "isPassable": false,
      "tacticalMovementPenalty": 0,
      "defenseBonus": 0
    }
  ],
  "roadModifiers": {
    "NONE": 1.0,
    "DIRT_ROAD": 0.75,
    "GRAVEL_ROAD": 0.65,
    "COBBLESTONE_ROAD": 0.50
  }
}"""

    const val DEFAULT_BUILDINGS_JSON = """{
  "version": "1.0.0",
  "buildings": [
    {
      "id": "town_hall",
      "name": "Town Hall",
      "faction": "CASTLE",
      "cost": { "gold": 0 },
      "prerequisites": [],
      "dailyIncome": 500,
      "category": "HALL"
    },
    {
      "id": "city_hall",
      "name": "City Hall",
      "faction": "CASTLE",
      "cost": { "gold": 2500 },
      "prerequisites": ["town_hall", "tavern", "blacksmith", "mage_guild_1"],
      "dailyIncome": 1000,
      "category": "HALL"
    },
    {
      "id": "capitol",
      "name": "Capitol",
      "faction": "CASTLE",
      "cost": { "gold": 10000 },
      "prerequisites": ["city_hall", "castle"],
      "dailyIncome": 4000,
      "category": "HALL"
    },
    {
      "id": "fort",
      "name": "Fort",
      "faction": "CASTLE",
      "cost": { "gold": 5000, "wood": 20, "ore": 20 },
      "prerequisites": [],
      "category": "FORTIFICATION"
    },
    {
      "id": "citadel",
      "name": "Citadel",
      "faction": "CASTLE",
      "cost": { "gold": 2500, "ore": 5 },
      "prerequisites": ["fort"],
      "category": "FORTIFICATION"
    },
    {
      "id": "castle",
      "name": "Castle",
      "faction": "CASTLE",
      "cost": { "gold": 5000, "wood": 10, "ore": 10 },
      "prerequisites": ["citadel"],
      "category": "FORTIFICATION"
    },
    {
      "id": "tavern",
      "name": "Tavern",
      "faction": "CASTLE",
      "cost": { "gold": 500, "wood": 5 },
      "prerequisites": [],
      "moraleBonus": 1,
      "category": "UTILITY"
    },
    {
      "id": "blacksmith",
      "name": "Blacksmith",
      "faction": "CASTLE",
      "cost": { "gold": 1000, "wood": 5 },
      "prerequisites": [],
      "category": "UTILITY"
    },
    {
      "id": "mage_guild_1",
      "name": "Mage Guild Level 1",
      "faction": "CASTLE",
      "cost": { "gold": 2000, "wood": 5, "ore": 5 },
      "prerequisites": [],
      "category": "MAGE_GUILD"
    },
    {
      "id": "guardhouse",
      "name": "Guardhouse",
      "faction": "CASTLE",
      "cost": { "gold": 500, "wood": 10 },
      "prerequisites": ["fort"],
      "creatureId": "pikeman",
      "dwellingTier": 1,
      "growth": 14,
      "category": "DWELLING"
    },
    {
      "id": "archers_tower",
      "name": "Archers Tower",
      "faction": "CASTLE",
      "cost": { "gold": 1000, "wood": 5 },
      "prerequisites": ["guardhouse"],
      "creatureId": "archer",
      "dwellingTier": 2,
      "growth": 9,
      "category": "DWELLING"
    },
    {
      "id": "griffin_tower",
      "name": "Griffin Tower",
      "faction": "CASTLE",
      "cost": { "gold": 1000, "ore": 5 },
      "prerequisites": ["guardhouse"],
      "creatureId": "griffin",
      "dwellingTier": 3,
      "growth": 7,
      "category": "DWELLING"
    },
    {
      "id": "barracks",
      "name": "Barracks",
      "faction": "CASTLE",
      "cost": { "gold": 2000, "wood": 5, "ore": 5 },
      "prerequisites": ["guardhouse", "blacksmith"],
      "creatureId": "swordsman",
      "dwellingTier": 4,
      "growth": 4,
      "category": "DWELLING"
    },
    {
      "id": "monastery",
      "name": "Monastery",
      "faction": "CASTLE",
      "cost": { "gold": 3000, "wood": 5, "ore": 5 },
      "prerequisites": ["archers_tower", "mage_guild_1"],
      "creatureId": "monk",
      "dwellingTier": 5,
      "growth": 3,
      "category": "DWELLING"
    },
    {
      "id": "training_grounds",
      "name": "Training Grounds",
      "faction": "CASTLE",
      "cost": { "gold": 5000, "wood": 20 },
      "prerequisites": ["barracks", "griffin_tower", "stables"],
      "creatureId": "cavalier",
      "dwellingTier": 6,
      "growth": 2,
      "category": "DWELLING"
    },
    {
      "id": "stables",
      "name": "Stables",
      "faction": "CASTLE",
      "cost": { "gold": 2000, "wood": 10 },
      "prerequisites": ["barracks"],
      "category": "UTILITY"
    },
    {
      "id": "portal_of_glory",
      "name": "Portal of Glory",
      "faction": "CASTLE",
      "cost": { "gold": 20000, "wood": 10, "ore": 10, "gems": 10, "mercury": 10, "sulfur": 10, "crystal": 10 },
      "prerequisites": ["monastery", "training_grounds"],
      "creatureId": "angel",
      "dwellingTier": 7,
      "growth": 1,
      "category": "DWELLING"
    },
    {
      "id": "marketplace",
      "name": "Marketplace",
      "faction": "CASTLE",
      "cost": { "gold": 500, "wood": 5 },
      "prerequisites": [],
      "category": "UTILITY"
    },
    {
      "id": "mage_guild_2",
      "name": "Mage Guild Level 2",
      "faction": "CASTLE",
      "cost": { "gold": 1000, "wood": 5, "ore": 5, "mercury": 4, "sulfur": 4, "crystal": 4, "gems": 4 },
      "prerequisites": ["mage_guild_1"],
      "category": "MAGE_GUILD"
    }
  ]
}"""

    const val DEFAULT_ATLAS_JSON = """{
  "version": "1.0.0",
  "textureAtlas": {
    "texturePath": "sprites/creatures_atlas.png",
    "width": 1024,
    "height": 1024,
    "sprites": [
      {
        "id": "pikeman_idle",
        "creatureId": "pikeman",
        "animation": "IDLE",
        "frameRateFps": 8,
        "isLooping": true,
        "frames": [
          { "x": 0, "y": 0, "w": 64, "h": 64, "pivotX": 32, "pivotY": 56, "durationMs": 125 },
          { "x": 64, "y": 0, "w": 64, "h": 64, "pivotX": 32, "pivotY": 56, "durationMs": 125 },
          { "x": 128, "y": 0, "w": 64, "h": 64, "pivotX": 32, "pivotY": 56, "durationMs": 125 },
          { "x": 192, "y": 0, "w": 64, "h": 64, "pivotX": 32, "pivotY": 56, "durationMs": 125 }
        ]
      },
      {
        "id": "pikeman_move",
        "creatureId": "pikeman",
        "animation": "MOVE",
        "frameRateFps": 10,
        "isLooping": true,
        "frames": [
          { "x": 0, "y": 64, "w": 64, "h": 64, "pivotX": 32, "pivotY": 56, "durationMs": 100 },
          { "x": 64, "y": 64, "w": 64, "h": 64, "pivotX": 32, "pivotY": 56, "durationMs": 100 },
          { "x": 128, "y": 64, "w": 64, "h": 64, "pivotX": 32, "pivotY": 56, "durationMs": 100 },
          { "x": 192, "y": 64, "w": 64, "h": 64, "pivotX": 32, "pivotY": 56, "durationMs": 100 }
        ]
      },
      {
        "id": "pikeman_attack",
        "creatureId": "pikeman",
        "animation": "ATTACK",
        "frameRateFps": 12,
        "isLooping": false,
        "impactFrameIndex": 2,
        "frames": [
          { "x": 0, "y": 128, "w": 64, "h": 64, "pivotX": 32, "pivotY": 56, "durationMs": 83 },
          { "x": 64, "y": 128, "w": 64, "h": 64, "pivotX": 32, "pivotY": 56, "durationMs": 83 },
          { "x": 128, "y": 128, "w": 64, "h": 64, "pivotX": 32, "pivotY": 56, "durationMs": 83 },
          { "x": 192, "y": 128, "w": 64, "h": 64, "pivotX": 32, "pivotY": 56, "durationMs": 83 }
        ]
      },
      {
        "id": "pikeman_die",
        "creatureId": "pikeman",
        "animation": "DIE",
        "frameRateFps": 8,
        "isLooping": false,
        "frames": [
          { "x": 0, "y": 192, "w": 64, "h": 64, "pivotX": 32, "pivotY": 56, "durationMs": 125 },
          { "x": 64, "y": 192, "w": 64, "h": 64, "pivotX": 32, "pivotY": 56, "durationMs": 125 },
          { "x": 128, "y": 192, "w": 64, "h": 64, "pivotX": 32, "pivotY": 56, "durationMs": 125 }
        ]
      },
      {
        "id": "archangel_idle",
        "creatureId": "archangel",
        "animation": "IDLE",
        "frameRateFps": 6,
        "isLooping": true,
        "frames": [
          { "x": 256, "y": 0, "w": 96, "h": 96, "pivotX": 48, "pivotY": 80, "durationMs": 166 },
          { "x": 352, "y": 0, "w": 96, "h": 96, "pivotX": 48, "pivotY": 80, "durationMs": 166 },
          { "x": 448, "y": 0, "w": 96, "h": 96, "pivotX": 48, "pivotY": 80, "durationMs": 166 }
        ]
      },
      {
        "id": "archangel_attack",
        "creatureId": "archangel",
        "animation": "ATTACK",
        "frameRateFps": 12,
        "isLooping": false,
        "impactFrameIndex": 2,
        "frames": [
          { "x": 256, "y": 96, "w": 96, "h": 96, "pivotX": 48, "pivotY": 80, "durationMs": 83 },
          { "x": 352, "y": 96, "w": 96, "h": 96, "pivotX": 48, "pivotY": 80, "durationMs": 83 },
          { "x": 448, "y": 96, "w": 96, "h": 96, "pivotX": 48, "pivotY": 80, "durationMs": 83 },
          { "x": 544, "y": 96, "w": 96, "h": 96, "pivotX": 48, "pivotY": 80, "durationMs": 83 }
        ]
      }
    ]
  }
}"""
}
