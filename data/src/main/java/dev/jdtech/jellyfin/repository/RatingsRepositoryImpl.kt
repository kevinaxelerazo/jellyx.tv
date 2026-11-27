package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.data.BuildConfig
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.JellyxEpisode
import dev.jdtech.jellyfin.models.JellyxItem
import dev.jdtech.jellyfin.models.JellyxMovie
import dev.jdtech.jellyfin.models.JellyxRatings
import dev.jdtech.jellyfin.models.JellyxRatingsDto
import dev.jdtech.jellyfin.models.JellyxShow
import dev.jdtech.jellyfin.models.RottenTomatoesStatus
import dev.jdtech.jellyfin.models.toJellyxRatings
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber

class RatingsRepositoryImpl(
    private val database: ServerDatabaseDao,
    private val omdbApiKey: String = BuildConfig.OMDB_API_KEY,
    private val tmdbApiKey: String = BuildConfig.TMDB_API_KEY,
    private val mdblistApiKey: String = BuildConfig.MDBLIST_API_KEY,
    private val httpClient: OkHttpClient = DEFAULT_HTTP_CLIENT,
    private val clockMs: () -> Long = System::currentTimeMillis,
) : RatingsRepository {

    override fun extractNativeRatings(
        communityRating: Float?,
        criticRating: Float?,
    ): JellyxRatings {
        val rtPct = criticRating?.roundToInt()?.coerceIn(0, 100)
        val rtStatus = rtPct?.let {
            if (it >= RT_FRESH_THRESHOLD) {
                RottenTomatoesStatus.FRESH
            } else {
                RottenTomatoesStatus.ROTTEN
            }
        }
        return JellyxRatings(
            imdbRating = communityRating,
            tmdbPercentage = null,
            rtCriticPercentage = rtPct,
            rtCriticStatus = rtStatus,
            rtAudiencePercentage = null,
        )
    }

    override fun extractNativeRatings(item: JellyxItem): JellyxRatings {
        val communityRating =
            when (item) {
                is JellyxMovie -> item.communityRating
                is JellyxShow -> item.communityRating
                is JellyxEpisode -> item.communityRating
                else -> null
            }
        return extractNativeRatings(communityRating, item.criticRating)
    }

    override suspend fun getEnrichedRatings(
        itemId: UUID,
        communityRating: Float?,
        criticRating: Float?,
        providerIds: Map<String, String>,
        isSeries: Boolean,
    ): JellyxRatings =
        withContext(Dispatchers.IO) {
            val native = extractNativeRatings(communityRating, criticRating)

            val cached = runCatching { database.getRatings(itemId) }.getOrNull()
            val now = clockMs()
            if (cached != null && (now - cached.updatedAtEpochMs) <= CACHE_TTL_MS) {
                return@withContext mergeRatings(native, cached.toJellyxRatings())
            }

            val imdbId =
                providerIds.entries.firstOrNull { it.key.equals("Imdb", ignoreCase = true) }?.value
            val tmdbId =
                providerIds.entries.firstOrNull { it.key.equals("Tmdb", ignoreCase = true) }?.value

            val hasApiKeys =
                omdbApiKey.isNotBlank() || tmdbApiKey.isNotBlank() || mdblistApiKey.isNotBlank()
            if (!hasApiKeys || (imdbId.isNullOrBlank() && tmdbId.isNullOrBlank())) {
                return@withContext native.copy(
                    tmdbPercentage =
                        native.tmdbPercentage ?: deriveTmdbFromCommunity(communityRating, tmdbId)
                )
            }

            var imdbScore = native.imdbRating
            var rtCritic = native.rtCriticPercentage
            var rtAudience = native.rtAudiencePercentage
            var tmdbPct = native.tmdbPercentage

            if (omdbApiKey.isNotBlank() && !imdbId.isNullOrBlank()) {
                fetchOmdbRatings(imdbId)?.let { omdb ->
                    if (omdb.imdbRating != null) imdbScore = omdb.imdbRating
                    if (omdb.rtCriticPercentage != null) rtCritic = omdb.rtCriticPercentage
                    if (omdb.rtAudiencePercentage != null) rtAudience = omdb.rtAudiencePercentage
                }
            }

            if (mdblistApiKey.isNotBlank() && !imdbId.isNullOrBlank()) {
                fetchMdblistRatings(imdbId)?.let { mdb ->
                    if (imdbScore == null && mdb.imdbRating != null) imdbScore = mdb.imdbRating
                    if (rtCritic == null && mdb.rtCriticPercentage != null) {
                        rtCritic = mdb.rtCriticPercentage
                    }
                    if (rtAudience == null && mdb.rtAudiencePercentage != null) {
                        rtAudience = mdb.rtAudiencePercentage
                    }
                    if (tmdbPct == null && mdb.tmdbPercentage != null) tmdbPct = mdb.tmdbPercentage
                }
            }

            if (tmdbPct == null && tmdbApiKey.isNotBlank() && !tmdbId.isNullOrBlank()) {
                tmdbPct = fetchTmdbPercentage(tmdbId = tmdbId, isSeries = isSeries)
            }

            if (tmdbPct == null) {
                tmdbPct = deriveTmdbFromCommunity(communityRating, tmdbId)
            }

            val dto =
                JellyxRatingsDto(
                    itemId = itemId,
                    imdbRating = imdbScore,
                    tmdbPercentage = tmdbPct,
                    rtCriticPercentage = rtCritic,
                    rtAudiencePercentage = rtAudience,
                    updatedAtEpochMs = now,
                )
            runCatching { database.insertRatings(dto) }

            dto.toJellyxRatings()
        }

    private fun deriveTmdbFromCommunity(communityRating: Float?, tmdbId: String?): Int? =
        if (!tmdbId.isNullOrBlank() && communityRating != null) {
            (communityRating * 10f).roundToInt().coerceIn(0, 100)
        } else {
            null
        }

    private fun mergeRatings(native: JellyxRatings, cached: JellyxRatings): JellyxRatings {
        val rtPct = cached.rtCriticPercentage ?: native.rtCriticPercentage
        val rtStatus = rtPct?.let {
            if (it >= RT_FRESH_THRESHOLD) RottenTomatoesStatus.FRESH
            else RottenTomatoesStatus.ROTTEN
        }
        return JellyxRatings(
            imdbRating = cached.imdbRating ?: native.imdbRating,
            tmdbPercentage = cached.tmdbPercentage ?: native.tmdbPercentage,
            rtCriticPercentage = rtPct,
            rtCriticStatus = rtStatus,
            rtAudiencePercentage = cached.rtAudiencePercentage,
        )
    }

    private fun fetchOmdbRatings(imdbId: String): JellyxRatings? {
        return runCatching {
            val url = "https://www.omdbapi.com/?i=$imdbId&apikey=$omdbApiKey"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                val json = JSONObject(body)
                if (json.optString("Response") == "False") return@runCatching null

                val imdb = json.optString("imdbRating").toFloatOrNull()
                var rtCritic: Int? = null
                val ratingsArr = json.optJSONArray("Ratings")
                if (ratingsArr != null) {
                    for (i in 0 until ratingsArr.length()) {
                        val obj = ratingsArr.optJSONObject(i) ?: continue
                        if (obj.optString("Source") == "Rotten Tomatoes") {
                            rtCritic = obj.optString("Value").removeSuffix("%").trim().toIntOrNull()
                        }
                    }
                }
                JellyxRatings(
                    imdbRating = imdb,
                    rtCriticPercentage = rtCritic,
                    rtCriticStatus =
                        rtCritic?.let {
                            if (it >= RT_FRESH_THRESHOLD) RottenTomatoesStatus.FRESH
                            else RottenTomatoesStatus.ROTTEN
                        },
                )
            }
        }
            .onFailure { Timber.w(it, "OMDb fetch failed") }
            .getOrNull()
    }

    private fun fetchMdblistRatings(imdbId: String): JellyxRatings? {
        return runCatching {
            val url = "https://mdblist.com/api/?i=$imdbId&apikey=$mdblistApiKey"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                val json = JSONObject(body)
                val ratingsArr = json.optJSONArray("ratings") ?: return@runCatching null

                var imdb: Float? = null
                var rtCritic: Int? = null
                var rtAudience: Int? = null
                var tmdb: Int? = null

                for (i in 0 until ratingsArr.length()) {
                    val obj = ratingsArr.optJSONObject(i) ?: continue
                    val source = obj.optString("source")
                    val value = obj.optDouble("value", Double.NaN)
                    if (value.isNaN()) continue
                    when (source) {
                        "imdb" -> imdb = value.toFloat()
                        "tomatoes" -> rtCritic = value.roundToInt()
                        "tomatoesaudience" -> rtAudience = value.roundToInt()
                        "tmdb" -> tmdb = value.roundToInt()
                    }
                }
                JellyxRatings(
                    imdbRating = imdb,
                    tmdbPercentage = tmdb,
                    rtCriticPercentage = rtCritic,
                    rtCriticStatus =
                        rtCritic?.let {
                            if (it >= RT_FRESH_THRESHOLD) RottenTomatoesStatus.FRESH
                            else RottenTomatoesStatus.ROTTEN
                        },
                    rtAudiencePercentage = rtAudience,
                )
            }
        }
            .onFailure { Timber.w(it, "MDBList fetch failed") }
            .getOrNull()
    }

    private fun fetchTmdbPercentage(tmdbId: String, isSeries: Boolean = false): Int? {
        val primaryType = if (isSeries) "tv" else "movie"
        val fallbackType = if (isSeries) "movie" else "tv"
        return fetchTmdbEndpointPercentage(primaryType, tmdbId)
            ?: fetchTmdbEndpointPercentage(fallbackType, tmdbId)
    }

    private fun fetchTmdbEndpointPercentage(mediaType: String, tmdbId: String): Int? {
        return runCatching {
            val url = "https://api.themoviedb.org/3/$mediaType/$tmdbId?api_key=$tmdbApiKey"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                val json = JSONObject(body)
                val voteAverage = json.optDouble("vote_average", Double.NaN)
                if (voteAverage.isNaN() || voteAverage <= 0.0) null
                else (voteAverage * 10.0).roundToInt().coerceIn(0, 100)
            }
        }
            .onFailure { Timber.w(it, "TMDb fetch failed (%s)", mediaType) }
            .getOrNull()
    }

    companion object {
        private const val RT_FRESH_THRESHOLD = 60
        const val CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L

        private val DEFAULT_HTTP_CLIENT: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
        }
    }
}
