package com.example.engine

import com.example.core.CombatSide
import com.example.core.CombatStack
import com.example.data.TerrainType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Line of Sight (LOS) and Fog of War calculation system on the tactical hexagonal grid.
 * Uses 3D cube coordinate raycasting and obstacle occlusion to compute visibility.
 */
object FogOfWarSystem {

    const val DEFAULT_VISION_RADIUS = 4
    const val SCOUT_FLYING_VISION_RADIUS = 6

    /**
     * Calculates the unit's sight radius based on its characteristics (flying, tier, speed).
     */
    fun getUnitVisionRadius(stack: CombatStack): Int {
        var radius = DEFAULT_VISION_RADIUS
        if (stack.definition.isFlying) {
            radius += 2
        }
        if (stack.effectiveSpeed >= 8) {
            radius += 1
        }
        if (stack.definition.tier >= 6) {
            radius += 1
        }
        return radius
    }

    /**
     * Determines whether an obstacle type blocks line of sight.
     */
    fun isObstacleOpaque(type: ObstacleType): Boolean {
        return when (type) {
            ObstacleType.ROCK -> true
            ObstacleType.WALL -> true
            ObstacleType.TREE_STUMP -> true
            ObstacleType.LAVA_PIT -> false
            ObstacleType.QUICKSAND -> false
            ObstacleType.MOAT -> false
        }
    }

    /**
     * Interpolates hex coordinates along a straight line in 3D cube coordinates.
     */
    fun getLineBetweenHexes(start: HexCoordinate, end: HexCoordinate): List<HexCoordinate> {
        val distance = start.distanceTo(end)
        if (distance == 0) return listOf(start)

        val results = mutableListOf<HexCoordinate>()
        for (i in 0..distance) {
            val t = i.toFloat() / distance.toFloat()
            val x = start.cubeX + (end.cubeX - start.cubeX) * t
            val y = start.cubeY + (end.cubeY - start.cubeY) * t
            val z = start.cubeZ + (end.cubeZ - start.cubeZ) * t
            results.add(cubeRound(x, y, z))
        }
        return results
    }

    private fun cubeRound(x: Float, y: Float, z: Float): HexCoordinate {
        var rx = x.roundToInt()
        var ry = y.roundToInt()
        var rz = z.roundToInt()

        val xDiff = abs(rx - x)
        val yDiff = abs(ry - y)
        val zDiff = abs(rz - z)

        if (xDiff > yDiff && xDiff > zDiff) {
            rx = -ry - rz
        } else if (yDiff > zDiff) {
            ry = -rx - rz
        } else {
            rz = -rx - ry
        }
        return HexCoordinate.fromCube(rx, ry, rz)
    }

    /**
     * Checks if there is an unobstructed line of sight between two hexes on the grid.
     */
    fun hasLineOfSight(
        grid: TacticalCombatGrid,
        fromHex: HexCoordinate,
        toHex: HexCoordinate,
        isObserverFlying: Boolean = false
    ): Boolean {
        if (!grid.isInBounds(fromHex) || !grid.isInBounds(toHex)) return false
        if (fromHex == toHex) return true

        val line = getLineBetweenHexes(fromHex, toHex)

        // Intermediate hexes between start and target
        for (i in 1 until line.size - 1) {
            val hex = line[i]
            if (!grid.isInBounds(hex)) return false
            val obstacle = grid.getObstacle(hex)
            if (obstacle != null && isObstacleOpaque(obstacle)) {
                // Flying observers can see over low obstacles if distance <= 2, but walls/tall rocks block
                if (!(isObserverFlying && obstacle == ObstacleType.TREE_STUMP)) {
                    return false
                }
            }
        }
        return true
    }

    /**
     * Computes the set of visible hexes for a single combat stack.
     */
    fun computeStackVision(
        grid: TacticalCombatGrid,
        stack: CombatStack,
        visionRadiusOverride: Int? = null
    ): Set<HexCoordinate> {
        if (!stack.isAlive) return emptySet()

        val radius = visionRadiusOverride ?: getUnitVisionRadius(stack)
        val occupiedHexes = grid.getOccupiedHexes(stack.hex, stack.definition.isWide, stack.facing)
        val visible = mutableSetOf<HexCoordinate>()

        for (r in 0 until grid.height) {
            for (c in 0 until grid.width) {
                val candidate = HexCoordinate(c, r)
                val minDistance = occupiedHexes.minOf { it.distanceTo(candidate) }
                if (minDistance <= radius) {
                    val hasLos = occupiedHexes.any { occ ->
                        hasLineOfSight(grid, occ, candidate, isObserverFlying = stack.definition.isFlying)
                    }
                    if (hasLos) {
                        visible.add(candidate)
                    }
                }
            }
        }

        return visible
    }

    /**
     * Computes the combined field of view for all alive units belonging to a specific combat side.
     */
    fun computeSideVision(
        grid: TacticalCombatGrid,
        allStacks: List<CombatStack>,
        side: CombatSide
    ): Set<HexCoordinate> {
        val visible = mutableSetOf<HexCoordinate>()
        val teamStacks = allStacks.filter { it.isAlive && it.side == side }

        for (stack in teamStacks) {
            visible.addAll(computeStackVision(grid, stack))
        }

        return visible
    }

    /**
     * Filters out enemy stacks that are not currently in line-of-sight of the observing side.
     */
    fun filterVisibleStacks(
        grid: TacticalCombatGrid,
        allStacks: List<CombatStack>,
        observerSide: CombatSide,
        visibleHexes: Set<HexCoordinate>
    ): List<CombatStack> {
        return allStacks.filter { stack ->
            if (!stack.isAlive) return@filter false
            // Allied units are always visible
            if (stack.side == observerSide) return@filter true

            // Enemy units are visible only if any occupied hex is in visibleHexes
            val occupied = grid.getOccupiedHexes(stack.hex, stack.definition.isWide, stack.facing)
            occupied.any { visibleHexes.contains(it) }
        }
    }
}
