package com.example.engine

import com.example.data.TerrainType

/**
 * Battlefield obstacle classification.
 */
enum class ObstacleType {
    ROCK,
    TREE_STUMP,
    LAVA_PIT,
    QUICKSAND,
    WALL,
    MOAT
}

/**
 * Tactical hex battlefield model managing boundaries, obstacles, occupancy, and terrain properties.
 */
class TacticalCombatGrid(
    val width: Int = HexCoordinate.GRID_WIDTH,
    val height: Int = HexCoordinate.GRID_HEIGHT,
    var battlefieldTerrain: TerrainType = TerrainType.GRASS
) {
    private val obstacles = mutableMapOf<HexCoordinate, ObstacleType>()
    private val hexTerrain = mutableMapOf<HexCoordinate, TerrainType>()

    /**
     * Gets terrain type at specific hex coordinate.
     */
    fun getTerrainAt(hex: HexCoordinate): TerrainType {
        return hexTerrain[hex] ?: battlefieldTerrain
    }

    /**
     * Sets specific terrain type for a hex coordinate.
     */
    fun setTerrainAt(hex: HexCoordinate, terrain: TerrainType) {
        require(isInBounds(hex)) { "Hex $hex is out of combat grid bounds" }
        hexTerrain[hex] = terrain
    }

    /**
     * Clears individual hex terrain overrides.
     */
    fun clearHexTerrains() {
        hexTerrain.clear()
    }

    /**
     * Returns an immutable copy of all hex terrain overrides.
     */
    fun getAllHexTerrains(): Map<HexCoordinate, TerrainType> = hexTerrain.toMap()

    /**
     * Checks if a coordinate is within grid bounds.
     */
    fun isInBounds(hex: HexCoordinate): Boolean {
        return hex.col in 0 until width && hex.row in 0 until height
    }

    /**
     * Places a tactical obstacle on the grid.
     */
    fun setObstacle(hex: HexCoordinate, type: ObstacleType) {
        require(isInBounds(hex)) { "Hex $hex is out of combat grid bounds" }
        obstacles[hex] = type
    }

    /**
     * Removes an obstacle if present.
     */
    fun removeObstacle(hex: HexCoordinate) {
        obstacles.remove(hex)
    }

    /**
     * Checks if a hex contains an obstacle.
     */
    fun hasObstacle(hex: HexCoordinate): Boolean {
        return obstacles.containsKey(hex)
    }

    /**
     * Gets obstacle type at given coordinate.
     */
    fun getObstacle(hex: HexCoordinate): ObstacleType? = obstacles[hex]

    /**
     * Clears all placed obstacles.
     */
    fun clearObstacles() {
        obstacles.clear()
    }

    /**
     * Returns an immutable copy of all obstacles.
     */
    fun getAllObstacles(): Map<HexCoordinate, ObstacleType> = obstacles.toMap()

    /**
     * Calculates the occupied hexes for a unit (1-hex or 2-hex wide creature).
     */
    fun getOccupiedHexes(headHex: HexCoordinate, isWide: Boolean, facing: FacingDirection): List<HexCoordinate> {
        if (!isWide) return listOf(headHex)
        val tailDir = if (facing == FacingDirection.EAST) HexDirection.WEST else HexDirection.EAST
        val tailHex = headHex.getNeighbor(tailDir)
        return listOf(headHex, tailHex)
    }

    /**
     * Checks if a 1-hex or 2-hex placement is valid and unobstructed.
     */
    fun isPlacementValid(headHex: HexCoordinate, isWide: Boolean, facing: FacingDirection): Boolean {
        val hexes = getOccupiedHexes(headHex, isWide, facing)
        return hexes.all { isInBounds(it) && !hasObstacle(it) }
    }
}
