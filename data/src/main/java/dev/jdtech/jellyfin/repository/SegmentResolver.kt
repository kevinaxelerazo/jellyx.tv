package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.JellyxChapter
import dev.jdtech.jellyfin.models.JellyxSegment
import dev.jdtech.jellyfin.models.JellyxSegmentType
import dev.jdtech.jellyfin.models.toJellyxChapters
import dev.jdtech.jellyfin.models.toJellyxSegment
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

class SegmentResolver(
    private val jellyfinApi: JellyfinApi,
    private val database: ServerDatabaseDao,
    private val httpClient: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build(),
) {
    suspend fun resolveSegments(
        itemId: UUID,
        chapters: List<JellyxChapter>? = null,
        durationMs: Long? = null,
    ): List<JellyxSegment> {
        val databaseSegments = runCatching {
            database.getSegments(itemId).map { it.toJellyxSegment() }
        }
            .getOrDefault(emptyList())
        if (databaseSegments.isNotEmpty()) {
            return databaseSegments
        }

        val officialSegments = runCatching {
            jellyfinApi.mediaSegmentApi.getItemSegments(itemId).content.items.map {
                it.toJellyxSegment()
            }
        }
            .onFailure { Timber.d(it, "Official MediaSegment API failed for %s", itemId) }
            .getOrDefault(emptyList())
        if (officialSegments.isNotEmpty()) {
            return officialSegments
        }

        val introSkipperSegments = fetchIntroSkipperSegments(itemId)
        if (introSkipperSegments.isNotEmpty()) {
            return introSkipperSegments
        }

        val resolvedChapters =
            chapters
                ?: runCatching {
                    val userId = jellyfinApi.userId ?: return@runCatching null
                    val item = jellyfinApi.libraryApi.getItem(itemId, userId).content
                    item.toJellyxChapters() to (item.runTimeTicks?.div(10_000L))
                }
                    .getOrNull()
                    ?.let { (ch, dur) ->
                        return extractSegmentsFromChapters(ch, dur)
                    }

        return extractSegmentsFromChapters(resolvedChapters.orEmpty(), durationMs)
    }

    private fun fetchIntroSkipperSegments(itemId: UUID): List<JellyxSegment> {
        val baseUrl = jellyfinApi.api.baseUrl?.trimEnd('/') ?: return emptyList()
        if (baseUrl.isBlank()) return emptyList()

        return runCatching {
                val requestBuilder =
                    Request.Builder().url("$baseUrl/Episode/$itemId/IntroTimestamps")
                jellyfinApi.api.accessToken
                    ?.takeIf { it.isNotBlank() }
                    ?.let { token -> requestBuilder.header("X-Emby-Token", token) }
                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching emptyList()
                    val body = response.body.string()
                    parseIntroSkipperJson(body)
                }
            }
            .onFailure { Timber.d(it, "IntroSkipper fallback failed for %s", itemId) }
            .getOrDefault(emptyList())
    }

    internal fun parseIntroSkipperJson(jsonString: String): List<JellyxSegment> {
        if (jsonString.isBlank()) return emptyList()
        val json =
            runCatching { Json.parseToJsonElement(jsonString).jsonObject }.getOrNull()
                ?: return emptyList()
        val result = mutableListOf<JellyxSegment>()

        parseSegmentObject(json["Introduction"] as? JsonObject, JellyxSegmentType.INTRO)
            ?.let(result::add)
        parseSegmentObject(json["Credits"] as? JsonObject, JellyxSegmentType.OUTRO)
            ?.let(result::add)

        if (result.isEmpty() && !json.containsKey("Introduction") && !json.containsKey("Credits")) {
            parseSegmentObject(json, JellyxSegmentType.INTRO)?.let(result::add)
        }

        return result
    }

    private fun parseSegmentObject(
        obj: JsonObject?,
        type: JellyxSegmentType,
    ): JellyxSegment? {
        obj ?: return null
        val isValid = (obj["Valid"] as? JsonPrimitive)?.booleanOrNull
        if (isValid == false) return null
        val startSec = (obj["IntroStart"] as? JsonPrimitive)?.doubleOrNull ?: return null
        val endSec = (obj["IntroEnd"] as? JsonPrimitive)?.doubleOrNull ?: return null
        if (endSec <= startSec) return null
        return JellyxSegment(
            type = type,
            startTicks = (startSec * 1000.0).roundToLong(),
            endTicks = (endSec * 1000.0).roundToLong(),
        )
    }

    internal fun extractSegmentsFromChapters(
        chapters: List<JellyxChapter>,
        durationMs: Long? = null,
    ): List<JellyxSegment> {
        if (chapters.isEmpty()) return emptyList()
        val segments = mutableListOf<JellyxSegment>()

        for (index in chapters.indices) {
            val chapter = chapters[index]
            val name = chapter.name?.trim().orEmpty()
            if (name.isEmpty()) continue

            val nextStart = chapters.getOrNull(index + 1)?.startPosition ?: durationMs
            when {
                INTRO_CHAPTER_REGEX.containsMatchIn(name) -> {
                    if (nextStart != null) {
                        val lengthMs = nextStart - chapter.startPosition
                        if (lengthMs in MIN_INTRO_DURATION_MS..MAX_INTRO_DURATION_MS) {
                            segments.add(
                                JellyxSegment(
                                    type = JellyxSegmentType.INTRO,
                                    startTicks = chapter.startPosition,
                                    endTicks = nextStart,
                                )
                            )
                        }
                    }
                }
                OUTRO_CHAPTER_REGEX.containsMatchIn(name) -> {
                    val endPos = nextStart ?: (chapter.startPosition + DEFAULT_OUTRO_FALLBACK_MS)
                    if (endPos > chapter.startPosition) {
                        segments.add(
                            JellyxSegment(
                                type = JellyxSegmentType.OUTRO,
                                startTicks = chapter.startPosition,
                                endTicks = endPos,
                            )
                        )
                    }
                }
            }
        }
        return segments
    }

    companion object {
        private val INTRO_CHAPTER_REGEX =
            Regex("^(intro|opening|op|pr[oó]logo)\\b", RegexOption.IGNORE_CASE)
        private val OUTRO_CHAPTER_REGEX =
            Regex("^(outro|ending|ed|credits|cr[eé]ditos)\\b", RegexOption.IGNORE_CASE)
        private const val MIN_INTRO_DURATION_MS = 15_000L
        private const val MAX_INTRO_DURATION_MS = 180_000L
        private const val DEFAULT_OUTRO_FALLBACK_MS = 120_000L
    }
}
