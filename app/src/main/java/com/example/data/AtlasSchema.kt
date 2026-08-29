package com.example.data

/**
 * Combat sprite animation states.
 */
enum class AnimationState {
    IDLE,
    MOVE,
    ATTACK,
    DEFEND,
    HIT,
    DIE,
    SPECIAL
}

/**
 * Individual sprite sub-texture bounding frame within a texture atlas.
 */
data class SpriteFrame(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val pivotX: Int = 0,
    val pivotY: Int = 0,
    val durationMs: Int = 100
) {
    init {
        require(x >= 0 && y >= 0) { "Frame coordinates must be non-negative" }
        require(width > 0 && height > 0) { "Frame dimensions must be positive" }
        require(durationMs > 0) { "Frame duration must be positive" }
    }
}

/**
 * Sequence of sprite frames for a particular animation state.
 */
data class AnimationSequence(
    val id: String,
    val creatureId: String,
    val animation: AnimationState,
    val frameRateFps: Int = 10,
    val isLooping: Boolean = true,
    val impactFrameIndex: Int = -1,
    val frames: List<SpriteFrame> = emptyList()
) {
    init {
        require(id.isNotBlank()) { "Sequence id cannot be blank" }
        require(frames.isNotEmpty()) { "Animation sequence must contain at least one frame" }
        require(frameRateFps > 0) { "Frame rate must be positive" }
    }

    /**
     * Total duration of the animation sequence in milliseconds.
     */
    val totalDurationMs: Int = frames.sumOf { it.durationMs }
}

/**
 * Texture Atlas metadata containing sprite definitions and sheet layout.
 */
data class TextureAtlasDefinition(
    val texturePath: String,
    val width: Int,
    val height: Int,
    val sprites: List<AnimationSequence>
) {
    init {
        require(width > 0 && height > 0) { "Atlas dimensions must be positive" }
    }

    /**
     * Retrieves an animation sequence for a specific creature and animation state.
     */
    fun findSequence(creatureId: String, state: AnimationState): AnimationSequence? {
        return sprites.firstOrNull { it.creatureId.equals(creatureId, ignoreCase = true) && it.animation == state }
    }
}
