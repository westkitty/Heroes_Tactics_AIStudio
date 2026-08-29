package com.example.assets

import com.example.data.AnimationSequence
import com.example.data.AnimationState
import com.example.data.SpriteFrame

/**
 * Controller driving animation state machines, frame progression, and impact triggers.
 * Completely decoupled from game simulation logic.
 */
class AnimationController(
    val creatureId: String,
    private val sequenceProvider: (state: AnimationState) -> AnimationSequence?
) {
    var currentState: AnimationState = AnimationState.IDLE
        private set

    var currentSequence: AnimationSequence? = null
        private set

    var currentFrameIndex: Int = 0
        private set

    var elapsedTimeInCurrentFrameMs: Int = 0
        private set

    var isFinished: Boolean = false
        private set

    private var impactTriggeredForCurrentCycle: Boolean = false

    var onImpactFrame: ((frameIndex: Int) -> Unit)? = null
    var onAnimationComplete: ((state: AnimationState) -> Unit)? = null

    init {
        transitionTo(AnimationState.IDLE)
    }

    /**
     * Changes the current animation state and resets frame timing.
     */
    fun transitionTo(newState: AnimationState, forceReset: Boolean = false) {
        if (currentState == newState && currentSequence != null && !forceReset && !isFinished) return

        currentState = newState
        currentSequence = sequenceProvider(newState) ?: createFallbackSequence(newState)
        currentFrameIndex = 0
        elapsedTimeInCurrentFrameMs = 0
        isFinished = false
        impactTriggeredForCurrentCycle = false
    }

    /**
     * Ticks animation progression forward by integer milliseconds.
     */
    fun tick(deltaMs: Int) {
        val seq = currentSequence ?: return
        if (seq.frames.isEmpty() || isFinished) return

        elapsedTimeInCurrentFrameMs += deltaMs
        val currentFrame = seq.frames[currentFrameIndex]

        if (elapsedTimeInCurrentFrameMs >= currentFrame.durationMs) {
            val leftover = elapsedTimeInCurrentFrameMs - currentFrame.durationMs
            elapsedTimeInCurrentFrameMs = leftover

            if (currentFrameIndex + 1 < seq.frames.size) {
                currentFrameIndex++
                // Check impact trigger
                if (currentFrameIndex == seq.impactFrameIndex && !impactTriggeredForCurrentCycle) {
                    impactTriggeredForCurrentCycle = true
                    onImpactFrame?.invoke(currentFrameIndex)
                }
            } else {
                // End of sequence reached
                if (seq.isLooping) {
                    currentFrameIndex = 0
                    impactTriggeredForCurrentCycle = false
                } else {
                    isFinished = true
                    onAnimationComplete?.invoke(currentState)
                }
            }
        }
    }

    /**
     * Returns the currently active sprite frame.
     */
    fun getCurrentFrame(): SpriteFrame {
        val seq = currentSequence
        if (seq == null || seq.frames.isEmpty()) {
            return SpriteFrame(0, 0, 64, 64, 32, 56, 100)
        }
        return seq.frames[currentFrameIndex.coerceIn(0, seq.frames.lastIndex)]
    }

    private fun createFallbackSequence(state: AnimationState): AnimationSequence {
        val frames = listOf(
            SpriteFrame(0, 0, 64, 64, 32, 56, 125),
            SpriteFrame(64, 0, 64, 64, 32, 56, 125)
        )
        return AnimationSequence(
            id = "${creatureId}_${state.name.lowercase()}_fallback",
            creatureId = creatureId,
            animation = state,
            frameRateFps = 8,
            isLooping = state == AnimationState.IDLE || state == AnimationState.MOVE,
            impactFrameIndex = if (state == AnimationState.ATTACK) 1 else -1,
            frames = frames
        )
    }
}
