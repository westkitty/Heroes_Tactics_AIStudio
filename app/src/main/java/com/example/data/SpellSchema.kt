package com.example.data

/**
 * Magic schools in HoMM3.
 */
enum class SpellSchool {
    AIR,
    EARTH,
    FIRE,
    WATER,
    ALL
}

/**
 * Spell mechanical behavior types.
 */
enum class SpellType {
    DAMAGE_TARGET,
    DAMAGE_AREA,
    BUFF_TARGET,
    DEBUFF_TARGET,
    HEAL_TARGET,
    RESURRECT_TARGET
}

/**
 * Spell data definition schema.
 */
data class SpellDefinition(
    val id: String,
    val name: String,
    val school: SpellSchool,
    val level: Int,
    val manaCost: Int,
    val type: SpellType,
    val baseDamage: Int = 0,
    val powerMultiplier: Int = 0,
    val radius: Int = 0,
    val statModifier: Int = 0,
    val speedFactor: Double = 1.0,
    val targetStat: String = "",
    val baseHeal: Int = 0,
    val baseHealth: Int = 0,
    val description: String = ""
) {
    init {
        require(id.isNotBlank()) { "Spell id cannot be blank" }
        require(level in 1..5) { "Spell level must be 1..5" }
        require(manaCost >= 0) { "Mana cost must be non-negative" }
    }

    /**
     * Computes deterministic spell damage based on hero Spell Power.
     */
    fun calculateDamage(spellPower: Int): Int {
        return baseDamage + (spellPower * powerMultiplier)
    }

    /**
     * Computes deterministic heal/resurrection amount based on hero Spell Power.
     */
    fun calculateHealOrResurrect(spellPower: Int): Int {
        return if (type == SpellType.RESURRECT_TARGET) {
            baseHealth + (spellPower * powerMultiplier)
        } else {
            baseHeal + (spellPower * powerMultiplier)
        }
    }
}
