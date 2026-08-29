package com.example

import com.example.core.CombatMath
import com.example.core.CombatSide
import com.example.core.CombatSimulation
import com.example.core.CombatStack
import com.example.core.DeterministicRng
import com.example.core.TurnAdvanceResult
import com.example.core.TurnOrderQueue
import com.example.data.CreatureAbility
import com.example.data.CreatureDefinition
import com.example.data.Faction
import com.example.data.ResourceCost
import com.example.engine.HexCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreSimulationTest {

    private fun createTestCreature(
        id: String,
        speed: Int,
        attack: Int = 10,
        defense: Int = 10,
        minDmg: Int = 5,
        maxDmg: Int = 5,
        health: Int = 20,
        isRanged: Boolean = false,
        shots: Int = 0,
        retaliations: Int = 1,
        abilities: List<CreatureAbility> = emptyList()
    ): CreatureDefinition {
        return CreatureDefinition(
            id = id,
            name = id.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
            faction = Faction.CASTLE,
            tier = 1,
            attack = attack,
            defense = defense,
            minDamage = minDmg,
            maxDamage = maxDmg,
            health = health,
            speed = speed,
            growth = 10,
            cost = ResourceCost(gold = 100),
            shots = shots,
            isRanged = isRanged,
            retaliations = retaliations,
            abilities = abilities
        )
    }

    @Test
    fun `test turn order sorting by speed descending and side tie breaking`() {
        val archangel = createTestCreature("archangel", speed = 18)
        val devil = createTestCreature("arch_devil", speed = 17)
        val champion = createTestCreature("champion", speed = 9)
        val pikemanFast = createTestCreature("pikeman1", speed = 5)
        val pikemanSlow = createTestCreature("pikeman2", speed = 5)

        val stack1 = CombatStack("att_angel", 0, archangel, 1, side = CombatSide.ATTACKER, hex = HexCoordinate(0, 5))
        val stack2 = CombatStack("def_devil", 0, devil, 1, side = CombatSide.DEFENDER, hex = HexCoordinate(14, 5))
        val stack3 = CombatStack("att_champ", 1, champion, 5, side = CombatSide.ATTACKER, hex = HexCoordinate(0, 1))
        val stack4 = CombatStack("def_pike", 1, pikemanFast, 10, side = CombatSide.DEFENDER, hex = HexCoordinate(14, 1))
        val stack5 = CombatStack("att_pike", 2, pikemanSlow, 10, side = CombatSide.ATTACKER, hex = HexCoordinate(0, 9))

        val queue = TurnOrderQueue(DeterministicRng(42))
        val sorted = queue.sortInitiativeOrder(listOf(stack5, stack4, stack3, stack2, stack1))

        assertEquals("att_angel", sorted[0].id) // Speed 18
        assertEquals("def_devil", sorted[1].id) // Speed 17
        assertEquals("att_champ", sorted[2].id) // Speed 9
        assertEquals("att_pike", sorted[3].id) // Speed 5, Attacker ties before Defender
        assertEquals("def_pike", sorted[4].id) // Speed 5, Defender
    }

    @Test
    fun `test wait action moves unit to waiting queue`() {
        val c1 = createTestCreature("fast", speed = 10)
        val c2 = createTestCreature("medium", speed = 7)
        val c3 = createTestCreature("slow", speed = 4)

        val s1 = CombatStack("s1", 0, c1, 5, side = CombatSide.ATTACKER, hex = HexCoordinate(0, 0))
        val s2 = CombatStack("s2", 1, c2, 5, side = CombatSide.DEFENDER, hex = HexCoordinate(14, 0))
        val s3 = CombatStack("s3", 2, c3, 5, side = CombatSide.ATTACKER, hex = HexCoordinate(0, 5))

        val queue = TurnOrderQueue(DeterministicRng(42))
        queue.initializeCombat(listOf(s1, s2, s3))

        val turn1 = queue.advanceTurn()
        assertTrue(turn1 is TurnAdvanceResult.ActiveStack)
        assertEquals("s1", (turn1 as TurnAdvanceResult.ActiveStack).stack.id)

        // S1 executes Wait
        val waited = queue.handleWait()
        assertTrue(waited)
        assertTrue(s1.hasWaited)
        assertFalse(s1.hasActed)

        // Next active unit should be S2 (medium)
        val turn2 = queue.advanceTurn()
        assertEquals("s2", (turn2 as TurnAdvanceResult.ActiveStack).stack.id)
        queue.finishStackAction()

        // Next active unit should be S3 (slow)
        val turn3 = queue.advanceTurn()
        assertEquals("s3", (turn3 as TurnAdvanceResult.ActiveStack).stack.id)
        queue.finishStackAction()

        // After main queue finishes, S1 acts from waiting queue
        val turn4 = queue.advanceTurn()
        assertEquals("s1", (turn4 as TurnAdvanceResult.ActiveStack).stack.id)
        queue.finishStackAction()

        // Round should now complete
        val turn5 = queue.advanceTurn()
        assertTrue(turn5 is TurnAdvanceResult.RoundCompleted)
    }

    @Test
    fun `test defend action grants 20 percent defense bonus`() {
        val creature = createTestCreature("swordsman", speed = 5, defense = 12)
        val enemy = createTestCreature("enemy", speed = 1, defense = 5)
        val stack = CombatStack("sword", 0, creature, 10, side = CombatSide.ATTACKER, hex = HexCoordinate(0, 0))
        val enemyStack = CombatStack("enemy", 0, enemy, 10, side = CombatSide.DEFENDER, hex = HexCoordinate(14, 0))

        assertEquals(12, stack.effectiveDefense)
        val queue = TurnOrderQueue(DeterministicRng(42))
        queue.initializeCombat(listOf(stack, enemyStack))
        val turn = queue.advanceTurn()
        assertTrue(turn is TurnAdvanceResult.ActiveStack)

        val defended = queue.handleDefend()
        assertTrue(defended)
        assertTrue(stack.isDefending)
        assertTrue(stack.hasActed)
        // 12 + (12 * 20 / 100 + 1) = 12 + 2 + 1 = 15
        assertEquals(15, stack.effectiveDefense)

        // Reset in next round
        stack.startNewRound()
        assertFalse(stack.isDefending)
        assertEquals(12, stack.effectiveDefense)
    }

    @Test
    fun `test combat math damage scaling attacker advantage`() {
        val diff5 = CombatMath.calculateStatMultiplierBps(attack = 15, defense = 10)
        // +5% * 5 = +25% = 12500 bps
        assertEquals(12500, diff5)

        val maxAttack = CombatMath.calculateStatMultiplierBps(attack = 80, defense = 10)
        // Capped at +300% = 40000 bps (4.0x)
        assertEquals(40000, maxAttack)
    }

    @Test
    fun `test combat math damage scaling defender advantage`() {
        val diff4 = CombatMath.calculateStatMultiplierBps(attack = 6, defense = 10)
        // -2.5% * 4 = -10% = 9000 bps (0.9x)
        assertEquals(9000, diff4)

        val maxDefense = CombatMath.calculateStatMultiplierBps(attack = 0, defense = 50)
        // Capped at -70% = 3000 bps (0.3x)
        assertEquals(3000, maxDefense)
    }

    @Test
    fun `test stack casualties math and top unit damage carry over`() {
        val defender = createTestCreature("gargoyle", speed = 6, health = 15, minDmg = 3, maxDmg = 3)
        val attacker = createTestCreature("swordsman", speed = 5, attack = 10, defense = 10, minDmg = 10, maxDmg = 10)

        // Attacker deals 10 * 10 = 100 damage (multiplier 1.0x with equal stats)
        val result = CombatMath.calculateCombatDamage(
            attackerDefinition = attacker,
            attackerCount = 10,
            attackerEffectiveAttack = 10,
            defenderDefinition = defender,
            defenderCount = 10,
            defenderEffectiveDefense = 10,
            defenderDamageTakenOnTopUnit = 0,
            rng = DeterministicRng(1)
        )

        assertEquals(100, result.totalDamageDealt)
        // 100 dmg / 15 health = 6 units killed (90 hp), 10 remaining dmg taken by next unit
        assertEquals(6, result.unitsKilled)
        assertEquals(4, result.survivingUnits)
        assertEquals(10, result.topUnitDamageTaken)
    }

    @Test
    fun `test royal griffin unlimited retaliations vs standard 1 retaliation`() {
        val standard = createTestCreature("pikeman", speed = 4, retaliations = 1)
        val royalGriffin = createTestCreature("royal_griffin", speed = 9, retaliations = 999, abilities = listOf(CreatureAbility.UNLIMITED_RETALIATION))
        val enemy = createTestCreature("goblin", speed = 5)

        assertTrue(CombatMath.canRetaliate(standard, 1, enemy, false))
        assertFalse(CombatMath.canRetaliate(standard, 0, enemy, false))

        assertTrue(CombatMath.canRetaliate(royalGriffin, 999, enemy, false))
        assertTrue(CombatMath.canRetaliate(royalGriffin, 998, enemy, false))

        // Ranged attacks do not trigger retaliation
        assertFalse(CombatMath.canRetaliate(standard, 1, enemy, isRangedAttack = true))

        // No enemy retaliation ability
        val cerberus = createTestCreature("cerberus", speed = 8, abilities = listOf(CreatureAbility.NO_ENEMY_RETALIATION))
        assertFalse(CombatMath.canRetaliate(standard, 1, cerberus, false))
    }

    @Test
    fun `test full deterministic combat simulation engagement`() {
        val pikemanDef = createTestCreature("pikeman", speed = 4, attack = 4, defense = 5, minDmg = 2, maxDmg = 2, health = 10)
        val impDef = createTestCreature("imp", speed = 5, attack = 2, defense = 3, minDmg = 1, maxDmg = 1, health = 4)

        val attackerPikes = CombatStack("pikes", 0, pikemanDef, 20, side = CombatSide.ATTACKER, hex = HexCoordinate(1, 5))
        val defenderImps = CombatStack("imps", 0, impDef, 30, side = CombatSide.DEFENDER, hex = HexCoordinate(2, 5))

        val sim = CombatSimulation(rng = DeterministicRng(12345))
        sim.setupBattle(listOf(attackerPikes), listOf(defenderImps))

        // Imps have higher speed (5 vs 4), so imps act first
        val firstTurn = sim.turnQueue.advanceTurn()
        assertTrue(firstTurn is TurnAdvanceResult.ActiveStack)
        assertEquals("imps", (firstTurn as TurnAdvanceResult.ActiveStack).stack.id)

        // Imps attack pikes
        val impDamage = sim.executeMeleeAttack(attackerPikes)
        assertNotNull(impDamage)
        assertTrue(impDamage!!.totalDamageDealt > 0)
        // Pikes retaliate
        assertTrue(attackerPikes.isAlive)

        // Next turn: Pikes attack back
        val secondTurn = sim.turnQueue.advanceTurn()
        assertTrue(secondTurn is TurnAdvanceResult.ActiveStack)
        assertEquals("pikes", (secondTurn as TurnAdvanceResult.ActiveStack).stack.id)

        val pikeDamage = sim.executeMeleeAttack(defenderImps)
        assertNotNull(pikeDamage)
        assertTrue(sim.battleLog.isNotEmpty())
    }
}
