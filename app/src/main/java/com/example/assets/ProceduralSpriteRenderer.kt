package com.example.assets

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.data.AnimationState
import com.example.data.Faction

/**
 * Procedural vector/pixel rendering engine for HoMM3 creature sprites and animations.
 * Provides rich, distinctive visual assets without relying on proprietary copyrighted art.
 */
object ProceduralSpriteRenderer {

    // Color palette per faction
    val CastleGold = Color(0xFFF5C542)
    val CastleBlue = Color(0xFF1E3A8A)
    val CastleSteel = Color(0xFFCBD5E1)
    val CastleCrimson = Color(0xFF881337)
    val InfernoRed = Color(0xFFDC2626)
    val InfernoDark = Color(0xFF1C1917)
    val InfernoOrange = Color(0xFFF97316)

    /**
     * Draws procedural creature sprite on Compose Canvas based on animation state and frame index.
     */
    fun drawCreature(
        drawScope: DrawScope,
        creatureId: String,
        state: AnimationState,
        frameIndex: Int,
        isFacingEast: Boolean,
        sizePx: Float
    ) {
        val half = sizePx / 2f
        val animOffset = when (state) {
            AnimationState.IDLE -> if (frameIndex % 2 == 1) 2f else 0f
            AnimationState.MOVE -> if (frameIndex % 2 == 1) 4f else -2f
            AnimationState.ATTACK -> if (frameIndex >= 1) 8f else 0f
            AnimationState.DEFEND -> -4f
            AnimationState.HIT -> -6f
            AnimationState.DIE -> 10f
            else -> 0f
        }

        val dirMultiplier = if (isFacingEast) 1f else -1f

        drawScope.apply {
            // Shadow
            drawOval(
                color = Color(0x66000000),
                topLeft = Offset(half - (sizePx * 0.35f), sizePx * 0.85f),
                size = Size(sizePx * 0.7f, sizePx * 0.15f)
            )

            when {
                creatureId.contains("angel", ignoreCase = true) -> {
                    drawAngel(animOffset, dirMultiplier, sizePx, state)
                }
                creatureId.contains("archer", ignoreCase = true) || creatureId.contains("marksman", ignoreCase = true) -> {
                    drawArcher(animOffset, dirMultiplier, sizePx, state)
                }
                creatureId.contains("griffin", ignoreCase = true) -> {
                    drawGriffin(animOffset, dirMultiplier, sizePx, state)
                }
                creatureId.contains("devil", ignoreCase = true) -> {
                    drawArchDevil(animOffset, dirMultiplier, sizePx, state)
                }
                creatureId.contains("cerberus", ignoreCase = true) -> {
                    drawCerberus(animOffset, dirMultiplier, sizePx, state)
                }
                creatureId.contains("imp", ignoreCase = true) || creatureId.contains("familiar", ignoreCase = true) -> {
                    drawImp(animOffset, dirMultiplier, sizePx, state)
                }
                creatureId.contains("pit_fiend", ignoreCase = true) -> {
                    drawPitFiend(animOffset, dirMultiplier, sizePx, state)
                }
                creatureId.contains("efreet", ignoreCase = true) -> {
                    drawEfreeti(animOffset, dirMultiplier, sizePx, state)
                }
                else -> {
                    // Default Pikeman / Swordsman / Knight
                    drawKnight(animOffset, dirMultiplier, sizePx, state, creatureId)
                }
            }
        }
    }

    private fun DrawScope.drawPitFiend(animOffset: Float, dir: Float, size: Float, state: AnimationState) {
        val cx = size / 2f + (animOffset * dir)
        val cy = size / 2f + animOffset

        // Large horned demon body
        drawCircle(color = Color(0xFF7F1D1D), radius = size * 0.16f, center = Offset(cx, cy - (size * 0.12f)))
        drawRect(color = Color(0xFF450A0A), topLeft = Offset(cx - (size * 0.16f), cy), size = Size(size * 0.32f, size * 0.38f))

        // Large Curved Horns
        drawLine(color = Color(0xFF1C1917), start = Offset(cx - 10f, cy - 20f), end = Offset(cx - 24f, cy - 35f), strokeWidth = 4f)
        drawLine(color = Color(0xFF1C1917), start = Offset(cx + 10f, cy - 20f), end = Offset(cx + 24f, cy - 35f), strokeWidth = 4f)

        // Flame Whip
        val whipX = cx + (24f * dir)
        val whipPath = Path().apply {
            moveTo(whipX, cy + 10f)
            quadraticTo(whipX + (20f * dir), cy - 10f, whipX + (10f * dir), cy - 30f)
        }
        drawPath(whipPath, color = InfernoOrange, style = Stroke(width = 3f))
        drawCircle(color = Color(0xFFFDE047), radius = 5f, center = Offset(whipX + (10f * dir), cy - 30f))
    }

    private fun DrawScope.drawEfreeti(animOffset: Float, dir: Float, size: Float, state: AnimationState) {
        val cx = size / 2f + (animOffset * dir)
        val cy = size / 2f + animOffset

        // Fiery genie body
        drawCircle(color = Color(0xFFEA580C), radius = size * 0.14f, center = Offset(cx, cy - (size * 0.15f)))
        drawRect(color = Color(0xFF9A3412), topLeft = Offset(cx - (size * 0.14f), cy - (size * 0.02f)), size = Size(size * 0.28f, size * 0.25f))

        // Smoke / Fire tail (no legs)
        val smokeTail = Path().apply {
            moveTo(cx - 12f, cy + (size * 0.22f))
            quadraticTo(cx - (20f * dir), cy + (size * 0.35f), cx + (5f * dir), cy + (size * 0.42f))
            quadraticTo(cx + 15f, cy + (size * 0.32f), cx + 12f, cy + (size * 0.22f))
            close()
        }
        drawPath(smokeTail, color = Color(0xFF475569))

        // Fiery Scimitar
        val swordX = cx + (20f * dir)
        drawLine(color = Color(0xFFFBBF24), start = Offset(swordX, cy + 15f), end = Offset(swordX + (12f * dir), cy - 20f), strokeWidth = 3.5f)
    }

    private fun DrawScope.drawAngel(animOffset: Float, dir: Float, size: Float, state: AnimationState) {
        val cx = size / 2f + (animOffset * dir)
        val cy = (size / 2f) + animOffset

        // Wings (L & R)
        val wingPath = Path().apply {
            moveTo(cx - (24f * dir), cy - 10f)
            cubicTo(cx - (40f * dir), cy - 35f, cx - (10f * dir), cy - 40f, cx, cy - 20f)
            cubicTo(cx + (10f * dir), cy - 40f, cx + (40f * dir), cy - 35f, cx + (24f * dir), cy - 10f)
            close()
        }
        drawPath(wingPath, color = Color(0xEEFFFFFF))
        drawPath(wingPath, color = CastleGold, style = Stroke(width = 2f))

        // Robes
        drawCircle(color = CastleGold, radius = size * 0.12f, center = Offset(cx, cy - (size * 0.15f)))
        drawRect(color = Color(0xFFF8FAFC), topLeft = Offset(cx - (size * 0.12f), cy - (size * 0.05f)), size = Size(size * 0.24f, size * 0.35f))

        // Flaming Sword
        val swordX = cx + (20f * dir)
        val swordY = cy - 15f
        drawLine(color = CastleSteel, start = Offset(swordX, swordY + 25f), end = Offset(swordX, swordY - 25f), strokeWidth = 3f)
        drawCircle(color = Color(0xFFFF9900), radius = 6f, center = Offset(swordX, swordY - 25f))
    }

    private fun DrawScope.drawArcher(animOffset: Float, dir: Float, size: Float, state: AnimationState) {
        val cx = size / 2f + (animOffset * dir)
        val cy = (size / 2f) + animOffset

        // Leather tunic
        drawCircle(color = Color(0xFF854D0E), radius = size * 0.12f, center = Offset(cx, cy - (size * 0.12f)))
        drawRect(color = Color(0xFF15803D), topLeft = Offset(cx - (size * 0.10f), cy), size = Size(size * 0.20f, size * 0.28f))

        // Bow
        val bowPath = Path().apply {
            moveTo(cx + (15f * dir), cy - 20f)
            quadraticTo(cx + (25f * dir), cy, cx + (15f * dir), cy + 20f)
        }
        drawPath(bowPath, color = Color(0xFF78350F), style = Stroke(width = 3f))
        drawLine(color = Color.White, start = Offset(cx + (15f * dir), cy - 20f), end = Offset(cx + (15f * dir), cy + 20f), strokeWidth = 1.5f)
    }

    private fun DrawScope.drawGriffin(animOffset: Float, dir: Float, size: Float, state: AnimationState) {
        val cx = size / 2f + (animOffset * dir)
        val cy = size / 2f + animOffset

        // Lion body + eagle head & wings
        drawOval(color = Color(0xFFD97706), topLeft = Offset(cx - (size * 0.25f), cy - (size * 0.10f)), size = Size(size * 0.50f, size * 0.30f))
        drawCircle(color = Color(0xFFFDE68A), radius = size * 0.14f, center = Offset(cx + (15f * dir), cy - (size * 0.15f)))

        // Griffin Wings
        val wing = Path().apply {
            moveTo(cx, cy - 10f)
            lineTo(cx - (20f * dir), cy - 35f)
            lineTo(cx + (10f * dir), cy - 25f)
            close()
        }
        drawPath(wing, color = Color(0xFFF59E0B))
    }

    private fun DrawScope.drawArchDevil(animOffset: Float, dir: Float, size: Float, state: AnimationState) {
        val cx = size / 2f + (animOffset * dir)
        val cy = size / 2f + animOffset

        // Crimson Demon Body
        drawCircle(color = InfernoRed, radius = size * 0.14f, center = Offset(cx, cy - (size * 0.15f)))
        drawRect(color = InfernoDark, topLeft = Offset(cx - (size * 0.12f), cy - (size * 0.02f)), size = Size(size * 0.24f, size * 0.35f))

        // Horns
        drawLine(color = InfernoDark, start = Offset(cx - 8f, cy - 25f), end = Offset(cx - 16f, cy - 38f), strokeWidth = 3f)
        drawLine(color = InfernoDark, start = Offset(cx + 8f, cy - 25f), end = Offset(cx + 16f, cy - 38f), strokeWidth = 3f)

        // Pitchfork
        val pitchX = cx + (22f * dir)
        drawLine(color = Color(0xFFE2E8F0), start = Offset(pitchX, cy + 25f), end = Offset(pitchX, cy - 30f), strokeWidth = 3f)
        drawLine(color = InfernoOrange, start = Offset(pitchX - 6f, cy - 30f), end = Offset(pitchX + 6f, cy - 30f), strokeWidth = 3f)
    }

    private fun DrawScope.drawCerberus(animOffset: Float, dir: Float, size: Float, state: AnimationState) {
        val cx = size / 2f + (animOffset * dir)
        val cy = size / 2f + animOffset

        // 3-headed hound body
        drawOval(color = Color(0xFF451A03), topLeft = Offset(cx - (size * 0.25f), cy - (size * 0.08f)), size = Size(size * 0.50f, size * 0.28f))
        // 3 heads
        drawCircle(color = Color(0xFF78350F), radius = 10f, center = Offset(cx + (15f * dir), cy - 15f))
        drawCircle(color = Color(0xFF78350F), radius = 10f, center = Offset(cx + (22f * dir), cy - 6f))
        drawCircle(color = Color(0xFF78350F), radius = 10f, center = Offset(cx + (15f * dir), cy + 4f))
    }

    private fun DrawScope.drawImp(animOffset: Float, dir: Float, size: Float, state: AnimationState) {
        val cx = size / 2f + (animOffset * dir)
        val cy = size / 2f + animOffset

        drawCircle(color = InfernoOrange, radius = size * 0.10f, center = Offset(cx, cy - (size * 0.10f)))
        drawRect(color = Color(0xFF991B1B), topLeft = Offset(cx - (size * 0.08f), cy), size = Size(size * 0.16f, size * 0.20f))
    }

    private fun DrawScope.drawKnight(animOffset: Float, dir: Float, size: Float, state: AnimationState, creatureId: String) {
        val cx = size / 2f + (animOffset * dir)
        val cy = size / 2f + animOffset

        // Steel helmet
        drawCircle(color = CastleSteel, radius = size * 0.12f, center = Offset(cx, cy - (size * 0.12f)))
        drawCircle(color = CastleGold, radius = size * 0.05f, center = Offset(cx, cy - (size * 0.16f))) // Crest

        // Plate armor
        drawRect(color = CastleBlue, topLeft = Offset(cx - (size * 0.12f), cy), size = Size(size * 0.24f, size * 0.30f))

        // Shield
        val shieldX = cx - (16f * dir)
        val shieldY = cy + 4f
        drawOval(color = CastleCrimson, topLeft = Offset(shieldX - 8f, shieldY - 14f), size = Size(16f, 28f))

        // Spear or Sword
        val weaponX = cx + (18f * dir)
        val isSpear = creatureId.contains("pike", ignoreCase = true) || creatureId.contains("halberd", ignoreCase = true)
        val weaponLength = if (isSpear) 50f else 32f
        drawLine(color = CastleSteel, start = Offset(weaponX, cy + 18f), end = Offset(weaponX + (8f * dir), cy - weaponLength), strokeWidth = 3f)
    }
}
