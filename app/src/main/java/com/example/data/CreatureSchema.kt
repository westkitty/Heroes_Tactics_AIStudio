package com.example.data

/**
 * Supported creature factions in the engine.
 */
enum class Faction {
    CASTLE,
    RAMPART,
    TOWER,
    INFERNO,
    NECROPOLIS,
    DUNGEON,
    STRONGHOLD,
    FORTRESS,
    CONFLUX,
    COVE,
    NEUTRAL,
    NONE
}

/**
 * Creature special ability tags.
 */
enum class CreatureAbility {
    RANGED,
    FLYING,
    DOUBLE_SHOT,
    DOUBLE_STRIKE,
    NO_MELEE_PENALTY,
    NO_ENEMY_RETALIATION,
    UNLIMITED_RETALIATION,
    UNLIMITED_RETALIATION_2,
    SPEAR_WALL,
    JOUSTING,
    CHAMPION_JOUSTING,
    MORALE_BOOST,
    MORALE_BOOST_2,
    RESURRECTION,
    MANA_CHANNELING,
    THREE_HEADED_ATTACK,
    TELEPORT_MOVE,
    ENEMY_LUCK_PENALTY
}

/**
 * Targeting mode for creature active abilities.
 */
enum class AbilityTargetType {
    SELF,
    ALLY_STACK,
    ENEMY_STACK,
    EMPTY_HEX,
    ANY_HEX
}

/**
 * Categorization of unique active creature ability effects.
 */
enum class ActiveAbilityEffectType {
    RESURRECTION,     // Revives fallen units in target ally stack
    HEAL,             // Restores health to target ally or self
    TELEPORT,         // Teleports caster to any unobstructed battlefield hex
    FIRE_SHIELD,      // Surrounds stack with retaliatory fire aura
    SUMMON_DEMONS,    // Raises fallen units as demonic reinforcements
    PRECISION_SHOT,   // Guaranteed critical hit armor-piercing shot
    DISPEL_MAGIC,     // Cleanses negative debuffs or positive enemy buffs
    BLOODLUST_FRENZY, // Temporarily surges attack rating
    DEATH_STRIKE      // Chance to deliver instant catastrophic damage
}

/**
 * Definition schema for unique active creature abilities with cooldown and charge limits.
 */
data class CreatureActiveAbility(
    val id: String,
    val name: String,
    val effectType: ActiveAbilityEffectType,
    val description: String,
    val cooldownRounds: Int = 0, // 0 = usable every round (subject to charges), >0 = cooldown rounds
    val maxCharges: Int = -1,    // -1 = infinite per battle, >= 1 = finite uses
    val targetType: AbilityTargetType = AbilityTargetType.ENEMY_STACK,
    val power: Int = 0,
    val range: Int = 0           // 0 = global / unlimited hex distance
) {
    init {
        require(id.isNotBlank()) { "Ability ID cannot be blank" }
        require(name.isNotBlank()) { "Ability name cannot be blank" }
        require(cooldownRounds >= 0) { "Cooldown rounds must be >= 0" }
    }
}

/**
 * Immutable resource cost map.
 */
data class ResourceCost(
    val gold: Int = 0,
    val wood: Int = 0,
    val ore: Int = 0,
    val mercury: Int = 0,
    val sulfur: Int = 0,
    val crystal: Int = 0,
    val gems: Int = 0
) {
    operator fun plus(other: ResourceCost): ResourceCost = ResourceCost(
        gold = gold + other.gold,
        wood = wood + other.wood,
        ore = ore + other.ore,
        mercury = mercury + other.mercury,
        sulfur = sulfur + other.sulfur,
        crystal = crystal + other.crystal,
        gems = gems + other.gems
    )

    operator fun times(multiplier: Int): ResourceCost = ResourceCost(
        gold = gold * multiplier,
        wood = wood * multiplier,
        ore = ore * multiplier,
        mercury = mercury * multiplier,
        sulfur = sulfur * multiplier,
        crystal = crystal * multiplier,
        gems = gems * multiplier
    )

    fun isAffordable(available: ResourceCost): Boolean {
        return available.gold >= gold &&
                available.wood >= wood &&
                available.ore >= ore &&
                available.mercury >= mercury &&
                available.sulfur >= sulfur &&
                available.crystal >= crystal &&
                available.gems >= gems
    }

    fun summary(): String {
        val parts = mutableListOf<String>()
        if (gold > 0) parts.add("$gold Gold")
        if (wood > 0) parts.add("$wood Wood")
        if (ore > 0) parts.add("$ore Ore")
        if (mercury > 0) parts.add("$mercury Mercury")
        if (sulfur > 0) parts.add("$sulfur Sulfur")
        if (crystal > 0) parts.add("$crystal Crystal")
        if (gems > 0) parts.add("$gems Gems")
        return if (parts.isEmpty()) "Free" else parts.joinToString(", ")
    }
}

/**
 * Creature statistical data schema.
 */
data class CreatureDefinition(
    val id: String,
    val name: String,
    val faction: Faction,
    val tier: Int,
    val attack: Int,
    val defense: Int,
    val minDamage: Int,
    val maxDamage: Int,
    val health: Int,
    val speed: Int,
    val growth: Int,
    val cost: ResourceCost,
    val shots: Int = 0,
    val isRanged: Boolean = false,
    val isFlying: Boolean = false,
    val isWide: Boolean = false,
    val retaliations: Int = 1,
    val abilities: List<CreatureAbility> = emptyList(),
    val activeAbilities: List<CreatureActiveAbility> = emptyList()
) {
    init {
        require(id.isNotBlank()) { "Creature id cannot be blank" }
        require(tier in 1..7) { "Creature tier must be 1..7" }
        require(attack >= 0) { "Attack must be non-negative" }
        require(defense >= 0) { "Defense must be non-negative" }
        require(minDamage > 0) { "MinDamage must be positive" }
        require(maxDamage >= minDamage) { "MaxDamage must be >= MinDamage" }
        require(health > 0) { "Health must be positive" }
        require(speed > 0) { "Speed must be positive" }
    }
}
