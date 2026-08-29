package com.example.engine.ai

import com.example.data.Faction
import com.example.data.ResourceCost
import com.example.data.TerrainDefinition
import com.example.data.TerrainType
import com.example.engine.AStarPathfinder
import com.example.engine.AdventureCoordinate
import com.example.engine.AdventureMapGrid

/**
 * Interactive neutral or strategic entities on the adventure map.
 */
sealed class AdventureMapEntity {
    abstract val coordinate: AdventureCoordinate

    data class Mine(
        override val coordinate: AdventureCoordinate,
        val resourceType: String,
        var isFlaggedByPlayer: Boolean = false,
        var isFlaggedByAi: Boolean = false
    ) : AdventureMapEntity()

    data class ResourceTreasure(
        override val coordinate: AdventureCoordinate,
        val resources: ResourceCost,
        var isCollected: Boolean = false
    ) : AdventureMapEntity()

    data class WanderingGuards(
        override val coordinate: AdventureCoordinate,
        val creatureName: String,
        val count: Int,
        val powerRating: Int,
        var isDefeated: Boolean = false
    ) : AdventureMapEntity()

    data class CastleStronghold(
        override val coordinate: AdventureCoordinate,
        val townName: String,
        val faction: Faction,
        var isPlayerOwned: Boolean = true
    ) : AdventureMapEntity()
}

/**
 * AI controlled roaming Hero or roaming army stack on the Adventure Map.
 */
data class AdventureAiHero(
    val id: String,
    val name: String,
    val faction: Faction,
    var position: AdventureCoordinate,
    var movementPoints: Int = 2000,
    val maxMovementPoints: Int = 2000,
    val armyPower: Int = 1200
) {
    fun resetMovement() {
        movementPoints = maxMovementPoints
    }
}

/**
 * Deterministic decision output for Adventure Map AI.
 */
sealed class AdventureAiDecision {
    data class Move(
        val path: List<AdventureCoordinate>,
        val pointsConsumed: Int,
        val targetEntity: AdventureMapEntity?
    ) : AdventureAiDecision()

    data class Interact(
        val coordinate: AdventureCoordinate,
        val entity: AdventureMapEntity,
        val actionDescription: String
    ) : AdventureAiDecision()

    object Rest : AdventureAiDecision()
}

/**
 * Deterministic Adventure Map AI evaluating strategic goals, mine flagging, and pathfinding.
 */
object AdventureMapAi {

    /**
     * Evaluates the map environment and calculates the optimal movement decision for an AI hero.
     */
    fun computeDecision(
        hero: AdventureAiHero,
        grid: AdventureMapGrid,
        entities: List<AdventureMapEntity>,
        playerCoord: AdventureCoordinate?,
        playerArmyPower: Int,
        terrainCatalog: Map<TerrainType, TerrainDefinition>
    ): AdventureAiDecision {
        if (hero.movementPoints <= 100) {
            return AdventureAiDecision.Rest
        }

        // Find candidate strategic objectives
        val activeEntities = entities.filter { entity ->
            when (entity) {
                is AdventureMapEntity.Mine -> !entity.isFlaggedByAi
                is AdventureMapEntity.ResourceTreasure -> !entity.isCollected
                is AdventureMapEntity.WanderingGuards -> !entity.isDefeated && entity.powerRating < hero.armyPower
                is AdventureMapEntity.CastleStronghold -> entity.isPlayerOwned
            }
        }

        var bestScore = Double.NEGATIVE_INFINITY
        var bestPath: List<AdventureCoordinate>? = null
        var bestTarget: AdventureMapEntity? = null
        var bestPathCost = 0

        // 1. Evaluate Paths to all valid map entities
        for (entity in activeEntities) {
            val pathResult = AStarPathfinder.findAdventurePath(
                grid = grid,
                startCoord = hero.position,
                goalCoord = entity.coordinate,
                availableMovementPoints = 99999,
                heroNativeFaction = hero.faction,
                terrainCatalog = terrainCatalog
            )

            if (pathResult.fullPath.isNotEmpty()) {
                val baseUtility = when (entity) {
                    is AdventureMapEntity.Mine -> 1200.0
                    is AdventureMapEntity.ResourceTreasure -> 600.0
                    is AdventureMapEntity.CastleStronghold -> 2000.0
                    is AdventureMapEntity.WanderingGuards -> 400.0
                }

                // Closer objectives scored higher
                val score = baseUtility - (pathResult.totalMovementCost * 0.5)

                if (score > bestScore) {
                    bestScore = score
                    bestPath = pathResult.fullPath
                    bestTarget = entity
                    bestPathCost = pathResult.totalMovementCost
                }
            }
        }

        // 2. Evaluate Aggression towards player hero
        if (playerCoord != null && hero.armyPower > (playerArmyPower * 1.1)) {
            val huntPath = AStarPathfinder.findAdventurePath(
                grid = grid,
                startCoord = hero.position,
                goalCoord = playerCoord,
                availableMovementPoints = 99999,
                heroNativeFaction = hero.faction,
                terrainCatalog = terrainCatalog
            )

            if (huntPath.fullPath.isNotEmpty()) {
                val huntScore = 1500.0 - (huntPath.totalMovementCost * 0.4)
                if (huntScore > bestScore) {
                    bestScore = huntScore
                    bestPath = huntPath.fullPath
                    bestTarget = null
                    bestPathCost = huntPath.totalMovementCost
                }
            }
        }

        if (bestPath != null && bestPath.size > 1) {
            // Traverse as many steps as affordable with current movement points
            var accumulatedCost = 0
            val subPath = mutableListOf(hero.position)

            for (i in 1 until bestPath.size) {
                val step = bestPath[i]
                val prev = bestPath[i - 1]
                val isDiag = kotlin.math.abs(step.x - prev.x) > 0 && kotlin.math.abs(step.y - prev.y) > 0
                val stepCost = grid.calculateTileCost(step, isDiag, hero.faction, terrainCatalog)

                if (accumulatedCost + stepCost <= hero.movementPoints) {
                    accumulatedCost += stepCost
                    subPath.add(step)
                } else {
                    break
                }
            }

            if (subPath.size > 1) {
                return AdventureAiDecision.Move(
                    path = subPath,
                    pointsConsumed = accumulatedCost,
                    targetEntity = bestTarget
                )
            }
        }

        return AdventureAiDecision.Rest
    }

    /**
     * Executes the calculated decision, updating hero state and entity interactions.
     */
    fun executeDecision(
        hero: AdventureAiHero,
        decision: AdventureAiDecision
    ): String {
        return when (decision) {
            is AdventureAiDecision.Move -> {
                hero.position = decision.path.last()
                hero.movementPoints = kotlin.math.max(0, hero.movementPoints - decision.pointsConsumed)

                // Check if arrived at target entity
                val entity = decision.targetEntity
                if (entity != null && hero.position == entity.coordinate) {
                    when (entity) {
                        is AdventureMapEntity.Mine -> {
                            entity.isFlaggedByAi = true
                            entity.isFlaggedByPlayer = false
                            "AI Hero ${hero.name} captured and flagged the ${entity.resourceType} mine!"
                        }
                        is AdventureMapEntity.ResourceTreasure -> {
                            entity.isCollected = true
                            "AI Hero ${hero.name} gathered resource cache (${entity.resources.summary()})!"
                        }
                        is AdventureMapEntity.WanderingGuards -> {
                            entity.isDefeated = true
                            "AI Hero ${hero.name} engaged and defeated ${entity.count} ${entity.creatureName} guards!"
                        }
                        is AdventureMapEntity.CastleStronghold -> {
                            entity.isPlayerOwned = false
                            "AI Hero ${hero.name} captured town ${entity.townName}!"
                        }
                    }
                } else {
                    "AI Hero ${hero.name} marched to ${hero.position} (MP left: ${hero.movementPoints})"
                }
            }
            is AdventureAiDecision.Interact -> {
                decision.actionDescription
            }
            is AdventureAiDecision.Rest -> {
                "AI Hero ${hero.name} rests and awaits the next morning."
            }
        }
    }
}
