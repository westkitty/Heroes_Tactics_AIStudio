package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CreatureDefinition
import com.example.data.GameCatalog
import com.example.data.SpellDefinition
import com.example.ui.theme.CastleNavyDark
import com.example.ui.theme.CastleSurfaceDark
import com.example.ui.theme.CrimsonAccent
import com.example.ui.theme.EmeraldBuff
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.GoldSecondary
import com.example.ui.theme.ManaBlue

/**
 * Data Schema and JSON Catalog Inspector Screen.
 */
@Composable
fun DataCatalogScreen(
    modifier: Modifier = Modifier
) {
    GameCatalog.ensureInitialized()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Creatures (${GameCatalog.creatures.size})", "Spells (${GameCatalog.spells.size})", "Terrains", "Atlas")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CastleNavyDark)
            .padding(8.dp)
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = CastleSurfaceDark,
            contentColor = GoldPrimary,
            edgePadding = 0.dp,
            modifier = Modifier.fillMaxWidth().testTag("catalog_tabs")
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == index) GoldPrimary else Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (selectedTab) {
            0 -> CreaturesList(creatures = GameCatalog.creatures)
            1 -> SpellsList(spells = GameCatalog.spells)
            2 -> TerrainList()
            3 -> AtlasViewer()
        }
    }
}

@Composable
private fun CreaturesList(creatures: List<CreatureDefinition>) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().testTag("creatures_catalog_list")
    ) {
        items(creatures) { creature ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CastleSurfaceDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${creature.name} (Tier ${creature.tier} • ${creature.faction})",
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = creature.cost.summary(),
                            color = GoldSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("ATK: ${creature.attack}", color = CrimsonAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("DEF: ${creature.defense}", color = ManaBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("DMG: ${creature.minDamage}-${creature.maxDamage}", color = Color.White, fontSize = 11.sp)
                        Text("HP: ${creature.health}", color = EmeraldBuff, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("SPD: ${creature.speed}", color = GoldSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    if (creature.abilities.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Abilities: ${creature.abilities.joinToString()}",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpellsList(spells: List<SpellDefinition>) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(spells) { spell ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CastleSurfaceDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(spell.name, fontWeight = FontWeight.Bold, color = ManaBlue, fontSize = 14.sp)
                        Text("Level ${spell.level} • Mana: ${spell.manaCost}", color = GoldPrimary, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(spell.description, color = Color(0xFFCBD5E1), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun TerrainList() {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(GameCatalog.terrain.values.toList()) { terrain ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CastleSurfaceDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(terrain.name, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Native: ${terrain.nativeFaction}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                    Text(
                        text = if (terrain.isPassable) "${terrain.baseMovementCost} MP" else "IMPASSABLE",
                        color = if (terrain.isPassable) EmeraldBuff else CrimsonAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun AtlasViewer() {
    val atlas = GameCatalog.atlas
    if (atlas == null) {
        Text("No atlas loaded", color = Color.White)
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CastleSurfaceDark)) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Atlas File: ${atlas.texturePath}", fontWeight = FontWeight.Bold, color = GoldPrimary)
                    Text("Atlas Resolution: ${atlas.width} x ${atlas.height} px", color = Color.LightGray, fontSize = 11.sp)
                }
            }
        }

        items(atlas.sprites) { seq ->
            Card(colors = CardDefaults.cardColors(containerColor = CastleSurfaceDark), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Sequence: ${seq.id}", fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Creature: ${seq.creatureId} | State: ${seq.animation} | Frames: ${seq.frames.size} (${seq.totalDurationMs}ms)", color = ManaBlue, fontSize = 11.sp)
                }
            }
        }
    }
}
