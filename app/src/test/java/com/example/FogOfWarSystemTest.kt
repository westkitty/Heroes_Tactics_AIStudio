package com.example

import com.example.core.CombatSide
import com.example.core.CombatSimulation
import com.example.core.CombatStack
import com.example.data.GameCatalog
import com.example.engine.FogOfWarSystem
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
class FogOfWarSystemTest {

    @Before
    fun setup() {
        GameCatalog.ensureInitialized()
    }

    @Test
    fun `test direct line of sight unobstructed within vision radius`() {
        val grid = TacticalCombatGrid()
        val start = HexCoordinate(0, 5)
        val target = HexCoordinate(3, 5)

        val hasLos = FogOfWarSystem.hasLineOfSight(
            grid = grid,
            fromHex = start,
            toHex = target,
            isObserverFlying = false
        )
        assertTrue("Should have clear line of sight along empty hex row", hasLos)
    }

    @Test
    fun `test rock obstacle blocks ground unit line of sight`() {
        val grid = TacticalCombatGrid()
        val start = HexCoordinate(0, 5)
        val blocker = HexCoordinate(2, 5)
        val target = HexCoordinate(4, 5)

        grid.setObstacle(blocker, ObstacleType.ROCK)

        val groundLos = FogOfWarSystem.hasLineOfSight(
            grid = grid,
            fromHex = start,
            toHex = target,
            isObserverFlying = false
        )
        assertFalse("Ground unit LOS should be blocked by rock obstacle", groundLos)
    }

    @Test
    fun `test flying creature can see over tree stump obstacles`() {
        val grid = TacticalCombatGrid()
        val start = HexCoordinate(0, 5)
        val stump = HexCoordinate(2, 5)
        val target = HexCoordinate(4, 5)

        grid.setObstacle(stump, ObstacleType.TREE_STUMP)

        val groundLos = FogOfWarSystem.hasLineOfSight(
            grid = grid,
            fromHex = start,
            toHex = target,
            isObserverFlying = false
        )
        assertFalse("Ground unit vision should be obstructed by tree stump", groundLos)

        val flyingLos = FogOfWarSystem.hasLineOfSight(
            grid = grid,
            fromHex = start,
            toHex = target,
            isObserverFlying = true
        )
        assertTrue("Flying unit should see over tree stump obstacle", flyingLos)
    }

    @Test
    fun `test computeSideVision aggregates all allied unit sightlines and filters hidden enemies`() {
        val sim = CombatSimulation()
        sim.fogOfWarEnabled = true

        val archer = GameCatalog.getCreature("archer")
        val pikeman = GameCatalog.getCreature("pikeman")
        val devil = GameCatalog.getCreature("arch_devil")

        val attStack = CombatStack("att_archer", 0, archer, 10, side = CombatSide.ATTACKER, hex = HexCoordinate(0, 0))
        val farEnemy = CombatStack("def_devil", 0, devil, 1, side = CombatSide.DEFENDER, hex = HexCoordinate(14, 10))
        val nearEnemy = CombatStack("def_pike", 1, pikeman, 5, side = CombatSide.DEFENDER, hex = HexCoordinate(2, 0))

        sim.setupBattle(listOf(attStack), listOf(farEnemy, nearEnemy))

        val visibleHexes = sim.calculateVisibleHexes(CombatSide.ATTACKER)
        assertTrue("Attacker stack's own hex must be visible", visibleHexes.contains(attStack.hex))
        assertTrue("Near enemy hex must be visible", visibleHexes.contains(nearEnemy.hex))
        assertFalse("Far enemy hex should be outside initial line of sight radius", visibleHexes.contains(farEnemy.hex))

        val visibleEnemies = sim.getVisibleStacks(CombatSide.ATTACKER)
        assertEquals("Should only see allied stack and near enemy", 2, visibleEnemies.size)
        assertTrue(visibleEnemies.any { it.id == nearEnemy.id })
        assertFalse(visibleEnemies.any { it.id == farEnemy.id })
    }

    @Test
    fun `test explored hexes accumulate when units move through fog`() {
        val sim = CombatSimulation()
        sim.fogOfWarEnabled = true

        val angel = GameCatalog.getCreature("archangel")
        val devil = GameCatalog.getCreature("arch_devil")

        val attAngel = CombatStack("att_angel", 0, angel, 1, side = CombatSide.ATTACKER, hex = HexCoordinate(0, 5))
        val defDevil = CombatStack("def_devil", 0, devil, 1, side = CombatSide.DEFENDER, hex = HexCoordinate(14, 5))

        sim.setupBattle(listOf(attAngel), listOf(defDevil))
        sim.turnQueue.advanceTurn()

        val initialExploredCount = sim.exploredHexes.size
        assertTrue(initialExploredCount > 0)

        // Angel advances towards center
        val moved = sim.executeMove(HexCoordinate(6, 5))
        assertTrue("Move should succeed for archangel", moved)
        val updatedExploredCount = sim.exploredHexes.size
        assertTrue("Explored hexes count should increase as unit explores battlefield", updatedExploredCount > initialExploredCount)
    }
}

