package com.example.engine

import com.example.data.Faction
import com.example.data.TerrainDefinition
import com.example.data.TerrainType
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.max

/**
 * Result of tactical pathfinding.
 */
data class TacticalPathResult(
    val path: List<HexCoordinate>,
    val totalCost: Int,
    val isReachable: Boolean
)

/**
 * Result of adventure map pathfinding incorporating hero movement budget.
 */
data class AdventurePathResult(
    val fullPath: List<AdventureCoordinate>,
    val reachablePath: List<AdventureCoordinate>,
    val totalMovementCost: Int,
    val usedMovementPoints: Int,
    val remainingMovementPoints: Int,
    val stepCosts: List<Int>,
    val isCompleteGoalReached: Boolean
)

/**
 * Deterministic A* Pathfinding Engine for both Tactical Hex Battles and Adventure Tile Exploration.
 */
object AStarPathfinder {

    // ==========================================
    // 1. TACTICAL HEX BATTLEFIELD PATHFINDING
    // ==========================================

    private data class HexNode(
        val hex: HexCoordinate,
        val gCost: Int,
        val hCost: Int,
        val parent: HexNode?
    ) : Comparable<HexNode> {
        val fCost: Int get() = gCost + hCost

        override fun compareTo(other: HexNode): Int {
            if (fCost != other.fCost) return fCost.compareTo(other.fCost)
            if (hCost != other.hCost) return hCost.compareTo(other.hCost)
            return hex.compareTo(other.hex)
        }
    }

    /**
     * Calculates the movement point cost of entering a hex on the tactical battlefield,
     * accounting for flying capabilities and native faction terrain bonuses.
     */
    fun calculateTacticalStepCost(
        grid: TacticalCombatGrid,
        toHex: HexCoordinate,
        isFlying: Boolean = false,
        creatureFaction: Faction = Faction.NONE,
        terrainCatalog: Map<TerrainType, TerrainDefinition> = emptyMap()
    ): Int {
        if (isFlying) return 1
        val terrainType = grid.getTerrainAt(toHex)
        val terrainDef = terrainCatalog[terrainType]
        if (terrainDef != null) {
            // Native faction ignores tactical movement penalty
            if (creatureFaction != Faction.NONE && creatureFaction == terrainDef.nativeFaction) {
                return 1
            }
            return 1 + terrainDef.tacticalMovementPenalty
        }
        return 1
    }

    /**
     * Finds shortest deterministic path on the hex battlefield between startHex and goalHex,
     * accounting for terrain movement costs, unit width, flight, and obstacle avoidance.
     */
    fun findTacticalPath(
        grid: TacticalCombatGrid,
        startHex: HexCoordinate,
        goalHex: HexCoordinate,
        isWide: Boolean = false,
        facing: FacingDirection = FacingDirection.EAST,
        maxMovementRange: Int = Int.MAX_VALUE,
        blockedHexes: Set<HexCoordinate> = emptySet(),
        isFlying: Boolean = false,
        creatureFaction: Faction = Faction.NONE,
        terrainCatalog: Map<TerrainType, TerrainDefinition> = emptyMap()
    ): TacticalPathResult {
        if (startHex == goalHex) {
            return TacticalPathResult(listOf(startHex), 0, true)
        }

        // Check goal destination validity
        if (!grid.isInBounds(goalHex) || grid.hasObstacle(goalHex)) {
            return TacticalPathResult(emptyList(), 0, false)
        }

        // 2-hex wide creature check at goal
        if (isWide) {
            val tailDir = if (facing == FacingDirection.EAST) HexDirection.WEST else HexDirection.EAST
            val tailHex = goalHex.getNeighbor(tailDir)
            if (!grid.isInBounds(tailHex) || grid.hasObstacle(tailHex)) {
                return TacticalPathResult(emptyList(), 0, false)
            }
        }

        // Flying units can move directly in a straight path if distance <= maxMovementRange
        if (isFlying) {
            val dist = startHex.distanceTo(goalHex)
            return if (dist <= maxMovementRange) {
                TacticalPathResult(listOf(startHex, goalHex), dist, true)
            } else {
                TacticalPathResult(emptyList(), dist, false)
            }
        }

        val openQueue = PriorityQueue<HexNode>()
        val gScoreMap = mutableMapOf<HexCoordinate, Int>()
        val closedSet = mutableSetOf<HexCoordinate>()

        val startNode = HexNode(startHex, 0, startHex.distanceTo(goalHex), null)
        openQueue.add(startNode)
        gScoreMap[startHex] = 0

        while (openQueue.isNotEmpty()) {
            val current = openQueue.poll()!!
            if (current.hex == goalHex) {
                // Reconstruct path
                val path = mutableListOf<HexCoordinate>()
                var curr: HexNode? = current
                while (curr != null) {
                    path.add(curr.hex)
                    curr = curr.parent
                }
                path.reverse()
                val isReachable = current.gCost <= maxMovementRange
                return TacticalPathResult(path, current.gCost, isReachable)
            }

            if (closedSet.contains(current.hex)) continue
            closedSet.add(current.hex)

            for (neighbor in current.hex.getAllNeighbors()) {
                if (!grid.isInBounds(neighbor) || grid.hasObstacle(neighbor)) continue
                if (blockedHexes.contains(neighbor) && neighbor != goalHex) continue

                if (isWide) {
                    val tailDir = if (facing == FacingDirection.EAST) HexDirection.WEST else HexDirection.EAST
                    val tailHex = neighbor.getNeighbor(tailDir)
                    if (!grid.isInBounds(tailHex) || grid.hasObstacle(tailHex)) continue
                    if (blockedHexes.contains(tailHex) && tailHex != startHex && tailHex != goalHex) continue
                }

                val stepCost = calculateTacticalStepCost(grid, neighbor, isFlying, creatureFaction, terrainCatalog)
                val tentativeG = current.gCost + stepCost
                if (tentativeG > maxMovementRange) continue

                val existingG = gScoreMap[neighbor] ?: Int.MAX_VALUE
                if (tentativeG < existingG) {
                    gScoreMap[neighbor] = tentativeG
                    val h = neighbor.distanceTo(goalHex)
                    openQueue.add(HexNode(neighbor, tentativeG, h, current))
                }
            }
        }

        return TacticalPathResult(emptyList(), 0, false)
    }

    /**
     * Calculates all hexes reachable by a combat unit with given speed/movement allowance,
     * accounting for terrain-based movement penalties.
     */
    fun getReachableHexes(
        grid: TacticalCombatGrid,
        startHex: HexCoordinate,
        speed: Int,
        isWide: Boolean = false,
        facing: FacingDirection = FacingDirection.EAST,
        blockedHexes: Set<HexCoordinate> = emptySet(),
        isFlying: Boolean = false,
        creatureFaction: Faction = Faction.NONE,
        terrainCatalog: Map<TerrainType, TerrainDefinition> = emptyMap()
    ): Map<HexCoordinate, Int> {
        val reachable = mutableMapOf<HexCoordinate, Int>()
        reachable[startHex] = 0

        if (isFlying) {
            for (col in 0 until grid.width) {
                for (row in 0 until grid.height) {
                    val hex = HexCoordinate(col, row)
                    if (hex == startHex) continue
                    if (grid.hasObstacle(hex) || blockedHexes.contains(hex)) continue
                    if (isWide) {
                        val tailDir = if (facing == FacingDirection.EAST) HexDirection.WEST else HexDirection.EAST
                        val tailHex = hex.getNeighbor(tailDir)
                        if (!grid.isInBounds(tailHex) || grid.hasObstacle(tailHex) || blockedHexes.contains(tailHex)) continue
                    }
                    val dist = startHex.distanceTo(hex)
                    if (dist <= speed) {
                        reachable[hex] = dist
                    }
                }
            }
            return reachable
        }

        // Ground unit breadth-first / Dijkstra flood with terrain weights
        val queue = PriorityQueue<HexNode>()
        queue.add(HexNode(startHex, 0, 0, null))

        while (queue.isNotEmpty()) {
            val current = queue.poll()!!

            for (neighbor in current.hex.getAllNeighbors()) {
                if (!grid.isInBounds(neighbor) || grid.hasObstacle(neighbor) || blockedHexes.contains(neighbor)) continue

                if (isWide) {
                    val tailDir = if (facing == FacingDirection.EAST) HexDirection.WEST else HexDirection.EAST
                    val tailHex = neighbor.getNeighbor(tailDir)
                    if (!grid.isInBounds(tailHex) || grid.hasObstacle(tailHex) || blockedHexes.contains(tailHex)) continue
                }

                val stepCost = calculateTacticalStepCost(grid, neighbor, isFlying, creatureFaction, terrainCatalog)
                val nextCost = current.gCost + stepCost
                if (nextCost <= speed) {
                    val prevCost = reachable[neighbor] ?: Int.MAX_VALUE
                    if (nextCost < prevCost) {
                        reachable[neighbor] = nextCost
                        queue.add(HexNode(neighbor, nextCost, 0, null))
                    }
                }
            }
        }

        return reachable
    }

    // ==========================================
    // 2. ADVENTURE MAP A* PATHFINDING
    // ==========================================

    private data class AdventureNode(
        val coord: AdventureCoordinate,
        val gCost: Int,
        val hCost: Int,
        val stepCost: Int,
        val parent: AdventureNode?
    ) : Comparable<AdventureNode> {
        val fCost: Int get() = gCost + hCost

        override fun compareTo(other: AdventureNode): Int {
            if (fCost != other.fCost) return fCost.compareTo(other.fCost)
            if (hCost != other.hCost) return hCost.compareTo(other.hCost)
            return coord.compareTo(other.coord)
        }
    }

    /**
     * Calculates optimal adventure path taking into account terrain costs and hero movement points.
     */
    fun findAdventurePath(
        grid: AdventureMapGrid,
        startCoord: AdventureCoordinate,
        goalCoord: AdventureCoordinate,
        availableMovementPoints: Int,
        heroNativeFaction: Faction = Faction.NONE,
        terrainCatalog: Map<TerrainType, TerrainDefinition>,
        allowDiagonal: Boolean = true
    ): AdventurePathResult {
        if (startCoord == goalCoord) {
            return AdventurePathResult(
                fullPath = listOf(startCoord),
                reachablePath = listOf(startCoord),
                totalMovementCost = 0,
                usedMovementPoints = 0,
                remainingMovementPoints = availableMovementPoints,
                stepCosts = emptyList(),
                isCompleteGoalReached = true
            )
        }

        val openQueue = PriorityQueue<AdventureNode>()
        val gScoreMap = mutableMapOf<AdventureCoordinate, Int>()
        val closedSet = mutableSetOf<AdventureCoordinate>()

        val startNode = AdventureNode(startCoord, 0, startCoord.chebyshevDistanceTo(goalCoord) * 100, 0, null)
        openQueue.add(startNode)
        gScoreMap[startCoord] = 0

        val neighborDeltas = if (allowDiagonal) {
            listOf(
                Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1),
                Pair(1, 1), Pair(1, -1), Pair(-1, 1), Pair(-1, -1)
            )
        } else {
            listOf(Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1))
        }

        var goalNode: AdventureNode? = null

        while (openQueue.isNotEmpty()) {
            val current = openQueue.poll()!!

            if (current.coord == goalCoord) {
                goalNode = current
                break
            }

            if (closedSet.contains(current.coord)) continue
            closedSet.add(current.coord)

            for ((dx, dy) in neighborDeltas) {
                val nextCoord = AdventureCoordinate(current.coord.x + dx, current.coord.y + dy)
                if (!grid.isInBounds(nextCoord)) continue

                val isDiagonal = dx != 0 && dy != 0
                val moveCost = grid.calculateTileCost(nextCoord, isDiagonal, heroNativeFaction, terrainCatalog)
                if (moveCost == Int.MAX_VALUE) continue

                val tentativeG = current.gCost + moveCost
                val existingG = gScoreMap[nextCoord] ?: Int.MAX_VALUE

                if (tentativeG < existingG) {
                    gScoreMap[nextCoord] = tentativeG
                    val h = nextCoord.chebyshevDistanceTo(goalCoord) * 100
                    openQueue.add(AdventureNode(nextCoord, tentativeG, h, moveCost, current))
                }
            }
        }

        if (goalNode == null) {
            // Unreachable
            return AdventurePathResult(
                fullPath = emptyList(),
                reachablePath = emptyList(),
                totalMovementCost = 0,
                usedMovementPoints = 0,
                remainingMovementPoints = availableMovementPoints,
                stepCosts = emptyList(),
                isCompleteGoalReached = false
            )
        }

        // Reconstruct full path
        val fullPath = mutableListOf<AdventureCoordinate>()
        val stepCosts = mutableListOf<Int>()
        var curr: AdventureNode? = goalNode

        while (curr != null) {
            fullPath.add(curr.coord)
            if (curr.parent != null) {
                stepCosts.add(curr.stepCost)
            }
            curr = curr.parent
        }

        fullPath.reverse()
        stepCosts.reverse()

        // Truncate path to hero's available movement points
        val reachablePath = mutableListOf<AdventureCoordinate>()
        reachablePath.add(startCoord)
        var spentMp = 0

        for (i in stepCosts.indices) {
            val cost = stepCosts[i]
            if (spentMp + cost <= availableMovementPoints) {
                spentMp += cost
                reachablePath.add(fullPath[i + 1])
            } else {
                break
            }
        }

        val totalCost = goalNode.gCost
        val remainingMp = max(0, availableMovementPoints - spentMp)
        val isReached = reachablePath.size == fullPath.size

        return AdventurePathResult(
            fullPath = fullPath,
            reachablePath = reachablePath,
            totalMovementCost = totalCost,
            usedMovementPoints = spentMp,
            remainingMovementPoints = remainingMp,
            stepCosts = stepCosts,
            isCompleteGoalReached = isReached
        )
    }
}
