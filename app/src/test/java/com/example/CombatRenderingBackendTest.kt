package com.example

import androidx.compose.ui.geometry.Offset
import com.example.core.CombatSide
import com.example.core.CombatSimulation
import com.example.core.CombatStack
import com.example.data.GameCatalog
import com.example.engine.HexCoordinate
import com.example.engine.ObstacleType
import com.example.renderer.CombatRenderingBackend
import com.example.renderer.CombatSceneAdapter
import com.example.renderer.CombatVisualFx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CombatRenderingBackendTest {

    @Before
    fun setup() {
        GameCatalog.ensureInitialized()
    }

    @Test
    fun `test combat scene adapter generates immutable decoupled snapshot`() {
        val sim = CombatSimulation()
        sim.grid.setObstacle(HexCoordinate(5, 5), ObstacleType.ROCK)

        val archangel = GameCatalog.getCreature("archangel")
        val imp = GameCatalog.getCreature("imp")

        val attackers = listOf(
            CombatStack(
                id = "att_1",
                slotIndex = 0,
                definition = archangel,
                count = 10,
                side = CombatSide.ATTACKER,
                hex = HexCoordinate(0, 0)
            )
        )
        val defenders = listOf(
            CombatStack(
                id = "def_1",
                slotIndex = 0,
                definition = imp,
                count = 20,
                side = CombatSide.DEFENDER,
                hex = HexCoordinate(14, 10)
            )
        )
        sim.setupBattle(attackers, defenders)
        sim.turnQueue.advanceTurn()

        val pathOverlay = listOf(HexCoordinate(0, 0), HexCoordinate(0, 1))
        val fx = listOf(CombatVisualFx.FloatingText("fx1", 0, 5, "-100", androidx.compose.ui.graphics.Color.Red, HexCoordinate(14, 10)))

        val snapshot = CombatSceneAdapter.createSnapshot(
            simulation = sim,
            selectedHex = HexCoordinate(0, 0),
            hoveredHex = HexCoordinate(0, 1),
            pathOverlay = pathOverlay,
            visualFx = fx,
            currentTick = 10
        )

        assertEquals(15, snapshot.gridWidth)
        assertEquals(11, snapshot.gridHeight)
        assertEquals(1, snapshot.obstacles.size)
        assertEquals(ObstacleType.ROCK, snapshot.obstacles[HexCoordinate(5, 5)])
        assertEquals(2, snapshot.stacks.size)

        val attackerRender = snapshot.stacks.first { it.id == "att_1" }
        assertEquals("archangel", attackerRender.creatureId)
        assertEquals(10, attackerRender.count)
        assertEquals(CombatSide.ATTACKER, attackerRender.side)
        assertTrue(attackerRender.isActive)

        val defenderRender = snapshot.stacks.first { it.id == "def_1" }
        assertEquals("imp", defenderRender.creatureId)
        assertEquals(20, defenderRender.count)
        assertEquals(CombatSide.DEFENDER, defenderRender.side)

        assertEquals(1, snapshot.activeVisualFx.size)
        assertTrue(snapshot.reachableHexes.isNotEmpty())
        assertEquals(2, snapshot.pathOverlay.size)
    }

    @Test
    fun `test hex center coordinate projection is deterministic and matches odd-r offset grid`() {
        val radius = 30f
        val origin = Offset(100f, 50f)

        val c00 = CombatRenderingBackend.calculateHexCenter(HexCoordinate(0, 0), radius, origin)
        val c10 = CombatRenderingBackend.calculateHexCenter(HexCoordinate(1, 0), radius, origin)
        val c01 = CombatRenderingBackend.calculateHexCenter(HexCoordinate(0, 1), radius, origin)

        val hexWidth = radius * kotlin.math.sqrt(3f)
        val vertSpacing = radius * 1.5f

        // Row 0 is even -> center is at origin + (0 * hexWidth) + (hexWidth / 2)
        assertEquals(origin.x + (hexWidth / 2f), c00.x, 0.001f)
        assertEquals(origin.y + radius, c00.y, 0.001f)

        // Col 1, Row 0 -> shifted by 1 full width
        assertEquals(origin.x + (1 * hexWidth) + (hexWidth / 2f), c10.x, 0.001f)

        // Col 0, Row 1 -> odd row is offset by half hex width
        assertEquals(origin.x + (hexWidth / 2f) + (hexWidth / 2f), c01.x, 0.001f)
        assertEquals(origin.y + (1 * vertSpacing) + radius, c01.y, 0.001f)
    }
}
