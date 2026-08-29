package com.example.core

import com.example.data.CreatureAbility
import com.example.data.CreatureDefinition
import kotlin.math.max
import kotlin.math.min

/**
 * Combat outcome container detailing raw damage, multipliers, killed units, and remaining health.
 */
data class DamageResult(
    val baseDamageRolled: Int,
    val attackerMultiplierBps: Int, // Basis points: 10000 = 1.0x
    val totalDamageDealt: Int,
    val unitsKilled: Int,
    val survivingUnits: Int,
    val topUnitDamageTaken: Int,
    val isCriticalLuck: Boolean = false,
    val isRangedPenaltyApplied: Boolean = false,
    val isMeleePenaltyApplied: Boolean = false,
    val joustingBonusBps: Int = 0
)

/**
 * Pure deterministic damage calculation and mitigation engine following official HoMM3 rules.
 * All computations use integer and fixed-point arithmetic (basis points: 10000 = 100%).
 */
object CombatMath {

    const val BASIS_POINTS_ONE = 10000 // 100.0%
    const val ATTACK_BONUS_PER_POINT_BPS = 500 // +5% per point of Attack > Defense
    const val MAX_ATTACK_BONUS_BPS = 30000 // Max +300% (4.0x total damage)
    const val DEFENSE_PENALTY_PER_POINT_BPS = 250 // -2.5% per point of Defense > Attack
    const val MAX_DEFENSE_PENALTY_BPS = 7000 // Max -70% (0.3x total damage)

    /**
     * Calculates the attack vs defense damage modifier in basis points (10000 = 1.0x).
     */
    fun calculateStatMultiplierBps(attack: Int, defense: Int): Int {
        return when {
            attack > defense -> {
                val diff = attack - defense
                val bonus = min(MAX_ATTACK_BONUS_BPS, diff * ATTACK_BONUS_PER_POINT_BPS)
                BASIS_POINTS_ONE + bonus
            }
            attack < defense -> {
                val diff = defense - attack
                val penalty = min(MAX_DEFENSE_PENALTY_BPS, diff * DEFENSE_PENALTY_PER_POINT_BPS)
                BASIS_POINTS_ONE - penalty
            }
            else -> BASIS_POINTS_ONE
        }
    }

    /**
     * Computes deterministic damage dealt from attacker stack to defender stack,
     * including terrain defense bonuses and native abilities.
     */
    fun calculateCombatDamage(
        attackerDefinition: CreatureDefinition,
        attackerCount: Int,
        attackerEffectiveAttack: Int,
        defenderDefinition: CreatureDefinition,
        defenderCount: Int,
        defenderEffectiveDefense: Int,
        defenderDamageTakenOnTopUnit: Int,
        isRangedAttack: Boolean = false,
        hexDistance: Int = 1,
        isMeleeEngaged: Boolean = false,
        hexesTraveledForJousting: Int = 0,
        luckScore: Int = 0,
        terrainDefenseBonus: Int = 0,
        rng: DeterministicRng = DeterministicRng()
    ): DamageResult {
        if (attackerCount <= 0 || defenderCount <= 0) {
            return DamageResult(0, BASIS_POINTS_ONE, 0, 0, defenderCount, defenderDamageTakenOnTopUnit)
        }

        // 1. Roll base damage per unit within creature min..max damage
        val rolledPerUnit = rng.nextInt(attackerDefinition.minDamage, attackerDefinition.maxDamage)
        val rawBaseDamage = attackerCount * rolledPerUnit

        // 2. Attack vs Defense multiplier (accounting for terrain defense bonus)
        val totalDefenderDefense = defenderEffectiveDefense + terrainDefenseBonus
        val statMultiplierBps = calculateStatMultiplierBps(attackerEffectiveAttack, totalDefenderDefense)

        // 3. Modifiers (Ranged distance / Melee penalty / Jousting / Luck)
        var modifierMultiplierBps = BASIS_POINTS_ONE

        // Ranged penalties
        var isRangedPenalty = false
        var isMeleePenalty = false

        if (isRangedAttack) {
            // Distance penalty: > 10 hexes is half damage unless native sharpshooter
            if (hexDistance > 10) {
                modifierMultiplierBps = (modifierMultiplierBps * 5000) / BASIS_POINTS_ONE
                isRangedPenalty = true
            }
            // Melee obstruction penalty: if adjacent enemy and creature lacks NO_MELEE_PENALTY
            if (isMeleeEngaged && !attackerDefinition.abilities.contains(CreatureAbility.NO_MELEE_PENALTY)) {
                modifierMultiplierBps = (modifierMultiplierBps * 5000) / BASIS_POINTS_ONE
                isMeleePenalty = true
            }
        }

        // Jousting bonus (Cavaliers / Champions): +5% per hex charged
        var joustingBonusBps = 0
        if (attackerDefinition.abilities.contains(CreatureAbility.JOUSTING) ||
            attackerDefinition.abilities.contains(CreatureAbility.CHAMPION_JOUSTING)
        ) {
            val joustRate = if (attackerDefinition.abilities.contains(CreatureAbility.CHAMPION_JOUSTING)) 700 else 500
            joustingBonusBps = hexesTraveledForJousting * joustRate
            modifierMultiplierBps += joustingBonusBps
        }

        // Luck bonus (Positive luck gives 1/24 per point chance for 200% double damage)
        var isCriticalLuck = false
        if (luckScore > 0) {
            val luckChancePercent = min(30, luckScore * 10)
            if (rng.checkChance(luckChancePercent)) {
                isCriticalLuck = true
                modifierMultiplierBps = (modifierMultiplierBps * 20000) / BASIS_POINTS_ONE
            }
        }

        // 4. Calculate total final integer damage
        val totalCombinedMultiplierBps = ((statMultiplierBps.toLong() * modifierMultiplierBps.toLong()) / BASIS_POINTS_ONE).toInt()
        val totalDamage = max(1, ((rawBaseDamage.toLong() * totalCombinedMultiplierBps.toLong()) / BASIS_POINTS_ONE).toInt())

        // 5. Calculate defender stack losses
        val unitMaxHp = defenderDefinition.health
        val totalHpLost = defenderDamageTakenOnTopUnit + totalDamage
        val unitsKilled = min(defenderCount, totalHpLost / unitMaxHp)
        val survivingUnits = defenderCount - unitsKilled
        val newTopUnitDamageTaken = if (survivingUnits > 0) totalHpLost % unitMaxHp else 0

        return DamageResult(
            baseDamageRolled = rawBaseDamage,
            attackerMultiplierBps = statMultiplierBps,
            totalDamageDealt = totalDamage,
            unitsKilled = unitsKilled,
            survivingUnits = survivingUnits,
            topUnitDamageTaken = newTopUnitDamageTaken,
            isCriticalLuck = isCriticalLuck,
            isRangedPenaltyApplied = isRangedPenalty,
            isMeleePenaltyApplied = isMeleePenalty,
            joustingBonusBps = joustingBonusBps
        )
    }

    /**
     * Determines whether defender can retaliate against an attack.
     */
    fun canRetaliate(
        defenderDefinition: CreatureDefinition,
        defenderRetaliationsRemaining: Int,
        attackerDefinition: CreatureDefinition,
        isRangedAttack: Boolean
    ): Boolean {
        if (isRangedAttack) return false
        if (attackerDefinition.abilities.contains(CreatureAbility.NO_ENEMY_RETALIATION)) return false
        return defenderRetaliationsRemaining > 0
    }
}
