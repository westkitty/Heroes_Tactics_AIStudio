package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.MineType
import com.example.core.ResourceEconomy
import com.example.data.BuildingCategory
import com.example.data.BuildingDefinition
import com.example.data.CreatureDefinition
import com.example.data.GameCatalog
import com.example.data.ResourceCost
import com.example.ui.theme.CastleNavyDark
import com.example.ui.theme.CastleSurfaceDark
import com.example.ui.theme.CrimsonAccent
import com.example.ui.theme.EmeraldBuff
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.GoldSecondary
import com.example.ui.theme.ManaBlue

/**
 * Kingdom Treasury and Castle Construction / Recruitment Management Screen.
 */
@Composable
fun EconomyScreen(
    modifier: Modifier = Modifier
) {
    GameCatalog.ensureInitialized()

    val economy = remember {
        val eco = ResourceEconomy()
        eco.addMine(MineType.SAWMILL)
        eco.addMine(MineType.ORE_PIT)
        eco
    }

    var selectedBuildingForRecruit by remember { mutableStateOf<BuildingDefinition?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CastleNavyDark)
            .padding(12.dp)
    ) {
        // 1. TOP HEADER: Day / Week and End Turn Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "MONTH 1, WEEK ${economy.week}, DAY ${economy.dayOfWeek}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary,
                    modifier = Modifier.testTag("economy_calendar_day")
                )
                Text(
                    text = "Total Days Elapsed: ${economy.day}",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            Button(
                onClick = {
                    economy.tickDay(GameCatalog.buildings, GameCatalog.creatures)
                    refreshTrigger++
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                modifier = Modifier.testTag("end_turn_day_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Next Day", tint = CastleNavyDark)
                Spacer(modifier = Modifier.width(4.dp))
                Text("End Day", color = CastleNavyDark, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. RESOURCE TREASURY BAR
        TreasuryBar(stockpile = economy.stockpile)

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "CASTLE TOWN STRUCTURES",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = GoldSecondary
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 3. TOWN BUILDING LIST
        val constructed = economy.getConstructedBuildings()

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().testTag("building_tree_list")
        ) {
            items(GameCatalog.buildings) { building ->
                val isBuilt = constructed.contains(building.id)
                val canBuild = !isBuilt && building.prerequisites.all { constructed.contains(it) } && building.cost.isAffordable(economy.stockpile)

                BuildingCard(
                    building = building,
                    isBuilt = isBuilt,
                    canBuild = canBuild,
                    availableRecruits = if (building.creatureId != null) economy.getAvailableRecruits(building.creatureId) else 0,
                    onConstruct = {
                        economy.constructBuilding(building)
                        refreshTrigger++
                    },
                    onRecruit = {
                        selectedBuildingForRecruit = building
                    }
                )
            }
        }
    }

    // Recruitment Dialog
    selectedBuildingForRecruit?.let { building ->
        val creature = GameCatalog.creatures.firstOrNull { it.id == building.creatureId }
        if (creature != null) {
            val available = economy.getAvailableRecruits(creature.id)
            RecruitmentDialog(
                creature = creature,
                available = available,
                stockpile = economy.stockpile,
                onDismiss = { selectedBuildingForRecruit = null },
                onRecruit = { count ->
                    economy.recruitCreatures(creature, count)
                    selectedBuildingForRecruit = null
                    refreshTrigger++
                }
            )
        }
    }
}

@Composable
private fun TreasuryBar(stockpile: ResourceCost) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CastleSurfaceDark),
        modifier = Modifier.fillMaxWidth().testTag("resource_treasury_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            ResourceItem(name = "Gold", amount = stockpile.gold, color = GoldPrimary)
            ResourceItem(name = "Wood", amount = stockpile.wood, color = Color(0xFF854D0E))
            ResourceItem(name = "Ore", amount = stockpile.ore, color = Color(0xFF94A3B8))
            ResourceItem(name = "Gems", amount = stockpile.gems, color = ManaBlue)
            ResourceItem(name = "Crystal", amount = stockpile.crystal, color = CrimsonAccent)
        }
    }
}

@Composable
private fun ResourceItem(name: String, amount: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, fontSize = 9.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
        Text(amount.toString(), fontSize = 13.sp, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BuildingCard(
    building: BuildingDefinition,
    isBuilt: Boolean,
    canBuild: Boolean,
    availableRecruits: Int,
    onConstruct: () -> Unit,
    onRecruit: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isBuilt) Color(0xFF1E293B) else CastleNavyDark
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isBuilt) GoldPrimary.copy(alpha = 0.5f) else Color(0xFF334155)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = building.name,
                        fontWeight = FontWeight.Bold,
                        color = if (isBuilt) GoldPrimary else Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (isBuilt) {
                        Surface(
                            shape = CircleShape,
                            color = EmeraldBuff.copy(alpha = 0.2f),
                            modifier = Modifier.size(16.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Built", tint = EmeraldBuff, modifier = Modifier.padding(2.dp))
                        }
                    }
                }

                if (building.dailyIncome > 0) {
                    Text("+${building.dailyIncome} Gold / day", color = GoldSecondary, fontSize = 11.sp)
                }

                if (building.creatureId != null) {
                    val creatureName = building.creatureId.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    Text("Dwelling: $creatureName | Available: $availableRecruits", color = ManaBlue, fontSize = 11.sp)
                }

                if (!isBuilt) {
                    Text("Cost: ${building.cost.summary()}", color = Color(0xFF94A3B8), fontSize = 10.sp)
                    if (building.prerequisites.isNotEmpty()) {
                        Text("Requires: ${building.prerequisites.joinToString()}", color = Color(0xFF64748B), fontSize = 9.sp)
                    }
                }
            }

            if (isBuilt && building.creatureId != null) {
                Button(
                    onClick = onRecruit,
                    enabled = availableRecruits > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Recruit", fontSize = 11.sp, color = CastleNavyDark, fontWeight = FontWeight.Bold)
                }
            } else if (!isBuilt) {
                Button(
                    onClick = onConstruct,
                    enabled = canBuild,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Build", fontSize = 11.sp, color = CastleNavyDark, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RecruitmentDialog(
    creature: CreatureDefinition,
    available: Int,
    stockpile: ResourceCost,
    onDismiss: () -> Unit,
    onRecruit: (Int) -> Unit
) {
    var recruitCount by remember { mutableIntStateOf(available) }
    val totalCost = creature.cost * recruitCount
    val canAfford = totalCost.isAffordable(stockpile) && recruitCount > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recruit ${creature.name}", color = GoldPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Available: $available units", color = Color.White)
                Text("Unit Cost: ${creature.cost.summary()}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { if (recruitCount > 1) recruitCount-- }) { Text("-") }
                    Text("$recruitCount", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    Button(onClick = { if (recruitCount < available) recruitCount++ }) { Text("+") }
                    Button(onClick = { recruitCount = available }) { Text("Max") }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Total: ${totalCost.summary()}",
                    fontWeight = FontWeight.Bold,
                    color = if (canAfford) GoldSecondary else CrimsonAccent
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onRecruit(recruitCount) },
                enabled = canAfford,
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
            ) {
                Text("Confirm", color = CastleNavyDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) }
        },
        containerColor = CastleSurfaceDark
    )
}
