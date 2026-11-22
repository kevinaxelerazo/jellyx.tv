package dev.jdtech.jellyfin.presentation.film

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.components.JellyxLoadingLogo
import dev.jdtech.jellyfin.film.presentation.livetv.LiveTvViewModel
import dev.jdtech.jellyfin.film.presentation.livetv.computeProgramProgress
import dev.jdtech.jellyfin.models.JellyxChannel
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LiveTvScreen(
    onChannelClick: (JellyxChannel) -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var initialLoad by rememberSaveable { mutableStateOf(true) }
    val safePadding = rememberSafePadding(handleStartInsets = false)
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val lifecycleOwner = LocalLifecycleOwner.current
    var skipFirstResume by remember { mutableStateOf(true) }

    LaunchedEffect(true) {
        if (initialLoad) {
            viewModel.loadChannels()
            initialLoad = false
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (skipFirstResume) {
                    skipFirstResume = false
                } else {
                    viewModel.loadChannels(silent = true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val nowDateTime =
        remember(state.currentTimeEpochSec) {
            Instant.ofEpochSecond(state.currentTimeEpochSec)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
        }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = MaterialTheme.spacings.medium),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacings.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = stringResource(CoreR.string.live_tv),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${state.filteredChannels.size} / ${state.allChannels.size} canales disponibles",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { viewModel.loadChannels() }) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_rotate_ccw),
                    contentDescription = "Actualizar EPG",
                )
            }
        }

        // Quick Search Field
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            singleLine = true,
            leadingIcon = {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_search),
                    contentDescription = null,
                )
            },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_x),
                            contentDescription = "Limpiar",
                        )
                    }
                }
            },
            placeholder = { Text(text = "Buscar canal o programa en vivo...") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        // Category Filter Chips
        if (state.categories.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(state.categories, key = { it.id }) { category ->
                    FilterChip(
                        selected = category.id == state.selectedCategoryId,
                        onClick = { viewModel.selectCategory(category.id) },
                        label = { Text(text = "${category.label} (${category.count})") },
                    )
                }
            }
        }

        when {
            state.isLoading && state.allChannels.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    JellyxLoadingLogo()
                }
            }
            state.error != null && state.allChannels.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text =
                                state.error?.localizedMessage
                                    ?: "No se pudieron cargar los canales en vivo",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(onClick = { viewModel.loadChannels() }) {
                            Text(text = "Reintentar")
                        }
                    }
                }
            }
            state.filteredChannels.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(CoreR.string.collection_no_media),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = safePadding.bottom + 24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        items = state.filteredChannels,
                        key = { _, channel -> channel.id },
                    ) { index, channel ->
                        val program = channel.currentProgram
                        val progress =
                            computeProgramProgress(
                                start = program?.startDate,
                                end = program?.endDate,
                                now = nowDateTime,
                            )
                        MobileChannelRow(
                            channel = channel,
                            fallbackNumber = index + 1,
                            progress = progress,
                            timeFormatter = timeFormatter,
                            onClick = { onChannelClick(channel) },
                            onToggleFavorite = { viewModel.toggleFavorite(channel) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileChannelRow(
    channel: JellyxChannel,
    fallbackNumber: Int,
    progress: Float?,
    timeFormatter: DateTimeFormatter,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val program = channel.currentProgram
    val nextProgram = channel.nextProgram
    val channelNum = channel.channelNumber?.takeIf { it.isNotBlank() } ?: fallbackNumber.toString()

    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            // Compact Channel Number Badge
            Box(
                modifier =
                    Modifier.width(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = channelNum,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Small Crisp Logo (42x32 dp)
            Box(
                modifier =
                    Modifier.size(width = 42.dp, height = 32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF141820))
                        .border(0.5.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val imageUri = channel.images.primary ?: channel.images.backdrop
                if (imageUri != null) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = channel.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(3.dp),
                    )
                } else {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_tv),
                        contentDescription = channel.displayName,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Channel Name, EPG Current + Progress Bar & Next Program
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = channel.displayName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Box(
                        modifier =
                            Modifier.size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                    )
                    Text(
                        text = channel.group,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                val programText =
                    if (program != null) {
                        val start = program.startDate
                        val end = program.endDate
                        val timePrefix =
                            if (start != null && end != null) {
                                "${start.format(timeFormatter)} - ${end.format(timeFormatter)} • "
                            } else {
                                ""
                            }
                        "$timePrefix${program.name}"
                    } else {
                        "Transmisión en directo"
                    }

                Text(
                    text = programText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (progress != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                                .height(3.5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f))
                    ) {
                        Box(
                            modifier =
                                Modifier.fillMaxWidth(progress.coerceAtLeast(0.03f))
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                if (nextProgram != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "⏭ Siguiente: ${nextProgram.name}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    painter =
                        painterResource(
                            if (channel.favorite) CoreR.drawable.ic_heart_filled
                            else CoreR.drawable.ic_heart
                        ),
                    contentDescription = "Favorito",
                    tint =
                        if (channel.favorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

