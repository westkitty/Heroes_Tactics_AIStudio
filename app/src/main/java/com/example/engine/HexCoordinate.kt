package com.example.engine

import kotlin.math.abs
import kotlin.math.max

/**
 * 6 Hexagonal neighbor directions on an odd-r hexagonal grid.
 */
enum class HexDirection(val index: Int) {
    EAST(0),
    NORTH_EAST(1),
    NORTH_WEST(2),
    WEST(3),
    SOUTH_WEST(4),
    SOUTH_EAST(5);

    fun opposite(): HexDirection = when (this) {
        EAST -> WEST
        NORTH_EAST -> SOUTH_WEST
        NORTH_WEST -> SOUTH_EAST
        WEST -> EAST
        SOUTH_WEST -> NORTH_EAST
        SOUTH_EAST -> NORTH_WEST
    }
}

/**
 * Orientation of combat units on the tactical hex battlefield.
 */
enum class FacingDirection {
    EAST, // Facing right (Attacker default)
    WEST  // Facing left (Defender default)
}

/**
 * Deterministic Hexagonal Coordinate for tactical grid combat (15x11).
 * Uses odd-r offset coordinates natively, with exact conversion to 3D Cube coordinates (x, y, z).
 */
data class HexCoordinate(
    val col: Int,
    val row: Int
) : Comparable<HexCoordinate> {

    /**
     * Cube Coordinate X (where x + y + z = 0)
     */
    val cubeX: Int get() = col - (row - (row and 1)) / 2

    /**
     * Cube Coordinate Z
     */
    val cubeZ: Int get() = row

    /**
     * Cube Coordinate Y
     */
    val cubeY: Int get() = -cubeX - cubeZ

    /**
     * Calculates the exact hexagonal Manhattan distance to another hex coordinate.
     */
    fun distanceTo(other: HexCoordinate): Int {
        return (abs(cubeX - other.cubeX) + abs(cubeY - other.cubeY) + abs(cubeZ - other.cubeZ)) / 2
    }

    /**
     * Returns the immediate neighbor in the specified HexDirection.
     */
    fun getNeighbor(direction: HexDirection): HexCoordinate {
        val isOddRow = (row and 1) != 0
        return when (direction) {
            HexDirection.EAST -> HexCoordinate(col + 1, row)
            HexDirection.WEST -> HexCoordinate(col - 1, row)
            HexDirection.NORTH_EAST -> if (isOddRow) HexCoordinate(col + 1, row - 1) else HexCoordinate(col, row - 1)
            HexDirection.NORTH_WEST -> if (isOddRow) HexCoordinate(col, row - 1) else HexCoordinate(col - 1, row - 1)
            HexDirection.SOUTH_EAST -> if (isOddRow) HexCoordinate(col + 1, row + 1) else HexCoordinate(col, row + 1)
            HexDirection.SOUTH_WEST -> if (isOddRow) HexCoordinate(col, row + 1) else HexCoordinate(col - 1, row + 1)
        }
    }

    /**
     * Returns all 6 neighboring coordinates.
     */
    fun getAllNeighbors(): List<HexCoordinate> {
        return HexDirection.values().map { getNeighbor(it) }
    }

    /**
     * Deterministic comparison for priority queues and sorting.
     */
    override fun compareTo(other: HexCoordinate): Int {
        if (row != other.row) return row.compareTo(other.row)
        return col.compareTo(other.col)
    }

    companion object {
        const val GRID_WIDTH = 15
        const val GRID_HEIGHT = 11

        fun fromCube(x: Int, y: Int, z: Int): HexCoordinate {
            val col = x + (z - (z and 1)) / 2
            val row = z
            return HexCoordinate(col, row)
        }

        fun isValidGridHex(col: Int, row: Int): Boolean {
            return col in 0 until GRID_WIDTH && row in 0 until GRID_HEIGHT
        }
    }
}
