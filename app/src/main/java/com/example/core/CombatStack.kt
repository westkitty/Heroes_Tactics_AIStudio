package com.example.core

import com.example.data.CreatureDefinition
import com.example.engine.FacingDirection
import com.example.engine.HexCoordinate

/**
 * Combat army allegiance side.
 */
enum class CombatSide {
    ATTACKER,
    DEFENDER
}

/**
 * Tactical Combat Unit Stack model.
 * Represents an active stack of creatures on the hex grid with real-time stats and turn flags.
 */
data class CombatStack(
    val id: String,
    val slotIndex: Int,
    val definition: CreatureDefinition,
    var count: Int,
    val initialCount: Int = count,
    val side: CombatSide,
    var hex: HexCoordinate,
    var facing: FacingDirection = if (side == CombatSide.ATTACKER) FacingDirection.EAST else FacingDirection.WEST,
    var damageTakenOnTopUnit: Int = 0,
    var shotsRemaining: Int = definition.shots,
    var retaliationsRemaining: Int = definition.retaliations,
    var hasActed: Boolean = false,
    var hasWaited: Boolean = false,
    var isDefending: Boolean = false,
    var moraleScore: Int = 0,
    var luckScore: Int = 0,
    val activeBuffs: MutableMap<String, Int> = mutableMapOf(), // Buff name -> remaining rounds
    val statModifiers: MutableMap<String, Int> = mutableMapOf(), // Stat name -> integer modifier
    val abilityCooldowns: MutableMap<String, Int> = mutableMapOf(), // Ability id -> remaining cooldown rounds
    val abilityChargesRemaining: MutableMap<String, Int> = mutableMapOf() // Ability id -> remaining battle charges
) {
    val isAlive: Boolean get() = count > 0

    val isRanged: Boolean get() = definition.isRanged && shotsRemaining > 0

    init {
        // Initialize charges from creature definition
        for (ability in definition.activeAbilities) {
            if (ability.maxCharges >= 0 && !abilityChargesRemaining.containsKey(ability.id)) {
                abilityChargesRemaining[ability.id] = ability.maxCharges
            }
        }
    }

    val effectiveAttack: Int
        get() = (definition.attack + (statModifiers["ATTACK"] ?: 0)).coerceAtLeast(0)

    val effectiveDefense: Int
        get() {
            var def = definition.defense + (statModifiers["DEFENSE"] ?: 0)
            if (isDefending) {
                // Defend action gives +20% defense (min +1)
                def += (def * 20) / 100 + 1
            }
            return def.coerceAtLeast(0)
        }

    val effectiveSpeed: Int
        get() {
            val speedBonus = statModifiers["SPEED"] ?: 0
            val speedMod = definition.speed + speedBonus
            val slowFactor = if (activeBuffs.containsKey("SLOW")) 2 else 1
            return (speedMod / slowFactor).coerceAtLeast(1)
        }

    val totalCurrentHealth: Int
        get() = if (count <= 0) 0 else ((count - 1) * definition.health) + (definition.health - damageTakenOnTopUnit)

    fun canUseActiveAbility(ability: com.example.data.CreatureActiveAbility): Boolean {
        if (!isAlive || hasActed) return false
        val currentCd = abilityCooldowns[ability.id] ?: 0
        if (currentCd > 0) return false
        val charges = abilityChargesRemaining[ability.id] ?: if (ability.maxCharges >= 0) ability.maxCharges else Int.MAX_VALUE
        if (charges <= 0) return false
        return true
    }

    fun recordActiveAbilityUsed(ability: com.example.data.CreatureActiveAbility) {
        if (ability.cooldownRounds > 0) {
            abilityCooldowns[ability.id] = ability.cooldownRounds
        }
        if (ability.maxCharges >= 0) {
            val currentCharges = abilityChargesRemaining[ability.id] ?: ability.maxCharges
            abilityChargesRemaining[ability.id] = (currentCharges - 1).coerceAtLeast(0)
        }
    }

    /**
     * Resets round-specific action states and ticks down ability cooldowns.
     */
    fun startNewRound() {
        hasActed = false
        hasWaited = false
        isDefending = false
        retaliationsRemaining = definition.retaliations

        // Tick down ability cooldowns
        val cdKeys = abilityCooldowns.keys.toList()
        for (key in cdKeys) {
            val cd = abilityCooldowns[key] ?: 0
            if (cd > 0) {
                val nextCd = cd - 1
                if (nextCd <= 0) {
                    abilityCooldowns.remove(key)
                } else {
                    abilityCooldowns[key] = nextCd
                }
            }
        }

        // Tick buffs / debuffs
        val expired = mutableListOf<String>()
        for ((buff, duration) in activeBuffs) {
            val remaining = duration - 1
            if (remaining <= 0) {
                expired.add(buff)
            } else {
                activeBuffs[buff] = remaining
            }
        }
        for (e in expired) {
            activeBuffs.remove(e)
            statModifiers.remove(e)
        }
    }
}
