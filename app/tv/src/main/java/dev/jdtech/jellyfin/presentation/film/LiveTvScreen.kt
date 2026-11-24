package dev.jdtech.jellyfin.presentation.film

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.components.JellyxLoadingLogo
import dev.jdtech.jellyfin.film.presentation.livetv.LiveTvCategory
import dev.jdtech.jellyfin.film.presentation.livetv.LiveTvViewModel
import dev.jdtech.jellyfin.film.presentation.livetv.computeProgramProgress
import dev.jdtech.jellyfin.models.JellyxChannel
import dev.jdtech.jellyfin.presentation.theme.spacings
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun LiveTvScreen(
    navigateToPlayer: (itemId: UUID, itemKind: BaseItemKind) -> Unit,
    isLoading: (Boolean) -> Unit = {},
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var initialLoad by rememberSaveable { mutableStateOf(true) }
    var skipFirstResume by remember { mutableStateOf(true) }
    val channelListFocusRequester = remember { FocusRequester() }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

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

    LaunchedEffect(state.isLoading) { isLoading(state.isLoading) }

    val nowDateTime =
        remember(state.currentTimeEpochSec) {
            Instant.ofEpochSecond(state.currentTimeEpochSec)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
        }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(horizontal = MaterialTheme.spacings.large)
                .padding(top = MaterialTheme.spacings.small, bottom = MaterialTheme.spacings.default),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Top Header Bar (Search + Live Count + Refresh EPG)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_search),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                placeholder = {
                    Text(
                        text = "Buscar canal o programa...",
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor =
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor =
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    ),
                shape = RoundedCornerShape(10.dp),
                modifier =
                    Modifier.width(270.dp)
                        .onPreviewKeyEvent { keyEvent ->
                            if (
                                keyEvent.type == KeyEventType.KeyDown &&
                                    keyEvent.nativeKeyEvent.keyCode ==
                                        android.view.KeyEvent.KEYCODE_DPAD_DOWN &&
                                    state.filteredChannels.isNotEmpty()
                            ) {
                                runCatching { channelListFocusRequester.requestFocus() }
                                true
                            } else {
                                false
                            }
                        },
            )

            Box(
                modifier =
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF151C28))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier =
                            Modifier.size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                    )
                    Text(
                        text =
                            "${state.filteredChannels.size} / ${state.allChannels.size} canales en vivo",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { viewModel.loadChannels() },
                colors =
                    ButtonDefaults.colors(
                        containerColor = Color(0xFF1A2232),
                        contentColor = Color.White,
                    ),
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_rotate_ccw),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Actualizar EPG", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (state.isLoading && state.allChannels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                JellyxLoadingLogo()
            }
            return@Column
        }

        if (!state.isLoading && state.allChannels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_tv),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.White.copy(alpha = 0.5f),
                    )
                    Text(
                        text =
                            state.error?.localizedMessage
                                ?: stringResource(CoreR.string.collection_no_media),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    Button(onClick = { viewModel.loadChannels() }) {
                        Text(text = "Reintentar")
                    }
                }
            }
            return@Column
        }

        // 3-Panel Layout (Categories | Compact Channel List + EPG | Live EPG Detail & Actions)
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Left Panel: Categories
            LazyColumn(
                modifier =
                    Modifier.width(210.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x990D111A))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(state.categories, key = { it.id }) { category ->
                    CategoryRow(
                        category = category,
                        isSelected = category.id == state.selectedCategoryId,
                        onSelect = { viewModel.selectCategory(category.id) },
                    )
                }
            }

            // Center Panel: Channel List with Small Logos, Readable Titles & EPG Progress
            Box(
                modifier =
                    Modifier.weight(1.15f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x990D111A))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(8.dp)
            ) {
                if (state.filteredChannels.isEmpty() && !state.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No se encontraron canales en esta categoría",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                        modifier = Modifier.fillMaxSize().focusRequester(channelListFocusRequester),
                    ) {
                        itemsIndexed(
                            items = state.filteredChannels,
                            key = { _, channel -> channel.id },
                        ) { index, channel ->
                            val isSelected = state.selectedChannel?.id == channel.id
                            val program = channel.currentProgram
                            val progress =
                                computeProgramProgress(
                                    start = program?.startDate,
                                    end = program?.endDate,
                                    now = nowDateTime,
                                )

                            TvChannelRow(
                                channel = channel,
                                fallbackNumber = index + 1,
                                isSelected = isSelected,
                                progress = progress,
                                timeFormatter = timeFormatter,
                                onFocused = { viewModel.selectChannel(channel) },
                                onClick = {
                                    navigateToPlayer(channel.id, BaseItemKind.TV_CHANNEL)
                                },
                            )
                        }
                    }
                }
            }

            // Right Panel: Selected Channel EPG Detail & Actions
            Box(
                modifier =
                    Modifier.weight(0.85f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xCC111622))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
            ) {
                val selectedChannel = state.selectedChannel
                if (selectedChannel == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Selecciona un canal para ver su guía EPG",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.65f),
                        )
                    }
                } else {
                    ChannelEpgDetailPanel(
                        channel = selectedChannel,
                        progress =
                            computeProgramProgress(
                                start = selectedChannel.currentProgram?.startDate,
                                end = selectedChannel.currentProgram?.endDate,
                                now = nowDateTime,
                            ),
                        timeFormatter = timeFormatter,
                        onWatchNow = {
                            navigateToPlayer(selectedChannel.id, BaseItemKind.TV_CHANNEL)
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(selectedChannel) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: LiveTvCategory,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        onClick = onSelect,
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor =
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                    else Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = Color.White,
                focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ),
        border =
            ClickableSurfaceDefaults.border(
                border =
                    if (isSelected) {
                        Border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(8.dp),
                        )
                    } else {
                        Border.None
                    },
                focusedBorder =
                    Border(
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp),
                    ),
            ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        modifier =
            Modifier.fillMaxWidth().onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onSelect()
                }
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = category.label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier =
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = category.count.toString(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun TvChannelRow(
    channel: JellyxChannel,
    fallbackNumber: Int,
    isSelected: Boolean,
    progress: Float?,
    timeFormatter: DateTimeFormatter,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val program = channel.currentProgram
    val channelNum = channel.channelNumber?.takeIf { it.isNotBlank() } ?: fallbackNumber.toString()

    Surface(
        onClick = onClick,
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor =
                    if (isSelected) Color(0xFF1D273B)
                    else Color(0xFF131823),
                focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = Color.White,
                focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ),
        border =
            ClickableSurfaceDefaults.border(
                border =
                    if (isSelected) {
                        Border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(10.dp),
                        )
                    } else {
                        Border.None
                    },
                focusedBorder =
                    Border(
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                    ),
            ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        modifier =
            Modifier.fillMaxWidth().onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onFocused()
                }
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            // Compact Channel Number Pill
            Box(
                modifier =
                    Modifier.width(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
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

            // Compact, Crisp Channel Logo (44x32 dp)
            Box(
                modifier =
                    Modifier.size(width = 44.dp, height = 32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF141820))
                        .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp)),
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
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Readable Title + EPG Program + Progress Bar
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = channel.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (channel.favorite) {
                        Text(text = "⭐", fontSize = 11.sp)
                    }
                    Box(
                        modifier =
                            Modifier.size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                val programText =
                    if (program != null) {
                        val start = program.startDate
                        val end = program.endDate
                        val timePrefix =
                            if (start != null && end != null) {
                                "${start.format(timeFormatter)}-${end.format(timeFormatter)} · "
                            } else {
                                ""
                            }
                        "$timePrefix${program.name}"
                    } else {
                        "Transmisión en directo"
                    }

                Text(
                    text = programText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (progress != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.Gray.copy(alpha = 0.35f))
                    ) {
                        Box(
                            modifier =
                                Modifier.fillMaxWidth(progress.coerceAtLeast(0.03f))
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelEpgDetailPanel(
    channel: JellyxChannel,
    progress: Float?,
    timeFormatter: DateTimeFormatter,
    onWatchNow: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val currentProgram = channel.currentProgram
    val nextProgram = channel.nextProgram

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Channel Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier =
                        Modifier.size(width = 56.dp, height = 40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF141820))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    val imageUri = channel.images.primary ?: channel.images.backdrop
                    if (imageUri != null) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = channel.displayName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                        )
                    } else {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_tv),
                            contentDescription = channel.displayName,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.displayName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val meta =
                        buildString {
                            channel.channelNumber?.takeIf { it.isNotBlank() }?.let {
                                append("CH $it • ")
                            }
                            append(channel.group)
                        }
                    Text(
                        text = meta,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.68f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Now Playing Card
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1A2233))
                        .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "AHORA EN EMISIÓN",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    val timeLabel =
                        if (
                            currentProgram?.startDate != null &&
                                currentProgram.endDate != null
                        ) {
                            "${currentProgram.startDate!!.format(timeFormatter)} - ${currentProgram.endDate!!.format(timeFormatter)}"
                        } else {
                            "EN VIVO"
                        }
                    Text(
                        text =
                            if (progress != null) "$timeLabel (${(progress * 100).toInt()}%)"
                            else timeLabel,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.78f),
                    )
                }

                Text(
                    text = currentProgram?.name?.takeIf { it.isNotBlank() } ?: "Programación en vivo",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (progress != null) {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.16f))
                    ) {
                        Box(
                            modifier =
                                Modifier.fillMaxWidth(progress.coerceAtLeast(0.03f))
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                val synopsis =
                    currentProgram?.overview?.takeIf { it.isNotBlank() }
                        ?: channel.overview.takeIf { it.isNotBlank() }
                        ?: "Señal directa IPTV passthrough sin recodificación."
                Text(
                    text = synopsis,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Up Next Card
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF141A27))
                        .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "⏭ A CONTINUACIÓN",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.6f),
                )
                if (nextProgram != null) {
                    val nextTime =
                        if (nextProgram.startDate != null && nextProgram.endDate != null) {
                            "${nextProgram.startDate!!.format(timeFormatter)} - ${nextProgram.endDate!!.format(timeFormatter)} · "
                        } else {
                            ""
                        }
                    Text(
                        text = "$nextTime${nextProgram.name}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = "Programación continua 24/7",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
            }
        }

        // Action Buttons
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onWatchNow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_play),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sintonizar Canal en Vivo",
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Button(
                onClick = onToggleFavorite,
                colors =
                    ButtonDefaults.colors(
                        containerColor = Color(0xFF1E2638),
                        contentColor = Color.White,
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter =
                        painterResource(
                            if (channel.favorite) CoreR.drawable.ic_heart_filled
                            else CoreR.drawable.ic_heart
                        ),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (channel.favorite) "Quitar de Favoritos" else "Agregar a Favoritos"
                )
            }
        }
    }
}
