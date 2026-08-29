package com.example.data

/**
 * Town building classification category.
 */
enum class BuildingCategory {
    HALL,
    FORTIFICATION,
    MAGE_GUILD,
    DWELLING,
    UTILITY,
    SPECIAL
}

/**
 * Town structure / building definition schema.
 */
data class BuildingDefinition(
    val id: String,
    val name: String,
    val faction: Faction,
    val cost: ResourceCost,
    val prerequisites: List<String> = emptyList(),
    val dailyIncome: Int = 0,
    val moraleBonus: Int = 0,
    val creatureId: String? = null,
    val dwellingTier: Int? = null,
    val growth: Int = 0,
    val category: BuildingCategory = BuildingCategory.UTILITY
) {
    init {
        require(id.isNotBlank()) { "Building id cannot be blank" }
        require(dailyIncome >= 0) { "Daily income must be non-negative" }
    }
}
