package dev.jdtech.jellyfin.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Int = 0,
    val apkUrl: String,
    val sha256: String? = null,
    val releaseNotes: String = "",
    val mandatory: Boolean = false,
)

@Serializable
data class AppUpdateManifest(
    val tv: AppUpdateInfo? = null,
    val mobile: AppUpdateInfo? = null,
)

@Serializable
data class GitHubAssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<GitHubAssetDto> = emptyList(),
)

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
)

object AppUpdateParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun compareSemver(remote: String, current: String): Int {
        val remoteParts = parseNumericParts(remote)
        val currentParts = parseNumericParts(current)
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r.compareTo(c)
        }
        return 0
    }

    fun isUpdateNewer(
        remoteCode: Int,
        remoteName: String,
        currentCode: Int,
        currentName: String,
    ): Boolean {
        if (remoteCode > 0 && remoteCode > currentCode) {
            return true
        }
        return compareSemver(remoteName, currentName) > 0
    }

    fun selectAssetForPlatform(assets: List<ReleaseAsset>, isTv: Boolean): ReleaseAsset? {
        val apkAssets =
            assets.filter {
                it.name.endsWith(".apk", ignoreCase = true) && it.downloadUrl.isNotBlank()
            }
        if (apkAssets.isEmpty()) return null

        val primaryTokens = if (isTv) listOf("tv") else listOf("mobile", "phone")
        val excludedToken = if (isTv) listOf("mobile", "phone") else listOf("tv")

        return apkAssets.firstOrNull { asset ->
            val lower = asset.name.lowercase()
            primaryTokens.any { lower.contains(it) } && excludedToken.none { lower.contains(it) }
        }
            ?: apkAssets.firstOrNull { asset ->
                val lower = asset.name.lowercase()
                primaryTokens.any { lower.contains(it) }
            }
            ?: if (apkAssets.size == 1) apkAssets.first() else null
    }

    fun selectSha256AssetForApk(assets: List<ReleaseAsset>, apkAssetName: String): ReleaseAsset? {
        val expectedSidecar = "$apkAssetName.sha256"
        return assets.firstOrNull {
            it.name.equals(expectedSidecar, ignoreCase = true) && it.downloadUrl.isNotBlank()
        }
            ?: assets.firstOrNull {
                it.name.contains("sha256", ignoreCase = true) && it.downloadUrl.isNotBlank()
            }
    }

    fun extractSha256FromText(text: String, apkAssetName: String? = null): String? {
        val lines = text.lines()
        if (!apkAssetName.isNullOrBlank()) {
            val matchedLine = lines.firstOrNull { it.contains(apkAssetName, ignoreCase = true) }
            if (matchedLine != null) {
                SHA256_REGEX.find(matchedLine)?.value?.lowercase()?.let { return it }
            }
        }
        return SHA256_REGEX.find(text)?.value?.lowercase()
    }

    fun parseUpdateManifestJson(rawJson: String, isTv: Boolean): AppUpdateInfo? {
        val element = runCatching { json.parseToJsonElement(rawJson) }.getOrNull() as? JsonObject
            ?: return null
        if (element.containsKey("tv") || element.containsKey("mobile")) {
            val manifest = runCatching { json.decodeFromJsonElement<AppUpdateManifest>(element) }.getOrNull()
            return if (isTv) manifest?.tv else manifest?.mobile
        }
        if (element.containsKey("apkUrl") && element.containsKey("versionName")) {
            return runCatching { json.decodeFromJsonElement<AppUpdateInfo>(element) }.getOrNull()
        }
        return null
    }

    fun parseGitHubReleaseJson(rawJson: String, isTv: Boolean): Pair<AppUpdateInfo, String?>? {
        val release = runCatching { json.decodeFromString<GitHubReleaseDto>(rawJson) }.getOrNull()
            ?: return null
        return mapGitHubRelease(release, isTv)
    }

    fun parseGitHubReleaseListJson(rawJson: String, isTv: Boolean): Pair<AppUpdateInfo, String?>? {
        val releases = runCatching { json.decodeFromString<List<GitHubReleaseDto>>(rawJson) }.getOrNull()
            ?: return null
        for (release in releases) {
            mapGitHubRelease(release, isTv)?.let { return it }
        }
        return null
    }

    private fun mapGitHubRelease(release: GitHubReleaseDto, isTv: Boolean): Pair<AppUpdateInfo, String?>? {
        if (release.draft || release.prerelease) return null

        val mappedAssets =
            release.assets.map { ReleaseAsset(name = it.name, downloadUrl = it.browserDownloadUrl) }
        val apkAsset = selectAssetForPlatform(mappedAssets, isTv) ?: return null
        val shaAsset = selectSha256AssetForApk(mappedAssets, apkAsset.name)
        val cleanVersionName = release.tagName.trim().removePrefix("v").removePrefix("V")
        if (cleanVersionName.isBlank()) return null

        val info =
            AppUpdateInfo(
                versionName = cleanVersionName,
                versionCode = 0,
                apkUrl = apkAsset.downloadUrl,
                sha256 = null,
                releaseNotes = release.body.orEmpty().trim(),
                mandatory = false,
            )
        return info to shaAsset?.downloadUrl
    }

    private fun parseNumericParts(version: String): List<Int> {
        val cleaned = version.trim().removePrefix("v").removePrefix("V").substringBefore("-")
        return cleaned.split(".").map { segment ->
            segment.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
    }

    private val SHA256_REGEX = Regex("\\b[a-fA-F0-9]{64}\\b")
}

