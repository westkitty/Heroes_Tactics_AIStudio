package com.example.data

/**
 * Terrain surface types on the adventure map.
 */
enum class TerrainType {
    GRASS,
    DIRT,
    ROUGH,
    DESERT,
    SNOW,
    SWAMP,
    LAVA,
    SUBTERRANEAN,
    WATER,
    ROCK
}

/**
 * Road overlay modifiers on adventure tiles.
 */
enum class RoadType(val multiplier: Double) {
    NONE(1.0),
    DIRT_ROAD(0.75),
    GRAVEL_ROAD(0.65),
    COBBLESTONE_ROAD(0.50)
}

/**
 * Terrain data definition schema.
 */
data class TerrainDefinition(
    val id: TerrainType,
    val name: String,
    val baseMovementCost: Int,
    val nativeFaction: Faction = Faction.NONE,
    val isPassable: Boolean = true,
    val tacticalMovementPenalty: Int = 0,
    val defenseBonus: Int = 0
) {
    init {
        require(baseMovementCost > 0) { "Base movement cost must be positive" }
        require(tacticalMovementPenalty >= 0) { "Tactical movement penalty must be >= 0" }
        require(defenseBonus >= 0) { "Defense bonus must be >= 0" }
    }
}
