package com.example

import com.example.engine.FacingDirection
import com.example.engine.HexCoordinate
import com.example.engine.HexDirection
import com.example.engine.ObstacleType
import com.example.engine.TacticalCombatGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HexTacticalEngineTest {

    @Test
    fun `test cube coordinate invariant sum is zero`() {
        for (row in 0..10) {
            for (col in 0..14) {
                val hex = HexCoordinate(col, row)
                val sum = hex.cubeX + hex.cubeY + hex.cubeZ
                assertEquals("Cube coords for ($col, $row) must sum to 0", 0, sum)
            }
        }
    }

    @Test
    fun `test hex distance symmetry and correctness`() {
        val a = HexCoordinate(0, 0)
        val b = HexCoordinate(5, 5)
        val c = HexCoordinate(14, 10)

        assertEquals(0, a.distanceTo(a))
        assertEquals(b.distanceTo(a), a.distanceTo(b))
        assertEquals(c.distanceTo(a), a.distanceTo(c))

        // Direct horizontal distance on even row
        val h1 = HexCoordinate(2, 4)
        val h2 = HexCoordinate(7, 4)
        assertEquals(5, h1.distanceTo(h2))

        // Immediate neighbors have distance 1
        for (dir in HexDirection.values()) {
            val neighbor = h1.getNeighbor(dir)
            assertEquals(1, h1.distanceTo(neighbor))
        }
    }

    @Test
    fun `test opposite directions symmetry`() {
        for (dir in HexDirection.values()) {
            val opp = dir.opposite()
            assertEquals(dir, opp.opposite())
        }
    }

    @Test
    fun `test two hex unit occupancy`() {
        val grid = TacticalCombatGrid()
        val head = HexCoordinate(5, 4)

        // Facing East -> Tail is West
        val occupiedEast = grid.getOccupiedHexes(head, isWide = true, facing = FacingDirection.EAST)
        assertEquals(2, occupiedEast.size)
        assertEquals(HexCoordinate(5, 4), occupiedEast[0])
        assertEquals(HexCoordinate(4, 4), occupiedEast[1])

        // Facing West -> Tail is East
        val occupiedWest = grid.getOccupiedHexes(head, isWide = true, facing = FacingDirection.WEST)
        assertEquals(2, occupiedWest.size)
        assertEquals(HexCoordinate(5, 4), occupiedWest[0])
        assertEquals(HexCoordinate(6, 4), occupiedWest[1])
    }

    @Test
    fun `test obstacles and boundaries validation`() {
        val grid = TacticalCombatGrid(15, 11)
        val rockHex = HexCoordinate(7, 5)

        assertFalse(grid.hasObstacle(rockHex))
        grid.setObstacle(rockHex, ObstacleType.ROCK)
        assertTrue(grid.hasObstacle(rockHex))

        // Obstacle makes placement invalid
        assertFalse(grid.isPlacementValid(rockHex, isWide = false, facing = FacingDirection.EAST))
        assertTrue(grid.isPlacementValid(HexCoordinate(0, 0), isWide = false, facing = FacingDirection.EAST))

        // Out of bounds check
        assertFalse(grid.isInBounds(HexCoordinate(-1, 0)))
        assertFalse(grid.isInBounds(HexCoordinate(15, 0)))
        assertFalse(grid.isInBounds(HexCoordinate(0, 11)))
    }
}
