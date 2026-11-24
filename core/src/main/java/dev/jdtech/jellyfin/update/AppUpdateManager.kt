package dev.jdtech.jellyfin.update

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.data.BuildConfig as DataBuildConfig
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

sealed interface ManualUpdateCheckResult {
    data class UpdateAvailable(val info: AppUpdateInfo) : ManualUpdateCheckResult

    data class UpToDate(val currentVersion: String) : ManualUpdateCheckResult

    data class Error(val message: String) : ManualUpdateCheckResult
}

@Singleton
class AppUpdateManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val isTelevisionDevice: Boolean by lazy {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager?
        val isTvMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanback =
            context.packageManager?.hasSystemFeature(PackageManager.FEATURE_LEANBACK) == true
        isTvMode || hasLeanback
    }

    private val _availableUpdate = MutableStateFlow(loadPersistedValidUpdate())
    val availableUpdate: StateFlow<AppUpdateInfo?> = _availableUpdate.asStateFlow()

    private val _isCheckingForUpdates = MutableStateFlow(false)
    val isCheckingForUpdates: StateFlow<Boolean> = _isCheckingForUpdates.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    private val _updateError = MutableStateFlow<String?>(null)
    val updateError: StateFlow<String?> = _updateError.asStateFlow()

    val currentVersionName: String
        get() = DataBuildConfig.VERSION_NAME

    val updateRepo: String
        get() = DataBuildConfig.UPDATE_REPO

    suspend fun checkForUpdates(force: Boolean = false): AppUpdateInfo? =
        withContext(Dispatchers.IO) {
            val manifestUrl = DataBuildConfig.UPDATE_MANIFEST_URL.trim()
            val repo = DataBuildConfig.UPDATE_REPO.trim()
            if (manifestUrl.isBlank() && repo.isBlank()) {
                return@withContext null
            }

            val now = System.currentTimeMillis()
            val lastCheck = appPreferences.getValue(appPreferences.lastUpdateCheckEpochMs)
            if (!force && lastCheck > 0L && (now - lastCheck) < CHECK_INTERVAL_MS) {
                return@withContext _availableUpdate.value
            }

            val remoteUpdate =
                fetchFromManifest(manifestUrl)
                    ?: fetchFromGitHub(repo)
                    ?: return@withContext null

            appPreferences.setValue(appPreferences.lastUpdateCheckEpochMs, now)

            if (
                !AppUpdateParser.isUpdateNewer(
                    remoteCode = remoteUpdate.versionCode,
                    remoteName = remoteUpdate.versionName,
                    currentCode = DataBuildConfig.VERSION_CODE,
                    currentName = DataBuildConfig.VERSION_NAME,
                )
            ) {
                clearPersistedUpdate()
                return@withContext null
            }

            val dismissedVersion = appPreferences.getValue(appPreferences.dismissedUpdateVersion)
            if (!remoteUpdate.mandatory && !force && dismissedVersion == remoteUpdate.versionName) {
                return@withContext null
            }

            persistAvailableUpdate(remoteUpdate)
            remoteUpdate
        }

    suspend fun checkForUpdatesManually(): ManualUpdateCheckResult =
        withContext(Dispatchers.IO) {
            _isCheckingForUpdates.value = true
            try {
                val manifestUrl = DataBuildConfig.UPDATE_MANIFEST_URL.trim()
                val repo = DataBuildConfig.UPDATE_REPO.trim()
                if (manifestUrl.isBlank() && repo.isBlank()) {
                    return@withContext ManualUpdateCheckResult.Error(
                        "No hay repositorio de actualizaciones configurado."
                    )
                }

                val remoteUpdate = fetchFromManifest(manifestUrl) ?: fetchFromGitHub(repo)
                if (remoteUpdate == null) {
                    return@withContext ManualUpdateCheckResult.Error(
                        "No se encontraron releases publicados en $repo o sin conexión."
                    )
                }

                appPreferences.setValue(
                    appPreferences.lastUpdateCheckEpochMs,
                    System.currentTimeMillis(),
                )

                if (
                    !AppUpdateParser.isUpdateNewer(
                        remoteCode = remoteUpdate.versionCode,
                        remoteName = remoteUpdate.versionName,
                        currentCode = DataBuildConfig.VERSION_CODE,
                        currentName = DataBuildConfig.VERSION_NAME,
                    )
                ) {
                    clearPersistedUpdate()
                    return@withContext ManualUpdateCheckResult.UpToDate(DataBuildConfig.VERSION_NAME)
                }

                appPreferences.setValue(appPreferences.dismissedUpdateVersion, null)
                persistAvailableUpdate(remoteUpdate)
                ManualUpdateCheckResult.UpdateAvailable(remoteUpdate)
            } finally {
                _isCheckingForUpdates.value = false
            }
        }

    private fun persistAvailableUpdate(remoteUpdate: AppUpdateInfo) {
        runCatching {
            appPreferences.setValue(
                appPreferences.availableUpdateJson,
                json.encodeToString(remoteUpdate),
            )
        }
        _availableUpdate.value = remoteUpdate
    }

    suspend fun downloadAndInstall(info: AppUpdateInfo) =
        withContext(Dispatchers.IO) {
            _updateError.value = null
            val updatesDir = File(context.cacheDir, UPDATES_DIR_NAME).apply { mkdirs() }
            val apkFile = File(updatesDir, "jellyx-update-${info.versionName}.apk")
            val pendingFile = File(updatesDir, "jellyx-update.apk.download")

            try {
                if (!apkFile.exists() || !verifySha256IfProvided(apkFile, info.sha256)) {
                    apkFile.delete()
                    pendingFile.delete()
                    _downloadProgress.value = 0f

                    val request =
                        Request.Builder()
                            .url(info.apkUrl)
                            .header("User-Agent", "Jellyx.tv/${DataBuildConfig.VERSION_NAME}")
                            .build()

                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IllegalStateException("HTTP ${response.code} al descargar actualización")
                        }
                        val body = response.body
                        val totalBytes = body.contentLength()
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L

                        body.byteStream().use { input ->
                            FileOutputStream(pendingFile).use { output ->
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    output.write(buffer, 0, read)
                                    downloaded += read
                                    if (totalBytes > 0L) {
                                        _downloadProgress.value =
                                            (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                    }
                                }
                                output.flush()
                            }
                        }
                    }

                    if (!verifySha256IfProvided(pendingFile, info.sha256)) {
                        pendingFile.delete()
                        throw IllegalStateException("La firma SHA-256 del APK descargado no coincide.")
                    }

                    if (!pendingFile.renameTo(apkFile)) {
                        pendingFile.copyTo(apkFile, overwrite = true)
                        pendingFile.delete()
                    }
                }

                _downloadProgress.value = null

                if (!context.packageManager.canRequestPackageInstalls()) {
                    _updateError.value =
                        "Permite 'Instalar aplicaciones desconocidas' para Jellyx.tv y vuelve a pulsar Actualizar."
                    runCatching {
                        val settingsIntent =
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${context.packageName}"),
                            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        context.startActivity(settingsIntent)
                    }
                    return@withContext
                }

                val apkUri =
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile,
                    )
                val installIntent =
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                context.startActivity(installIntent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to download/install update")
                _downloadProgress.value = null
                _updateError.value = e.localizedMessage ?: "No se pudo instalar la actualización."
            }
        }

    fun dismissUpdate(info: AppUpdateInfo) {
        if (info.mandatory) return
        appPreferences.setValue(appPreferences.dismissedUpdateVersion, info.versionName)
        clearPersistedUpdate()
    }

    fun clearError() {
        _updateError.value = null
    }

    private fun clearPersistedUpdate() {
        appPreferences.setValue(appPreferences.availableUpdateJson, null)
        _availableUpdate.value = null
    }

    private fun loadPersistedValidUpdate(): AppUpdateInfo? {
        val raw = appPreferences.getValue(appPreferences.availableUpdateJson) ?: return null
        val parsed = runCatching { json.decodeFromString<AppUpdateInfo>(raw) }.getOrNull() ?: return null
        if (
            !AppUpdateParser.isUpdateNewer(
                remoteCode = parsed.versionCode,
                remoteName = parsed.versionName,
                currentCode = DataBuildConfig.VERSION_CODE,
                currentName = DataBuildConfig.VERSION_NAME,
            )
        ) {
            appPreferences.setValue(appPreferences.availableUpdateJson, null)
            return null
        }
        val dismissed = appPreferences.getValue(appPreferences.dismissedUpdateVersion)
        if (!parsed.mandatory && dismissed == parsed.versionName) {
            return null
        }
        return parsed
    }

    private fun fetchFromManifest(manifestUrl: String): AppUpdateInfo? {
        if (manifestUrl.isBlank()) return null
        return runCatching {
            val req = Request.Builder().url(manifestUrl).build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val raw = resp.body.string()
                AppUpdateParser.parseUpdateManifestJson(raw, isTelevisionDevice)
            }
        }.onFailure { Timber.w(it, "Failed to fetch update manifest from %s", manifestUrl) }.getOrNull()
    }

    private fun fetchFromGitHub(repo: String): AppUpdateInfo? {
        if (repo.isBlank()) return null
        return runCatching {
            val apiUrl = "https://api.github.com/repos/$repo/releases/latest"
            val req =
                Request.Builder()
                    .url(apiUrl)
                    .header("Accept", "application/vnd.github+json")
                    .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val raw = resp.body.string()
                val (info, shaAssetUrl) =
                    AppUpdateParser.parseGitHubReleaseJson(raw, isTelevisionDevice) ?: return null
                val resolvedSha =
                    if (!shaAssetUrl.isNullOrBlank()) {
                        fetchSidecarSha256(shaAssetUrl, info.apkUrl.substringAfterLast('/'))
                    } else {
                        null
                    }
                info.copy(sha256 = resolvedSha)
            }
        }.onFailure { Timber.w(it, "Failed to fetch GitHub release for %s", repo) }.getOrNull()
    }

    private fun fetchSidecarSha256(url: String, apkFileName: String): String? {
        return runCatching {
            val req = Request.Builder().url(url).build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                AppUpdateParser.extractSha256FromText(resp.body.string(), apkFileName)
            }
        }.getOrNull()
    }

    private fun verifySha256IfProvided(file: File, expectedSha256: String?): Boolean {
        val expected = expectedSha256?.trim()?.lowercase()?.takeIf { it.length == 64 } ?: return true
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(32 * 1024)
            while (true) {
                val read = input.read(buf)
                if (read == -1) break
                digest.update(buf, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual == expected
    }

    companion object {
        private const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
        private const val UPDATES_DIR_NAME = "updates"
    }
}

