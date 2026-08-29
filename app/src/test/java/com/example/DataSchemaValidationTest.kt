package com.example

import com.example.data.DataParser
import com.example.data.GameCatalog
import com.example.data.SchemaValidationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataSchemaValidationTest {

    private val parser = DataParser()

    @Test
    fun `test default creatures json parses and satisfies all validation constraints`() {
        val creatures = parser.parseCreatures(GameCatalog.DEFAULT_CREATURES_JSON)
        assertTrue(creatures.isNotEmpty())
        for (c in creatures) {
            assertTrue(c.id.isNotBlank())
            assertTrue(c.name.isNotBlank())
            assertTrue(c.attack >= 0)
            assertTrue(c.defense >= 0)
            assertTrue(c.minDamage > 0)
            assertTrue(c.maxDamage >= c.minDamage)
            assertTrue(c.health > 0)
            assertTrue(c.speed > 0)
            assertTrue(c.tier in 1..7)
        }
    }

    @Test
    fun `test invalid creature schema with negative stats throws SchemaValidationException`() {
        val badJson = """{
            "creatures": [
                {
                    "id": "bad_creature",
                    "attack": -5,
                    "defense": 10,
                    "minDamage": 0,
                    "maxDamage": -2,
                    "health": -10,
                    "speed": 0
                }
            ]
        }"""

        try {
            parser.parseCreatures(badJson)
            fail("Expected SchemaValidationException")
        } catch (e: SchemaValidationException) {
            assertTrue(e.validationErrors.isNotEmpty())
        }
    }

    @Test
    fun `test spells json parses and calculates spell power scaling`() {
        val spells = parser.parseSpells(GameCatalog.DEFAULT_SPELLS_JSON)
        assertTrue(spells.isNotEmpty())

        val magicArrow = spells.first { it.id == "magic_arrow" }
        // Base 10 + (5 * 10) = 60
        assertEquals(60, magicArrow.calculateDamage(spellPower = 5))

        val lightning = spells.first { it.id == "lightning_bolt" }
        // Base 20 + (10 * 25) = 270
        assertEquals(270, lightning.calculateDamage(spellPower = 10))
    }

    @Test
    fun `test buildings json prerequisite validation detects dangling prerequisites`() {
        val danglingJson = """{
            "buildings": [
                {
                    "id": "super_castle",
                    "name": "Super Castle",
                    "prerequisites": ["non_existent_building_xyz"],
                    "cost": { "gold": 1000 }
                }
            ]
        }"""

        try {
            parser.parseBuildings(danglingJson)
            fail("Expected SchemaValidationException for missing prerequisite")
        } catch (e: SchemaValidationException) {
            assertTrue(e.validationErrors.any { it.contains("non_existent_building_xyz") })
        }
    }

    @Test
    fun `test atlas json parsing and sprite frame layout`() {
        val atlas = parser.parseAtlas(GameCatalog.DEFAULT_ATLAS_JSON)
        assertNotNull(atlas)
        assertTrue(atlas.sprites.isNotEmpty())
        assertEquals(1024, atlas.width)
        assertEquals(1024, atlas.height)

        val pikemanIdle = atlas.findSequence("pikeman", com.example.data.AnimationState.IDLE)
        assertNotNull(pikemanIdle)
        assertEquals(4, pikemanIdle!!.frames.size)
        assertEquals(500, pikemanIdle.totalDurationMs)
    }

    @Test
    fun `test expanded creatures include pit_fiend and efreeti with valid stats`() {
        val creatures = parser.parseCreatures(GameCatalog.DEFAULT_CREATURES_JSON)
        val pitFiend = creatures.find { it.id == "pit_fiend" }
        assertNotNull("pit_fiend should exist in creatures schema", pitFiend)
        assertEquals("Pit Fiend", pitFiend!!.name)
        assertEquals(13, pitFiend.attack)
        assertEquals(13, pitFiend.defense)
        assertEquals(45, pitFiend.health)
        assertEquals(6, pitFiend.speed)
        assertEquals(5, pitFiend.tier)

        val efreeti = creatures.find { it.id == "efreeti" }
        assertNotNull("efreeti should exist in creatures schema", efreeti)
        assertEquals("Efreeti", efreeti!!.name)
        assertEquals(16, efreeti.attack)
        assertEquals(12, efreeti.defense)
        assertEquals(90, efreeti.health)
        assertEquals(9, efreeti.speed)
        assertEquals(6, efreeti.tier)
        assertTrue("Efreeti should be flying", efreeti.isFlying)
    }

    @Test
    fun `test expanded spells include meteor_shower and chain_lightning`() {
        val spells = parser.parseSpells(GameCatalog.DEFAULT_SPELLS_JSON)
        val meteorShower = spells.find { it.id == "meteor_shower" }
        assertNotNull("meteor_shower should exist in spells schema", meteorShower)
        assertEquals("Meteor Shower", meteorShower!!.name)
        assertEquals(16, meteorShower.manaCost)
        // Base 25 + 25 * 6 = 175
        assertEquals(175, meteorShower.calculateDamage(spellPower = 6))

        val chainLightning = spells.find { it.id == "chain_lightning" }
        assertNotNull("chain_lightning should exist in spells schema", chainLightning)
        assertEquals("Chain Lightning", chainLightning!!.name)
        assertEquals(24, chainLightning.manaCost)
        // Base 40 + 25 * 8 = 240
        assertEquals(240, chainLightning.calculateDamage(spellPower = 8))
    }

    @Test
    fun `test expanded buildings include marketplace and mage_guild_2 with valid prerequisites`() {
        val buildings = parser.parseBuildings(GameCatalog.DEFAULT_BUILDINGS_JSON)
        val marketplace = buildings.find { it.id == "marketplace" }
        assertNotNull("marketplace should exist in buildings schema", marketplace)
        assertEquals("Marketplace", marketplace!!.name)
        assertEquals(500, marketplace.cost.gold)
        assertEquals(5, marketplace.cost.wood)

        val mageGuild2 = buildings.find { it.id == "mage_guild_2" }
        assertNotNull("mage_guild_2 should exist in buildings schema", mageGuild2)
        assertEquals("Mage Guild Level 2", mageGuild2!!.name)
        assertTrue(mageGuild2.prerequisites.contains("mage_guild_1"))
    }
}
