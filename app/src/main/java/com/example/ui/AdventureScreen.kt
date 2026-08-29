package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Faction
import com.example.data.GameCatalog
import com.example.data.ResourceCost
import com.example.data.RoadType
import com.example.data.TerrainType
import com.example.engine.AStarPathfinder
import com.example.engine.AdventureCoordinate
import com.example.engine.AdventureMapGrid
import com.example.engine.AdventurePathResult
import com.example.engine.ai.AdventureAiHero
import com.example.engine.ai.AdventureAiDecision
import com.example.engine.ai.AdventureMapAi
import com.example.engine.ai.AdventureMapEntity
import com.example.ui.theme.CastleNavyDark
import com.example.ui.theme.CastleSurfaceDark
import com.example.ui.theme.CrimsonAccent
import com.example.ui.theme.EmeraldBuff
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.GoldSecondary
import com.example.ui.theme.ManaBlue

/**
 * 2D Adventure Map Explorer and A* Pathfinding Visualizer with AI Opponent.
 */
@Composable
fun AdventureScreen(
    modifier: Modifier = Modifier
) {
    GameCatalog.ensureInitialized()

    val mapWidth = 12
    val mapHeight = 12

    val grid = remember {
        val map = AdventureMapGrid(mapWidth, mapHeight, TerrainType.GRASS)
        // Add diverse terrains
        for (x in 2..5) {
            for (y in 2..4) {
                map.setTile(AdventureCoordinate(x, y), TerrainType.SWAMP)
            }
        }
        for (x in 7..10) {
            for (y in 1..3) {
                map.setTile(AdventureCoordinate(x, y), TerrainType.SNOW)
            }
        }
        for (x in 6..9) {
            for (y in 7..10) {
                map.setTile(AdventureCoordinate(x, y), TerrainType.DESERT)
            }
        }
        // Cobblestone Road from (0, 0) through (5, 0) to (5, 8)
        for (x in 0..5) {
            map.setTile(AdventureCoordinate(x, 0), TerrainType.GRASS, road = RoadType.COBBLESTONE_ROAD)
        }
        for (y in 1..8) {
            map.setTile(AdventureCoordinate(5, y), TerrainType.GRASS, road = RoadType.COBBLESTONE_ROAD)
        }
        // Rock obstacles
        map.setTile(AdventureCoordinate(3, 6), TerrainType.ROCK, isBlocked = true)
        map.setTile(AdventureCoordinate(4, 6), TerrainType.ROCK, isBlocked = true)
        map
    }

    var heroPos by remember { mutableStateOf(AdventureCoordinate(0, 0)) }
    var availableMovementPoints by remember { mutableIntStateOf(1600) }
    var selectedGoal by remember { mutableStateOf<AdventureCoordinate?>(null) }
    var currentPathResult by remember { mutableStateOf<AdventurePathResult?>(null) }

    // AI Opponent & Map Entities
    val aiHero = remember {
        AdventureAiHero(
            id = "ai_haart",
            name = "Lord Haart",
            faction = Faction.INFERNO,
            position = AdventureCoordinate(11, 11),
            movementPoints = 1800,
            maxMovementPoints = 2000,
            armyPower = 1400
        )
    }

    var mapEntities by remember {
        mutableStateOf<List<AdventureMapEntity>>(
            listOf(
                AdventureMapEntity.Mine(AdventureCoordinate(1, 4), "Sawmill (Wood)"),
                AdventureMapEntity.Mine(AdventureCoordinate(10, 8), "Gold Mine"),
                AdventureMapEntity.ResourceTreasure(AdventureCoordinate(8, 4), ResourceCost(gold = 1000, gems = 3)),
                AdventureMapEntity.WanderingGuards(AdventureCoordinate(6, 5), "Cerberus", 10, 600)
            )
        )
    }

    var aiStatusMessage by remember { mutableStateOf("AI Lord Haart is plotting movements...") }

    fun calculatePath(goal: AdventureCoordinate) {
        selectedGoal = goal
        currentPathResult = AStarPathfinder.findAdventurePath(
            grid = grid,
            startCoord = heroPos,
            goalCoord = goal,
            availableMovementPoints = availableMovementPoints,
            heroNativeFaction = Faction.CASTLE,
            terrainCatalog = GameCatalog.terrain,
            allowDiagonal = true
        )
    }

    fun stepAiTurn() {
        val decision = AdventureMapAi.computeDecision(
            hero = aiHero,
            grid = grid,
            entities = mapEntities,
            playerCoord = heroPos,
            playerArmyPower = 1500,
            terrainCatalog = GameCatalog.terrain
        )
        val resultLog = AdventureMapAi.executeDecision(aiHero, decision)
        aiStatusMessage = resultLog
        // Force recomposition
        mapEntities = mapEntities.toList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CastleNavyDark)
            .padding(12.dp)
    ) {
        // Top status row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ADVENTURE MAP EXPLORER",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary,
                    modifier = Modifier.testTag("adventure_map_header")
                )
                Text(
                    text = "Hero MP: $availableMovementPoints | AI MP: ${aiHero.movementPoints}",
                    color = ManaBlue,
                    fontSize = 12.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = { stepAiTurn() },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAccent),
                    modifier = Modifier.testTag("step_ai_adventure_button")
                ) {
                    Text("AI Turn", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        availableMovementPoints = 2000
                        aiHero.resetMovement()
                        selectedGoal?.let { calculatePath(it) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Text("New Day", fontSize = 11.sp, color = CastleNavyDark, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // AI Status message banner
        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = aiStatusMessage,
                color = GoldSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Adventure Grid Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF0B132B), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val tileW = size.width / mapWidth.toFloat()
                            val tileH = size.height / mapHeight.toFloat()
                            val col = (offset.x / tileW).toInt().coerceIn(0, mapWidth - 1)
                            val row = (offset.y / tileH).toInt().coerceIn(0, mapHeight - 1)
                            val clicked = AdventureCoordinate(col, row)

                            if (selectedGoal == clicked && currentPathResult != null && currentPathResult!!.reachablePath.isNotEmpty()) {
                                // Move hero along reachable path
                                val dest = currentPathResult!!.reachablePath.last()
                                availableMovementPoints = currentPathResult!!.remainingMovementPoints
                                heroPos = dest
                                selectedGoal = null
                                currentPathResult = null

                                // Check player entity interaction
                                mapEntities.forEach { entity ->
                                    if (entity.coordinate == dest) {
                                        when (entity) {
                                            is AdventureMapEntity.Mine -> {
                                                entity.isFlaggedByPlayer = true
                                                entity.isFlaggedByAi = false
                                            }
                                            is AdventureMapEntity.ResourceTreasure -> {
                                                entity.isCollected = true
                                            }
                                            is AdventureMapEntity.WanderingGuards -> {
                                                entity.isDefeated = true
                                            }
                                            else -> {}
                                        }
                                    }
                                }
                            } else {
                                calculatePath(clicked)
                            }
                        }
                    }
            ) {
                val tileW = size.width / mapWidth.toFloat()
                val tileH = size.height / mapHeight.toFloat()

                // 1. Draw Map Tiles
                for (y in 0 until mapHeight) {
                    for (x in 0 until mapWidth) {
                        val coord = AdventureCoordinate(x, y)
                        val tile = grid.getTile(coord)
                        val topLeft = Offset(x * tileW, y * tileH)
                        val tileSize = Size(tileW, tileH)

                        val tileColor = when (tile.terrain) {
                            TerrainType.GRASS -> Color(0xFF15803D)
                            TerrainType.SWAMP -> Color(0xFF365314)
                            TerrainType.SNOW -> Color(0xFFE2E8F0)
                            TerrainType.DESERT -> Color(0xFFCA8A04)
                            TerrainType.ROCK -> Color(0xFF334155)
                            else -> Color(0xFF1E293B)
                        }

                        drawRect(color = tileColor, topLeft = topLeft, size = tileSize)
                        drawRect(color = Color(0x22000000), topLeft = topLeft, size = tileSize, style = Stroke(1f))

                        // Road indicator
                        if (tile.road != RoadType.NONE) {
                            drawCircle(
                                color = Color(0xFFFDE047),
                                radius = tileW * 0.15f,
                                center = Offset(topLeft.x + tileW / 2f, topLeft.y + tileH / 2f)
                            )
                        }
                    }
                }

                // 2. Draw Interactive Map Entities
                for (entity in mapEntities) {
                    val center = Offset(entity.coordinate.x * tileW + tileW / 2f, entity.coordinate.y * tileH + tileH / 2f)
                    when (entity) {
                        is AdventureMapEntity.Mine -> {
                            val flagColor = if (entity.isFlaggedByPlayer) GoldPrimary else if (entity.isFlaggedByAi) CrimsonAccent else Color(0xFF94A3B8)
                            drawRect(color = Color(0xFF475569), topLeft = Offset(center.x - 10f, center.y - 10f), size = Size(20f, 20f))
                            drawCircle(color = flagColor, radius = 5f, center = Offset(center.x, center.y - 12f))
                        }
                        is AdventureMapEntity.ResourceTreasure -> {
                            if (!entity.isCollected) {
                                drawCircle(color = Color(0xFFFBBF24), radius = 8f, center = center)
                                drawCircle(color = Color(0xFF78350F), radius = 8f, center = center, style = Stroke(1.5f))
                            }
                        }
                        is AdventureMapEntity.WanderingGuards -> {
                            if (!entity.isDefeated) {
                                drawCircle(color = Color(0xFFDC2626), radius = 9f, center = center)
                                drawCircle(color = Color.White, radius = 4f, center = center)
                            }
                        }
                        else -> {}
                    }
                }

                // 3. Draw A* Path Overlay
                currentPathResult?.let { result ->
                    for (i in result.fullPath.indices) {
                        val coord = result.fullPath[i]
                        val isReachable = result.reachablePath.contains(coord)
                        val center = Offset(coord.x * tileW + tileW / 2f, coord.y * tileH + tileH / 2f)

                        drawCircle(
                            color = if (isReachable) EmeraldBuff else CrimsonAccent,
                            radius = tileW * 0.22f,
                            center = center
                        )

                        if (i > 0) {
                            val prevCoord = result.fullPath[i - 1]
                            val prevCenter = Offset(prevCoord.x * tileW + tileW / 2f, prevCoord.y * tileH + tileH / 2f)
                            drawLine(
                                color = if (isReachable) EmeraldBuff else CrimsonAccent,
                                start = prevCenter,
                                end = center,
                                strokeWidth = 3f
                            )
                        }
                    }
                }

                // 4. Draw Player Hero Token (Gold)
                val heroCenter = Offset(heroPos.x * tileW + tileW / 2f, heroPos.y * tileH + tileH / 2f)
                drawCircle(color = GoldPrimary, radius = tileW * 0.38f, center = heroCenter)
                drawCircle(color = CastleNavyDark, radius = tileW * 0.32f, center = heroCenter)
                drawCircle(color = GoldSecondary, radius = tileW * 0.18f, center = heroCenter)

                // 5. Draw AI Hero Token (Crimson)
                val aiCenter = Offset(aiHero.position.x * tileW + tileW / 2f, aiHero.position.y * tileH + tileH / 2f)
                drawCircle(color = CrimsonAccent, radius = tileW * 0.38f, center = aiCenter)
                drawCircle(color = CastleNavyDark, radius = tileW * 0.32f, center = aiCenter)
                drawCircle(color = Color(0xFFEF4444), radius = tileW * 0.18f, center = aiCenter)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Path info card
        Card(
            colors = CardDefaults.cardColors(containerColor = CastleSurfaceDark),
            modifier = Modifier.fillMaxWidth().testTag("path_info_card")
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                if (currentPathResult != null) {
                    val r = currentPathResult!!
                    Text(
                        text = "Path: ${r.fullPath.size - 1} steps | Total Cost: ${r.totalMovementCost} MP",
                        fontWeight = FontWeight.Bold,
                        color = if (r.isCompleteGoalReached) EmeraldBuff else GoldSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Reachable today: ${r.reachablePath.size - 1} steps (uses ${r.usedMovementPoints} MP). Tap goal again to move.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp
                    )
                } else {
                    Text(
                        text = "Tap any map tile to compute optimal A* route with terrain movement penalties.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
