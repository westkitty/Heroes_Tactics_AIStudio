package com.example.engine

import com.example.data.Faction
import com.example.data.RoadType
import com.example.data.TerrainDefinition
import com.example.data.TerrainType

/**
 * 2D Tile coordinate for adventure map exploration.
 */
data class AdventureCoordinate(
    val x: Int,
    val y: Int
) : Comparable<AdventureCoordinate> {
    fun manhattanDistanceTo(other: AdventureCoordinate): Int {
        return kotlin.math.abs(x - other.x) + kotlin.math.abs(y - other.y)
    }

    fun chebyshevDistanceTo(other: AdventureCoordinate): Int {
        return kotlin.math.max(kotlin.math.abs(x - other.x), kotlin.math.abs(y - other.y))
    }

    override fun compareTo(other: AdventureCoordinate): Int {
        if (y != other.y) return y.compareTo(other.y)
        return x.compareTo(other.x)
    }
}

/**
 * Tile descriptor on the adventure map.
 */
data class AdventureTile(
    val coordinate: AdventureCoordinate,
    val terrain: TerrainType,
    val road: RoadType = RoadType.NONE,
    val isBlocked: Boolean = false
)

/**
 * NxM Adventure Map Grid.
 */
class AdventureMapGrid(
    val width: Int,
    val height: Int,
    defaultTerrain: TerrainType = TerrainType.GRASS
) {
    private val tiles = Array(height) { y ->
        Array(width) { x ->
            AdventureTile(AdventureCoordinate(x, y), defaultTerrain)
        }
    }

    init {
        require(width > 0 && height > 0) { "Map dimensions must be positive" }
    }

    fun isInBounds(coord: AdventureCoordinate): Boolean {
        return coord.x in 0 until width && coord.y in 0 until height
    }

    fun getTile(coord: AdventureCoordinate): AdventureTile {
        require(isInBounds(coord)) { "Coordinate $coord is out of map bounds" }
        return tiles[coord.y][coord.x]
    }

    fun setTile(coord: AdventureCoordinate, terrain: TerrainType, road: RoadType = RoadType.NONE, isBlocked: Boolean = false) {
        require(isInBounds(coord)) { "Coordinate $coord is out of map bounds" }
        tiles[coord.y][coord.x] = AdventureTile(coord, terrain, road, isBlocked)
    }

    /**
     * Calculates the deterministic movement cost in integer Movement Points to enter a target tile.
     */
    fun calculateTileCost(
        targetCoord: AdventureCoordinate,
        isDiagonal: Boolean,
        heroNativeFaction: Faction = Faction.NONE,
        terrainCatalog: Map<TerrainType, TerrainDefinition>
    ): Int {
        val tile = getTile(targetCoord)
        if (tile.isBlocked || tile.terrain == TerrainType.ROCK || tile.terrain == TerrainType.WATER) {
            return Int.MAX_VALUE
        }

        val terrainDef = terrainCatalog[tile.terrain]
            ?: TerrainDefinition(tile.terrain, tile.terrain.name, 100)

        // Native terrain eliminates penalty (cost becomes base 100)
        val baseCost = if (heroNativeFaction != Faction.NONE && terrainDef.nativeFaction == heroNativeFaction) {
            100
        } else {
            terrainDef.baseMovementCost
        }

        // Apply road multiplier if present
        val roadMultiplier = tile.road.multiplier
        val costWithRoad = (baseCost * roadMultiplier).toInt()

        // Diagonal movement cost modifier: 1.414x (141 / 100)
        return if (isDiagonal) {
            (costWithRoad * 141) / 100
        } else {
            costWithRoad
        }
    }
}
