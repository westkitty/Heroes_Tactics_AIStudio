package com.example

import com.example.core.MineType
import com.example.core.ResourceEconomy
import com.example.data.GameCatalog
import com.example.data.ResourceCost
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
class EconomySimulationTest {

    @Before
    fun setup() {
        GameCatalog.ensureInitialized()
    }

    @Test
    fun `test daily gold accumulation from town hall and mines`() {
        val economy = ResourceEconomy(initialStockpile = ResourceCost(gold = 1000, wood = 0, ore = 0))
        economy.addMine(MineType.SAWMILL) // +2 wood / day
        economy.addMine(MineType.GOLD_MINE) // +1000 gold / day

        // Starting Day 1, Town Hall produces +500 gold
        val day1Income = economy.tickDay(GameCatalog.buildings, GameCatalog.creatures)
        assertEquals(1500, day1Income.gold) // 500 town hall + 1000 gold mine
        assertEquals(2, day1Income.wood)

        assertEquals(2500, economy.stockpile.gold)
        assertEquals(2, economy.stockpile.wood)
        assertEquals(2, economy.day)
    }

    @Test
    fun `test building construction prerequisites and resource deduction`() {
        val economy = ResourceEconomy(
            initialStockpile = ResourceCost(gold = 10000, wood = 50, ore = 50, mercury = 10, sulfur = 10, crystal = 10, gems = 10)
        )

        val fort = GameCatalog.getBuilding("fort")
        val citadel = GameCatalog.getBuilding("citadel")
        val guardhouse = GameCatalog.getBuilding("guardhouse")
        val archersTower = GameCatalog.getBuilding("archers_tower")

        // Cannot build citadel before fort
        assertFalse(economy.constructBuilding(citadel))

        // Build Fort (5000 gold, 20 wood, 20 ore)
        assertTrue(economy.constructBuilding(fort))
        assertEquals(5000, economy.stockpile.gold)
        assertEquals(30, economy.stockpile.wood)
        assertEquals(30, economy.stockpile.ore)

        // Now citadel can be built
        assertTrue(economy.constructBuilding(citadel))

        // Guardhouse requires Fort (already built)
        assertTrue(economy.constructBuilding(guardhouse))

        // Archers tower requires Guardhouse
        assertTrue(economy.constructBuilding(archersTower))
    }

    @Test
    fun `test weekly creature growth and dwelling recruitment`() {
        val economy = ResourceEconomy(
            initialStockpile = ResourceCost(gold = 10000, wood = 40, ore = 40)
        )
        val fort = GameCatalog.getBuilding("fort")
        val guardhouse = GameCatalog.getBuilding("guardhouse")
        assertTrue(economy.constructBuilding(fort))
        assertTrue(economy.constructBuilding(guardhouse))

        // Pikeman base growth is 14
        val pikeman = GameCatalog.getCreature("pikeman")
        assertEquals(0, economy.getAvailableRecruits("pikeman"))

        // Simulate 7 days to reach Week 2 Day 1
        for (i in 1..7) {
            economy.tickDay(GameCatalog.buildings, GameCatalog.creatures)
        }
        assertEquals(2, economy.week)
        assertEquals(1, economy.dayOfWeek)
        assertEquals(14, economy.getAvailableRecruits("pikeman"))

        // Recruit 10 pikemen (60 gold each = 600 gold)
        val initialGold = economy.stockpile.gold
        assertTrue(economy.recruitCreatures(pikeman, 10))
        assertEquals(4, economy.getAvailableRecruits("pikeman"))
        assertEquals(initialGold - 600, economy.stockpile.gold)

        // Attempting to recruit more than available fails
        assertFalse(economy.recruitCreatures(pikeman, 10))
    }
}
