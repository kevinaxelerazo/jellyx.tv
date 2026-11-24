package dev.jdtech.jellyfin.utils

import android.app.SearchManager
import android.content.Intent
import android.provider.MediaStore

sealed interface TvIncomingCommand {
    data class OpenItem(val kind: String, val itemId: String) : TvIncomingCommand

    data class VoiceSearch(val query: String, val autoPlayFirst: Boolean = false) : TvIncomingCommand

    data object OpenSearchScreen : TvIncomingCommand
}

object TvVoiceIntentParser {
    const val ACTION_GMS_SEARCH = "com.google.android.gms.actions.SEARCH_ACTION"
    const val ACTION_MEDIA_PLAY_FROM_SEARCH = MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH
    const val ACTION_VOICE_SEARCH_RESULTS = "android.intent.action.VOICE_SEARCH_RESULTS"
    const val ACTION_VOICE_SEARCH_HANDS_FREE = "android.speech.action.VOICE_SEARCH_HANDS_FREE"

    private val SEARCH_ACTIONS =
        setOf(
            Intent.ACTION_SEARCH,
            ACTION_GMS_SEARCH,
            ACTION_MEDIA_PLAY_FROM_SEARCH,
            ACTION_VOICE_SEARCH_RESULTS,
            ACTION_VOICE_SEARCH_HANDS_FREE,
        )

    private val EXTRA_QUERY_KEYS =
        listOf(
            SearchManager.QUERY,
            SearchManager.USER_QUERY,
            MediaStore.EXTRA_MEDIA_TITLE,
            "search_query",
            "q",
            "text",
            "keyword",
            Intent.EXTRA_TEXT,
            "amazon.intent.extra.DATA_EXTRA_NAME",
        )

    private val URI_QUERY_KEYS = listOf("q", "query", "search", "text")

    private val WAKE_WORD_REGEX =
        Regex(
            pattern = "^(?:alexa|ok\\s+google|hey\\s+google|oye\\s+google)[\\s,:]+",
            option = RegexOption.IGNORE_CASE,
        )

    private val COMMAND_VERB_REGEX =
        Regex(
            pattern =
                "^(?:quiero\\s+ver|search\\s+for|búscame|buscame|buscar|busca|encuentra|ponme|pon|reproducir|reproduce|ver|search|find|play|watch)\\s+",
            option = RegexOption.IGNORE_CASE,
        )

    private val TRAILING_APP_CLAUSE_REGEX =
        Regex(
            pattern =
                "\\s+(?:en\\s+jellyx\\.tv|en\\s+jellyx\\s+tv|en\\s+jelly\\s+x|en\\s+jellyx|on\\s+jellyx\\.tv|on\\s+jellyx\\s+tv|on\\s+jelly\\s+x|on\\s+jellyx|in\\s+jellyx\\.tv|in\\s+jellyx\\s+tv|in\\s+jellyx|de\\s+jellyx\\.tv|de\\s+jellyx)[.?!,\\s]*$",
            option = RegexOption.IGNORE_CASE,
        )

    private val MULTI_SPACE_REGEX = Regex("\\s+")

    fun cleanVoiceQuery(raw: String): String {
        val baseline = raw.stripSurroundingQuotesAndWhitespace()
        if (baseline.isEmpty()) return ""

        var cleaned = baseline
        cleaned = cleaned.replace(WAKE_WORD_REGEX, "").stripSurroundingQuotesAndWhitespace()
        cleaned = cleaned.replace(COMMAND_VERB_REGEX, "").stripSurroundingQuotesAndWhitespace()
        cleaned = cleaned.replace(TRAILING_APP_CLAUSE_REGEX, "")
        cleaned =
            cleaned
                .replace(MULTI_SPACE_REGEX, " ")
                .trim { it.isWhitespace() || it == '.' || it == '?' || it == '!' || it == ',' }
                .stripSurroundingQuotesAndWhitespace()

        return cleaned.ifBlank { baseline }
    }

    fun parseRaw(
        action: String?,
        scheme: String?,
        host: String?,
        pathSegments: List<String>,
        uriQueryParams: Map<String, String?>,
        extras: Map<String, String?>,
    ): TvIncomingCommand? {
        if (scheme == "jellyx" && host == "item") {
            val kind = pathSegments.getOrNull(0)?.takeIf { it.isNotBlank() }
            val itemId = pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() }
            return if (kind != null && itemId != null) {
                TvIncomingCommand.OpenItem(kind = kind, itemId = itemId)
            } else {
                null
            }
        }

        if (scheme == "jellyx" && host == "search") {
            val rawQuery =
                URI_QUERY_KEYS.firstNotNullOfOrNull { key ->
                    uriQueryParams[key]?.takeIf { it.isNotBlank() }
                } ?: pathSegments.firstOrNull { it.isNotBlank() }
            val cleaned = rawQuery?.let(::cleanVoiceQuery).orEmpty()
            return if (cleaned.isNotBlank()) {
                TvIncomingCommand.VoiceSearch(query = cleaned)
            } else {
                TvIncomingCommand.OpenSearchScreen
            }
        }

        if (action in SEARCH_ACTIONS) {
            val rawQuery =
                EXTRA_QUERY_KEYS.firstNotNullOfOrNull { key ->
                    extras[key]?.takeIf { it.isNotBlank() }
                }
            val cleaned = rawQuery?.let(::cleanVoiceQuery).orEmpty()
            return if (cleaned.isNotBlank()) {
                TvIncomingCommand.VoiceSearch(
                    query = cleaned,
                    autoPlayFirst = action == ACTION_MEDIA_PLAY_FROM_SEARCH,
                )
            } else {
                TvIncomingCommand.OpenSearchScreen
            }
        }

        return null
    }

    fun parseIntent(intent: Intent?): TvIncomingCommand? {
        if (intent == null) return null

        val data = intent.data
        val uriQueryParams =
            if (data != null) {
                runCatching {
                    data.queryParameterNames.associateWith { name ->
                        runCatching { data.getQueryParameter(name) }.getOrNull()
                    }
                }.getOrDefault(emptyMap())
            } else {
                emptyMap()
            }

        val extrasMap = buildMap {
            val bundle = runCatching { intent.extras }.getOrNull()
            if (bundle != null) {
                runCatching {
                    for (key in bundle.keySet()) {
                        val value =
                            runCatching {
                                bundle.getString(key) ?: bundle.getCharSequence(key)?.toString()
                            }.getOrNull()
                        if (value != null) {
                            put(key, value)
                        }
                    }
                }
            }
            for (key in EXTRA_QUERY_KEYS) {
                if (!containsKey(key)) {
                    val value = runCatching { intent.getStringExtra(key) }.getOrNull()
                    if (value != null) {
                        put(key, value)
                    }
                }
            }
        }

        return parseRaw(
            action = intent.action,
            scheme = data?.scheme,
            host = data?.host,
            pathSegments = data?.pathSegments.orEmpty(),
            uriQueryParams = uriQueryParams,
            extras = extrasMap,
        )
    }

    private fun String.stripSurroundingQuotesAndWhitespace(): String =
        trim { it.isWhitespace() || it == '"' || it == '\'' || it == '«' || it == '»' }
}
