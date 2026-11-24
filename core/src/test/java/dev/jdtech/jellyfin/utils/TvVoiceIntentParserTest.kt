package dev.jdtech.jellyfin.utils

import android.app.SearchManager
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvVoiceIntentParserTest {

    @Test
    fun `cleanVoiceQuery strips assistant wake words, command verbs, and app suffixes`() {
        assertEquals(
            "vision quest",
            TvVoiceIntentParser.cleanVoiceQuery("Alexa, busca vision quest en jellyx.tv"),
        )
        assertEquals(
            "Vision Quest",
            TvVoiceIntentParser.cleanVoiceQuery("Ok Google buscar Vision Quest en Jellyx"),
        )
        assertEquals(
            "Breaking Bad",
            TvVoiceIntentParser.cleanVoiceQuery("search for Breaking Bad on jellyx.tv"),
        )
        assertEquals(
            "Dune",
            TvVoiceIntentParser.cleanVoiceQuery("pon Dune en jellyx tv"),
        )
        assertEquals(
            "Vision Quest",
            TvVoiceIntentParser.cleanVoiceQuery("Vision Quest"),
        )
        assertEquals(
            "play",
            TvVoiceIntentParser.cleanVoiceQuery("play"),
        )
    }

    @Test
    fun `parseRaw handles search actions, deep links, empty search, and unrelated actions`() {
        assertEquals(
            TvIncomingCommand.VoiceSearch(query = "vision quest", autoPlayFirst = false),
            TvVoiceIntentParser.parseRaw(
                action = Intent.ACTION_SEARCH,
                scheme = null,
                host = null,
                pathSegments = emptyList(),
                uriQueryParams = emptyMap(),
                extras = mapOf(SearchManager.QUERY to "busca vision quest en jellyx.tv"),
            ),
        )

        assertEquals(
            TvIncomingCommand.VoiceSearch(query = "Breaking Bad", autoPlayFirst = false),
            TvVoiceIntentParser.parseRaw(
                action = TvVoiceIntentParser.ACTION_GMS_SEARCH,
                scheme = null,
                host = null,
                pathSegments = emptyList(),
                uriQueryParams = emptyMap(),
                extras = mapOf(SearchManager.QUERY to "search for Breaking Bad on jellyx.tv"),
            ),
        )

        assertEquals(
            TvIncomingCommand.VoiceSearch(query = "Dune", autoPlayFirst = true),
            TvVoiceIntentParser.parseRaw(
                action = TvVoiceIntentParser.ACTION_MEDIA_PLAY_FROM_SEARCH,
                scheme = null,
                host = null,
                pathSegments = emptyList(),
                uriQueryParams = emptyMap(),
                extras = mapOf(SearchManager.QUERY to "pon Dune en jellyx tv"),
            ),
        )

        assertEquals(
            TvIncomingCommand.VoiceSearch(query = "vision quest", autoPlayFirst = false),
            TvVoiceIntentParser.parseRaw(
                action = Intent.ACTION_VIEW,
                scheme = "jellyx",
                host = "search",
                pathSegments = emptyList(),
                uriQueryParams = mapOf("q" to "vision quest"),
                extras = emptyMap(),
            ),
        )

        assertEquals(
            TvIncomingCommand.OpenItem(kind = "movie", itemId = "1234"),
            TvVoiceIntentParser.parseRaw(
                action = Intent.ACTION_VIEW,
                scheme = "jellyx",
                host = "item",
                pathSegments = listOf("movie", "1234"),
                uriQueryParams = emptyMap(),
                extras = emptyMap(),
            ),
        )

        assertEquals(
            TvIncomingCommand.OpenItem(kind = "show", itemId = "5678"),
            TvVoiceIntentParser.parseRaw(
                action = Intent.ACTION_VIEW,
                scheme = "jellyx",
                host = "item",
                pathSegments = listOf("show", "5678"),
                uriQueryParams = emptyMap(),
                extras = emptyMap(),
            ),
        )

        assertEquals(
            TvIncomingCommand.OpenSearchScreen,
            TvVoiceIntentParser.parseRaw(
                action = Intent.ACTION_SEARCH,
                scheme = null,
                host = null,
                pathSegments = emptyList(),
                uriQueryParams = emptyMap(),
                extras = emptyMap(),
            ),
        )

        assertNull(
            TvVoiceIntentParser.parseRaw(
                action = Intent.ACTION_MAIN,
                scheme = null,
                host = null,
                pathSegments = emptyList(),
                uriQueryParams = emptyMap(),
                extras = emptyMap(),
            ),
        )
    }
}
