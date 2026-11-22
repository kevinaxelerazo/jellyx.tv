package dev.jdtech.jellyfin.player.local.domain

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

interface NetworkBandwidthEstimator {
    suspend fun estimateBandwidthBps(probeUrl: String? = null): Long
}

class DefaultNetworkBandwidthEstimator
@Inject
constructor(@ApplicationContext private val context: Context) : NetworkBandwidthEstimator {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(350, TimeUnit.MILLISECONDS)
            .readTimeout(350, TimeUnit.MILLISECONDS)
            .build()

    override suspend fun estimateBandwidthBps(probeUrl: String?): Long =
        withContext(Dispatchers.IO) {
            val passiveBps = getPassiveBandwidthBps()

            if (
                !probeUrl.isNullOrBlank() &&
                    (probeUrl.startsWith("http://", ignoreCase = true) ||
                        probeUrl.startsWith("https://", ignoreCase = true))
            ) {
                val probedBps =
                    withTimeoutOrNull(350L) {
                        runCatching { measureHttpRangeProbeBps(probeUrl) }.getOrNull()
                    }
                if (probedBps != null && probedBps > 0L) {
                    return@withContext probedBps
                }
            }

            passiveBps
        }

    private fun getPassiveBandwidthBps(): Long {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager?.activeNetwork ?: return DEFAULT_CONSERVATIVE_BPS
        val capabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return DEFAULT_CONSERVATIVE_BPS

        val downstreamKbps = capabilities.linkDownstreamBandwidthKbps
        val isCellularOrUnknown = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

        return when {
            downstreamKbps > 0 -> {
                val bps = downstreamKbps.toLong() * 1_000L
                if (isCellularOrUnknown) minOf(bps, DEFAULT_CONSERVATIVE_BPS) else bps
            }
            else -> DEFAULT_CONSERVATIVE_BPS
        }
    }

    private fun measureHttpRangeProbeBps(probeUrl: String): Long? {
        val request = Request.Builder().url(probeUrl).header("Range", "bytes=0-1048575").build()

        val startNanos = System.nanoTime()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            val buffer = ByteArray(16 * 1024)
            var totalBytes = 0L
            body.byteStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    totalBytes += read
                    if (totalBytes >= MAX_PROBE_BYTES) break
                }
            }
            val elapsedNanos = (System.nanoTime() - startNanos).coerceAtLeast(1L)
            if (totalBytes < MIN_VALID_PROBE_BYTES) {
                Timber.d(
                    "Micro-probe read only %d bytes; falling back to passive estimate",
                    totalBytes,
                )
                return null
            }
            return (totalBytes * 8L * 1_000_000_000L) / elapsedNanos
        }
    }

    companion object {
        private const val DEFAULT_CONSERVATIVE_BPS = 25_000_000L
        private const val MIN_VALID_PROBE_BYTES = 256L * 1024L
        private const val MAX_PROBE_BYTES = 1024L * 1024L
    }
}
