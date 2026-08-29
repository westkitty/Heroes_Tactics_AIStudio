package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream

/**
 * Result of schema validation containing warnings or validation failure errors.
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

/**
 * Exception thrown when externalized JSON configuration fails validation.
 */
class SchemaValidationException(
    val validationErrors: List<String>
) : RuntimeException("Schema validation failed:\n" + validationErrors.joinToString("\n - ", prefix = " - "))

/**
 * Comprehensive schema parser and validation engine for game configurations.
 */
class DataParser {

    /**
     * Parses and validates a creatures JSON payload.
     */
    fun parseCreatures(jsonString: String): List<CreatureDefinition> {
        val errors = mutableListOf<String>()
        val json = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            throw SchemaValidationException(listOf("Invalid JSON format: ${e.message}"))
        }

        if (!json.has("creatures")) {
            throw SchemaValidationException(listOf("Missing root 'creatures' array"))
        }

        val array = json.getJSONArray("creatures")
        val creatures = mutableListOf<CreatureDefinition>()
        val seenIds = mutableSetOf<String>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val id = obj.optString("id", "").trim()
            if (id.isEmpty()) {
                errors.add("Creature at index $i is missing 'id'")
                continue
            }
            if (seenIds.contains(id)) {
                errors.add("Duplicate creature id '$id' at index $i")
            }
            seenIds.add(id)

            val name = obj.optString("name", id)
            val factionStr = obj.optString("faction", "NEUTRAL")
            val faction = try {
                Faction.valueOf(factionStr.uppercase())
            } catch (e: Exception) {
                errors.add("Creature '$id' has invalid faction '$factionStr'")
                Faction.NEUTRAL
            }

            val tier = obj.optInt("tier", 1)
            if (tier !in 1..7) {
                errors.add("Creature '$id' tier $tier out of range [1..7]")
            }

            val attack = obj.optInt("attack", -1)
            if (attack < 0) errors.add("Creature '$id' attack ($attack) must be >= 0")

            val defense = obj.optInt("defense", -1)
            if (defense < 0) errors.add("Creature '$id' defense ($defense) must be >= 0")

            val minDamage = obj.optInt("minDamage", -1)
            val maxDamage = obj.optInt("maxDamage", -1)
            if (minDamage <= 0) errors.add("Creature '$id' minDamage ($minDamage) must be > 0")
            if (maxDamage < minDamage) errors.add("Creature '$id' maxDamage ($maxDamage) < minDamage ($minDamage)")

            val health = obj.optInt("health", -1)
            if (health <= 0) errors.add("Creature '$id' health ($health) must be > 0")

            val speed = obj.optInt("speed", -1)
            if (speed <= 0) errors.add("Creature '$id' speed ($speed) must be > 0")

            val growth = obj.optInt("growth", 0)
            if (growth < 0) errors.add("Creature '$id' growth ($growth) must be >= 0")

            val costObj = obj.optJSONObject("cost") ?: JSONObject()
            val cost = ResourceCost(
                gold = costObj.optInt("gold", 0),
                wood = costObj.optInt("wood", 0),
                ore = costObj.optInt("ore", 0),
                mercury = costObj.optInt("mercury", 0),
                sulfur = costObj.optInt("sulfur", 0),
                crystal = costObj.optInt("crystal", 0),
                gems = costObj.optInt("gems", 0)
            )

            val shots = obj.optInt("shots", 0)
            val isRanged = obj.optBoolean("isRanged", shots > 0)
            val isFlying = obj.optBoolean("isFlying", false)
            val isWide = obj.optBoolean("isWide", false)
            val retaliations = obj.optInt("retaliations", 1)

            val abilitiesArray = obj.optJSONArray("abilities") ?: JSONArray()
            val abilities = mutableListOf<CreatureAbility>()
            for (j in 0 until abilitiesArray.length()) {
                val abilityStr = abilitiesArray.getString(j)
                try {
                    abilities.add(CreatureAbility.valueOf(abilityStr.uppercase()))
                } catch (e: Exception) {
                    errors.add("Creature '$id' has unrecognized ability '$abilityStr'")
                }
            }

            val activeAbilitiesArray = obj.optJSONArray("activeAbilities") ?: JSONArray()
            val activeAbilities = mutableListOf<CreatureActiveAbility>()
            for (j in 0 until activeAbilitiesArray.length()) {
                val abObj = activeAbilitiesArray.getJSONObject(j)
                val abId = abObj.optString("id", "").trim()
                val abName = abObj.optString("name", abId).trim()
                if (abId.isEmpty()) {
                    errors.add("Creature '$id' active ability at index $j is missing 'id'")
                    continue
                }
                val effectTypeStr = abObj.optString("effectType", "HEAL")
                val effectType = try {
                    ActiveAbilityEffectType.valueOf(effectTypeStr.uppercase())
                } catch (e: Exception) {
                    errors.add("Creature '$id' active ability '$abId' has invalid effectType '$effectTypeStr'")
                    ActiveAbilityEffectType.HEAL
                }
                val targetTypeStr = abObj.optString("targetType", "ENEMY_STACK")
                val targetType = try {
                    AbilityTargetType.valueOf(targetTypeStr.uppercase())
                } catch (e: Exception) {
                    AbilityTargetType.ENEMY_STACK
                }
                val desc = abObj.optString("description", "")
                val cooldown = abObj.optInt("cooldownRounds", 0)
                val maxCharges = abObj.optInt("maxCharges", -1)
                val power = abObj.optInt("power", 0)
                val range = abObj.optInt("range", 0)

                activeAbilities.add(
                    CreatureActiveAbility(
                        id = abId,
                        name = abName,
                        effectType = effectType,
                        description = desc,
                        cooldownRounds = cooldown,
                        maxCharges = maxCharges,
                        targetType = targetType,
                        power = power,
                        range = range
                    )
                )
            }

            if (errors.isEmpty()) {
                creatures.add(
                    CreatureDefinition(
                        id = id,
                        name = name,
                        faction = faction,
                        tier = tier,
                        attack = attack,
                        defense = defense,
                        minDamage = minDamage,
                        maxDamage = maxDamage,
                        health = health,
                        speed = speed,
                        growth = growth,
                        cost = cost,
                        shots = shots,
                        isRanged = isRanged,
                        isFlying = isFlying,
                        isWide = isWide,
                        retaliations = retaliations,
                        abilities = abilities,
                        activeAbilities = activeAbilities
                    )
                )
            }
        }

        if (errors.isNotEmpty()) {
            throw SchemaValidationException(errors)
        }
        return creatures
    }

    /**
     * Parses and validates a spells JSON payload.
     */
    fun parseSpells(jsonString: String): List<SpellDefinition> {
        val errors = mutableListOf<String>()
        val json = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            throw SchemaValidationException(listOf("Invalid JSON format: ${e.message}"))
        }

        if (!json.has("spells")) {
            throw SchemaValidationException(listOf("Missing root 'spells' array"))
        }

        val array = json.getJSONArray("spells")
        val spells = mutableListOf<SpellDefinition>()
        val seenIds = mutableSetOf<String>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val id = obj.optString("id", "").trim()
            if (id.isEmpty()) {
                errors.add("Spell at index $i missing 'id'")
                continue
            }
            if (seenIds.contains(id)) {
                errors.add("Duplicate spell id '$id'")
            }
            seenIds.add(id)

            val name = obj.optString("name", id)
            val schoolStr = obj.optString("school", "ALL")
            val school = try {
                SpellSchool.valueOf(schoolStr.uppercase())
            } catch (e: Exception) {
                errors.add("Spell '$id' has invalid school '$schoolStr'")
                SpellSchool.ALL
            }

            val level = obj.optInt("level", 1)
            if (level !in 1..5) errors.add("Spell '$id' level $level out of range [1..5]")

            val manaCost = obj.optInt("manaCost", 0)
            if (manaCost < 0) errors.add("Spell '$id' mana cost must be >= 0")

            val typeStr = obj.optString("type", "DAMAGE_TARGET")
            val type = try {
                SpellType.valueOf(typeStr.uppercase())
            } catch (e: Exception) {
                errors.add("Spell '$id' has invalid type '$typeStr'")
                SpellType.DAMAGE_TARGET
            }

            val baseDamage = obj.optInt("baseDamage", 0)
            val powerMultiplier = obj.optInt("powerMultiplier", 0)
            val radius = obj.optInt("radius", 0)
            val statModifier = obj.optInt("statModifier", 0)
            val speedFactor = obj.optDouble("speedFactor", 1.0)
            val targetStat = obj.optString("targetStat", "")
            val baseHeal = obj.optInt("baseHeal", 0)
            val baseHealth = obj.optInt("baseHealth", 0)
            val description = obj.optString("description", "")

            if (errors.isEmpty()) {
                spells.add(
                    SpellDefinition(
                        id = id,
                        name = name,
                        school = school,
                        level = level,
                        manaCost = manaCost,
                        type = type,
                        baseDamage = baseDamage,
                        powerMultiplier = powerMultiplier,
                        radius = radius,
                        statModifier = statModifier,
                        speedFactor = speedFactor,
                        targetStat = targetStat,
                        baseHeal = baseHeal,
                        baseHealth = baseHealth,
                        description = description
                    )
                )
            }
        }

        if (errors.isNotEmpty()) {
            throw SchemaValidationException(errors)
        }
        return spells
    }

    /**
     * Parses and validates a terrain configuration JSON payload.
     */
    fun parseTerrain(jsonString: String): Map<TerrainType, TerrainDefinition> {
        val errors = mutableListOf<String>()
        val json = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            throw SchemaValidationException(listOf("Invalid JSON format: ${e.message}"))
        }

        if (!json.has("terrains")) {
            throw SchemaValidationException(listOf("Missing root 'terrains' array"))
        }

        val array = json.getJSONArray("terrains")
        val terrainMap = mutableMapOf<TerrainType, TerrainDefinition>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val idStr = obj.optString("id", "").trim()
            val terrainType = try {
                TerrainType.valueOf(idStr.uppercase())
            } catch (e: Exception) {
                errors.add("Terrain at index $i has invalid id '$idStr'")
                continue
            }

            val name = obj.optString("name", idStr)
            val baseCost = obj.optInt("baseMovementCost", 100)
            if (baseCost <= 0) {
                errors.add("Terrain '$idStr' base cost must be positive")
            }

            val factionStr = obj.optString("nativeFaction", "NONE")
            val faction = try {
                Faction.valueOf(factionStr.uppercase())
            } catch (e: Exception) {
                Faction.NONE
            }

            val isPassable = obj.optBoolean("isPassable", true)
            val tacticalMovementPenalty = obj.optInt("tacticalMovementPenalty", 0)
            val defenseBonus = obj.optInt("defenseBonus", 0)

            if (tacticalMovementPenalty < 0) {
                errors.add("Terrain '$idStr' tacticalMovementPenalty must be >= 0")
            }
            if (defenseBonus < 0) {
                errors.add("Terrain '$idStr' defenseBonus must be >= 0")
            }

            terrainMap[terrainType] = TerrainDefinition(
                id = terrainType,
                name = name,
                baseMovementCost = baseCost,
                nativeFaction = faction,
                isPassable = isPassable,
                tacticalMovementPenalty = tacticalMovementPenalty,
                defenseBonus = defenseBonus
            )
        }

        if (errors.isNotEmpty()) {
            throw SchemaValidationException(errors)
        }
        return terrainMap
    }

    /**
     * Parses and validates building tree configuration JSON payload.
     */
    fun parseBuildings(jsonString: String, creatureCatalog: List<CreatureDefinition> = emptyList()): List<BuildingDefinition> {
        val errors = mutableListOf<String>()
        val json = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            throw SchemaValidationException(listOf("Invalid JSON format: ${e.message}"))
        }

        if (!json.has("buildings")) {
            throw SchemaValidationException(listOf("Missing root 'buildings' array"))
        }

        val array = json.getJSONArray("buildings")
        val buildings = mutableListOf<BuildingDefinition>()
        val buildingIds = mutableSetOf<String>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val id = obj.optString("id", "").trim()
            if (id.isEmpty()) {
                errors.add("Building at index $i is missing 'id'")
                continue
            }
            if (buildingIds.contains(id)) {
                errors.add("Duplicate building id '$id'")
            }
            buildingIds.add(id)

            val name = obj.optString("name", id)
            val factionStr = obj.optString("faction", "CASTLE")
            val faction = try {
                Faction.valueOf(factionStr.uppercase())
            } catch (e: Exception) {
                Faction.CASTLE
            }

            val costObj = obj.optJSONObject("cost") ?: JSONObject()
            val cost = ResourceCost(
                gold = costObj.optInt("gold", 0),
                wood = costObj.optInt("wood", 0),
                ore = costObj.optInt("ore", 0),
                mercury = costObj.optInt("mercury", 0),
                sulfur = costObj.optInt("sulfur", 0),
                crystal = costObj.optInt("crystal", 0),
                gems = costObj.optInt("gems", 0)
            )

            val prereqArray = obj.optJSONArray("prerequisites") ?: JSONArray()
            val prereqs = mutableListOf<String>()
            for (j in 0 until prereqArray.length()) {
                prereqs.add(prereqArray.getString(j))
            }

            val dailyIncome = obj.optInt("dailyIncome", 0)
            val moraleBonus = obj.optInt("moraleBonus", 0)
            val creatureId = if (obj.has("creatureId")) obj.getString("creatureId") else null
            val dwellingTier = if (obj.has("dwellingTier")) obj.getInt("dwellingTier") else null
            val growth = obj.optInt("growth", 0)

            val categoryStr = obj.optString("category", "UTILITY")
            val category = try {
                BuildingCategory.valueOf(categoryStr.uppercase())
            } catch (e: Exception) {
                BuildingCategory.UTILITY
            }

            buildings.add(
                BuildingDefinition(
                    id = id,
                    name = name,
                    faction = faction,
                    cost = cost,
                    prerequisites = prereqs,
                    dailyIncome = dailyIncome,
                    moraleBonus = moraleBonus,
                    creatureId = creatureId,
                    dwellingTier = dwellingTier,
                    growth = growth,
                    category = category
                )
            )
        }

        // Validate building prerequisites existence
        for (b in buildings) {
            for (p in b.prerequisites) {
                if (!buildingIds.contains(p)) {
                    errors.add("Building '${b.id}' references non-existent prerequisite building '$p'")
                }
            }
            if (b.creatureId != null && creatureCatalog.isNotEmpty()) {
                val exists = creatureCatalog.any { it.id.equals(b.creatureId, ignoreCase = true) }
                if (!exists) {
                    errors.add("Building '${b.id}' references unknown creatureId '${b.creatureId}'")
                }
            }
        }

        if (errors.isNotEmpty()) {
            throw SchemaValidationException(errors)
        }
        return buildings
    }

    /**
     * Parses and validates a texture atlas configuration JSON payload.
     */
    fun parseAtlas(jsonString: String): TextureAtlasDefinition {
        val errors = mutableListOf<String>()
        val json = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            throw SchemaValidationException(listOf("Invalid JSON format: ${e.message}"))
        }

        if (!json.has("textureAtlas")) {
            throw SchemaValidationException(listOf("Missing root 'textureAtlas' object"))
        }

        val atlasObj = json.getJSONObject("textureAtlas")
        val texturePath = atlasObj.optString("texturePath", "sprites/creatures.png")
        val width = atlasObj.optInt("width", 1024)
        val height = atlasObj.optInt("height", 1024)

        if (width <= 0 || height <= 0) {
            errors.add("Invalid atlas dimensions ${width}x${height}")
        }

        val spritesArray = atlasObj.optJSONArray("sprites") ?: JSONArray()
        val sequences = mutableListOf<AnimationSequence>()

        for (i in 0 until spritesArray.length()) {
            val sObj = spritesArray.getJSONObject(i)
            val id = sObj.optString("id", "").trim()
            val creatureId = sObj.optString("creatureId", "").trim()
            val animStr = sObj.optString("animation", "IDLE")
            val animState = try {
                AnimationState.valueOf(animStr.uppercase())
            } catch (e: Exception) {
                errors.add("Sequence '$id' has invalid animation state '$animStr'")
                AnimationState.IDLE
            }

            val fps = sObj.optInt("frameRateFps", 10)
            val isLooping = sObj.optBoolean("isLooping", true)
            val impactFrame = sObj.optInt("impactFrameIndex", -1)

            val framesArray = sObj.optJSONArray("frames") ?: JSONArray()
            val frames = mutableListOf<SpriteFrame>()
            for (j in 0 until framesArray.length()) {
                val fObj = framesArray.getJSONObject(j)
                val x = fObj.optInt("x", 0)
                val y = fObj.optInt("y", 0)
                val w = fObj.optInt("w", 32)
                val h = fObj.optInt("h", 32)
                val px = fObj.optInt("pivotX", w / 2)
                val py = fObj.optInt("pivotY", h)
                val dur = fObj.optInt("durationMs", 1000 / fps.coerceAtLeast(1))

                if (x + w > width || y + h > height) {
                    errors.add("Frame $j of '$id' bounds exceed atlas dimensions (${x + w}x${y + h} > ${width}x${height})")
                }

                frames.add(
                    SpriteFrame(
                        x = x,
                        y = y,
                        width = w,
                        height = h,
                        pivotX = px,
                        pivotY = py,
                        durationMs = dur
                    )
                )
            }

            if (frames.isEmpty()) {
                errors.add("Sequence '$id' has no frames defined")
            }

            if (errors.isEmpty()) {
                sequences.add(
                    AnimationSequence(
                        id = id,
                        creatureId = creatureId,
                        animation = animState,
                        frameRateFps = fps,
                        isLooping = isLooping,
                        impactFrameIndex = impactFrame,
                        frames = frames
                    )
                )
            }
        }

        if (errors.isNotEmpty()) {
            throw SchemaValidationException(errors)
        }

        return TextureAtlasDefinition(
            texturePath = texturePath,
            width = width,
            height = height,
            sprites = sequences
        )
    }
}
