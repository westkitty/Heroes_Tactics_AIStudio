package com.example

import com.example.core.CombatSide
import com.example.core.CombatSimulation
import com.example.core.CombatStack
import com.example.data.Faction
import com.example.data.GameCatalog
import com.example.data.ResourceCost
import com.example.data.TerrainType
import com.example.engine.AdventureCoordinate
import com.example.engine.AdventureMapGrid
import com.example.engine.HexCoordinate
import com.example.engine.ai.AdventureAiDecision
import com.example.engine.ai.AdventureAiHero
import com.example.engine.ai.AdventureMapAi
import com.example.engine.ai.AdventureMapEntity
import com.example.engine.ai.TacticalAiDecision
import com.example.engine.ai.TacticalCombatAi
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
class AiSystemTest {

    @Before
    fun setup() {
        GameCatalog.ensureInitialized()
    }

    @Test
    fun `test tactical combat AI executes ranged attack if shooter is unblocked`() {
        val sim = CombatSimulation()
        val archer = GameCatalog.getCreature("archer") // Speed 6
        val pikeman = GameCatalog.getCreature("pikeman") // Speed 4

        val shooter = CombatStack(
            id = "att_shooter",
            slotIndex = 0,
            definition = archer,
            count = 10,
            side = CombatSide.ATTACKER,
            hex = HexCoordinate(0, 5)
        )
        val enemy = CombatStack(
            id = "def_target",
            slotIndex = 0,
            definition = pikeman,
            count = 20,
            side = CombatSide.DEFENDER,
            hex = HexCoordinate(10, 5)
        )

        sim.setupBattle(listOf(shooter), listOf(enemy))
        sim.turnQueue.advanceTurn()

        val decision = TacticalCombatAi.computeDecision(sim)
        assertTrue("Expected RangedAttack decision, got $decision", decision is TacticalAiDecision.RangedAttack)
        val rangedDecision = decision as TacticalAiDecision.RangedAttack
        assertEquals(enemy.id, rangedDecision.targetStackId)

        // Execute turn
        TacticalCombatAi.executeAiTurn(sim)
        // Enemy should take damage
        assertTrue(enemy.count < 20 || enemy.damageTakenOnTopUnit > 0)
    }

    @Test
    fun `test tactical combat AI selects adjacent melee attack when in range`() {
        val sim = CombatSimulation()
        val archangel = GameCatalog.getCreature("archangel") // Speed 18
        val cerberus = GameCatalog.getCreature("cerberus") // Speed 8

        val meleeAttacker = CombatStack(
            id = "att_angel",
            slotIndex = 0,
            definition = archangel,
            count = 2,
            side = CombatSide.ATTACKER,
            hex = HexCoordinate(5, 5)
        )
        val enemyAdjacent = CombatStack(
            id = "def_cerb",
            slotIndex = 0,
            definition = cerberus,
            count = 5,
            side = CombatSide.DEFENDER,
            hex = HexCoordinate(5, 4)
        )

        sim.setupBattle(listOf(meleeAttacker), listOf(enemyAdjacent))
        sim.turnQueue.advanceTurn()

        val decision = TacticalCombatAi.computeDecision(sim)
        assertTrue("Expected MeleeAttack decision, got $decision", decision is TacticalAiDecision.MeleeAttack)
        val meleeDecision = decision as TacticalAiDecision.MeleeAttack
        assertEquals(enemyAdjacent.id, meleeDecision.targetStackId)
    }

    @Test
    fun `test tactical combat AI pathfinds and closes distance when out of range`() {
        val sim = CombatSimulation()
        val crusader = GameCatalog.getCreature("crusader") // Speed 6
        val pikeman = GameCatalog.getCreature("pikeman") // Speed 4

        val meleeAttacker = CombatStack(
            id = "att_crusader",
            slotIndex = 0,
            definition = crusader,
            count = 15,
            side = CombatSide.ATTACKER,
            hex = HexCoordinate(0, 0)
        )
        val farEnemy = CombatStack(
            id = "def_pike",
            slotIndex = 0,
            definition = pikeman,
            count = 5,
            side = CombatSide.DEFENDER,
            hex = HexCoordinate(14, 10)
        )

        sim.setupBattle(listOf(meleeAttacker), listOf(farEnemy))
        sim.turnQueue.advanceTurn()

        // 1. First turn: Faster melee unit chooses Wait so slower enemy must advance first
        val decisionWait = TacticalCombatAi.computeDecision(sim)
        assertEquals(TacticalAiDecision.Wait, decisionWait)

        // 2. If unit has already waited, it advances toward enemy
        meleeAttacker.hasWaited = true
        val decisionMove = TacticalCombatAi.computeDecision(sim)
        assertTrue("Expected Move decision to close distance, got $decisionMove", decisionMove is TacticalAiDecision.Move)
        val moveDecision = decisionMove as TacticalAiDecision.Move
        // Should move closer than initial (0, 0)
        val initialDist = HexCoordinate(0, 0).distanceTo(HexCoordinate(14, 10))
        val newDist = moveDecision.targetHex.distanceTo(HexCoordinate(14, 10))
        assertTrue("New distance $newDist should be less than initial $initialDist", newDist < initialDist)
    }

    @Test
    fun `test adventure map AI prioritizes capturing unflagged mines and collecting treasures`() {
        val grid = AdventureMapGrid(10, 10, TerrainType.GRASS)
        val aiHero = AdventureAiHero(
            id = "ai_hero_1",
            name = "Sandro",
            faction = Faction.INFERNO,
            position = AdventureCoordinate(0, 0),
            movementPoints = 1500,
            maxMovementPoints = 2000,
            armyPower = 2000
        )

        val mine = AdventureMapEntity.Mine(AdventureCoordinate(2, 0), "Gold Mine")
        val entities = listOf(mine)

        val decision = AdventureMapAi.computeDecision(
            hero = aiHero,
            grid = grid,
            entities = entities,
            playerCoord = AdventureCoordinate(9, 9),
            playerArmyPower = 1000,
            terrainCatalog = GameCatalog.terrain
        )

        assertTrue(decision is AdventureAiDecision.Move)
        val move = decision as AdventureAiDecision.Move
        assertEquals(mine, move.targetEntity)

        // Execute decision
        val log = AdventureMapAi.executeDecision(aiHero, decision)
        assertTrue(log.contains("Gold Mine"))
        assertTrue("Mine should now be flagged by AI", mine.isFlaggedByAi)
        assertEquals(AdventureCoordinate(2, 0), aiHero.position)
    }

    @Test
    fun `test adventure map AI pursues player if hero army is significantly stronger`() {
        val grid = AdventureMapGrid(10, 10, TerrainType.GRASS)
        val aiHero = AdventureAiHero(
            id = "ai_hero_2",
            name = "Xeron",
            faction = Faction.INFERNO,
            position = AdventureCoordinate(5, 5),
            movementPoints = 1200,
            maxMovementPoints = 2000,
            armyPower = 3500 // Much stronger than player's 1000
        )

        val entities = emptyList<AdventureMapEntity>()

        val decision = AdventureMapAi.computeDecision(
            hero = aiHero,
            grid = grid,
            entities = entities,
            playerCoord = AdventureCoordinate(6, 5),
            playerArmyPower = 1000,
            terrainCatalog = GameCatalog.terrain
        )

        assertTrue("AI should move towards player hero", decision is AdventureAiDecision.Move)
        val move = decision as AdventureAiDecision.Move
        assertTrue(move.path.isNotEmpty())
    }

    @Test
    fun `test auto battle automated turn loop runs until battle resolution`() {
        val sim = CombatSimulation()
        val pikeman = GameCatalog.getCreature("pikeman")
        val imp = GameCatalog.getCreature("imp")

        val attArmy = listOf(CombatStack("att_pike", 0, pikeman, 50, side = CombatSide.ATTACKER, hex = HexCoordinate(0, 5)))
        val defArmy = listOf(CombatStack("def_imp", 0, imp, 10, side = CombatSide.DEFENDER, hex = HexCoordinate(14, 5)))

        sim.setupBattle(attArmy, defArmy)

        var turnsExecuted = 0
        val maxTurns = 50

        while (!sim.isBattleOver && turnsExecuted < maxTurns) {
            val active = sim.turnQueue.currentActiveStack
            if (active != null && active.isAlive && !active.hasActed) {
                TacticalCombatAi.executeAiTurn(sim)
            } else {
                sim.advanceTurn()
            }
            turnsExecuted++
        }

        assertTrue("Auto-battle should finish within turn limit", sim.isBattleOver)
        assertNotNull("Battle should have a winner", sim.winner)
        assertEquals(CombatSide.ATTACKER, sim.winner)
    }
}
