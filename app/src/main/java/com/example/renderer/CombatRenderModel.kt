package com.example.renderer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.core.CombatSide
import com.example.core.CombatSimulation
import com.example.core.CombatStack
import com.example.data.AnimationState
import com.example.data.Faction
import com.example.data.GameCatalog
import com.example.data.TerrainType
import com.example.engine.AStarPathfinder
import com.example.engine.FacingDirection
import com.example.engine.HexCoordinate
import com.example.engine.ObstacleType

/**
 * Visual FX categories rendered during combat animations.
 */
sealed class CombatVisualFx {
    abstract val id: String
    abstract val startTick: Int
    abstract val durationTicks: Int

    data class FloatingText(
        override val id: String,
        override val startTick: Int,
        override val durationTicks: Int = 8,
        val text: String,
        val color: Color,
        val hex: HexCoordinate,
        val isCrit: Boolean = false
    ) : CombatVisualFx()

    data class Projectile(
        override val id: String,
        override val startTick: Int,
        override val durationTicks: Int = 6,
        val fromHex: HexCoordinate,
        val toHex: HexCoordinate,
        val color: Color = Color(0xFFFFD700),
        val isMagic: Boolean = false
    ) : CombatVisualFx()

    data class MeleeSlash(
        override val id: String,
        override val startTick: Int,
        override val durationTicks: Int = 4,
        val targetHex: HexCoordinate,
        val slashAngleDegrees: Float = 45f,
        val color: Color = Color(0xFFEF4444)
    ) : CombatVisualFx()

    data class SpellAura(
        override val id: String,
        override val startTick: Int,
        override val durationTicks: Int = 10,
        val centerHex: HexCoordinate,
        val radiusHexes: Int = 1,
        val auraColor: Color = Color(0xFF38BDF8)
    ) : CombatVisualFx()
}

/**
 * Immutable stack render state for decoupled presentation.
 */
data class StackRenderState(
    val id: String,
    val creatureId: String,
    val name: String,
    val faction: Faction,
    val count: Int,
    val maxHpPerUnit: Int,
    val currentHpOnTopUnit: Int,
    val side: CombatSide,
    val hex: HexCoordinate,
    val facing: FacingDirection,
    val isWide: Boolean,
    val isFlying: Boolean,
    val isRanged: Boolean,
    val isWaiting: Boolean,
    val isDefending: Boolean,
    val isActive: Boolean,
    val animationState: AnimationState,
    val frameIndex: Int,
    val buffs: Map<String, Int>
) {
    val healthRatio: Float
        get() = if (maxHpPerUnit <= 0) 1f else currentHpOnTopUnit.toFloat() / maxHpPerUnit.toFloat()
}

/**
 * Immutable snapshot of the entire tactical combat battlefield for rendering.
 */
data class CombatSceneSnapshot(
    val gridWidth: Int,
    val gridHeight: Int,
    val battlefieldTerrain: TerrainType,
    val hexTerrains: Map<HexCoordinate, TerrainType>,
    val obstacles: Map<HexCoordinate, ObstacleType>,
    val stacks: List<StackRenderState>,
    val activeStackId: String?,
    val selectedHex: HexCoordinate?,
    val hoveredHex: HexCoordinate?,
    val reachableHexes: Map<HexCoordinate, Int>,
    val attackableHexes: Set<HexCoordinate>,
    val pathOverlay: List<HexCoordinate>,
    val activeVisualFx: List<CombatVisualFx>,
    val roundNumber: Int,
    val isBattleOver: Boolean,
    val winner: CombatSide?,
    val currentTick: Int,
    val fogOfWarEnabled: Boolean = false,
    val visibleHexes: Set<HexCoordinate> = emptySet(),
    val exploredHexes: Set<HexCoordinate> = emptySet()
) {
    fun getStackAtHex(hex: HexCoordinate): StackRenderState? {
        return stacks.firstOrNull { stack ->
            stack.hex == hex || (stack.isWide && (if (stack.facing == FacingDirection.EAST) stack.hex.getNeighbor(com.example.engine.HexDirection.WEST) else stack.hex.getNeighbor(com.example.engine.HexDirection.EAST)) == hex)
        }
    }
}

/**
 * Adapter converting simulation state into immutable snapshot models for rendering.
 */
object CombatSceneAdapter {

    fun createSnapshot(
        simulation: CombatSimulation,
        selectedHex: HexCoordinate? = null,
        hoveredHex: HexCoordinate? = null,
        pathOverlay: List<HexCoordinate> = emptyList(),
        visualFx: List<CombatVisualFx> = emptyList(),
        currentTick: Int = 0
    ): CombatSceneSnapshot {
        val activeStack = simulation.turnQueue.currentActiveStack
        val fogEnabled = simulation.fogOfWarEnabled
        val visibleHexes = if (fogEnabled) simulation.calculateVisibleHexes(CombatSide.ATTACKER) else emptySet()
        val exploredHexes = if (fogEnabled) simulation.exploredHexes.toSet() else emptySet()

        val stackStates = simulation.getAllStacks()
            .filter { stack ->
                if (!stack.isAlive) return@filter false
                if (!fogEnabled || stack.side == CombatSide.ATTACKER) return@filter true
                // Mask enemy units outside player line of sight
                val occupied = simulation.grid.getOccupiedHexes(stack.hex, stack.definition.isWide, stack.facing)
                occupied.any { visibleHexes.contains(it) }
            }
            .map { stack ->
                val isActive = stack.id == activeStack?.id
                val animState = if (isActive) AnimationState.IDLE else AnimationState.IDLE
                val topUnitHp = stack.definition.health - stack.damageTakenOnTopUnit

                StackRenderState(
                    id = stack.id,
                    creatureId = stack.definition.id,
                    name = stack.definition.name,
                    faction = stack.definition.faction,
                    count = stack.count,
                    maxHpPerUnit = stack.definition.health,
                    currentHpOnTopUnit = topUnitHp.coerceIn(1, stack.definition.health),
                    side = stack.side,
                    hex = stack.hex,
                    facing = stack.facing,
                    isWide = stack.definition.isWide,
                    isFlying = stack.definition.isFlying,
                    isRanged = stack.definition.isRanged,
                    isWaiting = stack.hasWaited,
                    isDefending = stack.isDefending,
                    isActive = isActive,
                    animationState = animState,
                    frameIndex = currentTick,
                    buffs = stack.activeBuffs.toMap()
                )
            }

        // Calculate reachable movement hexes & attackable targets for active stack
        val reachableHexes = mutableMapOf<HexCoordinate, Int>()
        val attackableHexes = mutableSetOf<HexCoordinate>()

        if (activeStack != null && activeStack.isAlive && !activeStack.hasActed) {
            val blocked = simulation.getBlockedHexes(activeStack.id)
            val reach = AStarPathfinder.getReachableHexes(
                grid = simulation.grid,
                startHex = activeStack.hex,
                speed = activeStack.effectiveSpeed,
                isWide = activeStack.definition.isWide,
                facing = activeStack.facing,
                blockedHexes = blocked,
                isFlying = activeStack.definition.isFlying,
                creatureFaction = activeStack.definition.faction,
                terrainCatalog = GameCatalog.terrain
            )
            reachableHexes.putAll(reach)

            val enemyStacks = simulation.getAllStacks().filter { enemy ->
                enemy.isAlive && enemy.side != activeStack.side && (!fogEnabled || {
                    val occ = simulation.grid.getOccupiedHexes(enemy.hex, enemy.definition.isWide, enemy.facing)
                    occ.any { visibleHexes.contains(it) }
                }())
            }

            if (activeStack.isRanged && activeStack.shotsRemaining > 0) {
                // Ranged attacker can target visible enemy stacks
                for (enemy in enemyStacks) {
                    attackableHexes.addAll(
                        simulation.grid.getOccupiedHexes(enemy.hex, enemy.definition.isWide, enemy.facing)
                    )
                }
            } else {
                // Melee attacker can target enemy stacks adjacent to start hex or adjacent to reachable hexes
                val validAttackSpots = reachableHexes.keys + activeStack.hex
                for (enemy in enemyStacks) {
                    val enemyOccupied = simulation.grid.getOccupiedHexes(enemy.hex, enemy.definition.isWide, enemy.facing)
                    val canReachEnemy = enemyOccupied.any { eHex ->
                        eHex.getAllNeighbors().any { nHex -> validAttackSpots.contains(nHex) }
                    }
                    if (canReachEnemy) {
                        attackableHexes.addAll(enemyOccupied)
                    }
                }
            }
        }

        return CombatSceneSnapshot(
            gridWidth = simulation.grid.width,
            gridHeight = simulation.grid.height,
            battlefieldTerrain = simulation.grid.battlefieldTerrain,
            hexTerrains = simulation.grid.getAllHexTerrains(),
            obstacles = simulation.grid.getAllObstacles(),
            stacks = stackStates,
            activeStackId = activeStack?.id,
            selectedHex = selectedHex,
            hoveredHex = hoveredHex,
            reachableHexes = reachableHexes,
            attackableHexes = attackableHexes,
            pathOverlay = pathOverlay,
            activeVisualFx = visualFx,
            roundNumber = simulation.turnQueue.roundNumber,
            isBattleOver = simulation.isBattleOver,
            winner = simulation.winner,
            currentTick = currentTick,
            fogOfWarEnabled = fogEnabled,
            visibleHexes = visibleHexes,
            exploredHexes = exploredHexes
        )
    }
}

