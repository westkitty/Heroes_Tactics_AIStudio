package com.example.renderer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.assets.ProceduralSpriteRenderer
import com.example.core.CombatSide
import com.example.engine.FacingDirection
import com.example.engine.HexCoordinate
import com.example.engine.ObstacleType
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Minimal decoupled rendering backend for tactical hexagonal combat.
 * Strictly receives read-only scene data from simulation snapshots and renders visual elements
 * on Compose Canvas. Does NOT mutate or influence simulation state.
 */
object CombatRenderingBackend {

    // Palette
    val HexGridBorder = Color(0xFF1E293B)
    val HexTileBackground = Color(0xFF0F172A)
    val HexReachableGlow = Color(0x4410B981)
    val HexReachableBorder = Color(0xFF34D399)
    val HexAttackTargetGlow = Color(0x66DC2626)
    val HexAttackTargetBorder = Color(0xFFEF4444)
    val HexHoverGlow = Color(0x3338BDF8)
    val HexHoverBorder = Color(0xFF38BDF8)
    val HexSelectedBorder = Color(0xFFFBBF24)
    val PathLineColor = Color(0xFF60A5FA)
    val PathNodeColor = Color(0xFF93C5FD)

    // Obstacle Colors
    val RockColor = Color(0xFF64748B)
    val LogColor = Color(0xFF78350F)
    val CrystalColor = Color(0xFF38BDF8)
    val StumpColor = Color(0xFF451A03)

    // Terrain Base Colors
    fun getTerrainBaseColor(terrain: com.example.data.TerrainType): Color {
        return when (terrain) {
            com.example.data.TerrainType.GRASS -> Color(0xFF14241B)
            com.example.data.TerrainType.DIRT -> Color(0xFF261D17)
            com.example.data.TerrainType.ROUGH -> Color(0xFF242220)
            com.example.data.TerrainType.DESERT -> Color(0xFF2E2619)
            com.example.data.TerrainType.SNOW -> Color(0xFF1E293B)
            com.example.data.TerrainType.SWAMP -> Color(0xFF17261D)
            com.example.data.TerrainType.LAVA -> Color(0xFF2D1616)
            com.example.data.TerrainType.SUBTERRANEAN -> Color(0xFF1C1924)
            com.example.data.TerrainType.WATER -> Color(0xFF132238)
            com.example.data.TerrainType.ROCK -> Color(0xFF1F2937)
        }
    }

    /**
     * Calculates center point of a hex in canvas pixels.
     */
    fun calculateHexCenter(
        hex: HexCoordinate,
        hexRadius: Float,
        originOffset: Offset = Offset.Zero
    ): Offset {
        val width = sqrt(3.0).toFloat() * hexRadius
        val height = 2f * hexRadius * 0.75f
        val xOffset = if (hex.row % 2 == 1) width / 2f else 0f
        val cx = originOffset.x + (hex.col * width) + xOffset + (width / 2f)
        val cy = originOffset.y + (hex.row * height) + hexRadius
        return Offset(cx, cy)
    }

    /**
     * Builds a hexagonal polygon Path for a given center and radius.
     */
    fun buildHexagonPath(center: Offset, radius: Float): Path {
        val path = Path()
        for (i in 0 until 6) {
            val angleDeg = 60.0 * i - 30.0
            val angleRad = Math.toRadians(angleDeg)
            val x = center.x + (radius * cos(angleRad)).toFloat()
            val y = center.y + (radius * sin(angleRad)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    /**
     * Main entry point to render the entire tactical combat scene snapshot.
     */
    fun renderScene(
        drawScope: DrawScope,
        scene: CombatSceneSnapshot,
        hexRadius: Float,
        originOffset: Offset = Offset.Zero,
        textMeasurer: TextMeasurer? = null
    ) {
        drawScope.apply {
            // 1. Render Hex Grid & Terrain Backgrounds
            renderHexGrid(scene, hexRadius, originOffset)

            // 2. Render Reachable Movement Ranges & Attackable Target Overlays
            renderReachableAndTargetOverlays(scene, hexRadius, originOffset, textMeasurer)

            // 3. Render Pathfinding Trajectory Overlay
            renderPathOverlay(scene, hexRadius, originOffset)

            // 4. Render Obstacle Tokens
            renderObstacles(scene, hexRadius, originOffset)

            // 5. Render Units, Badges, and Health Bars
            renderUnitSprites(scene, hexRadius, originOffset, textMeasurer)

            // 6. Render Transient Visual Effects (Damage numbers, projectiles, slashes)
            renderVisualFx(scene, hexRadius, originOffset, textMeasurer)
        }
    }

    /**
     * Renders battlefield base hexagonal tiles with terrain-specific styling, borders, and Fog-of-War shroud.
     */
    private fun DrawScope.renderHexGrid(
        scene: CombatSceneSnapshot,
        hexRadius: Float,
        originOffset: Offset
    ) {
        for (r in 0 until scene.gridHeight) {
            for (c in 0 until scene.gridWidth) {
                val hex = HexCoordinate(c, r)
                val center = calculateHexCenter(hex, hexRadius, originOffset)
                val hexPath = buildHexagonPath(center, hexRadius * 0.96f)

                if (scene.fogOfWarEnabled) {
                    val isVisible = scene.visibleHexes.contains(hex)
                    val isExplored = scene.exploredHexes.contains(hex)

                    if (isVisible) {
                        val specificTerrain = scene.hexTerrains[hex] ?: scene.battlefieldTerrain
                        val tileBg = getTerrainBaseColor(specificTerrain)
                        drawPath(hexPath, color = tileBg, style = Fill)
                        drawPath(hexPath, color = HexGridBorder, style = Stroke(width = 1f))
                    } else if (isExplored) {
                        // Explored but currently shrouded in fog
                        val specificTerrain = scene.hexTerrains[hex] ?: scene.battlefieldTerrain
                        val tileBg = getTerrainBaseColor(specificTerrain)
                        val dimmedColor = Color(
                            red = tileBg.red * 0.35f,
                            green = tileBg.green * 0.35f,
                            blue = tileBg.blue * 0.35f,
                            alpha = 0.9f
                        )
                        drawPath(hexPath, color = dimmedColor, style = Fill)
                        drawPath(hexPath, color = HexGridBorder.copy(alpha = 0.25f), style = Stroke(width = 0.8f))
                    } else {
                        // Completely unrevealed in deep fog
                        drawPath(hexPath, color = Color(0xFF070D18), style = Fill)
                        drawPath(hexPath, color = Color(0x331E293B), style = Stroke(width = 0.5f))
                    }
                } else {
                    val specificTerrain = scene.hexTerrains[hex] ?: scene.battlefieldTerrain
                    val tileBg = getTerrainBaseColor(specificTerrain)

                    // Hex base tile
                    drawPath(hexPath, color = tileBg, style = Fill)
                    drawPath(hexPath, color = HexGridBorder, style = Stroke(width = 1f))
                }
            }
        }
    }

    /**
     * Renders highlighted hex overlays for reachable movement ranges and attackable targets based on unit stats.
     */
    private fun DrawScope.renderReachableAndTargetOverlays(
        scene: CombatSceneSnapshot,
        hexRadius: Float,
        originOffset: Offset,
        textMeasurer: TextMeasurer?
    ) {
        // 1. Draw Reachable Movement Range Overlays
        for ((hex, stepCost) in scene.reachableHexes) {
            val center = calculateHexCenter(hex, hexRadius, originOffset)
            val hexPath = buildHexagonPath(center, hexRadius * 0.94f)

            // Glowing fill and border for reachable hexes
            drawPath(hexPath, color = HexReachableGlow, style = Fill)
            drawPath(hexPath, color = HexReachableBorder, style = Stroke(width = 1.8f))

            // Step cost indicator dot or number in bottom right
            if (textMeasurer != null && stepCost > 0) {
                val costLayout = textMeasurer.measure(
                    text = "$stepCost",
                    style = TextStyle(
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xCC34D399)
                    )
                )
                drawText(
                    textLayoutResult = costLayout,
                    topLeft = Offset(
                        center.x + (hexRadius * 0.25f),
                        center.y + (hexRadius * 0.35f)
                    )
                )
            }
        }

        // 2. Draw Attackable Target Overlays
        val pulseFactor = (sin(scene.currentTick * 0.25) * 0.2 + 0.8).toFloat()
        for (hex in scene.attackableHexes) {
            val center = calculateHexCenter(hex, hexRadius, originOffset)
            val hexPath = buildHexagonPath(center, hexRadius * 0.95f)

            // Red warning aura for attackable enemies
            drawPath(hexPath, color = HexAttackTargetGlow.copy(alpha = 0.35f * pulseFactor), style = Fill)
            drawPath(hexPath, color = HexAttackTargetBorder, style = Stroke(width = 2.2f * pulseFactor))

            // Draw attack sword indicator on hex
            val crossSize = hexRadius * 0.28f
            drawLine(
                color = Color(0xFFFCA5A5),
                start = Offset(center.x - crossSize, center.y - crossSize),
                end = Offset(center.x + crossSize, center.y + crossSize),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFFCA5A5),
                start = Offset(center.x + crossSize, center.y - crossSize),
                end = Offset(center.x - crossSize, center.y + crossSize),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
        }

        // 3. Draw Hovered Hex Reticle
        scene.hoveredHex?.let { hovered ->
            if (hovered != scene.selectedHex) {
                val center = calculateHexCenter(hovered, hexRadius, originOffset)
                val hexPath = buildHexagonPath(center, hexRadius * 0.95f)
                drawPath(hexPath, color = HexHoverGlow, style = Fill)
                drawPath(hexPath, color = HexHoverBorder, style = Stroke(width = 1.5f))
            }
        }

        // 4. Draw Selected Hex Reticle (Double Ring)
        scene.selectedHex?.let { selected ->
            val center = calculateHexCenter(selected, hexRadius, originOffset)
            val hexPath1 = buildHexagonPath(center, hexRadius * 0.98f)
            val hexPath2 = buildHexagonPath(center, hexRadius * 0.88f)
            drawPath(hexPath1, color = HexSelectedBorder, style = Stroke(width = 2.5f))
            drawPath(hexPath2, color = HexSelectedBorder.copy(alpha = 0.5f), style = Stroke(width = 1f))
        }
    }

    /**
     * Visualizes the output of the A* pathfinding system with waypoints and trajectory lines.
     */
    private fun DrawScope.renderPathOverlay(
        scene: CombatSceneSnapshot,
        hexRadius: Float,
        originOffset: Offset
    ) {
        val path = scene.pathOverlay
        if (path.size < 2) return

        val points = path.map { calculateHexCenter(it, hexRadius, originOffset) }

        // Draw connecting path line
        for (i in 0 until points.size - 1) {
            drawLine(
                color = PathLineColor,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }

        // Draw waypoint node dots
        for ((index, pt) in points.withIndex()) {
            val isGoal = index == points.size - 1
            drawCircle(
                color = if (isGoal) Color(0xFFF59E0B) else PathNodeColor,
                radius = if (isGoal) 6f else 4f,
                center = pt
            )
        }
    }

    /**
     * Renders obstacle terrain tokens with geometric elevations.
     */
    private fun DrawScope.renderObstacles(
        scene: CombatSceneSnapshot,
        hexRadius: Float,
        originOffset: Offset
    ) {
        for ((hex, type) in scene.obstacles) {
            if (scene.fogOfWarEnabled && !scene.visibleHexes.contains(hex) && !scene.exploredHexes.contains(hex)) {
                // Completely hidden under Fog-of-War
                continue
            }
            val center = calculateHexCenter(hex, hexRadius, originOffset)
            when (type) {
                ObstacleType.ROCK -> {
                    // Jagged rock boulder
                    val rockPath = Path().apply {
                        moveTo(center.x - 14f, center.y + 10f)
                        lineTo(center.x - 8f, center.y - 12f)
                        lineTo(center.x + 6f, center.y - 15f)
                        lineTo(center.x + 15f, center.y - 2f)
                        lineTo(center.x + 12f, center.y + 12f)
                        close()
                    }
                    drawPath(rockPath, color = RockColor)
                    drawPath(rockPath, color = Color(0xFF94A3B8), style = Stroke(width = 1.5f))
                }
                ObstacleType.TREE_STUMP -> {
                    drawCircle(color = StumpColor, radius = 12f, center = center)
                    drawCircle(color = Color(0xFFB45309), radius = 6f, center = center)
                }
                ObstacleType.LAVA_PIT -> {
                    // Molten lava pit
                    drawCircle(color = Color(0xFFDC2626), radius = 13f, center = center)
                    drawCircle(color = Color(0xFFF59E0B), radius = 8f, center = center)
                }
                ObstacleType.QUICKSAND -> {
                    drawCircle(color = Color(0xFFD97706), radius = 12f, center = center)
                }
                ObstacleType.WALL -> {
                    drawRoundRect(
                        color = Color(0xFF64748B),
                        topLeft = Offset(center.x - 16f, center.y - 8f),
                        size = Size(32f, 16f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
                    )
                }
                ObstacleType.MOAT -> {
                    drawCircle(color = Color(0xFF2563EB), radius = 14f, center = center)
                }
            }
        }
    }

    /**
     * Renders creature sprites, health status fraction, stack count badges, and tactical stances.
     */
    private fun DrawScope.renderUnitSprites(
        scene: CombatSceneSnapshot,
        hexRadius: Float,
        originOffset: Offset,
        textMeasurer: TextMeasurer?
    ) {
        for (stack in scene.stacks) {
            val center = calculateHexCenter(stack.hex, hexRadius, originOffset)
            val spriteSize = hexRadius * 1.5f

            // 1. Active Unit Glow Halo
            if (stack.isActive) {
                drawCircle(
                    color = Color(0x44FBBF24),
                    radius = hexRadius * 0.85f,
                    center = center
                )
                drawCircle(
                    color = Color(0xFFFBBF24),
                    radius = hexRadius * 0.85f,
                    center = center,
                    style = Stroke(width = 2f)
                )
            }

            // 2. Unit Procedural Sprite
            ProceduralSpriteRenderer.drawCreature(
                drawScope = this,
                creatureId = stack.creatureId,
                state = stack.animationState,
                frameIndex = stack.frameIndex,
                isFacingEast = stack.facing == FacingDirection.EAST,
                sizePx = spriteSize
            )

            // 3. Health Bar Under Stack
            val barWidth = hexRadius * 1.1f
            val barHeight = 4f
            val barLeft = center.x - (barWidth / 2f)
            val barTop = center.y + (hexRadius * 0.55f)

            // Health Bar Background
            drawRect(
                color = Color(0xFF1E293B),
                topLeft = Offset(barLeft, barTop),
                size = Size(barWidth, barHeight)
            )

            // Health Bar Foreground
            val hpRatio = stack.healthRatio
            val hpColor = when {
                hpRatio > 0.6f -> Color(0xFF22C55E)
                hpRatio > 0.3f -> Color(0xFFF59E0B)
                else -> Color(0xFFEF4444)
            }
            drawRect(
                color = hpColor,
                topLeft = Offset(barLeft, barTop),
                size = Size(barWidth * hpRatio, barHeight)
            )
            drawRect(
                color = Color(0xFF0F172A),
                topLeft = Offset(barLeft, barTop),
                size = Size(barWidth, barHeight),
                style = Stroke(width = 1f)
            )

            // 4. Stack Count Badge
            val badgeWidth = 24f
            val badgeHeight = 14f
            val badgeX = center.x - (badgeWidth / 2f)
            val badgeY = center.y + (hexRadius * 0.68f)

            val badgeColor = if (stack.side == CombatSide.ATTACKER) Color(0xFF1E3A8A) else Color(0xFF7F1D1D)
            val borderColor = if (stack.isActive) Color(0xFFFBBF24) else Color(0xFFCBD5E1)

            drawRoundRect(
                color = badgeColor,
                topLeft = Offset(badgeX, badgeY),
                size = Size(badgeWidth, badgeHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
            )
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(badgeX, badgeY),
                size = Size(badgeWidth, badgeHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                style = Stroke(width = 1f)
            )

            // Badge text (if textMeasurer available)
            if (textMeasurer != null) {
                val textLayout = textMeasurer.measure(
                    text = "${stack.count}",
                    style = TextStyle(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(
                        badgeX + (badgeWidth - textLayout.size.width) / 2f,
                        badgeY + (badgeHeight - textLayout.size.height) / 2f
                    )
                )
            }

            // 5. Stance Badges (Defend / Wait)
            if (stack.isDefending) {
                // Shield icon badge on top right
                val iconX = center.x + (hexRadius * 0.45f)
                val iconY = center.y - (hexRadius * 0.5f)
                drawCircle(color = Color(0xFF0284C7), radius = 6f, center = Offset(iconX, iconY))
                drawCircle(color = Color.White, radius = 6f, center = Offset(iconX, iconY), style = Stroke(width = 1f))
            } else if (stack.isWaiting) {
                // Hourglass badge on top right
                val iconX = center.x + (hexRadius * 0.45f)
                val iconY = center.y - (hexRadius * 0.5f)
                drawCircle(color = Color(0xFFEAB308), radius = 6f, center = Offset(iconX, iconY))
                drawCircle(color = Color.White, radius = 6f, center = Offset(iconX, iconY), style = Stroke(width = 1f))
            }
        }
    }

    /**
     * Renders floating damage indicators, traveling missile projectiles, and impact slashes.
     */
    private fun DrawScope.renderVisualFx(
        scene: CombatSceneSnapshot,
        hexRadius: Float,
        originOffset: Offset,
        textMeasurer: TextMeasurer?
    ) {
        val currentTick = scene.currentTick

        for (fx in scene.activeVisualFx) {
            val elapsed = (currentTick - fx.startTick).coerceAtLeast(0)
            val progress = (elapsed.toFloat() / fx.durationTicks.toFloat()).coerceIn(0f, 1f)

            when (fx) {
                is CombatVisualFx.FloatingText -> {
                    val center = calculateHexCenter(fx.hex, hexRadius, originOffset)
                    val offsetY = -(progress * 28f)
                    val alpha = (1f - progress).coerceIn(0f, 1f)

                    if (textMeasurer != null) {
                        val textLayout = textMeasurer.measure(
                            text = fx.text,
                            style = TextStyle(
                                fontSize = if (fx.isCrit) 13.sp else 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = fx.color.copy(alpha = alpha)
                            )
                        )
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(center.x - (textLayout.size.width / 2f), center.y - 20f + offsetY)
                        )
                    }
                }

                is CombatVisualFx.Projectile -> {
                    val p1 = calculateHexCenter(fx.fromHex, hexRadius, originOffset)
                    val p2 = calculateHexCenter(fx.toHex, hexRadius, originOffset)

                    // Parabolic arc interpolation
                    val curX = p1.x + (p2.x - p1.x) * progress
                    val heightArc = -30f * sin(progress * Math.PI.toFloat())
                    val curY = p1.y + (p2.y - p1.y) * progress + heightArc

                    drawCircle(color = fx.color, radius = 5f, center = Offset(curX, curY))
                    drawCircle(color = Color.White, radius = 2.5f, center = Offset(curX, curY))
                }

                is CombatVisualFx.MeleeSlash -> {
                    val center = calculateHexCenter(fx.targetHex, hexRadius, originOffset)
                    val slashRadius = hexRadius * 0.7f * progress
                    val slashPath = Path().apply {
                        moveTo(center.x - slashRadius, center.y - slashRadius)
                        quadraticTo(center.x, center.y + (slashRadius * 0.5f), center.x + slashRadius, center.y + slashRadius)
                    }
                    drawPath(
                        path = slashPath,
                        color = fx.color.copy(alpha = 1f - (progress * 0.7f)),
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                    )
                }

                is CombatVisualFx.SpellAura -> {
                    val center = calculateHexCenter(fx.centerHex, hexRadius, originOffset)
                    val auraRadius = (hexRadius * (fx.radiusHexes + 0.5f)) * progress
                    drawCircle(
                        color = fx.auraColor.copy(alpha = 0.5f * (1f - progress)),
                        radius = auraRadius,
                        center = center
                    )
                    drawCircle(
                        color = fx.auraColor.copy(alpha = 1f - progress),
                        radius = auraRadius,
                        center = center,
                        style = Stroke(width = 2.5f)
                    )
                }
            }
        }
    }
}
