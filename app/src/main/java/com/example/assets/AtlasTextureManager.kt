package com.example.assets

import com.example.data.AnimationSequence
import com.example.data.AnimationState
import com.example.data.TextureAtlasDefinition

/**
 * Texture and animation atlas repository.
 * Resolves animation sequences and fallback frames for all supported creatures.
 */
class AtlasTextureManager(
    val atlas: TextureAtlasDefinition? = null
) {
    /**
     * Resolves an animation sequence for a creature and state.
     */
    fun getSequence(creatureId: String, state: AnimationState): AnimationSequence? {
        return atlas?.findSequence(creatureId, state)
    }

    /**
     * Creates an AnimationController wired directly to this atlas repository.
     */
    fun createController(creatureId: String): AnimationController {
        return AnimationController(creatureId) { state ->
            getSequence(creatureId, state)
        }
    }
}
