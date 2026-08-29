package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.CombatLogEvent
import com.example.core.CombatSide
import com.example.core.CombatSimulation
import com.example.core.CombatStack
import com.example.data.CreatureActiveAbility
import com.example.data.GameCatalog
import com.example.data.SpellDefinition
import com.example.engine.AStarPathfinder
import com.example.engine.HexCoordinate
import com.example.engine.ObstacleType
import com.example.engine.ai.TacticalCombatAi
import com.example.renderer.CombatRenderingBackend
import com.example.renderer.CombatSceneAdapter
import com.example.renderer.CombatVisualFx
import com.example.ui.theme.CastleNavyDark
import com.example.ui.theme.CastleSurfaceDark
import com.example.ui.theme.CrimsonAccent
import com.example.ui.theme.EmeraldBuff
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.GoldSecondary
import com.example.ui.theme.ManaBlue
import kotlinx.coroutines.delay
import kotlin.math.sqrt

/**
 * Interactive Hexagonal Tactical Battlefield Screen.
 */
@Composable
fun TacticalCombatScreen(
    modifier: Modifier = Modifier
) {
    GameCatalog.ensureInitialized()

    var simulation by remember {
        val sim = CombatSimulation()
        initDemoBattle(sim)
        mutableStateOf(sim)
    }

    var selectedHex by remember { mutableStateOf<HexCoordinate?>(null) }
    var hoveredHex by remember { mutableStateOf<HexCoordinate?>(null) }
    var pathOverlay by remember { mutableStateOf<List<HexCoordinate>>(emptyList()) }
    var visualFxList by remember { mutableStateOf<List<CombatVisualFx>>(emptyList()) }
    var showSpellDialog by remember { mutableStateOf(false) }
    var showAbilityDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showLoadDialog by remember { mutableStateOf(false) }
    var savedJsonState by remember { mutableStateOf("") }
    var animTick by remember { mutableIntStateOf(0) }
    var combatLogRefresh by remember { mutableIntStateOf(0) }
    var isAutoBattleActive by remember { mutableStateOf(false) }
    var isFogOfWarEnabled by remember { mutableStateOf(true) }

    // Synchronize Fog-of-War simulation setting
    LaunchedEffect(isFogOfWarEnabled) {
        simulation.fogOfWarEnabled = isFogOfWarEnabled
    }

    // Ticking animation loop for smooth sprite animations and fx cleanup
    LaunchedEffect(Unit) {
        while (true) {
            delay(125)
            animTick = (animTick + 1) % 1000
            // Prune expired visual FX
            if (visualFxList.isNotEmpty()) {
                visualFxList = visualFxList.filter { fx ->
                    animTick - fx.startTick < fx.durationTicks
                }
            }
        }
    }

    // Automated turn progression loop when Auto-Battle is enabled
    LaunchedEffect(isAutoBattleActive, animTick, simulation.isBattleOver) {
        if (isAutoBattleActive && !simulation.isBattleOver) {
            val active = simulation.turnQueue.currentActiveStack
            if (active != null && active.isAlive && !active.hasActed) {
                delay(350)
                if (isAutoBattleActive && !simulation.isBattleOver) {
                    executeAutoTurn(simulation)
                    combatLogRefresh++
                    selectedHex = null
                    pathOverlay = emptyList()
                }
            } else if (active != null && active.hasActed) {
                simulation.advanceTurn()
                combatLogRefresh++
            }
        } else if (simulation.isBattleOver) {
            isAutoBattleActive = false
        }
    }

    val activeStack = simulation.turnQueue.currentActiveStack
    val reachableHexes = remember(activeStack, simulation.turnQueue.roundNumber, combatLogRefresh) {
        if (activeStack != null && activeStack.isAlive) {
            val blocked = simulation.getBlockedHexes(activeStack.id)
            AStarPathfinder.getReachableHexes(
                grid = simulation.grid,
                startHex = activeStack.hex,
                speed = activeStack.effectiveSpeed,
                isWide = activeStack.definition.isWide,
                facing = activeStack.facing,
                blockedHexes = blocked,
                isFlying = activeStack.definition.isFlying,
                creatureFaction = activeStack.definition.faction,
                terrainCatalog = GameCatalog.terrain
            )
        } else {
            emptyMap()
        }
    }

    fun triggerAttackFx(attacker: CombatStack, defender: CombatStack, isRanged: Boolean, dmg: Int, crit: Boolean = false) {
        val newFx = mutableListOf<CombatVisualFx>()
        if (isRanged) {
            newFx.add(
                CombatVisualFx.Projectile(
                    id = "proj_${animTick}",
                    startTick = animTick,
                    durationTicks = 6,
                    fromHex = attacker.hex,
                    toHex = defender.hex
                )
            )
        } else {
            newFx.add(
                CombatVisualFx.MeleeSlash(
                    id = "slash_${animTick}",
                    startTick = animTick,
                    durationTicks = 4,
                    targetHex = defender.hex
                )
            )
        }
        newFx.add(
            CombatVisualFx.FloatingText(
                id = "dmg_${animTick}",
                startTick = animTick,
                durationTicks = 10,
                text = "-$dmg",
                color = if (crit) Color(0xFFF59E0B) else Color(0xFFEF4444),
                hex = defender.hex,
                isCrit = crit
            )
        )
        visualFxList = visualFxList + newFx
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CastleNavyDark)
            .padding(8.dp)
    ) {
        // --- 1. TOP HEADER: Round Info, Initiative Queue, Fog-of-War & Auto-Battle Controls ---
        HeaderAndTurnQueue(
            roundNumber = simulation.turnQueue.roundNumber,
            turnOrder = simulation.turnQueue.getUpcomingTurnOrder(),
            activeStack = activeStack,
            isFogOfWar = isFogOfWarEnabled,
            onToggleFogOfWar = {
                isFogOfWarEnabled = !isFogOfWarEnabled
                simulation.fogOfWarEnabled = isFogOfWarEnabled
                combatLogRefresh++
            },
            isAutoBattleActive = isAutoBattleActive,
            onToggleAutoBattle = {
                isAutoBattleActive = !isAutoBattleActive
            },
            onReset = {
                val newSim = CombatSimulation()
                initDemoBattle(newSim)
                newSim.fogOfWarEnabled = isFogOfWarEnabled
                simulation = newSim
                selectedHex = null
                pathOverlay = emptyList()
                visualFxList = emptyList()
                isAutoBattleActive = false
                combatLogRefresh++
            },
            onSave = {
                savedJsonState = simulation.exportStateToJson()
                showSaveDialog = true
            },
            onLoad = {
                showLoadDialog = true
            }
        )

        Spacer(modifier = Modifier.height(6.dp))

        // --- 2. ACTIVE UNIT CARD & STATS ---
        if (activeStack != null) {
            ActiveUnitCard(
                stack = activeStack,
                onOpenAbilities = { showAbilityDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // --- 3. HEX COMBAT CANVAS WITH HIGHLIGHTED OVERLAYS ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF0B132B), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            HexGridCanvas(
                simulation = simulation,
                selectedHex = selectedHex,
                hoveredHex = hoveredHex,
                pathOverlay = pathOverlay,
                visualFxList = visualFxList,
                animTick = animTick,
                onHexClicked = { hex ->
                    selectedHex = hex
                    val targetStack = simulation.getStackAtHex(hex)

                    if (activeStack != null && !activeStack.hasActed) {
                        if (targetStack != null && targetStack.side != activeStack.side) {
                            // Enemy target clicked
                            if (activeStack.isRanged && activeStack.shotsRemaining > 0) {
                                val beforeHp = targetStack.count * targetStack.definition.health - targetStack.damageTakenOnTopUnit
                                val dmgRes = simulation.executeRangedAttack(targetStack)
                                if (dmgRes != null) {
                                    val afterHp = targetStack.count * targetStack.definition.health - targetStack.damageTakenOnTopUnit
                                    triggerAttackFx(activeStack, targetStack, isRanged = true, dmg = kotlin.math.max(1, beforeHp - afterHp), crit = dmgRes.isCriticalLuck)
                                    simulation.advanceTurn()
                                    combatLogRefresh++
                                }
                            } else {
                                // Melee attack: check direct adjacency or step-to-adjacent
                                val dist = activeStack.hex.distanceTo(hex)
                                if (dist == 1) {
                                    val beforeHp = targetStack.count * targetStack.definition.health - targetStack.damageTakenOnTopUnit
                                    val dmgRes = simulation.executeMeleeAttack(targetStack)
                                    if (dmgRes != null) {
                                        val afterHp = targetStack.count * targetStack.definition.health - targetStack.damageTakenOnTopUnit
                                        triggerAttackFx(activeStack, targetStack, isRanged = false, dmg = kotlin.math.max(1, beforeHp - afterHp), crit = dmgRes.isCriticalLuck)
                                        simulation.advanceTurn()
                                        combatLogRefresh++
                                    }
                                } else {
                                    val adjReachable = hex.getAllNeighbors().firstOrNull { reachableHexes.containsKey(it) }
                                    if (adjReachable != null) {
                                        val beforeHp = targetStack.count * targetStack.definition.health - targetStack.damageTakenOnTopUnit
                                        val dmgRes = simulation.executeMeleeAttack(targetStack, attackFromHex = adjReachable)
                                        if (dmgRes != null) {
                                            val afterHp = targetStack.count * targetStack.definition.health - targetStack.damageTakenOnTopUnit
                                            triggerAttackFx(activeStack, targetStack, isRanged = false, dmg = kotlin.math.max(1, beforeHp - afterHp), crit = dmgRes.isCriticalLuck)
                                            simulation.advanceTurn()
                                            combatLogRefresh++
                                        }
                                    }
                                }
                            }
                        } else if (reachableHexes.containsKey(hex)) {
                            // Move to reachable hex
                            val blocked = simulation.getBlockedHexes(activeStack.id)
                            val pathRes = AStarPathfinder.findTacticalPath(
                                grid = simulation.grid,
                                startHex = activeStack.hex,
                                goalHex = hex,
                                isWide = activeStack.definition.isWide,
                                facing = activeStack.facing,
                                maxMovementRange = activeStack.effectiveSpeed,
                                blockedHexes = blocked,
                                isFlying = activeStack.definition.isFlying,
                                creatureFaction = activeStack.definition.faction,
                                terrainCatalog = GameCatalog.terrain
                            )
                            pathOverlay = pathRes.path

                            simulation.executeMove(hex)
                            simulation.turnQueue.finishStackAction()
                            simulation.advanceTurn()
                            combatLogRefresh++
                        }
                    }
                }
            )

            if (simulation.isBattleOver) {
                VictoryOverlay(
                    winner = simulation.winner ?: CombatSide.ATTACKER,
                    onRestart = {
                        val newSim = CombatSimulation()
                        initDemoBattle(newSim)
                        simulation = newSim
                        selectedHex = null
                        pathOverlay = emptyList()
                        visualFxList = emptyList()
                        combatLogRefresh++
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // --- 4. ACTION BAR ---
        ActionControls(
            activeStack = activeStack,
            isAutoBattleActive = isAutoBattleActive,
            onToggleAutoBattle = {
                isAutoBattleActive = !isAutoBattleActive
            },
            onWait = {
                simulation.waitTurn()
                simulation.advanceTurn()
                combatLogRefresh++
            },
            onDefend = {
                simulation.defendTurn()
                simulation.advanceTurn()
                combatLogRefresh++
            },
            onOpenAbilities = {
                showAbilityDialog = true
            },
            onCastSpell = {
                showSpellDialog = true
            },
            onAutoTurn = {
                executeAutoTurn(simulation)
                combatLogRefresh++
            }
        )

        Spacer(modifier = Modifier.height(6.dp))

        // --- 5. REAL-TIME BATTLE LOG COMPONENT ---
        BattleLogSection(
            events = simulation.battleLog,
            modifier = Modifier.height(115.dp)
        )
    }

    // Ability activation dialog
    if (showAbilityDialog && activeStack != null) {
        AbilitySelectionDialog(
            stack = activeStack,
            onDismiss = { showAbilityDialog = false },
            onExecute = { ability ->
                showAbilityDialog = false
                val targetHex = selectedHex
                val targetStack = if (targetHex != null) simulation.getStackAtHex(targetHex) else null
                val success = simulation.executeActiveAbility(ability.id, targetHex, targetStack)
                if (success) {
                    simulation.advanceTurn()
                    combatLogRefresh++
                }
            }
        )
    }

    // Spell casting dialog
    if (showSpellDialog) {
        SpellSelectionDialog(
            spells = GameCatalog.spells,
            onDismiss = { showSpellDialog = false },
            onCast = { spell ->
                showSpellDialog = false
                val targetHex = selectedHex ?: HexCoordinate(7, 5)
                simulation.castSpell(spell, spellPower = 5, targetHex)
                combatLogRefresh++
            }
        )
    }

    // Save Battle JSON Dialog
    if (showSaveDialog) {
        SaveBattleDialog(
            json = savedJsonState,
            onDismiss = { showSaveDialog = false }
        )
    }

    // Load / Resume Battle JSON Dialog
    if (showLoadDialog) {
        LoadBattleDialog(
            onDismiss = { showLoadDialog = false },
            onLoad = { json ->
                try {
                    val loaded = CombatSimulation.fromJson(json)
                    simulation = loaded
                    selectedHex = null
                    pathOverlay = emptyList()
                    visualFxList = emptyList()
                    combatLogRefresh++
                    showLoadDialog = false
                } catch (e: Exception) {
                    // Show parse error
                }
            }
        )
    }
}

@Composable
private fun HeaderAndTurnQueue(
    roundNumber: Int,
    turnOrder: List<CombatStack>,
    activeStack: CombatStack?,
    isFogOfWar: Boolean,
    onToggleFogOfWar: () -> Unit,
    isAutoBattleActive: Boolean,
    onToggleAutoBattle: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "R$roundNumber",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = GoldPrimary,
                modifier = Modifier.testTag("round_counter")
            )
            Spacer(modifier = Modifier.width(2.dp))
            IconButton(onClick = onToggleFogOfWar, modifier = Modifier.size(28.dp).testTag("fog_of_war_toggle")) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = if (isFogOfWar) "Fog of War: ON" else "Fog of War: OFF",
                    tint = if (isFogOfWar) GoldSecondary else Color(0xFF64748B),
                    modifier = Modifier.size(17.dp)
                )
            }
            IconButton(onClick = onReset, modifier = Modifier.size(28.dp).testTag("reset_battle_button")) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset Battle", tint = GoldSecondary, modifier = Modifier.size(17.dp))
            }
            IconButton(onClick = onSave, modifier = Modifier.size(28.dp).testTag("save_battle_button")) {
                Icon(Icons.Default.Star, contentDescription = "Save Session", tint = ManaBlue, modifier = Modifier.size(17.dp))
            }
            IconButton(onClick = onLoad, modifier = Modifier.size(28.dp).testTag("load_battle_button")) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Resume Session", tint = EmeraldBuff, modifier = Modifier.size(17.dp))
            }
        }

        // Horizontal turn order previews
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isAutoBattleActive) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF059669),
                    border = BorderStroke(1.dp, Color(0xFF34D399)),
                    modifier = Modifier.padding(1.dp).testTag("auto_battle_badge")
                ) {
                    Text(
                        text = "AUTO",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            turnOrder.take(8).forEach { stack ->
                val isActive = stack.id == activeStack?.id
                val sideColor = if (stack.side == CombatSide.ATTACKER) GoldPrimary else CrimsonAccent
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isActive) GoldSecondary else CastleSurfaceDark,
                    border = BorderStroke(1.dp, sideColor),
                    modifier = Modifier.padding(1.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stack.definition.name.take(4),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) CastleNavyDark else Color.White
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${stack.count}",
                            fontSize = 9.sp,
                            color = if (isActive) CastleNavyDark else sideColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveUnitCard(
    stack: CombatStack,
    onOpenAbilities: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CastleSurfaceDark),
        border = BorderStroke(1.dp, if (stack.side == CombatSide.ATTACKER) GoldPrimary else CrimsonAccent),
        modifier = Modifier.fillMaxWidth().testTag("active_unit_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(if (stack.side == CombatSide.ATTACKER) GoldPrimary else CrimsonAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "T${stack.definition.tier}",
                        fontWeight = FontWeight.Bold,
                        color = CastleNavyDark,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "${stack.definition.name} (${stack.count})",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "HP: ${stack.definition.health - stack.damageTakenOnTopUnit}/${stack.definition.health}  |  ${stack.side}",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                StatBadge(label = "ATK", value = stack.effectiveAttack.toString(), color = CrimsonAccent)
                StatBadge(label = "DEF", value = stack.effectiveDefense.toString(), color = ManaBlue)
                StatBadge(label = "SPD", value = stack.effectiveSpeed.toString(), color = EmeraldBuff)
                if (stack.isRanged) {
                    StatBadge(label = "SHT", value = stack.shotsRemaining.toString(), color = GoldSecondary)
                }
                if (stack.definition.activeAbilities.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF7C3AED).copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, Color(0xFFA78BFA)),
                        modifier = Modifier.clickable { onOpenAbilities() }
                    ) {
                        Text(
                            text = "ABILITY",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC4B5FD),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBadge(label: String, value: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, color)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 7.sp, color = color, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HexGridCanvas(
    simulation: CombatSimulation,
    selectedHex: HexCoordinate?,
    hoveredHex: HexCoordinate?,
    pathOverlay: List<HexCoordinate>,
    visualFxList: List<CombatVisualFx>,
    animTick: Int,
    onHexClicked: (HexCoordinate) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val clicked = calculateHexAtOffset(offset, size.width.toFloat(), size.height.toFloat())
                    if (clicked != null && HexCoordinate.isValidGridHex(clicked.col, clicked.row)) {
                        onHexClicked(clicked)
                    }
                }
            }
    ) {
        val gridWidth = HexCoordinate.GRID_WIDTH
        val gridHeight = HexCoordinate.GRID_HEIGHT

        // Calculate hex tile dimensions to fit canvas
        val hexRadius = size.width / (gridWidth * 1.75f + 1f)
        val hexWidth = hexRadius * sqrt(3f)
        val vertSpacing = hexRadius * 1.5f

        val startX = (size.width - (gridWidth * hexWidth)) / 2f
        val startY = (size.height - (gridHeight * vertSpacing)) / 2f

        val snapshot = CombatSceneAdapter.createSnapshot(
            simulation = simulation,
            selectedHex = selectedHex,
            hoveredHex = hoveredHex,
            pathOverlay = pathOverlay,
            visualFx = visualFxList,
            currentTick = animTick
        )

        CombatRenderingBackend.renderScene(
            drawScope = this,
            scene = snapshot,
            hexRadius = hexRadius,
            originOffset = Offset(startX, startY),
            textMeasurer = textMeasurer
        )
    }
}

private fun calculateHexAtOffset(offset: Offset, canvasW: Float, canvasH: Float): HexCoordinate? {
    val gridWidth = HexCoordinate.GRID_WIDTH
    val gridHeight = HexCoordinate.GRID_HEIGHT
    val hexRadius = canvasW / (gridWidth * 1.75f + 1f)
    val hexWidth = hexRadius * sqrt(3f)
    val vertSpacing = hexRadius * 1.5f

    val startX = (canvasW - (gridWidth * hexWidth)) / 2f
    val startY = (canvasH - (gridHeight * vertSpacing)) / 2f

    var closest: HexCoordinate? = null
    var minDist = Float.MAX_VALUE

    for (r in 0 until gridHeight) {
        for (c in 0 until gridWidth) {
            val hex = HexCoordinate(c, r)
            val center = CombatRenderingBackend.calculateHexCenter(hex, hexRadius, Offset(startX, startY))
            val dx = offset.x - center.x
            val dy = offset.y - center.y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < hexRadius && dist < minDist) {
                minDist = dist
                closest = hex
            }
        }
    }
    return closest
}

@Composable
private fun ActionControls(
    activeStack: CombatStack?,
    isAutoBattleActive: Boolean,
    onToggleAutoBattle: () -> Unit,
    onWait: () -> Unit,
    onDefend: () -> Unit,
    onOpenAbilities: () -> Unit,
    onCastSpell: () -> Unit,
    onAutoTurn: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Button(
            onClick = onToggleAutoBattle,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isAutoBattleActive) Color(0xFF059669) else CastleSurfaceDark
            ),
            border = BorderStroke(1.dp, if (isAutoBattleActive) Color(0xFF34D399) else GoldSecondary),
            modifier = Modifier.weight(1.15f).testTag("auto_battle_toggle")
        ) {
            Text(
                if (isAutoBattleActive) "AUTO ON" else "AUTO",
                fontSize = 10.sp,
                color = if (isAutoBattleActive) Color.White else GoldSecondary,
                fontWeight = FontWeight.Bold
            )
        }

        Button(
            onClick = onWait,
            enabled = activeStack != null && !activeStack.hasWaited,
            colors = ButtonDefaults.buttonColors(containerColor = CastleSurfaceDark),
            modifier = Modifier.weight(0.9f).testTag("action_wait_button")
        ) {
            Text("Wait", fontSize = 10.sp, color = GoldSecondary)
        }

        Button(
            onClick = onDefend,
            enabled = activeStack != null,
            colors = ButtonDefaults.buttonColors(containerColor = CastleSurfaceDark),
            modifier = Modifier.weight(0.9f).testTag("action_defend_button")
        ) {
            Text("Def", fontSize = 10.sp, color = ManaBlue)
        }

        if (activeStack != null && activeStack.definition.activeAbilities.isNotEmpty()) {
            Button(
                onClick = onOpenAbilities,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B21B6)),
                modifier = Modifier.weight(1.05f).testTag("action_ability_button")
            ) {
                Text("Ability", fontSize = 10.sp, color = Color(0xFFDDD6FE), fontWeight = FontWeight.Bold)
            }
        }

        Button(
            onClick = onCastSpell,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4C1D95)),
            modifier = Modifier.weight(0.95f).testTag("action_spell_button")
        ) {
            Text("Spell", fontSize = 10.sp, color = Color.White)
        }

        Button(
            onClick = onAutoTurn,
            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
            modifier = Modifier.weight(0.95f).testTag("action_auto_button")
        ) {
            Text("Step", fontSize = 10.sp, color = CastleNavyDark, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Real-Time Battle Log UI Component showing damage, morale triggers, active abilities, and turn transitions.
 */
@Composable
private fun BattleLogSection(
    events: List<CombatLogEvent>,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CastleSurfaceDark),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = modifier.fillMaxWidth().testTag("battle_log_container")
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TACTICAL COMBAT LOG",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldSecondary
                )
                Text(
                    text = "${events.size} events",
                    fontSize = 9.sp,
                    color = Color(0xFF64748B)
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                reverseLayout = true
            ) {
                items(events.reversed()) { event ->
                    val (badgeText, badgeColor, textColor, textContent) = when (event) {
                        is CombatLogEvent.MeleeAttacked -> Quadruple(
                            "MELEE",
                            CrimsonAccent,
                            Color(0xFFFCA5A5),
                            "[${event.attackerId}] attacked [${event.defenderId}] for ${event.damageResult.totalDamageDealt} dmg! (${event.damageResult.unitsKilled} slain)"
                        )
                        is CombatLogEvent.Retaliated -> Quadruple(
                            "RTL",
                            Color(0xFFEA580C),
                            Color(0xFFFDBA74),
                            "[${event.defenderId}] retaliated against [${event.attackerId}] for ${event.damageResult.totalDamageDealt} dmg!"
                        )
                        is CombatLogEvent.RangedShot -> Quadruple(
                            "SHOT",
                            Color(0xFFF59E0B),
                            Color(0xFFFDE68A),
                            "[${event.attackerId}] shot [${event.defenderId}] for ${event.damageResult.totalDamageDealt} dmg!"
                        )
                        is CombatLogEvent.ActiveAbilityUsed -> Quadruple(
                            "ABILITY",
                            Color(0xFF8B5CF6),
                            Color(0xFFDDD6FE),
                            "[${event.casterId}] cast ${event.abilityName} on ${event.targetDescription}: ${event.effectResult}"
                        )
                        is CombatLogEvent.SpellCast -> Quadruple(
                            "MAGIC",
                            ManaBlue,
                            Color(0xFFBAE6FD),
                            "${event.spellName} at (${event.targetHex.col},${event.targetHex.row}): ${event.effectDescription}"
                        )
                        is CombatLogEvent.StackMoved -> Quadruple(
                            "MOVE",
                            Color(0xFF64748B),
                            Color(0xFFCBD5E1),
                            "[${event.stackId}] moved (${event.fromHex.col},${event.fromHex.row}) -> (${event.toHex.col},${event.toHex.row})"
                        )
                        is CombatLogEvent.MoraleTriggered -> Quadruple(
                            "MORALE",
                            GoldPrimary,
                            GoldPrimary,
                            "High Morale! [${event.stackId}] is filled with holy vigor and gains a bonus action!"
                        )
                        is CombatLogEvent.TurnTransition -> Quadruple(
                            "TURN",
                            if (event.side == CombatSide.ATTACKER) GoldPrimary else CrimsonAccent,
                            Color.White,
                            "${event.side} [${event.stackName}] takes the initiative."
                        )
                        is CombatLogEvent.RoundStarted -> Quadruple(
                            "ROUND",
                            GoldSecondary,
                            GoldSecondary,
                            "--- Combat Round ${event.roundNumber} Begun ---"
                        )
                        is CombatLogEvent.StackDied -> Quadruple(
                            "DEATH",
                            CrimsonAccent,
                            CrimsonAccent,
                            "[${event.stackId}] was annihilated and collapsed in battle!"
                        )
                        is CombatLogEvent.StackWaited -> Quadruple(
                            "WAIT",
                            Color(0xFFEAB308),
                            Color(0xFFFEF08A),
                            "[${event.stackId}] holds position and waits."
                        )
                        is CombatLogEvent.StackDefended -> Quadruple(
                            "DEF",
                            ManaBlue,
                            Color(0xFF93C5FD),
                            "[${event.stackId}] assumes fortified defense (+20% DEF)."
                        )
                        is CombatLogEvent.Victory -> Quadruple(
                            "VICTORY",
                            GoldPrimary,
                            GoldPrimary,
                            "GLORIOUS VICTORY! The ${event.winningSide} army has conquered the battlefield!"
                        )
                    }

                    Row(
                        modifier = Modifier.padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = badgeColor.copy(alpha = 0.2f),
                            border = BorderStroke(0.5.dp, badgeColor),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = textContent,
                            fontSize = 10.sp,
                            color = textColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun VictoryOverlay(
    winner: CombatSide,
    onRestart: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDD000000)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CastleSurfaceDark),
            border = BorderStroke(2.dp, GoldPrimary),
            modifier = Modifier.padding(32.dp).testTag("victory_dialog")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (winner == CombatSide.ATTACKER) "HEROIC VICTORY!" else "DEFEAT!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (winner == CombatSide.ATTACKER) GoldPrimary else CrimsonAccent
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The ${winner.name} army has won the battle.",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRestart,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Text("Play Again", color = CastleNavyDark, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AbilitySelectionDialog(
    stack: CombatStack,
    onDismiss: () -> Unit,
    onExecute: (CreatureActiveAbility) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${stack.definition.name} Abilities", color = GoldPrimary, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(stack.definition.activeAbilities) { ability ->
                    val canUse = stack.canUseActiveAbility(ability)
                    val cooldown = stack.abilityCooldowns[ability.id] ?: 0
                    val charges = stack.abilityChargesRemaining[ability.id] ?: ability.maxCharges

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (canUse) CastleNavyDark else Color(0xFF1E293B).copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = canUse) { onExecute(ability) }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(ability.name, color = if (canUse) GoldPrimary else Color.Gray, fontWeight = FontWeight.Bold)
                                if (cooldown > 0) {
                                    Text("Cooldown: $cooldown rnd", color = CrimsonAccent, fontSize = 11.sp)
                                } else if (ability.maxCharges > 0) {
                                    Text("Charges: $charges/${ability.maxCharges}", color = ManaBlue, fontSize = 11.sp)
                                } else {
                                    Text("Ready", color = EmeraldBuff, fontSize = 11.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(ability.description, color = Color.LightGray, fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = Color.White) }
        },
        containerColor = CastleSurfaceDark
    )
}

@Composable
private fun SpellSelectionDialog(
    spells: List<SpellDefinition>,
    onDismiss: () -> Unit,
    onCast: (SpellDefinition) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Spellbook", color = GoldPrimary, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(spells) { spell ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CastleNavyDark),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCast(spell) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(spell.name, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("${spell.school} • Mana: ${spell.manaCost}", color = ManaBlue, fontSize = 11.sp)
                                Text(spell.description, color = Color.LightGray, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = Color.White) }
        },
        containerColor = CastleSurfaceDark
    )
}

@Composable
private fun SaveBattleDialog(
    json: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Combat Session JSON", color = GoldPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Current battle state serialized to JSON:",
                    color = Color.White,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                ) {
                    LazyColumn(modifier = Modifier.padding(6.dp)) {
                        item {
                            Text(json, fontSize = 9.5.sp, color = Color(0xFF38BDF8))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)) {
                Text("Done", color = CastleNavyDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {},
        containerColor = CastleSurfaceDark
    )
}

@Composable
private fun LoadBattleDialog(
    onDismiss: () -> Unit,
    onLoad: (String) -> Unit
) {
    var inputJson by remember { mutableStateOf("") }
    var parseError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resume Combat Session", color = GoldPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Paste a saved battle JSON string to resume tactical combat:",
                    color = Color.White,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = inputJson,
                    onValueChange = {
                        inputJson = it
                        parseError = null
                    },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = Color.White)
                )
                if (parseError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(parseError ?: "", color = CrimsonAccent, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (inputJson.isBlank()) {
                        parseError = "Please enter JSON state string"
                    } else {
                        try {
                            onLoad(inputJson)
                        } catch (e: Exception) {
                            parseError = "Invalid JSON: ${e.localizedMessage}"
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
            ) {
                Text("Resume Battle", color = CastleNavyDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) }
        },
        containerColor = CastleSurfaceDark
    )
}

private fun initDemoBattle(sim: CombatSimulation) {
    sim.grid.clearObstacles()
    sim.grid.setObstacle(HexCoordinate(7, 3), ObstacleType.ROCK)
    sim.grid.setObstacle(HexCoordinate(7, 7), ObstacleType.TREE_STUMP)
    sim.grid.setObstacle(HexCoordinate(8, 5), ObstacleType.LAVA_PIT)

    // Assign terrain features on specific hexes
    sim.grid.setTerrainAt(HexCoordinate(3, 4), com.example.data.TerrainType.SWAMP)
    sim.grid.setTerrainAt(HexCoordinate(3, 5), com.example.data.TerrainType.SWAMP)
    sim.grid.setTerrainAt(HexCoordinate(10, 4), com.example.data.TerrainType.ROUGH)
    sim.grid.setTerrainAt(HexCoordinate(10, 5), com.example.data.TerrainType.ROUGH)

    val pikeman = GameCatalog.getCreature("pikeman")
    val archer = GameCatalog.getCreature("archer")
    val archangel = GameCatalog.getCreature("archangel")
    val imp = GameCatalog.getCreature("imp")
    val cerberus = GameCatalog.getCreature("cerberus")
    val pitFiend = GameCatalog.getCreature("pit_fiend")
    val archDevil = GameCatalog.getCreature("arch_devil")

    val attackers = listOf(
        CombatStack("att_angel", 0, archangel, 2, side = CombatSide.ATTACKER, hex = HexCoordinate(0, 5)),
        CombatStack("att_archer", 1, archer, 15, side = CombatSide.ATTACKER, hex = HexCoordinate(0, 2)),
        CombatStack("att_pike", 2, pikeman, 25, side = CombatSide.ATTACKER, hex = HexCoordinate(1, 8))
    )

    val defenders = listOf(
        CombatStack("def_devil", 0, archDevil, 2, side = CombatSide.DEFENDER, hex = HexCoordinate(14, 5)),
        CombatStack("def_pit", 1, pitFiend, 4, side = CombatSide.DEFENDER, hex = HexCoordinate(13, 2)),
        CombatStack("def_cerb", 2, cerberus, 12, side = CombatSide.DEFENDER, hex = HexCoordinate(14, 8)),
        CombatStack("def_imp", 3, imp, 40, side = CombatSide.DEFENDER, hex = HexCoordinate(13, 9))
    )

    sim.setupBattle(attackers, defenders)
}

private fun executeAutoTurn(sim: CombatSimulation) {
    TacticalCombatAi.executeAiTurn(sim)
}

@Composable
private fun BorderStroke(width: androidx.compose.ui.unit.Dp, color: Color) = androidx.compose.foundation.BorderStroke(width, color)
