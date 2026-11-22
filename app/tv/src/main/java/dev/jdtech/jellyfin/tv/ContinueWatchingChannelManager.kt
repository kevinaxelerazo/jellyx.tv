package dev.jdtech.jellyfin.tv

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.JellyxEpisode
import dev.jdtech.jellyfin.models.JellyxItem
import dev.jdtech.jellyfin.models.JellyxMovie
import dev.jdtech.jellyfin.models.JellyxShow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

object ContinueWatchingChannelManager {
    private const val CHANNEL_INTERNAL_ID = "jellyx_continue_watching"
    private const val PREFS_NAME = "jellyx_tv_channels"
    private const val KEY_CHANNEL_ID = "continue_watching_channel_id"

    suspend fun syncContinueWatching(context: Context, items: List<JellyxItem>) =
        withContext(Dispatchers.IO) {
            runCatching {
                    val channelId = getOrCreateChannelId(context) ?: return@runCatching
                    val resolver = context.contentResolver

                    // Clear existing preview programs for this channel before re-publishing
                    resolver.delete(
                        TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
                        null,
                        null,
                    )

                    items.take(12).forEachIndexed { index, item ->
                        val deepLinkUri =
                            when (item) {
                                is JellyxMovie -> Uri.parse("jellyx://item/movie/${item.id}")
                                is JellyxShow -> Uri.parse("jellyx://item/show/${item.id}")
                                is JellyxEpisode ->
                                    Uri.parse("jellyx://item/show/${item.seriesId}")
                                else -> null
                            } ?: return@forEachIndexed

                        val title =
                            if (item is JellyxEpisode) {
                                "${item.seriesName} - S${item.parentIndexNumber}:E${item.indexNumber} ${item.name}"
                            } else {
                                item.name
                            }

                        val posterUri = item.images.backdrop ?: item.images.primary
                        val programType =
                            if (item is JellyxEpisode) {
                                TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE
                            } else {
                                TvContractCompat.PreviewPrograms.TYPE_MOVIE
                            }

                        val builder =
                            PreviewProgram.Builder()
                                .setChannelId(channelId)
                                .setType(programType)
                                .setTitle(title)
                                .setDescription(item.overview)
                                .setPosterArtUri(posterUri)
                                .setIntentUri(deepLinkUri)
                                .setInternalProviderId(item.id.toString())
                                .setWeight(items.size - index)

                        resolver.insert(
                            TvContractCompat.PreviewPrograms.CONTENT_URI,
                            builder.build().toContentValues(),
                        )
                    }
                }
                .onFailure {
                    Timber.w(it, "Failed to sync Android TV Continue Watching channel")
                }
        }

    private fun getOrCreateChannelId(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedId = prefs.getLong(KEY_CHANNEL_ID, -1L)
        if (savedId != -1L) {
            return savedId
        }

        val appLinkIntent =
            Intent(Intent.ACTION_VIEW, Uri.parse("jellyx://item/home")).apply {
                setPackage(context.packageName)
            }

        val channel =
            Channel.Builder()
                .setType(TvContractCompat.Channels.TYPE_PREVIEW)
                .setDisplayName("Continuar viendo")
                .setAppLinkIntent(appLinkIntent)
                .setInternalProviderId(CHANNEL_INTERNAL_ID)
                .build()

        val uri =
            context.contentResolver.insert(
                TvContractCompat.Channels.CONTENT_URI,
                channel.toContentValues(),
            ) ?: return null

        val channelId = ContentUris.parseId(uri)
        prefs.edit().putLong(KEY_CHANNEL_ID, channelId).apply()

        runCatching {
            val logo = BitmapFactory.decodeResource(context.resources, CoreR.mipmap.ic_launcher)
            if (logo != null) {
                androidx.tvprovider.media.tv.ChannelLogoUtils.storeChannelLogo(
                    context,
                    channelId,
                    logo,
                )
            }
        }

        runCatching { TvContractCompat.requestChannelBrowsable(context, channelId) }
        return channelId
    }
}

