package dev.jdtech.jellyfin.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateModelsTest {

    @Test
    fun `compareSemver and isUpdateNewer compare version codes and tags accurately`() {
        assertTrue(AppUpdateParser.compareSemver("v0.2.0", "0.1.4") > 0)
        assertTrue(AppUpdateParser.compareSemver("0.1.4", "0.1.4") == 0)
        assertTrue(AppUpdateParser.compareSemver("0.1.3", "0.1.4") < 0)

        assertTrue(
            AppUpdateParser.isUpdateNewer(
                remoteCode = 6,
                remoteName = "0.1.4",
                currentCode = 5,
                currentName = "0.1.4",
            )
        )
        assertFalse(
            AppUpdateParser.isUpdateNewer(
                remoteCode = 5,
                remoteName = "0.1.4",
                currentCode = 5,
                currentName = "0.1.4",
            )
        )
    }

    @Test
    fun `selectAssetForPlatform picks right APK from single GitHub release for TV vs Mobile`() {
        val assets =
            listOf(
                ReleaseAsset("jellyx-mobile-v0.2.0-release.apk", "https://gh/mobile.apk"),
                ReleaseAsset("jellyx-mobile-v0.2.0-release.apk.sha256", "https://gh/mobile.sha256"),
                ReleaseAsset("jellyx-tv-v0.2.0-release.apk", "https://gh/tv.apk"),
                ReleaseAsset("jellyx-tv-v0.2.0-release.apk.sha256", "https://gh/tv.sha256"),
            )

        val tvApk = AppUpdateParser.selectAssetForPlatform(assets, isTv = true)
        val mobileApk = AppUpdateParser.selectAssetForPlatform(assets, isTv = false)

        assertEquals("https://gh/tv.apk", tvApk?.downloadUrl)
        assertEquals("https://gh/mobile.apk", mobileApk?.downloadUrl)
        assertEquals(
            "https://gh/tv.sha256",
            AppUpdateParser.selectSha256AssetForApk(assets, tvApk!!.name)?.downloadUrl,
        )
    }

    @Test
    fun `parseGitHubReleaseJson extracts version, APK URL, and sha256 sidecar`() {
        val raw =
            """
            {
              "tag_name": "v0.2.0",
              "body": "Mejoras de rendimiento",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "jellyx-tv-v0.2.0-universal-release.apk",
                  "browser_download_url": "https://github.com/user/jellyx.tv/releases/download/v0.2.0/jellyx-tv.apk"
                },
                {
                  "name": "jellyx-mobile-v0.2.0-universal-release.apk",
                  "browser_download_url": "https://github.com/user/jellyx.tv/releases/download/v0.2.0/jellyx-mobile.apk"
                }
              ]
            }
            """.trimIndent()

        val parsedTv = AppUpdateParser.parseGitHubReleaseJson(raw, isTv = true)
        val parsedMobile = AppUpdateParser.parseGitHubReleaseJson(raw, isTv = false)

        assertNotNull(parsedTv)
        assertNotNull(parsedMobile)
        assertEquals("0.2.0", parsedTv!!.first.versionName)
        assertTrue(parsedTv.first.apkUrl.endsWith("jellyx-tv.apk"))
        assertTrue(parsedMobile!!.first.apkUrl.endsWith("jellyx-mobile.apk"))
    }

    @Test
    fun `parseUpdateManifestJson selects platform node`() {
        val manifest =
            """
            {
              "tv": {
                "versionName": "0.2.1",
                "versionCode": 8,
                "apkUrl": "https://cdn.jellyx.tv/tv.apk",
                "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "mandatory": true
              },
              "mobile": {
                "versionName": "0.2.1",
                "versionCode": 8,
                "apkUrl": "https://cdn.jellyx.tv/mobile.apk"
              }
            }
            """.trimIndent()

        val tv = AppUpdateParser.parseUpdateManifestJson(manifest, isTv = true)
        val mobile = AppUpdateParser.parseUpdateManifestJson(manifest, isTv = false)

        assertEquals("https://cdn.jellyx.tv/tv.apk", tv?.apkUrl)
        assertTrue(tv?.mandatory == true)
        assertEquals("https://cdn.jellyx.tv/mobile.apk", mobile?.apkUrl)
        assertNull(AppUpdateParser.parseUpdateManifestJson("{}", isTv = true))
    }
}

