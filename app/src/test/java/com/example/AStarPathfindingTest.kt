package com.example

import com.example.data.Faction
import com.example.data.GameCatalog
import com.example.data.RoadType
import com.example.data.TerrainType
import com.example.engine.AStarPathfinder
import com.example.engine.AdventureCoordinate
import com.example.engine.AdventureMapGrid
import com.example.engine.FacingDirection
import com.example.engine.HexCoordinate
import com.example.engine.ObstacleType
import com.example.engine.TacticalCombatGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AStarPathfindingTest {

    @Before
    fun setup() {
        GameCatalog.ensureInitialized()
    }

    @Test
    fun `test tactical straight line path without obstacles`() {
        val grid = TacticalCombatGrid()
        val start = HexCoordinate(1, 1)
        val goal = HexCoordinate(5, 1)

        val result = AStarPathfinder.findTacticalPath(
            grid = grid,
            startHex = start,
            goalHex = goal,
            maxMovementRange = 10
        )

        assertTrue(result.isReachable)
        assertEquals(4, result.totalCost)
        assertEquals(5, result.path.size)
        assertEquals(start, result.path.first())
        assertEquals(goal, result.path.last())
    }

    @Test
    fun `test tactical path navigation around rock obstacle wall`() {
        val grid = TacticalCombatGrid()
        val start = HexCoordinate(2, 3)
        val goal = HexCoordinate(4, 3)

        // Place obstacle wall blocking straight path at (3, 3) and neighbors
        grid.setObstacle(HexCoordinate(3, 3), ObstacleType.ROCK)
        grid.setObstacle(HexCoordinate(3, 2), ObstacleType.ROCK)

        val result = AStarPathfinder.findTacticalPath(
            grid = grid,
            startHex = start,
            goalHex = goal,
            maxMovementRange = 10
        )

        assertTrue(result.isReachable)
        assertTrue(result.totalCost > 2) // Must detour around obstacle wall
        assertFalse(result.path.contains(HexCoordinate(3, 3)))
        assertFalse(result.path.contains(HexCoordinate(3, 2)))
        assertEquals(goal, result.path.last())
    }

    @Test
    fun `test tactical flying unit ignores ground obstacles`() {
        val grid = TacticalCombatGrid()
        val start = HexCoordinate(1, 5)
        val goal = HexCoordinate(5, 5)

        // Completely surrounded with rocks
        grid.setObstacle(HexCoordinate(2, 5), ObstacleType.ROCK)
        grid.setObstacle(HexCoordinate(3, 5), ObstacleType.ROCK)
        grid.setObstacle(HexCoordinate(4, 5), ObstacleType.ROCK)

        val result = AStarPathfinder.findTacticalPath(
            grid = grid,
            startHex = start,
            goalHex = goal,
            maxMovementRange = 10,
            isFlying = true
        )

        assertTrue(result.isReachable)
        assertEquals(4, result.totalCost)
    }

    @Test
    fun `test tactical reachable hexes flood count`() {
        val grid = TacticalCombatGrid()
        val start = HexCoordinate(7, 5)

        // Speed 1 reachable set
        val speed1 = AStarPathfinder.getReachableHexes(grid, start, speed = 1)
        // Center + 6 neighbors = 7
        assertEquals(7, speed1.size)

        // Speed 2 reachable set
        val speed2 = AStarPathfinder.getReachableHexes(grid, start, speed = 2)
        // 1 + 6 + 12 = 19
        assertEquals(19, speed2.size)
    }

    @Test
    fun `test adventure map pathfinding with terrain cost matrix and roads`() {
        val map = AdventureMapGrid(10, 10, defaultTerrain = TerrainType.GRASS)
        // Set up a patch of swamp
        map.setTile(AdventureCoordinate(2, 0), TerrainType.SWAMP)
        map.setTile(AdventureCoordinate(2, 1), TerrainType.SWAMP)
        map.setTile(AdventureCoordinate(2, 2), TerrainType.SWAMP)

        // Set up a cobblestone road path along the bottom
        map.setTile(AdventureCoordinate(0, 3), TerrainType.GRASS, road = RoadType.COBBLESTONE_ROAD)
        map.setTile(AdventureCoordinate(1, 3), TerrainType.GRASS, road = RoadType.COBBLESTONE_ROAD)
        map.setTile(AdventureCoordinate(2, 3), TerrainType.GRASS, road = RoadType.COBBLESTONE_ROAD)
        map.setTile(AdventureCoordinate(3, 3), TerrainType.GRASS, road = RoadType.COBBLESTONE_ROAD)

        val start = AdventureCoordinate(0, 0)
        val goal = AdventureCoordinate(3, 0)

        val pathResult = AStarPathfinder.findAdventurePath(
            grid = map,
            startCoord = start,
            goalCoord = goal,
            availableMovementPoints = 2000,
            terrainCatalog = GameCatalog.terrain
        )

        assertTrue(pathResult.isCompleteGoalReached)
        assertTrue(pathResult.totalMovementCost > 0)
        assertEquals(start, pathResult.fullPath.first())
        assertEquals(goal, pathResult.fullPath.last())
    }

    @Test
    fun `test adventure map movement points budget truncation`() {
        val map = AdventureMapGrid(10, 10, defaultTerrain = TerrainType.GRASS)
        val start = AdventureCoordinate(0, 0)
        val goal = AdventureCoordinate(8, 0)

        // Total distance is 8 steps = 800 MP. If hero only has 300 MP:
        val pathResult = AStarPathfinder.findAdventurePath(
            grid = map,
            startCoord = start,
            goalCoord = goal,
            availableMovementPoints = 300,
            terrainCatalog = GameCatalog.terrain,
            allowDiagonal = false
        )

        assertFalse(pathResult.isCompleteGoalReached)
        assertEquals(9, pathResult.fullPath.size) // 0..8
        assertEquals(4, pathResult.reachablePath.size) // start (0) + 3 steps = 300 MP
        assertEquals(300, pathResult.usedMovementPoints)
        assertEquals(0, pathResult.remainingMovementPoints)
    }
}
