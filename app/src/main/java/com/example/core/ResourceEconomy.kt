package com.example.core

import com.example.data.BuildingCategory
import com.example.data.BuildingDefinition
import com.example.data.CreatureDefinition
import com.example.data.ResourceCost

/**
 * Mine ownership type producing daily resource yields.
 */
enum class MineType(val dailyProduction: ResourceCost) {
    SAWMILL(ResourceCost(wood = 2)),
    ORE_PIT(ResourceCost(ore = 2)),
    ALCHEMIST_LAB(ResourceCost(mercury = 1)),
    SULFUR_DUNE(ResourceCost(sulfur = 1)),
    CRYSTAL_CAVERN(ResourceCost(crystal = 1)),
    GEM_POND(ResourceCost(gems = 1)),
    GOLD_MINE(ResourceCost(gold = 1000))
}

/**
 * Kingdom treasury and resource economy simulator.
 * Manages daily economic ticks, town income, mine production, dwelling creature recruitment, and construction prerequisites.
 */
class ResourceEconomy(
    initialStockpile: ResourceCost = ResourceCost(gold = 10000, wood = 20, ore = 20, mercury = 5, sulfur = 5, crystal = 5, gems = 5)
) {
    var stockpile: ResourceCost = initialStockpile
        private set

    var day: Int = 1
        private set

    val week: Int get() = ((day - 1) / 7) + 1
    val dayOfWeek: Int get() = ((day - 1) % 7) + 1

    private val constructedBuildingIds = mutableSetOf<String>()
    private val ownedMines = mutableListOf<MineType>()
    private val dwellingAvailableCreatures = mutableMapOf<String, Int>() // Creature ID -> Available to recruit

    init {
        // Default starting town hall
        constructedBuildingIds.add("town_hall")
    }

    /**
     * Advances simulation by 1 day, accumulating gold and resources from buildings and mines.
     */
    fun tickDay(buildingCatalog: List<BuildingDefinition>, creatureCatalog: List<CreatureDefinition>): ResourceCost {
        var dailyIncome = ResourceCost()

        // 1. Town income
        for (bId in constructedBuildingIds) {
            val b = buildingCatalog.firstOrNull { it.id == bId }
            if (b != null && b.dailyIncome > 0) {
                dailyIncome = dailyIncome.copy(gold = dailyIncome.gold + b.dailyIncome)
            }
        }

        // 2. Mine income
        for (mine in ownedMines) {
            dailyIncome = dailyIncome + mine.dailyProduction
        }

        stockpile = stockpile + dailyIncome

        // 3. Weekly creature growth (happens on Day 1 of each new week)
        day++
        if (dayOfWeek == 1) {
            accumulateWeeklyCreatureGrowth(buildingCatalog, creatureCatalog)
        }

        return dailyIncome
    }

    /**
     * Accumulates weekly dwelling creature growth.
     */
    fun accumulateWeeklyCreatureGrowth(buildingCatalog: List<BuildingDefinition>, creatureCatalog: List<CreatureDefinition>) {
        val hasCastle = constructedBuildingIds.contains("castle")
        val hasCitadel = constructedBuildingIds.contains("citadel")

        val fortificationMultiplier = when {
            hasCastle -> 2.0 // Castle doubles growth (100% bonus)
            hasCitadel -> 1.5 // Citadel gives +50% growth
            else -> 1.0
        }

        for (bId in constructedBuildingIds) {
            val b = buildingCatalog.firstOrNull { it.id == bId }
            if (b != null && b.category == BuildingCategory.DWELLING && b.creatureId != null) {
                val creature = creatureCatalog.firstOrNull { it.id == b.creatureId }
                val baseGrowth = creature?.growth ?: b.growth
                val adjustedGrowth = (baseGrowth * fortificationMultiplier).toInt()
                val current = dwellingAvailableCreatures[b.creatureId] ?: 0
                dwellingAvailableCreatures[b.creatureId] = current + adjustedGrowth
            }
        }
    }

    /**
     * Constructs a building if prerequisites and resource costs are satisfied.
     */
    fun constructBuilding(building: BuildingDefinition): Boolean {
        if (constructedBuildingIds.contains(building.id)) return false

        // Check prerequisites
        if (!building.prerequisites.all { constructedBuildingIds.contains(it) }) {
            return false
        }

        // Check cost
        if (!building.cost.isAffordable(stockpile)) {
            return false
        }

        // Deduct cost and add building
        stockpile = ResourceCost(
            gold = stockpile.gold - building.cost.gold,
            wood = stockpile.wood - building.cost.wood,
            ore = stockpile.ore - building.cost.ore,
            mercury = stockpile.mercury - building.cost.mercury,
            sulfur = stockpile.sulfur - building.cost.sulfur,
            crystal = stockpile.crystal - building.cost.crystal,
            gems = stockpile.gems - building.cost.gems
        )
        constructedBuildingIds.add(building.id)
        return true
    }

    /**
     * Recruits creatures from dwelling pool.
     */
    fun recruitCreatures(creature: CreatureDefinition, count: Int): Boolean {
        if (count <= 0) return false
        val available = dwellingAvailableCreatures[creature.id] ?: 0
        if (available < count) return false

        val totalCost = creature.cost * count
        if (!totalCost.isAffordable(stockpile)) return false

        stockpile = ResourceCost(
            gold = stockpile.gold - totalCost.gold,
            wood = stockpile.wood - totalCost.wood,
            ore = stockpile.ore - totalCost.ore,
            mercury = stockpile.mercury - totalCost.mercury,
            sulfur = stockpile.sulfur - totalCost.sulfur,
            crystal = stockpile.crystal - totalCost.crystal,
            gems = stockpile.gems - totalCost.gems
        )

        dwellingAvailableCreatures[creature.id] = available - count
        return true
    }

    fun addMine(mine: MineType) {
        ownedMines.add(mine)
    }

    fun getConstructedBuildings(): Set<String> = constructedBuildingIds.toSet()

    fun getAvailableRecruits(creatureId: String): Int = dwellingAvailableCreatures[creatureId] ?: 0

    fun addResources(cost: ResourceCost) {
        stockpile = stockpile + cost
    }
}
