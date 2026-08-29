package com.example.engine.ai

import com.example.core.CombatMath
import com.example.core.CombatSide
import com.example.core.CombatSimulation
import com.example.core.CombatStack
import com.example.data.CreatureAbility
import com.example.data.SpellDefinition
import com.example.data.SpellType
import com.example.engine.AStarPathfinder
import com.example.engine.HexCoordinate

/**
 * Deterministic tactical decisions output by the Combat AI.
 */
sealed class TacticalAiDecision {
    data class MeleeAttack(val targetStackId: String, val attackFromHex: HexCoordinate) : TacticalAiDecision()
    data class RangedAttack(val targetStackId: String) : TacticalAiDecision()
    data class Move(val targetHex: HexCoordinate) : TacticalAiDecision()
    object Defend : TacticalAiDecision()
    object Wait : TacticalAiDecision()
    data class CastSpell(val spellId: String, val targetHex: HexCoordinate) : TacticalAiDecision()
}

/**
 * Deterministic AI controller for tactical hexagonal combat.
 * Evaluates threat matrices, damage output, retaliation risk, and positioning heuristics.
 */
object TacticalCombatAi {

    /**
     * Computes the optimal deterministic tactical decision for the currently active stack.
     */
    fun computeDecision(
        simulation: CombatSimulation,
        heroSpellPower: Int = 0,
        heroMana: Int = 0,
        availableSpells: List<SpellDefinition> = emptyList()
    ): TacticalAiDecision? {
        val activeStack = simulation.turnQueue.currentActiveStack ?: return null
        if (!activeStack.isAlive || activeStack.hasActed) return null

        val enemyStacks = simulation.getAllStacks()
            .filter { it.isAlive && it.side != activeStack.side }

        if (enemyStacks.isEmpty()) return TacticalAiDecision.Defend

        // 1. Spellcasting Evaluation (if hero mana and spells are available)
        if (heroMana > 0 && availableSpells.isNotEmpty()) {
            val spellDecision = evaluateSpellCasting(simulation, activeStack, enemyStacks, heroSpellPower, heroMana, availableSpells)
            if (spellDecision != null) return spellDecision
        }

        // 2. Ranged Attack Evaluation
        if (activeStack.isRanged && activeStack.shotsRemaining > 0) {
            val isEngagedInMelee = isStackEngagedInMelee(simulation, activeStack)
            val hasNoMeleePenalty = activeStack.definition.abilities.contains(CreatureAbility.NO_MELEE_PENALTY)

            if (!isEngagedInMelee || hasNoMeleePenalty) {
                val bestRangedTarget = selectBestRangedTarget(activeStack, enemyStacks)
                if (bestRangedTarget != null) {
                    return TacticalAiDecision.RangedAttack(bestRangedTarget.id)
                }
            }
        }

        // 3. Melee Attack Evaluation
        val blocked = simulation.getAllStacks()
            .filter { it.isAlive && it.id != activeStack.id }
            .flatMap { simulation.grid.getOccupiedHexes(it.hex, it.definition.isWide, it.facing) }
            .toSet()

        val reachableHexes = AStarPathfinder.getReachableHexes(
            grid = simulation.grid,
            startHex = activeStack.hex,
            speed = activeStack.effectiveSpeed,
            isWide = activeStack.definition.isWide,
            facing = activeStack.facing,
            blockedHexes = blocked,
            isFlying = activeStack.definition.isFlying
        )

        val bestMeleeAttack = evaluateMeleeAttacks(simulation, activeStack, enemyStacks, reachableHexes)
        if (bestMeleeAttack != null) {
            return bestMeleeAttack
        }

        // 4. Movement / Repositioning Evaluation
        val moveDecision = evaluateMovement(simulation, activeStack, enemyStacks, reachableHexes, blocked)
        if (moveDecision != null) {
            return moveDecision
        }

        // 5. Fallback: Defend stance (+20% defense bonus)
        return TacticalAiDecision.Defend
    }

    /**
     * Evaluates and applies the AI decision to the simulation deterministically.
     */
    fun executeAiTurn(
        simulation: CombatSimulation,
        heroSpellPower: Int = 0,
        heroMana: Int = 0,
        availableSpells: List<SpellDefinition> = emptyList()
    ): TacticalAiDecision? {
        val decision = computeDecision(simulation, heroSpellPower, heroMana, availableSpells) ?: return null

        when (decision) {
            is TacticalAiDecision.RangedAttack -> {
                val target = simulation.getAllStacks().firstOrNull { it.id == decision.targetStackId }
                if (target != null) {
                    simulation.executeRangedAttack(target)
                }
            }
            is TacticalAiDecision.MeleeAttack -> {
                val target = simulation.getAllStacks().firstOrNull { it.id == decision.targetStackId }
                if (target != null) {
                    simulation.executeMeleeAttack(target, attackFromHex = decision.attackFromHex)
                }
            }
            is TacticalAiDecision.Move -> {
                simulation.executeMove(decision.targetHex)
                simulation.turnQueue.finishStackAction()
            }
            is TacticalAiDecision.Wait -> {
                simulation.waitTurn()
            }
            is TacticalAiDecision.Defend -> {
                simulation.defendTurn()
            }
            is TacticalAiDecision.CastSpell -> {
                val spell = availableSpells.firstOrNull { it.id == decision.spellId }
                if (spell != null) {
                    simulation.castSpell(spell, heroSpellPower, decision.targetHex)
                }
            }
        }

        simulation.turnQueue.advanceTurn()
        return decision
    }

    private fun isStackEngagedInMelee(simulation: CombatSimulation, stack: CombatStack): Boolean {
        val neighbors = stack.hex.getAllNeighbors()
        return neighbors.any { nHex ->
            val occupant = simulation.getStackAtHex(nHex)
            occupant != null && occupant.isAlive && occupant.side != stack.side
        }
    }

    private fun selectBestRangedTarget(attacker: CombatStack, enemyStacks: List<CombatStack>): CombatStack? {
        return enemyStacks.maxByOrNull { enemy ->
            var score = (enemy.effectiveAttack * enemy.count * 100) / kotlin.math.max(1, enemy.effectiveDefense)
            if (enemy.isRanged) score = (score * 1.5).toInt()
            if (enemy.count * enemy.definition.health < attacker.count * attacker.definition.maxDamage * 2) {
                // High chance to wipe or severely cripple stack
                score += 300
            }
            score
        }
    }

    private fun evaluateMeleeAttacks(
        simulation: CombatSimulation,
        attacker: CombatStack,
        enemyStacks: List<CombatStack>,
        reachableHexes: Map<HexCoordinate, Int>
    ): TacticalAiDecision.MeleeAttack? {
        var bestScore = Int.MIN_VALUE
        var bestDecision: TacticalAiDecision.MeleeAttack? = null

        for (enemy in enemyStacks) {
            val defenderOccupied = simulation.grid.getOccupiedHexes(enemy.hex, enemy.definition.isWide, enemy.facing)

            // Find all candidate attack hexes around defender
            val candidateHexes = mutableSetOf<HexCoordinate>()
            for (dHex in defenderOccupied) {
                for (nHex in dHex.getAllNeighbors()) {
                    if (simulation.grid.isInBounds(nHex)) {
                        // Is attacker currently here, or is it in reachableHexes?
                        if (nHex == attacker.hex || reachableHexes.containsKey(nHex)) {
                            candidateHexes.add(nHex)
                        }
                    }
                }
            }

            for (atkHex in candidateHexes) {
                val distance = attacker.hex.distanceTo(atkHex)
                val joustingBonus = if (attacker.definition.abilities.contains(CreatureAbility.JOUSTING)) distance else 0

                // Score this attack
                val expectedDamage = (attacker.count * (attacker.definition.minDamage + attacker.definition.maxDamage) / 2)
                val defenderHp = (enemy.count - 1) * enemy.definition.health + (enemy.definition.health - enemy.damageTakenOnTopUnit)
                val willKillStack = expectedDamage >= defenderHp

                val canRetaliate = enemy.retaliationsRemaining > 0 &&
                        !attacker.definition.abilities.contains(CreatureAbility.NO_ENEMY_RETALIATION)

                var score = expectedDamage
                if (willKillStack) score += 1000
                if (enemy.isRanged) score += 400
                if (!canRetaliate) score += 200

                // Retaliation penalty
                if (canRetaliate && !willKillStack) {
                    val retaliateDmg = (enemy.count * (enemy.definition.minDamage + enemy.definition.maxDamage) / 2)
                    score -= retaliateDmg
                }

                score -= distance * 10 // prefer shorter moves

                if (score > bestScore) {
                    bestScore = score
                    bestDecision = TacticalAiDecision.MeleeAttack(enemy.id, atkHex)
                }
            }
        }

        return bestDecision
    }

    private fun evaluateMovement(
        simulation: CombatSimulation,
        attacker: CombatStack,
        enemyStacks: List<CombatStack>,
        reachableHexes: Map<HexCoordinate, Int>,
        blocked: Set<HexCoordinate>
    ): TacticalAiDecision? {
        if (reachableHexes.isEmpty()) {
            return TacticalAiDecision.Defend
        }

        // Find primary target enemy (closest)
        val targetEnemy = enemyStacks.minByOrNull { attacker.hex.distanceTo(it.hex) } ?: return null

        // Find full path towards enemy target
        val pathResult = AStarPathfinder.findTacticalPath(
            grid = simulation.grid,
            startHex = attacker.hex,
            goalHex = targetEnemy.hex,
            isWide = attacker.definition.isWide,
            facing = attacker.facing,
            maxMovementRange = 99,
            blockedHexes = blocked,
            isFlying = attacker.definition.isFlying
        )

        // Find furthest reachable step along the path
        val path = pathResult.path
        val reachableStep = path.reversed().firstOrNull { reachableHexes.containsKey(it) && it != attacker.hex }

        if (reachableStep != null) {
            // Tactical decision: If moving forward exposes unit without attacking, consider waiting
            val distToEnemyAfterMove = reachableStep.distanceTo(targetEnemy.hex)
            if (distToEnemyAfterMove > 1 && !attacker.hasWaited && attacker.effectiveSpeed > targetEnemy.effectiveSpeed) {
                // Slower enemy will have to move first next turn if we wait
                return TacticalAiDecision.Wait
            }
            return TacticalAiDecision.Move(reachableStep)
        }

        // If nowhere to advance, Defend or Wait
        return if (!attacker.hasWaited) TacticalAiDecision.Wait else TacticalAiDecision.Defend
    }

    private fun evaluateSpellCasting(
        simulation: CombatSimulation,
        attacker: CombatStack,
        enemyStacks: List<CombatStack>,
        spellPower: Int,
        mana: Int,
        availableSpells: List<SpellDefinition>
    ): TacticalAiDecision.CastSpell? {
        val affordableSpells = availableSpells.filter { it.manaCost <= mana }
        if (affordableSpells.isEmpty()) return null

        // Look for strong damage or area spells
        val damageSpell = affordableSpells.firstOrNull { it.type == SpellType.DAMAGE_AREA || it.type == SpellType.DAMAGE_TARGET }
        if (damageSpell != null) {
            val highPriorityTarget = enemyStacks.maxByOrNull { it.count * it.definition.health }
            if (highPriorityTarget != null) {
                return TacticalAiDecision.CastSpell(damageSpell.id, highPriorityTarget.hex)
            }
        }
        return null
    }
}
