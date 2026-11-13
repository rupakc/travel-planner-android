package io.github.rupakc.travelplanner.core.domain.map

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldMapTest {
    /** One square country "AA" at x 10..20, y 10..20 (map units) and "BB" with two rings. */
    private fun sample(): ByteArray {
        val bytes = ByteArrayOutputStream()
        val o = DataOutputStream(bytes)

        fun ring(vararg p: Int) {
            o.writeShort(p.size / 2)
            p.forEach { o.writeShort(it) }
        }
        o.writeShort(2)
        o.write("AA".toByteArray())
        o.writeShort(1)
        ring(1000, 1000, 2000, 1000, 2000, 2000, 1000, 2000, 1000, 1000)
        o.write("BB".toByteArray())
        o.writeShort(2)
        ring(3000, 3000, 3100, 3000, 3100, 3100, 3000, 3000)
        ring(5000, 5000, 5100, 5000, 5100, 5100, 5000, 5000)
        return bytes.toByteArray()
    }

    private val map = WorldMapData.parse(sample().inputStream())

    @Test fun `parse reads codes rings and map units`() {
        assertEquals(setOf("AA", "BB"), map.countries.keys)
        assertEquals(1, map.countries.getValue("AA").size)
        assertEquals(2, map.countries.getValue("BB").size)
        val ring = map.countries.getValue("AA").first()
        assertEquals(5, ring.size)
        assertEquals(10f, ring.minX, 0f)
        assertEquals(20f, ring.maxY, 0f)
    }

    @Test fun `truncated input is rejected`() {
        assertTrue(runCatching { WorldMapData.parse(sample().copyOf(10).inputStream()) }.isFailure)
    }

    @Test fun `projection fits centres and round trips`() {
        val p = MapProjection(720f, 600f)
        assertEquals(2f, p.scale, 0f)
        assertEquals(0f, p.offsetX, 0f)
        assertEquals(120f, p.offsetY, 0f)
        assertEquals(360f, p.toScreenX(180f), 0f)
        val hit = p.toMap(p.toScreenX(37f), p.toScreenY(55f))
        assertNotNull(hit)
        val (x, y) = hit!!
        assertEquals(37f, x, 0.001f)
        assertEquals(55f, y, 0.001f)
        assertNull(p.toMap(10f, 5f))
        assertNull(MapProjection(0f, 0f).toMap(1f, 1f))
    }

    @Test fun `hit test finds the country, any ring, and sea`() {
        assertEquals("AA", map.hit(15f, 15f))
        assertEquals("BB", map.hit(30.2f, 30.05f))
        assertEquals("BB", map.hit(50.8f, 50.2f))
        assertNull(map.hit(40f, 40f))
        assertNull(map.hit(25f, 15f))
    }

    @Test fun `tap goes through the projection`() {
        val p = MapProjection(720f, 360f)
        assertEquals("AA", ScratchMapRules.tap(map, p, p.toScreenX(15f), p.toScreenY(15f)))
        assertNull(ScratchMapRules.tap(map, p, p.toScreenX(100f), p.toScreenY(100f)))
    }

    @Test fun `visited mapping normalises and drops unknown codes`() {
        assertEquals(setOf("AA", "BB"), ScratchMapRules.filled(map, listOf(" aa", "BB", "ZZ", "", "XYZ")))
        assertFalse("ZZ" in ScratchMapRules.filled(map, listOf("zz")))
    }
}

class BundledWorldMapTest {
    private val map = java.io.File("../ui/src/main/res/raw/world_map.bin").inputStream().use { WorldMapData.parse(it) }

    @Test fun `the bundled asset covers the world and is small`() {
        assertTrue(map.countries.size > 150)
        assertTrue(java.io.File("../ui/src/main/res/raw/world_map.bin").length() < 40_000)
        listOf("US", "FR", "NO", "IN", "JP", "BR", "AU").forEach { assertTrue(it, it in map.countries) }
    }

    @Test fun `known points hit the right country`() {
        fun at(lon: Float, lat: Float) = map.hit(lon + 180f, 90f - lat)
        assertEquals("IN", at(78f, 22f))
        assertEquals("BR", at(-52f, -10f))
        assertEquals("FR", at(2.5f, 47f))
        assertNull(at(-30f, 0f))
    }

    @Test fun `enclave is drawn after its host and wins the hit test`() {
        fun ring(o: DataOutputStream, vararg p: Int) {
            o.writeShort(p.size / 2)
            p.forEach { o.writeShort(it) }
        }
        val bytes = ByteArrayOutputStream()
        val o = DataOutputStream(bytes)
        o.writeShort(2)
        // "LS" sorts before "ZA" but is the small square inside the big one.
        o.write("LS".toByteArray())
        o.writeShort(1)
        ring(o, 5000, 5000, 5500, 5000, 5500, 5500, 5000, 5500, 5000, 5000)
        o.write("ZA".toByteArray())
        o.writeShort(1)
        ring(o, 1000, 1000, 9000, 1000, 9000, 9000, 1000, 9000, 1000, 1000)
        val m = WorldMapData.parse(bytes.toByteArray().inputStream())
        assertEquals(listOf("ZA", "LS"), m.drawOrder)
        assertEquals("LS", m.hit(52f, 52f))
        assertEquals("ZA", m.hit(20f, 20f))
        assertNull(m.hit(95f, 95f))
    }
}
