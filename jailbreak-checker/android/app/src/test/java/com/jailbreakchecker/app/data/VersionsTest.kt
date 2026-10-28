package com.jailbreakchecker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {

    private fun range(id: String, min: String, max: String) =
        FirmwareRange(id = id, label = id, min = min, max = max)

    @Test
    fun playStationStyleComparesMinorAsDecimal() {
        assertTrue(Versions.compare("9.03", "9.5") < 0)
        assertEquals(0, Versions.compare("9.5", "9.50"))
        assertTrue(Versions.compare("9.60", "9.5") > 0)
        assertTrue(Versions.compare("13.52", "13.6") < 0)
        assertTrue(Versions.compare("10.00", "9.60") > 0)
    }

    @Test
    fun dottedVersionsCompareNumerically() {
        assertTrue(Versions.compare("23.0.1", "4.1.0") > 0)
        assertTrue(Versions.compare("10.0.25398.4909", "10.0.25398.4478") > 0)
        assertEquals(0, Versions.compare("20.0", "20.0.0"))
        assertTrue(Versions.compare("11.17.0-50E", "11.4.0") > 0)
    }

    @Test
    fun matchPrefersBoundedRangesOverCatchAll() {
        val ranges = listOf(
            range("partial", "10.0.25398.4478", "10.0.25398.4909"),
            range("other", "", ""),
        )
        assertEquals("partial", Versions.match("10.0.25398.4908", ranges)?.id)
        assertEquals("other", Versions.match("10.0.26100.1", ranges)?.id)
    }

    @Test
    fun matchHandlesOpenEndedRanges() {
        val ranges = listOf(
            range("old", "1.00", "13.60"),
            range("new", "14.20", ""),
        )
        assertEquals("old", Versions.match("13.6", ranges)?.id)
        assertEquals("new", Versions.match("15.00", ranges)?.id)
        assertNull(Versions.match("14.00", ranges))
        assertNull(Versions.match("abc", ranges))
    }

    @Test
    fun bundledCatalogRangesAreWellFormed() {
        val text = java.io.File("../../data/consoles.json").readText()
        val catalog = Repository.json.decodeFromString(Catalog.serializer(), text)
        val brandIds = catalog.brands.map { it.id }.toSet()
        assertTrue(catalog.consoles.size > 20)
        for (console in catalog.consoles) {
            assertTrue("${console.id} brand", console.brandId in brandIds)
            assertTrue("${console.id} status", Status.parse(console.summary.status) != Status.UNKNOWN)
            if (console.mode == "all") {
                assertTrue("${console.id} allVersions", console.allVersions != null)
            } else {
                assertTrue("${console.id} firmwares", console.firmwares.isNotEmpty())
            }
            val variantIds = console.variants.map { it.id }.toSet()
            for (fw in console.firmwares) {
                assertTrue("${fw.id} status", Status.parse(fw.status) != Status.UNKNOWN)
                assertTrue("${fw.id} variant", fw.variant.isBlank() || fw.variant in variantIds)
                if (fw.min.isNotBlank() && fw.max.isNotBlank()) {
                    assertTrue("${fw.id} min<=max", Versions.compare(fw.min, fw.max) <= 0)
                }
            }
        }
    }
}
