package com.example.core

import com.example.data.ActiveAbilityEffectType
import com.example.data.CreatureAbility
import com.example.data.CreatureActiveAbility
import com.example.data.GameCatalog
import com.example.data.SpellDefinition
import com.example.data.SpellType
import com.example.engine.AStarPathfinder
import com.example.engine.FacingDirection
import com.example.engine.FogOfWarSystem
import com.example.engine.HexCoordinate
import com.example.engine.HexDirection
import com.example.engine.TacticalCombatGrid

/**
 * Combat event action record for battle log, telemetry, and animation sequencing.
 */
sealed class CombatLogEvent {
    data class StackMoved(val stackId: String, val fromHex: HexCoordinate, val toHex: HexCoordinate, val path: List<HexCoordinate>) : CombatLogEvent()
    data class MeleeAttacked(val attackerId: String, val defenderId: String, val damageResult: DamageResult) : CombatLogEvent()
    data class Retaliated(val defenderId: String, val attackerId: String, val damageResult: DamageResult) : CombatLogEvent()
    data class RangedShot(val attackerId: String, val defenderId: String, val damageResult: DamageResult) : CombatLogEvent()
    data class ActiveAbilityUsed(val casterId: String, val abilityName: String, val targetDescription: String, val effectResult: String) : CombatLogEvent()
    data class SpellCast(val spellName: String, val targetHex: HexCoordinate, val effectDescription: String) : CombatLogEvent()
    data class StackDied(val stackId: String, val stackName: String = "") : CombatLogEvent()
    data class StackWaited(val stackId: String) : CombatLogEvent()
    data class StackDefended(val stackId: String) : CombatLogEvent()
    data class MoraleTriggered(val stackId: String, val isPositive: Boolean) : CombatLogEvent()
    data class RoundStarted(val roundNumber: Int) : CombatLogEvent()
    data class TurnTransition(val activeStackId: String, val stackName: String, val side: CombatSide) : CombatLogEvent()
    data class Victory(val winningSide: CombatSide) : CombatLogEvent()
}

/**
 * Headless deterministic simulation engine for tactical hexagonal battles.
 * Decoupled completely from rendering and audio.
 */
class CombatSimulation(
    val grid: TacticalCombatGrid = TacticalCombatGrid(),
    private val rng: DeterministicRng = DeterministicRng()
) {
    private val stacks = mutableListOf<CombatStack>()
    val turnQueue = TurnOrderQueue(rng)
    val battleLog = mutableListOf<CombatLogEvent>()

    var isBattleOver: Boolean = false
        private set
    var winner: CombatSide? = null
        private set

    var fogOfWarEnabled: Boolean = false
    val exploredHexes = mutableSetOf<HexCoordinate>()

    /**
     * Calculates the set of currently visible hexes for the specified combat side (e.g. Player/ATTACKER).
     */
    fun calculateVisibleHexes(forSide: CombatSide = CombatSide.ATTACKER): Set<HexCoordinate> {
        val visible = FogOfWarSystem.computeSideVision(grid, stacks, forSide)
        exploredHexes.addAll(visible)
        return visible
    }

    /**
     * Checks if a given hex coordinate is within line of sight of the specified combat side.
     */
    fun isHexVisible(hex: HexCoordinate, forSide: CombatSide = CombatSide.ATTACKER): Boolean {
        if (!fogOfWarEnabled) return true
        return calculateVisibleHexes(forSide).contains(hex)
    }

    /**
     * Returns the list of stacks visible to the specified combat side under fog-of-war.
     */
    fun getVisibleStacks(forSide: CombatSide = CombatSide.ATTACKER): List<CombatStack> {
        if (!fogOfWarEnabled) return getAllStacks().filter { it.isAlive }
        val visibleHexes = calculateVisibleHexes(forSide)
        return FogOfWarSystem.filterVisibleStacks(grid, stacks, forSide, visibleHexes)
    }

    /**
     * Initializes a battle with attacker and defender armies.
     */
    fun setupBattle(
        attackerStacks: List<CombatStack>,
        defenderStacks: List<CombatStack>
    ) {
        stacks.clear()
        battleLog.clear()
        exploredHexes.clear()
        isBattleOver = false
        winner = null

        stacks.addAll(attackerStacks)
        stacks.addAll(defenderStacks)

        turnQueue.initializeCombat(stacks)
        calculateVisibleHexes(CombatSide.ATTACKER)
        battleLog.add(CombatLogEvent.RoundStarted(turnQueue.roundNumber))
        turnQueue.currentActiveStack?.let {
            battleLog.add(CombatLogEvent.TurnTransition(it.id, it.definition.name, it.side))
        }
    }

    fun getAllStacks(): List<CombatStack> = stacks.toList()

    fun getStackAtHex(hex: HexCoordinate): CombatStack? {
        return stacks.firstOrNull { stack ->
            if (!stack.isAlive) return@firstOrNull false
            val occupied = grid.getOccupiedHexes(stack.hex, stack.definition.isWide, stack.facing)
            occupied.contains(hex)
        }
    }

    fun getBlockedHexes(excludeStackId: String? = null): Set<HexCoordinate> {
        val blocked = mutableSetOf<HexCoordinate>()
        for (s in stacks) {
            if (s.isAlive && s.id != excludeStackId) {
                blocked.addAll(grid.getOccupiedHexes(s.hex, s.definition.isWide, s.facing))
            }
        }
        return blocked
    }

    /**
     * Calculates defense bonus on a hex based on terrain definitions.
     */
    private fun getTerrainDefenseBonus(hex: HexCoordinate): Int {
        val terrainType = grid.getTerrainAt(hex)
        val terrainDef = GameCatalog.terrain[terrainType]
        return terrainDef?.defenseBonus ?: 0
    }

    /**
     * Advances to next turn and records log events.
     */
    fun advanceTurn(): TurnAdvanceResult {
        val result = turnQueue.advanceTurn()
        when (result) {
            is TurnAdvanceResult.ActiveStack -> {
                if (result.isExtraMoraleTurn) {
                    battleLog.add(CombatLogEvent.MoraleTriggered(result.stack.id, isPositive = true))
                }
                battleLog.add(CombatLogEvent.TurnTransition(result.stack.id, result.stack.definition.name, result.stack.side))
            }
            is TurnAdvanceResult.RoundCompleted -> {
                battleLog.add(CombatLogEvent.RoundStarted(result.roundNumber))
            }
            is TurnAdvanceResult.CombatEnded -> {
                isBattleOver = true
                winner = result.winningSide
                battleLog.add(CombatLogEvent.Victory(result.winningSide))
            }
        }
        return result
    }

    /**
     * Executes movement of active stack to target destination hex, accounting for terrain penalties.
     */
    fun executeMove(targetHex: HexCoordinate): Boolean {
        val stack = turnQueue.currentActiveStack ?: return false
        if (!stack.isAlive || stack.hasActed) return false

        val blocked = getBlockedHexes(stack.id)
        val pathResult = AStarPathfinder.findTacticalPath(
            grid = grid,
            startHex = stack.hex,
            goalHex = targetHex,
            isWide = stack.definition.isWide,
            facing = stack.facing,
            maxMovementRange = stack.effectiveSpeed,
            blockedHexes = blocked,
            isFlying = stack.definition.isFlying,
            creatureFaction = stack.definition.faction,
            terrainCatalog = GameCatalog.terrain
        )

        if (!pathResult.isReachable || pathResult.path.isEmpty()) return false

        val oldHex = stack.hex
        stack.hex = targetHex

        // Update facing direction based on movement
        if (targetHex.col > oldHex.col) {
            stack.facing = FacingDirection.EAST
        } else if (targetHex.col < oldHex.col) {
            stack.facing = FacingDirection.WEST
        }

        battleLog.add(CombatLogEvent.StackMoved(stack.id, oldHex, targetHex, pathResult.path))
        if (fogOfWarEnabled) {
            calculateVisibleHexes(CombatSide.ATTACKER)
        }
        return true
    }

    /**
     * Performs a melee attack from active stack to adjacent enemy stack.
     */
    fun executeMeleeAttack(
        targetStack: CombatStack,
        attackFromHex: HexCoordinate? = null
    ): DamageResult? {
        val attacker = turnQueue.currentActiveStack ?: return null
        if (!attacker.isAlive || attacker.hasActed || !targetStack.isAlive || attacker.side == targetStack.side) {
            return null
        }

        // If attackFromHex provided and different from current hex, move there first
        var hexesTraveled = 0
        if (attackFromHex != null && attackFromHex != attacker.hex) {
            val dist = attacker.hex.distanceTo(attackFromHex)
            if (!executeMove(attackFromHex)) return null
            hexesTraveled = dist
        }

        // Check adjacency
        val attackerOccupied = grid.getOccupiedHexes(attacker.hex, attacker.definition.isWide, attacker.facing)
        val defenderOccupied = grid.getOccupiedHexes(targetStack.hex, targetStack.definition.isWide, targetStack.facing)

        val isAdjacent = attackerOccupied.any { aHex ->
            defenderOccupied.any { dHex -> aHex.distanceTo(dHex) == 1 }
        }
        if (!isAdjacent) return null

        // Face the target
        if (targetStack.hex.col > attacker.hex.col) {
            attacker.facing = FacingDirection.EAST
        } else if (targetStack.hex.col < attacker.hex.col) {
            attacker.facing = FacingDirection.WEST
        }

        val defenderTerrainDefenseBonus = getTerrainDefenseBonus(targetStack.hex)
        val attackerTerrainDefenseBonus = getTerrainDefenseBonus(attacker.hex)

        // Calculate primary damage
        val damage = CombatMath.calculateCombatDamage(
            attackerDefinition = attacker.definition,
            attackerCount = attacker.count,
            attackerEffectiveAttack = attacker.effectiveAttack,
            defenderDefinition = targetStack.definition,
            defenderCount = targetStack.count,
            defenderEffectiveDefense = targetStack.effectiveDefense,
            defenderDamageTakenOnTopUnit = targetStack.damageTakenOnTopUnit,
            isRangedAttack = false,
            hexDistance = 1,
            hexesTraveledForJousting = hexesTraveled,
            luckScore = attacker.luckScore,
            terrainDefenseBonus = defenderTerrainDefenseBonus,
            rng = rng
        )

        // Apply casualties to defender
        targetStack.count = damage.survivingUnits
        targetStack.damageTakenOnTopUnit = damage.topUnitDamageTaken

        battleLog.add(CombatLogEvent.MeleeAttacked(attacker.id, targetStack.id, damage))

        // Double strike ability (e.g. Crusader)
        if (attacker.definition.abilities.contains(CreatureAbility.DOUBLE_STRIKE) && targetStack.isAlive) {
            val secondStrike = CombatMath.calculateCombatDamage(
                attackerDefinition = attacker.definition,
                attackerCount = attacker.count,
                attackerEffectiveAttack = attacker.effectiveAttack,
                defenderDefinition = targetStack.definition,
                defenderCount = targetStack.count,
                defenderEffectiveDefense = targetStack.effectiveDefense,
                defenderDamageTakenOnTopUnit = targetStack.damageTakenOnTopUnit,
                isRangedAttack = false,
                hexDistance = 1,
                hexesTraveledForJousting = 0,
                luckScore = attacker.luckScore,
                terrainDefenseBonus = defenderTerrainDefenseBonus,
                rng = rng
            )
            targetStack.count = secondStrike.survivingUnits
            targetStack.damageTakenOnTopUnit = secondStrike.topUnitDamageTaken
            battleLog.add(CombatLogEvent.MeleeAttacked(attacker.id, targetStack.id, secondStrike))
        }

        // Retaliation strike if defender survived
        if (targetStack.isAlive && CombatMath.canRetaliate(targetStack.definition, targetStack.retaliationsRemaining, attacker.definition, false)) {
            targetStack.retaliationsRemaining--

            val retaliation = CombatMath.calculateCombatDamage(
                attackerDefinition = targetStack.definition,
                attackerCount = targetStack.count,
                attackerEffectiveAttack = targetStack.effectiveAttack,
                defenderDefinition = attacker.definition,
                defenderCount = attacker.count,
                defenderEffectiveDefense = attacker.effectiveDefense,
                defenderDamageTakenOnTopUnit = attacker.damageTakenOnTopUnit,
                isRangedAttack = false,
                hexDistance = 1,
                luckScore = targetStack.luckScore,
                terrainDefenseBonus = attackerTerrainDefenseBonus,
                rng = rng
            )

            attacker.count = retaliation.survivingUnits
            attacker.damageTakenOnTopUnit = retaliation.topUnitDamageTaken
            battleLog.add(CombatLogEvent.Retaliated(targetStack.id, attacker.id, retaliation))

            if (!attacker.isAlive) {
                battleLog.add(CombatLogEvent.StackDied(attacker.id, attacker.definition.name))
            }
        }

        if (!targetStack.isAlive) {
            battleLog.add(CombatLogEvent.StackDied(targetStack.id, targetStack.definition.name))
        }

        // Conclude turn
        checkBattleEndCondition()
        turnQueue.finishStackAction()
        return damage
    }

    /**
     * Performs a ranged missile attack against an enemy stack.
     */
    fun executeRangedAttack(targetStack: CombatStack): DamageResult? {
        val attacker = turnQueue.currentActiveStack ?: return null
        if (!attacker.isAlive || attacker.hasActed || !targetStack.isAlive || attacker.side == targetStack.side) {
            return null
        }
        if (!attacker.isRanged || attacker.shotsRemaining <= 0) return null

        attacker.shotsRemaining--

        val dist = attacker.hex.distanceTo(targetStack.hex)

        // Check if melee engaged
        val adjacentHostiles = attacker.hex.getAllNeighbors().any { nHex ->
            val host = getStackAtHex(nHex)
            host != null && host.side != attacker.side && host.isAlive
        }

        // Face target
        if (targetStack.hex.col > attacker.hex.col) {
            attacker.facing = FacingDirection.EAST
        } else if (targetStack.hex.col < attacker.hex.col) {
            attacker.facing = FacingDirection.WEST
        }

        val defenderTerrainDefenseBonus = getTerrainDefenseBonus(targetStack.hex)

        val damage = CombatMath.calculateCombatDamage(
            attackerDefinition = attacker.definition,
            attackerCount = attacker.count,
            attackerEffectiveAttack = attacker.effectiveAttack,
            defenderDefinition = targetStack.definition,
            defenderCount = targetStack.count,
            defenderEffectiveDefense = targetStack.effectiveDefense,
            defenderDamageTakenOnTopUnit = targetStack.damageTakenOnTopUnit,
            isRangedAttack = true,
            hexDistance = dist,
            isMeleeEngaged = adjacentHostiles,
            luckScore = attacker.luckScore,
            terrainDefenseBonus = defenderTerrainDefenseBonus,
            rng = rng
        )

        targetStack.count = damage.survivingUnits
        targetStack.damageTakenOnTopUnit = damage.topUnitDamageTaken
        battleLog.add(CombatLogEvent.RangedShot(attacker.id, targetStack.id, damage))

        // Double shot ability (e.g. Marksman)
        if (attacker.definition.abilities.contains(CreatureAbility.DOUBLE_SHOT) && targetStack.isAlive && attacker.shotsRemaining > 0) {
            attacker.shotsRemaining--
            val secondShot = CombatMath.calculateCombatDamage(
                attackerDefinition = attacker.definition,
                attackerCount = attacker.count,
                attackerEffectiveAttack = attacker.effectiveAttack,
                defenderDefinition = targetStack.definition,
                defenderCount = targetStack.count,
                defenderEffectiveDefense = targetStack.effectiveDefense,
                defenderDamageTakenOnTopUnit = targetStack.damageTakenOnTopUnit,
                isRangedAttack = true,
                hexDistance = dist,
                isMeleeEngaged = adjacentHostiles,
                luckScore = attacker.luckScore,
                terrainDefenseBonus = defenderTerrainDefenseBonus,
                rng = rng
            )
            targetStack.count = secondShot.survivingUnits
            targetStack.damageTakenOnTopUnit = secondShot.topUnitDamageTaken
            battleLog.add(CombatLogEvent.RangedShot(attacker.id, targetStack.id, secondShot))
        }

        if (!targetStack.isAlive) {
            battleLog.add(CombatLogEvent.StackDied(targetStack.id, targetStack.definition.name))
        }

        checkBattleEndCondition()
        turnQueue.finishStackAction()
        return damage
    }

    /**
     * Executes a unique creature active ability and manages cooldowns/charges.
     */
    fun executeActiveAbility(
        abilityId: String,
        targetHex: HexCoordinate? = null,
        targetStack: CombatStack? = null
    ): Boolean {
        val caster = turnQueue.currentActiveStack ?: return false
        if (!caster.isAlive || caster.hasActed) return false
        val ability = caster.definition.activeAbilities.firstOrNull { it.id == abilityId } ?: return false
        if (!caster.canUseActiveAbility(ability)) return false

        var success = false
        when (ability.effectType) {
            ActiveAbilityEffectType.RESURRECTION -> {
                val target = targetStack ?: (if (targetHex != null) getStackAtHex(targetHex) else null)
                if (target != null && target.side == caster.side && target.isAlive) {
                    val maxHealth = target.definition.health
                    val totalMissingHealth = (target.initialCount - target.count) * maxHealth + target.damageTakenOnTopUnit
                    val resPower = if (ability.power > 0) ability.power else 100
                    val resHealthPool = caster.count * resPower
                    val healthToRestore = kotlin.math.min(totalMissingHealth, resHealthPool)
                    val unitsRevived = kotlin.math.min(
                        target.initialCount - target.count,
                        (healthToRestore + maxHealth - 1 - target.damageTakenOnTopUnit) / maxHealth
                    )
                    target.count += unitsRevived
                    target.damageTakenOnTopUnit = 0
                    caster.recordActiveAbilityUsed(ability)
                    battleLog.add(
                        CombatLogEvent.ActiveAbilityUsed(
                            casterId = caster.id,
                            abilityName = ability.name,
                            targetDescription = target.definition.name,
                            effectResult = "Resurrected $unitsRevived fallen creatures!"
                        )
                    )
                    success = true
                }
            }
            ActiveAbilityEffectType.HEAL -> {
                val target = targetStack ?: (if (targetHex != null) getStackAtHex(targetHex) else caster)
                if (target != null && target.side == caster.side && target.isAlive) {
                    val healAmount = caster.count * (if (ability.power > 0) ability.power else 25)
                    target.damageTakenOnTopUnit = kotlin.math.max(0, target.damageTakenOnTopUnit - healAmount)
                    caster.recordActiveAbilityUsed(ability)
                    battleLog.add(
                        CombatLogEvent.ActiveAbilityUsed(
                            casterId = caster.id,
                            abilityName = ability.name,
                            targetDescription = target.definition.name,
                            effectResult = "Restored health (wound reduced by $healAmount HP)"
                        )
                    )
                    success = true
                }
            }
            ActiveAbilityEffectType.TELEPORT -> {
                val dest = targetHex ?: return false
                if (grid.isInBounds(dest) && !grid.hasObstacle(dest) && getStackAtHex(dest) == null) {
                    if (caster.definition.isWide) {
                        val tailDir = if (caster.facing == FacingDirection.EAST) HexDirection.WEST else HexDirection.EAST
                        val tail = dest.getNeighbor(tailDir)
                        if (!grid.isInBounds(tail) || grid.hasObstacle(tail) || (getStackAtHex(tail) != null && getStackAtHex(tail) != caster)) {
                            return false
                        }
                    }
                    val fromHex = caster.hex
                    caster.hex = dest
                    caster.recordActiveAbilityUsed(ability)
                    battleLog.add(
                        CombatLogEvent.ActiveAbilityUsed(
                            casterId = caster.id,
                            abilityName = ability.name,
                            targetDescription = "Hex ($dest)",
                            effectResult = "Teleported from ($fromHex) to ($dest)"
                        )
                    )
                    success = true
                }
            }
            ActiveAbilityEffectType.FIRE_SHIELD -> {
                caster.activeBuffs["FIRE_SHIELD"] = 3
                caster.recordActiveAbilityUsed(ability)
                battleLog.add(
                    CombatLogEvent.ActiveAbilityUsed(
                        casterId = caster.id,
                        abilityName = ability.name,
                        targetDescription = caster.definition.name,
                        effectResult = "Engulfed in fiery shield aura for 3 rounds!"
                    )
                )
                success = true
            }
            ActiveAbilityEffectType.SUMMON_DEMONS -> {
                val target = targetStack ?: (if (targetHex != null) getStackAtHex(targetHex) else null)
                if (target != null && target.side == caster.side) {
                    val deadCount = (target.initialCount - target.count).coerceAtLeast(1)
                    val demonCount = kotlin.math.min(deadCount, caster.count)
                    caster.recordActiveAbilityUsed(ability)
                    battleLog.add(
                        CombatLogEvent.ActiveAbilityUsed(
                            casterId = caster.id,
                            abilityName = ability.name,
                            targetDescription = target.definition.name,
                            effectResult = "Summoned $demonCount Demon reinforcements!"
                        )
                    )
                    success = true
                }
            }
            ActiveAbilityEffectType.DISPEL_MAGIC -> {
                val target = targetStack ?: (if (targetHex != null) getStackAtHex(targetHex) else caster)
                if (target != null && target.isAlive) {
                    target.activeBuffs.clear()
                    target.statModifiers.clear()
                    caster.recordActiveAbilityUsed(ability)
                    battleLog.add(
                        CombatLogEvent.ActiveAbilityUsed(
                            casterId = caster.id,
                            abilityName = ability.name,
                            targetDescription = target.definition.name,
                            effectResult = "Purified all magic debuffs and modifiers!"
                        )
                    )
                    success = true
                }
            }
            ActiveAbilityEffectType.BLOODLUST_FRENZY -> {
                val target = targetStack ?: (if (targetHex != null) getStackAtHex(targetHex) else caster)
                if (target != null && target.side == caster.side && target.isAlive) {
                    val atkBonus = if (ability.power > 0) ability.power else 6
                    target.statModifiers["ATTACK"] = (target.statModifiers["ATTACK"] ?: 0) + atkBonus
                    target.activeBuffs["BLOODLUST"] = 3
                    caster.recordActiveAbilityUsed(ability)
                    battleLog.add(
                        CombatLogEvent.ActiveAbilityUsed(
                            casterId = caster.id,
                            abilityName = ability.name,
                            targetDescription = target.definition.name,
                            effectResult = "Surged with Bloodlust! +$atkBonus Attack for 3 rounds."
                        )
                    )
                    success = true
                }
            }
            ActiveAbilityEffectType.PRECISION_SHOT, ActiveAbilityEffectType.DEATH_STRIKE -> {
                val target = targetStack ?: (if (targetHex != null) getStackAtHex(targetHex) else null)
                if (target != null && target.side != caster.side && target.isAlive) {
                    val rawDmg = caster.count * caster.definition.maxDamage * 2
                    val mult = CombatMath.calculateStatMultiplierBps(caster.effectiveAttack + 8, target.effectiveDefense)
                    val totalDmg = kotlin.math.max(1, (rawDmg * mult) / CombatMath.BASIS_POINTS_ONE)
                    val killed = kotlin.math.min(target.count, totalDmg / target.definition.health)
                    target.count -= killed
                    caster.recordActiveAbilityUsed(ability)
                    battleLog.add(
                        CombatLogEvent.ActiveAbilityUsed(
                            casterId = caster.id,
                            abilityName = ability.name,
                            targetDescription = target.definition.name,
                            effectResult = "Lethal strike dealt $totalDmg damage! ($killed slain)"
                        )
                    )
                    if (!target.isAlive) {
                        battleLog.add(CombatLogEvent.StackDied(target.id, target.definition.name))
                    }
                    success = true
                }
            }
        }

        if (success) {
            checkBattleEndCondition()
            turnQueue.finishStackAction()
        }
        return success
    }

    /**
     * Casts a tactical spell on a target hex.
     */
    fun castSpell(spell: SpellDefinition, spellPower: Int, targetHex: HexCoordinate): Boolean {
        val targetStack = getStackAtHex(targetHex)

        when (spell.type) {
            SpellType.DAMAGE_TARGET -> {
                if (targetStack == null || !targetStack.isAlive) return false
                val rawDamage = spell.calculateDamage(spellPower)
                val unitMaxHp = targetStack.definition.health
                val totalHpLost = targetStack.damageTakenOnTopUnit + rawDamage
                val unitsKilled = kotlin.math.min(targetStack.count, totalHpLost / unitMaxHp)
                targetStack.count -= unitsKilled
                targetStack.damageTakenOnTopUnit = if (targetStack.count > 0) totalHpLost % unitMaxHp else 0

                battleLog.add(
                    CombatLogEvent.SpellCast(
                        spell.name,
                        targetHex,
                        "Dealt $rawDamage magic damage! ($unitsKilled killed)"
                    )
                )
                if (!targetStack.isAlive) {
                    battleLog.add(CombatLogEvent.StackDied(targetStack.id, targetStack.definition.name))
                }
            }
            SpellType.HEAL_TARGET -> {
                if (targetStack == null || !targetStack.isAlive) return false
                val healAmount = spell.calculateHealOrResurrect(spellPower)
                targetStack.damageTakenOnTopUnit = kotlin.math.max(0, targetStack.damageTakenOnTopUnit - healAmount)
                battleLog.add(CombatLogEvent.SpellCast(spell.name, targetHex, "Healed for $healAmount HP!"))
            }
            SpellType.RESURRECT_TARGET -> {
                if (targetStack == null || !targetStack.isAlive) return false
                val resHp = spell.calculateHealOrResurrect(spellPower)
                val unitMaxHp = targetStack.definition.health
                val restoredUnits = kotlin.math.min(targetStack.initialCount - targetStack.count, resHp / unitMaxHp)
                targetStack.count += restoredUnits
                targetStack.damageTakenOnTopUnit = 0
                battleLog.add(CombatLogEvent.SpellCast(spell.name, targetHex, "Resurrected $restoredUnits fallen creatures!"))
            }
            SpellType.BUFF_TARGET -> {
                if (targetStack == null || !targetStack.isAlive) return false
                targetStack.activeBuffs["HASTE"] = 3
                targetStack.statModifiers["SPEED"] = spell.statModifier
                battleLog.add(CombatLogEvent.SpellCast(spell.name, targetHex, "Hasted! Speed +${spell.statModifier}"))
            }
            SpellType.DEBUFF_TARGET -> {
                if (targetStack == null || !targetStack.isAlive) return false
                targetStack.activeBuffs["SLOW"] = 3
                battleLog.add(CombatLogEvent.SpellCast(spell.name, targetHex, "Slowed! Speed reduced by 50%"))
            }
            SpellType.DAMAGE_AREA -> {
                val affected = targetHex.getAllNeighbors() + targetHex
                for (hex in affected) {
                    val s = getStackAtHex(hex)
                    if (s != null && s.isAlive) {
                        val dmg = spell.calculateDamage(spellPower)
                        val unitMaxHp = s.definition.health
                        val totalLoss = s.damageTakenOnTopUnit + dmg
                        val killed = kotlin.math.min(s.count, totalLoss / unitMaxHp)
                        s.count -= killed
                        s.damageTakenOnTopUnit = if (s.count > 0) totalLoss % unitMaxHp else 0
                        if (!s.isAlive) battleLog.add(CombatLogEvent.StackDied(s.id, s.definition.name))
                    }
                }
                battleLog.add(CombatLogEvent.SpellCast(spell.name, targetHex, "Fireball blasted target area!"))
            }
        }

        checkBattleEndCondition()
        return true
    }

    /**
     * Executes 'WAIT' for current stack.
     */
    fun waitTurn(): Boolean {
        val stack = turnQueue.currentActiveStack ?: return false
        val success = turnQueue.handleWait()
        if (success) {
            battleLog.add(CombatLogEvent.StackWaited(stack.id))
        }
        return success
    }

    /**
     * Executes 'DEFEND' for current stack.
     */
    fun defendTurn(): Boolean {
        val stack = turnQueue.currentActiveStack ?: return false
        val success = turnQueue.handleDefend()
        if (success) {
            battleLog.add(CombatLogEvent.StackDefended(stack.id))
        }
        return success
    }

    /**
     * Exports current session state to JSON.
     */
    fun exportStateToJson(): String {
        return CombatStateSerializer.exportToJson(this)
    }

    /**
     * Restores simulation state from JSON.
     */
    companion object {
        fun fromJson(jsonString: String): CombatSimulation {
            return CombatStateSerializer.importFromJson(jsonString)
        }
    }

    private fun checkBattleEndCondition() {
        val aliveAttackers = stacks.filter { it.side == CombatSide.ATTACKER && it.isAlive }
        val aliveDefenders = stacks.filter { it.side == CombatSide.DEFENDER && it.isAlive }

        if (aliveAttackers.isEmpty()) {
            isBattleOver = true
            winner = CombatSide.DEFENDER
            battleLog.add(CombatLogEvent.Victory(CombatSide.DEFENDER))
        } else if (aliveDefenders.isEmpty()) {
            isBattleOver = true
            winner = CombatSide.ATTACKER
            battleLog.add(CombatLogEvent.Victory(CombatSide.ATTACKER))
        }
    }
}
