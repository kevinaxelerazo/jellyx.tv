package dev.jdtech.jellyfin.models

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingPlatformFilterTest {

    @Test
    fun allSixPlatformsMatchExpectedStudiosAndProductionHouses() {
        val disney = StreamingPlatformCatalog.findById("disney")!!
        listOf(
                "Walt Disney Pictures",
                "Pixar",
                "Marvel Studios",
                "Lucasfilm Ltd.",
                "20th Century Studios",
            )
            .forEach { studio ->
                assertTrue(
                    "Expected Disney+ to match $studio",
                    StreamingPlatformCatalog.matchesStudioName(disney, studio),
                )
            }

        val prime = StreamingPlatformCatalog.findById("prime")!!
        listOf("Amazon Studios", "Amazon MGM Studios").forEach { studio ->
            assertTrue(
                "Expected Prime Video to match $studio",
                StreamingPlatformCatalog.matchesStudioName(prime, studio),
            )
        }

        val hbo = StreamingPlatformCatalog.findById("hbo")!!
        listOf("HBO Max", "Max", "Warner Bros. Pictures", "DC Studios").forEach { studio ->
            assertTrue(
                "Expected HBO / Max to match $studio",
                StreamingPlatformCatalog.matchesStudioName(hbo, studio),
            )
        }

        val netflix = StreamingPlatformCatalog.findById("netflix")!!
        assertTrue(StreamingPlatformCatalog.matchesStudioName(netflix, "Netflix Animation"))

        val paramount = StreamingPlatformCatalog.findById("paramount")!!
        listOf("Paramount Pictures", "Showtime", "Nickelodeon", "CBS Studios").forEach { studio ->
            assertTrue(
                "Expected Paramount+ to match $studio",
                StreamingPlatformCatalog.matchesStudioName(paramount, studio),
            )
        }

        val apple = StreamingPlatformCatalog.findById("apple")!!
        listOf("Apple Studios", "Apple Original Films").forEach { studio ->
            assertTrue(
                "Expected Apple TV+ to match $studio",
                StreamingPlatformCatalog.matchesStudioName(apple, studio),
            )
        }
    }

    @Test
    fun matchStudioIds_returnsOnlyMatchingDistinctUuids() {
        val marvelId = UUID.randomUUID()
        val pixarId = UUID.randomUUID()
        val paramountId = UUID.randomUUID()
        val serverStudios =
            listOf(
                marvelId to "Marvel Studios",
                pixarId to "Pixar Animation Studios",
                marvelId to "Marvel",
                paramountId to "Paramount Pictures",
            )

        val disney = StreamingPlatformCatalog.findById("disney")!!
        val matched = StreamingPlatformCatalog.matchStudioIds(disney, serverStudios)

        assertEquals(listOf(marvelId, pixarId), matched)
    }

    @Test
    fun findByQuery_resolvesAliasesCaseInsensitively() {
        assertEquals("netflix", StreamingPlatformCatalog.findByQuery("  NETFLIX ")?.id)
        assertEquals("prime", StreamingPlatformCatalog.findByQuery("Amazon   Prime Video")?.id)
        assertEquals("hbo", StreamingPlatformCatalog.findByQuery("hbo max")?.id)
        assertEquals("hbo", StreamingPlatformCatalog.findByQuery("MAX")?.id)
        assertEquals("disney", StreamingPlatformCatalog.findByQuery("Disney Plus")?.id)
        assertEquals("paramount", StreamingPlatformCatalog.findByQuery("PARAMOUNT+")?.id)
        assertEquals("apple", StreamingPlatformCatalog.findByQuery("apple tv plus")?.id)
        assertNotNull(StreamingPlatformCatalog.findById("DISNEY"))
        assertNull(StreamingPlatformCatalog.findByQuery("Unknown Service"))
    }

    @Test
    fun antiFalsePositiveChecks_doNotCrossMatchUnrelatedStudios() {
        val hbo = StreamingPlatformCatalog.findById("hbo")!!
        assertFalse(StreamingPlatformCatalog.matchesStudioName(hbo, "Mad Max Productions"))
        assertFalse(StreamingPlatformCatalog.matchesStudioName(hbo, "Miramax"))

        val apple = StreamingPlatformCatalog.findById("apple")!!
        assertFalse(StreamingPlatformCatalog.matchesStudioName(apple, "Pineapple Studios"))

        val disney = StreamingPlatformCatalog.findById("disney")!!
        assertFalse(StreamingPlatformCatalog.matchesStudioName(disney, "Paramount"))
    }
}
