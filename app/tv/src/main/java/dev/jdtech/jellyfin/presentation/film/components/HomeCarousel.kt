package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Carousel
import androidx.tv.material3.CarouselState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovies
import dev.jdtech.jellyfin.film.presentation.home.HomeAction
import dev.jdtech.jellyfin.models.JellyxItem
import dev.jdtech.jellyfin.presentation.theme.JellyxTheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeCarousel(
    items: List<JellyxItem>,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    var activeIndex by remember(items.size) { mutableIntStateOf(0) }
    val carouselState = remember(activeIndex) {
        CarouselState(initialActiveItemIndex = activeIndex.coerceIn(0, items.lastIndex))
    }

    Carousel(
        itemCount = items.size,
        carouselState = carouselState,
        modifier = modifier
            .height(300.dp)
            .fillMaxWidth()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown || items.size <= 1) {
                    return@onPreviewKeyEvent false
                }
                when (keyEvent.key) {
                    Key.DirectionRight -> {
                        activeIndex = (carouselState.activeItemIndex + 1) % items.size
                        true
                    }
                    Key.DirectionLeft -> {
                        activeIndex = (carouselState.activeItemIndex - 1 + items.size) % items.size
                        true
                    }
                    else -> false
                }
            },
        contentTransformEndToStart = fadeIn(tween(1000)).togetherWith(fadeOut(tween(1000))),
        contentTransformStartToEnd = fadeIn(tween(1000)).togetherWith(fadeOut(tween(1000))),
    ) { itemIndex ->
        val item = items[itemIndex]
        HomeCarouselItem(item = item, onAction = onAction)
    }
}

@Composable
@Preview(showBackground = true)
private fun HomeCarouselPreview() {
    JellyxTheme { HomeCarousel(items = dummyMovies, onAction = {}) }
}
