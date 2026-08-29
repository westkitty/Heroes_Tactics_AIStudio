package com.example.core

import com.example.data.GameCatalog
import com.example.data.TerrainType
import com.example.engine.FacingDirection
import com.example.engine.HexCoordinate
import com.example.engine.ObstacleType
import com.example.engine.TacticalCombatGrid
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes and deserializes the state of a Tactical Combat session to and from JSON,
 * enabling save, pause, resume, and deterministic replay of battles.
 */
object CombatStateSerializer {

    /**
     * Exports the complete simulation state to a formatted JSON string.
     */
    fun exportToJson(simulation: CombatSimulation): String {
        val root = JSONObject()
        root.put("version", "1.0.0")
        root.put("timestamp", System.currentTimeMillis())
        root.put("isBattleOver", simulation.isBattleOver)
        root.put("winner", simulation.winner?.name)
        root.put("roundNumber", simulation.turnQueue.roundNumber)
        root.put("activeStackId", simulation.turnQueue.currentActiveStack?.id)

        // 1. Grid & Terrain
        val gridObj = JSONObject()
        gridObj.put("width", simulation.grid.width)
        gridObj.put("height", simulation.grid.height)
        gridObj.put("battlefieldTerrain", simulation.grid.battlefieldTerrain.name)

        val obstaclesArray = JSONArray()
        for ((hex, type) in simulation.grid.getAllObstacles()) {
            val obs = JSONObject()
            obs.put("col", hex.col)
            obs.put("row", hex.row)
            obs.put("type", type.name)
            obstaclesArray.put(obs)
        }
        gridObj.put("obstacles", obstaclesArray)

        val hexTerrainsArray = JSONArray()
        for ((hex, terrain) in simulation.grid.getAllHexTerrains()) {
            val ht = JSONObject()
            ht.put("col", hex.col)
            ht.put("row", hex.row)
            ht.put("terrain", terrain.name)
            hexTerrainsArray.put(ht)
        }
        gridObj.put("hexTerrains", hexTerrainsArray)
        root.put("grid", gridObj)

        // 2. Combat Stacks
        val stacksArray = JSONArray()
        for (stack in simulation.getAllStacks()) {
            val stackObj = JSONObject()
            stackObj.put("id", stack.id)
            stackObj.put("slotIndex", stack.slotIndex)
            stackObj.put("creatureId", stack.definition.id)
            stackObj.put("count", stack.count)
            stackObj.put("initialCount", stack.initialCount)
            stackObj.put("side", stack.side.name)
            stackObj.put("hexCol", stack.hex.col)
            stackObj.put("hexRow", stack.hex.row)
            stackObj.put("facing", stack.facing.name)
            stackObj.put("damageTakenOnTopUnit", stack.damageTakenOnTopUnit)
            stackObj.put("shotsRemaining", stack.shotsRemaining)
            stackObj.put("retaliationsRemaining", stack.retaliationsRemaining)
            stackObj.put("hasActed", stack.hasActed)
            stackObj.put("hasWaited", stack.hasWaited)
            stackObj.put("isDefending", stack.isDefending)
            stackObj.put("moraleScore", stack.moraleScore)
            stackObj.put("luckScore", stack.luckScore)

            val buffsObj = JSONObject()
            for ((k, v) in stack.activeBuffs) {
                buffsObj.put(k, v)
            }
            stackObj.put("activeBuffs", buffsObj)

            val modifiersObj = JSONObject()
            for ((k, v) in stack.statModifiers) {
                modifiersObj.put(k, v)
            }
            stackObj.put("statModifiers", modifiersObj)

            val cooldownsObj = JSONObject()
            for ((k, v) in stack.abilityCooldowns) {
                cooldownsObj.put(k, v)
            }
            stackObj.put("abilityCooldowns", cooldownsObj)

            val chargesObj = JSONObject()
            for ((k, v) in stack.abilityChargesRemaining) {
                chargesObj.put(k, v)
            }
            stackObj.put("abilityChargesRemaining", chargesObj)

            stacksArray.put(stackObj)
        }
        root.put("stacks", stacksArray)

        // 3. Upcoming Queue Stack IDs
        val upcomingArray = JSONArray()
        for (upcoming in simulation.turnQueue.getUpcomingTurnOrder()) {
            upcomingArray.put(upcoming.id)
        }
        root.put("upcomingQueue", upcomingArray)

        // 4. Battle Log Events
        val logArray = JSONArray()
        for (event in simulation.battleLog) {
            val ev = JSONObject()
            when (event) {
                is CombatLogEvent.StackMoved -> {
                    ev.put("type", "StackMoved")
                    ev.put("stackId", event.stackId)
                    ev.put("fromCol", event.fromHex.col)
                    ev.put("fromRow", event.fromHex.row)
                    ev.put("toCol", event.toHex.col)
                    ev.put("toRow", event.toHex.row)
                }
                is CombatLogEvent.MeleeAttacked -> {
                    ev.put("type", "MeleeAttacked")
                    ev.put("attackerId", event.attackerId)
                    ev.put("defenderId", event.defenderId)
                    ev.put("damage", event.damageResult.totalDamageDealt)
                    ev.put("killed", event.damageResult.unitsKilled)
                }
                is CombatLogEvent.Retaliated -> {
                    ev.put("type", "Retaliated")
                    ev.put("defenderId", event.defenderId)
                    ev.put("attackerId", event.attackerId)
                    ev.put("damage", event.damageResult.totalDamageDealt)
                    ev.put("killed", event.damageResult.unitsKilled)
                }
                is CombatLogEvent.RangedShot -> {
                    ev.put("type", "RangedShot")
                    ev.put("attackerId", event.attackerId)
                    ev.put("defenderId", event.defenderId)
                    ev.put("damage", event.damageResult.totalDamageDealt)
                    ev.put("killed", event.damageResult.unitsKilled)
                }
                is CombatLogEvent.ActiveAbilityUsed -> {
                    ev.put("type", "ActiveAbilityUsed")
                    ev.put("casterId", event.casterId)
                    ev.put("abilityName", event.abilityName)
                    ev.put("targetDescription", event.targetDescription)
                    ev.put("effectResult", event.effectResult)
                }
                is CombatLogEvent.SpellCast -> {
                    ev.put("type", "SpellCast")
                    ev.put("spellName", event.spellName)
                    ev.put("targetCol", event.targetHex.col)
                    ev.put("targetRow", event.targetHex.row)
                    ev.put("effectDescription", event.effectDescription)
                }
                is CombatLogEvent.StackDied -> {
                    ev.put("type", "StackDied")
                    ev.put("stackId", event.stackId)
                    ev.put("stackName", event.stackName)
                }
                is CombatLogEvent.StackWaited -> {
                    ev.put("type", "StackWaited")
                    ev.put("stackId", event.stackId)
                }
                is CombatLogEvent.StackDefended -> {
                    ev.put("type", "StackDefended")
                    ev.put("stackId", event.stackId)
                }
                is CombatLogEvent.MoraleTriggered -> {
                    ev.put("type", "MoraleTriggered")
                    ev.put("stackId", event.stackId)
                    ev.put("isPositive", event.isPositive)
                }
                is CombatLogEvent.RoundStarted -> {
                    ev.put("type", "RoundStarted")
                    ev.put("roundNumber", event.roundNumber)
                }
                is CombatLogEvent.TurnTransition -> {
                    ev.put("type", "TurnTransition")
                    ev.put("activeStackId", event.activeStackId)
                    ev.put("stackName", event.stackName)
                    ev.put("side", event.side.name)
                }
                is CombatLogEvent.Victory -> {
                    ev.put("type", "Victory")
                    ev.put("winningSide", event.winningSide.name)
                }
            }
            logArray.put(ev)
        }
        root.put("battleLog", logArray)

        return root.toString(2)
    }

    /**
     * Imports and reconstructs a simulation session from a JSON string.
     */
    fun importFromJson(jsonString: String): CombatSimulation {
        val root = JSONObject(jsonString)

        // 1. Grid & Obstacles
        val gridObj = root.getJSONObject("grid")
        val width = gridObj.optInt("width", HexCoordinate.GRID_WIDTH)
        val height = gridObj.optInt("height", HexCoordinate.GRID_HEIGHT)
        val terrainName = gridObj.optString("battlefieldTerrain", "GRASS")
        val gridTerrain = try { TerrainType.valueOf(terrainName) } catch (e: Exception) { TerrainType.GRASS }

        val grid = TacticalCombatGrid(width, height, gridTerrain)

        val obstaclesArray = gridObj.optJSONArray("obstacles") ?: JSONArray()
        for (i in 0 until obstaclesArray.length()) {
            val obs = obstaclesArray.getJSONObject(i)
            val hex = HexCoordinate(obs.getInt("col"), obs.getInt("row"))
            val type = ObstacleType.valueOf(obs.getString("type"))
            if (grid.isInBounds(hex)) {
                grid.setObstacle(hex, type)
            }
        }

        val hexTerrainsArray = gridObj.optJSONArray("hexTerrains") ?: JSONArray()
        for (i in 0 until hexTerrainsArray.length()) {
            val ht = hexTerrainsArray.getJSONObject(i)
            val hex = HexCoordinate(ht.getInt("col"), ht.getInt("row"))
            val terrain = TerrainType.valueOf(ht.getString("terrain"))
            if (grid.isInBounds(hex)) {
                grid.setTerrainAt(hex, terrain)
            }
        }

        val simulation = CombatSimulation(grid = grid)

        // 2. Stacks
        val stacksArray = root.getJSONArray("stacks")
        val loadedStacks = mutableListOf<CombatStack>()

        for (i in 0 until stacksArray.length()) {
            val sObj = stacksArray.getJSONObject(i)
            val creatureId = sObj.getString("creatureId")
            val def = GameCatalog.getCreature(creatureId)

            val id = sObj.getString("id")
            val slot = sObj.getInt("slotIndex")
            val count = sObj.getInt("count")
            val initialCount = sObj.optInt("initialCount", count)
            val side = CombatSide.valueOf(sObj.getString("side"))
            val hex = HexCoordinate(sObj.getInt("hexCol"), sObj.getInt("hexRow"))
            val facing = FacingDirection.valueOf(sObj.optString("facing", if (side == CombatSide.ATTACKER) "EAST" else "WEST"))
            val dmgTop = sObj.optInt("damageTakenOnTopUnit", 0)
            val shots = sObj.optInt("shotsRemaining", def.shots)
            val retaliations = sObj.optInt("retaliationsRemaining", def.retaliations)
            val hasActed = sObj.optBoolean("hasActed", false)
            val hasWaited = sObj.optBoolean("hasWaited", false)
            val isDefending = sObj.optBoolean("isDefending", false)
            val morale = sObj.optInt("moraleScore", 0)
            val luck = sObj.optInt("luckScore", 0)

            val buffsMap = mutableMapOf<String, Int>()
            val buffsObj = sObj.optJSONObject("activeBuffs")
            buffsObj?.let {
                for (key in it.keys()) {
                    buffsMap[key] = it.getInt(key)
                }
            }

            val statModsMap = mutableMapOf<String, Int>()
            val modsObj = sObj.optJSONObject("statModifiers")
            modsObj?.let {
                for (key in it.keys()) {
                    statModsMap[key] = it.getInt(key)
                }
            }

            val cdsMap = mutableMapOf<String, Int>()
            val cdsObj = sObj.optJSONObject("abilityCooldowns")
            cdsObj?.let {
                for (key in it.keys()) {
                    cdsMap[key] = it.getInt(key)
                }
            }

            val chargesMap = mutableMapOf<String, Int>()
            val chargesObj = sObj.optJSONObject("abilityChargesRemaining")
            chargesObj?.let {
                for (key in it.keys()) {
                    chargesMap[key] = it.getInt(key)
                }
            }

            val stack = CombatStack(
                id = id,
                slotIndex = slot,
                definition = def,
                count = count,
                initialCount = initialCount,
                side = side,
                hex = hex,
                facing = facing,
                damageTakenOnTopUnit = dmgTop,
                shotsRemaining = shots,
                retaliationsRemaining = retaliations,
                hasActed = hasActed,
                hasWaited = hasWaited,
                isDefending = isDefending,
                moraleScore = morale,
                luckScore = luck,
                activeBuffs = buffsMap,
                statModifiers = statModsMap,
                abilityCooldowns = cdsMap,
                abilityChargesRemaining = chargesMap
            )
            loadedStacks.add(stack)
        }

        val attackers = loadedStacks.filter { it.side == CombatSide.ATTACKER }
        val defenders = loadedStacks.filter { it.side == CombatSide.DEFENDER }
        simulation.setupBattle(attackers, defenders)

        // Restore turn queue state
        val roundNumber = root.optInt("roundNumber", 1)
        val activeStackId = if (root.has("activeStackId") && !root.isNull("activeStackId")) root.getString("activeStackId") else null
        val upcomingArray = root.optJSONArray("upcomingQueue")
        val queueIds = mutableListOf<String>()
        if (upcomingArray != null) {
            for (i in 0 until upcomingArray.length()) {
                queueIds.add(upcomingArray.getString(i))
            }
        }

        simulation.turnQueue.restoreTurnOrderState(
            savedRoundNumber = roundNumber,
            allCombatStacks = loadedStacks,
            currentQueueStackIds = queueIds,
            waitingQueueStackIds = emptyList(),
            activeStackId = activeStackId
        )

        // Restore battle log
        val logArray = root.optJSONArray("battleLog")
        if (logArray != null) {
            simulation.battleLog.clear()
            for (i in 0 until logArray.length()) {
                val ev = logArray.getJSONObject(i)
                when (ev.optString("type")) {
                    "StackMoved" -> simulation.battleLog.add(
                        CombatLogEvent.StackMoved(
                            ev.getString("stackId"),
                            HexCoordinate(ev.getInt("fromCol"), ev.getInt("fromRow")),
                            HexCoordinate(ev.getInt("toCol"), ev.getInt("toRow")),
                            emptyList()
                        )
                    )
                    "MeleeAttacked" -> simulation.battleLog.add(
                        CombatLogEvent.MeleeAttacked(
                            ev.getString("attackerId"),
                            ev.getString("defenderId"),
                            DamageResult(0, 10000, ev.optInt("damage", 0), ev.optInt("killed", 0), 0, 0)
                        )
                    )
                    "Retaliated" -> simulation.battleLog.add(
                        CombatLogEvent.Retaliated(
                            ev.getString("defenderId"),
                            ev.getString("attackerId"),
                            DamageResult(0, 10000, ev.optInt("damage", 0), ev.optInt("killed", 0), 0, 0)
                        )
                    )
                    "RangedShot" -> simulation.battleLog.add(
                        CombatLogEvent.RangedShot(
                            ev.getString("attackerId"),
                            ev.getString("defenderId"),
                            DamageResult(0, 10000, ev.optInt("damage", 0), ev.optInt("killed", 0), 0, 0)
                        )
                    )
                    "ActiveAbilityUsed" -> simulation.battleLog.add(
                        CombatLogEvent.ActiveAbilityUsed(
                            ev.getString("casterId"),
                            ev.getString("abilityName"),
                            ev.optString("targetDescription", ""),
                            ev.optString("effectResult", "")
                        )
                    )
                    "SpellCast" -> simulation.battleLog.add(
                        CombatLogEvent.SpellCast(
                            ev.getString("spellName"),
                            HexCoordinate(ev.optInt("targetCol", 0), ev.optInt("targetRow", 0)),
                            ev.optString("effectDescription", "")
                        )
                    )
                    "StackDied" -> simulation.battleLog.add(
                        CombatLogEvent.StackDied(ev.getString("stackId"), ev.optString("stackName", ""))
                    )
                    "StackWaited" -> simulation.battleLog.add(
                        CombatLogEvent.StackWaited(ev.getString("stackId"))
                    )
                    "StackDefended" -> simulation.battleLog.add(
                        CombatLogEvent.StackDefended(ev.getString("stackId"))
                    )
                    "RoundStarted" -> simulation.battleLog.add(
                        CombatLogEvent.RoundStarted(ev.getInt("roundNumber"))
                    )
                    "TurnTransition" -> simulation.battleLog.add(
                        CombatLogEvent.TurnTransition(
                            ev.getString("activeStackId"),
                            ev.optString("stackName", ""),
                            CombatSide.valueOf(ev.getString("side"))
                        )
                    )
                    "Victory" -> simulation.battleLog.add(
                        CombatLogEvent.Victory(CombatSide.valueOf(ev.getString("winningSide")))
                    )
                }
            }
        }

        return simulation
    }
}
