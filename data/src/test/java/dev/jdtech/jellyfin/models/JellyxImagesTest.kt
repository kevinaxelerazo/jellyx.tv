package dev.jdtech.jellyfin.models

import android.net.Uri
import dev.jdtech.jellyfin.repository.JellyfinRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class JellyxImagesTest {

    @Before
    fun setup() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val base = firstArg<String>().trimEnd('/')
            fakeUri(base, "", emptyMap())
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    private fun fakeUri(
        base: String,
        path: String,
        params: Map<String, String>,
    ): Uri {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.getQueryParameter(any()) } answers { params[firstArg()] }
        every { uri.encodedPath } returns path
        every { uri.toString() } answers {
            val query =
                if (params.isEmpty()) ""
                else "?" + params.entries.joinToString("&") { "${it.key}=${it.value}" }
            "$base/$path$query"
        }
        every { uri.buildUpon() } answers {
            var currentPath = path
            val currentParams = LinkedHashMap(params)
            val builder = mockk<Uri.Builder>(relaxed = true)
            every { builder.appendEncodedPath(any()) } answers {
                val segment = firstArg<String>().trimStart('/')
                currentPath = if (currentPath.isEmpty()) segment else "$currentPath/$segment"
                builder
            }
            every { builder.appendQueryParameter(any(), any()) } answers {
                currentParams[firstArg()] = secondArg()
                builder
            }
            every { builder.build() } answers { fakeUri(base, currentPath, currentParams.toMap()) }
            builder
        }
        return uri
    }

    @Test
    fun `toJellyxImages appends server-side maxWidth and quality query parameters`() {
        val repository = mockk<JellyfinRepository>()
        every { repository.getBaseUrl() } returns "https://demo.jellyx.tv"

        val itemId = UUID.randomUUID()
        val seriesId = UUID.randomUUID()
        val parentBackdropId = UUID.randomUUID()
        val parentLogoId = UUID.randomUUID()

        val dto =
            mockk<BaseItemDto>(relaxed = true) {
                every { id } returns itemId
                every { this@mockk.seriesId } returns seriesId
                every { parentBackdropItemId } returns parentBackdropId
                every { parentLogoItemId } returns parentLogoId
                every { imageTags } returns
                    mapOf(
                        ImageType.PRIMARY to "primary-tag",
                        ImageType.LOGO to "logo-tag",
                    )
                every { backdropImageTags } returns listOf("backdrop-tag")
                every { seriesPrimaryImageTag } returns "series-primary-tag"
                every { parentBackdropImageTags } returns listOf("parent-backdrop-tag")
                every { parentLogoImageTag } returns "parent-logo-tag"
            }

        val images = dto.toJellyxImages(repository)

        assertNotNull(images.primary)
        assertEquals("500", images.primary?.getQueryParameter("maxWidth"))
        assertEquals("85", images.primary?.getQueryParameter("quality"))
        assertEquals("primary-tag", images.primary?.getQueryParameter("tag"))

        assertNotNull(images.backdrop)
        assertEquals("1280", images.backdrop?.getQueryParameter("maxWidth"))
        assertEquals("85", images.backdrop?.getQueryParameter("quality"))
        assertEquals("backdrop-tag", images.backdrop?.getQueryParameter("tag"))

        assertNotNull(images.logo)
        assertEquals("600", images.logo?.getQueryParameter("maxWidth"))
        assertEquals("90", images.logo?.getQueryParameter("quality"))
        assertEquals("logo-tag", images.logo?.getQueryParameter("tag"))

        assertNotNull(images.showPrimary)
        assertEquals("500", images.showPrimary?.getQueryParameter("maxWidth"))
        assertEquals("85", images.showPrimary?.getQueryParameter("quality"))
        assertEquals("series-primary-tag", images.showPrimary?.getQueryParameter("tag"))

        assertNotNull(images.showBackdrop)
        assertEquals("1280", images.showBackdrop?.getQueryParameter("maxWidth"))
        assertEquals("85", images.showBackdrop?.getQueryParameter("quality"))
        assertEquals("parent-backdrop-tag", images.showBackdrop?.getQueryParameter("tag"))

        assertNotNull(images.showLogo)
        assertEquals("600", images.showLogo?.getQueryParameter("maxWidth"))
        assertEquals("90", images.showLogo?.getQueryParameter("quality"))
        assertEquals("parent-logo-tag", images.showLogo?.getQueryParameter("tag"))
    }
}
