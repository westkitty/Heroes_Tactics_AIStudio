package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GameCatalog
import com.example.ui.AdventureScreen
import com.example.ui.DataCatalogScreen
import com.example.ui.EconomyScreen
import com.example.ui.TacticalCombatScreen
import com.example.ui.theme.CastleNavyDark
import com.example.ui.theme.CastleSurfaceDark
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.GoldSecondary
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize default catalogs
        GameCatalog.ensureInitialized()

        setContent {
            MyApplicationTheme {
                MainAppContainer()
            }
        }
    }
}

@Composable
fun MainAppContainer() {
    var selectedScreenIndex by remember { mutableIntStateOf(0) }

    val navItems = listOf(
        Triple("Combat", Icons.Default.PlayArrow, "nav_tab_combat"),
        Triple("Kingdom", Icons.Default.Home, "nav_tab_kingdom"),
        Triple("Map", Icons.Default.LocationOn, "nav_tab_map"),
        Triple("Catalog", Icons.Default.Info, "nav_tab_catalog")
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = CastleSurfaceDark,
                modifier = Modifier.fillMaxWidth().testTag("bottom_navigation_bar")
            ) {
                navItems.forEachIndexed { index, item ->
                    val isSelected = selectedScreenIndex == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedScreenIndex = index },
                        icon = {
                            Icon(
                                item.second,
                                contentDescription = item.first,
                                tint = if (isSelected) GoldPrimary else Color(0xFF94A3B8)
                            )
                        },
                        label = {
                            Text(
                                item.first,
                                color = if (isSelected) GoldPrimary else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color(0x33D4AF37)
                        ),
                        modifier = Modifier.testTag(item.third)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CastleNavyDark)
                .padding(innerPadding)
        ) {
            when (selectedScreenIndex) {
                0 -> TacticalCombatScreen()
                1 -> EconomyScreen()
                2 -> AdventureScreen()
                3 -> DataCatalogScreen()
            }
        }
    }
}
