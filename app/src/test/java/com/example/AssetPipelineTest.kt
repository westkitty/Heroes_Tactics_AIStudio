package com.example

import com.example.assets.AnimationController
import com.example.assets.AtlasTextureManager
import com.example.data.AnimationState
import com.example.data.GameCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AssetPipelineTest {

    @Before
    fun setup() {
        GameCatalog.ensureInitialized()
    }

    @Test
    fun `test atlas texture manager resolves sequences`() {
        val atlas = GameCatalog.atlas
        assertNotNull(atlas)

        val manager = AtlasTextureManager(atlas)
        val pikemanIdle = manager.getSequence("pikeman", AnimationState.IDLE)
        assertNotNull(pikemanIdle)
        assertEquals(4, pikemanIdle!!.frames.size)
        assertTrue(pikemanIdle.isLooping)

        val pikemanAttack = manager.getSequence("pikeman", AnimationState.ATTACK)
        assertNotNull(pikemanAttack)
        assertEquals(2, pikemanAttack!!.impactFrameIndex)
        assertFalse(pikemanAttack.isLooping)
    }

    @Test
    fun `test animation controller state progression and impact frame trigger`() {
        val atlas = GameCatalog.atlas
        val manager = AtlasTextureManager(atlas)
        val controller = manager.createController("pikeman")

        assertEquals(AnimationState.IDLE, controller.currentState)
        assertEquals(0, controller.currentFrameIndex)

        // Advance idle frames (125ms per frame)
        controller.tick(130)
        assertEquals(1, controller.currentFrameIndex)

        // Transition to Attack
        var impactTriggered = false
        controller.onImpactFrame = { frame ->
            if (frame == 2) impactTriggered = true
        }

        controller.transitionTo(AnimationState.ATTACK)
        assertEquals(AnimationState.ATTACK, controller.currentState)
        assertEquals(0, controller.currentFrameIndex)
        assertFalse(impactTriggered)

        // Attack frames are 83ms each.
        // Advance to frame 1 (83ms)
        controller.tick(90)
        assertEquals(1, controller.currentFrameIndex)
        assertFalse(impactTriggered)

        // Advance to frame 2 (impact frame)
        controller.tick(90)
        assertEquals(2, controller.currentFrameIndex)
        assertTrue(impactTriggered)
    }

    @Test
    fun `test fallback animation sequence generation for unmapped creature states`() {
        val manager = AtlasTextureManager(null) // Empty atlas
        val controller = manager.createController("unknown_creature")

        assertNotNull(controller.currentSequence)
        assertEquals(2, controller.currentSequence!!.frames.size)
        assertEquals(AnimationState.IDLE, controller.currentState)

        // Check frame bounds
        val frame = controller.getCurrentFrame()
        assertEquals(64, frame.width)
        assertEquals(64, frame.height)
    }
}
