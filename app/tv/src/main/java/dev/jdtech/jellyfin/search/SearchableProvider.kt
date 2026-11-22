package dev.jdtech.jellyfin.search

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.models.JellyxEpisode
import dev.jdtech.jellyfin.models.JellyxMovie
import dev.jdtech.jellyfin.models.JellyxShow
import dev.jdtech.jellyfin.repository.JellyfinRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class SearchableProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SearchableProviderEntryPoint {
        fun jellyfinRepository(): JellyfinRepository
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(COLUMNS)
        val rawQuery =
            selectionArgs?.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: uri.lastPathSegment
                    ?.takeIf { it != SearchManager.SUGGEST_URI_PATH_QUERY }
        val searchQuery = rawQuery?.trim().orEmpty()
        if (searchQuery.length < 2) {
            return cursor
        }

        val appContext = context?.applicationContext ?: return cursor
        val repository =
            runCatching {
                EntryPointAccessors.fromApplication(
                    appContext,
                    SearchableProviderEntryPoint::class.java,
                ).jellyfinRepository()
            }.getOrElse { error ->
                Timber.w(error, "SearchableProvider: failed to resolve JellyfinRepository")
                return cursor
            }

        val items =
            runBlocking(Dispatchers.IO) {
                runCatching { repository.getSearchItems(searchQuery) }
                    .onFailure { Timber.w(it, "SearchableProvider: search failed for %s", searchQuery) }
                    .getOrDefault(emptyList())
            }

        items.forEachIndexed { index, item ->
            val routeTarget =
                when (item) {
                    is JellyxMovie -> "movie" to item.id.toString()
                    is JellyxShow -> "show" to item.id.toString()
                    is JellyxEpisode -> "show" to item.seriesId.toString()
                    else -> null
                } ?: return@forEachIndexed

            val (kind, targetId) = routeTarget
            val subtitle = item.overview.takeIf { it.isNotBlank() } ?: ""
            val posterUri = item.images.primary?.toString()

            cursor.addRow(
                arrayOf<Any?>(
                    index.toLong(),
                    item.name,
                    subtitle,
                    posterUri,
                    "video/mp4",
                    Intent.ACTION_VIEW,
                    "jellyx://item/$kind/$targetId",
                ),
            )
        }

        return cursor
    }

    override fun getType(uri: Uri): String = SearchManager.SUGGEST_MIME_TYPE

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private companion object {
        val COLUMNS =
            arrayOf(
                BaseColumns._ID,
                SearchManager.SUGGEST_COLUMN_TEXT_1,
                SearchManager.SUGGEST_COLUMN_TEXT_2,
                SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE,
                SearchManager.SUGGEST_COLUMN_CONTENT_TYPE,
                SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
                SearchManager.SUGGEST_COLUMN_INTENT_DATA,
            )
    }
}

